package typr.avro.codegen

import typr.avro._
import typr.{jvm, Lang, Naming, Scope}
import typr.jvm.Code.{CodeOps, TreeOps, TypeOps}
import typr.internal.codegen._

/** Generates versioned record types and type aliases for schema evolution.
  *
  * When schema evolution is enabled:
  *   - Records are renamed with version suffix (OrderV1, OrderV2, etc.)
  *   - Type aliases are generated for the latest version (Order = OrderV3)
  *   - Optionally, migration helpers are generated (OrderMigrations)
  */
class VersionedRecordCodegen(naming: Naming, lang: Lang) {

  /** Result of processing versioned schemas */
  case class VersionedSchemaGroup(
      /** Base name without version (e.g., "Order") */
      baseName: String,
      /** Namespace */
      namespace: Option[String],
      /** Versioned schemas sorted by version */
      versions: List[(Int, AvroSchemaFile)],
      /** The latest version number */
      latestVersion: Int
  )

  /** Group schema files by their base name for versioned processing.
    *
    * Only groups schemas that have version information. Non-versioned schemas are returned unchanged.
    */
  def groupVersionedSchemas(schemas: List[AvroSchemaFile]): (List[VersionedSchemaGroup], List[AvroSchemaFile]) = {
    val (versioned, nonVersioned) = schemas.partition(_.version.isDefined)

    val grouped = versioned
      .groupBy { sf =>
        sf.primarySchema match {
          case r: AvroRecord => (r.name, r.namespace)
          case e: AvroEnum   => (e.name, e.namespace)
          case f: AvroFixed  => (f.name, f.namespace)
          case _: AvroError  => ("", None)
        }
      }
      .filter(_._1._1.nonEmpty)

    val groups = grouped.map { case ((name, namespace), files) =>
      val sorted = files.sortBy(_.version.get)
      val versions = sorted.map(f => (f.version.get, f))
      VersionedSchemaGroup(name, namespace, versions, versions.last._1)
    }.toList

    (groups, nonVersioned)
  }

  /** Rename a record to include version suffix (e.g., Order -> OrderV1) */
  def renameRecordWithVersion(record: AvroRecord, version: Int): AvroRecord = {
    record.copy(name = s"${record.name}V$version")
  }

  /** Rename an enum to include version suffix */
  def renameEnumWithVersion(avroEnum: AvroEnum, version: Int): AvroEnum = {
    avroEnum.copy(name = s"${avroEnum.name}V$version")
  }

  /** Rename schema file's primary schema with version */
  def renameSchemaWithVersion(schemaFile: AvroSchemaFile, version: Int): AvroSchemaFile = {
    val renamedPrimary = schemaFile.primarySchema match {
      case r: AvroRecord => renameRecordWithVersion(r, version)
      case e: AvroEnum   => renameEnumWithVersion(e, version)
      case other         => other
    }
    schemaFile.copy(primarySchema = renamedPrimary)
  }

  /** Generate a type alias interface for the latest version.
    *
    * Generates: public interface Order extends OrderV3 {}
    */
  def generateLatestTypeAlias(group: VersionedSchemaGroup): jvm.File = {
    val typeName = naming.avroRecordTypeName(group.baseName, group.namespace)
    val latestTypeName = naming.avroRecordTypeName(s"${group.baseName}V${group.latestVersion}", group.namespace)

    val interface = jvm.Adt.Sum(
      annotations = Nil,
      comments = jvm.Comments(List(s"Type alias for the latest version of ${group.baseName} (V${group.latestVersion})")),
      name = typeName,
      tparams = Nil,
      members = Nil,
      implements = List(latestTypeName),
      subtypes = Nil,
      staticMembers = Nil,
      permittedSubtypes = Nil
    )

    jvm.File(typeName, jvm.Code.Tree(interface), secondaryTypes = Nil, scope = Scope.Main)
  }
}

/** Generates migration helpers between schema versions.
  *
  * For a schema with versions 1, 2, 3, generates:
  *   - OrderMigrations.migrateV1ToV2(OrderV1): OrderV2
  *   - OrderMigrations.migrateV2ToV3(OrderV2): OrderV3
  */
class MigrationCodegen(naming: Naming, lang: Lang) {

  /** Field difference between two versions */
  sealed trait FieldDiff

  object FieldDiff {
    case class Added(field: AvroField) extends FieldDiff
    case class Removed(fieldName: String) extends FieldDiff
    case class Changed(oldField: AvroField, newField: AvroField) extends FieldDiff
  }

  /** Analyze differences between two record versions */
  def analyzeDiff(older: AvroRecord, newer: AvroRecord): List[FieldDiff] = {
    val oldFieldsByName = older.fields.map(f => f.name -> f).toMap
    val newFieldsByName = newer.fields.map(f => f.name -> f).toMap

    val added = newer.fields
      .filterNot(f => oldFieldsByName.contains(f.name))
      .map(FieldDiff.Added)

    val removed = older.fields
      .filterNot(f => newFieldsByName.contains(f.name))
      .map(f => FieldDiff.Removed(f.name))

    val changed = newer.fields.flatMap { newField =>
      oldFieldsByName.get(newField.name).flatMap { oldField =>
        if (oldField.fieldType != newField.fieldType) {
          Some(FieldDiff.Changed(oldField, newField))
        } else {
          None
        }
      }
    }

    added ++ removed ++ changed
  }

  /** Generate migration class with methods for all version transitions */
  def generateMigrationClass(group: VersionedRecordCodegen#VersionedSchemaGroup, typeMapper: AvroTypeMapper): Option[jvm.File] = {
    val versions = group.versions.map { case (v, sf) =>
      sf.primarySchema match {
        case r: AvroRecord => Some((v, r))
        case _             => None
      }
    }.flatten

    if (versions.size < 2) return None

    val migrationClassName = jvm.Type.Qualified(
      naming.avroRecordPackage / jvm.Ident(s"${group.baseName}Migrations")
    )

    val migrationMethods = versions.sliding(2).toList.flatMap {
      case List((fromVersion, fromRecord), (toVersion, toRecord)) =>
        generateMigrationMethod(
          fromVersion,
          fromRecord,
          toVersion,
          toRecord,
          group.namespace,
          typeMapper
        )
      case _ => None
    }

    if (migrationMethods.isEmpty) return None

    val migrationsClass = jvm.Adt.Sum(
      annotations = Nil,
      comments = jvm.Comments(List(s"Migration helpers for ${group.baseName} schema versions")),
      name = migrationClassName,
      tparams = Nil,
      members = Nil,
      implements = Nil,
      subtypes = Nil,
      staticMembers = migrationMethods,
      permittedSubtypes = Nil
    )

    Some(jvm.File(migrationClassName, jvm.Code.Tree(migrationsClass), secondaryTypes = Nil, scope = Scope.Main))
  }

  /** Generate a single migration method from one version to the next */
  private def generateMigrationMethod(
      fromVersion: Int,
      fromRecord: AvroRecord,
      toVersion: Int,
      toRecord: AvroRecord,
      namespace: Option[String],
      typeMapper: AvroTypeMapper
  ): Option[jvm.Method] = {
    val fromTypeName = naming.avroRecordTypeName(s"${fromRecord.name}V$fromVersion", namespace)
    val toTypeName = naming.avroRecordTypeName(s"${toRecord.name}V$toVersion", namespace)

    val diffs = analyzeDiff(fromRecord, toRecord)

    val fromParam = jvm.Param(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      name = jvm.Ident("source"),
      tpe = fromTypeName,
      default = None
    )

    val fieldAssignments = toRecord.fields.map { toField =>
      val fromFieldOpt = fromRecord.fields.find(_.name == toField.name)

      fromFieldOpt match {
        case Some(fromField) if fromField.fieldType == toField.fieldType =>
          val getter = lang.nullaryMethodCall(jvm.Ident("source").code, jvm.Ident(toField.name))
          jvm.Arg.Pos(getter)
        case Some(_) =>
          val defaultValue = getDefaultValueForField(toField, typeMapper)
          jvm.Arg.Pos(defaultValue)
        case None =>
          val defaultValue = getDefaultValueForField(toField, typeMapper)
          jvm.Arg.Pos(defaultValue)
      }
    }

    val constructorCall = jvm.New(toTypeName.code, fieldAssignments)

    Some(
      jvm.Method(
        annotations = Nil,
        comments = jvm.Comments(List(s"Migrate from V$fromVersion to V$toVersion")),
        tparams = Nil,
        name = jvm.Ident(s"migrateV${fromVersion}ToV$toVersion"),
        params = List(fromParam),
        implicitParams = Nil,
        tpe = toTypeName,
        throws = Nil,
        body = jvm.Body.Stmts(List(jvm.Return(constructorCall.code).code)),
        isOverride = false,
        isDefault = false
      )
    )
  }

  /** Get a default value for a field based on its type */
  private def getDefaultValueForField(field: AvroField, typeMapper: AvroTypeMapper): jvm.Code = {
    field.defaultValue match {
      case Some(jsonDefault) =>
        parseDefaultValue(jsonDefault, field.fieldType, typeMapper)
      case None =>
        getTypeDefaultValue(field.fieldType, typeMapper)
    }
  }

  /** Parse a JSON default value to code */
  private def parseDefaultValue(json: String, fieldType: AvroType, typeMapper: AvroTypeMapper): jvm.Code = {
    fieldType match {
      case AvroType.String                         => jvm.StrLit(json.stripPrefix("\"").stripSuffix("\"")).code
      case AvroType.Int                            => code"${jvm.Ident(json.trim)}"
      case AvroType.Long                           => code"${jvm.Ident(json.trim + "L")}"
      case AvroType.Float                          => code"${jvm.Ident(json.trim + "f")}"
      case AvroType.Double                         => code"${jvm.Ident(json.trim + "d")}"
      case AvroType.Boolean if json.trim == "true" => code"true"
      case AvroType.Boolean                        => code"false"
      case AvroType.Null                           => code"null"
      case AvroType.Union(_) if json == "null"     => code"null"
      case AvroType.Array(_) if json == "[]"       => lang.ListType.create(Nil)
      case AvroType.Map(_) if json == "{}"         => lang.MapOps.newMutableMap(lang.String, lang.topType)
      case _                                       => code"null"
    }
  }

  /** Get the default value for a type (when no explicit default) */
  private def getTypeDefaultValue(fieldType: AvroType, typeMapper: AvroTypeMapper): jvm.Code = fieldType match {
    case AvroType.String                                            => jvm.StrLit("").code
    case AvroType.Int                                               => code"0"
    case AvroType.Long                                              => code"0L"
    case AvroType.Float                                             => code"0.0f"
    case AvroType.Double                                            => code"0.0d"
    case AvroType.Boolean                                           => code"false"
    case AvroType.Null                                              => code"null"
    case AvroType.Union(members) if members.contains(AvroType.Null) => code"null"
    case AvroType.Array(_)                                          => lang.ListType.create(Nil)
    case AvroType.Map(_)                                            => lang.MapOps.newMutableMap(lang.String, lang.topType)
    case _                                                          => code"null"
  }
}

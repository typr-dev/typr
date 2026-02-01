package typr.avro.codegen

import typr.avro._
import typr.jvm.Code.{CodeOps, TreeOps, TypeOps}
import typr.openapi.codegen.JsonLibSupport
import typr.{jvm, Lang, Naming, Scope, TypesJava}
import typr.internal.codegen._

/** Generates jvm.File for Avro record types */
class RecordCodegen(
    naming: Naming,
    typeMapper: AvroTypeMapper,
    lang: Lang,
    avroWireFormat: AvroWireFormatSupport,
    jsonSchema: AvroRecord => String,
    wrapperTypeMap: Map[(Option[String], String), jvm.Type.Qualified],
    jsonLibSupport: JsonLibSupport
) {

  private val GenericRecordType = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.generic.GenericRecord"))

  /** Map a field type, handling wrapper types if specified */
  private def mapFieldType(field: AvroField, recordNamespace: Option[String]): jvm.Type = {
    field.wrapperType match {
      case Some(wrapperName) =>
        val wrapperType = wrapperTypeMap.getOrElse(
          (recordNamespace, wrapperName),
          wrapperTypeMap.getOrElse(
            (None, wrapperName),
            sys.error(s"Wrapper type $wrapperName not found")
          )
        )
        field.fieldType match {
          case AvroType.Union(members) if members.contains(AvroType.Null) =>
            lang.Optional.tpe(wrapperType)
          case _ =>
            wrapperType
        }
      case None =>
        typeMapper.mapType(field.fieldType)
    }
  }

  /** Generate a record class from an AvroRecord */
  def generate(record: AvroRecord, parentType: Option[jvm.Type.Qualified]): jvm.File = {
    val tpe = naming.avroRecordTypeName(record.name, record.namespace)
    val comments = record.doc.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty)

    val isJsonFormat = avroWireFormat.isJsonWireFormat

    val params = record.fields.map { field =>
      val fieldType = mapFieldType(field, record.namespace)
      // Add JSON annotations for JSON wire format
      val annotations = if (isJsonFormat) {
        jsonLibSupport.propertyAnnotations(field.name)
      } else {
        Nil
      }
      jvm.Param(
        annotations = annotations,
        comments = field.doc.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty),
        name = naming.avroFieldName(field.name),
        tpe = fieldType,
        default = None
      )
    }

    // For JSON wire format: no Avro schema or serialization methods, but add JSON library static members
    // For Avro binary formats: generate schema and toGenericRecord/fromGenericRecord
    val (members, staticMembers) = if (isJsonFormat) {
      // JSON wire format: records are annotated DTOs
      // Add JSON library static members (e.g., Circe encoder/decoder derivation)
      val jsonStaticMembers = jsonLibSupport.objectTypeStaticMembers(tpe)
      (Nil, jsonStaticMembers)
    } else {
      val schemaJson = jsonSchema(record)
      val schemaField = avroWireFormat.schemaField(record, schemaJson)
      val toGenericRecordBase = avroWireFormat.toGenericRecordMethod(record)
      // If record implements a parent type, mark toGenericRecord as override
      val toGenericRecord = if (parentType.isDefined) toGenericRecordBase.copy(isOverride = true) else toGenericRecordBase
      val fromGenericRecord = avroWireFormat.fromGenericRecordMethod(record)
      (List(toGenericRecord), List(schemaField, fromGenericRecord))
    }

    val recordAdt = jvm.Adt.Record(
      annotations = Nil,
      constructorAnnotations = Nil,
      isWrapper = false,
      privateConstructor = false,
      comments = comments,
      name = tpe,
      tparams = Nil,
      params = params,
      implicitParams = Nil,
      `extends` = None,
      implements = parentType.toList,
      members = members,
      staticMembers = staticMembers
    )

    val generatedCode = jvm.Code.Tree(recordAdt)
    jvm.File(tpe, generatedCode, secondaryTypes = Nil, scope = Scope.Main)
  }

  /** Generate a sealed trait/interface for an event group (sum type) */
  def generateEventGroup(group: AvroEventGroup): jvm.File = {
    val tpe = naming.avroEventGroupTypeName(group.name, group.namespace)
    val comments = group.doc.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty)

    val isJsonFormat = avroWireFormat.isJsonWireFormat

    // For JSON wire format: no Avro methods, just a marker interface with Jackson annotations
    // For Avro binary formats: generate toGenericRecord and fromGenericRecord
    val (annotations, members, staticMembers) = if (isJsonFormat) {
      // JSON wire format: sealed interface with annotations for polymorphic JSON
      val subtypes = group.members.map { member =>
        val memberType = naming.avroRecordTypeName(member.name, member.namespace)
        (memberType, member.name)
      }
      val jsonAnnotations = jsonLibSupport.sealedTypeAnnotations(subtypes, "@type")
      (jsonAnnotations, Nil, Nil)
    } else {
      // Generate the toGenericRecord abstract method
      val toGenericRecordMethod = jvm.Method(
        annotations = Nil,
        comments = jvm.Comments(List("Convert this event to a GenericRecord for serialization")),
        tparams = Nil,
        name = jvm.Ident("toGenericRecord"),
        params = Nil,
        implicitParams = Nil,
        tpe = GenericRecordType,
        throws = Nil,
        body = jvm.Body.Abstract,
        isOverride = false,
        isDefault = false
      )

      // Generate the fromGenericRecord dispatcher method
      val fromGenericRecordMethod = generateFromGenericRecordDispatcher(group, tpe)

      (Nil, List(toGenericRecordMethod), List(fromGenericRecordMethod))
    }

    val sealedTrait = jvm.Adt.Sum(
      annotations = annotations,
      comments = comments,
      name = tpe,
      tparams = Nil,
      members = members,
      implements = Nil,
      subtypes = Nil,
      staticMembers = staticMembers,
      permittedSubtypes = group.members.map(m => naming.avroRecordTypeName(m.name, m.namespace))
    )

    val generatedCode = jvm.Code.Tree(sealedTrait)
    jvm.File(tpe, generatedCode, secondaryTypes = Nil, scope = Scope.Main)
  }

  /** Generate the fromGenericRecord dispatcher that routes to subtypes based on schema name */
  private def generateFromGenericRecordDispatcher(group: AvroEventGroup, groupType: jvm.Type.Qualified): jvm.Method = {
    val recordParam = jvm.Param(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      name = jvm.Ident("record"),
      tpe = GenericRecordType,
      default = None
    )

    // Generate if-else chain on schema full name
    val schemaNameExpr = recordParam.name.code.invoke("getSchema").invoke("getFullName")

    // Build if-else branches for each member type
    val branches = group.members.map { member =>
      val memberType = naming.avroRecordTypeName(member.name, member.namespace)
      val fullName = member.fullName
      val condition = schemaNameExpr.invoke("equals", jvm.StrLit(fullName).code)
      val returnStmt = jvm.Return(memberType.code.invoke("fromGenericRecord", recordParam.name.code)).code
      jvm.If.Branch(condition, returnStmt)
    }

    // Default case: throw exception for unknown schema
    val throwStmt = jvm
      .Throw(
        jvm.Type
          .Qualified("java.lang.IllegalArgumentException")
          .construct(
            code""""Unknown schema: " + $schemaNameExpr"""
          )
      )
      .code

    val ifElseChain = jvm.If(branches, Some(throwStmt)).code

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(List("Create an event from a GenericRecord, dispatching to the correct subtype based on schema name")),
      tparams = Nil,
      name = jvm.Ident("fromGenericRecord"),
      params = List(recordParam),
      implicitParams = Nil,
      tpe = groupType,
      throws = Nil,
      body = jvm.Body.Stmts(List(ifElseChain)),
      isOverride = false,
      isDefault = false
    )
  }

  /** Generate an enum class from an AvroEnum */
  def generateEnum(avroEnum: AvroEnum): jvm.File = {
    val tpe = naming.avroRecordTypeName(avroEnum.name, avroEnum.namespace)
    val comments = avroEnum.doc.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty)

    val values = typr.NonEmptyList
      .fromList(avroEnum.symbols.map { symbol =>
        (naming.avroEnumValueName(symbol), jvm.StrLit(symbol).code)
      })
      .getOrElse(sys.error(s"Enum ${avroEnum.name} has no symbols"))

    val enumTree = jvm.Enum(
      annotations = Nil,
      comments = comments,
      tpe = tpe,
      values = values,
      members = Nil,
      staticMembers = Nil
    )

    val generatedCode = jvm.Code.Tree(enumTree)
    jvm.File(tpe, generatedCode, secondaryTypes = Nil, scope = Scope.Main)
  }
}

/** Maps Avro types to JVM types.
  *
  * @param lang
  *   Target language
  * @param unionTypeNames
  *   Map from complex union types to their generated type names
  * @param naming
  *   Naming configuration for precise types
  * @param enablePreciseTypes
  *   Whether to generate precise wrapper types for constrained types
  * @param wrapperTypeMap
  *   Map from (namespace, wrapperName) to generated wrapper types
  */
class AvroTypeMapper(
    lang: Lang,
    unionTypeNames: Map[AvroType.Union, jvm.Type.Qualified],
    naming: Option[Naming],
    enablePreciseTypes: Boolean,
    wrapperTypeMap: Map[(Option[String], String), jvm.Type.Qualified]
) {

  /** Simplified constructor for when no complex unions or wrapper types are being generated */
  def this(lang: Lang) = this(lang, Map.empty, None, false, Map.empty)

  def mapType(avroType: AvroType): jvm.Type = avroType match {
    case AvroType.Null    => lang.voidType
    case AvroType.Boolean => lang.Boolean
    case AvroType.Int     => lang.Int
    case AvroType.Long    => lang.Long
    case AvroType.Float   => lang.Float
    case AvroType.Double  => lang.Double
    case AvroType.Bytes   => lang.ByteArray
    case AvroType.String  => lang.String

    case AvroType.UUID                 => TypesJava.UUID
    case AvroType.Date                 => TypesJava.LocalDate
    case AvroType.TimeMillis           => TypesJava.LocalTime
    case AvroType.TimeMicros           => TypesJava.LocalTime
    case AvroType.TimeNanos            => TypesJava.LocalTime
    case AvroType.TimestampMillis      => TypesJava.Instant
    case AvroType.TimestampMicros      => TypesJava.Instant
    case AvroType.TimestampNanos       => TypesJava.Instant
    case AvroType.LocalTimestampMillis => TypesJava.LocalDateTime
    case AvroType.LocalTimestampMicros => TypesJava.LocalDateTime
    case AvroType.LocalTimestampNanos  => TypesJava.LocalDateTime
    case AvroType.Duration             => lang.ByteArray // 12-byte fixed

    case d: AvroType.DecimalBytes =>
      if (enablePreciseTypes) naming.map(n => jvm.Type.Qualified(n.preciseDecimalNName(d.precision, d.scale))).getOrElse(TypesJava.BigDecimal)
      else TypesJava.BigDecimal
    case d: AvroType.DecimalFixed =>
      if (enablePreciseTypes) naming.map(n => jvm.Type.Qualified(n.preciseDecimalNName(d.precision, d.scale))).getOrElse(TypesJava.BigDecimal)
      else TypesJava.BigDecimal

    case AvroType.Array(items) =>
      lang.ListType.tpe.of(mapType(items))

    case AvroType.Map(values) =>
      lang.MapOps.tpe.of(lang.String, mapType(values))

    case u @ AvroType.Union(members) =>
      members.filterNot(_ == AvroType.Null) match {
        case List(single) =>
          // Nullable type: ["null", T] or [T, "null"]
          if (members.contains(AvroType.Null)) {
            lang.Optional.tpe(mapType(single))
          } else {
            mapType(single)
          }
        case nonNullMembers =>
          // Complex union - look up the generated type name
          unionTypeNames.get(normalizeUnion(u)) match {
            case Some(unionType) =>
              // Wrap in Optional if union contains null
              if (members.contains(AvroType.Null)) {
                lang.Optional.tpe(unionType)
              } else {
                unionType
              }
            case None =>
              // Fallback to Object/Any if not in the map
              lang.topType
          }
      }

    case AvroType.Named(fullName) =>
      jvm.Type.Qualified(jvm.QIdent(fullName))

    case AvroType.Record(record) =>
      jvm.Type.Qualified(jvm.QIdent(record.fullName))

    case AvroType.EnumType(avroEnum) =>
      jvm.Type.Qualified(jvm.QIdent(avroEnum.fullName))

    case AvroType.Fixed(fixed) =>
      if (enablePreciseTypes) naming.map(n => jvm.Type.Qualified(n.preciseBinaryNName(fixed.size))).getOrElse(lang.ByteArray)
      else lang.ByteArray
  }

  /** Normalize a union for use as a map key (remove null, sort members) */
  private def normalizeUnion(union: AvroType.Union): AvroType.Union = {
    val nonNull = union.members.filterNot(_ == AvroType.Null)
    AvroType.Union(nonNull.sortBy(_.toString))
  }
}

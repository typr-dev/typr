package typr.cli.app

import typr.{MetaDb, Nullability, db}
import typr.avro.{AvroField, AvroRecord, AvroSchema, AvroSchemaFile, AvroType}
import typr.grpc.{ProtoField, ProtoFieldLabel, ProtoFile, ProtoMessage, ProtoType}
import typr.openapi.{ModelClass, ParsedSpec, PrimitiveType, TypeInfo}

/** A uniform "entity" abstraction across all source kinds — a record-like thing with a name, a path within its source, and a list of named, typed fields. Drives the "create domain type from a real
  * entity" flow: instead of asking the user to invent a `boundary:entity` string, we show real entities (tables, models, records, messages) from already-loaded sources and let them pick.
  */
final case class Entity(
    sourceName: String,
    kind: Entity.Kind,
    /** The entity's path within its source as it should appear in `primary`:
      *   - db: "schema.table" or "table"
      *   - spec: model name
      *   - avro: fully-qualified record name
      *   - proto: fully-qualified message name
      */
    path: String,
    /** A clean PascalCase suggestion for the [[typr.config.generated.DomainType]] name. */
    suggestedName: String,
    fields: List[Entity.Field]
) {

  /** The `primary` string this entity would produce in a DomainType. */
  def primaryKey: String = s"$sourceName:$path"

  /** Lowercase, normalised field-name set used for alignment overlap scoring. */
  def fieldNameSet: Set[String] = fields.iterator.map(f => Entity.normalise(f.name)).toSet
}

object Entity {

  enum Kind {
    case Db, Spec, Avro, Proto
  }

  final case class Field(name: String, typeLabel: String, optional: Boolean)

  /** Convert a snake_case / kebab-case / dot.separated name to PascalCase. Used to suggest a domain-type name from an entity path.
    */
  def pascalCase(s: String): String = {
    val cleaned = s.replaceAll("[^A-Za-z0-9]+", " ").trim
    if (cleaned.isEmpty) "Type"
    else cleaned.split("\\s+").iterator.map(w => w.headOption.fold("")(_.toUpper.toString) + w.drop(1)).mkString
  }

  /** Normalised form of a field name for alignment overlap. Lowercase, leading underscore stripped, snake↔camel collapsed.
    */
  def normalise(name: String): String = {
    val stripped = name.dropWhile(c => c == '_' || c == '-')
    val parts = scala.collection.mutable.ListBuffer.empty[String]
    val cur = new StringBuilder
    stripped.foreach { ch =>
      if (ch == '_' || ch == '-') {
        if (cur.nonEmpty) { parts += cur.toString.toLowerCase; cur.clear() }
      } else if (ch.isUpper && cur.nonEmpty && !cur.last.isUpper) {
        parts += cur.toString.toLowerCase
        cur.clear()
        cur += ch
      } else cur += ch
    }
    if (cur.nonEmpty) parts += cur.toString.toLowerCase
    parts.mkString
  }
}

/** Enumerate entities across all loaded sources, and compute alignment suggestions between them.
  *
  * The catalog only sees sources whose [[LoadedSource]] is already in [[AppApi.sourceCache]]. Sources still loading are simply absent from the list; sources that failed don't contribute either. As
  * individual sources complete in the background, calling [[fromCache]] again picks up the new entities — no caching in here, just walks the latest cache state.
  */
object EntityCatalog {

  /** One alignment-suggestion result: a candidate entity from a different source, with the count of fields whose normalised names overlap.
    */
  final case class Alignment(target: Entity, matched: Int, total: Int) {
    def score: Double = if (total == 0) 0.0 else matched.toDouble / total
    def looksAligned: Boolean = matched >= 2 && score >= 0.5
  }

  def fromCache(cache: collection.Map[String, LoadedSource]): List[Entity] =
    cache.toList.sortBy(_._1).flatMap { case (sourceName, loaded) =>
      loaded match {
        case LoadedSource.Db(metaDb)   => fromDb(sourceName, metaDb)
        case LoadedSource.Spec(spec)   => fromSpec(sourceName, spec)
        case LoadedSource.Avro(files)  => fromAvro(sourceName, files)
        case LoadedSource.Proto(files) => fromProto(sourceName, files)
      }
    }

  /** Suggest aligned-source candidates for `primary` from `all`. Candidates must come from a different source, must share at least 2 field names with the target, and must cover ≥50% of the target's
    * fields. Sorted best-first.
    *
    * Field-name comparison normalises snake_case / camelCase / leading underscores to a single lower-case word stream — so `customer_id`, `customerId`, and `CustomerID` all match.
    */
  def alignmentSuggestions(primary: Entity, all: List[Entity]): List[Alignment] = {
    val primaryFields = primary.fieldNameSet
    if (primaryFields.isEmpty) return Nil
    all.iterator
      .filter(e => e.sourceName != primary.sourceName)
      .map { candidate =>
        val intersect = primaryFields.intersect(candidate.fieldNameSet).size
        Alignment(candidate, intersect, primaryFields.size)
      }
      .filter(_.looksAligned)
      .toList
      .sortBy(a => (-a.matched, -a.score, a.target.path))
  }

  // ─────────────────────────────────── per-kind enumeration ───────────────────────────────────

  private def fromDb(sourceName: String, metaDb: MetaDb): List[Entity] =
    metaDb.relations.values
      .flatMap(_.get.toList)
      .toList
      .collect { case t: db.Table => t }
      .sortBy(_.name.value)
      .map { table =>
        val path = table.name.schema match {
          case Some(s) if s.nonEmpty => s"$s.${table.name.name}"
          case _                     => table.name.name
        }
        val fields = table.cols.toList.map { c =>
          Entity.Field(
            name = c.name.value,
            typeLabel = c.tpe.toString,
            optional = c.nullability != Nullability.NoNulls
          )
        }
        Entity(sourceName, Entity.Kind.Db, path, Entity.pascalCase(table.name.name), fields)
      }

  private def fromSpec(sourceName: String, spec: ParsedSpec): List[Entity] =
    spec.models.collect { case obj: ModelClass.ObjectType => obj }.sortBy(_.name).map { obj =>
      val fields = obj.properties.map(p =>
        Entity.Field(
          name = p.name,
          typeLabel = renderTypeInfo(p.typeInfo),
          optional = !p.required || p.nullable
        )
      )
      Entity(sourceName, Entity.Kind.Spec, obj.name, Entity.pascalCase(obj.name), fields)
    }

  private def fromAvro(sourceName: String, files: List[AvroSchemaFile]): List[Entity] = {
    val schemas: List[AvroSchema] = files.flatMap(f => f.primarySchema :: f.inlineSchemas)
    schemas.collect { case r: AvroRecord => r }.sortBy(_.fullName).map { r =>
      Entity(sourceName, Entity.Kind.Avro, r.fullName, Entity.pascalCase(r.name), r.fields.map(toEntityField))
    }
  }

  private def fromProto(sourceName: String, files: List[ProtoFile]): List[Entity] = {
    def walk(m: ProtoMessage): List[ProtoMessage] = m :: m.nestedMessages.flatMap(walk)
    val messages = files.flatMap(_.messages.flatMap(walk)).filterNot(_.isMapEntry).sortBy(_.fullName)
    messages.map { m =>
      Entity(sourceName, Entity.Kind.Proto, m.fullName, Entity.pascalCase(m.name), m.fields.map(toEntityField))
    }
  }

  // ─────────────────────────────────── field rendering ───────────────────────────────────

  private def toEntityField(f: AvroField): Entity.Field =
    Entity.Field(name = f.name, typeLabel = renderAvroType(f.fieldType), optional = f.isOptional)

  private def toEntityField(f: ProtoField): Entity.Field = {
    val opt = f.proto3Optional ||
      f.label == ProtoFieldLabel.Optional ||
      f.fieldType.isInstanceOf[ProtoType.Message]
    Entity.Field(name = f.name, typeLabel = renderProtoType(f.fieldType), optional = opt)
  }

  private def renderTypeInfo(t: TypeInfo): String = t match {
    case TypeInfo.Primitive(p)    => renderPrimitive(p)
    case TypeInfo.ListOf(inner)   => s"List[${renderTypeInfo(inner)}]"
    case TypeInfo.Optional(inner) => s"Optional[${renderTypeInfo(inner)}]"
    case TypeInfo.MapOf(k, v)     => s"Map[${renderTypeInfo(k)}, ${renderTypeInfo(v)}]"
    case TypeInfo.Ref(name)       => name
    case TypeInfo.Any             => "Any"
    case TypeInfo.InlineEnum(vs)  => s"enum(${vs.take(3).mkString("|")}${if (vs.size > 3) "…" else ""})"
  }

  private def renderPrimitive(p: PrimitiveType): String = p match {
    case PrimitiveType.String     => "String"
    case PrimitiveType.Int32      => "Int"
    case PrimitiveType.Int64      => "Long"
    case PrimitiveType.Float      => "Float"
    case PrimitiveType.Double     => "Double"
    case PrimitiveType.Boolean    => "Boolean"
    case PrimitiveType.Date       => "Date"
    case PrimitiveType.DateTime   => "DateTime"
    case PrimitiveType.Time       => "Time"
    case PrimitiveType.UUID       => "UUID"
    case PrimitiveType.URI        => "URI"
    case PrimitiveType.Email      => "Email"
    case PrimitiveType.Binary     => "Binary"
    case PrimitiveType.Byte       => "Byte"
    case PrimitiveType.BigDecimal => "BigDecimal"
  }

  private def renderAvroType(t: AvroType): String = t match {
    case AvroType.Null           => "null"
    case AvroType.Boolean        => "boolean"
    case AvroType.Int            => "int"
    case AvroType.Long           => "long"
    case AvroType.Float          => "float"
    case AvroType.Double         => "double"
    case AvroType.Bytes          => "bytes"
    case AvroType.String         => "string"
    case AvroType.Array(items)   => s"array[${renderAvroType(items)}]"
    case AvroType.Map(values)    => s"map[string, ${renderAvroType(values)}]"
    case AvroType.Union(members) =>
      AvroType.unwrapNullable(AvroType.Union(members)) match {
        case Some(inner) => s"${renderAvroType(inner)}?"
        case None        => members.map(renderAvroType).mkString(" | ")
      }
    case AvroType.Named(fullName)       => fullName
    case AvroType.Record(r)             => r.fullName
    case AvroType.EnumType(en)          => en.fullName
    case AvroType.Fixed(f)              => f.fullName
    case AvroType.UUID                  => "uuid"
    case AvroType.Date                  => "date"
    case AvroType.TimeMillis            => "time-ms"
    case AvroType.TimeMicros            => "time-us"
    case AvroType.TimestampMillis       => "timestamp-ms"
    case AvroType.TimestampMicros       => "timestamp-us"
    case AvroType.LocalTimestampMillis  => "local-timestamp-ms"
    case AvroType.LocalTimestampMicros  => "local-timestamp-us"
    case AvroType.TimeNanos             => "time-ns"
    case AvroType.TimestampNanos        => "timestamp-ns"
    case AvroType.LocalTimestampNanos   => "local-timestamp-ns"
    case AvroType.DecimalBytes(p, s)    => s"decimal($p,$s)"
    case AvroType.DecimalFixed(p, s, _) => s"decimal($p,$s)"
    case AvroType.Duration              => "duration"
  }

  private def renderProtoType(t: ProtoType): String = t match {
    case ProtoType.Double      => "double"
    case ProtoType.Float       => "float"
    case ProtoType.Int32       => "int32"
    case ProtoType.Int64       => "int64"
    case ProtoType.UInt32      => "uint32"
    case ProtoType.UInt64      => "uint64"
    case ProtoType.SInt32      => "sint32"
    case ProtoType.SInt64      => "sint64"
    case ProtoType.Fixed32     => "fixed32"
    case ProtoType.Fixed64     => "fixed64"
    case ProtoType.SFixed32    => "sfixed32"
    case ProtoType.SFixed64    => "sfixed64"
    case ProtoType.Bool        => "bool"
    case ProtoType.String      => "string"
    case ProtoType.Bytes       => "bytes"
    case ProtoType.Message(fn) => shortName(fn)
    case ProtoType.Enum(fn)    => shortName(fn)
    case ProtoType.Map(k, v)   => s"map<${renderProtoType(k)}, ${renderProtoType(v)}>"
    case ProtoType.Timestamp   => "Timestamp"
    case ProtoType.Duration    => "Duration"
    case other                 => other.toString.takeWhile(_ != '(')
  }

  private def shortName(fullName: String): String =
    fullName.split('.').lastOption.getOrElse(fullName)
}

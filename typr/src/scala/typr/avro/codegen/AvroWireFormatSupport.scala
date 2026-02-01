package typr.avro.codegen

import typr.avro._
import typr.jvm
import typr.jvm.Code.{CodeOps, TreeOps, TypeOps}
import typr.Lang
import typr.internal.codegen.{LangScala, LangKotlin}

/** Abstraction over Avro wire formats for code generation.
  *
  * This trait provides methods for generating Avro serialization code (toGenericRecord, fromGenericRecord) that work with both Confluent Schema Registry format and raw binary encoding.
  *
  * For JSON wire format, records use JSON annotations instead of Avro serialization methods.
  */
trait AvroWireFormatSupport {

  /** Language being generated */
  def lang: Lang

  /** Generate imports for record classes */
  def recordImports: List[jvm.Type.Qualified]

  /** Generate static schema field: public static final Schema SCHEMA$ = ... */
  def schemaField(record: AvroRecord, jsonSchema: String): jvm.ClassMember

  /** Generate toGenericRecord method */
  def toGenericRecordMethod(record: AvroRecord): jvm.Method

  /** Generate fromGenericRecord factory method */
  def fromGenericRecordMethod(record: AvroRecord): jvm.ClassMember

  /** Dependencies required by generated code */
  def dependencies: List[KafkaDependency]

  /** Whether this is a JSON wire format (records get JSON annotations, no Avro methods) */
  def isJsonWireFormat: Boolean = false
}

/** A dependency required by the generated code */
case class KafkaDependency(
    groupId: String,
    artifactId: String,
    version: String
)

object AvroWireFormatSupport {

  /** Create appropriate AvroWireFormatSupport for the given options */
  def apply(
      avroWireFormat: AvroWireFormat,
      lang: Lang,
      enumNames: Set[String],
      unionTypeNames: Map[AvroType.Union, jvm.Type.Qualified],
      naming: Option[typr.Naming],
      enablePreciseTypes: Boolean,
      wrapperTypeMap: Map[(Option[String], String), jvm.Type.Qualified]
  ): AvroWireFormatSupport = avroWireFormat match {
    case AvroWireFormat.ConfluentRegistry    => new ConfluentRegistryCodegen(lang, enumNames, unionTypeNames, naming, enablePreciseTypes, wrapperTypeMap)
    case AvroWireFormat.BinaryEncoded        => new BinaryEncodedCodegen(lang, enumNames, unionTypeNames, naming, enablePreciseTypes, wrapperTypeMap)
    case AvroWireFormat.JsonEncoded(jsonLib) => new JsonEncodedCodegen(lang, jsonLib)
  }
}

/** Confluent Schema Registry wire format code generation */
class ConfluentRegistryCodegen(
    val lang: Lang,
    enumNames: Set[String],
    unionTypeNames: Map[AvroType.Union, jvm.Type.Qualified],
    naming: Option[typr.Naming],
    enablePreciseTypes: Boolean,
    wrapperTypeMap: Map[(Option[String], String), jvm.Type.Qualified]
) extends AvroWireFormatSupport {
  import typr.internal.codegen._

  private val SchemaType = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.Schema"))
  private val SchemaParserType = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.Schema.Parser"))
  private val GenericRecordType = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.generic.GenericRecord"))
  private val GenericDataRecordType = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.generic.GenericData.Record"))
  private val GenericEnumSymbolType = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.generic.GenericData.EnumSymbol"))

  private def isEnum(fullName: String): Boolean = enumNames.contains(fullName)

  /** Normalize a union for map lookup (remove null, sort members) */
  private def normalizeUnion(union: AvroType.Union): AvroType.Union = {
    val nonNull = union.members.filterNot(_ == AvroType.Null)
    AvroType.Union(nonNull.sortBy(_.toString))
  }

  /** Check if a union is "complex" (multiple non-null members) */
  private def isComplexUnion(union: AvroType.Union): Boolean = {
    val nonNull = union.members.filterNot(_ == AvroType.Null)
    nonNull.size >= 2
  }

  /** Generate instanceof check (language-specific syntax) */
  private def instanceOfCheck(expr: jvm.Code, tpe: jvm.Type): jvm.Code = lang match {
    case _: LangScala  => code"$expr.isInstanceOf[$tpe]"
    case _: LangKotlin => code"$expr is $tpe"
    case _             => code"$expr instanceof $tpe" // Java
  }

  /** Map Avro type to type for instanceof checks (language-aware). For Kotlin, uses Kotlin types; for Java/Scala, uses Java boxed types.
    */
  private def mapAvroTypeToInstanceOfType(avroType: AvroType): jvm.Type = {
    val isKotlin = lang.isInstanceOf[LangKotlin]
    avroType match {
      case AvroType.Boolean                                                              => if (isKotlin) lang.Boolean else jvm.Type.Qualified("java.lang.Boolean")
      case AvroType.Int                                                                  => if (isKotlin) lang.Int else jvm.Type.Qualified("java.lang.Integer")
      case AvroType.Long                                                                 => if (isKotlin) lang.Long else jvm.Type.Qualified("java.lang.Long")
      case AvroType.Float                                                                => if (isKotlin) lang.Float else jvm.Type.Qualified("java.lang.Float")
      case AvroType.Double                                                               => if (isKotlin) lang.Double else jvm.Type.Qualified("java.lang.Double")
      case AvroType.String                                                               => jvm.Type.Qualified("java.lang.CharSequence") // Avro returns Utf8, which implements CharSequence
      case AvroType.Bytes                                                                => lang.ByteArrayType
      case AvroType.UUID                                                                 => jvm.Type.Qualified("java.util.UUID")
      case AvroType.Date                                                                 => jvm.Type.Qualified("java.time.LocalDate")
      case AvroType.TimeMillis | AvroType.TimeMicros | AvroType.TimeNanos                => jvm.Type.Qualified("java.time.LocalTime")
      case AvroType.TimestampMillis | AvroType.TimestampMicros | AvroType.TimestampNanos => jvm.Type.Qualified("java.time.Instant")
      case AvroType.LocalTimestampMillis | AvroType.LocalTimestampMicros | AvroType.LocalTimestampNanos => jvm.Type.Qualified("java.time.LocalDateTime")
      case _: AvroType.DecimalBytes                                                                     => jvm.Type.Qualified("java.math.BigDecimal")
      case _: AvroType.DecimalFixed                                                                     => jvm.Type.Qualified("java.math.BigDecimal")
      case AvroType.Array(_)                                                                            => jvm.Type.Qualified("java.util.List")
      case AvroType.Map(_)                                                                              => jvm.Type.Qualified("java.util.Map")
      case AvroType.Named(fullName)                                                                     => jvm.Type.Qualified(jvm.QIdent(fullName))
      case AvroType.Record(r)                                                                           => jvm.Type.Qualified(jvm.QIdent(r.fullName))
      case AvroType.EnumType(e)                                                                         => jvm.Type.Qualified(jvm.QIdent(e.fullName))
      case AvroType.Fixed(_)                                                                            => lang.ByteArrayType
      case _                                                                                            => jvm.Type.Qualified("java.lang.Object")
    }
  }

  /** Get the name part for type-related methods (isXxx, asXxx) */
  private def getTypeNamePart(member: AvroType): String = member match {
    case AvroType.Boolean                                                                             => "Boolean"
    case AvroType.Int                                                                                 => "Int"
    case AvroType.Long                                                                                => "Long"
    case AvroType.Float                                                                               => "Float"
    case AvroType.Double                                                                              => "Double"
    case AvroType.Bytes                                                                               => "Bytes"
    case AvroType.String                                                                              => "String"
    case AvroType.UUID                                                                                => "UUID"
    case AvroType.Date                                                                                => "Date"
    case AvroType.TimeMillis | AvroType.TimeMicros | AvroType.TimeNanos                               => "Time"
    case AvroType.TimestampMillis | AvroType.TimestampMicros | AvroType.TimestampNanos                => "Timestamp"
    case AvroType.LocalTimestampMillis | AvroType.LocalTimestampMicros | AvroType.LocalTimestampNanos => "LocalTimestamp"
    case _: AvroType.DecimalBytes                                                                     => "Decimal"
    case _: AvroType.DecimalFixed                                                                     => "Decimal"
    case AvroType.Duration                                                                            => "Duration"
    case AvroType.Array(_)                                                                            => "Array"
    case AvroType.Map(_)                                                                              => "Map"
    case AvroType.Named(fullName)                                                                     => fullName.split('.').last
    case AvroType.Record(r)                                                                           => r.name
    case AvroType.EnumType(e)                                                                         => e.name
    case AvroType.Fixed(f)                                                                            => f.name
    case AvroType.Null                                                                                => "Null"
    case AvroType.Union(_)                                                                            => "Union"
  }

  /** Convert enum value to string for Avro serialization. Java/Kotlin: .name() - uses built-in enum method Scala: .value - uses the value property from sealed abstract class
    */
  private def enumToString(expr: jvm.Code): jvm.Code = lang match {
    case _: LangScala  => expr.select("value")
    case _: LangKotlin => expr.select("name")
    case _             => expr.invoke("name")
  }

  /** Create enum from string for Avro deserialization. Java/Kotlin: EnumType.valueOf(str) - uses built-in enum method Scala: EnumType.force(str) - uses the force method from sealed abstract class
    */
  private def enumFromString(enumType: jvm.Type.Qualified, strExpr: jvm.Code): jvm.Code = lang match {
    case _: LangScala => enumType.code.invoke("force", strExpr)
    case _            => enumType.code.invoke("valueOf", strExpr)
  }

  override def recordImports: List[jvm.Type.Qualified] = List(
    SchemaType,
    GenericRecordType,
    GenericDataRecordType,
    GenericEnumSymbolType
  )

  override def schemaField(record: AvroRecord, jsonSchema: String): jvm.ClassMember = {
    val parserNew = SchemaParserType.construct()
    val parseCall = parserNew.invoke("parse", jvm.StrLit(jsonSchema).code)

    jvm.Value(
      annotations = Nil,
      name = jvm.Ident("SCHEMA"),
      tpe = SchemaType,
      body = Some(parseCall),
      isLazy = false,
      isOverride = false
    )
  }

  override def toGenericRecordMethod(record: AvroRecord): jvm.Method = {
    val recordIdent = jvm.Ident("record")
    val recordType = jvm.Type.Qualified(jvm.QIdent(record.fullName))
    val schemaRef = recordType.code.select("SCHEMA")

    val createRecord = jvm.LocalVar(
      name = recordIdent,
      tpe = Some(GenericDataRecordType),
      value = GenericDataRecordType.construct(schemaRef)
    )

    val putStatements = record.fields.map { field =>
      val fieldName = jvm.Ident(field.name)
      val valueExpr = convertToAvro(field, lang.prop(code"this", fieldName), schemaRef, record.namespace)
      jvm.Stmt.simple(recordIdent.code.invoke("put", jvm.StrLit(field.name).code, valueExpr))
    }

    val returnStmt = jvm.Return(recordIdent.code)

    val allStmts: List[jvm.Code] = (createRecord.code :: putStatements.map(_.code)) :+ returnStmt.code

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(List("Convert this record to a GenericRecord for serialization")),
      tparams = Nil,
      name = jvm.Ident("toGenericRecord"),
      params = Nil,
      implicitParams = Nil,
      tpe = GenericRecordType,
      throws = Nil,
      body = jvm.Body.Stmts(allStmts),
      isOverride = false,
      isDefault = false
    )
  }

  override def fromGenericRecordMethod(record: AvroRecord): jvm.ClassMember = {
    val recordParam = jvm.Param(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      name = jvm.Ident("record"),
      tpe = GenericRecordType,
      default = None
    )
    val recordType = jvm.Type.Qualified(jvm.QIdent(record.fullName))

    val fieldExtractions = record.fields.map { field =>
      val getExpr = recordParam.name.code.invoke("get", jvm.StrLit(field.name).code)
      convertFromAvro(field, getExpr, record.namespace)
    }

    val constructorArgs = fieldExtractions.map(jvm.Arg.Pos.apply)
    val body = jvm.New(recordType.code, constructorArgs)

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(List("Create a record from a GenericRecord (for deserialization)")),
      tparams = Nil,
      name = jvm.Ident("fromGenericRecord"),
      params = List(recordParam),
      implicitParams = Nil,
      tpe = recordType,
      throws = Nil,
      body = jvm.Body.Expr(body.code),
      isOverride = false,
      isDefault = false
    )
  }

  private def convertToAvro(field: AvroField, expr: jvm.Code, schemaRef: jvm.Code, recordNamespace: Option[String]): jvm.Code = {
    field.wrapperType match {
      case Some(wrapperName) =>
        val isNullable = field.fieldType match {
          case AvroType.Union(members) => members.contains(AvroType.Null)
          case _                       => false
        }
        if (isNullable) {
          val innerExpr = lang.Optional.getAfterCheck(expr)
          val unwrapped = lang.nullaryMethodCall(innerExpr, jvm.Ident("unwrap"))
          val nonNullType = AvroType.nonNullMembers(field.fieldType).head
          val innerField = field.copy(wrapperType = None, fieldType = nonNullType)
          val converted = convertToAvroInner(innerField, unwrapped, schemaRef, recordNamespace)
          jvm.IfExpr(lang.Optional.isEmpty(expr), code"null", converted).code
        } else {
          val unwrapped = lang.nullaryMethodCall(expr, jvm.Ident("unwrap"))
          val innerField = field.copy(wrapperType = None)
          convertToAvroInner(innerField, unwrapped, schemaRef, recordNamespace)
        }
      case None =>
        convertToAvroInner(field, expr, schemaRef, recordNamespace)
    }
  }

  private def convertToAvroInner(field: AvroField, expr: jvm.Code, schemaRef: jvm.Code, recordNamespace: Option[String]): jvm.Code = {
    field.fieldType match {
      case AvroType.UUID =>
        expr.invoke("toString")
      case AvroType.Date =>
        jvm.Cast(jvm.Type.Primitive("int"), expr.invoke("toEpochDay")).code
      case AvroType.TimeMillis =>
        jvm.Cast(jvm.Type.Primitive("int"), code"${expr.invoke("toNanoOfDay")} / 1000000L").code
      case AvroType.TimeMicros =>
        code"${expr.invoke("toNanoOfDay")} / 1000L"
      case AvroType.TimeNanos =>
        expr.invoke("toNanoOfDay")
      case AvroType.TimestampMillis =>
        expr.invoke("toEpochMilli")
      case AvroType.TimestampMicros =>
        code"${expr.invoke("getEpochSecond")} * 1000000L + ${expr.invoke("getNano")} / 1000L"
      case AvroType.TimestampNanos =>
        code"${expr.invoke("getEpochSecond")} * 1000000000L + ${expr.invoke("getNano")}"
      case AvroType.LocalTimestampMillis =>
        expr.invoke("toInstant", jvm.Type.Qualified("java.time.ZoneOffset").code.select("UTC")).invoke("toEpochMilli")
      case AvroType.LocalTimestampMicros =>
        val inst = expr.invoke("toInstant", jvm.Type.Qualified("java.time.ZoneOffset").code.select("UTC"))
        code"${inst.invoke("getEpochSecond")} * 1000000L + ${inst.invoke("getNano")} / 1000L"
      case AvroType.LocalTimestampNanos =>
        val inst = expr.invoke("toInstant", jvm.Type.Qualified("java.time.ZoneOffset").code.select("UTC"))
        code"${inst.invoke("getEpochSecond")} * 1000000000L + ${inst.invoke("getNano")}"
      case d: AvroType.DecimalBytes =>
        // Scale the BigDecimal to match the schema's scale before serializing
        // If precise types are enabled, we need to call decimalValue() to get the underlying BigDecimal
        val rawExpr = if (enablePreciseTypes) lang.nullaryMethodCall(expr, jvm.Ident("decimalValue")) else expr
        val roundingMode = jvm.Type.Qualified("java.math.RoundingMode").code.select("HALF_UP")
        val scaled = rawExpr.invoke("setScale", code"${d.scale}", roundingMode)
        jvm.Type.Qualified("java.nio.ByteBuffer").code.invoke("wrap", scaled.invoke("unscaledValue").invoke("toByteArray"))
      case d: AvroType.DecimalFixed =>
        // Scale the BigDecimal to match the schema's scale before serializing
        // If precise types are enabled, we need to call decimalValue() to get the underlying BigDecimal
        val rawExpr = if (enablePreciseTypes) lang.nullaryMethodCall(expr, jvm.Ident("decimalValue")) else expr
        val roundingMode = jvm.Type.Qualified("java.math.RoundingMode").code.select("HALF_UP")
        val scaled = rawExpr.invoke("setScale", code"${d.scale}", roundingMode)
        jvm.Type.Qualified("java.nio.ByteBuffer").code.invoke("wrap", scaled.invoke("unscaledValue").invoke("toByteArray"))
      case u @ AvroType.Union(members) if isComplexUnion(u) =>
        // Complex union: extract the wrapped value using isXxx/asXxx methods
        // Generate a chain of ternary expressions
        val nonNull = members.filterNot(_ == AvroType.Null)
        val hasNull = members.contains(AvroType.Null)
        val innerExpr = if (hasNull) lang.Optional.getAfterCheck(expr) else expr
        val extractedValue = nonNull.foldRight[jvm.Code](code"null") { (memberType, elseExpr) =>
          val typeName = getTypeNamePart(memberType)
          val isCheck = lang.nullaryMethodCall(innerExpr, jvm.Ident(s"is$typeName"))
          val asValue = lang.nullaryMethodCall(innerExpr, jvm.Ident(s"as$typeName"))
          jvm.IfExpr(isCheck, asValue, elseExpr).code
        }
        if (hasNull) {
          jvm.IfExpr(lang.Optional.isEmpty(expr), code"null", extractedValue).code
        } else {
          extractedValue
        }
      case AvroType.Union(members) if members.contains(AvroType.Null) =>
        val nonNull = members.filterNot(_ == AvroType.Null).head
        val innerField = field.copy(fieldType = nonNull)
        val innerExpr = lang.Optional.getAfterCheck(expr)
        val converted = convertToAvroInner(innerField, innerExpr, schemaRef, recordNamespace)
        jvm.IfExpr(lang.Optional.isEmpty(expr), code"null", converted).code
      case AvroType.Array(items) =>
        val itemField = AvroField(field.name, None, items, None, FieldOrder.Ignore, Nil, None)
        val e = jvm.Ident("e")
        val mapped = convertToAvroInner(itemField, e.code, schemaRef, recordNamespace)
        val lambda = jvm.Lambda(e, mapped)
        lang.ListType.toJavaList(lang.ListType.map(expr, lambda.code), jvm.Type.Wildcard)
      case AvroType.Map(values) =>
        expr // Maps of primitives pass through
      case AvroType.Named(fullName) if isEnum(fullName) =>
        // Create GenericData.EnumSymbol for proper Avro serialization
        val fieldSchemaRef = schemaRef.invoke("getField", jvm.StrLit(field.name).code).invoke("schema")
        GenericEnumSymbolType.construct(fieldSchemaRef, enumToString(expr))
      case AvroType.Named(_) =>
        lang.nullaryMethodCall(expr, jvm.Ident("toGenericRecord"))
      case AvroType.EnumType(_) =>
        // Create GenericData.EnumSymbol for proper Avro serialization
        val fieldSchemaRef = schemaRef.invoke("getField", jvm.StrLit(field.name).code).invoke("schema")
        GenericEnumSymbolType.construct(fieldSchemaRef, enumToString(expr))
      case _ =>
        expr // Primitives pass through directly
    }
  }

  private def convertFromAvro(field: AvroField, expr: jvm.Code, recordNamespace: Option[String]): jvm.Code = {
    field.wrapperType match {
      case Some(wrapperName) =>
        val wrapperType = wrapperTypeMap.getOrElse(
          (recordNamespace, wrapperName),
          wrapperTypeMap.getOrElse(
            (None, wrapperName),
            sys.error(s"Wrapper type $wrapperName not found")
          )
        )
        val isNullable = field.fieldType match {
          case AvroType.Union(members) => members.contains(AvroType.Null)
          case _                       => false
        }
        val innerField = field.copy(wrapperType = None)
        if (isNullable) {
          val converted = convertFromAvroInner(innerField.copy(fieldType = AvroType.nonNullMembers(field.fieldType).head), expr, recordNamespace)
          val wrapped = wrapperType.code.invoke("valueOf", converted)
          jvm.IfExpr(code"$expr == null", lang.Optional.none, lang.Optional.some(wrapped)).code
        } else {
          val converted = convertFromAvroInner(innerField, expr, recordNamespace)
          wrapperType.code.invoke("valueOf", converted)
        }
      case None =>
        convertFromAvroInner(field, expr, recordNamespace)
    }
  }

  private def convertFromAvroInner(field: AvroField, expr: jvm.Code, recordNamespace: Option[String]): jvm.Code = {
    val isKotlin = lang.isInstanceOf[LangKotlin]
    field.fieldType match {
      case AvroType.Boolean =>
        jvm.Cast(lang.Boolean, expr).code
      case AvroType.Int =>
        // Kotlin: cast to Int directly (Kotlin handles Java Integer -> Int conversion)
        // Java/Scala: cast to java.lang.Integer
        if (isKotlin) jvm.Cast(lang.Int, expr).code
        else jvm.Cast(jvm.Type.Qualified("java.lang.Integer"), expr).code
      case AvroType.Long =>
        if (isKotlin) jvm.Cast(lang.Long, expr).code
        else jvm.Cast(jvm.Type.Qualified("java.lang.Long"), expr).code
      case AvroType.Float =>
        if (isKotlin) jvm.Cast(lang.Float, expr).code
        else jvm.Cast(jvm.Type.Qualified("java.lang.Float"), expr).code
      case AvroType.Double =>
        if (isKotlin) jvm.Cast(lang.Double, expr).code
        else jvm.Cast(jvm.Type.Qualified("java.lang.Double"), expr).code
      case AvroType.String =>
        expr.invoke("toString")
      case AvroType.Bytes =>
        jvm.Cast(lang.ByteArrayType, expr).code
      case AvroType.UUID =>
        jvm.Type.Qualified("java.util.UUID").code.invoke("fromString", expr.invoke("toString"))
      case AvroType.Date =>
        jvm.Type.Qualified("java.time.LocalDate").code.invoke("ofEpochDay", jvm.Cast(jvm.Type.Qualified("java.lang.Integer"), expr).code)
      case AvroType.TimeMillis =>
        jvm.Type.Qualified("java.time.LocalTime").code.invoke("ofNanoOfDay", code"${jvm.Cast(jvm.Type.Qualified("java.lang.Integer"), expr).code} * 1000000L")
      case AvroType.TimeMicros =>
        jvm.Type.Qualified("java.time.LocalTime").code.invoke("ofNanoOfDay", code"${jvm.Cast(jvm.Type.Qualified("java.lang.Long"), expr).code} * 1000L")
      case AvroType.TimeNanos =>
        jvm.Type.Qualified("java.time.LocalTime").code.invoke("ofNanoOfDay", jvm.Cast(jvm.Type.Qualified("java.lang.Long"), expr).code)
      case AvroType.TimestampMillis =>
        jvm.Type.Qualified("java.time.Instant").code.invoke("ofEpochMilli", jvm.Cast(jvm.Type.Qualified("java.lang.Long"), expr).code)
      case AvroType.TimestampMicros =>
        val longExpr = jvm.Cast(jvm.Type.Qualified("java.lang.Long"), expr).code
        val millis = code"$longExpr / 1000"
        val nanos = code"($longExpr % 1000) * 1000"
        jvm.Type.Qualified("java.time.Instant").code.invoke("ofEpochMilli", millis).invoke("plusNanos", nanos)
      case AvroType.TimestampNanos =>
        val longExpr = jvm.Cast(jvm.Type.Qualified("java.lang.Long"), expr).code
        val seconds = code"$longExpr / 1000000000L"
        val nanos = code"($longExpr % 1000000000L)"
        jvm.Type.Qualified("java.time.Instant").code.invoke("ofEpochSecond", seconds, nanos)
      case AvroType.LocalTimestampMillis =>
        val instant = jvm.Type.Qualified("java.time.Instant").code.invoke("ofEpochMilli", jvm.Cast(jvm.Type.Qualified("java.lang.Long"), expr).code)
        jvm.Type.Qualified("java.time.LocalDateTime").code.invoke("ofInstant", instant, jvm.Type.Qualified("java.time.ZoneOffset").code.select("UTC"))
      case AvroType.LocalTimestampMicros =>
        val longExpr = jvm.Cast(jvm.Type.Qualified("java.lang.Long"), expr).code
        val millis = code"$longExpr / 1000"
        val nanos = code"($longExpr % 1000) * 1000"
        val instant = jvm.Type.Qualified("java.time.Instant").code.invoke("ofEpochMilli", millis).invoke("plusNanos", nanos)
        jvm.Type.Qualified("java.time.LocalDateTime").code.invoke("ofInstant", instant, jvm.Type.Qualified("java.time.ZoneOffset").code.select("UTC"))
      case AvroType.LocalTimestampNanos =>
        val longExpr = jvm.Cast(jvm.Type.Qualified("java.lang.Long"), expr).code
        val seconds = code"$longExpr / 1000000000L"
        val nanos = code"($longExpr % 1000000000L)"
        val instant = jvm.Type.Qualified("java.time.Instant").code.invoke("ofEpochSecond", seconds, nanos)
        jvm.Type.Qualified("java.time.LocalDateTime").code.invoke("ofInstant", instant, jvm.Type.Qualified("java.time.ZoneOffset").code.select("UTC"))
      case d: AvroType.DecimalBytes =>
        // Avro returns ByteBuffer for bytes type, extract the byte array
        val byteBuffer = jvm.Cast(jvm.Type.Qualified("java.nio.ByteBuffer"), expr).code
        val bigIntBytes = byteBuffer.invoke("array")
        val bigInt = jvm.Type.Qualified("java.math.BigInteger").construct(bigIntBytes)
        val bigDecimal = jvm.Type.Qualified("java.math.BigDecimal").construct(bigInt, code"${d.scale}")
        // If precise types are enabled, wrap in the precise type using unsafeForce
        if (enablePreciseTypes) {
          naming match {
            case Some(n) =>
              val preciseType = jvm.Type.Qualified(n.preciseDecimalNName(d.precision, d.scale))
              preciseType.code.invoke("unsafeForce", bigDecimal)
            case None => bigDecimal
          }
        } else bigDecimal
      case d: AvroType.DecimalFixed =>
        // Avro returns ByteBuffer for bytes type, extract the byte array
        val byteBuffer = jvm.Cast(jvm.Type.Qualified("java.nio.ByteBuffer"), expr).code
        val bigIntBytes = byteBuffer.invoke("array")
        val bigInt = jvm.Type.Qualified("java.math.BigInteger").construct(bigIntBytes)
        val bigDecimal = jvm.Type.Qualified("java.math.BigDecimal").construct(bigInt, code"${d.scale}")
        // If precise types are enabled, wrap in the precise type using unsafeForce
        if (enablePreciseTypes) {
          naming match {
            case Some(n) =>
              val preciseType = jvm.Type.Qualified(n.preciseDecimalNName(d.precision, d.scale))
              preciseType.code.invoke("unsafeForce", bigDecimal)
            case None => bigDecimal
          }
        } else bigDecimal
      case u @ AvroType.Union(members) if isComplexUnion(u) =>
        // Complex union: check runtime type and wrap in the appropriate wrapper
        val nonNull = members.filterNot(_ == AvroType.Null)
        val hasNull = members.contains(AvroType.Null)
        val normalized = normalizeUnion(u)
        unionTypeNames.get(normalized) match {
          case Some(unionType) =>
            // Fallback for when no type matches
            // For Kotlin/Scala, throw is an expression; for Java, use null and wrap with requireNonNull
            val isJava = !lang.isInstanceOf[LangScala] && !lang.isInstanceOf[LangKotlin]
            val fallback = if (hasNull) {
              code"null"
            } else if (isJava) {
              code"null" // Java will use Objects.requireNonNull wrapper
            } else {
              val errorMsg = jvm.StrLit("Unknown union type").code
              jvm.Throw(jvm.Type.Qualified("java.lang.IllegalArgumentException").construct(errorMsg)).code
            }
            // Generate instanceof chain to wrap the value
            val wrapExpr = nonNull.foldRight[jvm.Code](fallback) { (memberType, elseExpr) =>
              val javaType = mapAvroTypeToInstanceOfType(memberType)
              val instanceCheck = instanceOfCheck(expr, javaType)
              val castExpr = jvm.Cast(javaType, expr).code
              // For strings, call toString() to convert from CharSequence/Utf8 to String
              val valueExpr = memberType match {
                case AvroType.String => castExpr.invoke("toString")
                case _               => castExpr
              }
              val wrapped = unionType.code.invoke("of", valueExpr)
              jvm.IfExpr(instanceCheck, wrapped, elseExpr).code
            }
            if (hasNull) {
              val nullCheck = jvm.IfExpr(code"$expr == null", code"null", wrapExpr)
              lang.Optional.ofNullable(nullCheck.code)
            } else if (isJava) {
              // For Java non-nullable unions, wrap with requireNonNull
              val errorMsg = jvm.StrLit("Unknown union type").code
              jvm.Type.Qualified("java.util.Objects").code.invoke("requireNonNull", wrapExpr, errorMsg)
            } else {
              wrapExpr
            }
          case None =>
            // Fallback if union type wasn't generated
            expr
        }
      case AvroType.Union(members) if members.contains(AvroType.Null) =>
        val nonNull = members.filterNot(_ == AvroType.Null).head
        val innerField = field.copy(fieldType = nonNull)
        val converted = convertFromAvroInner(innerField, expr, recordNamespace)
        val nullCheck = jvm.IfExpr(code"$expr == null", code"null", converted)
        lang.Optional.ofNullable(nullCheck.code)
      case AvroType.Array(items) =>
        val itemField = AvroField(field.name, None, items, None, FieldOrder.Ignore, Nil, None)
        val e = jvm.Ident("e")
        val mapped = convertFromAvroInner(itemField, e.code, recordNamespace)
        val lambda = jvm.Lambda(e, mapped)
        val listCast = jvm.Cast(jvm.Type.Qualified("java.util.List").of(jvm.Type.Wildcard), expr).code
        val nativeList = lang.ListType.fromJavaList(listCast, jvm.Type.Wildcard)
        lang.ListType.map(nativeList, lambda.code)
      case AvroType.Map(values) =>
        jvm.Cast(jvm.Type.Qualified("java.util.Map").of(lang.String, jvm.Type.Wildcard), expr).code
      case AvroType.Named(fullName) if isEnum(fullName) =>
        val typeName = jvm.Type.Qualified(jvm.QIdent(fullName))
        enumFromString(typeName, expr.invoke("toString"))
      case AvroType.Named(fullName) =>
        val typeName = jvm.Type.Qualified(jvm.QIdent(fullName))
        typeName.code.invoke("fromGenericRecord", jvm.Cast(GenericRecordType, expr).code)
      case AvroType.EnumType(avroEnum) =>
        val typeName = jvm.Type.Qualified(jvm.QIdent(avroEnum.fullName))
        enumFromString(typeName, expr.invoke("toString"))
      case _ =>
        expr
    }
  }

  override def dependencies: List[KafkaDependency] = List(
    KafkaDependency("org.apache.kafka", "kafka-clients", "3.9.0"),
    KafkaDependency("org.apache.avro", "avro", "1.12.0"),
    KafkaDependency("io.confluent", "kafka-avro-serializer", "7.8.0")
  )
}

/** Binary encoded Avro (no schema registry) code generation */
class BinaryEncodedCodegen(
    val lang: Lang,
    enumNames: Set[String],
    unionTypeNames: Map[AvroType.Union, jvm.Type.Qualified],
    naming: Option[typr.Naming],
    enablePreciseTypes: Boolean,
    wrapperTypeMap: Map[(Option[String], String), jvm.Type.Qualified]
) extends AvroWireFormatSupport {

  private val delegate = new ConfluentRegistryCodegen(lang, enumNames, unionTypeNames, naming, enablePreciseTypes, wrapperTypeMap)

  override def recordImports: List[jvm.Type.Qualified] = delegate.recordImports

  override def schemaField(record: AvroRecord, jsonSchema: String): jvm.ClassMember =
    delegate.schemaField(record, jsonSchema)

  override def toGenericRecordMethod(record: AvroRecord): jvm.Method =
    delegate.toGenericRecordMethod(record)

  override def fromGenericRecordMethod(record: AvroRecord): jvm.ClassMember =
    delegate.fromGenericRecordMethod(record)

  override def dependencies: List[KafkaDependency] = List(
    KafkaDependency("org.apache.kafka", "kafka-clients", "3.9.0"),
    KafkaDependency("org.apache.avro", "avro", "1.12.0")
  )
}

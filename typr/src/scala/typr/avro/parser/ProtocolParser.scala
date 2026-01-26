package typr.avro.parser

import org.apache.avro.{Protocol, Schema}
import typr.avro._

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._
import scala.reflect.Selectable.reflectiveSelectable

/** Parser for Avro protocol files (.avpr) using Apache Avro library */
object ProtocolParser {

  /** Parse all .avpr files from a directory */
  def parseDirectory(directory: Path): Either[AvroParseError, List[AvroProtocol]] = {
    if (!Files.isDirectory(directory)) {
      Left(AvroParseError.DirectoryNotFound(directory.toString))
    } else {
      val avprFiles = Files
        .walk(directory)
        .filter(p => Files.isRegularFile(p) && p.toString.endsWith(".avpr"))
        .iterator()
        .asScala
        .toList
        .sortBy(_.toString)

      val results = avprFiles.map { file =>
        parseFile(file).left.map(e => (file, e))
      }

      val errors = results.collect { case Left((file, error)) => s"${file}: ${error.message}" }
      if (errors.nonEmpty) {
        Left(AvroParseError.MultipleErrors(errors))
      } else {
        Right(results.collect { case Right(protocol) => protocol })
      }
    }
  }

  /** Parse a single .avpr file */
  def parseFile(file: Path): Either[AvroParseError, AvroProtocol] = {
    try {
      val content = Files.readString(file)
      parseProtocol(content, Some(file.toString))
    } catch {
      case e: Exception => Left(AvroParseError.FileReadError(file.toString, e.getMessage))
    }
  }

  /** Parse Avro protocol from JSON string */
  def parseProtocol(json: String, sourcePath: Option[String]): Either[AvroParseError, AvroProtocol] = {
    try {
      val protocol = Protocol.parse(json)
      Right(convertProtocol(protocol))
    } catch {
      case e: org.apache.avro.SchemaParseException =>
        Left(AvroParseError.SchemaParseError(e.getMessage))
      case e: Exception =>
        Left(AvroParseError.UnexpectedError(e.getMessage))
    }
  }

  /** Convert Apache Avro Protocol to our internal representation */
  private def convertProtocol(protocol: Protocol): AvroProtocol = {
    val types = protocol.getTypes.asScala.toList.map(convertNamedSchema)
    val messages = protocol.getMessages.asScala.toList.map { case (name, msg) =>
      convertMessage(name, msg)
    }

    AvroProtocol(
      name = protocol.getName,
      namespace = Option(protocol.getNamespace),
      doc = Option(protocol.getDoc),
      types = types,
      messages = messages
    )
  }

  /** Convert a named schema (record, enum, fixed, error) */
  private def convertNamedSchema(schema: Schema): AvroSchema = {
    schema.getType match {
      case Schema.Type.RECORD =>
        if (schema.isError) {
          convertErrorSchema(schema)
        } else {
          convertRecordSchema(schema)
        }
      case Schema.Type.ENUM =>
        convertEnumSchema(schema)
      case Schema.Type.FIXED =>
        convertFixedSchema(schema)
      case other =>
        throw new IllegalArgumentException(s"Expected named type but got: $other")
    }
  }

  /** Convert a record schema */
  private def convertRecordSchema(schema: Schema): AvroRecord = {
    val fields = schema.getFields.asScala.toList.map(convertField)
    AvroRecord(
      name = schema.getName,
      namespace = Option(schema.getNamespace),
      doc = Option(schema.getDoc),
      fields = fields,
      aliases = schema.getAliases.asScala.toList
    )
  }

  /** Convert an error schema */
  private def convertErrorSchema(schema: Schema): AvroError = {
    val fields = schema.getFields.asScala.toList.map(convertField)
    AvroError(
      name = schema.getName,
      namespace = Option(schema.getNamespace),
      doc = Option(schema.getDoc),
      fields = fields,
      aliases = schema.getAliases.asScala.toList
    )
  }

  /** Convert an enum schema */
  private def convertEnumSchema(schema: Schema): AvroEnum =
    AvroEnum(
      name = schema.getName,
      namespace = Option(schema.getNamespace),
      doc = Option(schema.getDoc),
      symbols = schema.getEnumSymbols.asScala.toList,
      defaultSymbol = Option(schema.getEnumDefault),
      aliases = schema.getAliases.asScala.toList
    )

  /** Convert a fixed schema */
  private def convertFixedSchema(schema: Schema): AvroFixed =
    AvroFixed(
      name = schema.getName,
      namespace = Option(schema.getNamespace),
      doc = Option(schema.getDoc),
      size = schema.getFixedSize,
      aliases = schema.getAliases.asScala.toList
    )

  /** Convert a field */
  private def convertField(field: Schema.Field): AvroField = {
    AvroField(
      name = field.name(),
      doc = Option(field.doc()),
      fieldType = convertType(field.schema()),
      defaultValue = if (field.hasDefaultValue) Some(defaultValueToJson(field.defaultVal())) else None,
      order = convertOrder(field.order()),
      aliases = field.aliases().asScala.toList,
      wrapperType = extractWrapperType(field)
    )
  }

  /** Extract x-typr-wrapper attribute from a field's custom properties */
  private def extractWrapperType(field: Schema.Field): Option[String] = {
    val props = field.getObjectProps
    if (props != null) Option(props.get("x-typr-wrapper")).map(_.toString)
    else None
  }

  /** Convert a message. Uses AnyRef to avoid Scala/Java nested class syntax issues. */
  private def convertMessage(name: String, message: AnyRef): AvroMessage = {
    // Protocol.Message is a nested Java class, access via reflection-like duck typing
    val protoMessage = message.asInstanceOf[{
        def getRequest(): Schema
        def getResponse(): Schema
        def getErrors(): Schema
        def getDoc(): String
        def isOneWay(): Boolean
      }
    ]

    val request = protoMessage.getRequest().getFields.asScala.toList.map(convertField)
    val response = convertType(protoMessage.getResponse())
    val errors = protoMessage
      .getErrors()
      .getTypes
      .asScala
      .toList
      .filterNot(_.getType == Schema.Type.STRING) // Filter out the implicit string error
      .map(convertType)

    AvroMessage(
      name = name,
      doc = Option(protoMessage.getDoc()),
      request = request,
      response = response,
      errors = errors,
      oneWay = protoMessage.isOneWay()
    )
  }

  /** Convert Apache Avro Schema to AvroType */
  private def convertType(schema: Schema): AvroType = {
    val logicalType = Option(schema.getLogicalType).map(_.getName)

    schema.getType match {
      case Schema.Type.NULL    => AvroType.Null
      case Schema.Type.BOOLEAN => AvroType.Boolean
      case Schema.Type.INT =>
        logicalType match {
          case Some("date")        => AvroType.Date
          case Some("time-millis") => AvroType.TimeMillis
          case _                   => AvroType.Int
        }
      case Schema.Type.LONG =>
        logicalType match {
          case Some("time-micros")            => AvroType.TimeMicros
          case Some("time-nanos")             => AvroType.TimeNanos
          case Some("timestamp-millis")       => AvroType.TimestampMillis
          case Some("timestamp-micros")       => AvroType.TimestampMicros
          case Some("timestamp-nanos")        => AvroType.TimestampNanos
          case Some("local-timestamp-millis") => AvroType.LocalTimestampMillis
          case Some("local-timestamp-micros") => AvroType.LocalTimestampMicros
          case Some("local-timestamp-nanos")  => AvroType.LocalTimestampNanos
          case _                              => AvroType.Long
        }
      case Schema.Type.FLOAT  => AvroType.Float
      case Schema.Type.DOUBLE => AvroType.Double
      case Schema.Type.BYTES =>
        logicalType match {
          case Some("decimal") =>
            val precision = schema.getObjectProp("precision").asInstanceOf[java.lang.Integer].intValue()
            val scale = schema.getObjectProp("scale").asInstanceOf[java.lang.Integer].intValue()
            AvroType.DecimalBytes(precision, scale)
          case _ => AvroType.Bytes
        }
      case Schema.Type.STRING =>
        logicalType match {
          case Some("uuid") => AvroType.UUID
          case _            => AvroType.String
        }
      case Schema.Type.ARRAY =>
        AvroType.Array(convertType(schema.getElementType))
      case Schema.Type.MAP =>
        AvroType.Map(convertType(schema.getValueType))
      case Schema.Type.UNION =>
        val members = schema.getTypes.asScala.toList.map(convertType)
        AvroType.Union(members)
      case Schema.Type.RECORD =>
        AvroType.Named(schema.getFullName)
      case Schema.Type.ENUM =>
        AvroType.Named(schema.getFullName)
      case Schema.Type.FIXED =>
        logicalType match {
          case Some("decimal") =>
            val precision = schema.getObjectProp("precision").asInstanceOf[java.lang.Integer].intValue()
            val scale = schema.getObjectProp("scale").asInstanceOf[java.lang.Integer].intValue()
            AvroType.DecimalFixed(precision, scale, schema.getFixedSize)
          case Some("duration") =>
            AvroType.Duration
          case _ =>
            AvroType.Named(schema.getFullName)
        }
    }
  }

  private def convertOrder(order: Schema.Field.Order): FieldOrder = order match {
    case Schema.Field.Order.ASCENDING  => FieldOrder.Ascending
    case Schema.Field.Order.DESCENDING => FieldOrder.Descending
    case Schema.Field.Order.IGNORE     => FieldOrder.Ignore
  }

  /** Convert an Avro default value to proper JSON string */
  private def defaultValueToJson(value: AnyRef): String = {
    import org.apache.avro.JsonProperties
    value match {
      case JsonProperties.NULL_VALUE => "null"
      case s: java.lang.String       => s""""${escapeJsonString(s)}""""
      case n: java.lang.Number       => n.toString
      case b: java.lang.Boolean      => b.toString
      case m: java.util.Map[_, _] =>
        val entries = m.asScala.map { case (k, v) => s""""$k": ${defaultValueToJson(v.asInstanceOf[AnyRef])}""" }
        s"{${entries.mkString(", ")}}"
      case l: java.util.List[_] =>
        val items = l.asScala.map(v => defaultValueToJson(v.asInstanceOf[AnyRef]))
        s"[${items.mkString(", ")}]"
      case null  => "null"
      case other => other.toString
    }
  }

  private def escapeJsonString(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
}

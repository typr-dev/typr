package typr.avro.parser

import org.apache.avro.{JsonProperties, Schema}
import typr.avro._

import java.nio.file.{Files, Path}
import scala.collection.mutable
import scala.jdk.CollectionConverters._

/** Parser for Avro schema files (.avsc) using Apache Avro library */
object AvroParser {

  /** Convert an Avro default value to proper JSON string. Handles the special JsonProperties.NULL case for null defaults.
    */
  private def defaultValueToJson(value: AnyRef): String = {
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

  /** Parse all .avsc files from a directory.
    *
    * Uses a shared Schema.Parser to allow cross-file references. Files are parsed with dependency ordering to ensure referenced schemas are parsed before their dependents.
    *
    * For directory-based sum types: schemas in subdirectories are automatically grouped. For example:
    *   - schemas/order-events/OrderPlaced.avsc → directoryGroup = Some("order-events")
    *   - schemas/Address.avsc → directoryGroup = None
    *
    * Supports `$ref` syntax for cross-file references:
    *   - `{"$ref": "./Address.avsc"}` - relative to current file
    *   - `{"$ref": "../common/Address.avsc"}` - relative path
    */
  def parseDirectory(directory: Path): Either[AvroParseError, List[AvroSchemaFile]] = {
    if (!Files.isDirectory(directory)) {
      Left(AvroParseError.DirectoryNotFound(directory.toString))
    } else {
      val avscFiles = Files
        .walk(directory)
        .filter(p => Files.isRegularFile(p) && p.toString.endsWith(".avsc"))
        .iterator()
        .asScala
        .toList
        .map(_.toAbsolutePath.normalize())
        .sortBy(_.toString)

      // First pass: read all files
      val fileContents: Either[AvroParseError, Map[Path, String]] = {
        val contents = avscFiles.map { file =>
          try {
            Right(file -> Files.readString(file))
          } catch {
            case e: Exception =>
              Left(AvroParseError.FileReadError(file.toString, e.getMessage))
          }
        }
        contents.collectFirst { case Left(e) => e } match {
          case Some(error) => Left(error)
          case None        => Right(contents.collect { case Right(pair) => pair }.toMap)
        }
      }

      fileContents.flatMap { contents =>
        // Resolve $refs and collect dependencies
        val resolving = mutable.Set.empty[Path]
        val resolutionResults = contents.map { case (file, content) =>
          if (RefResolver.containsRefs(content)) {
            RefResolver.resolve(file, content, resolving).map(result => (file, result.json, result.dependencies))
          } else {
            Right((file, content, Set.empty[Path]))
          }
        }

        resolutionResults.collectFirst { case Left(e) => e } match {
          case Some(error) => Left(error)
          case None =>
            val resolved = resolutionResults.collect { case Right(r) => r }
            val resolvedContents = resolved.map { case (file, json, _) => file -> json }.toMap
            val dependencies = resolved.map { case (file, _, deps) => file -> deps }.toMap

            // Topologically sort files by dependencies (dependencies parsed first)
            val sortedFiles = topologicalSort(avscFiles, dependencies)

            // Parse files in dependency order
            val sharedParser = new Schema.Parser()
            val results = sortedFiles.map { file =>
              val json = resolvedContents(file)
              val directoryGroup = determineDirectoryGroup(directory, file)

              parseSchemaWithParser(json, Some(file.toString), sharedParser, directoryGroup, schemaRole = SchemaRole.Value) match {
                case Right(schemaFile) => Right(schemaFile)
                case Left(error)       => Left(s"$file: ${error.message}")
              }
            }

            val errors = results.collect { case Left(error) => error }
            if (errors.nonEmpty) {
              Left(AvroParseError.MultipleErrors(errors))
            } else {
              Right(results.collect { case Right(schema) => schema })
            }
        }
      }
    }
  }

  /** Topological sort of files based on their dependencies.
    *
    * Returns files ordered so that dependencies come before dependents.
    */
  private def topologicalSort(files: List[Path], dependencies: Map[Path, Set[Path]]): List[Path] = {
    val result = mutable.ListBuffer.empty[Path]
    val visited = mutable.Set.empty[Path]
    val inProgress = mutable.Set.empty[Path]

    def visit(file: Path): Unit = {
      if (!visited.contains(file) && !inProgress.contains(file)) {
        inProgress += file

        // Visit dependencies first
        for (dep <- dependencies.getOrElse(file, Set.empty)) {
          visit(dep)
        }

        inProgress -= file
        visited += file
        result += file
      }
    }

    for (file <- files) {
      visit(file)
    }

    result.toList
  }

  /** Determine the directory group for a schema file.
    *
    * Returns the name of the immediate subdirectory if the file is in a subdirectory, or None if it's in the root directory.
    */
  private def determineDirectoryGroup(rootDirectory: Path, file: Path): Option[String] = {
    val relativePath = rootDirectory.relativize(file.getParent)
    if (relativePath.getNameCount == 0 || relativePath.toString.isEmpty) {
      None
    } else {
      // Take only the first level subdirectory name
      Some(relativePath.getName(0).toString)
    }
  }

  /** Parse a single .avsc file, resolving any $ref references.
    *
    * Note: For single file parsing with $ref, the referenced files must be parseable by the shared parser. If parsing fails, ensure all referenced schemas are available.
    */
  def parseFile(file: Path): Either[AvroParseError, AvroSchemaFile] = {
    try {
      val content = Files.readString(file)
      val normalizedPath = file.toAbsolutePath.normalize()
      val sharedParser = new Schema.Parser()

      // If there are $refs, resolve them and parse dependencies first
      if (RefResolver.containsRefs(content)) {
        val resolving = mutable.Set.empty[Path]
        RefResolver.resolve(normalizedPath, content, resolving).flatMap { result =>
          // Parse dependencies first (recursively)
          parseDependencies(result.dependencies.toList.sortBy(_.toString), sharedParser, mutable.Set.empty).flatMap { _ =>
            parseSchemaWithParser(result.json, Some(file.toString), sharedParser, directoryGroup = None, schemaRole = SchemaRole.Value)
          }
        }
      } else {
        parseSchemaWithParser(content, Some(file.toString), sharedParser, directoryGroup = None, schemaRole = SchemaRole.Value)
      }
    } catch {
      case e: Exception => Left(AvroParseError.FileReadError(file.toString, e.getMessage))
    }
  }

  /** Parse a list of dependency files and add them to the shared parser */
  private def parseDependencies(deps: List[Path], sharedParser: Schema.Parser, visited: mutable.Set[Path]): Either[AvroParseError, Unit] = {
    deps.foldLeft[Either[AvroParseError, Unit]](Right(())) { (acc, dep) =>
      acc.flatMap(_ => parseDependency(dep, sharedParser, visited))
    }
  }

  /** Parse a dependency file and add it to the shared parser */
  private def parseDependency(file: Path, sharedParser: Schema.Parser, visited: mutable.Set[Path]): Either[AvroParseError, Unit] = {
    val normalizedPath = file.toAbsolutePath.normalize()
    if (visited.contains(normalizedPath)) {
      Right(()) // Already parsed
    } else {
      visited += normalizedPath

      try {
        val content = Files.readString(file)

        // Resolve $refs in this dependency and parse transitive dependencies
        val resolvedJson = if (RefResolver.containsRefs(content)) {
          val resolving = mutable.Set.empty[Path]
          RefResolver.resolve(normalizedPath, content, resolving).flatMap { result =>
            // Parse transitive dependencies first
            parseDependencies(result.dependencies.toList.sortBy(_.toString), sharedParser, visited).map(_ => result.json)
          }
        } else {
          Right(content)
        }

        resolvedJson.flatMap { json =>
          // Parse into the shared parser (this adds the types to the parser's cache)
          try {
            sharedParser.parse(json)
            Right(())
          } catch {
            case e: org.apache.avro.SchemaParseException =>
              Left(AvroParseError.SchemaParseError(e.getMessage))
            case e: Exception =>
              Left(AvroParseError.UnexpectedError(e.getMessage))
          }
        }
      } catch {
        case e: Exception =>
          Left(AvroParseError.FileReadError(file.toString, e.getMessage))
      }
    }
  }

  /** Parse Avro schema from JSON string */
  def parseSchema(json: String, sourcePath: Option[String]): Either[AvroParseError, AvroSchemaFile] = {
    parseSchemaWithParser(json, sourcePath, new Schema.Parser(), directoryGroup = None, schemaRole = SchemaRole.Value)
  }

  /** Parse Avro schema from JSON string with explicit schema role */
  def parseSchemaWithRole(json: String, sourcePath: Option[String], schemaRole: SchemaRole): Either[AvroParseError, AvroSchemaFile] = {
    parseSchemaWithParser(json, sourcePath, new Schema.Parser(), directoryGroup = None, schemaRole = schemaRole)
  }

  /** Parse Avro schema from JSON string using a shared parser (for cross-file references) */
  private def parseSchemaWithParser(json: String, sourcePath: Option[String], parser: Schema.Parser, directoryGroup: Option[String], schemaRole: SchemaRole): Either[AvroParseError, AvroSchemaFile] = {
    try {
      val schema = parser.parse(json)
      val (primary, inlines) = convertSchema(schema)
      Right(AvroSchemaFile(primary, inlines, sourcePath, directoryGroup, schemaRole, version = None))
    } catch {
      case e: org.apache.avro.SchemaParseException =>
        Left(AvroParseError.SchemaParseError(e.getMessage))
      case e: Exception =>
        Left(AvroParseError.UnexpectedError(e.getMessage))
    }
  }

  /** Convert Apache Avro Schema to our internal representation */
  private def convertSchema(schema: Schema): (AvroSchema, List[AvroSchema]) = {
    val inlineSchemas = scala.collection.mutable.ListBuffer.empty[AvroSchema]
    val processing = scala.collection.mutable.Set.empty[String]

    def convert(s: Schema): AvroSchema = s.getType match {
      case Schema.Type.RECORD =>
        // Track this record to detect self-references in fields
        processing += s.getFullName
        val fields = s.getFields.asScala.toList.map { f =>
          AvroField(
            name = f.name(),
            doc = Option(f.doc()),
            fieldType = convertType(f.schema(), inlineSchemas, processing),
            defaultValue = if (f.hasDefaultValue) Some(defaultValueToJson(f.defaultVal())) else None,
            order = convertOrder(f.order()),
            aliases = f.aliases().asScala.toList,
            wrapperType = extractWrapperType(f)
          )
        }
        processing -= s.getFullName
        AvroRecord(
          name = s.getName,
          namespace = Option(s.getNamespace),
          doc = Option(s.getDoc),
          fields = fields,
          aliases = s.getAliases.asScala.toList
        )

      case Schema.Type.ENUM =>
        AvroEnum(
          name = s.getName,
          namespace = Option(s.getNamespace),
          doc = Option(s.getDoc),
          symbols = s.getEnumSymbols.asScala.toList,
          defaultSymbol = Option(s.getEnumDefault),
          aliases = s.getAliases.asScala.toList
        )

      case Schema.Type.FIXED =>
        AvroFixed(
          name = s.getName,
          namespace = Option(s.getNamespace),
          doc = Option(s.getDoc),
          size = s.getFixedSize,
          aliases = s.getAliases.asScala.toList
        )

      case other =>
        throw new IllegalArgumentException(s"Expected named type (record, enum, fixed) but got: $other")
    }

    val primary = convert(schema)
    (primary, inlineSchemas.toList)
  }

  /** Convert Apache Avro Schema to AvroType (for field types, etc.)
    *
    * @param schema
    *   The Apache Avro schema to convert
    * @param inlineSchemas
    *   Buffer to collect inline schema definitions
    * @param processing
    *   Set of schema full names currently being processed (for cycle detection)
    */
  private def convertType(
      schema: Schema,
      inlineSchemas: scala.collection.mutable.ListBuffer[AvroSchema],
      processing: scala.collection.mutable.Set[String] = scala.collection.mutable.Set.empty
  ): AvroType = {
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
        AvroType.Array(convertType(schema.getElementType, inlineSchemas, processing))
      case Schema.Type.MAP =>
        AvroType.Map(convertType(schema.getValueType, inlineSchemas, processing))
      case Schema.Type.UNION =>
        val members = schema.getTypes.asScala.toList.map(t => convertType(t, inlineSchemas, processing))
        AvroType.Union(members)
      case Schema.Type.RECORD =>
        val fullName = schema.getFullName
        // Check for recursive reference - if we're already processing this schema, just return a Named reference
        if (processing.contains(fullName)) {
          AvroType.Named(fullName)
        } else {
          processing += fullName
          val record = convertRecordSchema(schema, inlineSchemas, processing)
          processing -= fullName
          inlineSchemas += record
          AvroType.Named(record.fullName)
        }
      case Schema.Type.ENUM =>
        val avroEnum = convertEnumSchema(schema)
        inlineSchemas += avroEnum
        AvroType.Named(avroEnum.fullName)
      case Schema.Type.FIXED =>
        logicalType match {
          case Some("decimal") =>
            val precision = schema.getObjectProp("precision").asInstanceOf[java.lang.Integer].intValue()
            val scale = schema.getObjectProp("scale").asInstanceOf[java.lang.Integer].intValue()
            AvroType.DecimalFixed(precision, scale, schema.getFixedSize)
          case Some("duration") =>
            AvroType.Duration
          case _ =>
            val fixed = convertFixedSchema(schema)
            inlineSchemas += fixed
            AvroType.Named(fixed.fullName)
        }
    }
  }

  private def convertRecordSchema(
      schema: Schema,
      inlineSchemas: scala.collection.mutable.ListBuffer[AvroSchema],
      processing: scala.collection.mutable.Set[String]
  ): AvroRecord = {
    val fields = schema.getFields.asScala.toList.map { f =>
      AvroField(
        name = f.name(),
        doc = Option(f.doc()),
        fieldType = convertType(f.schema(), inlineSchemas, processing),
        defaultValue = if (f.hasDefaultValue) Some(defaultValueToJson(f.defaultVal())) else None,
        order = convertOrder(f.order()),
        aliases = f.aliases().asScala.toList,
        wrapperType = extractWrapperType(f)
      )
    }
    AvroRecord(
      name = schema.getName,
      namespace = Option(schema.getNamespace),
      doc = Option(schema.getDoc),
      fields = fields,
      aliases = schema.getAliases.asScala.toList
    )
  }

  private def convertEnumSchema(schema: Schema): AvroEnum =
    AvroEnum(
      name = schema.getName,
      namespace = Option(schema.getNamespace),
      doc = Option(schema.getDoc),
      symbols = schema.getEnumSymbols.asScala.toList,
      defaultSymbol = Option(schema.getEnumDefault),
      aliases = schema.getAliases.asScala.toList
    )

  private def convertFixedSchema(schema: Schema): AvroFixed =
    AvroFixed(
      name = schema.getName,
      namespace = Option(schema.getNamespace),
      doc = Option(schema.getDoc),
      size = schema.getFixedSize,
      aliases = schema.getAliases.asScala.toList
    )

  private def convertOrder(order: Schema.Field.Order): FieldOrder = order match {
    case Schema.Field.Order.ASCENDING  => FieldOrder.Ascending
    case Schema.Field.Order.DESCENDING => FieldOrder.Descending
    case Schema.Field.Order.IGNORE     => FieldOrder.Ignore
  }

  /** Extract x-typr-wrapper attribute from a field's custom properties */
  private def extractWrapperType(field: Schema.Field): Option[String] = {
    val props = field.getObjectProps
    if (props != null) {
      Option(props.get("x-typr-wrapper")).map(_.toString)
    } else {
      None
    }
  }
}

/** Errors that can occur during Avro schema parsing */
sealed trait AvroParseError {
  def message: String
}

object AvroParseError {
  case class DirectoryNotFound(path: String) extends AvroParseError {
    def message: String = s"Directory not found: $path"
  }

  case class FileReadError(path: String, details: String) extends AvroParseError {
    def message: String = s"Failed to read file $path: $details"
  }

  case class SchemaParseError(details: String) extends AvroParseError {
    def message: String = s"Failed to parse Avro schema: $details"
  }

  case class UnexpectedError(details: String) extends AvroParseError {
    def message: String = s"Unexpected error: $details"
  }

  case class MultipleErrors(errors: List[String]) extends AvroParseError {
    def message: String = s"Multiple errors:\n${errors.mkString("\n")}"
  }
}

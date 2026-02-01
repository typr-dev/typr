package typr.grpc.parser

import com.google.protobuf.DescriptorProtos.{FieldDescriptorProto, FileDescriptorSet}
import com.google.protobuf.{DescriptorProtos, ExtensionRegistry}
import typr.grpc._

import java.io.{BufferedInputStream, FileInputStream}
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

/** Parser for Protobuf schema files (.proto) using protoc and FileDescriptorSet.
  *
  * Two input modes:
  *   - ProtoSource.Directory: runs protoc --descriptor_set_out to compile .proto files
  *   - ProtoSource.DescriptorSet: reads pre-built FileDescriptorSet file
  */
object ProtobufParser {

  /** Parse protos from the configured source */
  def parse(source: ProtoSource): Either[GrpcParseError, List[ProtoFile]] = source match {
    case ProtoSource.Directory(path, includePaths) =>
      parseDirectory(path, includePaths)
    case ProtoSource.DescriptorSet(path) =>
      parseDescriptorSet(path)
  }

  /** Parse all .proto files from a directory by running protoc */
  def parseDirectory(directory: Path, includePaths: List[Path]): Either[GrpcParseError, List[ProtoFile]] = {
    if (!Files.isDirectory(directory)) {
      Left(GrpcParseError.DirectoryNotFound(directory.toString))
    } else {
      val protoFiles = Files
        .walk(directory)
        .filter(p => Files.isRegularFile(p) && p.toString.endsWith(".proto"))
        .iterator()
        .asScala
        .toList
        .map(_.toAbsolutePath.normalize())
        .sortBy(_.toString)

      if (protoFiles.isEmpty) {
        Right(Nil)
      } else {
        runProtocAndParse(directory, protoFiles, includePaths)
      }
    }
  }

  /** Parse a pre-built FileDescriptorSet file */
  def parseDescriptorSet(descriptorSetPath: Path): Either[GrpcParseError, List[ProtoFile]] = {
    if (!Files.isRegularFile(descriptorSetPath)) {
      Left(GrpcParseError.FileReadError(descriptorSetPath.toString, "File not found"))
    } else {
      readAndConvertDescriptorSet(descriptorSetPath)
    }
  }

  /** Run protoc to generate a FileDescriptorSet, then parse it */
  private def runProtocAndParse(
      sourceDir: Path,
      protoFiles: List[Path],
      includePaths: List[Path]
  ): Either[GrpcParseError, List[ProtoFile]] = {
    resolveProtoc().flatMap { protocPath =>
      val tempFile = Files.createTempFile("typr-grpc-", ".desc")
      try {
        val allIncludes = (sourceDir :: includePaths).distinct
        val includeArgs = allIncludes.flatMap(p => List("--proto_path", p.toString))

        // Include the typr annotations proto from our resources
        val annotationsDir = extractAnnotationsProto()
        val annotationsInclude = annotationsDir.map(dir => List("--proto_path", dir.toString)).getOrElse(Nil)

        val args = List(protocPath.toString) ++
          includeArgs ++
          annotationsInclude ++
          List(
            "--descriptor_set_out",
            tempFile.toString,
            "--include_source_info",
            "--include_imports"
          ) ++
          protoFiles.map(_.toString)

        val process = new ProcessBuilder(args.asJava)
          .redirectErrorStream(false)
          .start()

        val stderr = new String(process.getErrorStream.readAllBytes())
        val exitCode = process.waitFor()

        if (exitCode != 0) {
          Left(GrpcParseError.ProtocFailed(exitCode, stderr))
        } else {
          readAndConvertDescriptorSet(tempFile)
        }
      } finally {
        val _ = Files.deleteIfExists(tempFile)
      }
    }
  }

  /** Extract typr/annotations.proto from resources to a temp directory */
  private def extractAnnotationsProto(): Option[Path] = {
    val resource = getClass.getResourceAsStream("/typr/annotations.proto")
    if (resource == null) {
      None
    } else {
      try {
        val tempDir = Files.createTempDirectory("typr-proto-")
        val typrDir = tempDir.resolve("typr")
        Files.createDirectories(typrDir)
        val annotationsFile = typrDir.resolve("annotations.proto")
        Files.copy(resource, annotationsFile)
        Some(tempDir)
      } catch {
        case _: Exception => None
      } finally {
        resource.close()
      }
    }
  }

  /** Resolve protoc binary path. Downloads via coursier if not available. */
  private def resolveProtoc(): Either[GrpcParseError, Path] = {
    // Try to find protoc on PATH first
    val onPath = findOnPath("protoc")
    if (onPath.isDefined) {
      Right(onPath.get)
    } else {
      downloadProtocViaCachingMechanism()
    }
  }

  /** Find an executable on PATH */
  private def findOnPath(name: String): Option[Path] = {
    val pathEnv = System.getenv("PATH")
    if (pathEnv == null) return None

    val pathSep = System.getProperty("path.separator")
    val exeSuffix = if (System.getProperty("os.name").toLowerCase.contains("win")) ".exe" else ""

    pathEnv
      .split(pathSep)
      .iterator
      .flatMap { dir =>
        val candidate = Path.of(dir).resolve(name + exeSuffix)
        if (Files.isExecutable(candidate)) Some(candidate) else None
      }
      .nextOption()
  }

  /** Download protoc using coursier-style caching.
    *
    * Downloads from Maven Central: com.google.protobuf:protoc:VERSION:exe:CLASSIFIER
    */
  private def downloadProtocViaCachingMechanism(): Either[GrpcParseError, Path] = {
    val protocVersion = "4.29.3"
    val classifier = osClassifier()

    val cacheDir = Path.of(System.getProperty("user.home"), ".cache", "typr", "protoc", protocVersion)
    val exeSuffix = if (System.getProperty("os.name").toLowerCase.contains("win")) ".exe" else ""
    val cachedProtoc = cacheDir.resolve(s"protoc$exeSuffix")

    if (Files.isExecutable(cachedProtoc)) {
      Right(cachedProtoc)
    } else {
      try {
        Files.createDirectories(cacheDir)
        val url = s"https://repo1.maven.org/maven2/com/google/protobuf/protoc/$protocVersion/protoc-$protocVersion-$classifier$exeSuffix"

        val connection = new java.net.URI(url).toURL.openConnection()
        val input = new BufferedInputStream(connection.getInputStream)
        try {
          Files.copy(input, cachedProtoc, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
          cachedProtoc.toFile.setExecutable(true)
          Right(cachedProtoc)
        } finally {
          input.close()
        }
      } catch {
        case e: Exception =>
          Left(GrpcParseError.ProtocNotFound(s"Failed to download protoc: ${e.getMessage}. Install protoc manually or provide a pre-built descriptor set."))
      }
    }
  }

  /** Detect OS classifier for protoc Maven artifact */
  private def osClassifier(): String = {
    val osName = System.getProperty("os.name").toLowerCase
    val osArch = System.getProperty("os.arch").toLowerCase

    val os =
      if (osName.contains("mac") || osName.contains("darwin")) "osx"
      else if (osName.contains("linux")) "linux"
      else if (osName.contains("win")) "windows"
      else "linux"

    val arch =
      if (osArch.contains("aarch64") || osArch.contains("arm64")) "aarch_64"
      else if (osArch.contains("x86_64") || osArch.contains("amd64")) "x86_64"
      else "x86_64"

    s"$os-$arch"
  }

  /** Read and parse a FileDescriptorSet file, converting to our internal types */
  private def readAndConvertDescriptorSet(path: Path): Either[GrpcParseError, List[ProtoFile]] = {
    try {
      val registry = ExtensionRegistry.newInstance()
      registerTyPrExtensions(registry)

      val input = new BufferedInputStream(new FileInputStream(path.toFile))
      val descriptorSet =
        try {
          FileDescriptorSet.parseFrom(input, registry)
        } finally {
          input.close()
        }

      val protoFiles = descriptorSet.getFileList.asScala.toList
        .filterNot(_.getName.startsWith("google/protobuf/"))
        .filterNot(_.getName == "typr/annotations.proto")
        .map(convertFileDescriptor)

      Right(protoFiles)
    } catch {
      case e: Exception =>
        Left(GrpcParseError.DescriptorParseError(e.getMessage))
    }
  }

  /** Register typr custom extensions with the ExtensionRegistry.
    *
    * The typr.wrapper field option (field 50000) is read from unknown fields in extractWrapperOption, so no explicit extension registration is needed. The registry is kept for potential future
    * extensions.
    */
  private def registerTyPrExtensions(registry: ExtensionRegistry): Unit = {}

  /** Convert a FileDescriptorProto to our internal ProtoFile */
  private def convertFileDescriptor(file: DescriptorProtos.FileDescriptorProto): ProtoFile = {
    val protoPackage = if (file.hasPackage) Some(file.getPackage) else None
    val javaPackage = if (file.hasOptions && file.getOptions.hasJavaPackage) Some(file.getOptions.getJavaPackage) else None

    val messages = file.getMessageTypeList.asScala.toList
      .map(msg => convertMessage(msg, protoPackage.getOrElse("")))

    val enums = file.getEnumTypeList.asScala.toList
      .map(e => convertEnum(e, protoPackage.getOrElse("")))

    val services = file.getServiceList.asScala.toList
      .map(svc => convertService(svc, protoPackage.getOrElse("")))

    val syntax = if (file.hasSyntax && file.getSyntax == "proto2") ProtoSyntax.Proto2 else ProtoSyntax.Proto3

    ProtoFile(
      protoPackage = protoPackage,
      javaPackage = javaPackage,
      messages = messages,
      enums = enums,
      services = services,
      sourcePath = if (file.hasName) Some(file.getName) else None,
      syntax = syntax
    )
  }

  /** Convert a DescriptorProto (message) to our internal ProtoMessage */
  private def convertMessage(msg: DescriptorProtos.DescriptorProto, parentPrefix: String): ProtoMessage = {
    val fullName = if (parentPrefix.isEmpty) msg.getName else s"$parentPrefix.${msg.getName}"

    // Build oneofs first so we can assign fields to them
    val oneofDescriptors = msg.getOneofDeclList.asScala.toList.zipWithIndex

    val fields = msg.getFieldList.asScala.toList.map { field =>
      convertField(field, fullName)
    }

    // Group fields into oneofs (excluding synthetic oneofs for proto3 optional)
    val oneofs = oneofDescriptors.flatMap { case (oneofDesc, idx) =>
      val oneofFields = fields.filter(_.oneofIndex.contains(idx))
      // Proto3 optional creates synthetic oneofs with no fields (since we set oneofIndex=None for proto3Optional fields)
      if (oneofFields.isEmpty) {
        None
      } else {
        Some(
          ProtoOneof(
            name = oneofDesc.getName,
            fields = oneofFields
          )
        )
      }
    }

    val nestedMessages = msg.getNestedTypeList.asScala.toList
      .map(nested => convertMessage(nested, fullName))

    val nestedEnums = msg.getEnumTypeList.asScala.toList
      .map(e => convertEnum(e, fullName))

    ProtoMessage(
      name = msg.getName,
      fullName = fullName,
      fields = fields,
      nestedMessages = nestedMessages,
      nestedEnums = nestedEnums,
      oneofs = oneofs,
      isMapEntry = msg.getOptions.getMapEntry
    )
  }

  /** Convert a FieldDescriptorProto to our internal ProtoField */
  private def convertField(field: DescriptorProtos.FieldDescriptorProto, messageFullName: String): ProtoField = {
    val fieldType = convertFieldType(field)
    val label = convertFieldLabel(field.getLabel)

    // Extract (typr.wrapper) from custom options
    val wrapperType = extractWrapperOption(field)

    val proto3Optional = field.hasProto3Optional && field.getProto3Optional

    ProtoField(
      name = field.getName,
      number = field.getNumber,
      fieldType = fieldType,
      label = label,
      wrapperType = wrapperType,
      defaultValue = if (field.hasDefaultValue) Some(field.getDefaultValue) else None,
      oneofIndex = if (field.hasOneofIndex && !(proto3Optional)) Some(field.getOneofIndex) else None,
      proto3Optional = proto3Optional
    )
  }

  /** Convert a field's type descriptor to our internal ProtoType */
  private def convertFieldType(field: DescriptorProtos.FieldDescriptorProto): ProtoType = {
    import FieldDescriptorProto.Type._

    // For message and enum types, check for well-known types first
    if (field.getType == TYPE_MESSAGE) {
      val typeName = stripLeadingDot(field.getTypeName)

      // Check for well-known types
      ProtoType.wellKnownTypes.get(typeName) match {
        case Some(wellKnown) => return wellKnown
        case None            => ()
      }

      // Check for map entry type (indicated by label REPEATED + message type that is a map entry)
      // Maps are handled at a higher level during message conversion
      return ProtoType.Message(typeName)
    }

    if (field.getType == TYPE_ENUM) {
      val typeName = stripLeadingDot(field.getTypeName)
      return ProtoType.Enum(typeName)
    }

    field.getType match {
      case TYPE_DOUBLE   => ProtoType.Double
      case TYPE_FLOAT    => ProtoType.Float
      case TYPE_INT64    => ProtoType.Int64
      case TYPE_UINT64   => ProtoType.UInt64
      case TYPE_INT32    => ProtoType.Int32
      case TYPE_FIXED64  => ProtoType.Fixed64
      case TYPE_FIXED32  => ProtoType.Fixed32
      case TYPE_BOOL     => ProtoType.Bool
      case TYPE_STRING   => ProtoType.String
      case TYPE_BYTES    => ProtoType.Bytes
      case TYPE_UINT32   => ProtoType.UInt32
      case TYPE_SFIXED32 => ProtoType.SFixed32
      case TYPE_SFIXED64 => ProtoType.SFixed64
      case TYPE_SINT32   => ProtoType.SInt32
      case TYPE_SINT64   => ProtoType.SInt64
      case _             => ProtoType.Bytes
    }
  }

  /** Convert field label to our internal representation */
  private def convertFieldLabel(label: FieldDescriptorProto.Label): ProtoFieldLabel = {
    import FieldDescriptorProto.Label._
    label match {
      case LABEL_OPTIONAL => ProtoFieldLabel.Optional
      case LABEL_REQUIRED => ProtoFieldLabel.Required
      case LABEL_REPEATED => ProtoFieldLabel.Repeated
      case _              => ProtoFieldLabel.Optional
    }
  }

  /** Extract the (typr.wrapper) option from a field's options.
    *
    * The field option is registered at field number 50000 as a string extension on FieldOptions.
    */
  private def extractWrapperOption(field: DescriptorProtos.FieldDescriptorProto): Option[String] = {
    if (!field.hasOptions) return None

    val options = field.getOptions
    val unknownFields = options.getUnknownFields

    // The typr.wrapper extension is at field number 50000
    // When parsed with a registry, it appears in the known fields.
    // When parsed without a registry, it appears in unknown fields.
    val fieldNum = 50000

    if (unknownFields.hasField(fieldNum)) {
      val field50000 = unknownFields.getField(fieldNum)
      val values = field50000.getLengthDelimitedList
      if (!values.isEmpty) {
        Some(values.get(0).toStringUtf8)
      } else {
        None
      }
    } else {
      None
    }
  }

  /** Convert a EnumDescriptorProto to our internal ProtoEnum */
  private def convertEnum(enumDesc: DescriptorProtos.EnumDescriptorProto, parentPrefix: String): ProtoEnum = {
    val fullName = if (parentPrefix.isEmpty) enumDesc.getName else s"$parentPrefix.${enumDesc.getName}"

    val values = enumDesc.getValueList.asScala.toList.map { v =>
      ProtoEnumValue(v.getName, v.getNumber)
    }

    val allowAlias = enumDesc.getOptions.getAllowAlias

    ProtoEnum(
      name = enumDesc.getName,
      fullName = fullName,
      values = values,
      allowAlias = allowAlias
    )
  }

  /** Convert a ServiceDescriptorProto to our internal ProtoService */
  private def convertService(svc: DescriptorProtos.ServiceDescriptorProto, parentPrefix: String): ProtoService = {
    val fullName = if (parentPrefix.isEmpty) svc.getName else s"$parentPrefix.${svc.getName}"

    val methods = svc.getMethodList.asScala.toList.map { method =>
      ProtoMethod(
        name = method.getName,
        inputType = stripLeadingDot(method.getInputType),
        outputType = stripLeadingDot(method.getOutputType),
        clientStreaming = method.getClientStreaming,
        serverStreaming = method.getServerStreaming
      )
    }

    ProtoService(
      name = svc.getName,
      fullName = fullName,
      methods = methods
    )
  }

  /** Strip leading dot from protobuf fully-qualified type names.
    *
    * Protobuf uses ".package.MessageName" format in descriptors, but we want "package.MessageName".
    */
  private def stripLeadingDot(typeName: String): String = {
    if (typeName.startsWith(".")) typeName.substring(1)
    else typeName
  }

  /** Detect map fields and convert them.
    *
    * In protobuf, map<K,V> fields are compiled as repeated MessageType where the message has map_entry option. We need to detect this pattern and convert to ProtoType.Map.
    */
  def resolveMapFields(file: ProtoFile): ProtoFile = {
    // Collect all map entry types from all messages
    def collectMapEntries(messages: List[ProtoMessage]): Map[String, (ProtoType, ProtoType)] = {
      messages.flatMap { msg =>
        // Check nested types for map entries (map entry messages are nested inside the containing message)
        val directEntries = msg.nestedMessages.filter(_.isMapEntry).flatMap { entry =>
          val keyField = entry.fields.find(_.number == 1)
          val valueField = entry.fields.find(_.number == 2)
          (keyField, valueField) match {
            case (Some(k), Some(v)) =>
              Some(entry.fullName -> (k.fieldType, v.fieldType))
            case _ => None
          }
        }
        directEntries ++ collectMapEntries(msg.nestedMessages)
      }.toMap
    }

    val allMessages = flattenMessages(file.messages)
    val mapEntryTypes = allMessages
      .filter(_.isMapEntry)
      .flatMap { entry =>
        val keyField = entry.fields.find(_.number == 1)
        val valueField = entry.fields.find(_.number == 2)
        (keyField, valueField) match {
          case (Some(k), Some(v)) =>
            Some(entry.fullName -> (k.fieldType, v.fieldType))
          case _ => None
        }
      }
      .toMap

    def resolveMessage(msg: ProtoMessage): ProtoMessage = {
      val resolvedFields = msg.fields.map { field =>
        field.fieldType match {
          case ProtoType.Message(typeName) if mapEntryTypes.contains(typeName) =>
            val (keyType, valueType) = mapEntryTypes(typeName)
            field.copy(fieldType = ProtoType.Map(keyType, valueType))
          case _ => field
        }
      }

      msg.copy(
        fields = resolvedFields,
        nestedMessages = msg.nestedMessages.filterNot(_.isMapEntry).map(resolveMessage),
        oneofs = msg.oneofs.map { oneof =>
          oneof.copy(fields = oneof.fields.map { field =>
            field.fieldType match {
              case ProtoType.Message(typeName) if mapEntryTypes.contains(typeName) =>
                val (keyType, valueType) = mapEntryTypes(typeName)
                field.copy(fieldType = ProtoType.Map(keyType, valueType))
              case _ => field
            }
          })
        }
      )
    }

    file.copy(messages = file.messages.map(resolveMessage))
  }

  /** Flatten all messages including nested ones */
  private def flattenMessages(messages: List[ProtoMessage]): List[ProtoMessage] = {
    messages.flatMap { msg =>
      msg :: flattenMessages(msg.nestedMessages)
    }
  }
}

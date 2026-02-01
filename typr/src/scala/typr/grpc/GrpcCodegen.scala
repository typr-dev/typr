package typr.grpc

import typr.grpc.codegen._
import typr.grpc.computed.ComputedGrpcService
import typr.grpc.parser.ProtobufParser
import typr.internal.codegen._
import typr.jvm.Code.{CodeOps, TreeOps}
import typr.{jvm, Lang, Naming, Scope}

/** Main entry point for gRPC/Protobuf code generation */
object GrpcCodegen {

  case class Result(
      files: List[jvm.File],
      errors: List[String]
  )

  /** Generate code from Protobuf .proto files */
  def generate(
      options: GrpcOptions,
      lang: Lang
  ): Result = {
    ProtobufParser.parse(options.protoSource) match {
      case Left(error) =>
        Result(Nil, List(error.message))
      case Right(protoFiles) =>
        generateFromProtoFiles(protoFiles, options, lang)
    }
  }

  /** Generate code from parsed proto files */
  private def generateFromProtoFiles(
      protoFiles: List[ProtoFile],
      options: GrpcOptions,
      lang: Lang
  ): Result = {
    val naming = new Naming(options.pkg, lang)

    // Resolve map fields in all proto files
    val resolvedFiles = protoFiles.map(ProtobufParser.resolveMapFields)

    // Flatten all messages (including nested) across all files
    val allMessages = resolvedFiles.flatMap(f => flattenMessages(f.messages))
    val allEnums = resolvedFiles.flatMap(f => flattenEnums(f.messages, f.enums))
    val allServices = resolvedFiles.flatMap(_.services)

    // Create base type mapper (without wrapper types) for wrapper collection
    val baseTypeMapper = new ProtobufTypeMapper(
      lang = lang,
      naming = naming,
      wrapperTypeMap = Map.empty
    )

    // Collect wrapper types from all messages
    val computedWrappers = ComputedProtobufWrapper.collect(allMessages, baseTypeMapper, naming)

    // Build wrapper type lookup map: wrapperName -> QualifiedType
    val wrapperTypeMap: Map[String, jvm.Type.Qualified] =
      computedWrappers.map { w =>
        w.tpe.value.name.value -> w.tpe
      }.toMap

    // Create type mapper with wrapper types
    val typeMapper = new ProtobufTypeMapper(
      lang = lang,
      naming = naming,
      wrapperTypeMap = wrapperTypeMap
    )

    val files = List.newBuilder[jvm.File]
    val errors = List.newBuilder[String]

    // Generate wrapper types
    if (options.generateMessages && computedWrappers.nonEmpty) {
      computedWrappers.foreach { wrapper =>
        files += generateWrapperType(wrapper, lang)
      }
    }

    // Generate message classes
    if (options.generateMessages) {
      val messageCodegen = new MessageCodegen(
        naming = naming,
        typeMapper = typeMapper,
        lang = lang,
        wrapperTypeMap = wrapperTypeMap
      )

      allMessages.foreach { message =>
        try {
          files += messageCodegen.generate(message)

          // Generate oneof types for this message
          val oneofCodegen = new OneOfCodegen(naming, typeMapper, lang)
          message.oneofs.foreach { oneof =>
            files += oneofCodegen.generate(message.fullName, oneof)
          }
        } catch {
          case e: Exception =>
            errors += s"Failed to generate message ${message.name}: ${e.getMessage}"
        }
      }

      // Generate enum classes
      val enumCodegen = new EnumCodegen(naming, lang)
      allEnums.foreach { protoEnum =>
        try {
          files += enumCodegen.generate(protoEnum)
        } catch {
          case e: Exception =>
            errors += s"Failed to generate enum ${protoEnum.name}: ${e.getMessage}"
        }
      }
    }

    // Generate service interfaces, servers, and clients using Computed/Files pattern
    if (allServices.nonEmpty && (options.generateServices || options.generateServers || options.generateClients)) {
      val framework = options.frameworkIntegration.grpcFramework

      allServices.foreach { service =>
        try {
          // Phase 1: Compute service metadata
          val computed = ComputedGrpcService.from(
            proto = service,
            naming = naming,
            lang = lang,
            typeMapper = typeMapper,
            framework = framework
          )

          // Phase 2: Generate files from computed metadata
          val filesGrpcService = new FilesGrpcService(computed, lang, options)
          files ++= filesGrpcService.all
        } catch {
          case e: Exception =>
            errors += s"Failed to generate service ${service.name}: ${e.getMessage}"
        }
      }
    }

    Result(files.result(), errors.result())
  }

  /** Generate a wrapper type file */
  private def generateWrapperType(wrapper: ComputedProtobufWrapper, lang: Lang): jvm.File = {
    val value = jvm.Ident("value")
    val v = jvm.Ident("v")
    val thisRef = code"this"

    val valueOfMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(List(s"Create a ${wrapper.tpe.value.name.value} from a raw value")),
      tparams = Nil,
      name = jvm.Ident("valueOf"),
      params = List(jvm.Param(Nil, jvm.Comments.Empty, v, wrapper.underlyingJvmType, None)),
      implicitParams = Nil,
      tpe = wrapper.tpe,
      throws = Nil,
      body = jvm.Body.Stmts(List(jvm.Return(jvm.New(wrapper.tpe.code, List(jvm.Arg.Pos(v.code))).code).code)),
      isOverride = false,
      isDefault = false
    )

    val unwrapMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(List("Get the underlying value")),
      tparams = Nil,
      name = jvm.Ident("unwrap"),
      params = Nil,
      implicitParams = Nil,
      tpe = wrapper.underlyingJvmType,
      throws = Nil,
      body = jvm.Body.Stmts(List(jvm.Return(lang.prop(thisRef, value)).code)),
      isOverride = false,
      isDefault = false
    )

    val record = jvm.Adt.Record(
      annotations = Nil,
      constructorAnnotations = Nil,
      isWrapper = true,
      privateConstructor = false,
      comments = jvm.Comments(List(s"Wrapper type for ${wrapper.underlyingJvmType.render}")),
      name = wrapper.tpe,
      tparams = Nil,
      params = List(jvm.Param(Nil, jvm.Comments.Empty, value, wrapper.underlyingJvmType, None)),
      implicitParams = Nil,
      `extends` = None,
      implements = Nil,
      members = List(unwrapMethod),
      staticMembers = List(valueOfMethod)
    )

    jvm.File(wrapper.tpe, jvm.Code.Tree(record), secondaryTypes = Nil, scope = Scope.Main)
  }

  /** Flatten all messages including nested ones */
  private def flattenMessages(messages: List[ProtoMessage]): List[ProtoMessage] = {
    messages.flatMap { msg =>
      msg :: flattenMessages(msg.nestedMessages)
    }
  }

  /** Collect all enums including nested ones from messages */
  private def flattenEnums(messages: List[ProtoMessage], topLevelEnums: List[ProtoEnum]): List[ProtoEnum] = {
    val nestedEnums = messages.flatMap { msg =>
      msg.nestedEnums ++ flattenEnums(msg.nestedMessages, Nil)
    }
    topLevelEnums ++ nestedEnums
  }
}

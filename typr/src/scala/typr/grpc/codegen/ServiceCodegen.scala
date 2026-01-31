package typr.grpc.codegen

import typr.grpc._
import typr.effects.EffectTypeOps
import typr.{jvm, Lang, Naming, Scope}
import typr.jvm.Code.{CodeOps, TreeOps, TypeOps}
import typr.internal.codegen._

/** Generates service interfaces, server adapters, and client wrappers from Protobuf service definitions.
  *
  * For each service generates:
  *   1. Clean service interface - one method per RPC, using clean types 2. Server adapter - implements BindableService, builds ServerServiceDefinition directly 3. Client wrapper - uses Channel +
  *      ClientCalls with MethodDescriptor constants
  */
class ServiceCodegen(
    naming: Naming,
    lang: Lang,
    options: GrpcOptions,
    typeMapper: ProtobufTypeMapper
) {

  private val effectOps: Option[EffectTypeOps] = options.effectType.ops

  // gRPC types
  private val IteratorType = lang.typeSupport.JavaIteratorType
  private val ChannelType = jvm.Type.Qualified(jvm.QIdent("io.grpc.Channel"))
  private val CallOptionsType = jvm.Type.Qualified(jvm.QIdent("io.grpc.CallOptions"))
  private val MethodDescriptorType = jvm.Type.Qualified(jvm.QIdent("io.grpc.MethodDescriptor"))
  private val MethodTypeType = MethodDescriptorType / jvm.Ident("MethodType")
  private val ClientCallsType = jvm.Type.Qualified(jvm.QIdent("io.grpc.stub.ClientCalls"))
  private val ServerCallsType = jvm.Type.Qualified(jvm.QIdent("io.grpc.stub.ServerCalls"))
  private val ServerServiceDefinitionType = jvm.Type.Qualified(jvm.QIdent("io.grpc.ServerServiceDefinition"))
  private val BindableServiceType = jvm.Type.Qualified(jvm.QIdent("io.grpc.BindableService"))

  /** Generate all files for a service */
  def generate(service: ProtoService): List[jvm.File] = {
    val files = List.newBuilder[jvm.File]

    // Generate clean service interface
    if (options.generateServices) {
      files += generateServiceInterface(service)
    }

    // Generate server adapter
    if (options.generateServers) {
      files += generateServerAdapter(service)
    }

    // Generate client wrapper
    if (options.generateClients) {
      files += generateClientWrapper(service)
    }

    files.result()
  }

  /** Generate the clean service interface with one method per RPC */
  private def generateServiceInterface(service: ProtoService): jvm.File = {
    val tpe = naming.grpcServiceTypeName(service.name)

    val methods = service.methods.map { method =>
      generateServiceMethod(method)
    }

    val serviceInterface = jvm.Class(
      annotations = Nil,
      comments = jvm.Comments(List(s"Clean service interface for ${service.name} gRPC service")),
      classType = jvm.ClassType.Interface,
      name = tpe,
      tparams = Nil,
      params = Nil,
      implicitParams = Nil,
      `extends` = None,
      implements = Nil,
      members = methods,
      staticMembers = Nil
    )

    jvm.File(tpe, jvm.Code.Tree(serviceInterface), secondaryTypes = Nil, scope = Scope.Main)
  }

  /** Generate a method signature for a service RPC */
  private def generateServiceMethod(method: ProtoMethod): jvm.Method = {
    val methodName = naming.grpcMethodName(method.name)

    val inputType = typeMapper.mapType(ProtoType.Message(method.inputType))
    val outputType = typeMapper.mapType(ProtoType.Message(method.outputType))

    val (params, returnType) = method.rpcPattern match {
      case RpcPattern.Unary =>
        val p = List(jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("request"), inputType, None))
        val ret = wrapInEffect(outputType)
        (p, ret)

      case RpcPattern.ServerStreaming =>
        val p = List(jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("request"), inputType, None))
        val ret = IteratorType.of(outputType)
        (p, ret)

      case RpcPattern.ClientStreaming =>
        val p = List(jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("requests"), IteratorType.of(inputType), None))
        val ret = wrapInEffect(outputType)
        (p, ret)

      case RpcPattern.BidiStreaming =>
        val p = List(jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("requests"), IteratorType.of(inputType), None))
        val ret = IteratorType.of(outputType)
        (p, ret)
    }

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = methodName,
      params = params,
      implicitParams = Nil,
      tpe = returnType,
      throws = Nil,
      body = jvm.Body.Abstract,
      isOverride = false,
      isDefault = false
    )
  }

  /** Wrap a type in the configured effect type if non-blocking */
  private def wrapInEffect(tpe: jvm.Type): jvm.Type = {
    effectOps match {
      case Some(ops) =>
        val boxed = if (tpe == jvm.Type.Void) {
          jvm.Type.Qualified(jvm.QIdent("java.lang.Void"))
        } else {
          tpe
        }
        ops.tpe.of(boxed)
      case None => tpe
    }
  }

  /** Get the proto full method name for gRPC: "package.ServiceName/MethodName" */
  private def fullMethodName(service: ProtoService, method: ProtoMethod): String = {
    s"${service.fullName}/${method.name}"
  }

  /** Get the gRPC MethodType for a method */
  private def grpcMethodType(method: ProtoMethod): jvm.Code = {
    method.rpcPattern match {
      case RpcPattern.Unary           => MethodTypeType.code.select("UNARY")
      case RpcPattern.ServerStreaming => MethodTypeType.code.select("SERVER_STREAMING")
      case RpcPattern.ClientStreaming => MethodTypeType.code.select("CLIENT_STREAMING")
      case RpcPattern.BidiStreaming   => MethodTypeType.code.select("BIDI_STREAMING")
    }
  }

  /** Generate a MethodDescriptor constant for an RPC method */
  private def generateMethodDescriptor(service: ProtoService, method: ProtoMethod): jvm.Value = {
    val inputType = naming.grpcMessageTypeName(method.inputType)
    val outputType = naming.grpcMessageTypeName(method.outputType)

    val descriptorName = jvm.Ident(methodDescriptorFieldName(method))

    val inputMarshaller = marshallerRef(inputType)
    val outputMarshaller = marshallerRef(outputType)

    val descriptorType = MethodDescriptorType.of(inputType, outputType)

    val builderChain = MethodDescriptorType.code
      .invoke("newBuilder", inputMarshaller, outputMarshaller)
      .invoke("setType", grpcMethodType(method))
      .invoke("setFullMethodName", jvm.StrLit(fullMethodName(service, method)).code)
      .invoke("build")

    jvm.Value(
      annotations = Nil,
      name = descriptorName,
      tpe = descriptorType,
      body = Some(builderChain),
      isLazy = false,
      isOverride = false
    )
  }

  /** Generate a reference to a message type's marshaller */
  private def marshallerRef(messageType: jvm.Type.Qualified): jvm.Code = {
    messageType.code.select(naming.grpcMarshallerName.value)
  }

  /** Generate the field name for a MethodDescriptor constant */
  private def methodDescriptorFieldName(method: ProtoMethod): String = {
    // Convert camelCase to UPPER_SNAKE_CASE
    val name = method.name
    val sb = new StringBuilder
    name.foreach { c =>
      if (c.isUpper && sb.nonEmpty) sb.append('_')
      sb.append(c.toUpper)
    }
    sb.toString()
  }

  /** Generate server adapter that implements BindableService and delegates to clean interface */
  private def generateServerAdapter(service: ProtoService): jvm.File = {
    val tpe = naming.grpcServerTypeName(service.name)
    val serviceTpe = naming.grpcServiceTypeName(service.name)

    val frameworkAnnotations = options.frameworkIntegration.grpcFramework
      .map(_.serverAnnotations)
      .getOrElse(Nil)

    // Constructor parameter: the clean service implementation
    val delegateParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("delegate"), serviceTpe, None)

    // Generate MethodDescriptor constants as static members
    val methodDescriptors = service.methods.map { method =>
      generateMethodDescriptor(service, method)
    }

    // Generate bindService() method
    val bindServiceMethod = generateBindServiceMethod(service, tpe, delegateParam.name)

    val serverClass = jvm.Class(
      annotations = frameworkAnnotations,
      comments = jvm.Comments(List(s"gRPC server adapter for ${service.name} - delegates to clean service interface")),
      classType = jvm.ClassType.Class,
      name = tpe,
      tparams = Nil,
      params = List(delegateParam),
      implicitParams = Nil,
      `extends` = None,
      implements = List(BindableServiceType),
      members = List(bindServiceMethod),
      staticMembers = methodDescriptors
    )

    jvm.File(tpe, jvm.Code.Tree(serverClass), secondaryTypes = Nil, scope = Scope.Main)
  }

  /** Generate the bindService() method that builds ServerServiceDefinition */
  private def generateBindServiceMethod(service: ProtoService, classTpe: jvm.Type.Qualified, delegateIdent: jvm.Ident): jvm.Method = {
    // Build the ServerServiceDefinition using builder pattern
    var builderChain: jvm.Code = ServerServiceDefinitionType.code.invoke("builder", jvm.StrLit(service.fullName).code)

    service.methods.foreach { method =>
      val descriptorRef = classTpe.code.select(methodDescriptorFieldName(method))
      val handler = generateServerHandler(method, delegateIdent)

      builderChain = builderChain.invoke("addMethod", descriptorRef, handler)
    }

    builderChain = builderChain.invoke("build")

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("bindService"),
      params = Nil,
      implicitParams = Nil,
      tpe = ServerServiceDefinitionType,
      throws = Nil,
      body = jvm.Body.Stmts(List(jvm.Return(builderChain).code)),
      isOverride = true,
      isDefault = false
    )
  }

  /** Generate the ServerCalls handler for a method */
  private def generateServerHandler(method: ProtoMethod, delegateIdent: jvm.Ident): jvm.Code = {
    val methodName = naming.grpcMethodName(method.name)

    method.rpcPattern match {
      case RpcPattern.Unary =>
        // ServerCalls.asyncUnaryCall((request, responseObserver) -> { ... })
        val request = jvm.Ident("request")
        val responseObserver = jvm.Ident("responseObserver")
        val delegateCall = delegateIdent.code.invoke(methodName.value, request.code)

        val lambdaBody = effectOps match {
          case Some(ops) =>
            val response = jvm.Ident("response")
            val onItemBody = jvm.Body.Stmts(
              List(
                responseObserver.code.invoke("onNext", response.code),
                responseObserver.code.invoke("onCompleted")
              )
            )
            val onItemLambda = jvm.Lambda(List(jvm.LambdaParam(response)), onItemBody)
            val error = jvm.Ident("error")
            val onFailureLambda = jvm.Lambda(List(jvm.LambdaParam(error)), jvm.Body.Expr(responseObserver.code.invoke("onError", error.code)))
            jvm.Body.Stmts(List(ops.subscribeWith(delegateCall, onItemLambda.code, onFailureLambda.code)))
          case None =>
            val onNext = responseObserver.code.invoke("onNext", delegateCall)
            val onCompleted = responseObserver.code.invoke("onCompleted")
            jvm.Body.Stmts(List(onNext, onCompleted))
        }

        val lambda = jvm.Lambda(List(jvm.LambdaParam(request, None), jvm.LambdaParam(responseObserver, None)), lambdaBody)
        ServerCallsType.code.invoke("asyncUnaryCall", lambda.code)

      case RpcPattern.ServerStreaming =>
        // ServerCalls.asyncServerStreamingCall((request, responseObserver) -> { ... })
        val request = jvm.Ident("request")
        val responseObserver = jvm.Ident("responseObserver")
        val results = jvm.Ident("results")
        val resultsDecl = jvm.LocalVar(results, None, delegateIdent.code.invoke(methodName.value, request.code))
        val whileBody = List(
          responseObserver.code.invoke("onNext", results.code.invoke("next"))
        )
        val whileLoop = jvm.While(results.code.invoke("hasNext"), whileBody)
        val onCompleted = responseObserver.code.invoke("onCompleted")
        val lambdaBody = jvm.Body.Stmts(List(resultsDecl.code, whileLoop.code, onCompleted))
        val lambda = jvm.Lambda(List(jvm.LambdaParam(request, None), jvm.LambdaParam(responseObserver, None)), lambdaBody)
        ServerCallsType.code.invoke("asyncServerStreamingCall", lambda.code)

      case RpcPattern.ClientStreaming =>
        val responseObserver = jvm.Ident("responseObserver")
        val throwStmt = jvm
          .Throw(
            jvm.Type
              .Qualified("java.lang.UnsupportedOperationException")
              .construct(jvm.StrLit("Client streaming not yet implemented in server adapter").code)
          )
          .code
        val lambda = jvm.Lambda(List(jvm.LambdaParam(responseObserver, None)), jvm.Body.Stmts(List(throwStmt)))
        ServerCallsType.code.invoke("asyncClientStreamingCall", lambda.code)

      case RpcPattern.BidiStreaming =>
        val responseObserver = jvm.Ident("responseObserver")
        val throwStmt = jvm
          .Throw(
            jvm.Type
              .Qualified("java.lang.UnsupportedOperationException")
              .construct(jvm.StrLit("Bidi streaming not yet implemented in server adapter").code)
          )
          .code
        val lambda = jvm.Lambda(List(jvm.LambdaParam(responseObserver, None)), jvm.Body.Stmts(List(throwStmt)))
        ServerCallsType.code.invoke("asyncBidiStreamingCall", lambda.code)
    }
  }

  /** Generate client wrapper that uses Channel and ClientCalls directly */
  private def generateClientWrapper(service: ProtoService): jvm.File = {
    val tpe = naming.grpcClientTypeName(service.name)
    val serviceTpe = naming.grpcServiceTypeName(service.name)

    val frameworkAnnotations = options.frameworkIntegration.grpcFramework
      .map(fw => fw.clientFieldAnnotations(service.name))
      .getOrElse(Nil)

    // Constructor parameter: the gRPC Channel
    val channelParam = jvm.Param(
      frameworkAnnotations,
      jvm.Comments.Empty,
      jvm.Ident("channel"),
      ChannelType,
      None
    )

    // Generate MethodDescriptor constants as static members
    val methodDescriptors = service.methods.map { method =>
      generateMethodDescriptor(service, method)
    }

    // Generate methods that call stub with proto conversion
    val methods = service.methods.map { method =>
      generateClientMethod(method, tpe, channelParam.name)
    }

    val clientClass = jvm.Class(
      annotations = Nil,
      comments = jvm.Comments(List(s"gRPC client wrapper for ${service.name} - wraps Channel with clean types")),
      classType = jvm.ClassType.Class,
      name = tpe,
      tparams = Nil,
      params = List(channelParam),
      implicitParams = Nil,
      `extends` = None,
      implements = List(serviceTpe),
      members = methods,
      staticMembers = methodDescriptors
    )

    jvm.File(tpe, jvm.Code.Tree(clientClass), secondaryTypes = Nil, scope = Scope.Main)
  }

  /** Generate a client method that uses ClientCalls */
  private def generateClientMethod(method: ProtoMethod, classTpe: jvm.Type.Qualified, channelIdent: jvm.Ident): jvm.Method = {
    val methodName = naming.grpcMethodName(method.name)
    val cleanInputType = naming.grpcMessageTypeName(method.inputType)
    val cleanOutputType = naming.grpcMessageTypeName(method.outputType)
    val descriptorRef = classTpe.code.select(methodDescriptorFieldName(method))

    method.rpcPattern match {
      case RpcPattern.Unary =>
        val requestParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("request"), cleanInputType, None)
        val blockingCall = ClientCallsType.code.invoke(
          "blockingUnaryCall",
          channelIdent.code,
          descriptorRef,
          CallOptionsType.code.select("DEFAULT"),
          requestParam.name.code
        )

        val bodyStmts = effectOps match {
          case Some(ops) =>
            val supplierLambda = jvm.Lambda(Nil, jvm.Body.Expr(blockingCall))
            List(jvm.Return(ops.defer(supplierLambda.code)).code)
          case None =>
            List(jvm.Return(blockingCall).code)
        }

        jvm.Method(
          annotations = Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = methodName,
          params = List(requestParam),
          implicitParams = Nil,
          tpe = wrapInEffect(cleanOutputType),
          throws = Nil,
          body = jvm.Body.Stmts(bodyStmts),
          isOverride = true,
          isDefault = false
        )

      case RpcPattern.ServerStreaming =>
        val requestParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("request"), cleanInputType, None)
        val callExpr = ClientCallsType.code.invoke(
          "blockingServerStreamingCall",
          channelIdent.code,
          descriptorRef,
          CallOptionsType.code.select("DEFAULT"),
          requestParam.name.code
        )
        val returnStmt = jvm.Return(callExpr).code

        jvm.Method(
          annotations = Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = methodName,
          params = List(requestParam),
          implicitParams = Nil,
          tpe = IteratorType.of(cleanOutputType),
          throws = Nil,
          body = jvm.Body.Stmts(List(returnStmt)),
          isOverride = true,
          isDefault = false
        )

      case RpcPattern.ClientStreaming =>
        val requestsParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("requests"), IteratorType.of(cleanInputType), None)

        jvm.Method(
          annotations = Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = methodName,
          params = List(requestsParam),
          implicitParams = Nil,
          tpe = wrapInEffect(cleanOutputType),
          throws = Nil,
          body = jvm.Body.Stmts(
            List(
              jvm
                .Throw(
                  jvm.Type
                    .Qualified("java.lang.UnsupportedOperationException")
                    .construct(jvm.StrLit("Client streaming not yet implemented in client wrapper").code)
                )
                .code
            )
          ),
          isOverride = true,
          isDefault = false
        )

      case RpcPattern.BidiStreaming =>
        val requestsParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("requests"), IteratorType.of(cleanInputType), None)

        jvm.Method(
          annotations = Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = methodName,
          params = List(requestsParam),
          implicitParams = Nil,
          tpe = IteratorType.of(cleanOutputType),
          throws = Nil,
          body = jvm.Body.Stmts(
            List(
              jvm
                .Throw(
                  jvm.Type
                    .Qualified("java.lang.UnsupportedOperationException")
                    .construct(jvm.StrLit("Bidi streaming not yet implemented in client wrapper").code)
                )
                .code
            )
          ),
          isOverride = true,
          isDefault = false
        )
    }
  }
}

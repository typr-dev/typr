package typr.grpc.codegen

import typr.effects.EffectTypeOps
import typr.grpc.{GrpcOptions, RpcPattern}
import typr.grpc.computed.{ComputedGrpcMethod, ComputedGrpcService}
import typr.internal.codegen._
import typr.jvm.Code.{CodeOps, TreeOps, TypeOps}
import typr.{jvm, Lang, Scope}

/** Generates JVM files from computed gRPC service definitions.
  *
  * Separates file generation from computation logic. Uses ComputedGrpcService for all type information.
  */
class FilesGrpcService(
    computed: ComputedGrpcService,
    lang: Lang,
    options: GrpcOptions
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

  /** Generate all files for this service */
  def all: List[jvm.File] = {
    val files = List.newBuilder[jvm.File]

    if (options.generateServices) {
      files += serviceInterfaceFile
    }

    if (options.generateServers) {
      files += serverAdapterFile
    }

    if (options.generateClients) {
      files += clientWrapperFile
    }

    files.result()
  }

  /** Generate the clean service interface with one method per RPC */
  def serviceInterfaceFile: jvm.File = {
    val methods = computed.methods.map(generateServiceMethod)

    val serviceInterface = jvm.Class(
      annotations = Nil,
      comments = jvm.Comments(List(s"Clean service interface for ${computed.protoName} gRPC service")),
      classType = jvm.ClassType.Interface,
      name = computed.serviceTpe,
      tparams = Nil,
      params = Nil,
      implicitParams = Nil,
      `extends` = None,
      implements = Nil,
      members = methods,
      staticMembers = Nil
    )

    jvm.File(computed.serviceTpe, jvm.Code.Tree(serviceInterface), secondaryTypes = Nil, scope = Scope.Main)
  }

  /** Generate server adapter that implements BindableService and delegates to clean interface */
  def serverAdapterFile: jvm.File = {
    val frameworkAnnotations = computed.framework
      .map(_.serverAnnotations)
      .getOrElse(Nil)

    val delegateParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("delegate"), computed.serviceTpe, None)

    val methodDescriptors = computed.methods.map(generateMethodDescriptor)

    val bindServiceMethod = generateBindServiceMethod(delegateParam.name)

    val serverClass = jvm.Class(
      annotations = frameworkAnnotations,
      comments = jvm.Comments(List(s"gRPC server adapter for ${computed.protoName} - delegates to clean service interface")),
      classType = jvm.ClassType.Class,
      name = computed.serverTpe,
      tparams = Nil,
      params = List(delegateParam),
      implicitParams = Nil,
      `extends` = None,
      implements = List(BindableServiceType),
      members = List(bindServiceMethod),
      staticMembers = methodDescriptors
    )

    jvm.File(computed.serverTpe, jvm.Code.Tree(serverClass), secondaryTypes = Nil, scope = Scope.Main)
  }

  /** Generate client wrapper that uses Channel and ClientCalls directly */
  def clientWrapperFile: jvm.File = {
    val frameworkAnnotations = computed.framework
      .map(fw => fw.clientFieldAnnotations(computed.protoName))
      .getOrElse(Nil)

    val channelParam = jvm.Param(
      frameworkAnnotations,
      jvm.Comments.Empty,
      jvm.Ident("channel"),
      ChannelType,
      None
    )

    val methodDescriptors = computed.methods.map(generateMethodDescriptor)

    val methods = computed.methods.map(m => generateClientMethod(m, channelParam.name))

    val clientClass = jvm.Class(
      annotations = Nil,
      comments = jvm.Comments(List(s"gRPC client wrapper for ${computed.protoName} - wraps Channel with clean types")),
      classType = jvm.ClassType.Class,
      name = computed.clientTpe,
      tparams = Nil,
      params = List(channelParam),
      implicitParams = Nil,
      `extends` = None,
      implements = List(computed.serviceTpe),
      members = methods,
      staticMembers = methodDescriptors
    )

    jvm.File(computed.clientTpe, jvm.Code.Tree(clientClass), secondaryTypes = Nil, scope = Scope.Main)
  }

  // ===== Private helpers =====

  private def generateServiceMethod(method: ComputedGrpcMethod): jvm.Method = {
    val (params, returnType) = method.rpcPattern match {
      case RpcPattern.Unary =>
        val p = List(jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("request"), method.inputType, None))
        val ret = wrapInEffect(method.outputType)
        (p, ret)

      case RpcPattern.ServerStreaming =>
        val p = List(jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("request"), method.inputType, None))
        val ret = IteratorType.of(method.outputType)
        (p, ret)

      case RpcPattern.ClientStreaming =>
        val p = List(jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("requests"), IteratorType.of(method.inputType), None))
        val ret = wrapInEffect(method.outputType)
        (p, ret)

      case RpcPattern.BidiStreaming =>
        val p = List(jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("requests"), IteratorType.of(method.inputType), None))
        val ret = IteratorType.of(method.outputType)
        (p, ret)
    }

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = method.name,
      params = params,
      implicitParams = Nil,
      tpe = returnType,
      throws = Nil,
      body = jvm.Body.Abstract,
      isOverride = false,
      isDefault = false
    )
  }

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

  private def grpcMethodType(method: ComputedGrpcMethod): jvm.Code = {
    method.rpcPattern match {
      case RpcPattern.Unary           => MethodTypeType.code.select("UNARY")
      case RpcPattern.ServerStreaming => MethodTypeType.code.select("SERVER_STREAMING")
      case RpcPattern.ClientStreaming => MethodTypeType.code.select("CLIENT_STREAMING")
      case RpcPattern.BidiStreaming   => MethodTypeType.code.select("BIDI_STREAMING")
    }
  }

  private def generateMethodDescriptor(method: ComputedGrpcMethod): jvm.Value = {
    val descriptorName = jvm.Ident(method.descriptorFieldName)
    val inputMarshaller = marshallerRef(method.protoInputType)
    val outputMarshaller = marshallerRef(method.protoOutputType)
    val descriptorType = MethodDescriptorType.of(method.protoInputType, method.protoOutputType)

    val builderChain = MethodDescriptorType.code
      .invoke("newBuilder", inputMarshaller, outputMarshaller)
      .invoke("setType", grpcMethodType(method))
      .invoke("setFullMethodName", jvm.StrLit(method.fullMethodName(computed.fullName)).code)
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

  private def marshallerRef(messageType: jvm.Type.Qualified): jvm.Code = {
    messageType.code.select(computed.naming.grpcMarshallerName.value)
  }

  private def generateBindServiceMethod(delegateIdent: jvm.Ident): jvm.Method = {
    var builderChain: jvm.Code = ServerServiceDefinitionType.code.invoke("builder", jvm.StrLit(computed.fullName).code)

    computed.methods.foreach { method =>
      val descriptorRef = computed.serverTpe.code.select(method.descriptorFieldName)
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

  private def generateServerHandler(method: ComputedGrpcMethod, delegateIdent: jvm.Ident): jvm.Code = {
    method.rpcPattern match {
      case RpcPattern.Unary =>
        val request = jvm.Ident("request")
        val responseObserver = jvm.Ident("responseObserver")
        val delegateCall = delegateIdent.code.invoke(method.name.value, request.code)

        val lambdaBody = effectOps match {
          case Some(ops) =>
            val response = jvm.Ident("response")
            val onItemBody = jvm.Body.Stmts(
              List(
                responseObserver.code.invoke("onNext", response.code),
                responseObserver.code.invoke("onCompleted")
              )
            )
            // Add type annotation to help Scala 3 type inference
            val onItemLambda = jvm.Lambda(List(jvm.LambdaParam(response, Some(method.protoOutputType))), onItemBody)
            val error = jvm.Ident("error")
            val ThrowableType = jvm.Type.Qualified(jvm.QIdent("java.lang.Throwable"))
            val onFailureLambda = jvm.Lambda(List(jvm.LambdaParam(error, Some(ThrowableType))), jvm.Body.Expr(responseObserver.code.invoke("onError", error.code)))
            jvm.Body.Stmts(List(ops.subscribeWith(delegateCall, onItemLambda.code, onFailureLambda.code)))
          case None =>
            val onNext = responseObserver.code.invoke("onNext", delegateCall)
            val onCompleted = responseObserver.code.invoke("onCompleted")
            jvm.Body.Stmts(List(onNext, onCompleted))
        }

        val lambda = jvm.Lambda(List(jvm.LambdaParam(request, None), jvm.LambdaParam(responseObserver, None)), lambdaBody)
        ServerCallsType.code.invoke("asyncUnaryCall", lambda.code)

      case RpcPattern.ServerStreaming =>
        val request = jvm.Ident("request")
        val responseObserver = jvm.Ident("responseObserver")
        val results = jvm.Ident("results")
        val resultsDecl = jvm.LocalVar(results, None, delegateIdent.code.invoke(method.name.value, request.code))
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

  private def generateClientMethod(method: ComputedGrpcMethod, channelIdent: jvm.Ident): jvm.Method = {
    val descriptorRef = computed.clientTpe.code.select(method.descriptorFieldName)

    method.rpcPattern match {
      case RpcPattern.Unary =>
        val requestParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("request"), method.protoInputType, None)
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
          name = method.name,
          params = List(requestParam),
          implicitParams = Nil,
          tpe = wrapInEffect(method.protoOutputType),
          throws = Nil,
          body = jvm.Body.Stmts(bodyStmts),
          isOverride = true,
          isDefault = false
        )

      case RpcPattern.ServerStreaming =>
        val requestParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("request"), method.protoInputType, None)
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
          name = method.name,
          params = List(requestParam),
          implicitParams = Nil,
          tpe = IteratorType.of(method.protoOutputType),
          throws = Nil,
          body = jvm.Body.Stmts(List(returnStmt)),
          isOverride = true,
          isDefault = false
        )

      case RpcPattern.ClientStreaming =>
        val requestsParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("requests"), IteratorType.of(method.protoInputType), None)

        jvm.Method(
          annotations = Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = method.name,
          params = List(requestsParam),
          implicitParams = Nil,
          tpe = wrapInEffect(method.protoOutputType),
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
        val requestsParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("requests"), IteratorType.of(method.protoInputType), None)

        jvm.Method(
          annotations = Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = method.name,
          params = List(requestsParam),
          implicitParams = Nil,
          tpe = IteratorType.of(method.protoOutputType),
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

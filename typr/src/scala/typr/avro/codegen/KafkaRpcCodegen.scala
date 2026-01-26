package typr.avro.codegen

import typr.avro._
import typr.effects.EffectType
import typr.jvm.Code.TypeOps
import typr.internal.codegen._
import typr.openapi.codegen.JsonLibSupport
import typr.{jvm, Lang, Naming, Scope}

/** Generates Kafka RPC request/response wrappers, clients, and servers.
  *
  * Request wrappers include correlation IDs for reply matching. Response wrappers are sealed types with Success/Error cases. Clients implement the service interface via Kafka RPC. Servers dispatch to
  * handlers and send replies.
  */
class KafkaRpcCodegen(
    naming: Naming,
    lang: Lang,
    framework: KafkaFramework,
    typeMapper: AvroTypeMapper,
    jsonLibSupport: JsonLibSupport,
    effectType: EffectType
) {

  private val isAsync = effectType != EffectType.Blocking

  private val UUIDType = jvm.Type.Qualified(jvm.QIdent("java.util.UUID"))
  private val StringType = lang.String

  /** Generate all RPC files for a protocol */
  def generate(protocol: AvroProtocol): List[jvm.File] = {
    val files = List.newBuilder[jvm.File]

    // Generate the Request<R> interface that all request types implement
    files += generateRequestInterface(protocol)

    protocol.messages.foreach { message =>
      files += generateRequestType(message, protocol)
      if (!message.oneWay) {
        files += generateResponseType(message, protocol.namespace)
      }
    }

    files += generateClient(protocol)
    files += generateServer(protocol)

    files.result()
  }

  /** Generate sealed Request interface that all request types implement */
  private def generateRequestInterface(protocol: AvroProtocol): jvm.File = {
    val requestInterfaceType = naming.avroServiceRequestInterfaceTypeName(protocol.name, protocol.namespace)

    // Collect all request types as permitted subtypes
    val permittedTypes = protocol.messages.map { message =>
      naming.avroMessageRequestTypeName(message.name, protocol.namespace)
    }

    val sealedInterface = jvm.Adt.Sum(
      annotations = Nil,
      comments = jvm.Comments(List(s"Sealed request interface for ${protocol.name} RPC")),
      name = requestInterfaceType,
      tparams = Nil,
      members = Nil,
      implements = Nil,
      subtypes = Nil, // Subtypes are records defined in their own files
      staticMembers = Nil,
      permittedSubtypes = permittedTypes
    )

    jvm.File(requestInterfaceType, jvm.Code.Tree(sealedInterface), secondaryTypes = Nil, scope = Scope.Main)
  }

  /** Generate request wrapper type: record with correlationId + request params */
  private def generateRequestType(message: AvroMessage, protocol: AvroProtocol): jvm.File = {
    val namespace = protocol.namespace
    val requestType = naming.avroMessageRequestTypeName(message.name, namespace)
    val requestInterfaceType = naming.avroServiceRequestInterfaceTypeName(protocol.name, namespace)

    val correlationIdParam = jvm.Param(
      Nil,
      jvm.Comments(List("Correlation ID for request/reply matching")),
      jvm.Ident("correlationId"),
      StringType,
      None
    )

    val messageParams = message.request.map { field =>
      val fieldType = typeMapper.mapType(field.fieldType)
      jvm.Param(
        Nil,
        field.doc.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty),
        naming.avroFieldName(field.name),
        fieldType,
        None
      )
    }

    val createMethod = generateCreateMethod(requestType, messageParams)

    val record = jvm.Adt.Record(
      annotations = Nil,
      constructorAnnotations = Nil,
      isWrapper = false,
      privateConstructor = false,
      comments = jvm.Comments(List(s"Request wrapper for ${message.name} RPC call")),
      name = requestType,
      tparams = Nil,
      params = correlationIdParam :: messageParams,
      implicitParams = Nil,
      `extends` = None,
      implements = List(requestInterfaceType),
      members = Nil,
      staticMembers = List(createMethod)
    )

    jvm.File(requestType, jvm.Code.Tree(record), secondaryTypes = Nil, scope = Scope.Main)
  }

  /** Generate static factory method that auto-generates correlation ID */
  private def generateCreateMethod(requestType: jvm.Type.Qualified, params: List[jvm.Param[jvm.Type]]): jvm.Method = {
    val correlationIdExpr = code"$UUIDType.randomUUID().toString()"

    val constructorArgs = jvm.Ident("correlationId").code :: params.map(p => p.name.code)
    val constructorCall = requestType.construct(constructorArgs*)

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(List("Create a request with auto-generated correlation ID")),
      tparams = Nil,
      name = jvm.Ident("create"),
      params = params,
      implicitParams = Nil,
      tpe = requestType,
      throws = Nil,
      body = jvm.Body.Stmts(
        List(
          jvm.LocalVar(jvm.Ident("correlationId"), Some(StringType), correlationIdExpr).code,
          jvm.Return(constructorCall).code
        )
      ),
      isOverride = false,
      isDefault = false
    )
  }

  /** Generate response wrapper type: sealed interface with Success/Error cases */
  private def generateResponseType(message: AvroMessage, namespace: Option[String]): jvm.File = {
    val responseType = naming.avroMessageResponseTypeName(message.name, namespace)

    // For Java, add abstract method (which records satisfy automatically)
    // For Kotlin, don't add abstract member - data class properties satisfy the contract implicitly
    val correlationIdMembers: List[jvm.Method] = if (lang.isInstanceOf[LangKotlin]) {
      Nil
    } else {
      List(
        jvm.Method(
          annotations = Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = jvm.Ident("correlationId"),
          params = Nil,
          implicitParams = Nil,
          tpe = StringType,
          throws = Nil,
          body = jvm.Body.Abstract,
          isOverride = false,
          isDefault = false
        )
      )
    }

    val responseValueType = if (message.oneWay) {
      jvm.Type.Void
    } else {
      typeMapper.mapType(message.response)
    }

    val successType = responseType / jvm.Ident("Success")
    val successCase = jvm.Adt.Record(
      annotations = Nil,
      constructorAnnotations = Nil,
      isWrapper = false,
      privateConstructor = false,
      comments = jvm.Comments(List("Successful response")),
      name = successType,
      tparams = Nil,
      params = List(
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("correlationId"), StringType, None),
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("value"), responseValueType, None)
      ),
      implicitParams = Nil,
      `extends` = None,
      implements = List(responseType),
      members = Nil,
      staticMembers = Nil
    )

    val errorType = responseType / jvm.Ident("Error")
    val errorValueType = if (message.errors.size == 1) {
      message.errors.head match {
        case AvroType.Named(fullName) => jvm.Type.Qualified(jvm.QIdent(fullName))
        case other                    => typeMapper.mapType(other)
      }
    } else if (message.errors.nonEmpty) {
      naming.avroMessageErrorTypeName(message.name, namespace)
    } else {
      jvm.Type.Qualified(jvm.QIdent("java.lang.Throwable"))
    }

    val errorCase = jvm.Adt.Record(
      annotations = Nil,
      constructorAnnotations = Nil,
      isWrapper = false,
      privateConstructor = false,
      comments = jvm.Comments(List("Error response")),
      name = errorType,
      tparams = Nil,
      params = List(
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("correlationId"), StringType, None),
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("error"), errorValueType, None)
      ),
      implicitParams = Nil,
      `extends` = None,
      implements = List(responseType),
      members = Nil,
      staticMembers = Nil
    )

    // Generate annotations for JSON polymorphic deserialization
    val subtypes = List(
      (successType, "Success"),
      (errorType, "Error")
    )
    val jsonAnnotations = jsonLibSupport.sealedTypeAnnotations(subtypes, "@type")

    val sealedInterface = jvm.Adt.Sum(
      annotations = jsonAnnotations,
      comments = jvm.Comments(List(s"Response wrapper for ${message.name} RPC call")),
      name = responseType,
      tparams = Nil,
      members = correlationIdMembers,
      implements = Nil,
      subtypes = List(successCase, errorCase),
      staticMembers = Nil
    )

    jvm.File(responseType, jvm.Code.Tree(sealedInterface), secondaryTypes = Nil, scope = Scope.Main)
  }

  /** Generate RPC client that implements the service interface */
  private def generateClient(protocol: AvroProtocol): jvm.File = {
    val clientType = naming.avroServiceClientTypeName(protocol.name, protocol.namespace)
    val serviceType = naming.avroServiceTypeName(protocol.name, protocol.namespace)

    val templateVar = jvm.Ident("replyingTemplate")
    val requestTopic = s"${toTopicName(protocol.name)}-requests"

    // Use language-appropriate top type (Object for Java, Any for Kotlin)
    val AnyType = lang.topType
    val templateFieldType = framework.rpcClientFieldType(StringType, AnyType, AnyType)

    val templateParam = jvm.Param(
      Nil,
      jvm.Comments.Empty,
      templateVar,
      templateFieldType,
      None
    )

    val methods = protocol.messages.map { message =>
      generateClientMethod(message, protocol.namespace, templateVar, requestTopic)
    }

    val cls = jvm.Adt.Record(
      annotations = List(framework.serviceAnnotation),
      constructorAnnotations = framework.constructorAnnotations,
      isWrapper = false,
      privateConstructor = false,
      comments = jvm.Comments(List(s"Kafka RPC client for ${protocol.name}")),
      name = clientType,
      tparams = Nil,
      params = List(templateParam),
      implicitParams = Nil,
      `extends` = None,
      implements = Nil,
      members = methods,
      staticMembers = Nil
    )

    jvm.File(clientType, jvm.Code.Tree(cls), secondaryTypes = Nil, scope = Scope.Main)
  }

  private def generateClientMethod(
      message: AvroMessage,
      namespace: Option[String],
      templateVar: jvm.Ident,
      requestTopic: String
  ): jvm.Method = {
    val requestType = naming.avroMessageRequestTypeName(message.name, namespace)

    val params = message.request.map { field =>
      val fieldType = typeMapper.mapType(field.fieldType)
      jvm.Param(
        Nil,
        field.doc.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty),
        naming.avroFieldName(field.name),
        fieldType,
        None
      )
    }

    val requestArgsList = params.map(_.name.code)
    val createRequestCall = requestType.code.invoke("create", requestArgsList*)
    val requestVar = jvm.Ident("request")

    val ExceptionType = jvm.Type.Qualified(jvm.QIdent("java.lang.Exception"))

    if (message.oneWay) {
      // One-way: fire and forget
      val rpcCall = if (isAsync) {
        framework.rpcRequestCallAsync(templateVar, requestTopic, requestVar.code)
      } else {
        framework.rpcRequestCallBlocking(templateVar, requestTopic, requestVar.code)
      }

      val returnType = if (isAsync) framework.voidEffectType else jvm.Type.Void

      jvm.Method(
        annotations = Nil,
        comments = message.doc.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty),
        tparams = Nil,
        name = jvm.Ident(message.name),
        params = params,
        implicitParams = Nil,
        tpe = returnType,
        throws = if (isAsync) Nil else List(ExceptionType),
        body = if (isAsync) {
          // Return the effect directly (Uni<Void> / CompletableFuture<Void>)
          val replyVar = jvm.Ident("__reply")
          val lambda = jvm.Lambda(replyVar, jvm.Body.Expr(code"null"))
          val mappedCall = code"$rpcCall.map(${lambda.code})"
          jvm.Body.Stmts(
            List(
              jvm.LocalVar(requestVar, Some(requestType), createRequestCall).code,
              jvm.Return(mappedCall).code
            )
          )
        } else {
          jvm.Body.Stmts(
            List(
              jvm.LocalVar(requestVar, Some(requestType), createRequestCall).code,
              code"$rpcCall;".code
            )
          )
        },
        isOverride = false,
        isDefault = false
      )
    } else {
      // Request-reply: get response and transform to result
      val responseType = naming.avroMessageResponseTypeName(message.name, namespace)
      val replyVar = jvm.Ident("reply")

      val successType = responseType / jvm.Ident("Success")
      val errorResponseType = responseType / jvm.Ident("Error")

      val s = jvm.Ident("s")
      val e = jvm.Ident("e")

      val valueType = typeMapper.mapType(message.response)

      // Return type depends on whether errors are defined
      // Use lang-aware property access for value/error (Kotlin uses .value, Java uses .value())
      val sValueAccess = lang.propertyGetterAccess(s.code, jvm.Ident("value"))
      val eErrorAccess = lang.propertyGetterAccess(e.code, jvm.Ident("error"))
      val IllegalStateExceptionType = jvm.Type.Qualified(jvm.QIdent("java.lang.IllegalStateException"))
      val defaultError = jvm.Throw(IllegalStateExceptionType.construct(jvm.StrLit("Unexpected response type").code)).code

      val (baseReturnType, switchExpr) = if (message.errors.nonEmpty) {
        val resultType = naming.avroResultTypeName(namespace)
        val okType = resultType / jvm.Ident("Ok")
        val errType = resultType / jvm.Ident("Err")
        val msgErrorType = getErrorType(message, namespace)

        val typeSwitch = jvm.TypeSwitch(
          value = replyVar.code,
          cases = List(
            jvm.TypeSwitch.Case(successType, s, okType.construct(sValueAccess)),
            jvm.TypeSwitch.Case(errorResponseType, e, errType.construct(eErrorAccess))
          ),
          nullCase = None,
          defaultCase = Some(defaultError)
        )
        (resultType.of(valueType, msgErrorType), typeSwitch.code)
      } else {
        // No errors - just return the value directly
        val typeSwitch = jvm.TypeSwitch(
          value = replyVar.code,
          cases = List(
            jvm.TypeSwitch.Case(successType, s, sValueAccess)
          ),
          nullCase = None,
          defaultCase = Some(defaultError)
        )
        (valueType, typeSwitch.code)
      }

      if (isAsync) {
        // Async mode: return Uni<Result<T,E>> / CompletableFuture<Result<T,E>>
        val asyncRpcCall = framework.rpcRequestCallAsync(templateVar, requestTopic, requestVar.code)
        val returnType = framework.effectOf(baseReturnType)
        val lambda = jvm.Lambda(replyVar, jvm.Body.Expr(switchExpr))
        val mappedCall = code"$asyncRpcCall.map(${lambda.code})"

        jvm.Method(
          annotations = Nil,
          comments = message.doc.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty),
          tparams = Nil,
          name = jvm.Ident(message.name),
          params = params,
          implicitParams = Nil,
          tpe = returnType,
          throws = Nil,
          body = jvm.Body.Stmts(
            List(
              jvm.LocalVar(requestVar, Some(requestType), createRequestCall).code,
              jvm.Return(mappedCall).code
            )
          ),
          isOverride = false,
          isDefault = false
        )
      } else {
        // Blocking mode
        val rpcCall = framework.rpcRequestCallBlocking(templateVar, requestTopic, requestVar.code)

        jvm.Method(
          annotations = Nil,
          comments = message.doc.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty),
          tparams = Nil,
          name = jvm.Ident(message.name),
          params = params,
          implicitParams = Nil,
          tpe = baseReturnType,
          throws = List(ExceptionType),
          body = jvm.Body.Stmts(
            List(
              jvm.LocalVar(requestVar, Some(requestType), createRequestCall).code,
              jvm.LocalVar(replyVar, None, rpcCall).code,
              jvm.Return(switchExpr).code
            )
          ),
          isOverride = false,
          isDefault = false
        )
      }
    }
  }

  /** Generate RPC server that dispatches to handler */
  private def generateServer(protocol: AvroProtocol): jvm.File = {
    val serverType = naming.avroServiceServerTypeName(protocol.name, protocol.namespace)
    val handlerType = naming.avroHandlerTypeName(protocol.name, protocol.namespace)
    val requestInterfaceType = naming.avroServiceRequestInterfaceTypeName(protocol.name, protocol.namespace)

    val handlerVar = jvm.Ident("handler")
    val requestTopic = s"${toTopicName(protocol.name)}-requests"
    val replyTopic = s"${toTopicName(protocol.name)}-replies"

    val handlerParam = jvm.Param(Nil, jvm.Comments.Empty, handlerVar, handlerType, None)

    // Use language-appropriate top type (Object for Java, Any for Kotlin)
    // For Kotlin, if there are one-way methods that return null, make it nullable (Any?)
    val isKotlin = lang.isInstanceOf[LangKotlin]
    val hasOneWayMethods = protocol.messages.exists(_.oneWay)
    val TopType: jvm.Type = if (isKotlin && hasOneWayMethods) {
      jvm.Type.KotlinNullable(lang.topType)
    } else {
      lang.topType
    }

    // Generate dispatcher - returns Object/Any (Kafka serializes whatever we return)
    val dispatchCases = protocol.messages.map { message =>
      val requestType = naming.avroMessageRequestTypeName(message.name, protocol.namespace)
      val r = jvm.Ident("r")
      val handlerMethodName = jvm.Ident("handle" + message.name.capitalize)
      if (message.oneWay) {
        // One-way: call handler, return null (no reply sent)
        // Kotlin uses block expression (last expr is value), Java uses yield
        val body = if (isKotlin) {
          code"""|{
                 |  $handlerMethodName($r)
                 |  null
                 |}""".stripMargin
        } else {
          code"{ $handlerMethodName($r); yield null; }"
        }
        jvm.TypeSwitch.Case(requestType, r, body)
      } else {
        jvm.TypeSwitch.Case(requestType, r, code"$handlerMethodName($r)")
      }
    }

    val requestVar = jvm.Ident("request")
    val dispatchSwitch = jvm.TypeSwitch(
      value = requestVar.code,
      cases = dispatchCases,
      nullCase = None,
      defaultCase = None // No default needed - sealed interface is exhaustive
    )

    val handleRequestMethod = jvm.Method(
      annotations = framework.serverListenerAnnotations(requestTopic, Some(replyTopic)),
      comments = jvm.Comments(List("Dispatch incoming requests to handler methods")),
      tparams = Nil,
      name = jvm.Ident("handleRequest"),
      params = List(jvm.Param(Nil, jvm.Comments.Empty, requestVar, requestInterfaceType, None)),
      implicitParams = Nil,
      tpe = TopType,
      throws = Nil,
      body = jvm.Body.Stmts(List(jvm.Return(dispatchSwitch.code).code)),
      isOverride = false,
      isDefault = false
    )

    val handlerMethods = protocol.messages.map { message =>
      generateServerHandlerMethod(message, protocol.namespace, handlerVar)
    }

    val cls = jvm.Adt.Record(
      annotations = List(framework.serviceAnnotation),
      constructorAnnotations = framework.constructorAnnotations,
      isWrapper = false,
      privateConstructor = false,
      comments = jvm.Comments(List(s"Kafka RPC server for ${protocol.name}")),
      name = serverType,
      tparams = Nil,
      params = List(handlerParam),
      implicitParams = Nil,
      `extends` = None,
      implements = Nil,
      members = handleRequestMethod :: handlerMethods,
      staticMembers = Nil
    )

    jvm.File(serverType, jvm.Code.Tree(cls), secondaryTypes = Nil, scope = Scope.Main)
  }

  private def generateServerHandlerMethod(
      message: AvroMessage,
      namespace: Option[String],
      handlerVar: jvm.Ident
  ): jvm.Method = {
    val requestType = naming.avroMessageRequestTypeName(message.name, namespace)
    val requestVar = jvm.Ident("request")
    val requestParam = jvm.Param(Nil, jvm.Comments.Empty, requestVar, requestType, None)

    val handlerArgs = message.request.map { field =>
      val fieldName = naming.avroFieldName(field.name)
      lang.propertyGetterAccess(requestVar.code, fieldName)
    }

    val handlerCall = handlerVar.code.invoke(message.name, handlerArgs*)

    if (message.oneWay) {
      // One-way: void return, expression body
      jvm.Method(
        annotations = Nil,
        comments = jvm.Comments.Empty,
        tparams = Nil,
        name = jvm.Ident("handle" + message.name.capitalize),
        params = List(requestParam),
        implicitParams = Nil,
        tpe = jvm.Type.Void,
        throws = Nil,
        body = jvm.Body.Expr(handlerCall),
        isOverride = false,
        isDefault = false
      )
    } else {
      val responseType = naming.avroMessageResponseTypeName(message.name, namespace)
      val correlationIdExpr = lang.propertyGetterAccess(requestVar.code, jvm.Ident("correlationId"))
      val successType = responseType / jvm.Ident("Success")

      if (message.errors.nonEmpty) {
        // Handler returns Result<T, E> - pattern match and convert
        val resultType = naming.avroResultTypeName(namespace)
        val errorResponseType = responseType / jvm.Ident("Error")
        val okTypeBase = resultType / jvm.Ident("Ok")
        val errTypeBase = resultType / jvm.Ident("Err")
        // For Kotlin pattern matching, need wildcards on generic types
        val okType = if (lang.isInstanceOf[LangKotlin]) okTypeBase.of(jvm.Type.Wildcard, jvm.Type.Wildcard) else okTypeBase
        val errType = if (lang.isInstanceOf[LangKotlin]) errTypeBase.of(jvm.Type.Wildcard, jvm.Type.Wildcard) else errTypeBase

        val valueType = typeMapper.mapType(message.response)
        val msgErrorType = getErrorType(message, namespace)

        val resultVar = jvm.Ident("result")
        val ok = jvm.Ident("ok")
        val err = jvm.Ident("err")

        // Add casts because pattern matching on generic records loses type info
        val okValueAccess = lang.propertyGetterAccess(ok.code, jvm.Ident("value"))
        val errValueAccess = lang.propertyGetterAccess(err.code, jvm.Ident("error"))
        val okValueCast = jvm.Cast(valueType, okValueAccess).code
        val errValueCast = jvm.Cast(msgErrorType, errValueAccess).code
        // Kotlin needs else branch with star projections even though Result is sealed
        val needsDefault = lang.isInstanceOf[LangKotlin]
        val typeSwitch = jvm.TypeSwitch(
          value = resultVar.code,
          cases = List(
            jvm.TypeSwitch.Case(okType, ok, successType.construct(correlationIdExpr, okValueCast)),
            jvm.TypeSwitch.Case(errType, err, errorResponseType.construct(correlationIdExpr, errValueCast))
          ),
          nullCase = None,
          defaultCase = if (needsDefault) Some(code"""throw IllegalStateException("Unreachable")""") else None
        )

        jvm.Method(
          annotations = Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = jvm.Ident("handle" + message.name.capitalize),
          params = List(requestParam),
          implicitParams = Nil,
          tpe = responseType,
          throws = Nil,
          body = jvm.Body.Stmts(
            List(
              jvm.LocalVar(resultVar, None, handlerCall).code,
              jvm.Return(typeSwitch.code).code
            )
          ),
          isOverride = false,
          isDefault = false
        )
      } else {
        // Handler returns T directly - just wrap in Success
        val resultVar = jvm.Ident("result")

        jvm.Method(
          annotations = Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = jvm.Ident("handle" + message.name.capitalize),
          params = List(requestParam),
          implicitParams = Nil,
          tpe = responseType,
          throws = Nil,
          body = jvm.Body.Stmts(
            List(
              jvm.LocalVar(resultVar, None, handlerCall).code,
              jvm.Return(successType.construct(correlationIdExpr, resultVar.code)).code
            )
          ),
          isOverride = false,
          isDefault = false
        )
      }
    }
  }

  private def toTopicName(name: String): String =
    name.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase

  /** Get the error type for a message - single error type or error union */
  private def getErrorType(message: AvroMessage, namespace: Option[String]): jvm.Type = {
    if (message.errors.size == 1) {
      message.errors.head match {
        case AvroType.Named(fullName) => jvm.Type.Qualified(jvm.QIdent(fullName))
        case other                    => typeMapper.mapType(other)
      }
    } else {
      naming.avroMessageErrorTypeName(message.name, namespace)
    }
  }
}

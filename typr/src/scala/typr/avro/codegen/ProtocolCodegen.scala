package typr.avro.codegen

import typr.avro._
import typr.effects.EffectTypeOps
import typr.internal.codegen._
import typr.jvm.Code.TypeOps
import typr.{jvm, Lang, Naming, Scope}

/** Generates typed service interfaces from Avro protocols (.avpr files) */
class ProtocolCodegen(
    naming: Naming,
    lang: Lang,
    options: AvroOptions,
    typeMapper: AvroTypeMapper
) {

  private val effectOps: Option[EffectTypeOps] = options.effectType.ops

  /** Generate all files for a protocol */
  def generate(protocol: AvroProtocol): List[jvm.File] = {
    val files = List.newBuilder[jvm.File]

    // Generate error classes (simple data classes, no longer need to extend Exception)
    protocol.types.foreach {
      case error: AvroError =>
        files += generateErrorClass(error, protocol.namespace)
      case _ => // Records and enums are handled by RecordCodegen
    }

    // Generate error union types for messages with multiple errors
    protocol.messages.foreach { message =>
      if (message.errors.size > 1) {
        files += generateErrorUnionType(message, protocol.namespace)
      }
    }

    // Generate generic Result<T, E> type if any message has errors
    if (protocol.messages.exists(_.errors.nonEmpty)) {
      files += generateGenericResultType(protocol.namespace)
    }

    // Generate service interface
    files += generateServiceInterface(protocol)

    // Generate handler interface (for server implementations)
    files += generateHandlerInterface(protocol)

    files.result()
  }

  /** Generate an error data class. These are simple data classes that hold error information. They don't extend Exception - instead, methods return Result ADTs that wrap errors.
    */
  private def generateErrorClass(error: AvroError, protocolNamespace: Option[String]): jvm.File = {
    val namespace = error.namespace.orElse(protocolNamespace)
    val tpe = naming.avroErrorTypeName(error.name, namespace)
    val comments = error.doc.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty)

    val params = error.fields.map { field =>
      val fieldType = typeMapper.mapType(field.fieldType)
      jvm.Param(
        annotations = Nil,
        comments = field.doc.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty),
        name = naming.avroFieldName(field.name),
        tpe = fieldType,
        default = None
      )
    }

    val errorAdt = jvm.Adt.Record(
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
      implements = Nil,
      members = Nil,
      staticMembers = Nil
    )

    jvm.File(tpe, jvm.Code.Tree(errorAdt), secondaryTypes = Nil, scope = Scope.Main)
  }

  /** Generate an error union type when a message can throw multiple errors.
    *
    * Generates: sealed interface <MessageName>Error { record <ErrorName>(<ErrorType> error) implements <MessageName>Error {} ... }
    */
  private def generateErrorUnionType(message: AvroMessage, protocolNamespace: Option[String]): jvm.File = {
    val errorUnionType = naming.avroMessageErrorTypeName(message.name, protocolNamespace)
    val comments = jvm.Comments(List(s"Error union type for ${message.name} - one of the possible error outcomes"))

    // Generate a case for each error type
    val cases = message.errors.collect { case AvroType.Named(fullName) =>
      val errorType = jvm.Type.Qualified(jvm.QIdent(fullName))
      val caseName = fullName.split('.').last // Use simple name for case
      val caseType = errorUnionType / jvm.Ident(caseName)

      jvm.Adt.Record(
        annotations = Nil,
        constructorAnnotations = Nil,
        isWrapper = false,
        privateConstructor = false,
        comments = jvm.Comments.Empty,
        name = caseType,
        tparams = Nil,
        params = List(jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("error"), errorType, None)),
        implicitParams = Nil,
        `extends` = None,
        implements = List(errorUnionType),
        members = Nil,
        staticMembers = Nil
      )
    }

    val sealedInterface = jvm.Adt.Sum(
      annotations = Nil,
      comments = comments,
      name = errorUnionType,
      tparams = Nil,
      members = Nil,
      implements = Nil,
      subtypes = cases,
      staticMembers = Nil
    )

    jvm.File(errorUnionType, jvm.Code.Tree(sealedInterface), secondaryTypes = Nil, scope = Scope.Main)
  }

  /** Generate a generic Result<T, E> type for the protocol.
    *
    * Generates: sealed interface Result<T, E> { record Ok<T, E>(T value) implements Result<T, E> {} record Err<T, E>(E error) implements Result<T, E> {} }
    */
  private def generateGenericResultType(protocolNamespace: Option[String]): jvm.File = {
    val resultType = naming.avroResultTypeName(protocolNamespace)
    val comments = jvm.Comments(List("Generic result type - either success value or error"))

    // Type parameters
    val T = jvm.Ident("T")
    val E = jvm.Ident("E")
    val TType = jvm.Type.Abstract(T)
    val EType = jvm.Type.Abstract(E)

    // The Result<T, E> type with type parameters
    val resultWithParams = resultType.of(TType, EType)

    // Ok case: record Ok<T, E>(T value) implements Result<T, E>
    val okType = resultType / jvm.Ident("Ok")
    val okCase = jvm.Adt.Record(
      annotations = Nil,
      constructorAnnotations = Nil,
      isWrapper = false,
      privateConstructor = false,
      comments = jvm.Comments(List("Successful result")),
      name = okType,
      tparams = List(TType, EType),
      params = List(jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("value"), TType, None)),
      implicitParams = Nil,
      `extends` = None,
      implements = List(resultWithParams),
      members = Nil,
      staticMembers = Nil
    )

    // Err case: record Err<T, E>(E error) implements Result<T, E>
    val errType = resultType / jvm.Ident("Err")
    val errCase = jvm.Adt.Record(
      annotations = Nil,
      constructorAnnotations = Nil,
      isWrapper = false,
      privateConstructor = false,
      comments = jvm.Comments(List("Error result")),
      name = errType,
      tparams = List(TType, EType),
      params = List(jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("error"), EType, None)),
      implicitParams = Nil,
      `extends` = None,
      implements = List(resultWithParams),
      members = Nil,
      staticMembers = Nil
    )

    val sealedInterface = jvm.Adt.Sum(
      annotations = Nil,
      comments = comments,
      name = resultType,
      tparams = List(TType, EType),
      members = Nil,
      implements = Nil,
      subtypes = List(okCase, errCase),
      staticMembers = Nil
    )

    jvm.File(resultType, jvm.Code.Tree(sealedInterface), secondaryTypes = Nil, scope = Scope.Main)
  }

  /** Generate the service interface with a method per message */
  private def generateServiceInterface(protocol: AvroProtocol): jvm.File = {
    val tpe = naming.avroServiceTypeName(protocol.name, protocol.namespace)
    val comments = protocol.doc.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty)

    val methods = protocol.messages.map { message =>
      generateServiceMethod(message, protocol.namespace)
    }

    // Use jvm.Class with Interface type to get a regular interface (not sealed)
    val serviceInterface = jvm.Class(
      annotations = Nil,
      comments = comments,
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

  /** Generate a method signature for a service message.
    *
    * For messages with errors: returns Result<T, E> (no throws clause) For messages without errors: returns response type directly
    */
  private def generateServiceMethod(
      message: AvroMessage,
      protocolNamespace: Option[String]
  ): jvm.Method = {
    val methodName = jvm.Ident(message.name)
    val comments = message.doc.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty)

    // Convert request fields to parameters
    val params = message.request.map { field =>
      val fieldType = typeMapper.mapType(field.fieldType)
      jvm.Param(
        annotations = Nil,
        comments = field.doc.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty),
        name = naming.avroFieldName(field.name),
        tpe = fieldType,
        default = None
      )
    }

    // Return type depends on whether errors are defined:
    // - With errors: Result<T, E>
    // - Without errors: T directly (or void for one-way)
    val rawReturnType = if (message.errors.nonEmpty) {
      val resultType = naming.avroResultTypeName(protocolNamespace)
      val responseType = if (message.oneWay) {
        jvm.Type.Qualified(jvm.QIdent("java.lang.Void"))
      } else {
        typeMapper.mapType(message.response)
      }
      val errorType = getErrorType(message, protocolNamespace)
      resultType.of(responseType, errorType)
    } else if (message.oneWay) {
      jvm.Type.Void
    } else {
      typeMapper.mapType(message.response)
    }

    // Wrap in effect type if configured
    // Note: primitive void must become boxed Void when used as a type parameter
    val returnType = effectOps match {
      case Some(ops) =>
        val boxedReturnType = if (rawReturnType == jvm.Type.Void) {
          jvm.Type.Qualified(jvm.QIdent("java.lang.Void"))
        } else {
          rawReturnType
        }
        ops.tpe.of(boxedReturnType)
      case None => rawReturnType
    }

    // No throws clause - errors are encoded in the Result ADT
    jvm.Method(
      annotations = Nil,
      comments = comments,
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

  /** Get the error type for a message - single error type or error union */
  private def getErrorType(message: AvroMessage, protocolNamespace: Option[String]): jvm.Type = {
    if (message.errors.size == 1) {
      message.errors.head match {
        case AvroType.Named(fullName) => jvm.Type.Qualified(jvm.QIdent(fullName))
        case other                    => typeMapper.mapType(other)
      }
    } else {
      naming.avroMessageErrorTypeName(message.name, protocolNamespace)
    }
  }

  /** Generate handler interface (for implementing servers). This is a marker interface that extends the service interface. Implementations use this to indicate they're server-side handlers.
    */
  private def generateHandlerInterface(protocol: AvroProtocol): jvm.File = {
    val tpe = naming.avroHandlerTypeName(protocol.name, protocol.namespace)
    val serviceTpe = naming.avroServiceTypeName(protocol.name, protocol.namespace)
    val comments = jvm.Comments(List(s"Handler interface for ${protocol.name} protocol"))

    // Handler is a marker interface - it extends the service interface
    // without redeclaring methods. This works across all languages.
    val handlerInterface = jvm.Class(
      annotations = Nil,
      comments = comments,
      classType = jvm.ClassType.Interface,
      name = tpe,
      tparams = Nil,
      params = Nil,
      implicitParams = Nil,
      `extends` = Some(serviceTpe),
      implements = Nil,
      members = Nil,
      staticMembers = Nil
    )

    jvm.File(tpe, jvm.Code.Tree(handlerInterface), secondaryTypes = Nil, scope = Scope.Main)
  }
}

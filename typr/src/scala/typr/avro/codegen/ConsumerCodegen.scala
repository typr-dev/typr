package typr.avro.codegen

import typr.avro._
import typr.effects.EffectTypeOps
import typr.jvm.Code.{CodeOps, TreeOps}
import typr.internal.codegen._
import typr.{jvm, Lang, Naming, Scope}

/** Generates typed Kafka consumer wrappers and handler interfaces */
class ConsumerCodegen(
    naming: Naming,
    lang: Lang,
    options: AvroOptions
) {

  // Effect type configuration
  private val effectOps: Option[EffectTypeOps] = options.effectType.ops

  // Handler return type: Effect[Unit] for async, void for blocking
  // For Java, uses java.lang.Void (boxed) since generic type parameters require boxed types
  // For Scala/Kotlin, uses their native Unit type
  private def handlerReturnType: jvm.Type = effectOps match {
    case Some(ops) => jvm.Type.TApply(ops.tpe, List(lang.voidType))
    case None      => jvm.Type.Void
  }

  // Kafka types
  private val ConsumerType = jvm.Type.Qualified(jvm.QIdent("org.apache.kafka.clients.consumer.Consumer"))
  private val ConsumerRecordsType = jvm.Type.Qualified(jvm.QIdent("org.apache.kafka.clients.consumer.ConsumerRecords"))
  private val DurationType = jvm.Type.Qualified(jvm.QIdent("java.time.Duration"))

  // Java types
  private val CloseableType = jvm.Type.Qualified(jvm.QIdent("java.io.Closeable"))
  private val AutoCloseableType = jvm.Type.Qualified(jvm.QIdent("java.lang.AutoCloseable"))
  private val UUID = jvm.Type.Qualified(jvm.QIdent("java.util.UUID"))
  private val IllegalStateExceptionType = jvm.Type.Qualified(jvm.QIdent("java.lang.IllegalStateException"))

  /** Generate a typed consumer and handler for a single record type */
  def generateConsumer(record: AvroRecord): List[jvm.File] = {
    val topicName = options.topicMapping.getOrElse(record.fullName, toTopicName(record.name))
    val keyType = options.topicKeys.getOrElse(topicName, options.defaultKeyType)
    val headerSchemaName = options.topicHeaders.getOrElse(topicName, options.defaultHeaderSchema.orNull)
    val headerType = Option(headerSchemaName).map(name => jvm.Type.Qualified(naming.avroHeaderClassName(name)))

    List(
      generateHandlerInterface(topicName, keyTypeToJvmType(keyType), naming.avroRecordTypeName(record.name, record.namespace), headerType, members = Nil),
      generateConsumerClass(topicName, keyTypeToJvmType(keyType), naming.avroRecordTypeName(record.name, record.namespace), headerType, members = Nil)
    )
  }

  /** Generate a typed consumer and handler for an event group */
  def generateEventGroupConsumer(group: AvroEventGroup): List[jvm.File] = {
    val topicName = toTopicName(group.name)
    val keyType = options.topicKeys.getOrElse(topicName, options.defaultKeyType)
    val headerSchemaName = options.topicHeaders.getOrElse(topicName, options.defaultHeaderSchema.orNull)
    val headerType = Option(headerSchemaName).map(name => jvm.Type.Qualified(naming.avroHeaderClassName(name)))

    List(
      generateHandlerInterface(topicName, keyTypeToJvmType(keyType), naming.avroEventGroupTypeName(group.name, group.namespace), headerType, members = group.members),
      generateConsumerClass(topicName, keyTypeToJvmType(keyType), naming.avroEventGroupTypeName(group.name, group.namespace), headerType, members = group.members)
    )
  }

  /** Generate a handler interface with handle methods */
  private def generateHandlerInterface(
      topicName: String,
      keyType: jvm.Type,
      valueType: jvm.Type.Qualified,
      headerType: Option[jvm.Type.Qualified],
      members: List[AvroRecord]
  ): jvm.File = {
    val handlerTypeName = jvm.Type.Qualified(naming.avroHandlerName(topicName))

    val methods: List[jvm.Method] = if (members.isEmpty) {
      // Single-event topic: just one handle method
      List(generateHandleMethod(keyType, valueType, headerType, isAbstract = true))
    } else {
      // Multi-event topic: one method per member type + handleUnknown
      val memberMethods = members.map { member =>
        generateMemberHandleMethod(member, keyType, headerType, isAbstract = true)
      }
      val unknownMethod = generateHandleUnknownMethod(keyType, valueType, headerType)
      memberMethods ++ List(unknownMethod)
    }

    // Use Class with ClassType.Interface for a regular interface
    val handlerInterface = jvm.Class(
      annotations = Nil,
      comments = jvm.Comments(List(s"Handler interface for $topicName topic events")),
      classType = jvm.ClassType.Interface,
      name = handlerTypeName,
      tparams = Nil,
      params = Nil,
      implicitParams = Nil,
      `extends` = None,
      implements = Nil,
      members = methods,
      staticMembers = Nil
    )

    jvm.File(
      handlerTypeName,
      jvm.Code.Tree(handlerInterface),
      secondaryTypes = Nil,
      scope = Scope.Main
    )
  }

  /** Generate the main handle method for single-event topics */
  private def generateHandleMethod(
      keyType: jvm.Type,
      valueType: jvm.Type,
      headerType: Option[jvm.Type.Qualified],
      isAbstract: Boolean
  ): jvm.Method = {
    val keyParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("key"), keyType, None)
    val valueParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("value"), valueType, None)
    val headerParams = headerType.map(ht => jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("headers"), ht, None)).toList

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(List("Handle a message from the topic")),
      tparams = Nil,
      name = jvm.Ident("handle"),
      params = List(keyParam, valueParam) ++ headerParams,
      implicitParams = Nil,
      tpe = handlerReturnType,
      throws = Nil,
      body = if (isAbstract) jvm.Body.Abstract else jvm.Body.Stmts(Nil),
      isOverride = false,
      isDefault = false
    )
  }

  /** Generate a handle method for a specific member type in event groups */
  private def generateMemberHandleMethod(
      member: AvroRecord,
      keyType: jvm.Type,
      headerType: Option[jvm.Type.Qualified],
      isAbstract: Boolean
  ): jvm.Method = {
    val methodName = s"handle${member.name}"
    val memberType = naming.avroRecordTypeName(member.name, member.namespace)

    val keyParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("key"), keyType, None)
    val eventParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("event"), memberType, None)
    val headerParams = headerType.map(ht => jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("headers"), ht, None)).toList

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(List(s"Handle a ${member.name} event")),
      tparams = Nil,
      name = jvm.Ident(methodName),
      params = List(keyParam, eventParam) ++ headerParams,
      implicitParams = Nil,
      tpe = handlerReturnType,
      throws = Nil,
      body = if (isAbstract) jvm.Body.Abstract else jvm.Body.Stmts(Nil),
      isOverride = false,
      isDefault = false
    )
  }

  /** Generate handleUnknown default method for event groups */
  private def generateHandleUnknownMethod(
      keyType: jvm.Type,
      valueType: jvm.Type,
      headerType: Option[jvm.Type.Qualified]
  ): jvm.Method = {
    val keyParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("key"), keyType, None)
    val eventParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("event"), valueType, None)
    val headerParams = headerType.map(ht => jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("headers"), ht, None)).toList

    // throw new IllegalStateException("Unknown event type: " + event.getClass())
    // Kotlin uses javaClass property instead of getClass() method
    val getClassCall = lang.extension match {
      case "kt" => jvm.Ident("event").code.select("javaClass")
      case _    => lang.nullaryMethodCall(jvm.Ident("event").code, jvm.Ident("getClass"))
    }
    val errorMessage = code"${jvm.StrLit("Unknown event type: ").code} + $getClassCall"
    val throwExpr = jvm.Throw(jvm.New(IllegalStateExceptionType.code, List(jvm.Arg.Pos(errorMessage))).code).code

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(List("Handle unknown event types (default throws exception)")),
      tparams = Nil,
      name = jvm.Ident("handleUnknown"),
      params = List(keyParam, eventParam) ++ headerParams,
      implicitParams = Nil,
      tpe = handlerReturnType,
      throws = Nil,
      body = jvm.Body.Stmts(List(throwExpr)),
      isOverride = false,
      isDefault = true
    )
  }

  /** Generate a consumer class that wraps Kafka consumer and dispatches to handler */
  private def generateConsumerClass(
      topicName: String,
      keyType: jvm.Type,
      valueType: jvm.Type.Qualified,
      headerType: Option[jvm.Type.Qualified],
      members: List[AvroRecord]
  ): jvm.File = {
    val consumerTypeName = jvm.Type.Qualified(naming.avroConsumerName(topicName))
    val handlerTypeName = jvm.Type.Qualified(naming.avroHandlerName(topicName))
    val consumerFieldType = ConsumerType.of(keyType, valueType)

    // Constructor parameters
    val consumerParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("consumer"), consumerFieldType, None)
    val handlerParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("handler"), handlerTypeName, None)
    val topicParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("topic"), lang.String, Some(jvm.StrLit(topicName).code))

    // Methods
    val pollMethod = generatePollMethod(keyType, valueType, headerType, members)
    val closeMethod = generateCloseMethod()

    val methods = List(pollMethod, closeMethod)

    val consumerRecord = jvm.Adt.Record(
      annotations = Nil,
      constructorAnnotations = Nil,
      isWrapper = false,
      privateConstructor = false,
      comments = jvm.Comments(List(s"Type-safe consumer for $topicName topic")),
      name = consumerTypeName,
      tparams = Nil,
      params = List(consumerParam, handlerParam, topicParam),
      implicitParams = Nil,
      `extends` = None,
      implements = List(closeableInterface),
      members = methods,
      staticMembers = Nil
    )

    // Add wildcard imports needed by effectOps.foreachDiscard
    val additionalImports = effectOps.map(_.foreachDiscardImports).getOrElse(Nil)

    jvm.File(
      consumerTypeName,
      jvm.Code.Tree(consumerRecord),
      secondaryTypes = Nil,
      scope = Scope.Main,
      additionalImports = additionalImports
    )
  }

  private def closeableInterface: jvm.Type = lang.extension match {
    case "kt" => CloseableType
    case _    => AutoCloseableType
  }

  /** Generate poll method that dispatches to handler */
  private def generatePollMethod(
      keyType: jvm.Type,
      valueType: jvm.Type,
      headerType: Option[jvm.Type.Qualified],
      members: List[AvroRecord]
  ): jvm.Method = {
    effectOps match {
      case Some(ops) =>
        generateAsyncPollMethod(keyType, valueType, headerType, members, ops)
      case None =>
        generateBlockingPollMethod(keyType, valueType, headerType, members)
    }
  }

  /** Generate blocking poll method (returns void, executes handlers synchronously) */
  private def generateBlockingPollMethod(
      keyType: jvm.Type,
      valueType: jvm.Type,
      headerType: Option[jvm.Type.Qualified],
      members: List[AvroRecord]
  ): jvm.Method = {
    val durationParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("timeout"), DurationType, None)

    // ConsumerRecords<K, V> records = consumer.poll(timeout)
    val consumerRecordsType = ConsumerRecordsType.of(keyType, valueType)
    val pollCall = jvm.Ident("consumer").code.invoke("poll", jvm.Ident("timeout").code)
    val recordsVar = jvm.LocalVar(jvm.Ident("records"), Some(consumerRecordsType), pollCall)

    // Build the loop body - depends on whether we have members (event group) or not
    val loopBody = if (members.isEmpty) {
      // Single event type: just call handler.handle(key, value, headers)
      buildSingleEventLoopBody(keyType, valueType, headerType)
    } else {
      // Event group: switch on event type
      buildEventGroupLoopBody(keyType, valueType, headerType, members)
    }

    // Generate for-each loop using Java's Iterable.forEach
    val lambdaBody = jvm.Body.Stmts(loopBody)
    val lambda = jvm.Lambda(List(jvm.LambdaParam(jvm.Ident("record"))), lambdaBody)
    val forLoop = jvm.Ident("records").code.invoke("forEach", lambda.code)

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(List("Poll for messages and dispatch to handler")),
      tparams = Nil,
      name = jvm.Ident("poll"),
      params = List(durationParam),
      implicitParams = Nil,
      tpe = jvm.Type.Void,
      throws = Nil,
      body = jvm.Body.Stmts(List(recordsVar.code, forLoop)),
      isOverride = false,
      isDefault = false
    )
  }

  /** Generate async poll method (returns Effect[Unit], composes handler effects) */
  private def generateAsyncPollMethod(
      keyType: jvm.Type,
      valueType: jvm.Type,
      headerType: Option[jvm.Type.Qualified],
      members: List[AvroRecord],
      ops: EffectTypeOps
  ): jvm.Method = {
    val durationParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("timeout"), DurationType, None)
    val returnType = jvm.Type.TApply(ops.tpe, List(lang.voidType))

    // ConsumerRecords<K, V> records = consumer.poll(timeout)
    val consumerRecordsType = ConsumerRecordsType.of(keyType, valueType)
    val pollCall = jvm.Ident("consumer").code.invoke("poll", jvm.Ident("timeout").code)
    val recordsVar = jvm.LocalVar(jvm.Ident("records"), Some(consumerRecordsType), pollCall)

    // Build the handler body that processes a single record
    val handlerBody = buildAsyncHandlerBody(headerType, members)

    // Use foreachDiscard to traverse records
    val foreachExpr = ops.foreachDiscard(jvm.Ident("records").code, jvm.Ident("record"), handlerBody)

    // return foreachDiscard(records, record -> ...)
    val returnStmt = jvm.Return(foreachExpr)

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(List("Poll for messages and dispatch to handler, returning composed effect")),
      tparams = Nil,
      name = jvm.Ident("poll"),
      params = List(durationParam),
      implicitParams = Nil,
      tpe = returnType,
      throws = Nil,
      body = jvm.Body.Stmts(List(recordsVar.code, returnStmt.code)),
      isOverride = false,
      isDefault = false
    )
  }

  /** Build the handler body expression that processes a single record and returns Effect[Unit] */
  private def buildAsyncHandlerBody(
      headerType: Option[jvm.Type.Qualified],
      members: List[AvroRecord]
  ): jvm.Code = {
    // Build handler call with key, value, and optional headers from record
    val keyExpr = lang.nullaryMethodCall(jvm.Ident("record").code, jvm.Ident("key"))
    val valueExpr = lang.nullaryMethodCall(jvm.Ident("record").code, jvm.Ident("value"))

    val headerExpr = headerType.map { ht =>
      val recordHeaders = lang.nullaryMethodCall(jvm.Ident("record").code, jvm.Ident("headers"))
      ht.code.invoke("fromHeaders", recordHeaders)
    }

    if (members.isEmpty) {
      // Single event: handler.handle(key, value, headers)
      val handlerArgs = List(keyExpr, valueExpr) ++ headerExpr.toList
      jvm.Ident("handler").code.invoke("handle", handlerArgs*)
    } else {
      // Event group: switch/match that returns the effect
      generateAsyncEventSwitch(keyExpr, valueExpr, headerExpr, members)
    }
  }

  /** Generate switch/match statement for event dispatch that returns effect */
  private def generateAsyncEventSwitch(
      keyExpr: jvm.Code,
      valueExpr: jvm.Code,
      headerExpr: Option[jvm.Code],
      members: List[AvroRecord]
  ): jvm.Code = {
    val headerArgs = headerExpr.toList

    // Build cases for each member type - each returns the effect from handler
    val cases = members.map { member =>
      val memberType = naming.avroRecordTypeName(member.name, member.namespace)
      val handlerArgs = List(keyExpr, jvm.Ident("e").code) ++ headerArgs
      val handleCall = jvm.Ident("handler").code.invoke(s"handle${member.name}", handlerArgs*)
      jvm.TypeSwitch.Case(memberType, jvm.Ident("e"), handleCall)
    }

    // Default case returns handleUnknown effect
    val defaultHandlerArgs = List(keyExpr, valueExpr) ++ headerArgs
    val defaultHandleCall = jvm.Ident("handler").code.invoke("handleUnknown", defaultHandlerArgs*)

    jvm
      .TypeSwitch(
        value = valueExpr,
        cases = cases,
        nullCase = None,
        defaultCase = Some(defaultHandleCall)
      )
      .code
  }

  /** Build loop body for single event topics */
  private def buildSingleEventLoopBody(
      keyType: jvm.Type,
      valueType: jvm.Type,
      headerType: Option[jvm.Type.Qualified]
  ): List[jvm.Code] = {
    // K key = record.key()
    val keyCall = lang.nullaryMethodCall(jvm.Ident("record").code, jvm.Ident("key"))
    val keyVar = jvm.LocalVar(jvm.Ident("key"), Some(keyType), keyCall)

    // V value = record.value()
    val valueCall = lang.nullaryMethodCall(jvm.Ident("record").code, jvm.Ident("value"))
    val valueVar = jvm.LocalVar(jvm.Ident("value"), Some(valueType), valueCall)

    val headerVars = headerType.map { ht =>
      // Headers headers = StandardHeaders.fromHeaders(record.headers())
      val recordHeaders = lang.nullaryMethodCall(jvm.Ident("record").code, jvm.Ident("headers"))
      val fromHeadersCall = ht.code.invoke("fromHeaders", recordHeaders)
      jvm.LocalVar(jvm.Ident("headers"), Some(ht), fromHeadersCall)
    }.toList

    // handler.handle(key, value, headers)
    val handlerArgs = List(jvm.Ident("key").code, jvm.Ident("value").code) ++
      headerType.map(_ => jvm.Ident("headers").code).toList
    val handleCall = jvm.Stmt.simple(jvm.Ident("handler").code.invoke("handle", handlerArgs*))

    List(keyVar.code, valueVar.code) ++ headerVars.map(_.code) ++ List(handleCall.code)
  }

  /** Build loop body for event group topics with switch/match */
  private def buildEventGroupLoopBody(
      keyType: jvm.Type,
      valueType: jvm.Type,
      headerType: Option[jvm.Type.Qualified],
      members: List[AvroRecord]
  ): List[jvm.Code] = {
    // K key = record.key()
    val keyCall = lang.nullaryMethodCall(jvm.Ident("record").code, jvm.Ident("key"))
    val keyVar = jvm.LocalVar(jvm.Ident("key"), Some(keyType), keyCall)

    // V value = record.value()
    val valueCall = lang.nullaryMethodCall(jvm.Ident("record").code, jvm.Ident("value"))
    val valueVar = jvm.LocalVar(jvm.Ident("value"), Some(valueType), valueCall)

    val headerVars = headerType.map { ht =>
      val recordHeaders = lang.nullaryMethodCall(jvm.Ident("record").code, jvm.Ident("headers"))
      val fromHeadersCall = ht.code.invoke("fromHeaders", recordHeaders)
      jvm.LocalVar(jvm.Ident("headers"), Some(ht), fromHeadersCall)
    }.toList

    // Generate switch/match based on language
    val switchExpr = generateEventSwitch(headerType, members)

    List(keyVar.code, valueVar.code) ++ headerVars.map(_.code) ++ List(switchExpr)
  }

  /** Generate switch/match statement for event dispatch */
  private def generateEventSwitch(
      headerType: Option[jvm.Type.Qualified],
      members: List[AvroRecord]
  ): jvm.Code = {
    val headerArgs = headerType.map(_ => jvm.Ident("headers").code).toList

    // Build cases for each member type using TypeSwitch
    val cases = members.map { member =>
      val memberType = naming.avroRecordTypeName(member.name, member.namespace)
      val handlerArgs = List(jvm.Ident("key").code, jvm.Ident("e").code) ++ headerArgs
      val handleCall = jvm.Stmt.simple(jvm.Ident("handler").code.invoke(s"handle${member.name}", handlerArgs*))
      jvm.TypeSwitch.Case(memberType, jvm.Ident("e"), handleCall.code)
    }

    // Add default case
    val defaultHandlerArgs = List(jvm.Ident("key").code, jvm.Ident("value").code) ++ headerArgs
    val defaultHandleCall = jvm.Stmt.simple(jvm.Ident("handler").code.invoke("handleUnknown", defaultHandlerArgs*))

    jvm
      .TypeSwitch(
        value = jvm.Ident("value").code,
        cases = cases,
        nullCase = None,
        defaultCase = Some(defaultHandleCall.code)
      )
      .code
  }

  /** Generate close method */
  private def generateCloseMethod(): jvm.Method = {
    val closeCall = lang.nullaryMethodCall(jvm.Ident("consumer").code, jvm.Ident("close"))

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(List("Close the consumer")),
      tparams = Nil,
      name = jvm.Ident("close"),
      params = Nil,
      implicitParams = Nil,
      tpe = jvm.Type.Void,
      throws = Nil,
      body = jvm.Body.Stmts(List(jvm.Stmt.simple(closeCall).code)),
      isOverride = true,
      isDefault = false
    )
  }

  /** Convert a name to topic name format (kebab-case) */
  private def toTopicName(name: String): String = {
    name.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase
  }

  /** Convert KeyType to JVM type */
  private def keyTypeToJvmType(keyType: KeyType): jvm.Type = keyType match {
    case KeyType.StringKey    => lang.String
    case KeyType.UUIDKey      => UUID
    case KeyType.LongKey      => lang.Long
    case KeyType.IntKey       => lang.Int
    case KeyType.BytesKey     => lang.ByteArrayType
    case KeyType.SchemaKey(_) => lang.String
  }
}

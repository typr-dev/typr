package typr.avro.codegen

import typr.avro._
import typr.effects.{EffectType, EffectTypeOps}
import typr.jvm.Code.{CodeOps, TreeOps, TypeOps}
import typr.internal.codegen._
import typr.{jvm, Lang, Naming, Scope}

/** Generates type-safe Kafka producer wrappers */
class ProducerCodegen(
    naming: Naming,
    lang: Lang,
    options: AvroOptions
) {

  // Effect type configuration
  private val effectOps: Option[EffectTypeOps] = options.effectType.ops

  // Kafka types
  private val ProducerType = jvm.Type.Qualified(jvm.QIdent("org.apache.kafka.clients.producer.Producer"))
  private val ProducerRecordType = jvm.Type.Qualified(jvm.QIdent("org.apache.kafka.clients.producer.ProducerRecord"))
  private val RecordMetadataType = jvm.Type.Qualified(jvm.QIdent("org.apache.kafka.clients.producer.RecordMetadata"))
  private val CallbackType = jvm.Type.Qualified(jvm.QIdent("org.apache.kafka.clients.producer.Callback"))
  private val CompletableFutureType = jvm.Type.Qualified(jvm.QIdent("java.util.concurrent.CompletableFuture"))

  // Java types
  private val FutureType = jvm.Type.Qualified(jvm.QIdent("java.util.concurrent.Future"))
  private val AutoCloseableType = jvm.Type.Qualified(jvm.QIdent("java.lang.AutoCloseable"))
  private val CloseableType = jvm.Type.Qualified(jvm.QIdent("java.io.Closeable"))
  private val UUID = jvm.Type.Qualified(jvm.QIdent("java.util.UUID"))

  /** Generate a typed producer for a single record type */
  def generateProducer(record: AvroRecord): jvm.File = {
    val topicName = options.topicMapping.getOrElse(record.fullName, toTopicName(record.name))
    val keyType = options.topicKeys.getOrElse(topicName, options.defaultKeyType)
    val headerSchemaName = options.topicHeaders.getOrElse(topicName, options.defaultHeaderSchema.orNull)
    val headerType = Option(headerSchemaName).map(name => jvm.Type.Qualified(naming.avroHeaderClassName(name)))

    generateProducerClass(
      topicName,
      keyTypeToJvmType(keyType),
      naming.avroRecordTypeName(record.name, record.namespace),
      headerType
    )
  }

  /** Generate a typed producer for an event group */
  def generateEventGroupProducer(group: AvroEventGroup): jvm.File = {
    val topicName = toTopicName(group.name)
    val keyType = options.topicKeys.getOrElse(topicName, options.defaultKeyType)
    val headerSchemaName = options.topicHeaders.getOrElse(topicName, options.defaultHeaderSchema.orNull)
    val headerType = Option(headerSchemaName).map(name => jvm.Type.Qualified(naming.avroHeaderClassName(name)))

    generateProducerClass(
      topicName,
      keyTypeToJvmType(keyType),
      naming.avroEventGroupTypeName(group.name, group.namespace),
      headerType
    )
  }

  private def generateProducerClass(
      topicName: String,
      keyType: jvm.Type,
      valueType: jvm.Type.Qualified,
      headerType: Option[jvm.Type.Qualified]
  ): jvm.File = {
    val producerTypeName = jvm.Type.Qualified(naming.avroProducerName(topicName))
    val producerFieldType = ProducerType.of(keyType, valueType)

    // Constructor parameters: producer and topic
    val producerParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("producer"), producerFieldType, None)
    val topicParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("topic"), lang.String, Some(jvm.StrLit(topicName).code))

    // Methods
    val sendMethod = generateSendMethod(keyType, valueType)
    val sendWithHeadersMethod = headerType.map(ht => generateSendWithHeadersMethod(keyType, valueType, ht))
    val closeMethod = generateCloseMethod()

    val methods = List(sendMethod) ++ sendWithHeadersMethod.toList ++ List(closeMethod)

    // Generate as a record/data class with methods
    val producerRecord = jvm.Adt.Record(
      annotations = Nil,
      constructorAnnotations = Nil,
      isWrapper = false,
      privateConstructor = false,
      comments = jvm.Comments(List(s"Type-safe producer for $topicName topic")),
      name = producerTypeName,
      tparams = Nil,
      params = List(producerParam, topicParam),
      implicitParams = Nil,
      `extends` = None,
      implements = List(closeableInterface),
      members = methods,
      staticMembers = Nil
    )

    jvm.File(
      producerTypeName,
      jvm.Code.Tree(producerRecord),
      secondaryTypes = Nil,
      scope = Scope.Main
    )
  }

  private def closeableInterface: jvm.Type = lang.extension match {
    case "kt" => CloseableType
    case _    => AutoCloseableType
  }

  private def generateSendMethod(keyType: jvm.Type, valueType: jvm.Type): jvm.Method = {
    val keyParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("key"), keyType, None)
    val valueParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("value"), valueType, None)

    val recordExpr = jvm
      .New(
        ProducerRecordType.of(keyType, valueType).code,
        List(
          jvm.Arg.Pos(jvm.Ident("topic").code),
          jvm.Arg.Pos(jvm.Ident("key").code),
          jvm.Arg.Pos(jvm.Ident("value").code)
        )
      )
      .code

    effectOps match {
      case Some(ops) =>
        // Wrap producer.send in the effect type using CompletableFuture callback
        val (returnType, body) = generateAsyncSendBody(recordExpr, ops)
        jvm.Method(
          annotations = Nil,
          comments = jvm.Comments(List("Send a message to the topic asynchronously")),
          tparams = Nil,
          name = jvm.Ident("send"),
          params = List(keyParam, valueParam),
          implicitParams = Nil,
          tpe = returnType,
          throws = Nil,
          body = jvm.Body.Stmts(body),
          isOverride = false,
          isDefault = false
        )

      case None =>
        // Blocking: return Future<RecordMetadata> (current behavior)
        val sendExpr = jvm.Ident("producer").code.invoke("send", recordExpr)
        jvm.Method(
          annotations = Nil,
          comments = jvm.Comments(List("Send a message to the topic")),
          tparams = Nil,
          name = jvm.Ident("send"),
          params = List(keyParam, valueParam),
          implicitParams = Nil,
          tpe = FutureType.of(RecordMetadataType),
          throws = Nil,
          body = jvm.Body.Stmts(List(jvm.Return(sendExpr).code)),
          isOverride = false,
          isDefault = false
        )
    }
  }

  /** Generate async send body using proper lazy async pattern */
  private def generateAsyncSendBody(recordExpr: jvm.Code, ops: EffectTypeOps): (jvm.Type, List[jvm.Code]) = {
    val returnType = jvm.Type.TApply(ops.tpe, List(RecordMetadataType))

    val asyncExpr = ops.async(RecordMetadataType) { (onSuccess, onFailure) =>
      // Build Kafka callback: (result, exception) -> { if (exception != null) onFailure(exception) else onSuccess(result) }
      val result = jvm.Ident("result")
      val exception = jvm.Ident("exception")

      val callbackBody = jvm.If(
        List(jvm.If.Branch(code"$exception != null", jvm.Stmt.simple(onFailure(exception.code)).code)),
        Some(jvm.Stmt.simple(onSuccess(result.code)).code)
      )
      val callback = jvm.Lambda(
        List(jvm.LambdaParam(result), jvm.LambdaParam(exception)),
        jvm.Body.Stmts(List(callbackBody.code))
      )

      // Build the send call with the callback
      jvm.Ident("producer").code.invoke("send", recordExpr, callback.code)
    }

    val stmts = List(jvm.Return(asyncExpr).code)
    (returnType, stmts)
  }

  private def generateSendWithHeadersMethod(keyType: jvm.Type, valueType: jvm.Type, headerType: jvm.Type.Qualified): jvm.Method = {
    val keyParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("key"), keyType, None)
    val valueParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("value"), valueType, None)
    val headersParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("headers"), headerType, None)

    val toHeadersCall = lang.nullaryMethodCall(jvm.Ident("headers").code, jvm.Ident("toHeaders"))

    // ProducerRecord(topic, partition, key, value, headers)
    // We pass null for partition
    val recordExpr = jvm
      .New(
        ProducerRecordType.of(keyType, valueType).code,
        List(
          jvm.Arg.Pos(jvm.Ident("topic").code),
          jvm.Arg.Pos(code"null"), // partition
          jvm.Arg.Pos(jvm.Ident("key").code),
          jvm.Arg.Pos(jvm.Ident("value").code),
          jvm.Arg.Pos(toHeadersCall)
        )
      )
      .code

    effectOps match {
      case Some(ops) =>
        // Wrap producer.send in the effect type
        val (returnType, body) = generateAsyncSendBody(recordExpr, ops)
        jvm.Method(
          annotations = Nil,
          comments = jvm.Comments(List("Send a message with headers to the topic asynchronously")),
          tparams = Nil,
          name = jvm.Ident("send"),
          params = List(keyParam, valueParam, headersParam),
          implicitParams = Nil,
          tpe = returnType,
          throws = Nil,
          body = jvm.Body.Stmts(body),
          isOverride = false,
          isDefault = false
        )

      case None =>
        // Blocking: return Future<RecordMetadata> (current behavior)
        val sendExpr = jvm.Ident("producer").code.invoke("send", recordExpr)
        jvm.Method(
          annotations = Nil,
          comments = jvm.Comments(List("Send a message with headers to the topic")),
          tparams = Nil,
          name = jvm.Ident("send"),
          params = List(keyParam, valueParam, headersParam),
          implicitParams = Nil,
          tpe = FutureType.of(RecordMetadataType),
          throws = Nil,
          body = jvm.Body.Stmts(List(jvm.Return(sendExpr).code)),
          isOverride = false,
          isDefault = false
        )
    }
  }

  private def generateCloseMethod(): jvm.Method = {
    val closeCall = lang.nullaryMethodCall(jvm.Ident("producer").code, jvm.Ident("close"))

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(List("Close the producer")),
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

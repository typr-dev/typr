package typr.avro.codegen

import typr.boundaries.framework.QuarkusFramework
import typr.internal.codegen._
import typr.jvm

/** Quarkus Kafka framework integration using SmallRye Reactive Messaging.
  *
  * Uses Emitter + @Channel for publishing, @Incoming for consuming, KafkaRequestReply for RPC client, and @Incoming + @Outgoing for RPC server.
  */
object KafkaFrameworkQuarkus extends KafkaFramework {

  // Effect type (Mutiny)
  val Uni: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("io.smallrye.mutiny.Uni"))
  val Void: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("java.lang.Void"))

  // Quarkus/SmallRye types
  val MutinyEmitter: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("io.smallrye.reactive.messaging.MutinyEmitter"))
  val Message: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("org.eclipse.microprofile.reactive.messaging.Message"))
  val Metadata: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("org.eclipse.microprofile.reactive.messaging.Metadata"))
  val KafkaRequestReply: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("io.smallrye.reactive.messaging.kafka.reply.KafkaRequestReply"))

  // Reactive Messaging annotations
  val Incoming: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("org.eclipse.microprofile.reactive.messaging.Incoming"))
  val Outgoing: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("org.eclipse.microprofile.reactive.messaging.Outgoing"))
  val Channel: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("org.eclipse.microprofile.reactive.messaging.Channel"))

  // Delegate to QuarkusFramework base
  override def name: String = QuarkusFramework.name
  override def serviceAnnotation: jvm.Annotation = QuarkusFramework.serviceAnnotation
  override def constructorAnnotations: List[jvm.Annotation] = QuarkusFramework.constructorAnnotations

  override def effectType: jvm.Type.Qualified = Uni

  override def effectOf(inner: jvm.Type): jvm.Type = Uni.of(inner)

  override def voidEffectType: jvm.Type = Uni.of(Void)

  override def publisherFieldType(keyType: jvm.Type, valueType: jvm.Type): jvm.Type =
    MutinyEmitter.of(valueType)

  override def publisherFieldAnnotations(channelName: String): List[jvm.Annotation] =
    List(jvm.Annotation(Channel, List(jvm.Annotation.Arg.Positional(jvm.StrLit(channelName).code))))

  override def publishCall(templateVar: jvm.Ident, topicExpr: jvm.Code, keyExpr: jvm.Code, valueExpr: jvm.Code): jvm.Code =
    code"$templateVar.send($valueExpr)"

  override def publishReturnType(keyType: jvm.Type, valueType: jvm.Type): jvm.Type =
    Void // Emitter.send() returns CompletionStage<Void>

  override def pure(value: jvm.Code): jvm.Code =
    code"$Uni.createFrom().item($value)"

  override def voidSuccess: jvm.Code =
    code"$Uni.createFrom().voidItem()"

  override def listenerAnnotation(topic: String): jvm.Annotation =
    jvm.Annotation(Incoming, List(jvm.Annotation.Arg.Positional(jvm.StrLit(topic).code)))

  override def consumerRecordType(keyType: jvm.Type, valueType: jvm.Type): jvm.Type =
    Message.of(valueType)

  override def getPayload(recordVar: jvm.Ident): jvm.Code =
    code"$recordVar.getPayload()"

  override def getMetadata(recordVar: jvm.Ident): jvm.Code =
    code"$recordVar.getMetadata()"

  override def metadataType: jvm.Type.Qualified = Metadata

  override def rpcClientFieldType(requestKeyType: jvm.Type, requestValueType: jvm.Type, responseValueType: jvm.Type): jvm.Type =
    KafkaRequestReply.of(requestValueType, responseValueType)

  override def rpcRequestCallBlocking(templateVar: jvm.Ident, topic: String, requestExpr: jvm.Code): jvm.Code =
    code"$templateVar.request($requestExpr).await().indefinitely()"

  override def rpcRequestCallAsync(templateVar: jvm.Ident, topic: String, requestExpr: jvm.Code): jvm.Code =
    code"$templateVar.request($requestExpr)"

  override def serverListenerAnnotations(requestTopic: String, replyTopic: Option[String]): List[jvm.Annotation] = {
    val incoming = jvm.Annotation(Incoming, List(jvm.Annotation.Arg.Positional(jvm.StrLit(requestTopic).code)))
    val outgoing = replyTopic.map(rt => jvm.Annotation(Outgoing, List(jvm.Annotation.Arg.Positional(jvm.StrLit(rt).code))))
    List(incoming) ++ outgoing.toList
  }
}

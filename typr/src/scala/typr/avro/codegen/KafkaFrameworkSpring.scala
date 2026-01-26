package typr.avro.codegen

import typr.jvm
import typr.internal.codegen._

/** Spring Kafka framework integration.
  *
  * Uses KafkaTemplate for publishing, @KafkaListener for consuming, ReplyingKafkaTemplate for RPC client, and @KafkaListener + @SendTo for RPC server.
  */
object KafkaFrameworkSpring extends KafkaFramework {

  // Effect type
  val CompletableFuture: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("java.util.concurrent.CompletableFuture"))
  val Void: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("java.lang.Void"))

  // Spring Kafka types
  val KafkaTemplate: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("org.springframework.kafka.core.KafkaTemplate"))
  val ReplyingKafkaTemplate: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("org.springframework.kafka.requestreply.ReplyingKafkaTemplate"))
  val ConsumerRecord: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("org.apache.kafka.clients.consumer.ConsumerRecord"))
  val ProducerRecord: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("org.apache.kafka.clients.producer.ProducerRecord"))
  val Headers: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("org.apache.kafka.common.header.Headers"))
  val SendResult: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("org.springframework.kafka.support.SendResult"))

  // Spring annotations
  val Service: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("org.springframework.stereotype.Service"))
  val KafkaListener: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("org.springframework.kafka.annotation.KafkaListener"))
  val SendTo: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("org.springframework.messaging.handler.annotation.SendTo"))

  override def effectType: jvm.Type.Qualified = CompletableFuture

  override def effectOf(inner: jvm.Type): jvm.Type = CompletableFuture.of(inner)

  override def voidEffectType: jvm.Type = CompletableFuture.of(Void)

  override def serviceAnnotation: jvm.Annotation =
    jvm.Annotation(Service, Nil)

  override def constructorAnnotations: List[jvm.Annotation] = Nil

  override def publisherFieldType(keyType: jvm.Type, valueType: jvm.Type): jvm.Type =
    KafkaTemplate.of(keyType, valueType)

  override def publisherFieldAnnotations(channelName: String): List[jvm.Annotation] = Nil

  override def publishCall(templateVar: jvm.Ident, topicExpr: jvm.Code, keyExpr: jvm.Code, valueExpr: jvm.Code): jvm.Code =
    code"$templateVar.send($topicExpr, $keyExpr, $valueExpr)"

  override def publishReturnType(keyType: jvm.Type, valueType: jvm.Type): jvm.Type =
    SendResult.of(keyType, valueType)

  override def pure(value: jvm.Code): jvm.Code =
    code"$CompletableFuture.completedFuture($value)"

  override def voidSuccess: jvm.Code =
    code"$CompletableFuture.completedFuture(null)"

  override def listenerAnnotation(topic: String): jvm.Annotation =
    jvm.Annotation(KafkaListener, List(jvm.Annotation.Arg.Named(jvm.Ident("topics"), jvm.StrLit(topic).code)))

  override def consumerRecordType(keyType: jvm.Type, valueType: jvm.Type): jvm.Type =
    ConsumerRecord.of(keyType, valueType)

  override def getPayload(recordVar: jvm.Ident): jvm.Code =
    code"$recordVar.value()"

  override def getMetadata(recordVar: jvm.Ident): jvm.Code =
    code"$recordVar.headers()"

  override def metadataType: jvm.Type.Qualified = Headers

  override def rpcClientFieldType(requestKeyType: jvm.Type, requestValueType: jvm.Type, responseValueType: jvm.Type): jvm.Type =
    ReplyingKafkaTemplate.of(requestKeyType, requestValueType, responseValueType)

  override def rpcRequestCallBlocking(templateVar: jvm.Ident, topic: String, requestExpr: jvm.Code): jvm.Code = {
    val topicLit = jvm.StrLit(topic).code
    code"$templateVar.sendAndReceive(new $ProducerRecord<>($topicLit, $requestExpr)).get().value()"
  }

  override def rpcRequestCallAsync(templateVar: jvm.Ident, topic: String, requestExpr: jvm.Code): jvm.Code = {
    val topicLit = jvm.StrLit(topic).code
    code"$templateVar.sendAndReceive(new $ProducerRecord<>($topicLit, $requestExpr)).thenApply(r -> r.value())"
  }

  override def serverListenerAnnotations(requestTopic: String, replyTopic: Option[String]): List[jvm.Annotation] = {
    val listener = jvm.Annotation(KafkaListener, List(jvm.Annotation.Arg.Named(jvm.Ident("topics"), jvm.StrLit(requestTopic).code)))
    replyTopic match {
      case Some(_) => List(listener, jvm.Annotation(SendTo, Nil))
      case None    => List(listener)
    }
  }
}

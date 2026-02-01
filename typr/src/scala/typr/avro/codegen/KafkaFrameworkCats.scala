package typr.avro.codegen

import typr.boundaries.framework.{CatsFramework, FrameworkTypes}
import typr.internal.codegen._
import typr.jvm

/** Cats/Typelevel fs2-kafka framework integration.
  *
  * Uses KafkaProducer[IO, K, V] for publishing, fs2.Stream-based consumers, and pure functional patterns with cats.effect.IO.
  *
  * Key differences from Spring/Quarkus:
  *   - No DI annotations (constructor params for dependencies)
  *   - Event consumers are stream-based (handler traits + consumer helpers)
  *   - RPC uses fs2.Stream patterns
  */
object KafkaFrameworkCats extends KafkaFramework {

  // Effect type (Cats Effect IO)
  val IO: jvm.Type.Qualified = FrameworkTypes.CatsEffect.IO
  val Unit: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("scala.Unit"))

  // fs2-kafka types
  val KafkaProducer: jvm.Type.Qualified = FrameworkTypes.Fs2Kafka.KafkaProducer
  val ProducerRecord: jvm.Type.Qualified = FrameworkTypes.Fs2Kafka.ProducerRecord
  val ProducerRecords: jvm.Type.Qualified = FrameworkTypes.Fs2Kafka.ProducerRecords
  val ProducerResult: jvm.Type.Qualified = FrameworkTypes.Fs2Kafka.ProducerResult
  val ConsumerRecord: jvm.Type.Qualified = FrameworkTypes.Fs2Kafka.ConsumerRecord
  val CommittableConsumerRecord: jvm.Type.Qualified = FrameworkTypes.Fs2Kafka.CommittableConsumerRecord
  val Headers: jvm.Type.Qualified = FrameworkTypes.Fs2Kafka.Headers
  val RecordMetadata: jvm.Type.Qualified = FrameworkTypes.Fs2Kafka.RecordMetadata

  // Delegate to CatsFramework base
  override def name: String = CatsFramework.name
  override def serviceAnnotation: jvm.Annotation = CatsFramework.serviceAnnotation
  override def constructorAnnotations: List[jvm.Annotation] = CatsFramework.constructorAnnotations

  override def effectType: jvm.Type.Qualified = IO

  override def effectOf(inner: jvm.Type): jvm.Type = IO.of(inner)

  override def voidEffectType: jvm.Type = IO.of(Unit)

  override def publisherFieldType(keyType: jvm.Type, valueType: jvm.Type): jvm.Type =
    KafkaProducer.of(IO, keyType, valueType)

  override def publisherFieldAnnotations(channelName: String): List[jvm.Annotation] = Nil

  override def publishCall(templateVar: jvm.Ident, topicExpr: jvm.Code, keyExpr: jvm.Code, valueExpr: jvm.Code): jvm.Code = {
    val record = code"$ProducerRecord($topicExpr, $keyExpr, $valueExpr)"
    val records = code"$ProducerRecords.one($record)"
    code"$templateVar.produce($records).flatten"
  }

  override def publishReturnType(keyType: jvm.Type, valueType: jvm.Type): jvm.Type =
    ProducerResult.of(keyType, valueType)

  override def pure(value: jvm.Code): jvm.Code =
    code"$IO.pure($value)"

  override def voidSuccess: jvm.Code =
    code"$IO.unit"

  override def listenerAnnotation(topic: String): jvm.Annotation =
    CatsFramework.serviceAnnotation

  override def consumerRecordType(keyType: jvm.Type, valueType: jvm.Type): jvm.Type =
    ConsumerRecord.of(keyType, valueType)

  override def getPayload(recordVar: jvm.Ident): jvm.Code =
    code"$recordVar.value"

  override def getMetadata(recordVar: jvm.Ident): jvm.Code =
    code"$recordVar.headers"

  override def metadataType: jvm.Type.Qualified = Headers

  override def rpcClientFieldType(requestKeyType: jvm.Type, requestValueType: jvm.Type, responseValueType: jvm.Type): jvm.Type =
    KafkaProducer.of(IO, requestKeyType, requestValueType)

  override def rpcRequestCallBlocking(templateVar: jvm.Ident, topic: String, requestExpr: jvm.Code): jvm.Code = {
    val topicLit = jvm.StrLit(topic).code
    // fs2-kafka ProducerRecord requires (topic, key, value) - use correlationId as key
    val record = code"$ProducerRecord($topicLit, $requestExpr.correlationId, $requestExpr)"
    val records = code"$ProducerRecords.one($record)"
    code"$templateVar.produce($records).flatten.unsafeRunSync()(cats.effect.unsafe.IORuntime.global)"
  }

  override def rpcRequestCallAsync(templateVar: jvm.Ident, topic: String, requestExpr: jvm.Code): jvm.Code = {
    val topicLit = jvm.StrLit(topic).code
    // fs2-kafka ProducerRecord requires (topic, key, value) - use correlationId as key
    val record = code"$ProducerRecord($topicLit, $requestExpr.correlationId, $requestExpr)"
    val records = code"$ProducerRecords.one($record)"
    code"$templateVar.produce($records).flatten"
  }

  override def serverListenerAnnotations(requestTopic: String, replyTopic: Option[String]): List[jvm.Annotation] =
    Nil
}

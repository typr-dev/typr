package typr.avro.codegen

import typr.boundaries.framework.Framework
import typr.jvm

/** Framework integration for generating Kafka event publishers/listeners and RPC client/server.
  *
  * Implementations provide framework-specific types and code patterns for Spring and Quarkus. Extends the base Framework trait to share common DI patterns.
  */
trait KafkaFramework extends Framework {

  /** Effect type for async operations (e.g., CompletableFuture, Uni) */
  def effectType: jvm.Type.Qualified

  /** Wrap a type in the effect (e.g., CompletableFuture[T], Uni[T]) */
  def effectOf(inner: jvm.Type): jvm.Type

  /** Void wrapped in effect type */
  def voidEffectType: jvm.Type

  // ===== Event Publishing =====

  /** Type for the Kafka template/emitter field */
  def publisherFieldType(keyType: jvm.Type, valueType: jvm.Type): jvm.Type

  /** Field annotations for the publisher (e.g., @Channel for Quarkus) */
  def publisherFieldAnnotations(channelName: String): List[jvm.Annotation]

  /** Generate code to publish an event. Returns the send expression. */
  def publishCall(templateVar: jvm.Ident, topicExpr: jvm.Code, keyExpr: jvm.Code, valueExpr: jvm.Code): jvm.Code

  /** Return type for publish operations (e.g., SendResult for Spring, Void for Quarkus) */
  def publishReturnType(keyType: jvm.Type, valueType: jvm.Type): jvm.Type

  /** Wrap a value in the effect's pure/completed */
  def pure(value: jvm.Code): jvm.Code

  /** Return void success in effect */
  def voidSuccess: jvm.Code

  // ===== Event Listening =====

  /** Listener method annotation */
  def listenerAnnotation(topic: String): jvm.Annotation

  /** Parameter type for consumer record */
  def consumerRecordType(keyType: jvm.Type, valueType: jvm.Type): jvm.Type

  /** Extract payload from consumer record variable */
  def getPayload(recordVar: jvm.Ident): jvm.Code

  /** Extract metadata/headers from consumer record variable */
  def getMetadata(recordVar: jvm.Ident): jvm.Code

  /** Metadata type (Headers or Metadata) */
  def metadataType: jvm.Type.Qualified

  // ===== RPC Client =====

  /** Type for the RPC client template field */
  def rpcClientFieldType(requestKeyType: jvm.Type, requestValueType: jvm.Type, responseValueType: jvm.Type): jvm.Type

  /** Generate code for blocking RPC request call (waits for response) */
  def rpcRequestCallBlocking(templateVar: jvm.Ident, topic: String, requestExpr: jvm.Code): jvm.Code

  /** Generate code for async RPC request call (returns effect type) */
  def rpcRequestCallAsync(templateVar: jvm.Ident, topic: String, requestExpr: jvm.Code): jvm.Code

  // ===== RPC Server =====

  /** Server handler method annotations */
  def serverListenerAnnotations(requestTopic: String, replyTopic: Option[String]): List[jvm.Annotation]
}

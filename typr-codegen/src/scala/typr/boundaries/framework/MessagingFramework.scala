package typr.boundaries.framework

import typr.jvm

/** Messaging protocol framework extension for Avro/Kafka code generation.
  *
  * Provides messaging-specific code generation patterns on top of the base Framework trait for Kafka producers and consumers.
  */
trait MessagingFramework extends Framework {

  /** Annotation for message listeners (e.g., @KafkaListener, @Incoming) */
  def listenerAnnotation(topic: String): jvm.Annotation

  /** The type for message producers/publishers */
  def publisherType(keyType: jvm.Type, valueType: jvm.Type): jvm.Type

  /** Generate code to publish a message */
  def publishCall(
      publisherIdent: jvm.Ident,
      topic: String,
      key: jvm.Code,
      value: jvm.Code
  ): jvm.Code

  /** Annotation to inject a message template/emitter */
  def publisherInjectionAnnotation(topic: String): List[jvm.Annotation]
}

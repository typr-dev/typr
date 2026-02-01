package typr.boundaries.framework

import typr.jvm

/** Common JVM types used by framework integrations */
object FrameworkTypes {

  // Spring DI annotations
  object Spring {
    val Service: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("org.springframework.stereotype.Service"))
    val Component: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("org.springframework.stereotype.Component"))
  }

  // CDI / Jakarta Inject annotations
  object CDI {
    val ApplicationScoped: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("jakarta.enterprise.context.ApplicationScoped"))
    val Singleton: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("jakarta.inject.Singleton"))
    val Inject: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("jakarta.inject.Inject"))
  }

  // Scala standard library types
  object Scala {
    val Unused: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("scala.annotation.unused"))
  }

  // Cats Effect types
  object CatsEffect {
    val IO: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("cats.effect.IO"))
  }

  // fs2-kafka types
  object Fs2Kafka {
    val KafkaProducer: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("fs2.kafka.KafkaProducer"))
    val KafkaConsumer: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("fs2.kafka.KafkaConsumer"))
    val ProducerRecord: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("fs2.kafka.ProducerRecord"))
    val ProducerRecords: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("fs2.kafka.ProducerRecords"))
    val ProducerResult: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("fs2.kafka.ProducerResult"))
    val ConsumerRecord: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("fs2.kafka.ConsumerRecord"))
    val CommittableConsumerRecord: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("fs2.kafka.CommittableConsumerRecord"))
    val Headers: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("fs2.kafka.Headers"))
    val RecordMetadata: jvm.Type.Qualified = jvm.Type.Qualified(jvm.QIdent("org.apache.kafka.clients.producer.RecordMetadata"))
  }
}

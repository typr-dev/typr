package com.example.events.serde

import com.example.events.OrderCancelled
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serializer

/** Serde for OrderCancelled */
class OrderCancelledSerde extends Serde[OrderCancelled] with Serializer[OrderCancelled]  with Deserializer[OrderCancelled] {
  val innerSerializer: KafkaAvroSerializer = new KafkaAvroSerializer()

  val innerDeserializer: KafkaAvroDeserializer = new KafkaAvroDeserializer()

  override def configure(
    configs: java.util.Map[String, ?],
    isKey: Boolean
  ): Unit = {
    innerSerializer.configure(configs, isKey)
    innerDeserializer.configure(configs, isKey)
  }

  override def serialize(
    topic: String,
    data: OrderCancelled
  ): Array[Byte] = {
    if (data == null) {
      return null
    }
    return innerSerializer.serialize(topic, data.toGenericRecord)
  }

  override def deserialize(
    topic: String,
    data: Array[Byte]
  ): OrderCancelled = {
    if (data == null) {
      return null
    }
    val record: GenericRecord = innerDeserializer.deserialize(topic, data).asInstanceOf[GenericRecord]
    return OrderCancelled.fromGenericRecord(record)
  }

  override def close: Unit = {
    innerSerializer.close()
    innerDeserializer.close()
  }

  override def serializer: Serializer[OrderCancelled] = this

  override def deserializer: Deserializer[OrderCancelled] = this
}
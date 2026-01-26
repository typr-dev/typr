package com.example.events.serde

import com.example.events.OrderCancelled
import com.example.events.OrderEvents
import com.example.events.OrderPlaced
import com.example.events.OrderUpdated
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serializer

/** Serde for OrderEvents (sealed type with multiple event variants) */
class OrderEventsSerde extends Serde[OrderEvents] with Serializer[OrderEvents]  with Deserializer[OrderEvents] {
  val inner: KafkaAvroDeserializer = new KafkaAvroDeserializer()

  override def configure(
    configs: java.util.Map[String, ?],
    isKey: Boolean
  ): Unit = {
    inner.configure(configs, isKey)
  }

  override def serialize(
    topic: String,
    data: OrderEvents
  ): Array[Byte] = {
    if (data == null) {
      return null
    }
    return data match {
      case e: OrderCancelled => new OrderCancelledSerde().serialize(topic, e)
      case e: OrderPlaced => new OrderPlacedSerde().serialize(topic, e)
      case e: OrderUpdated => new OrderUpdatedSerde().serialize(topic, e)
    }
  }

  override def deserialize(
    topic: String,
    data: Array[Byte]
  ): OrderEvents = {
    if (data == null) {
      return null
    }
    val record: GenericRecord = inner.deserialize(topic, data).asInstanceOf[GenericRecord]
    return OrderEvents.fromGenericRecord(record)
  }

  override def close: Unit = {
    inner.close()
  }

  override def serializer: Serializer[OrderEvents] = this

  override def deserializer: Deserializer[OrderEvents] = this
}
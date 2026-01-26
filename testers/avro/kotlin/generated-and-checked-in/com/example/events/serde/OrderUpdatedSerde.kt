package com.example.events.serde

import com.example.events.OrderUpdated
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializer
import kotlin.collections.MutableMap
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serializer

/** Serde for OrderUpdated */
class OrderUpdatedSerde() : Serde<OrderUpdated>, Serializer<OrderUpdated>, Deserializer<OrderUpdated> {
  override fun close() {
    innerSerializer.close()
    innerDeserializer.close()
  }

  override fun configure(
    configs: MutableMap<kotlin.String, *>,
    isKey: kotlin.Boolean
  ) {
    innerSerializer.configure(configs, isKey)
    innerDeserializer.configure(configs, isKey)
  }

  override fun deserialize(
    topic: kotlin.String,
    data: ByteArray?
  ): OrderUpdated? {
    if (data == null) {
      return null
    }
    val record: GenericRecord = (innerDeserializer.deserialize(topic, data) as GenericRecord)
    return OrderUpdated.fromGenericRecord(record)
  }

  override fun deserializer(): Deserializer<OrderUpdated> = this

  val innerDeserializer: KafkaAvroDeserializer = KafkaAvroDeserializer()

  val innerSerializer: KafkaAvroSerializer = KafkaAvroSerializer()

  override fun serialize(
    topic: kotlin.String,
    data: OrderUpdated?
  ): ByteArray? {
    if (data == null) {
      return null
    }
    return innerSerializer.serialize(topic, data.toGenericRecord())
  }

  override fun serializer(): Serializer<OrderUpdated> = this
}
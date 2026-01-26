package com.example.events.serde

import com.example.events.Invoice
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializer
import kotlin.collections.MutableMap
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serializer

/** Serde for Invoice */
class InvoiceSerde() : Serde<Invoice>, Serializer<Invoice>, Deserializer<Invoice> {
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
  ): Invoice? {
    if (data == null) {
      return null
    }
    val record: GenericRecord = (innerDeserializer.deserialize(topic, data) as GenericRecord)
    return Invoice.fromGenericRecord(record)
  }

  override fun deserializer(): Deserializer<Invoice> = this

  val innerDeserializer: KafkaAvroDeserializer = KafkaAvroDeserializer()

  val innerSerializer: KafkaAvroSerializer = KafkaAvroSerializer()

  override fun serialize(
    topic: kotlin.String,
    data: Invoice?
  ): ByteArray? {
    if (data == null) {
      return null
    }
    return innerSerializer.serialize(topic, data.toGenericRecord())
  }

  override fun serializer(): Serializer<Invoice> = this
}
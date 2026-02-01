package com.example.events.serde

import com.example.events.common.Money
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serializer

/** Serde for Money */
class MoneySerde extends Serde[Money] with Serializer[Money]  with Deserializer[Money] {
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
    data: Money
  ): Array[Byte] = {
    if (data == null) {
      return null
    }
    return innerSerializer.serialize(topic, data.toGenericRecord)
  }

  override def deserialize(
    topic: String,
    data: Array[Byte]
  ): Money = {
    if (data == null) {
      return null
    }
    val record: GenericRecord = innerDeserializer.deserialize(topic, data).asInstanceOf[GenericRecord]
    return Money.fromGenericRecord(record)
  }

  override def close: Unit = {
    innerSerializer.close()
    innerDeserializer.close()
  }

  override def serializer: Serializer[Money] = this

  override def deserializer: Deserializer[Money] = this
}
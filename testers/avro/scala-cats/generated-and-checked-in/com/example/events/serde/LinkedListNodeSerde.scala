package com.example.events.serde

import com.example.events.LinkedListNode
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serializer

/** Serde for LinkedListNode */
class LinkedListNodeSerde extends Serde[LinkedListNode] with Serializer[LinkedListNode]  with Deserializer[LinkedListNode] {
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
    data: LinkedListNode
  ): Array[Byte] = {
    if (data == null) {
      return null
    }
    return innerSerializer.serialize(topic, data.toGenericRecord)
  }

  override def deserialize(
    topic: String,
    data: Array[Byte]
  ): LinkedListNode = {
    if (data == null) {
      return null
    }
    val record: GenericRecord = innerDeserializer.deserialize(topic, data).asInstanceOf[GenericRecord]
    return LinkedListNode.fromGenericRecord(record)
  }

  override def close: Unit = {
    innerSerializer.close()
    innerDeserializer.close()
  }

  override def serializer: Serializer[LinkedListNode] = this

  override def deserializer: Deserializer[LinkedListNode] = this
}
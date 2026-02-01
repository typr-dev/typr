package com.example.events.serde

import com.example.events.TreeNode
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serializer

/** Serde for TreeNode */
class TreeNodeSerde extends Serde[TreeNode] with Serializer[TreeNode]  with Deserializer[TreeNode] {
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
    data: TreeNode
  ): Array[Byte] = {
    if (data == null) {
      return null
    }
    return innerSerializer.serialize(topic, data.toGenericRecord)
  }

  override def deserialize(
    topic: String,
    data: Array[Byte]
  ): TreeNode = {
    if (data == null) {
      return null
    }
    val record: GenericRecord = innerDeserializer.deserialize(topic, data).asInstanceOf[GenericRecord]
    return TreeNode.fromGenericRecord(record)
  }

  override def close: Unit = {
    innerSerializer.close()
    innerDeserializer.close()
  }

  override def serializer: Serializer[TreeNode] = this

  override def deserializer: Deserializer[TreeNode] = this
}
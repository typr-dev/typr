package com.example.events

import org.apache.avro.Schema
import org.apache.avro.Schema.Parser
import org.apache.avro.generic.GenericData.Record
import org.apache.avro.generic.GenericRecord

/** A recursive tree structure for testing recursive type support */
data class TreeNode(
  /** The value stored in this node */
  val value: kotlin.String,
  /** Optional left child */
  val left: TreeNode?,
  /** Optional right child */
  val right: TreeNode?
) {
  /** Convert this record to a GenericRecord for serialization */
  fun toGenericRecord(): GenericRecord {
    val record: Record = Record(TreeNode.SCHEMA)
    record.put("value", this.value)
    record.put("left", (if (this.left == null) null else this.left.toGenericRecord()))
    record.put("right", (if (this.right == null) null else this.right.toGenericRecord()))
    return record
  }

  companion object {
    val SCHEMA: Schema = Parser().parse("{\"type\": \"record\",\"name\": \"TreeNode\",\"namespace\": \"com.example.events\",\"doc\": \"A recursive tree structure for testing recursive type support\",\"fields\": [{\"name\": \"value\",\"doc\": \"The value stored in this node\",\"type\": \"string\"},{\"name\": \"left\",\"doc\": \"Optional left child\",\"type\": [\"null\",\"com.example.events.TreeNode\"],\"default\": null},{\"name\": \"right\",\"doc\": \"Optional right child\",\"type\": [\"null\",\"com.example.events.TreeNode\"],\"default\": null}]}")

    /** Create a record from a GenericRecord (for deserialization) */
    fun fromGenericRecord(record: GenericRecord): TreeNode = TreeNode(record.get("value").toString(), (if (record.get("left") == null) null else TreeNode.fromGenericRecord((record.get("left") as GenericRecord))), (if (record.get("right") == null) null else TreeNode.fromGenericRecord((record.get("right") as GenericRecord))))
  }
}
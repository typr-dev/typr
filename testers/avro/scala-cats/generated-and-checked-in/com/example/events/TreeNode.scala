package com.example.events

import org.apache.avro.Schema
import org.apache.avro.Schema.Parser
import org.apache.avro.generic.GenericData.Record
import org.apache.avro.generic.GenericRecord

/** A recursive tree structure for testing recursive type support */
case class TreeNode(
  /** The value stored in this node */
  value: String,
  /** Optional left child */
  left: Option[TreeNode],
  /** Optional right child */
  right: Option[TreeNode]
) {
  /** Convert this record to a GenericRecord for serialization */
  def toGenericRecord: GenericRecord = {
    val record: Record = new Record(TreeNode.SCHEMA)
    record.put("value", this.value)
    record.put("left", (if (this.left.isEmpty) null else this.left.get.toGenericRecord))
    record.put("right", (if (this.right.isEmpty) null else this.right.get.toGenericRecord))
    return record
  }
}

object TreeNode {
  val SCHEMA: Schema = new Parser().parse("""{"type": "record","name": "TreeNode","namespace": "com.example.events","doc": "A recursive tree structure for testing recursive type support","fields": [{"name": "value","doc": "The value stored in this node","type": "string"},{"name": "left","doc": "Optional left child","type": ["null","com.example.events.TreeNode"],"default": null},{"name": "right","doc": "Optional right child","type": ["null","com.example.events.TreeNode"],"default": null}]}""")

  /** Create a record from a GenericRecord (for deserialization) */
  def fromGenericRecord(record: GenericRecord): TreeNode = new TreeNode(record.get("value").toString(), Option((if (record.get("left") == null) null else TreeNode.fromGenericRecord(record.get("left").asInstanceOf[GenericRecord]))), Option((if (record.get("right") == null) null else TreeNode.fromGenericRecord(record.get("right").asInstanceOf[GenericRecord]))))
}
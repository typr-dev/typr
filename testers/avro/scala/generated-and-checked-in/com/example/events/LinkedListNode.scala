package com.example.events

import org.apache.avro.Schema
import org.apache.avro.Schema.Parser
import org.apache.avro.generic.GenericData.Record
import org.apache.avro.generic.GenericRecord

/** A recursive linked list for testing recursive type support */
case class LinkedListNode(
  /** The value stored in this node */
  value: Int,
  /** Optional next node in the list */
  next: Option[LinkedListNode]
) {
  /** Convert this record to a GenericRecord for serialization */
  def toGenericRecord: GenericRecord = {
    val record: Record = new Record(LinkedListNode.SCHEMA)
    record.put("value", this.value)
    record.put("next", (if (this.next.isEmpty) null else this.next.get.toGenericRecord))
    return record
  }
}

object LinkedListNode {
  val SCHEMA: Schema = new Parser().parse("""{"type": "record","name": "LinkedListNode","namespace": "com.example.events","doc": "A recursive linked list for testing recursive type support","fields": [{"name": "value","doc": "The value stored in this node","type": "int"},{"name": "next","doc": "Optional next node in the list","type": ["null","com.example.events.LinkedListNode"],"default": null}]}""")

  /** Create a record from a GenericRecord (for deserialization) */
  def fromGenericRecord(record: GenericRecord): LinkedListNode = new LinkedListNode(record.get("value").asInstanceOf[Integer], Option((if (record.get("next") == null) null else LinkedListNode.fromGenericRecord(record.get("next").asInstanceOf[GenericRecord]))))
}
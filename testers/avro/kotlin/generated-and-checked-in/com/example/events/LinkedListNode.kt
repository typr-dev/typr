package com.example.events

import org.apache.avro.Schema
import org.apache.avro.Schema.Parser
import org.apache.avro.generic.GenericData.Record
import org.apache.avro.generic.GenericRecord

/** A recursive linked list for testing recursive type support */
data class LinkedListNode(
  /** The value stored in this node */
  val value: Int,
  /** Optional next node in the list */
  val next: LinkedListNode?
) {
  /** Convert this record to a GenericRecord for serialization */
  fun toGenericRecord(): GenericRecord {
    val record: Record = Record(LinkedListNode.SCHEMA)
    record.put("value", this.value)
    record.put("next", (if (this.next == null) null else this.next.toGenericRecord()))
    return record
  }

  companion object {
    val SCHEMA: Schema = Parser().parse("{\"type\": \"record\",\"name\": \"LinkedListNode\",\"namespace\": \"com.example.events\",\"doc\": \"A recursive linked list for testing recursive type support\",\"fields\": [{\"name\": \"value\",\"doc\": \"The value stored in this node\",\"type\": \"int\"},{\"name\": \"next\",\"doc\": \"Optional next node in the list\",\"type\": [\"null\",\"com.example.events.LinkedListNode\"],\"default\": null}]}")

    /** Create a record from a GenericRecord (for deserialization) */
    fun fromGenericRecord(record: GenericRecord): LinkedListNode = LinkedListNode((record.get("value") as Int), (if (record.get("next") == null) null else LinkedListNode.fromGenericRecord((record.get("next") as GenericRecord))))
  }
}
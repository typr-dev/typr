package com.example.events

import java.lang.IllegalArgumentException
import org.apache.avro.generic.GenericRecord

sealed interface OrderEvents {
  companion object {
    /** Create an event from a GenericRecord, dispatching to the correct subtype based on schema name */
    fun fromGenericRecord(record: GenericRecord): OrderEvents {
      if (record.getSchema().getFullName().equals("com.example.events.OrderCancelled")) {
        return OrderCancelled.fromGenericRecord(record)
      }else if (record.getSchema().getFullName().equals("com.example.events.OrderPlaced")) {
        return OrderPlaced.fromGenericRecord(record)
      }else if (record.getSchema().getFullName().equals("com.example.events.OrderUpdated")) {
        return OrderUpdated.fromGenericRecord(record)
      } else {
        throw IllegalArgumentException("Unknown schema: " + record.getSchema().getFullName())
      }
    }
  }

  /** Convert this event to a GenericRecord for serialization */
  abstract fun toGenericRecord(): GenericRecord
}
package com.example.events

import java.lang.IllegalArgumentException
import org.apache.avro.generic.GenericRecord

trait OrderEvents {
  /** Convert this event to a GenericRecord for serialization */
  def toGenericRecord: GenericRecord
}

object OrderEvents {
  /** Create an event from a GenericRecord, dispatching to the correct subtype based on schema name */
  def fromGenericRecord(record: GenericRecord): OrderEvents = {
    if (record.getSchema().getFullName().equals("com.example.events.OrderCancelled")) {
      return OrderCancelled.fromGenericRecord(record)
    }else if (record.getSchema().getFullName().equals("com.example.events.OrderPlaced")) {
      return OrderPlaced.fromGenericRecord(record)
    }else if (record.getSchema().getFullName().equals("com.example.events.OrderUpdated")) {
      return OrderUpdated.fromGenericRecord(record)
    } else {
      throw new IllegalArgumentException("Unknown schema: " + record.getSchema().getFullName())
    }
  }
}
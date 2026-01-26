package com.example.events;

import org.apache.avro.generic.GenericRecord;

public sealed interface OrderEvents permits OrderCancelled, OrderPlaced, OrderUpdated {
  /**
   * Create an event from a GenericRecord, dispatching to the correct subtype based on schema name
   */
  static OrderEvents fromGenericRecord(GenericRecord record) {
    if (record.getSchema().getFullName().equals("com.example.events.OrderCancelled")) {
      return OrderCancelled.fromGenericRecord(record);
    } else if (record.getSchema().getFullName().equals("com.example.events.OrderPlaced")) {
      return OrderPlaced.fromGenericRecord(record);
    } else if (record.getSchema().getFullName().equals("com.example.events.OrderUpdated")) {
      return OrderUpdated.fromGenericRecord(record);
    } else {
      throw new IllegalArgumentException("Unknown schema: " + record.getSchema().getFullName());
    }
  }

  /** Convert this event to a GenericRecord for serialization */
  GenericRecord toGenericRecord();
}

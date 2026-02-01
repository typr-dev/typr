package com.example.events;

import java.util.Optional;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Parser;
import org.apache.avro.generic.GenericData.Record;
import org.apache.avro.generic.GenericRecord;

/** A recursive linked list for testing recursive type support */
public record LinkedListNode(
    /** The value stored in this node */
    Integer value,
    /** Optional next node in the list */
    Optional<LinkedListNode> next) {
  /** The value stored in this node */
  public LinkedListNode withValue(Integer value) {
    return new LinkedListNode(value, next);
  }

  /** Optional next node in the list */
  public LinkedListNode withNext(Optional<LinkedListNode> next) {
    return new LinkedListNode(value, next);
  }

  public static Schema SCHEMA =
      new Parser()
          .parse(
              "{\"type\": \"record\",\"name\": \"LinkedListNode\",\"namespace\":"
                  + " \"com.example.events\",\"doc\": \"A recursive linked list for testing"
                  + " recursive type support\",\"fields\": [{\"name\": \"value\",\"doc\": \"The"
                  + " value stored in this node\",\"type\": \"int\"},{\"name\": \"next\",\"doc\":"
                  + " \"Optional next node in the list\",\"type\":"
                  + " [\"null\",\"com.example.events.LinkedListNode\"],\"default\": null}]}");

  /** Create a record from a GenericRecord (for deserialization) */
  public static LinkedListNode fromGenericRecord(GenericRecord record) {
    return new LinkedListNode(
        ((Integer) record.get("value")),
        Optional.ofNullable(
            (record.get("next") == null
                ? null
                : LinkedListNode.fromGenericRecord(((GenericRecord) record.get("next"))))));
  }

  /** Convert this record to a GenericRecord for serialization */
  public GenericRecord toGenericRecord() {
    Record record = new Record(LinkedListNode.SCHEMA);
    record.put("value", this.value());
    record.put("next", (this.next().isEmpty() ? null : this.next().get().toGenericRecord()));
    return record;
  }
}

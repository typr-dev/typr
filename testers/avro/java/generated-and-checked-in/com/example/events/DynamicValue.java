package com.example.events;

import java.util.Objects;
import java.util.Optional;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Parser;
import org.apache.avro.generic.GenericData.Record;
import org.apache.avro.generic.GenericRecord;

/** A record with complex union types for testing union type generation */
public record DynamicValue(
    /** Unique identifier */
    String id,
    /** A value that can be string, int, or boolean */
    StringOrIntOrBoolean value,
    /** An optional value that can be string or long */
    Optional<StringOrLong> optionalValue) {
  /** Unique identifier */
  public DynamicValue withId(String id) {
    return new DynamicValue(id, value, optionalValue);
  }

  /** A value that can be string, int, or boolean */
  public DynamicValue withValue(StringOrIntOrBoolean value) {
    return new DynamicValue(id, value, optionalValue);
  }

  /** An optional value that can be string or long */
  public DynamicValue withOptionalValue(Optional<StringOrLong> optionalValue) {
    return new DynamicValue(id, value, optionalValue);
  }

  public static Schema SCHEMA =
      new Parser()
          .parse(
              "{\"type\": \"record\",\"name\": \"DynamicValue\",\"namespace\":"
                  + " \"com.example.events\",\"doc\": \"A record with complex union types for"
                  + " testing union type generation\",\"fields\": [{\"name\": \"id\",\"doc\":"
                  + " \"Unique identifier\",\"type\": \"string\"},{\"name\": \"value\",\"doc\": \"A"
                  + " value that can be string, int, or boolean\",\"type\":"
                  + " [\"string\",\"int\",\"boolean\"]},{\"name\": \"optionalValue\",\"doc\": \"An"
                  + " optional value that can be string or long\",\"type\":"
                  + " [\"null\",\"string\",\"long\"]}]}");

  /** Create a record from a GenericRecord (for deserialization) */
  public static DynamicValue fromGenericRecord(GenericRecord record) {
    return new DynamicValue(
        record.get("id").toString(),
        Objects.requireNonNull(
            (record.get("value") instanceof CharSequence
                ? StringOrIntOrBoolean.of(((CharSequence) record.get("value")).toString())
                : (record.get("value") instanceof Integer
                    ? StringOrIntOrBoolean.of(((Integer) record.get("value")))
                    : (record.get("value") instanceof Boolean
                        ? StringOrIntOrBoolean.of(((Boolean) record.get("value")))
                        : null))),
            "Unknown union type"),
        Optional.ofNullable(
            (record.get("optionalValue") == null
                ? null
                : (record.get("optionalValue") instanceof CharSequence
                    ? StringOrLong.of(((CharSequence) record.get("optionalValue")).toString())
                    : (record.get("optionalValue") instanceof Long
                        ? StringOrLong.of(((Long) record.get("optionalValue")))
                        : null)))));
  }

  /** Convert this record to a GenericRecord for serialization */
  public GenericRecord toGenericRecord() {
    Record record = new Record(DynamicValue.SCHEMA);
    record.put("id", this.id());
    record.put(
        "value",
        (this.value().isString()
            ? this.value().asString()
            : (this.value().isInt()
                ? this.value().asInt()
                : (this.value().isBoolean() ? this.value().asBoolean() : null))));
    record.put(
        "optionalValue",
        (this.optionalValue().isEmpty()
            ? null
            : (this.optionalValue().get().isString()
                ? this.optionalValue().get().asString()
                : (this.optionalValue().get().isLong()
                    ? this.optionalValue().get().asLong()
                    : null))));
    return record;
  }
}

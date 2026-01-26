package com.example.service;

import java.time.Instant;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Parser;
import org.apache.avro.generic.GenericData.Record;
import org.apache.avro.generic.GenericRecord;

public record User(
    /** User unique identifier */
    String id,
    /** User email address */
    String email,
    /** User display name */
    String name,
    Instant createdAt) {
  /** User unique identifier */
  public User withId(String id) {
    return new User(id, email, name, createdAt);
  }

  /** User email address */
  public User withEmail(String email) {
    return new User(id, email, name, createdAt);
  }

  /** User display name */
  public User withName(String name) {
    return new User(id, email, name, createdAt);
  }

  public User withCreatedAt(Instant createdAt) {
    return new User(id, email, name, createdAt);
  }

  public static Schema SCHEMA =
      new Parser()
          .parse(
              "{\"type\": \"record\",\"name\": \"User\",\"namespace\":"
                  + " \"com.example.service\",\"fields\": [{\"name\": \"id\",\"doc\": \"User unique"
                  + " identifier\",\"type\": \"string\"},{\"name\": \"email\",\"doc\": \"User email"
                  + " address\",\"type\": \"string\"},{\"name\": \"name\",\"doc\": \"User display"
                  + " name\",\"type\": \"string\"},{\"name\": \"createdAt\",\"type\": {\"type\":"
                  + " \"long\", \"logicalType\": \"timestamp-millis\"}}]}");

  /** Create a record from a GenericRecord (for deserialization) */
  public static User fromGenericRecord(GenericRecord record) {
    return new User(
        record.get("id").toString(),
        record.get("email").toString(),
        record.get("name").toString(),
        Instant.ofEpochMilli(((Long) record.get("createdAt"))));
  }

  /** Convert this record to a GenericRecord for serialization */
  public GenericRecord toGenericRecord() {
    Record record = new Record(User.SCHEMA);
    record.put("id", this.id());
    record.put("email", this.email());
    record.put("name", this.name());
    record.put("createdAt", this.createdAt().toEpochMilli());
    return record;
  }
}

package com.example.service

import java.time.Instant
import org.apache.avro.Schema
import org.apache.avro.Schema.Parser
import org.apache.avro.generic.GenericData.Record
import org.apache.avro.generic.GenericRecord

data class User(
  /** User unique identifier */
  val id: kotlin.String,
  /** User email address */
  val email: kotlin.String,
  /** User display name */
  val name: kotlin.String,
  val createdAt: Instant
) {
  /** Convert this record to a GenericRecord for serialization */
  fun toGenericRecord(): GenericRecord {
    val record: Record = Record(User.SCHEMA)
    record.put("id", this.id)
    record.put("email", this.email)
    record.put("name", this.name)
    record.put("createdAt", this.createdAt.toEpochMilli())
    return record
  }

  companion object {
    val SCHEMA: Schema = Parser().parse("{\"type\": \"record\",\"name\": \"User\",\"namespace\": \"com.example.service\",\"fields\": [{\"name\": \"id\",\"doc\": \"User unique identifier\",\"type\": \"string\"},{\"name\": \"email\",\"doc\": \"User email address\",\"type\": \"string\"},{\"name\": \"name\",\"doc\": \"User display name\",\"type\": \"string\"},{\"name\": \"createdAt\",\"type\": {\"type\": \"long\", \"logicalType\": \"timestamp-millis\"}}]}")

    /** Create a record from a GenericRecord (for deserialization) */
    fun fromGenericRecord(record: GenericRecord): User = User(record.get("id").toString(), record.get("email").toString(), record.get("name").toString(), Instant.ofEpochMilli((record.get("createdAt") as Long)))
  }
}
package com.example.service

import java.time.Instant
import org.apache.avro.Schema
import org.apache.avro.Schema.Parser
import org.apache.avro.generic.GenericData.Record
import org.apache.avro.generic.GenericRecord

case class User(
  /** User unique identifier */
  id: String,
  /** User email address */
  email: String,
  /** User display name */
  name: String,
  createdAt: Instant
) {
  /** Convert this record to a GenericRecord for serialization */
  def toGenericRecord: GenericRecord = {
    val record: Record = new Record(User.SCHEMA)
    record.put("id", this.id)
    record.put("email", this.email)
    record.put("name", this.name)
    record.put("createdAt", this.createdAt.toEpochMilli())
    return record
  }
}

object User {
  val SCHEMA: Schema = new Parser().parse("""{"type": "record","name": "User","namespace": "com.example.service","fields": [{"name": "id","doc": "User unique identifier","type": "string"},{"name": "email","doc": "User email address","type": "string"},{"name": "name","doc": "User display name","type": "string"},{"name": "createdAt","type": {"type": "long", "logicalType": "timestamp-millis"}}]}""")

  /** Create a record from a GenericRecord (for deserialization) */
  def fromGenericRecord(record: GenericRecord): User = {
    new User(
      record.get("id").toString(),
      record.get("email").toString(),
      record.get("name").toString(),
      Instant.ofEpochMilli(record.get("createdAt").asInstanceOf[java.lang.Long])
    )
  }
}
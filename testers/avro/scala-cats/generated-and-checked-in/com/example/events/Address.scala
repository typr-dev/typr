package com.example.events

import org.apache.avro.Schema
import org.apache.avro.Schema.Parser
import org.apache.avro.generic.GenericData.Record
import org.apache.avro.generic.GenericRecord

/** A physical address */
case class Address(
  /** Street address */
  street: String,
  /** City name */
  city: String,
  /** Postal/ZIP code */
  postalCode: String,
  /** Country code (ISO 3166-1 alpha-2) */
  country: String
) {
  /** Convert this record to a GenericRecord for serialization */
  def toGenericRecord: GenericRecord = {
    val record: Record = new Record(Address.SCHEMA)
    record.put("street", this.street)
    record.put("city", this.city)
    record.put("postalCode", this.postalCode)
    record.put("country", this.country)
    return record
  }
}

object Address {
  val SCHEMA: Schema = new Parser().parse("""{"type": "record","name": "Address","namespace": "com.example.events","doc": "A physical address","fields": [{"name": "street","doc": "Street address","type": "string"},{"name": "city","doc": "City name","type": "string"},{"name": "postalCode","doc": "Postal/ZIP code","type": "string"},{"name": "country","doc": "Country code (ISO 3166-1 alpha-2)","type": "string"}]}""")

  /** Create a record from a GenericRecord (for deserialization) */
  def fromGenericRecord(record: GenericRecord): Address = {
    new Address(
      record.get("street").toString(),
      record.get("city").toString(),
      record.get("postalCode").toString(),
      record.get("country").toString()
    )
  }
}
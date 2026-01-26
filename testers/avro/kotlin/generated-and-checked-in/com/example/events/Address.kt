package com.example.events

import org.apache.avro.Schema
import org.apache.avro.Schema.Parser
import org.apache.avro.generic.GenericData.Record
import org.apache.avro.generic.GenericRecord

/** A physical address */
data class Address(
  /** Street address */
  val street: kotlin.String,
  /** City name */
  val city: kotlin.String,
  /** Postal/ZIP code */
  val postalCode: kotlin.String,
  /** Country code (ISO 3166-1 alpha-2) */
  val country: kotlin.String
) {
  /** Convert this record to a GenericRecord for serialization */
  fun toGenericRecord(): GenericRecord {
    val record: Record = Record(Address.SCHEMA)
    record.put("street", this.street)
    record.put("city", this.city)
    record.put("postalCode", this.postalCode)
    record.put("country", this.country)
    return record
  }

  companion object {
    val SCHEMA: Schema = Parser().parse("{\"type\": \"record\",\"name\": \"Address\",\"namespace\": \"com.example.events\",\"doc\": \"A physical address\",\"fields\": [{\"name\": \"street\",\"doc\": \"Street address\",\"type\": \"string\"},{\"name\": \"city\",\"doc\": \"City name\",\"type\": \"string\"},{\"name\": \"postalCode\",\"doc\": \"Postal/ZIP code\",\"type\": \"string\"},{\"name\": \"country\",\"doc\": \"Country code (ISO 3166-1 alpha-2)\",\"type\": \"string\"}]}")

    /** Create a record from a GenericRecord (for deserialization) */
    fun fromGenericRecord(record: GenericRecord): Address = Address(record.get("street").toString(), record.get("city").toString(), record.get("postalCode").toString(), record.get("country").toString())
  }
}
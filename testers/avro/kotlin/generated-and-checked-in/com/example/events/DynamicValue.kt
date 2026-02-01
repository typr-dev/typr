package com.example.events

import java.lang.CharSequence
import java.lang.IllegalArgumentException
import org.apache.avro.Schema
import org.apache.avro.Schema.Parser
import org.apache.avro.generic.GenericData.Record
import org.apache.avro.generic.GenericRecord

/** A record with complex union types for testing union type generation */
data class DynamicValue(
  /** Unique identifier */
  val id: kotlin.String,
  /** A value that can be string, int, or boolean */
  val value: StringOrIntOrBoolean,
  /** An optional value that can be string or long */
  val optionalValue: StringOrLong?
) {
  /** Convert this record to a GenericRecord for serialization */
  fun toGenericRecord(): GenericRecord {
    val record: Record = Record(DynamicValue.SCHEMA)
    record.put("id", this.id)
    record.put("value", (if (this.value.isString()) this.value.asString() else (if (this.value.isInt()) this.value.asInt() else (if (this.value.isBoolean()) this.value.asBoolean() else null))))
    record.put("optionalValue", (if (this.optionalValue == null) null else (if (this.optionalValue.isString()) this.optionalValue.asString() else (if (this.optionalValue.isLong()) this.optionalValue.asLong() else null))))
    return record
  }

  companion object {
    val SCHEMA: Schema = Parser().parse("{\"type\": \"record\",\"name\": \"DynamicValue\",\"namespace\": \"com.example.events\",\"doc\": \"A record with complex union types for testing union type generation\",\"fields\": [{\"name\": \"id\",\"doc\": \"Unique identifier\",\"type\": \"string\"},{\"name\": \"value\",\"doc\": \"A value that can be string, int, or boolean\",\"type\": [\"string\",\"int\",\"boolean\"]},{\"name\": \"optionalValue\",\"doc\": \"An optional value that can be string or long\",\"type\": [\"null\",\"string\",\"long\"]}]}")

    /** Create a record from a GenericRecord (for deserialization) */
    fun fromGenericRecord(record: GenericRecord): DynamicValue = DynamicValue(record.get("id").toString(), (if (record.get("value") is CharSequence) StringOrIntOrBoolean.of((record.get("value") as CharSequence).toString()) else (if (record.get("value") is Int) StringOrIntOrBoolean.of((record.get("value") as Int)) else (if (record.get("value") is kotlin.Boolean) StringOrIntOrBoolean.of((record.get("value") as kotlin.Boolean)) else throw IllegalArgumentException("Unknown union type")))), (if (record.get("optionalValue") == null) null else (if (record.get("optionalValue") is CharSequence) StringOrLong.of((record.get("optionalValue") as CharSequence).toString()) else (if (record.get("optionalValue") is kotlin.Long) StringOrLong.of((record.get("optionalValue") as kotlin.Long)) else null))))
  }
}
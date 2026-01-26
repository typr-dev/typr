package com.example.events

import java.lang.CharSequence
import java.lang.IllegalArgumentException
import org.apache.avro.Schema
import org.apache.avro.Schema.Parser
import org.apache.avro.generic.GenericData.Record
import org.apache.avro.generic.GenericRecord

/** A record with complex union types for testing union type generation */
case class DynamicValue(
  /** Unique identifier */
  id: String,
  /** A value that can be string, int, or boolean */
  value: StringOrIntOrBoolean,
  /** An optional value that can be string or long */
  optionalValue: Option[StringOrLong]
) {
  /** Convert this record to a GenericRecord for serialization */
  def toGenericRecord: GenericRecord = {
    val record: Record = new Record(DynamicValue.SCHEMA)
    record.put("id", this.id)
    record.put("value", (if (this.value.isString) this.value.asString else (if (this.value.isInt) this.value.asInt else (if (this.value.isBoolean) this.value.asBoolean else null))))
    record.put("optionalValue", (if (this.optionalValue.isEmpty) null else (if (this.optionalValue.get.isString) this.optionalValue.get.asString else (if (this.optionalValue.get.isLong) this.optionalValue.get.asLong else null))))
    return record
  }
}

object DynamicValue {
  val SCHEMA: Schema = new Parser().parse("""{"type": "record","name": "DynamicValue","namespace": "com.example.events","doc": "A record with complex union types for testing union type generation","fields": [{"name": "id","doc": "Unique identifier","type": "string"},{"name": "value","doc": "A value that can be string, int, or boolean","type": ["string","int","boolean"]},{"name": "optionalValue","doc": "An optional value that can be string or long","type": ["null","string","long"]}]}""")

  /** Create a record from a GenericRecord (for deserialization) */
  def fromGenericRecord(record: GenericRecord): DynamicValue = new DynamicValue(record.get("id").toString(), (if (record.get("value").isInstanceOf[CharSequence]) StringOrIntOrBoolean.of(record.get("value").asInstanceOf[CharSequence].toString()) else (if (record.get("value").isInstanceOf[Integer]) StringOrIntOrBoolean.of(record.get("value").asInstanceOf[Integer]) else (if (record.get("value").isInstanceOf[java.lang.Boolean]) StringOrIntOrBoolean.of(record.get("value").asInstanceOf[java.lang.Boolean]) else throw new IllegalArgumentException("Unknown union type")))), Option((if (record.get("optionalValue") == null) null else (if (record.get("optionalValue").isInstanceOf[CharSequence]) StringOrLong.of(record.get("optionalValue").asInstanceOf[CharSequence].toString()) else (if (record.get("optionalValue").isInstanceOf[java.lang.Long]) StringOrLong.of(record.get("optionalValue").asInstanceOf[java.lang.Long]) else null)))))
}
package com.example.events.common

import com.example.events.precisetypes.Decimal18_4
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.nio.ByteBuffer
import org.apache.avro.Schema
import org.apache.avro.Schema.Parser
import org.apache.avro.generic.GenericData.Record
import org.apache.avro.generic.GenericRecord

/** Represents a monetary amount with currency */
data class Money(
  /** The monetary amount */
  val amount: Decimal18_4,
  /** Currency code (ISO 4217) */
  val currency: kotlin.String
) {
  /** Convert this record to a GenericRecord for serialization */
  fun toGenericRecord(): GenericRecord {
    val record: Record = Record(Money.SCHEMA)
    record.put("amount", ByteBuffer.wrap(this.amount.decimalValue().setScale(4, RoundingMode.HALF_UP).unscaledValue().toByteArray()))
    record.put("currency", this.currency)
    return record
  }

  companion object {
    val SCHEMA: Schema = Parser().parse("{\"type\": \"record\",\"name\": \"Money\",\"namespace\": \"com.example.events.common\",\"doc\": \"Represents a monetary amount with currency\",\"fields\": [{\"name\": \"amount\",\"doc\": \"The monetary amount\",\"type\": {\"type\": \"bytes\", \"logicalType\": \"decimal\", \"precision\": 18, \"scale\": 4}},{\"name\": \"currency\",\"doc\": \"Currency code (ISO 4217)\",\"type\": \"string\"}]}")

    /** Create a record from a GenericRecord (for deserialization) */
    fun fromGenericRecord(record: GenericRecord): Money = Money(Decimal18_4.unsafeForce(BigDecimal(BigInteger((record.get("amount") as ByteBuffer).array()), 4)), record.get("currency").toString())
  }
}
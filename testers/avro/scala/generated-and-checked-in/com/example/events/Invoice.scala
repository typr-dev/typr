package com.example.events

import com.example.events.common.Money
import java.time.Instant
import java.util.UUID
import org.apache.avro.Schema
import org.apache.avro.Schema.Parser
import org.apache.avro.generic.GenericData.Record
import org.apache.avro.generic.GenericRecord

/** An invoice with money amount using ref */
case class Invoice(
  /** Unique identifier for the invoice */
  invoiceId: UUID,
  /** Customer ID */
  customerId: Long,
  /** Total amount with currency */
  total: Money,
  /** When the invoice was issued */
  issuedAt: Instant
) {
  /** Convert this record to a GenericRecord for serialization */
  def toGenericRecord: GenericRecord = {
    val record: Record = new Record(Invoice.SCHEMA)
    record.put("invoiceId", this.invoiceId.toString())
    record.put("customerId", this.customerId)
    record.put("total", this.total.toGenericRecord)
    record.put("issuedAt", this.issuedAt.toEpochMilli())
    return record
  }
}

object Invoice {
  val SCHEMA: Schema = new Parser().parse("""{"type": "record","name": "Invoice","namespace": "com.example.events","doc": "An invoice with money amount using ref","fields": [{"name": "invoiceId","doc": "Unique identifier for the invoice","type": {"type": "string", "logicalType": "uuid"}},{"name": "customerId","doc": "Customer ID","type": "long"},{"name": "total","doc": "Total amount with currency","type": {"type": "record", "name": "Money", "namespace": "com.example.events.common","doc": "Represents a monetary amount with currency","fields": [{"name": "amount","doc": "The monetary amount","type": {"type": "bytes", "logicalType": "decimal", "precision": 18, "scale": 4}},{"name": "currency","doc": "Currency code (ISO 4217)","type": "string"}]}},{"name": "issuedAt","doc": "When the invoice was issued","type": {"type": "long", "logicalType": "timestamp-millis"}}]}""")

  /** Create a record from a GenericRecord (for deserialization) */
  def fromGenericRecord(record: GenericRecord): Invoice = {
    new Invoice(
      UUID.fromString(record.get("invoiceId").toString()),
      record.get("customerId").asInstanceOf[java.lang.Long],
      Money.fromGenericRecord(record.get("total").asInstanceOf[GenericRecord]),
      Instant.ofEpochMilli(record.get("issuedAt").asInstanceOf[java.lang.Long])
    )
  }
}
package com.example.events

import java.time.Instant
import java.util.UUID
import org.apache.avro.Schema
import org.apache.avro.Schema.Parser
import org.apache.avro.generic.GenericData.Record
import org.apache.avro.generic.GenericRecord

/** Event emitted when a payment is successfully charged */
case class PaymentCharged(
  /** Unique identifier for the order */
  orderId: UUID,
  /** Payment provider's payment ID */
  paymentId: String,
  /** Payment provider's transaction ID */
  transactionId: String,
  /** Amount charged in smallest currency unit */
  amount: Long,
  /** When the payment was charged */
  paidAt: Instant
) extends OrderEvents {
  /** Convert this record to a GenericRecord for serialization */
  override def toGenericRecord: GenericRecord = {
    val record: Record = new Record(PaymentCharged.SCHEMA)
    record.put("orderId", this.orderId.toString())
    record.put("paymentId", this.paymentId)
    record.put("transactionId", this.transactionId)
    record.put("amount", this.amount)
    record.put("paidAt", this.paidAt.toEpochMilli())
    return record
  }
}

object PaymentCharged {
  val SCHEMA: Schema = new Parser().parse("""{"type": "record","name": "PaymentCharged","namespace": "com.example.events","doc": "Event emitted when a payment is successfully charged","fields": [{"name": "orderId","doc": "Unique identifier for the order","type": {"type": "string", "logicalType": "uuid"}},{"name": "paymentId","doc": "Payment provider's payment ID","type": "string"},{"name": "transactionId","doc": "Payment provider's transaction ID","type": "string"},{"name": "amount","doc": "Amount charged in smallest currency unit","type": "long"},{"name": "paidAt","doc": "When the payment was charged","type": {"type": "long", "logicalType": "timestamp-millis"}}]}""")

  /** Create a record from a GenericRecord (for deserialization) */
  def fromGenericRecord(record: GenericRecord): PaymentCharged = {
    new PaymentCharged(
      UUID.fromString(record.get("orderId").toString()),
      record.get("paymentId").toString(),
      record.get("transactionId").toString(),
      record.get("amount").asInstanceOf[java.lang.Long],
      Instant.ofEpochMilli(record.get("paidAt").asInstanceOf[java.lang.Long])
    )
  }
}
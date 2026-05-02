package com.example.events

import java.time.Instant
import org.apache.avro.Schema
import org.apache.avro.Schema.Parser
import org.apache.avro.generic.GenericData.Record
import org.apache.avro.generic.GenericRecord

/** Callback received from payment provider when payment status changes */
case class PaymentCallback(
  /** Order ID for correlation */
  orderId: String,
  /** Payment provider's payment ID */
  paymentId: String,
  /** Payment provider's transaction ID */
  transactionId: String,
  /** Amount in smallest currency unit */
  amount: Long,
  /** When the payment was charged */
  paidAt: Instant
) extends OrderEvents {
  /** Convert this record to a GenericRecord for serialization */
  override def toGenericRecord: GenericRecord = {
    val record: Record = new Record(PaymentCallback.SCHEMA)
    record.put("orderId", this.orderId)
    record.put("paymentId", this.paymentId)
    record.put("transactionId", this.transactionId)
    record.put("amount", this.amount)
    record.put("paidAt", this.paidAt.toEpochMilli())
    return record
  }
}

object PaymentCallback {
  val SCHEMA: Schema = new Parser().parse("""{"type": "record","name": "PaymentCallback","namespace": "com.example.events","doc": "Callback received from payment provider when payment status changes","fields": [{"name": "orderId","doc": "Order ID for correlation","type": "string"},{"name": "paymentId","doc": "Payment provider's payment ID","type": "string"},{"name": "transactionId","doc": "Payment provider's transaction ID","type": "string"},{"name": "amount","doc": "Amount in smallest currency unit","type": "long"},{"name": "paidAt","doc": "When the payment was charged","type": {"type": "long", "logicalType": "timestamp-millis"}}]}""")

  /** Create a record from a GenericRecord (for deserialization) */
  def fromGenericRecord(record: GenericRecord): PaymentCallback = {
    new PaymentCallback(
      record.get("orderId").toString(),
      record.get("paymentId").toString(),
      record.get("transactionId").toString(),
      record.get("amount").asInstanceOf[java.lang.Long],
      Instant.ofEpochMilli(record.get("paidAt").asInstanceOf[java.lang.Long])
    )
  }
}
package com.example.events

import com.example.events.precisetypes.Decimal10_2
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import org.apache.avro.Schema
import org.apache.avro.Schema.Parser
import org.apache.avro.generic.GenericData.Record
import org.apache.avro.generic.GenericRecord

/** Event emitted when an order is cancelled */
data class OrderCancelled(
  /** Unique identifier for the order */
  val orderId: UUID,
  /** Customer who placed the order */
  val customerId: kotlin.Long,
  /** Optional cancellation reason */
  val reason: kotlin.String?,
  /** When the order was cancelled */
  val cancelledAt: Instant,
  /** Amount to be refunded, if applicable */
  val refundAmount: Decimal10_2?
) : OrderEvents {
  /** Convert this record to a GenericRecord for serialization */
  override fun toGenericRecord(): GenericRecord {
    val record: Record = Record(OrderCancelled.SCHEMA)
    record.put("orderId", this.orderId.toString())
    record.put("customerId", this.customerId)
    record.put("reason", (if (this.reason == null) null else this.reason))
    record.put("cancelledAt", this.cancelledAt.toEpochMilli())
    record.put("refundAmount", (if (this.refundAmount == null) null else ByteBuffer.wrap(this.refundAmount.decimalValue().setScale(2, RoundingMode.HALF_UP).unscaledValue().toByteArray())))
    return record
  }

  companion object {
    val SCHEMA: Schema = Parser().parse("{\"type\": \"record\",\"name\": \"OrderCancelled\",\"namespace\": \"com.example.events\",\"doc\": \"Event emitted when an order is cancelled\",\"fields\": [{\"name\": \"orderId\",\"doc\": \"Unique identifier for the order\",\"type\": {\"type\": \"string\", \"logicalType\": \"uuid\"}},{\"name\": \"customerId\",\"doc\": \"Customer who placed the order\",\"type\": \"long\"},{\"name\": \"reason\",\"doc\": \"Optional cancellation reason\",\"type\": [\"null\",\"string\"],\"default\": null},{\"name\": \"cancelledAt\",\"doc\": \"When the order was cancelled\",\"type\": {\"type\": \"long\", \"logicalType\": \"timestamp-millis\"}},{\"name\": \"refundAmount\",\"doc\": \"Amount to be refunded, if applicable\",\"type\": [\"null\",{\"type\": \"bytes\", \"logicalType\": \"decimal\", \"precision\": 10, \"scale\": 2}],\"default\": null}]}")

    /** Create a record from a GenericRecord (for deserialization) */
    fun fromGenericRecord(record: GenericRecord): OrderCancelled = OrderCancelled(UUID.fromString(record.get("orderId").toString()), (record.get("customerId") as kotlin.Long), (if (record.get("reason") == null) null else record.get("reason").toString()), Instant.ofEpochMilli((record.get("cancelledAt") as Long)), (if (record.get("refundAmount") == null) null else Decimal10_2.unsafeForce(BigDecimal(BigInteger((record.get("refundAmount") as ByteBuffer).array()), 2))))
  }
}
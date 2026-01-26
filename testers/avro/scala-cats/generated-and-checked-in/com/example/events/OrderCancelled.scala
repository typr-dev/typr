package com.example.events

import com.example.events.precisetypes.Decimal10_2
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
case class OrderCancelled(
  /** Unique identifier for the order */
  orderId: UUID,
  /** Customer who placed the order */
  customerId: Long,
  /** Optional cancellation reason */
  reason: Option[String],
  /** When the order was cancelled */
  cancelledAt: Instant,
  /** Amount to be refunded, if applicable */
  refundAmount: Option[Decimal10_2]
) extends OrderEvents {
  /** Convert this record to a GenericRecord for serialization */
  override def toGenericRecord: GenericRecord = {
    val record: Record = new Record(OrderCancelled.SCHEMA)
    record.put("orderId", this.orderId.toString())
    record.put("customerId", this.customerId)
    record.put("reason", (if (this.reason.isEmpty) null else this.reason.get))
    record.put("cancelledAt", this.cancelledAt.toEpochMilli())
    record.put("refundAmount", (if (this.refundAmount.isEmpty) null else ByteBuffer.wrap(this.refundAmount.get.decimalValue.setScale(2, RoundingMode.HALF_UP).unscaledValue().toByteArray())))
    return record
  }
}

object OrderCancelled {
  val SCHEMA: Schema = new Parser().parse("""{"type": "record","name": "OrderCancelled","namespace": "com.example.events","doc": "Event emitted when an order is cancelled","fields": [{"name": "orderId","doc": "Unique identifier for the order","type": {"type": "string", "logicalType": "uuid"}},{"name": "customerId","doc": "Customer who placed the order","type": "long"},{"name": "reason","doc": "Optional cancellation reason","type": ["null","string"],"default": null},{"name": "cancelledAt","doc": "When the order was cancelled","type": {"type": "long", "logicalType": "timestamp-millis"}},{"name": "refundAmount","doc": "Amount to be refunded, if applicable","type": ["null",{"type": "bytes", "logicalType": "decimal", "precision": 10, "scale": 2}],"default": null}]}""")

  /** Create a record from a GenericRecord (for deserialization) */
  def fromGenericRecord(record: GenericRecord): OrderCancelled = {
    new OrderCancelled(
      UUID.fromString(record.get("orderId").toString()),
      record.get("customerId").asInstanceOf[java.lang.Long],
      Option((if (record.get("reason") == null) null else record.get("reason").toString())),
      Instant.ofEpochMilli(record.get("cancelledAt").asInstanceOf[java.lang.Long]),
      Option((if (record.get("refundAmount") == null) null else Decimal10_2.unsafeForce(new java.math.BigDecimal(new BigInteger(record.get("refundAmount").asInstanceOf[ByteBuffer].array()), 2))))
    )
  }
}
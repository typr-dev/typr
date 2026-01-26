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
import scala.jdk.CollectionConverters.ListHasAsScala
import scala.jdk.CollectionConverters.SeqHasAsJava

/** Event emitted when an order is placed */
case class OrderPlaced(
  /** Unique identifier for the order */
  orderId: UUID,
  /** Customer who placed the order */
  customerId: Long,
  /** Total amount of the order */
  totalAmount: Decimal10_2,
  /** When the order was placed */
  placedAt: Instant,
  /** List of item IDs in the order */
  items: List[String],
  /** Optional shipping address */
  shippingAddress: Option[String]
) extends OrderEvents {
  /** Convert this record to a GenericRecord for serialization */
  override def toGenericRecord: GenericRecord = {
    val record: Record = new Record(OrderPlaced.SCHEMA)
    record.put("orderId", this.orderId.toString())
    record.put("customerId", this.customerId)
    record.put("totalAmount", ByteBuffer.wrap(this.totalAmount.decimalValue.setScale(2, RoundingMode.HALF_UP).unscaledValue().toByteArray()))
    record.put("placedAt", this.placedAt.toEpochMilli())
    record.put("items", this.items.map(e => e).toList.asJava)
    record.put("shippingAddress", (if (this.shippingAddress.isEmpty) null else this.shippingAddress.get))
    return record
  }
}

object OrderPlaced {
  val SCHEMA: Schema = new Parser().parse("""{"type": "record","name": "OrderPlaced","namespace": "com.example.events","doc": "Event emitted when an order is placed","fields": [{"name": "orderId","doc": "Unique identifier for the order","type": {"type": "string", "logicalType": "uuid"}},{"name": "customerId","doc": "Customer who placed the order","type": "long"},{"name": "totalAmount","doc": "Total amount of the order","type": {"type": "bytes", "logicalType": "decimal", "precision": 10, "scale": 2}},{"name": "placedAt","doc": "When the order was placed","type": {"type": "long", "logicalType": "timestamp-millis"}},{"name": "items","doc": "List of item IDs in the order","type": {"type": "array", "items": "string"}},{"name": "shippingAddress","doc": "Optional shipping address","type": ["null","string"],"default": null}]}""")

  /** Create a record from a GenericRecord (for deserialization) */
  def fromGenericRecord(record: GenericRecord): OrderPlaced = {
    new OrderPlaced(
      UUID.fromString(record.get("orderId").toString()),
      record.get("customerId").asInstanceOf[java.lang.Long],
      Decimal10_2.unsafeForce(new java.math.BigDecimal(new BigInteger(record.get("totalAmount").asInstanceOf[ByteBuffer].array()), 2)),
      Instant.ofEpochMilli(record.get("placedAt").asInstanceOf[java.lang.Long]),
      record.get("items").asInstanceOf[java.util.List[?]].asScala.toList.map(e => e.toString()).toList,
      Option((if (record.get("shippingAddress") == null) null else record.get("shippingAddress").toString()))
    )
  }
}
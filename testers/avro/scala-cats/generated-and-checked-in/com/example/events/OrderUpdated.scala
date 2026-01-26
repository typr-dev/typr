package com.example.events

import java.time.Instant
import java.util.UUID
import org.apache.avro.Schema
import org.apache.avro.Schema.Parser
import org.apache.avro.generic.GenericData.EnumSymbol
import org.apache.avro.generic.GenericData.Record
import org.apache.avro.generic.GenericRecord

/** Event emitted when an order status changes */
case class OrderUpdated(
  /** Unique identifier for the order */
  orderId: UUID,
  /** Previous status of the order */
  previousStatus: OrderStatus,
  /** New status of the order */
  newStatus: OrderStatus,
  /** When the status was updated */
  updatedAt: Instant,
  /** Shipping address if status is SHIPPED */
  shippingAddress: Option[Address]
) extends OrderEvents {
  /** Convert this record to a GenericRecord for serialization */
  override def toGenericRecord: GenericRecord = {
    val record: Record = new Record(OrderUpdated.SCHEMA)
    record.put("orderId", this.orderId.toString())
    record.put("previousStatus", new EnumSymbol(OrderUpdated.SCHEMA.getField("previousStatus").schema(), this.previousStatus.value))
    record.put("newStatus", new EnumSymbol(OrderUpdated.SCHEMA.getField("newStatus").schema(), this.newStatus.value))
    record.put("updatedAt", this.updatedAt.toEpochMilli())
    record.put("shippingAddress", (if (this.shippingAddress.isEmpty) null else this.shippingAddress.get.toGenericRecord))
    return record
  }
}

object OrderUpdated {
  val SCHEMA: Schema = new Parser().parse("""{"type": "record","name": "OrderUpdated","namespace": "com.example.events","doc": "Event emitted when an order status changes","fields": [{"name": "orderId","doc": "Unique identifier for the order","type": {"type": "string", "logicalType": "uuid"}},{"name": "previousStatus","doc": "Previous status of the order","type": {"type": "enum", "name": "OrderStatus", "namespace": "com.example.events","symbols": ["PENDING","CONFIRMED","SHIPPED","DELIVERED","CANCELLED"]}},{"name": "newStatus","doc": "New status of the order","type": "com.example.events.OrderStatus"},{"name": "updatedAt","doc": "When the status was updated","type": {"type": "long", "logicalType": "timestamp-millis"}},{"name": "shippingAddress","doc": "Shipping address if status is SHIPPED","type": ["null",{"type": "record", "name": "Address", "namespace": "com.example.events","doc": "A physical address","fields": [{"name": "street","doc": "Street address","type": "string"},{"name": "city","doc": "City name","type": "string"},{"name": "postalCode","doc": "Postal/ZIP code","type": "string"},{"name": "country","doc": "Country code (ISO 3166-1 alpha-2)","type": "string"}]}],"default": null}]}""")

  /** Create a record from a GenericRecord (for deserialization) */
  def fromGenericRecord(record: GenericRecord): OrderUpdated = {
    new OrderUpdated(
      UUID.fromString(record.get("orderId").toString()),
      OrderStatus.force(record.get("previousStatus").toString()),
      OrderStatus.force(record.get("newStatus").toString()),
      Instant.ofEpochMilli(record.get("updatedAt").asInstanceOf[java.lang.Long]),
      Option((if (record.get("shippingAddress") == null) null else Address.fromGenericRecord(record.get("shippingAddress").asInstanceOf[GenericRecord])))
    )
  }
}
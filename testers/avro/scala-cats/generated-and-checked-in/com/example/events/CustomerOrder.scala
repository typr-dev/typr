package com.example.events

import org.apache.avro.Schema
import org.apache.avro.Schema.Parser
import org.apache.avro.generic.GenericData.Record
import org.apache.avro.generic.GenericRecord

/** Order with wrapper types for type-safe IDs */
case class CustomerOrder(
  /** Unique order identifier */
  orderId: OrderId,
  /** Customer identifier */
  customerId: CustomerId,
  /** Customer email address */
  email: Option[Email],
  /** Order amount in cents (no wrapper) */
  amount: Long
) {
  /** Convert this record to a GenericRecord for serialization */
  def toGenericRecord: GenericRecord = {
    val record: Record = new Record(CustomerOrder.SCHEMA)
    record.put("orderId", this.orderId.unwrap)
    record.put("customerId", this.customerId.unwrap)
    record.put("email", (if (this.email.isEmpty) null else this.email.get.unwrap))
    record.put("amount", this.amount)
    return record
  }
}

object CustomerOrder {
  val SCHEMA: Schema = new Parser().parse("""{"type": "record","name": "CustomerOrder","namespace": "com.example.events","doc": "Order with wrapper types for type-safe IDs","fields": [{"name": "orderId","doc": "Unique order identifier","type": "string"},{"name": "customerId","doc": "Customer identifier","type": "long"},{"name": "email","doc": "Customer email address","type": ["null","string"],"default": null},{"name": "amount","doc": "Order amount in cents (no wrapper)","type": "long"}]}""")

  /** Create a record from a GenericRecord (for deserialization) */
  def fromGenericRecord(record: GenericRecord): CustomerOrder = {
    new CustomerOrder(
      OrderId.valueOf(record.get("orderId").toString()),
      CustomerId.valueOf(record.get("customerId").asInstanceOf[java.lang.Long]),
      (if (record.get("email") == null) None else Some(Email.valueOf(record.get("email").toString()))),
      record.get("amount").asInstanceOf[java.lang.Long]
    )
  }
}
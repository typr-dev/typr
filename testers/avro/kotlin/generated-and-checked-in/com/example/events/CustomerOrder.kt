package com.example.events

import org.apache.avro.Schema
import org.apache.avro.Schema.Parser
import org.apache.avro.generic.GenericData.Record
import org.apache.avro.generic.GenericRecord

/** Order with wrapper types for type-safe IDs */
data class CustomerOrder(
  /** Unique order identifier */
  val orderId: OrderId,
  /** Customer identifier */
  val customerId: CustomerId,
  /** Customer email address */
  val email: Email?,
  /** Order amount in cents (no wrapper) */
  val amount: kotlin.Long
) {
  /** Convert this record to a GenericRecord for serialization */
  fun toGenericRecord(): GenericRecord {
    val record: Record = Record(CustomerOrder.SCHEMA)
    record.put("orderId", this.orderId.unwrap())
    record.put("customerId", this.customerId.unwrap())
    record.put("email", (if (this.email == null) null else this.email.unwrap()))
    record.put("amount", this.amount)
    return record
  }

  companion object {
    val SCHEMA: Schema = Parser().parse("{\"type\": \"record\",\"name\": \"CustomerOrder\",\"namespace\": \"com.example.events\",\"doc\": \"Order with wrapper types for type-safe IDs\",\"fields\": [{\"name\": \"orderId\",\"doc\": \"Unique order identifier\",\"type\": \"string\"},{\"name\": \"customerId\",\"doc\": \"Customer identifier\",\"type\": \"long\"},{\"name\": \"email\",\"doc\": \"Customer email address\",\"type\": [\"null\",\"string\"],\"default\": null},{\"name\": \"amount\",\"doc\": \"Order amount in cents (no wrapper)\",\"type\": \"long\"}]}")

    /** Create a record from a GenericRecord (for deserialization) */
    fun fromGenericRecord(record: GenericRecord): CustomerOrder = CustomerOrder(OrderId.valueOf(record.get("orderId").toString()), CustomerId.valueOf((record.get("customerId") as kotlin.Long)), (if (record.get("email") == null) null else Email.valueOf(record.get("email").toString())), (record.get("amount") as kotlin.Long))
  }
}
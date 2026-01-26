package com.example.events;

import java.util.Optional;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Parser;
import org.apache.avro.generic.GenericData.Record;
import org.apache.avro.generic.GenericRecord;

/** Order with wrapper types for type-safe IDs */
public record CustomerOrder(
    /** Unique order identifier */
    OrderId orderId,
    /** Customer identifier */
    CustomerId customerId,
    /** Customer email address */
    Optional<Email> email,
    /** Order amount in cents (no wrapper) */
    Long amount) {
  /** Unique order identifier */
  public CustomerOrder withOrderId(OrderId orderId) {
    return new CustomerOrder(orderId, customerId, email, amount);
  }

  /** Customer identifier */
  public CustomerOrder withCustomerId(CustomerId customerId) {
    return new CustomerOrder(orderId, customerId, email, amount);
  }

  /** Customer email address */
  public CustomerOrder withEmail(Optional<Email> email) {
    return new CustomerOrder(orderId, customerId, email, amount);
  }

  /** Order amount in cents (no wrapper) */
  public CustomerOrder withAmount(Long amount) {
    return new CustomerOrder(orderId, customerId, email, amount);
  }

  public static Schema SCHEMA =
      new Parser()
          .parse(
              "{\"type\": \"record\",\"name\": \"CustomerOrder\",\"namespace\":"
                  + " \"com.example.events\",\"doc\": \"Order with wrapper types for type-safe"
                  + " IDs\",\"fields\": [{\"name\": \"orderId\",\"doc\": \"Unique order"
                  + " identifier\",\"type\": \"string\"},{\"name\": \"customerId\",\"doc\":"
                  + " \"Customer identifier\",\"type\": \"long\"},{\"name\": \"email\",\"doc\":"
                  + " \"Customer email address\",\"type\": [\"null\",\"string\"],\"default\":"
                  + " null},{\"name\": \"amount\",\"doc\": \"Order amount in cents (no"
                  + " wrapper)\",\"type\": \"long\"}]}");

  /** Create a record from a GenericRecord (for deserialization) */
  public static CustomerOrder fromGenericRecord(GenericRecord record) {
    return new CustomerOrder(
        OrderId.valueOf(record.get("orderId").toString()),
        CustomerId.valueOf(((Long) record.get("customerId"))),
        (record.get("email") == null
            ? Optional.empty()
            : Optional.of(Email.valueOf(record.get("email").toString()))),
        ((Long) record.get("amount")));
  }

  /** Convert this record to a GenericRecord for serialization */
  public GenericRecord toGenericRecord() {
    Record record = new Record(CustomerOrder.SCHEMA);
    record.put("orderId", this.orderId().unwrap());
    record.put("customerId", this.customerId().unwrap());
    record.put("email", (this.email().isEmpty() ? null : this.email().get().unwrap()));
    record.put("amount", this.amount());
    return record;
  }
}

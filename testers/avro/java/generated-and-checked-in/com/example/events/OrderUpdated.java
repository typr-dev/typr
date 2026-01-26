package com.example.events;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Parser;
import org.apache.avro.generic.GenericData.EnumSymbol;
import org.apache.avro.generic.GenericData.Record;
import org.apache.avro.generic.GenericRecord;

/** Event emitted when an order status changes */
public record OrderUpdated(
    /** Unique identifier for the order */
    UUID orderId,
    /** Previous status of the order */
    OrderStatus previousStatus,
    /** New status of the order */
    OrderStatus newStatus,
    /** When the status was updated */
    Instant updatedAt,
    /** Shipping address if status is SHIPPED */
    Optional<Address> shippingAddress)
    implements OrderEvents {
  /** Unique identifier for the order */
  public OrderUpdated withOrderId(UUID orderId) {
    return new OrderUpdated(orderId, previousStatus, newStatus, updatedAt, shippingAddress);
  }

  /** Previous status of the order */
  public OrderUpdated withPreviousStatus(OrderStatus previousStatus) {
    return new OrderUpdated(orderId, previousStatus, newStatus, updatedAt, shippingAddress);
  }

  /** New status of the order */
  public OrderUpdated withNewStatus(OrderStatus newStatus) {
    return new OrderUpdated(orderId, previousStatus, newStatus, updatedAt, shippingAddress);
  }

  /** When the status was updated */
  public OrderUpdated withUpdatedAt(Instant updatedAt) {
    return new OrderUpdated(orderId, previousStatus, newStatus, updatedAt, shippingAddress);
  }

  /** Shipping address if status is SHIPPED */
  public OrderUpdated withShippingAddress(Optional<Address> shippingAddress) {
    return new OrderUpdated(orderId, previousStatus, newStatus, updatedAt, shippingAddress);
  }

  public static Schema SCHEMA =
      new Parser()
          .parse(
              "{\"type\": \"record\",\"name\": \"OrderUpdated\",\"namespace\":"
                  + " \"com.example.events\",\"doc\": \"Event emitted when an order status"
                  + " changes\",\"fields\": [{\"name\": \"orderId\",\"doc\": \"Unique identifier"
                  + " for the order\",\"type\": {\"type\": \"string\", \"logicalType\":"
                  + " \"uuid\"}},{\"name\": \"previousStatus\",\"doc\": \"Previous status of the"
                  + " order\",\"type\": {\"type\": \"enum\", \"name\": \"OrderStatus\","
                  + " \"namespace\": \"com.example.events\",\"symbols\":"
                  + " [\"PENDING\",\"CONFIRMED\",\"SHIPPED\",\"DELIVERED\",\"CANCELLED\"]}},{\"name\":"
                  + " \"newStatus\",\"doc\": \"New status of the order\",\"type\":"
                  + " \"com.example.events.OrderStatus\"},{\"name\": \"updatedAt\",\"doc\": \"When"
                  + " the status was updated\",\"type\": {\"type\": \"long\", \"logicalType\":"
                  + " \"timestamp-millis\"}},{\"name\": \"shippingAddress\",\"doc\": \"Shipping"
                  + " address if status is SHIPPED\",\"type\": [\"null\",{\"type\": \"record\","
                  + " \"name\": \"Address\", \"namespace\": \"com.example.events\",\"doc\": \"A"
                  + " physical address\",\"fields\": [{\"name\": \"street\",\"doc\": \"Street"
                  + " address\",\"type\": \"string\"},{\"name\": \"city\",\"doc\": \"City"
                  + " name\",\"type\": \"string\"},{\"name\": \"postalCode\",\"doc\": \"Postal/ZIP"
                  + " code\",\"type\": \"string\"},{\"name\": \"country\",\"doc\": \"Country code"
                  + " (ISO 3166-1 alpha-2)\",\"type\": \"string\"}]}],\"default\": null}]}");

  /** Create a record from a GenericRecord (for deserialization) */
  public static OrderUpdated fromGenericRecord(GenericRecord record) {
    return new OrderUpdated(
        UUID.fromString(record.get("orderId").toString()),
        OrderStatus.valueOf(record.get("previousStatus").toString()),
        OrderStatus.valueOf(record.get("newStatus").toString()),
        Instant.ofEpochMilli(((Long) record.get("updatedAt"))),
        Optional.ofNullable(
            (record.get("shippingAddress") == null
                ? null
                : Address.fromGenericRecord(((GenericRecord) record.get("shippingAddress"))))));
  }

  /** Convert this record to a GenericRecord for serialization */
  @Override
  public GenericRecord toGenericRecord() {
    Record record = new Record(OrderUpdated.SCHEMA);
    record.put("orderId", this.orderId().toString());
    record.put(
        "previousStatus",
        new EnumSymbol(
            OrderUpdated.SCHEMA.getField("previousStatus").schema(), this.previousStatus().name()));
    record.put(
        "newStatus",
        new EnumSymbol(
            OrderUpdated.SCHEMA.getField("newStatus").schema(), this.newStatus().name()));
    record.put("updatedAt", this.updatedAt().toEpochMilli());
    record.put(
        "shippingAddress",
        (this.shippingAddress().isEmpty() ? null : this.shippingAddress().get().toGenericRecord()));
    return record;
  }
}

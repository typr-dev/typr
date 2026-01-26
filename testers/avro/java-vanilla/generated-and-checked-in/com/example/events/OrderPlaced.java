package com.example.events;

import com.example.events.precisetypes.Decimal10_2;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Parser;
import org.apache.avro.generic.GenericData.Record;
import org.apache.avro.generic.GenericRecord;

/** Event emitted when an order is placed */
public record OrderPlaced(
    /** Unique identifier for the order */
    UUID orderId,
    /** Customer who placed the order */
    Long customerId,
    /** Total amount of the order */
    Decimal10_2 totalAmount,
    /** When the order was placed */
    Instant placedAt,
    /** List of item IDs in the order */
    List<String> items,
    /** Optional shipping address */
    Optional<String> shippingAddress)
    implements OrderEvents {
  /** Unique identifier for the order */
  public OrderPlaced withOrderId(UUID orderId) {
    return new OrderPlaced(orderId, customerId, totalAmount, placedAt, items, shippingAddress);
  }

  /** Customer who placed the order */
  public OrderPlaced withCustomerId(Long customerId) {
    return new OrderPlaced(orderId, customerId, totalAmount, placedAt, items, shippingAddress);
  }

  /** Total amount of the order */
  public OrderPlaced withTotalAmount(Decimal10_2 totalAmount) {
    return new OrderPlaced(orderId, customerId, totalAmount, placedAt, items, shippingAddress);
  }

  /** When the order was placed */
  public OrderPlaced withPlacedAt(Instant placedAt) {
    return new OrderPlaced(orderId, customerId, totalAmount, placedAt, items, shippingAddress);
  }

  /** List of item IDs in the order */
  public OrderPlaced withItems(List<String> items) {
    return new OrderPlaced(orderId, customerId, totalAmount, placedAt, items, shippingAddress);
  }

  /** Optional shipping address */
  public OrderPlaced withShippingAddress(Optional<String> shippingAddress) {
    return new OrderPlaced(orderId, customerId, totalAmount, placedAt, items, shippingAddress);
  }

  public static Schema SCHEMA =
      new Parser()
          .parse(
              "{\"type\": \"record\",\"name\": \"OrderPlaced\",\"namespace\":"
                  + " \"com.example.events\",\"doc\": \"Event emitted when an order is"
                  + " placed\",\"fields\": [{\"name\": \"orderId\",\"doc\": \"Unique identifier for"
                  + " the order\",\"type\": {\"type\": \"string\", \"logicalType\":"
                  + " \"uuid\"}},{\"name\": \"customerId\",\"doc\": \"Customer who placed the"
                  + " order\",\"type\": \"long\"},{\"name\": \"totalAmount\",\"doc\": \"Total"
                  + " amount of the order\",\"type\": {\"type\": \"bytes\", \"logicalType\":"
                  + " \"decimal\", \"precision\": 10, \"scale\": 2}},{\"name\":"
                  + " \"placedAt\",\"doc\": \"When the order was placed\",\"type\": {\"type\":"
                  + " \"long\", \"logicalType\": \"timestamp-millis\"}},{\"name\":"
                  + " \"items\",\"doc\": \"List of item IDs in the order\",\"type\": {\"type\":"
                  + " \"array\", \"items\": \"string\"}},{\"name\": \"shippingAddress\",\"doc\":"
                  + " \"Optional shipping address\",\"type\": [\"null\",\"string\"],\"default\":"
                  + " null}]}");

  /** Create a record from a GenericRecord (for deserialization) */
  public static OrderPlaced fromGenericRecord(GenericRecord record) {
    return new OrderPlaced(
        UUID.fromString(record.get("orderId").toString()),
        ((Long) record.get("customerId")),
        Decimal10_2.unsafeForce(
            new BigDecimal(new BigInteger(((ByteBuffer) record.get("totalAmount")).array()), 2)),
        Instant.ofEpochMilli(((Long) record.get("placedAt"))),
        ((List<?>) record.get("items")).stream().map(e -> e.toString()).toList(),
        Optional.ofNullable(
            (record.get("shippingAddress") == null
                ? null
                : record.get("shippingAddress").toString())));
  }

  /** Convert this record to a GenericRecord for serialization */
  @Override
  public GenericRecord toGenericRecord() {
    Record record = new Record(OrderPlaced.SCHEMA);
    record.put("orderId", this.orderId().toString());
    record.put("customerId", this.customerId());
    record.put(
        "totalAmount",
        ByteBuffer.wrap(
            this.totalAmount()
                .decimalValue()
                .setScale(2, RoundingMode.HALF_UP)
                .unscaledValue()
                .toByteArray()));
    record.put("placedAt", this.placedAt().toEpochMilli());
    record.put("items", this.items().stream().map(e -> e).toList());
    record.put(
        "shippingAddress",
        (this.shippingAddress().isEmpty() ? null : this.shippingAddress().get()));
    return record;
  }
}

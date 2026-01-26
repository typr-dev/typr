package com.example.events;

import com.example.events.precisetypes.Decimal10_2;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Parser;
import org.apache.avro.generic.GenericData.Record;
import org.apache.avro.generic.GenericRecord;

/** Event emitted when an order is cancelled */
public record OrderCancelled(
    /** Unique identifier for the order */
    UUID orderId,
    /** Customer who placed the order */
    Long customerId,
    /** Optional cancellation reason */
    Optional<String> reason,
    /** When the order was cancelled */
    Instant cancelledAt,
    /** Amount to be refunded, if applicable */
    Optional<Decimal10_2> refundAmount)
    implements OrderEvents {
  /** Unique identifier for the order */
  public OrderCancelled withOrderId(UUID orderId) {
    return new OrderCancelled(orderId, customerId, reason, cancelledAt, refundAmount);
  }

  /** Customer who placed the order */
  public OrderCancelled withCustomerId(Long customerId) {
    return new OrderCancelled(orderId, customerId, reason, cancelledAt, refundAmount);
  }

  /** Optional cancellation reason */
  public OrderCancelled withReason(Optional<String> reason) {
    return new OrderCancelled(orderId, customerId, reason, cancelledAt, refundAmount);
  }

  /** When the order was cancelled */
  public OrderCancelled withCancelledAt(Instant cancelledAt) {
    return new OrderCancelled(orderId, customerId, reason, cancelledAt, refundAmount);
  }

  /** Amount to be refunded, if applicable */
  public OrderCancelled withRefundAmount(Optional<Decimal10_2> refundAmount) {
    return new OrderCancelled(orderId, customerId, reason, cancelledAt, refundAmount);
  }

  public static Schema SCHEMA =
      new Parser()
          .parse(
              "{\"type\": \"record\",\"name\": \"OrderCancelled\",\"namespace\":"
                  + " \"com.example.events\",\"doc\": \"Event emitted when an order is"
                  + " cancelled\",\"fields\": [{\"name\": \"orderId\",\"doc\": \"Unique identifier"
                  + " for the order\",\"type\": {\"type\": \"string\", \"logicalType\":"
                  + " \"uuid\"}},{\"name\": \"customerId\",\"doc\": \"Customer who placed the"
                  + " order\",\"type\": \"long\"},{\"name\": \"reason\",\"doc\": \"Optional"
                  + " cancellation reason\",\"type\": [\"null\",\"string\"],\"default\":"
                  + " null},{\"name\": \"cancelledAt\",\"doc\": \"When the order was"
                  + " cancelled\",\"type\": {\"type\": \"long\", \"logicalType\":"
                  + " \"timestamp-millis\"}},{\"name\": \"refundAmount\",\"doc\": \"Amount to be"
                  + " refunded, if applicable\",\"type\": [\"null\",{\"type\": \"bytes\","
                  + " \"logicalType\": \"decimal\", \"precision\": 10, \"scale\": 2}],\"default\":"
                  + " null}]}");

  /** Create a record from a GenericRecord (for deserialization) */
  public static OrderCancelled fromGenericRecord(GenericRecord record) {
    return new OrderCancelled(
        UUID.fromString(record.get("orderId").toString()),
        ((Long) record.get("customerId")),
        Optional.ofNullable(
            (record.get("reason") == null ? null : record.get("reason").toString())),
        Instant.ofEpochMilli(((Long) record.get("cancelledAt"))),
        Optional.ofNullable(
            (record.get("refundAmount") == null
                ? null
                : Decimal10_2.unsafeForce(
                    new BigDecimal(
                        new BigInteger(((ByteBuffer) record.get("refundAmount")).array()), 2)))));
  }

  /** Convert this record to a GenericRecord for serialization */
  @Override
  public GenericRecord toGenericRecord() {
    Record record = new Record(OrderCancelled.SCHEMA);
    record.put("orderId", this.orderId().toString());
    record.put("customerId", this.customerId());
    record.put("reason", (this.reason().isEmpty() ? null : this.reason().get()));
    record.put("cancelledAt", this.cancelledAt().toEpochMilli());
    record.put(
        "refundAmount",
        (this.refundAmount().isEmpty()
            ? null
            : ByteBuffer.wrap(
                this.refundAmount()
                    .get()
                    .decimalValue()
                    .setScale(2, RoundingMode.HALF_UP)
                    .unscaledValue()
                    .toByteArray())));
    return record;
  }
}

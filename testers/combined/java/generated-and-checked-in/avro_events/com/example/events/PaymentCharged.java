package com.example.events;

import java.time.Instant;
import java.util.UUID;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Parser;
import org.apache.avro.generic.GenericData.Record;
import org.apache.avro.generic.GenericRecord;

/** Event emitted when a payment is successfully charged */
public record PaymentCharged(
  /** Unique identifier for the order */
  UUID orderId,
  /** Payment provider's payment ID */
  String paymentId,
  /** Payment provider's transaction ID */
  String transactionId,
  /** Amount charged in smallest currency unit */
  Long amount,
  /** When the payment was charged */
  Instant paidAt
) implements OrderEvents {
  /** Unique identifier for the order */
  public PaymentCharged withOrderId(UUID orderId) {
    return new PaymentCharged(orderId, paymentId, transactionId, amount, paidAt);
  }

  /** Payment provider's payment ID */
  public PaymentCharged withPaymentId(String paymentId) {
    return new PaymentCharged(orderId, paymentId, transactionId, amount, paidAt);
  }

  /** Payment provider's transaction ID */
  public PaymentCharged withTransactionId(String transactionId) {
    return new PaymentCharged(orderId, paymentId, transactionId, amount, paidAt);
  }

  /** Amount charged in smallest currency unit */
  public PaymentCharged withAmount(Long amount) {
    return new PaymentCharged(orderId, paymentId, transactionId, amount, paidAt);
  }

  /** When the payment was charged */
  public PaymentCharged withPaidAt(Instant paidAt) {
    return new PaymentCharged(orderId, paymentId, transactionId, amount, paidAt);
  }

  public static Schema SCHEMA = new Parser().parse("{\"type\": \"record\",\"name\": \"PaymentCharged\",\"namespace\": \"com.example.events\",\"doc\": \"Event emitted when a payment is successfully charged\",\"fields\": [{\"name\": \"orderId\",\"doc\": \"Unique identifier for the order\",\"type\": {\"type\": \"string\", \"logicalType\": \"uuid\"}},{\"name\": \"paymentId\",\"doc\": \"Payment provider's payment ID\",\"type\": \"string\"},{\"name\": \"transactionId\",\"doc\": \"Payment provider's transaction ID\",\"type\": \"string\"},{\"name\": \"amount\",\"doc\": \"Amount charged in smallest currency unit\",\"type\": \"long\"},{\"name\": \"paidAt\",\"doc\": \"When the payment was charged\",\"type\": {\"type\": \"long\", \"logicalType\": \"timestamp-millis\"}}]}");

  /** Create a record from a GenericRecord (for deserialization) */
  static public PaymentCharged fromGenericRecord(GenericRecord record) {
    return new PaymentCharged(UUID.fromString(record.get("orderId").toString()), record.get("paymentId").toString(), record.get("transactionId").toString(), ((Long) record.get("amount")), Instant.ofEpochMilli(((Long) record.get("paidAt"))));
  }

  /** Convert this record to a GenericRecord for serialization */
  @Override
  public GenericRecord toGenericRecord() {
    Record record = new Record(PaymentCharged.SCHEMA);
    record.put("orderId", this.orderId().toString());
    record.put("paymentId", this.paymentId());
    record.put("transactionId", this.transactionId());
    record.put("amount", this.amount());
    record.put("paidAt", this.paidAt().toEpochMilli());
    return record;
  }
}
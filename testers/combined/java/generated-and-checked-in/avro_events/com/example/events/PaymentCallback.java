package com.example.events;

import java.time.Instant;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Parser;
import org.apache.avro.generic.GenericData.Record;
import org.apache.avro.generic.GenericRecord;

/** Callback received from payment provider when payment status changes */
public record PaymentCallback(
  /** Order ID for correlation */
  String orderId,
  /** Payment provider's payment ID */
  String paymentId,
  /** Payment provider's transaction ID */
  String transactionId,
  /** Amount in smallest currency unit */
  Long amount,
  /** When the payment was charged */
  Instant paidAt
) implements OrderEvents {
  /** Order ID for correlation */
  public PaymentCallback withOrderId(String orderId) {
    return new PaymentCallback(orderId, paymentId, transactionId, amount, paidAt);
  }

  /** Payment provider's payment ID */
  public PaymentCallback withPaymentId(String paymentId) {
    return new PaymentCallback(orderId, paymentId, transactionId, amount, paidAt);
  }

  /** Payment provider's transaction ID */
  public PaymentCallback withTransactionId(String transactionId) {
    return new PaymentCallback(orderId, paymentId, transactionId, amount, paidAt);
  }

  /** Amount in smallest currency unit */
  public PaymentCallback withAmount(Long amount) {
    return new PaymentCallback(orderId, paymentId, transactionId, amount, paidAt);
  }

  /** When the payment was charged */
  public PaymentCallback withPaidAt(Instant paidAt) {
    return new PaymentCallback(orderId, paymentId, transactionId, amount, paidAt);
  }

  public static Schema SCHEMA = new Parser().parse("{\"type\": \"record\",\"name\": \"PaymentCallback\",\"namespace\": \"com.example.events\",\"doc\": \"Callback received from payment provider when payment status changes\",\"fields\": [{\"name\": \"orderId\",\"doc\": \"Order ID for correlation\",\"type\": \"string\"},{\"name\": \"paymentId\",\"doc\": \"Payment provider's payment ID\",\"type\": \"string\"},{\"name\": \"transactionId\",\"doc\": \"Payment provider's transaction ID\",\"type\": \"string\"},{\"name\": \"amount\",\"doc\": \"Amount in smallest currency unit\",\"type\": \"long\"},{\"name\": \"paidAt\",\"doc\": \"When the payment was charged\",\"type\": {\"type\": \"long\", \"logicalType\": \"timestamp-millis\"}}]}");

  /** Create a record from a GenericRecord (for deserialization) */
  static public PaymentCallback fromGenericRecord(GenericRecord record) {
    return new PaymentCallback(record.get("orderId").toString(), record.get("paymentId").toString(), record.get("transactionId").toString(), ((Long) record.get("amount")), Instant.ofEpochMilli(((Long) record.get("paidAt"))));
  }

  /** Convert this record to a GenericRecord for serialization */
  @Override
  public GenericRecord toGenericRecord() {
    Record record = new Record(PaymentCallback.SCHEMA);
    record.put("orderId", this.orderId());
    record.put("paymentId", this.paymentId());
    record.put("transactionId", this.transactionId());
    record.put("amount", this.amount());
    record.put("paidAt", this.paidAt().toEpochMilli());
    return record;
  }
}
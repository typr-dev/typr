package com.example.events;

import com.example.events.common.Money;
import java.time.Instant;
import java.util.UUID;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Parser;
import org.apache.avro.generic.GenericData.Record;
import org.apache.avro.generic.GenericRecord;

/** An invoice with money amount using ref */
public record Invoice(
    /** Unique identifier for the invoice */
    UUID invoiceId,
    /** Customer ID */
    Long customerId,
    /** Total amount with currency */
    Money total,
    /** When the invoice was issued */
    Instant issuedAt) {
  /** Unique identifier for the invoice */
  public Invoice withInvoiceId(UUID invoiceId) {
    return new Invoice(invoiceId, customerId, total, issuedAt);
  }

  /** Customer ID */
  public Invoice withCustomerId(Long customerId) {
    return new Invoice(invoiceId, customerId, total, issuedAt);
  }

  /** Total amount with currency */
  public Invoice withTotal(Money total) {
    return new Invoice(invoiceId, customerId, total, issuedAt);
  }

  /** When the invoice was issued */
  public Invoice withIssuedAt(Instant issuedAt) {
    return new Invoice(invoiceId, customerId, total, issuedAt);
  }

  public static Schema SCHEMA =
      new Parser()
          .parse(
              "{\"type\": \"record\",\"name\": \"Invoice\",\"namespace\":"
                  + " \"com.example.events\",\"doc\": \"An invoice with money amount using"
                  + " ref\",\"fields\": [{\"name\": \"invoiceId\",\"doc\": \"Unique identifier for"
                  + " the invoice\",\"type\": {\"type\": \"string\", \"logicalType\":"
                  + " \"uuid\"}},{\"name\": \"customerId\",\"doc\": \"Customer ID\",\"type\":"
                  + " \"long\"},{\"name\": \"total\",\"doc\": \"Total amount with"
                  + " currency\",\"type\": {\"type\": \"record\", \"name\": \"Money\","
                  + " \"namespace\": \"com.example.events.common\",\"doc\": \"Represents a monetary"
                  + " amount with currency\",\"fields\": [{\"name\": \"amount\",\"doc\": \"The"
                  + " monetary amount\",\"type\": {\"type\": \"bytes\", \"logicalType\":"
                  + " \"decimal\", \"precision\": 18, \"scale\": 4}},{\"name\":"
                  + " \"currency\",\"doc\": \"Currency code (ISO 4217)\",\"type\":"
                  + " \"string\"}]}},{\"name\": \"issuedAt\",\"doc\": \"When the invoice was"
                  + " issued\",\"type\": {\"type\": \"long\", \"logicalType\":"
                  + " \"timestamp-millis\"}}]}");

  /** Create a record from a GenericRecord (for deserialization) */
  public static Invoice fromGenericRecord(GenericRecord record) {
    return new Invoice(
        UUID.fromString(record.get("invoiceId").toString()),
        ((Long) record.get("customerId")),
        Money.fromGenericRecord(((GenericRecord) record.get("total"))),
        Instant.ofEpochMilli(((Long) record.get("issuedAt"))));
  }

  /** Convert this record to a GenericRecord for serialization */
  public GenericRecord toGenericRecord() {
    Record record = new Record(Invoice.SCHEMA);
    record.put("invoiceId", this.invoiceId().toString());
    record.put("customerId", this.customerId());
    record.put("total", this.total().toGenericRecord());
    record.put("issuedAt", this.issuedAt().toEpochMilli());
    return record;
  }
}

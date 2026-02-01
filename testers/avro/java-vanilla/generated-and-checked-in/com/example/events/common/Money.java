package com.example.events.common;

import com.example.events.precisetypes.Decimal18_4;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Parser;
import org.apache.avro.generic.GenericData.Record;
import org.apache.avro.generic.GenericRecord;

/** Represents a monetary amount with currency */
public record Money(
    /** The monetary amount */
    Decimal18_4 amount,
    /** Currency code (ISO 4217) */
    String currency) {
  /** The monetary amount */
  public Money withAmount(Decimal18_4 amount) {
    return new Money(amount, currency);
  }

  /** Currency code (ISO 4217) */
  public Money withCurrency(String currency) {
    return new Money(amount, currency);
  }

  public static Schema SCHEMA =
      new Parser()
          .parse(
              "{\"type\": \"record\",\"name\": \"Money\",\"namespace\":"
                  + " \"com.example.events.common\",\"doc\": \"Represents a monetary amount with"
                  + " currency\",\"fields\": [{\"name\": \"amount\",\"doc\": \"The monetary"
                  + " amount\",\"type\": {\"type\": \"bytes\", \"logicalType\": \"decimal\","
                  + " \"precision\": 18, \"scale\": 4}},{\"name\": \"currency\",\"doc\": \"Currency"
                  + " code (ISO 4217)\",\"type\": \"string\"}]}");

  /** Create a record from a GenericRecord (for deserialization) */
  public static Money fromGenericRecord(GenericRecord record) {
    return new Money(
        Decimal18_4.unsafeForce(
            new BigDecimal(new BigInteger(((ByteBuffer) record.get("amount")).array()), 4)),
        record.get("currency").toString());
  }

  /** Convert this record to a GenericRecord for serialization */
  public GenericRecord toGenericRecord() {
    Record record = new Record(Money.SCHEMA);
    record.put(
        "amount",
        ByteBuffer.wrap(
            this.amount()
                .decimalValue()
                .setScale(4, RoundingMode.HALF_UP)
                .unscaledValue()
                .toByteArray()));
    record.put("currency", this.currency());
    return record;
  }
}

package com.example.events;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Parser;
import org.apache.avro.generic.GenericData.Record;
import org.apache.avro.generic.GenericRecord;

/** A physical address */
public record Address(
    /** Street address */
    String street,
    /** City name */
    String city,
    /** Postal/ZIP code */
    String postalCode,
    /** Country code (ISO 3166-1 alpha-2) */
    String country) {
  /** Street address */
  public Address withStreet(String street) {
    return new Address(street, city, postalCode, country);
  }

  /** City name */
  public Address withCity(String city) {
    return new Address(street, city, postalCode, country);
  }

  /** Postal/ZIP code */
  public Address withPostalCode(String postalCode) {
    return new Address(street, city, postalCode, country);
  }

  /** Country code (ISO 3166-1 alpha-2) */
  public Address withCountry(String country) {
    return new Address(street, city, postalCode, country);
  }

  public static Schema SCHEMA =
      new Parser()
          .parse(
              "{\"type\": \"record\",\"name\": \"Address\",\"namespace\":"
                  + " \"com.example.events\",\"doc\": \"A physical address\",\"fields\":"
                  + " [{\"name\": \"street\",\"doc\": \"Street address\",\"type\":"
                  + " \"string\"},{\"name\": \"city\",\"doc\": \"City name\",\"type\":"
                  + " \"string\"},{\"name\": \"postalCode\",\"doc\": \"Postal/ZIP code\",\"type\":"
                  + " \"string\"},{\"name\": \"country\",\"doc\": \"Country code (ISO 3166-1"
                  + " alpha-2)\",\"type\": \"string\"}]}");

  /** Create a record from a GenericRecord (for deserialization) */
  public static Address fromGenericRecord(GenericRecord record) {
    return new Address(
        record.get("street").toString(),
        record.get("city").toString(),
        record.get("postalCode").toString(),
        record.get("country").toString());
  }

  /** Convert this record to a GenericRecord for serialization */
  public GenericRecord toGenericRecord() {
    Record record = new Record(Address.SCHEMA);
    record.put("street", this.street());
    record.put("city", this.city());
    record.put("postalCode", this.postalCode());
    record.put("country", this.country());
    return record;
  }
}

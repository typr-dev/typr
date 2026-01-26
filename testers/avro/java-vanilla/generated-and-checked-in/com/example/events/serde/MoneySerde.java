package com.example.events.serde;

import com.example.events.common.Money;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

/** Serde for Money */
public class MoneySerde implements Serde<Money>, Serializer<Money>, Deserializer<Money> {
  DatumWriter<GenericRecord> writer = new GenericDatumWriter(Money.SCHEMA);

  DatumReader<GenericRecord> reader = new GenericDatumReader(Money.SCHEMA);

  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {}

  @Override
  public byte[] serialize(String topic, Money data) {
    if (data == null) {
      return null;
    }
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
      writer.write(data.toGenericRecord(), encoder);
      encoder.flush();
      return out.toByteArray();
    } catch (IOException e) {
      throw new SerializationException("Error serializing Avro message", e);
    }
  }

  @Override
  public Money deserialize(String topic, byte[] data) {
    if (data == null) {
      return null;
    }
    try {
      BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(data, null);
      GenericRecord record = reader.read(null, decoder);
      return Money.fromGenericRecord(record);
    } catch (IOException e) {
      throw new SerializationException("Error deserializing Avro message", e);
    }
  }

  @Override
  public void close() {}

  @Override
  public Serializer<Money> serializer() {
    return this;
  }

  @Override
  public Deserializer<Money> deserializer() {
    return this;
  }
}

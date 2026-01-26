package com.example.events.serde;

import com.example.events.LinkedListNode;
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

/** Serde for LinkedListNode */
public class LinkedListNodeSerde
    implements Serde<LinkedListNode>, Serializer<LinkedListNode>, Deserializer<LinkedListNode> {
  DatumWriter<GenericRecord> writer = new GenericDatumWriter(LinkedListNode.SCHEMA);

  DatumReader<GenericRecord> reader = new GenericDatumReader(LinkedListNode.SCHEMA);

  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {}

  @Override
  public byte[] serialize(String topic, LinkedListNode data) {
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
  public LinkedListNode deserialize(String topic, byte[] data) {
    if (data == null) {
      return null;
    }
    try {
      BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(data, null);
      GenericRecord record = reader.read(null, decoder);
      return LinkedListNode.fromGenericRecord(record);
    } catch (IOException e) {
      throw new SerializationException("Error deserializing Avro message", e);
    }
  }

  @Override
  public void close() {}

  @Override
  public Serializer<LinkedListNode> serializer() {
    return this;
  }

  @Override
  public Deserializer<LinkedListNode> deserializer() {
    return this;
  }
}

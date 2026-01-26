package com.example.events.serde;

import com.example.events.OrderPlaced;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.util.Map;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

/** Serde for OrderPlaced */
public class OrderPlacedSerde
    implements Serde<OrderPlaced>, Serializer<OrderPlaced>, Deserializer<OrderPlaced> {
  KafkaAvroSerializer innerSerializer = new KafkaAvroSerializer();

  KafkaAvroDeserializer innerDeserializer = new KafkaAvroDeserializer();

  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {
    innerSerializer.configure(configs, isKey);
    innerDeserializer.configure(configs, isKey);
  }

  @Override
  public byte[] serialize(String topic, OrderPlaced data) {
    if (data == null) {
      return null;
    }
    return innerSerializer.serialize(topic, data.toGenericRecord());
  }

  @Override
  public OrderPlaced deserialize(String topic, byte[] data) {
    if (data == null) {
      return null;
    }
    GenericRecord record = ((GenericRecord) innerDeserializer.deserialize(topic, data));
    return OrderPlaced.fromGenericRecord(record);
  }

  @Override
  public void close() {
    innerSerializer.close();
    innerDeserializer.close();
  }

  @Override
  public Serializer<OrderPlaced> serializer() {
    return this;
  }

  @Override
  public Deserializer<OrderPlaced> deserializer() {
    return this;
  }
}

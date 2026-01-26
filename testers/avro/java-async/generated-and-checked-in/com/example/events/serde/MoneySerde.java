package com.example.events.serde;

import com.example.events.common.Money;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.util.Map;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

/** Serde for Money */
public class MoneySerde implements Serde<Money>, Serializer<Money>, Deserializer<Money> {
  KafkaAvroSerializer innerSerializer = new KafkaAvroSerializer();

  KafkaAvroDeserializer innerDeserializer = new KafkaAvroDeserializer();

  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {
    innerSerializer.configure(configs, isKey);
    innerDeserializer.configure(configs, isKey);
  }

  @Override
  public byte[] serialize(String topic, Money data) {
    if (data == null) {
      return null;
    }
    return innerSerializer.serialize(topic, data.toGenericRecord());
  }

  @Override
  public Money deserialize(String topic, byte[] data) {
    if (data == null) {
      return null;
    }
    GenericRecord record = ((GenericRecord) innerDeserializer.deserialize(topic, data));
    return Money.fromGenericRecord(record);
  }

  @Override
  public void close() {
    innerSerializer.close();
    innerDeserializer.close();
  }

  @Override
  public Serializer<Money> serializer() {
    return this;
  }

  @Override
  public Deserializer<Money> deserializer() {
    return this;
  }
}

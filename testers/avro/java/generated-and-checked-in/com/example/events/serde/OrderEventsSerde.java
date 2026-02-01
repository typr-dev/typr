package com.example.events.serde;

import com.example.events.OrderCancelled;
import com.example.events.OrderEvents;
import com.example.events.OrderPlaced;
import com.example.events.OrderUpdated;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import java.util.Map;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

/** Serde for OrderEvents (sealed type with multiple event variants) */
public class OrderEventsSerde
    implements Serde<OrderEvents>, Serializer<OrderEvents>, Deserializer<OrderEvents> {
  KafkaAvroDeserializer inner = new KafkaAvroDeserializer();

  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {
    inner.configure(configs, isKey);
  }

  @Override
  public byte[] serialize(String topic, OrderEvents data) {
    if (data == null) {
      return null;
    }
    return switch (data) {
      case OrderCancelled e -> new OrderCancelledSerde().serialize(topic, e);
      case OrderPlaced e -> new OrderPlacedSerde().serialize(topic, e);
      case OrderUpdated e -> new OrderUpdatedSerde().serialize(topic, e);
    };
  }

  @Override
  public OrderEvents deserialize(String topic, byte[] data) {
    if (data == null) {
      return null;
    }
    GenericRecord record = ((GenericRecord) inner.deserialize(topic, data));
    return OrderEvents.fromGenericRecord(record);
  }

  @Override
  public void close() {
    inner.close();
  }

  @Override
  public Serializer<OrderEvents> serializer() {
    return this;
  }

  @Override
  public Deserializer<OrderEvents> deserializer() {
    return this;
  }
}

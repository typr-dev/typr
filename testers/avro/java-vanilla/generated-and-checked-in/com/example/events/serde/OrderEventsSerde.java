package com.example.events.serde;

import com.example.events.OrderCancelled;
import com.example.events.OrderEvents;
import com.example.events.OrderPlaced;
import com.example.events.OrderUpdated;
import java.util.HashMap;
import java.util.Map;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

/** Serde for OrderEvents (sealed type with multiple event variants, vanilla Avro) */
public class OrderEventsSerde
    implements Serde<OrderEvents>, Serializer<OrderEvents>, Deserializer<OrderEvents> {
  Map<String, DatumReader<GenericRecord>> readers = new HashMap();

  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {}

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

  /**
   * Deserialize by trying each member schema. For production use, consider adding a schema header.
   */
  @Override
  public OrderEvents deserialize(String topic, byte[] data) {
    if (data == null) {
      return null;
    }
    try {
      try {
        OrderCancelled result = new OrderCancelledSerde().deserialize(topic, data);
        if (result != null) {
          return result;
        }
      } catch (Exception ignored) {

      }
      try {
        OrderPlaced result = new OrderPlacedSerde().deserialize(topic, data);
        if (result != null) {
          return result;
        }
      } catch (Exception ignored) {

      }
      try {
        OrderUpdated result = new OrderUpdatedSerde().deserialize(topic, data);
        if (result != null) {
          return result;
        }
      } catch (Exception ignored) {

      }
      throw new SerializationException("Could not deserialize to any known event type");
    } catch (Exception e) {
      throw new SerializationException("Error deserializing Avro message", e);
    }
  }

  @Override
  public void close() {}

  @Override
  public Serializer<OrderEvents> serializer() {
    return this;
  }

  @Override
  public Deserializer<OrderEvents> deserializer() {
    return this;
  }
}

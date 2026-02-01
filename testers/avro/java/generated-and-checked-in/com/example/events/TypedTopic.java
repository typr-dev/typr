package com.example.events;

import org.apache.kafka.common.serialization.Serde;

/** A typed topic with key and value serdes */
public record TypedTopic<K, V>(String name, Serde<K> keySerde, Serde<V> valueSerde) {
  public TypedTopic<K, V> withName(String name) {
    return new TypedTopic<>(name, keySerde, valueSerde);
  }

  public TypedTopic<K, V> withKeySerde(Serde<K> keySerde) {
    return new TypedTopic<>(name, keySerde, valueSerde);
  }

  public TypedTopic<K, V> withValueSerde(Serde<V> valueSerde) {
    return new TypedTopic<>(name, keySerde, valueSerde);
  }
}

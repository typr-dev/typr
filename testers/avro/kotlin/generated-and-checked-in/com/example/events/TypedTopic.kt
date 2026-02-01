package com.example.events

import org.apache.kafka.common.serialization.Serde

/** A typed topic with key and value serdes */
data class TypedTopic<K, V>(
  val name: kotlin.String,
  val keySerde: Serde<K>,
  val valueSerde: Serde<V>
)
package com.example.events

import org.apache.kafka.common.serialization.Serde

/** A typed topic with key and value serdes */
case class TypedTopic[K, V](
  name: String,
  keySerde: Serde[K],
  valueSerde: Serde[V]
)
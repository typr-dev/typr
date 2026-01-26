package com.example.events.header

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.header.internals.RecordHeaders

/** Typed headers for Kafka messages */
case class StandardHeaders(
  correlationId: UUID,
  timestamp: Instant,
  source: Option[String]
) {
  /** Convert to Kafka Headers */
  def toHeaders: Headers = {
    val headers: Headers = new RecordHeaders()
    headers.add("correlationId", correlationId.toString().getBytes(StandardCharsets.UTF_8))
    headers.add("timestamp", java.lang.Long.toString(timestamp.toEpochMilli).getBytes(StandardCharsets.UTF_8))
    source.foreach(v => headers.add("source", v.getBytes(StandardCharsets.UTF_8)))
    return headers
  }
}

object StandardHeaders {
  /** Parse from Kafka Headers */
  def fromHeaders(headers: Headers): StandardHeaders = {
    val correlationId: UUID = UUID.fromString(new String(headers.lastHeader("correlationId").value(), StandardCharsets.UTF_8))
    val timestamp: Instant = Instant.ofEpochMilli(java.lang.Long.parseLong(new String(headers.lastHeader("timestamp").value(), StandardCharsets.UTF_8)))
    val source: Option[String] = Option.apply(headers.lastHeader("source")).map(h => new String(h.value(), StandardCharsets.UTF_8))
    return new StandardHeaders(correlationId, timestamp, source)
  }
}
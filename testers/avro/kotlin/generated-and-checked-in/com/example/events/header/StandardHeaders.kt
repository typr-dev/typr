package com.example.events.header

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.header.internals.RecordHeaders

/** Typed headers for Kafka messages */
data class StandardHeaders(
  val correlationId: UUID,
  val timestamp: Instant,
  val source: kotlin.String?
) {
  /** Convert to Kafka Headers */
  fun toHeaders(): Headers {
    val headers: Headers = RecordHeaders()
    headers.add("correlationId", correlationId.toString().toByteArray(StandardCharsets.UTF_8))
    headers.add("timestamp", timestamp.toEpochMilli().toString().toByteArray(StandardCharsets.UTF_8))
    source?.let { headers.add("source", it.toByteArray(StandardCharsets.UTF_8)) }
    return headers
  }

  companion object {
    /** Parse from Kafka Headers */
    fun fromHeaders(headers: Headers): StandardHeaders {
      val correlationId: UUID = UUID.fromString(String(headers.lastHeader("correlationId").value(), StandardCharsets.UTF_8))
      val timestamp: Instant = Instant.ofEpochMilli(String(headers.lastHeader("timestamp").value(), StandardCharsets.UTF_8).toLong())
      val source: kotlin.String? = headers.lastHeader("source")?.let { String(it.value(), StandardCharsets.UTF_8) }
      return StandardHeaders(correlationId, timestamp, source)
    }
  }
}
package com.example.events.header;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;

/** Typed headers for Kafka messages */
public record StandardHeaders(UUID correlationId, Instant timestamp, Optional<String> source) {
  public StandardHeaders withCorrelationId(UUID correlationId) {
    return new StandardHeaders(correlationId, timestamp, source);
  }

  public StandardHeaders withTimestamp(Instant timestamp) {
    return new StandardHeaders(correlationId, timestamp, source);
  }

  public StandardHeaders withSource(Optional<String> source) {
    return new StandardHeaders(correlationId, timestamp, source);
  }

  /** Parse from Kafka Headers */
  public static StandardHeaders fromHeaders(Headers headers) {
    UUID correlationId =
        UUID.fromString(
            new String(headers.lastHeader("correlationId").value(), StandardCharsets.UTF_8));
    Instant timestamp =
        Instant.ofEpochMilli(
            Long.parseLong(
                new String(headers.lastHeader("timestamp").value(), StandardCharsets.UTF_8)));
    Optional<String> source =
        Optional.ofNullable(headers.lastHeader("source"))
            .map(h -> new String(h.value(), StandardCharsets.UTF_8));
    return new StandardHeaders(correlationId, timestamp, source);
  }

  /** Convert to Kafka Headers */
  public Headers toHeaders() {
    Headers headers = new RecordHeaders();
    headers.add("correlationId", correlationId.toString().getBytes(StandardCharsets.UTF_8));
    headers.add(
        "timestamp", Long.toString(timestamp.toEpochMilli()).getBytes(StandardCharsets.UTF_8));
    source.ifPresent(v -> headers.add("source", v.getBytes(StandardCharsets.UTF_8)));
    return headers;
  }
}

package combined.avro_events.consumer;

import com.example.events.DynamicValue;
import combined.avro_events.header.StandardHeaders;
import java.lang.Void;
import java.util.concurrent.CompletableFuture;

/** Handler interface for dynamic-value topic events */
public interface DynamicValueHandler {
  /** Handle a message from the topic */
  CompletableFuture<Void> handle(
    String key,
    DynamicValue value,
    StandardHeaders headers
  );
}
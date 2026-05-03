package combined.avro_events.consumer;

import com.example.events.common.Money;
import combined.avro_events.header.StandardHeaders;
import java.lang.Void;
import java.util.concurrent.CompletableFuture;

/** Handler interface for money topic events */
public interface MoneyHandler {
  /** Handle a message from the topic */
  CompletableFuture<Void> handle(
    String key,
    Money value,
    StandardHeaders headers
  );
}
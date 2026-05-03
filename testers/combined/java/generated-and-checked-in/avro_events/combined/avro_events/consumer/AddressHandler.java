package combined.avro_events.consumer;

import com.example.events.Address;
import combined.avro_events.header.StandardHeaders;
import java.lang.Void;
import java.util.concurrent.CompletableFuture;

/** Handler interface for address topic events */
public interface AddressHandler {
  /** Handle a message from the topic */
  CompletableFuture<Void> handle(
    String key,
    Address value,
    StandardHeaders headers
  );
}
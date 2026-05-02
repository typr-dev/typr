package combined.avro_events.consumer;

import com.example.events.LinkedListNode;
import combined.avro_events.header.StandardHeaders;
import java.lang.Void;
import java.util.concurrent.CompletableFuture;

/** Handler interface for linked-list-node topic events */
public interface LinkedListNodeHandler {
  /** Handle a message from the topic */
  CompletableFuture<Void> handle(
    String key,
    LinkedListNode value,
    StandardHeaders headers
  );
}
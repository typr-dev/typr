package combined.avro_events.consumer;

import com.example.events.Invoice;
import combined.avro_events.header.StandardHeaders;
import java.lang.Void;
import java.util.concurrent.CompletableFuture;

/** Handler interface for invoice topic events */
public interface InvoiceHandler {
  /** Handle a message from the topic */
  CompletableFuture<Void> handle(
    String key,
    Invoice value,
    StandardHeaders headers
  );
}
package combined.avro_events.consumer;

import com.example.events.OrderCancelled;
import com.example.events.OrderEvents;
import com.example.events.OrderPlaced;
import com.example.events.OrderUpdated;
import com.example.events.PaymentCallback;
import com.example.events.PaymentCharged;
import combined.avro_events.header.StandardHeaders;
import java.lang.IllegalStateException;
import java.lang.Void;
import java.util.concurrent.CompletableFuture;

/** Handler interface for order-events topic events */
public interface OrderEventsHandler {
  /** Handle a OrderCancelled event */
  CompletableFuture<Void> handleOrderCancelled(
    String key,
    OrderCancelled event,
    StandardHeaders headers
  );

  /** Handle a OrderPlaced event */
  CompletableFuture<Void> handleOrderPlaced(
    String key,
    OrderPlaced event,
    StandardHeaders headers
  );

  /** Handle a OrderUpdated event */
  CompletableFuture<Void> handleOrderUpdated(
    String key,
    OrderUpdated event,
    StandardHeaders headers
  );

  /** Handle a PaymentCallback event */
  CompletableFuture<Void> handlePaymentCallback(
    String key,
    PaymentCallback event,
    StandardHeaders headers
  );

  /** Handle a PaymentCharged event */
  CompletableFuture<Void> handlePaymentCharged(
    String key,
    PaymentCharged event,
    StandardHeaders headers
  );

  /** Handle unknown event types (default throws exception) */
  default CompletableFuture<Void> handleUnknown(
    String key,
    OrderEvents event,
    StandardHeaders headers
  ) {
    throw new IllegalStateException("Unknown event type: " + event.getClass());
  }
}
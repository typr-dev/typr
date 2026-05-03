package combined.avro_events.consumer

import com.example.events.CustomerOrder
import combined.avro_events.header.StandardHeaders
import io.smallrye.mutiny.Uni

/** Handler interface for customer-order topic events */
interface CustomerOrderHandler {
  /** Handle a message from the topic */
  abstract fun handle(
    key: kotlin.String,
    value: CustomerOrder,
    headers: StandardHeaders
  ): Uni<Unit>
}
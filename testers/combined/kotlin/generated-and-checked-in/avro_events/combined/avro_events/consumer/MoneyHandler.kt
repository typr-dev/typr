package combined.avro_events.consumer

import com.example.events.common.Money
import combined.avro_events.header.StandardHeaders
import io.smallrye.mutiny.Uni

/** Handler interface for money topic events */
interface MoneyHandler {
  /** Handle a message from the topic */
  abstract fun handle(
    key: kotlin.String,
    value: Money,
    headers: StandardHeaders
  ): Uni<Unit>
}
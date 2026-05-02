package combined.avro_events.consumer

import com.example.events.DynamicValue
import combined.avro_events.header.StandardHeaders
import io.smallrye.mutiny.Uni

/** Handler interface for dynamic-value topic events */
interface DynamicValueHandler {
  /** Handle a message from the topic */
  abstract fun handle(
    key: kotlin.String,
    value: DynamicValue,
    headers: StandardHeaders
  ): Uni<Unit>
}
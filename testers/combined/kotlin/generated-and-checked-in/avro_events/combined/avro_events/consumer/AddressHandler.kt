package combined.avro_events.consumer

import com.example.events.Address
import combined.avro_events.header.StandardHeaders
import io.smallrye.mutiny.Uni

/** Handler interface for address topic events */
interface AddressHandler {
  /** Handle a message from the topic */
  abstract fun handle(
    key: kotlin.String,
    value: Address,
    headers: StandardHeaders
  ): Uni<Unit>
}
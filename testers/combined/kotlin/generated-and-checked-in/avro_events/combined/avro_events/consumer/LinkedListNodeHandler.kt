package combined.avro_events.consumer

import com.example.events.LinkedListNode
import combined.avro_events.header.StandardHeaders
import io.smallrye.mutiny.Uni

/** Handler interface for linked-list-node topic events */
interface LinkedListNodeHandler {
  /** Handle a message from the topic */
  abstract fun handle(
    key: kotlin.String,
    value: LinkedListNode,
    headers: StandardHeaders
  ): Uni<Unit>
}
package combined.avro_events.consumer

import com.example.events.Invoice
import combined.avro_events.header.StandardHeaders
import io.smallrye.mutiny.Uni

/** Handler interface for invoice topic events */
interface InvoiceHandler {
  /** Handle a message from the topic */
  abstract fun handle(
    key: kotlin.String,
    value: Invoice,
    headers: StandardHeaders
  ): Uni<Unit>
}
package combined.avro_events.consumer

import com.example.events.CustomerOrder
import combined.avro_events.header.StandardHeaders
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.Uni
import java.io.Closeable
import java.time.Duration
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecords

/** Type-safe consumer for customer-order topic */
data class CustomerOrderConsumer(
  val consumer: Consumer<kotlin.String, CustomerOrder>,
  val handler: CustomerOrderHandler,
  val topic: kotlin.String = "customer-order"
) : Closeable {
  /** Close the consumer */
  override fun close() {
    consumer.close()
  }

  /** Poll for messages and dispatch to handler, returning composed effect */
  fun poll(timeout: Duration): Uni<Unit> {
    val records: ConsumerRecords<kotlin.String, CustomerOrder> = consumer.poll(timeout)
    return Multi.createFrom().iterable(records).onItem().transformToUniAndConcatenate({ record -> handler.handle(record.key(), record.value(), StandardHeaders.fromHeaders(record.headers())) }).collect().last()
  }
}
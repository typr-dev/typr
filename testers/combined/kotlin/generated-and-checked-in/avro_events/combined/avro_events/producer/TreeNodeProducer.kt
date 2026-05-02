package combined.avro_events.producer

import com.example.events.TreeNode
import combined.avro_events.header.StandardHeaders
import io.smallrye.mutiny.Uni
import java.io.Closeable
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata

/** Type-safe producer for tree-node topic */
data class TreeNodeProducer(
  val producer: Producer<kotlin.String, TreeNode>,
  val topic: kotlin.String = "tree-node"
) : Closeable {
  /** Close the producer */
  override fun close() {
    producer.close()
  }

  /** Send a message to the topic asynchronously */
  fun send(
    key: kotlin.String,
    value: TreeNode
  ): Uni<RecordMetadata> {
    return Uni.createFrom().emitter({ em -> producer.send(ProducerRecord<kotlin.String, TreeNode>(topic, key, value), { result, exception -> if (exception != null) {
      em.fail(exception)
    } else {
      em.complete(result)
    } }) })
  }

  /** Send a message with headers to the topic asynchronously */
  fun send(
    key: kotlin.String,
    value: TreeNode,
    headers: StandardHeaders
  ): Uni<RecordMetadata> {
    return Uni.createFrom().emitter({ em -> producer.send(ProducerRecord<kotlin.String, TreeNode>(topic, null, key, value, headers.toHeaders()), { result, exception -> if (exception != null) {
      em.fail(exception)
    } else {
      em.complete(result)
    } }) })
  }
}
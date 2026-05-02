package combined.avro_events.serde

import com.example.events.OrderCancelled
import com.example.events.OrderEvents
import com.example.events.OrderPlaced
import com.example.events.OrderUpdated
import com.example.events.PaymentCallback
import com.example.events.PaymentCharged
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import java.lang.IllegalStateException
import kotlin.collections.MutableMap
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serializer

/** Serde for OrderEvents (sealed type with multiple event variants) */
class OrderEventsSerde() : Serde<OrderEvents>, Serializer<OrderEvents>, Deserializer<OrderEvents> {
  override fun close() {
    inner.close()
  }

  override fun configure(
    configs: MutableMap<kotlin.String, *>,
    isKey: kotlin.Boolean
  ) {
    inner.configure(configs, isKey)
  }

  override fun deserialize(
    topic: kotlin.String,
    data: ByteArray?
  ): OrderEvents? {
    if (data == null) {
      return null
    }
    val record: GenericRecord = (inner.deserialize(topic, data) as GenericRecord)
    return OrderEvents.fromGenericRecord(record)
  }

  override fun deserializer(): Deserializer<OrderEvents> = this

  val inner: KafkaAvroDeserializer = KafkaAvroDeserializer()

  override fun serialize(
    topic: kotlin.String,
    data: OrderEvents?
  ): ByteArray? {
    if (data == null) {
      return null
    }
    return when (val __r = data) {
      is OrderCancelled -> { val e = __r; OrderCancelledSerde().serialize(topic, e) }
      is OrderPlaced -> { val e = __r; OrderPlacedSerde().serialize(topic, e) }
      is OrderUpdated -> { val e = __r; OrderUpdatedSerde().serialize(topic, e) }
      is PaymentCallback -> { val e = __r; PaymentCallbackSerde().serialize(topic, e) }
      is PaymentCharged -> { val e = __r; PaymentChargedSerde().serialize(topic, e) }
      else -> throw IllegalStateException("Unexpected type")
    }
  }

  override fun serializer(): Serializer<OrderEvents> = this
}
package combined.avro_events.serde;

import com.example.events.PaymentCharged;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.util.Map;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

/** Serde for PaymentCharged */
public class PaymentChargedSerde implements Serde<PaymentCharged>, Serializer<PaymentCharged>, Deserializer<PaymentCharged> {
  KafkaAvroSerializer innerSerializer = new KafkaAvroSerializer();

  KafkaAvroDeserializer innerDeserializer = new KafkaAvroDeserializer();

  @Override
  public void configure(
    Map<String, ?> configs,
    boolean isKey
  ) {
    innerSerializer.configure(configs, isKey);
    innerDeserializer.configure(configs, isKey);
  }

  @Override
  public byte[] serialize(
    String topic,
    PaymentCharged data
  ) {
    if (data == null) {
      return null;
    }
    return innerSerializer.serialize(topic, data.toGenericRecord());
  }

  @Override
  public PaymentCharged deserialize(
    String topic,
    byte[] data
  ) {
    if (data == null) {
      return null;
    }
    GenericRecord record = ((GenericRecord) innerDeserializer.deserialize(topic, data));
    return PaymentCharged.fromGenericRecord(record);
  }

  @Override
  public void close() {
    innerSerializer.close();
    innerDeserializer.close();
  }

  @Override
  public Serializer<PaymentCharged> serializer() {
    return this;
  }

  @Override
  public Deserializer<PaymentCharged> deserializer() {
    return this;
  }
}
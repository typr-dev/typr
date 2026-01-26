package com.example.events.serde;

import com.example.events.CustomerOrder;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.util.Map;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

/** Serde for CustomerOrder */
public class CustomerOrderSerde
    implements Serde<CustomerOrder>, Serializer<CustomerOrder>, Deserializer<CustomerOrder> {
  KafkaAvroSerializer innerSerializer = new KafkaAvroSerializer();

  KafkaAvroDeserializer innerDeserializer = new KafkaAvroDeserializer();

  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {
    innerSerializer.configure(configs, isKey);
    innerDeserializer.configure(configs, isKey);
  }

  @Override
  public byte[] serialize(String topic, CustomerOrder data) {
    if (data == null) {
      return null;
    }
    return innerSerializer.serialize(topic, data.toGenericRecord());
  }

  @Override
  public CustomerOrder deserialize(String topic, byte[] data) {
    if (data == null) {
      return null;
    }
    GenericRecord record = ((GenericRecord) innerDeserializer.deserialize(topic, data));
    return CustomerOrder.fromGenericRecord(record);
  }

  @Override
  public void close() {
    innerSerializer.close();
    innerDeserializer.close();
  }

  @Override
  public Serializer<CustomerOrder> serializer() {
    return this;
  }

  @Override
  public Deserializer<CustomerOrder> deserializer() {
    return this;
  }
}

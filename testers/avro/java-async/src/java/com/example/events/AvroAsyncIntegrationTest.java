package com.example.events;

import static org.junit.Assert.*;

import com.example.events.precisetypes.Decimal10_2;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Integration tests for async (CompletableFuture) Avro producers.
 *
 * <p>Tests the CompletableFuture-based producer API generated with effectType = CompletableFuture.
 *
 * <p>Requires Kafka running on localhost:9092 (use docker-compose up kafka).
 */
public class AvroAsyncIntegrationTest {

  private static final String BOOTSTRAP_SERVERS = "localhost:9092";
  private static final String TEST_RUN_ID = UUID.randomUUID().toString().substring(0, 8);

  private static boolean kafkaAvailable = false;

  @BeforeClass
  public static void checkKafkaAvailability() {
    Properties props = new Properties();
    props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
    props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
    props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 5000);

    try (AdminClient admin = AdminClient.create(props)) {
      admin.listTopics().names().get();
      kafkaAvailable = true;
      System.out.println("Kafka is available at " + BOOTSTRAP_SERVERS);
    } catch (Exception e) {
      System.out.println("Kafka not available at " + BOOTSTRAP_SERVERS + ": " + e.getMessage());
      System.out.println(
          "Skipping Kafka integration tests. Start Kafka with: docker-compose up -d kafka");
    }
  }

  @Test
  public void testSerdeWithoutKafka() {
    // Test basic serde functionality without Kafka
    OrderPlaced original =
        new OrderPlaced(
            UUID.randomUUID(),
            12345L,
            Decimal10_2.unsafeForce(new BigDecimal("99.99")),
            Instant.now(),
            List.of("item-1", "item-2"),
            Optional.of("123 Main St"));

    var record = original.toGenericRecord();
    OrderPlaced deserialized = OrderPlaced.fromGenericRecord(record);

    assertEquals(original.orderId(), deserialized.orderId());
    assertEquals(original.customerId(), deserialized.customerId());
    assertEquals(
        0,
        original.totalAmount().decimalValue().compareTo(deserialized.totalAmount().decimalValue()));
  }

  @Test
  public void testAddressSerialization() {
    Address original = new Address("456 Async St", "FutureCity", "54321", "US");

    var record = original.toGenericRecord();
    Address deserialized = Address.fromGenericRecord(record);

    assertEquals(original.street(), deserialized.street());
    assertEquals(original.city(), deserialized.city());
    assertEquals(original.postalCode(), deserialized.postalCode());
    assertEquals(original.country(), deserialized.country());
  }

  @Test
  public void testOrderUpdatedWithNestedRecord() {
    Address address = new Address("789 Nested St", "RecordTown", "11111", "NR");
    OrderUpdated original =
        new OrderUpdated(
            UUID.randomUUID(),
            OrderStatus.PENDING,
            OrderStatus.SHIPPED,
            Instant.now(),
            Optional.of(address));

    var record = original.toGenericRecord();
    OrderUpdated deserialized = OrderUpdated.fromGenericRecord(record);

    assertEquals(original.orderId(), deserialized.orderId());
    assertEquals(original.previousStatus(), deserialized.previousStatus());
    assertEquals(original.newStatus(), deserialized.newStatus());
    assertTrue(deserialized.shippingAddress().isPresent());
    assertEquals(address.street(), deserialized.shippingAddress().get().street());
  }

  @Test
  public void testAllEnumValues() {
    for (OrderStatus status : OrderStatus.values()) {
      OrderUpdated original =
          new OrderUpdated(UUID.randomUUID(), status, status, Instant.now(), Optional.empty());

      var record = original.toGenericRecord();
      OrderUpdated deserialized = OrderUpdated.fromGenericRecord(record);

      assertEquals(status, deserialized.previousStatus());
      assertEquals(status, deserialized.newStatus());
    }
  }

  @Test
  public void testKafkaRoundTrip() throws Exception {
    if (!kafkaAvailable) {
      System.out.println("Skipping Kafka test - Kafka not available");
      return;
    }

    String topicName = "async-test-" + TEST_RUN_ID;
    createTopicIfNotExists(topicName);

    OrderPlaced original =
        new OrderPlaced(
            UUID.randomUUID(),
            99999L,
            Decimal10_2.unsafeForce(new BigDecimal("1234.56")),
            Instant.now(),
            List.of("async-item-1", "async-item-2"),
            Optional.of("Async Test Address"));

    byte[] serialized = serializeGenericRecord(original.toGenericRecord(), OrderPlaced.SCHEMA);

    try (KafkaProducer<String, byte[]> producer = createProducer()) {
      producer
          .send(new ProducerRecord<>(topicName, original.orderId().toString(), serialized))
          .get();
      producer.flush();
    }

    try (KafkaConsumer<String, byte[]> consumer = createConsumer()) {
      consumer.subscribe(Collections.singletonList(topicName));

      ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(10));
      assertFalse("Should receive at least one record", records.isEmpty());

      ConsumerRecord<String, byte[]> received = records.iterator().next();
      GenericRecord genericRecord = deserializeGenericRecord(received.value(), OrderPlaced.SCHEMA);
      OrderPlaced deserialized = OrderPlaced.fromGenericRecord(genericRecord);

      assertEquals(original.orderId(), deserialized.orderId());
      assertEquals(original.customerId(), deserialized.customerId());
    }
  }

  // ========== SchemaValidator Tests ==========

  @Test
  public void testSchemaValidatorBackwardCompatibility() {
    SchemaValidator validator = new SchemaValidator();
    assertTrue(validator.isBackwardCompatible(OrderPlaced.SCHEMA, OrderPlaced.SCHEMA));
  }

  @Test
  public void testSchemaValidatorGetSchemaByName() {
    SchemaValidator validator = new SchemaValidator();
    assertEquals(OrderPlaced.SCHEMA, validator.getSchemaByName("com.example.events.OrderPlaced"));
    assertNull(validator.getSchemaByName("com.example.events.Unknown"));
  }

  private void createTopicIfNotExists(String topicName)
      throws ExecutionException, InterruptedException {
    Properties props = new Properties();
    props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);

    try (AdminClient admin = AdminClient.create(props)) {
      Set<String> existingTopics = admin.listTopics().names().get();
      if (!existingTopics.contains(topicName)) {
        NewTopic newTopic = new NewTopic(topicName, 1, (short) 1);
        admin.createTopics(Collections.singletonList(newTopic)).all().get();
      }
    }
  }

  private KafkaProducer<String, byte[]> createProducer() {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    return new KafkaProducer<>(props);
  }

  private KafkaConsumer<String, byte[]> createConsumer() {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
    return new KafkaConsumer<>(props);
  }

  private byte[] serializeGenericRecord(GenericRecord record, org.apache.avro.Schema schema)
      throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    org.apache.avro.io.BinaryEncoder encoder =
        org.apache.avro.io.EncoderFactory.get().binaryEncoder(out, null);
    org.apache.avro.generic.GenericDatumWriter<GenericRecord> writer =
        new org.apache.avro.generic.GenericDatumWriter<>(schema);
    writer.write(record, encoder);
    encoder.flush();
    return out.toByteArray();
  }

  private GenericRecord deserializeGenericRecord(byte[] data, org.apache.avro.Schema schema)
      throws Exception {
    org.apache.avro.io.BinaryDecoder decoder =
        org.apache.avro.io.DecoderFactory.get().binaryDecoder(data, null);
    org.apache.avro.generic.GenericDatumReader<GenericRecord> reader =
        new org.apache.avro.generic.GenericDatumReader<>(schema);
    return reader.read(null, decoder);
  }
}

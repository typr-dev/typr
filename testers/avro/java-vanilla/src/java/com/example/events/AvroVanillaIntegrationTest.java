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
 * Integration tests for VanillaAvro serialization (without Schema Registry).
 *
 * <p>These tests validate that Avro serialization works correctly using pure Avro binary encoding
 * without requiring Confluent Schema Registry.
 *
 * <p>Requires Kafka running on localhost:9092 (use docker-compose up kafka).
 */
public class AvroVanillaIntegrationTest {

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
  public void testBinarySerializationRoundTrip() throws Exception {
    // Test that we can serialize and deserialize using pure Avro binary encoding
    OrderPlaced original =
        new OrderPlaced(
            UUID.randomUUID(),
            12345L,
            Decimal10_2.unsafeForce(new BigDecimal("99.99")),
            Instant.now(),
            List.of("item-1", "item-2", "item-3"),
            Optional.of("123 Main St"));

    // Serialize using Avro binary encoding (no Schema Registry)
    byte[] serialized = serializeGenericRecord(original.toGenericRecord(), OrderPlaced.SCHEMA);
    assertNotNull("Serialized bytes should not be null", serialized);
    assertTrue("Serialized bytes should not be empty", serialized.length > 0);

    // Deserialize using Avro binary encoding
    GenericRecord genericRecord = deserializeGenericRecord(serialized, OrderPlaced.SCHEMA);
    OrderPlaced deserialized = OrderPlaced.fromGenericRecord(genericRecord);

    assertEquals(original.orderId(), deserialized.orderId());
    assertEquals(original.customerId(), deserialized.customerId());
    assertEquals(
        0,
        original.totalAmount().decimalValue().compareTo(deserialized.totalAmount().decimalValue()));
    assertEquals(original.items(), deserialized.items());
    assertEquals(original.shippingAddress(), deserialized.shippingAddress());
  }

  @Test
  public void testAddressBinarySerialization() throws Exception {
    Address original = new Address("789 Binary Ave", "SerializationCity", "54321", "AV");

    byte[] serialized = serializeGenericRecord(original.toGenericRecord(), Address.SCHEMA);
    GenericRecord genericRecord = deserializeGenericRecord(serialized, Address.SCHEMA);
    Address deserialized = Address.fromGenericRecord(genericRecord);

    assertEquals(original.street(), deserialized.street());
    assertEquals(original.city(), deserialized.city());
    assertEquals(original.postalCode(), deserialized.postalCode());
    assertEquals(original.country(), deserialized.country());
  }

  @Test
  public void testOrderUpdatedWithNestedRecordBinarySerialization() throws Exception {
    Address address = new Address("456 Nested St", "RecordTown", "11111", "NR");
    OrderUpdated original =
        new OrderUpdated(
            UUID.randomUUID(),
            OrderStatus.PENDING,
            OrderStatus.SHIPPED,
            Instant.now(),
            Optional.of(address));

    byte[] serialized = serializeGenericRecord(original.toGenericRecord(), OrderUpdated.SCHEMA);
    GenericRecord genericRecord = deserializeGenericRecord(serialized, OrderUpdated.SCHEMA);
    OrderUpdated deserialized = OrderUpdated.fromGenericRecord(genericRecord);

    assertEquals(original.orderId(), deserialized.orderId());
    assertEquals(original.previousStatus(), deserialized.previousStatus());
    assertEquals(original.newStatus(), deserialized.newStatus());
    assertTrue(deserialized.shippingAddress().isPresent());
    assertEquals(address.street(), deserialized.shippingAddress().get().street());
  }

  @Test
  public void testEnumBinarySerialization() throws Exception {
    // Test all enum values serialize correctly
    for (OrderStatus status : OrderStatus.values()) {
      OrderUpdated original =
          new OrderUpdated(UUID.randomUUID(), status, status, Instant.now(), Optional.empty());

      byte[] serialized = serializeGenericRecord(original.toGenericRecord(), OrderUpdated.SCHEMA);
      GenericRecord genericRecord = deserializeGenericRecord(serialized, OrderUpdated.SCHEMA);
      OrderUpdated deserialized = OrderUpdated.fromGenericRecord(genericRecord);

      assertEquals(status, deserialized.previousStatus());
      assertEquals(status, deserialized.newStatus());
    }
  }

  @Test
  public void testKafkaRoundTripWithVanillaAvro() throws Exception {
    if (!kafkaAvailable) {
      System.out.println("Skipping Kafka test - Kafka not available");
      return;
    }

    String topicName = "vanilla-avro-test-" + TEST_RUN_ID;
    createTopicIfNotExists(topicName);

    OrderPlaced original =
        new OrderPlaced(
            UUID.randomUUID(),
            88888L,
            Decimal10_2.unsafeForce(new BigDecimal("555.55")),
            Instant.now(),
            List.of("vanilla-item-1"),
            Optional.of("Vanilla Test Address"));

    // Serialize without Schema Registry - pure binary
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
      // Deserialize without Schema Registry - pure binary
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
    assertTrue(validator.isBackwardCompatible(Address.SCHEMA, Address.SCHEMA));
  }

  @Test
  public void testSchemaValidatorGetSchemaByName() {
    SchemaValidator validator = new SchemaValidator();
    assertEquals(OrderPlaced.SCHEMA, validator.getSchemaByName("com.example.events.OrderPlaced"));
    assertEquals(Address.SCHEMA, validator.getSchemaByName("com.example.events.Address"));
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

package com.example.events;

import static org.junit.Assert.*;

import com.example.events.common.Money;
import com.example.events.precisetypes.Decimal10_2;
import com.example.events.precisetypes.Decimal18_4;
import com.example.service.*;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.HashMap;
import java.util.Map;
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
 * Integration tests for Avro serialization/deserialization through Kafka.
 *
 * <p>These tests are idempotent - they use unique topic names and random consumer group IDs so they
 * can be safely re-run on the same Kafka instance.
 *
 * <p>Requires Kafka running on localhost:9092 (use docker-compose up kafka).
 */
public class AvroKafkaIntegrationTest {

  private static final String BOOTSTRAP_SERVERS = "localhost:9092";
  private static final String SCHEMA_REGISTRY_URL = "http://localhost:8081";
  private static final String TEST_RUN_ID = UUID.randomUUID().toString().substring(0, 8);

  private static boolean kafkaAvailable = false;
  private static boolean schemaRegistryAvailable = false;

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

    // Check Schema Registry availability
    try {
      java.net.HttpURLConnection conn =
          (java.net.HttpURLConnection)
              new java.net.URL(SCHEMA_REGISTRY_URL + "/subjects").openConnection();
      conn.setConnectTimeout(5000);
      conn.setReadTimeout(5000);
      conn.setRequestMethod("GET");
      if (conn.getResponseCode() == 200) {
        schemaRegistryAvailable = true;
        System.out.println("Schema Registry is available at " + SCHEMA_REGISTRY_URL);
      }
      conn.disconnect();
    } catch (Exception e) {
      System.out.println(
          "Schema Registry not available at " + SCHEMA_REGISTRY_URL + ": " + e.getMessage());
      System.out.println(
          "Skipping Schema Registry tests. Start with: docker-compose up -d schema-registry");
    }
  }

  @Test
  public void testOrderPlacedSerdeWithoutKafka() {
    OrderPlaced original =
        new OrderPlaced(
            UUID.randomUUID(),
            12345L,
            Decimal10_2.unsafeForce(new BigDecimal("99.99")),
            Instant.now(),
            List.of("item-1", "item-2", "item-3"),
            Optional.of("123 Main St"));

    GenericRecord record = original.toGenericRecord();
    OrderPlaced deserialized = OrderPlaced.fromGenericRecord(record);

    assertEquals(original.orderId(), deserialized.orderId());
    assertEquals(original.customerId(), deserialized.customerId());
    assertEquals(original.totalAmount(), deserialized.totalAmount());
    assertEquals(original.placedAt().toEpochMilli(), deserialized.placedAt().toEpochMilli());
    assertEquals(original.items(), deserialized.items());
    assertEquals(original.shippingAddress(), deserialized.shippingAddress());
  }

  @Test
  public void testOrderPlacedWithNullOptionalField() {
    OrderPlaced original =
        new OrderPlaced(
            UUID.randomUUID(),
            12345L,
            Decimal10_2.unsafeForce(new BigDecimal("50.00")),
            Instant.now(),
            List.of("item-a"),
            Optional.empty());

    GenericRecord record = original.toGenericRecord();
    OrderPlaced deserialized = OrderPlaced.fromGenericRecord(record);

    assertEquals(original.orderId(), deserialized.orderId());
    assertTrue(deserialized.shippingAddress().isEmpty());
  }

  @Test
  public void testOrderUpdatedWithNestedRecord() {
    Address address = new Address("456 Oak Ave", "Springfield", "12345", "US");
    OrderUpdated original =
        new OrderUpdated(
            UUID.randomUUID(),
            OrderStatus.PENDING,
            OrderStatus.SHIPPED,
            Instant.now(),
            Optional.of(address));

    GenericRecord record = original.toGenericRecord();
    OrderUpdated deserialized = OrderUpdated.fromGenericRecord(record);

    assertEquals(original.orderId(), deserialized.orderId());
    assertEquals(original.previousStatus(), deserialized.previousStatus());
    assertEquals(original.newStatus(), deserialized.newStatus());
    assertEquals(original.updatedAt().toEpochMilli(), deserialized.updatedAt().toEpochMilli());
    assertTrue(deserialized.shippingAddress().isPresent());

    Address deserializedAddr = deserialized.shippingAddress().get();
    assertEquals(address.street(), deserializedAddr.street());
    assertEquals(address.city(), deserializedAddr.city());
    assertEquals(address.postalCode(), deserializedAddr.postalCode());
    assertEquals(address.country(), deserializedAddr.country());
  }

  @Test
  public void testOrderUpdatedWithNullNestedRecord() {
    OrderUpdated original =
        new OrderUpdated(
            UUID.randomUUID(),
            OrderStatus.CONFIRMED,
            OrderStatus.CANCELLED,
            Instant.now(),
            Optional.empty());

    GenericRecord record = original.toGenericRecord();
    OrderUpdated deserialized = OrderUpdated.fromGenericRecord(record);

    assertEquals(original.previousStatus(), deserialized.previousStatus());
    assertEquals(original.newStatus(), deserialized.newStatus());
    assertTrue(deserialized.shippingAddress().isEmpty());
  }

  @Test
  public void testAllEnumValues() {
    for (OrderStatus status : OrderStatus.values()) {
      OrderUpdated original =
          new OrderUpdated(UUID.randomUUID(), status, status, Instant.now(), Optional.empty());

      GenericRecord record = original.toGenericRecord();
      OrderUpdated deserialized = OrderUpdated.fromGenericRecord(record);

      assertEquals(status, deserialized.previousStatus());
      assertEquals(status, deserialized.newStatus());
    }
  }

  @Test
  public void testKafkaRoundTripOrderPlaced() throws Exception {
    if (!kafkaAvailable) {
      System.out.println("Skipping Kafka test - Kafka not available");
      return;
    }

    String topicName = "order-placed-test-" + TEST_RUN_ID;
    createTopicIfNotExists(topicName);

    OrderPlaced original =
        new OrderPlaced(
            UUID.randomUUID(),
            99999L,
            Decimal10_2.unsafeForce(new BigDecimal("1234.56")),
            Instant.now(),
            List.of("kafka-item-1", "kafka-item-2"),
            Optional.of("Kafka Test Address"));

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
      assertEquals(original.totalAmount(), deserialized.totalAmount());
      assertEquals(original.items(), deserialized.items());
      assertEquals(original.shippingAddress(), deserialized.shippingAddress());
    }
  }

  @Test
  public void testKafkaRoundTripOrderUpdated() throws Exception {
    if (!kafkaAvailable) {
      System.out.println("Skipping Kafka test - Kafka not available");
      return;
    }

    String topicName = "order-updated-test-" + TEST_RUN_ID;
    createTopicIfNotExists(topicName);

    Address address = new Address("789 Kafka St", "MessageCity", "54321", "KF");
    OrderUpdated original =
        new OrderUpdated(
            UUID.randomUUID(),
            OrderStatus.PENDING,
            OrderStatus.DELIVERED,
            Instant.now(),
            Optional.of(address));

    byte[] serialized = serializeGenericRecord(original.toGenericRecord(), OrderUpdated.SCHEMA);

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
      GenericRecord genericRecord = deserializeGenericRecord(received.value(), OrderUpdated.SCHEMA);
      OrderUpdated deserialized = OrderUpdated.fromGenericRecord(genericRecord);

      assertEquals(original.orderId(), deserialized.orderId());
      assertEquals(original.previousStatus(), deserialized.previousStatus());
      assertEquals(original.newStatus(), deserialized.newStatus());
      assertTrue(deserialized.shippingAddress().isPresent());
      assertEquals(address.street(), deserialized.shippingAddress().get().street());
    }
  }

  @Test
  public void testKafkaMultipleMessages() throws Exception {
    if (!kafkaAvailable) {
      System.out.println("Skipping Kafka test - Kafka not available");
      return;
    }

    String topicName = "order-batch-test-" + TEST_RUN_ID;
    createTopicIfNotExists(topicName);

    List<OrderPlaced> originals = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      originals.add(
          new OrderPlaced(
              UUID.randomUUID(),
              (long) i,
              Decimal10_2.unsafeForce(new BigDecimal(i + ".99")),
              Instant.now(),
              List.of("batch-item-" + i),
              i % 2 == 0 ? Optional.of("Address " + i) : Optional.empty()));
    }

    try (KafkaProducer<String, byte[]> producer = createProducer()) {
      for (OrderPlaced order : originals) {
        byte[] serialized = serializeGenericRecord(order.toGenericRecord(), OrderPlaced.SCHEMA);
        producer
            .send(new ProducerRecord<>(topicName, order.orderId().toString(), serialized))
            .get();
      }
      producer.flush();
    }

    Map<UUID, OrderPlaced> receivedOrders = new HashMap<>();
    try (KafkaConsumer<String, byte[]> consumer = createConsumer()) {
      consumer.subscribe(Collections.singletonList(topicName));

      int attempts = 0;
      while (receivedOrders.size() < originals.size() && attempts < 10) {
        ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(2));
        for (ConsumerRecord<String, byte[]> record : records) {
          GenericRecord genericRecord =
              deserializeGenericRecord(record.value(), OrderPlaced.SCHEMA);
          OrderPlaced deserialized = OrderPlaced.fromGenericRecord(genericRecord);
          receivedOrders.put(deserialized.orderId(), deserialized);
        }
        attempts++;
      }
    }

    assertEquals("Should receive all messages", originals.size(), receivedOrders.size());

    for (OrderPlaced original : originals) {
      OrderPlaced received = receivedOrders.get(original.orderId());
      assertNotNull("Should find order " + original.orderId(), received);
      assertEquals(original.customerId(), received.customerId());
      assertEquals(original.shippingAddress(), received.shippingAddress());
    }
  }

  // ========== SchemaValidator Tests ==========

  @Test
  public void testSchemaValidatorBackwardCompatibility() {
    SchemaValidator validator = new SchemaValidator();

    // Same schema should be backward compatible with itself
    assertTrue(validator.isBackwardCompatible(OrderPlaced.SCHEMA, OrderPlaced.SCHEMA));
    assertTrue(validator.isBackwardCompatible(OrderUpdated.SCHEMA, OrderUpdated.SCHEMA));
    assertTrue(validator.isBackwardCompatible(Address.SCHEMA, Address.SCHEMA));
  }

  @Test
  public void testSchemaValidatorForwardCompatibility() {
    SchemaValidator validator = new SchemaValidator();

    // Same schema should be forward compatible with itself
    assertTrue(validator.isForwardCompatible(OrderPlaced.SCHEMA, OrderPlaced.SCHEMA));
    assertTrue(validator.isForwardCompatible(OrderUpdated.SCHEMA, OrderUpdated.SCHEMA));
  }

  @Test
  public void testSchemaValidatorFullCompatibility() {
    SchemaValidator validator = new SchemaValidator();

    // Same schema should be fully compatible with itself
    assertTrue(validator.isFullyCompatible(OrderPlaced.SCHEMA, OrderPlaced.SCHEMA));
    assertTrue(validator.isFullyCompatible(OrderUpdated.SCHEMA, OrderUpdated.SCHEMA));
    assertTrue(validator.isFullyCompatible(Address.SCHEMA, Address.SCHEMA));
  }

  @Test
  public void testSchemaValidatorCheckCompatibility() {
    SchemaValidator validator = new SchemaValidator();

    var result = validator.checkCompatibility(OrderPlaced.SCHEMA, OrderPlaced.SCHEMA);
    assertNotNull(result);
    assertEquals(
        org.apache.avro.SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE, result.getType());
  }

  @Test
  public void testSchemaValidatorGetMissingFields() {
    SchemaValidator validator = new SchemaValidator();

    // Same schema should have no missing fields
    var missingFields = validator.getMissingFields(OrderPlaced.SCHEMA, OrderPlaced.SCHEMA);
    assertNotNull(missingFields);
    assertTrue("Same schema should have no missing fields", missingFields.isEmpty());
  }

  @Test
  public void testSchemaValidatorGetSchemaByName() {
    SchemaValidator validator = new SchemaValidator();

    // Should find known schemas
    assertEquals(OrderPlaced.SCHEMA, validator.getSchemaByName("com.example.events.OrderPlaced"));
    assertEquals(OrderUpdated.SCHEMA, validator.getSchemaByName("com.example.events.OrderUpdated"));
    assertEquals(
        OrderCancelled.SCHEMA, validator.getSchemaByName("com.example.events.OrderCancelled"));
    assertEquals(Address.SCHEMA, validator.getSchemaByName("com.example.events.Address"));

    // Should return null for unknown schemas
    assertNull(validator.getSchemaByName("com.example.events.Unknown"));
    assertNull(validator.getSchemaByName(""));
  }

  @Test
  public void testSchemaValidatorValidateRequiredFields() {
    SchemaValidator validator = new SchemaValidator();

    // Should validate required fields (currently returns true)
    assertTrue(validator.validateRequiredFields(OrderPlaced.SCHEMA));
    assertTrue(validator.validateRequiredFields(Address.SCHEMA));
  }

  // ========== Complex Union Types Tests (Feature 3) ==========

  @Test
  public void testComplexUnionTypeStringOrIntOrBoolean() {
    // Test creating union values with different types
    StringOrIntOrBoolean stringValue = StringOrIntOrBoolean.of("hello");
    StringOrIntOrBoolean intValue = StringOrIntOrBoolean.of(42);
    StringOrIntOrBoolean boolValue = StringOrIntOrBoolean.of(true);

    // Test isXxx methods
    assertTrue(stringValue.isString());
    assertFalse(stringValue.isInt());
    assertFalse(stringValue.isBoolean());

    assertTrue(intValue.isInt());
    assertFalse(intValue.isString());
    assertFalse(intValue.isBoolean());

    assertTrue(boolValue.isBoolean());
    assertFalse(boolValue.isString());
    assertFalse(boolValue.isInt());

    // Test asXxx methods
    assertEquals("hello", stringValue.asString());
    assertEquals(Integer.valueOf(42), intValue.asInt());
    assertEquals(Boolean.TRUE, boolValue.asBoolean());
  }

  @Test
  public void testComplexUnionTypeThrowsOnWrongType() {
    StringOrIntOrBoolean stringValue = StringOrIntOrBoolean.of("hello");

    try {
      stringValue.asInt();
      fail("Expected UnsupportedOperationException");
    } catch (UnsupportedOperationException e) {
      // Expected
    }

    try {
      stringValue.asBoolean();
      fail("Expected UnsupportedOperationException");
    } catch (UnsupportedOperationException e) {
      // Expected
    }
  }

  @Test
  public void testDynamicValueWithComplexUnions() {
    // Test with string value
    DynamicValue withString =
        new DynamicValue("id-1", StringOrIntOrBoolean.of("test-string"), Optional.empty());

    GenericRecord record1 = withString.toGenericRecord();
    DynamicValue deserialized1 = DynamicValue.fromGenericRecord(record1);

    assertEquals("id-1", deserialized1.id());
    assertTrue(deserialized1.value().isString());
    assertEquals("test-string", deserialized1.value().asString());
    assertTrue(deserialized1.optionalValue().isEmpty());

    // Test with int value
    DynamicValue withInt =
        new DynamicValue("id-2", StringOrIntOrBoolean.of(123), Optional.of(StringOrLong.of(456L)));

    GenericRecord record2 = withInt.toGenericRecord();
    DynamicValue deserialized2 = DynamicValue.fromGenericRecord(record2);

    assertEquals("id-2", deserialized2.id());
    assertTrue(deserialized2.value().isInt());
    assertEquals(Integer.valueOf(123), deserialized2.value().asInt());
    assertTrue(deserialized2.optionalValue().isPresent());
    assertTrue(deserialized2.optionalValue().get().isLong());
    assertEquals(Long.valueOf(456L), deserialized2.optionalValue().get().asLong());

    // Test with boolean value and optional string
    DynamicValue withBool =
        new DynamicValue(
            "id-3", StringOrIntOrBoolean.of(false), Optional.of(StringOrLong.of("optional-str")));

    GenericRecord record3 = withBool.toGenericRecord();
    DynamicValue deserialized3 = DynamicValue.fromGenericRecord(record3);

    assertEquals("id-3", deserialized3.id());
    assertTrue(deserialized3.value().isBoolean());
    assertEquals(Boolean.FALSE, deserialized3.value().asBoolean());
    assertTrue(deserialized3.optionalValue().isPresent());
    assertTrue(deserialized3.optionalValue().get().isString());
    assertEquals("optional-str", deserialized3.optionalValue().get().asString());
  }

  @Test
  public void testDynamicValueKafkaRoundTrip() throws Exception {
    if (!kafkaAvailable) {
      System.out.println("Skipping Kafka test - Kafka not available");
      return;
    }

    String topicName = "dynamic-value-test-" + TEST_RUN_ID;
    createTopicIfNotExists(topicName);

    DynamicValue original =
        new DynamicValue(
            "kafka-id", StringOrIntOrBoolean.of(999), Optional.of(StringOrLong.of("kafka-string")));

    byte[] serialized = serializeGenericRecord(original.toGenericRecord(), DynamicValue.SCHEMA);

    try (KafkaProducer<String, byte[]> producer = createProducer()) {
      producer.send(new ProducerRecord<>(topicName, original.id(), serialized)).get();
      producer.flush();
    }

    try (KafkaConsumer<String, byte[]> consumer = createConsumer()) {
      consumer.subscribe(Collections.singletonList(topicName));

      ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(10));
      assertFalse("Should receive at least one record", records.isEmpty());

      ConsumerRecord<String, byte[]> received = records.iterator().next();
      GenericRecord genericRecord = deserializeGenericRecord(received.value(), DynamicValue.SCHEMA);
      DynamicValue deserialized = DynamicValue.fromGenericRecord(genericRecord);

      assertEquals(original.id(), deserialized.id());
      assertTrue(deserialized.value().isInt());
      assertEquals(Integer.valueOf(999), deserialized.value().asInt());
      assertTrue(deserialized.optionalValue().isPresent());
      assertEquals("kafka-string", deserialized.optionalValue().get().asString());
    }
  }

  // ========== Avro $ref Support Tests (Feature 5) ==========

  @Test
  public void testInvoiceWithMoneyRef() {
    Money total = new Money(Decimal18_4.unsafeForce(new BigDecimal("1234.5678")), "USD");
    Invoice original = new Invoice(UUID.randomUUID(), 12345L, total, Instant.now());

    GenericRecord record = original.toGenericRecord();
    Invoice deserialized = Invoice.fromGenericRecord(record);

    assertEquals(original.invoiceId(), deserialized.invoiceId());
    assertEquals(original.customerId(), deserialized.customerId());
    assertEquals(original.total().amount(), deserialized.total().amount());
    assertEquals(original.total().currency(), deserialized.total().currency());
    assertEquals(original.issuedAt().toEpochMilli(), deserialized.issuedAt().toEpochMilli());
  }

  @Test
  public void testMoneyStandalone() {
    Money original = new Money(Decimal18_4.unsafeForce(new BigDecimal("99999.9999")), "EUR");

    GenericRecord record = original.toGenericRecord();
    Money deserialized = Money.fromGenericRecord(record);

    assertEquals(original.amount(), deserialized.amount());
    assertEquals(original.currency(), deserialized.currency());
  }

  @Test
  public void testInvoiceKafkaRoundTrip() throws Exception {
    if (!kafkaAvailable) {
      System.out.println("Skipping Kafka test - Kafka not available");
      return;
    }

    String topicName = "invoice-test-" + TEST_RUN_ID;
    createTopicIfNotExists(topicName);

    Money total = new Money(Decimal18_4.unsafeForce(new BigDecimal("5000.00")), "GBP");
    Invoice original = new Invoice(UUID.randomUUID(), 67890L, total, Instant.now());

    byte[] serialized = serializeGenericRecord(original.toGenericRecord(), Invoice.SCHEMA);

    try (KafkaProducer<String, byte[]> producer = createProducer()) {
      producer
          .send(new ProducerRecord<>(topicName, original.invoiceId().toString(), serialized))
          .get();
      producer.flush();
    }

    try (KafkaConsumer<String, byte[]> consumer = createConsumer()) {
      consumer.subscribe(Collections.singletonList(topicName));

      ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(10));
      assertFalse("Should receive at least one record", records.isEmpty());

      ConsumerRecord<String, byte[]> received = records.iterator().next();
      GenericRecord genericRecord = deserializeGenericRecord(received.value(), Invoice.SCHEMA);
      Invoice deserialized = Invoice.fromGenericRecord(genericRecord);

      assertEquals(original.invoiceId(), deserialized.invoiceId());
      assertEquals(original.customerId(), deserialized.customerId());
      assertEquals(original.total().amount(), deserialized.total().amount());
      assertEquals("GBP", deserialized.total().currency());
    }
  }

  // ========== Topics/TypedTopic Tests (Feature 1 - Key Schemas) ==========

  @Test
  public void testTopicsConstantsExist() {
    // Verify that all topic bindings are defined
    assertNotNull(Topics.ADDRESS);
    assertNotNull(Topics.DYNAMIC_VALUE);
    assertNotNull(Topics.INVOICE);
    assertNotNull(Topics.MONEY);
    assertNotNull(Topics.ORDER_CANCELLED);
    assertNotNull(Topics.ORDER_EVENTS);
    assertNotNull(Topics.ORDER_PLACED);
    assertNotNull(Topics.ORDER_UPDATED);
  }

  @Test
  public void testTypedTopicProperties() {
    // Verify topic names
    assertEquals("address", Topics.ADDRESS.name());
    assertEquals("dynamic-value", Topics.DYNAMIC_VALUE.name());
    assertEquals("invoice", Topics.INVOICE.name());
    assertEquals("order-events", Topics.ORDER_EVENTS.name());

    // Verify serdes are not null
    assertNotNull(Topics.ADDRESS.keySerde());
    assertNotNull(Topics.ADDRESS.valueSerde());
    assertNotNull(Topics.DYNAMIC_VALUE.keySerde());
    assertNotNull(Topics.DYNAMIC_VALUE.valueSerde());
    assertNotNull(Topics.INVOICE.keySerde());
    assertNotNull(Topics.INVOICE.valueSerde());
  }

  @Test
  public void testTypedTopicSerdeRoundTrip() {
    if (!schemaRegistryAvailable) {
      System.out.println("Skipping Schema Registry test - Schema Registry not available");
      return;
    }

    // Configure the serde with Schema Registry
    Map<String, Object> config = new HashMap<>();
    config.put("schema.registry.url", SCHEMA_REGISTRY_URL);

    var serializer = Topics.ADDRESS.valueSerde().serializer();
    var deserializer = Topics.ADDRESS.valueSerde().deserializer();
    serializer.configure(config, false);
    deserializer.configure(config, false);

    Address original = new Address("123 Test St", "TestCity", "12345", "US");

    String topicName = "serde-test-address-" + TEST_RUN_ID;
    byte[] serialized = serializer.serialize(topicName, original);
    Address deserialized = deserializer.deserialize(topicName, serialized);

    assertEquals(original.street(), deserialized.street());
    assertEquals(original.city(), deserialized.city());
    assertEquals(original.postalCode(), deserialized.postalCode());
    assertEquals(original.country(), deserialized.country());
  }

  @Test
  public void testTypedTopicDynamicValueSerde() {
    if (!schemaRegistryAvailable) {
      System.out.println("Skipping Schema Registry test - Schema Registry not available");
      return;
    }

    // Configure the serde with Schema Registry
    Map<String, Object> config = new HashMap<>();
    config.put("schema.registry.url", SCHEMA_REGISTRY_URL);

    var serializer = Topics.DYNAMIC_VALUE.valueSerde().serializer();
    var deserializer = Topics.DYNAMIC_VALUE.valueSerde().deserializer();
    serializer.configure(config, false);
    deserializer.configure(config, false);

    DynamicValue original =
        new DynamicValue(
            "serde-test", StringOrIntOrBoolean.of("value"), Optional.of(StringOrLong.of(100L)));

    String topicName = "serde-test-dynamic-value-" + TEST_RUN_ID;
    byte[] serialized = serializer.serialize(topicName, original);
    DynamicValue deserialized = deserializer.deserialize(topicName, serialized);

    assertEquals(original.id(), deserialized.id());
    assertTrue(deserialized.value().isString());
    assertEquals("value", deserialized.value().asString());
    assertTrue(deserialized.optionalValue().isPresent());
    assertEquals(Long.valueOf(100L), deserialized.optionalValue().get().asLong());
  }

  @Test
  public void testTypedTopicInvoiceSerde() {
    if (!schemaRegistryAvailable) {
      System.out.println("Skipping Schema Registry test - Schema Registry not available");
      return;
    }

    // Configure the serde with Schema Registry
    Map<String, Object> config = new HashMap<>();
    config.put("schema.registry.url", SCHEMA_REGISTRY_URL);

    var serializer = Topics.INVOICE.valueSerde().serializer();
    var deserializer = Topics.INVOICE.valueSerde().deserializer();
    serializer.configure(config, false);
    deserializer.configure(config, false);

    Money total = new Money(Decimal18_4.unsafeForce(new BigDecimal("250.00")), "CAD");
    Invoice original = new Invoice(UUID.randomUUID(), 11111L, total, Instant.now());

    String topicName = "serde-test-invoice-" + TEST_RUN_ID;
    byte[] serialized = serializer.serialize(topicName, original);
    Invoice deserialized = deserializer.deserialize(topicName, serialized);

    assertEquals(original.invoiceId(), deserialized.invoiceId());
    assertEquals(original.customerId(), deserialized.customerId());
    assertEquals(original.total().amount(), deserialized.total().amount());
    assertEquals(original.total().currency(), deserialized.total().currency());
  }

  // ========== Recursive Types Tests ==========

  @Test
  public void testTreeNodeSimpleRoundTrip() {
    // Test a simple leaf node
    TreeNode leaf = new TreeNode("leaf", Optional.empty(), Optional.empty());

    GenericRecord record = leaf.toGenericRecord();
    TreeNode deserialized = TreeNode.fromGenericRecord(record);

    assertEquals(leaf.value(), deserialized.value());
    assertEquals(leaf.left(), deserialized.left());
    assertEquals(leaf.right(), deserialized.right());
  }

  @Test
  public void testTreeNodeRecursiveRoundTrip() {
    // Test a tree with nested nodes
    TreeNode leftChild = new TreeNode("left-child", Optional.empty(), Optional.empty());
    TreeNode rightChild = new TreeNode("right-child", Optional.empty(), Optional.empty());
    TreeNode root = new TreeNode("root", Optional.of(leftChild), Optional.of(rightChild));

    GenericRecord record = root.toGenericRecord();
    TreeNode deserialized = TreeNode.fromGenericRecord(record);

    assertEquals("root", deserialized.value());
    assertTrue(deserialized.left().isPresent());
    assertTrue(deserialized.right().isPresent());
    assertEquals("left-child", deserialized.left().get().value());
    assertEquals("right-child", deserialized.right().get().value());
    assertFalse(deserialized.left().get().left().isPresent());
    assertFalse(deserialized.right().get().right().isPresent());
  }

  @Test
  public void testTreeNodeDeeplyNested() {
    // Test a deeply nested structure (left-leaning tree)
    TreeNode level3 = new TreeNode("level3", Optional.empty(), Optional.empty());
    TreeNode level2 = new TreeNode("level2", Optional.of(level3), Optional.empty());
    TreeNode level1 = new TreeNode("level1", Optional.of(level2), Optional.empty());
    TreeNode root = new TreeNode("root", Optional.of(level1), Optional.empty());

    GenericRecord record = root.toGenericRecord();
    TreeNode deserialized = TreeNode.fromGenericRecord(record);

    assertEquals("root", deserialized.value());
    assertEquals("level1", deserialized.left().get().value());
    assertEquals("level2", deserialized.left().get().left().get().value());
    assertEquals("level3", deserialized.left().get().left().get().left().get().value());
    assertFalse(deserialized.left().get().left().get().left().get().left().isPresent());
  }

  @Test
  public void testLinkedListNodeSimpleRoundTrip() {
    // Test a single node list
    LinkedListNode single = new LinkedListNode(42, Optional.empty());

    GenericRecord record = single.toGenericRecord();
    LinkedListNode deserialized = LinkedListNode.fromGenericRecord(record);

    assertEquals(42L, (long) deserialized.value());
    assertFalse(deserialized.next().isPresent());
  }

  @Test
  public void testLinkedListNodeChainRoundTrip() {
    // Test a linked list: 1 -> 2 -> 3 -> null
    LinkedListNode node3 = new LinkedListNode(3, Optional.empty());
    LinkedListNode node2 = new LinkedListNode(2, Optional.of(node3));
    LinkedListNode node1 = new LinkedListNode(1, Optional.of(node2));

    GenericRecord record = node1.toGenericRecord();
    LinkedListNode deserialized = LinkedListNode.fromGenericRecord(record);

    assertEquals(1L, (long) deserialized.value());
    assertTrue(deserialized.next().isPresent());
    assertEquals(2L, (long) deserialized.next().get().value());
    assertTrue(deserialized.next().get().next().isPresent());
    assertEquals(3L, (long) deserialized.next().get().next().get().value());
    assertFalse(deserialized.next().get().next().get().next().isPresent());
  }

  @Test
  public void testTreeNodeKafkaRoundTrip() throws Exception {
    if (!kafkaAvailable) {
      System.out.println("Skipping Kafka test - Kafka not available");
      return;
    }

    String topicName = "tree-node-test-" + TEST_RUN_ID;
    createTopicIfNotExists(topicName);

    TreeNode leftChild = new TreeNode("left", Optional.empty(), Optional.empty());
    TreeNode rightChild = new TreeNode("right", Optional.empty(), Optional.empty());
    TreeNode original = new TreeNode("root", Optional.of(leftChild), Optional.of(rightChild));

    byte[] serialized = serializeGenericRecord(original.toGenericRecord(), TreeNode.SCHEMA);

    try (KafkaProducer<String, byte[]> producer = createProducer()) {
      producer.send(new ProducerRecord<>(topicName, "tree-key", serialized)).get();
      producer.flush();
    }

    try (KafkaConsumer<String, byte[]> consumer = createConsumer()) {
      consumer.subscribe(Collections.singletonList(topicName));

      ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(10));
      assertFalse("Should receive at least one record", records.isEmpty());

      ConsumerRecord<String, byte[]> received = records.iterator().next();
      GenericRecord genericRecord = deserializeGenericRecord(received.value(), TreeNode.SCHEMA);
      TreeNode deserialized = TreeNode.fromGenericRecord(genericRecord);

      assertEquals("root", deserialized.value());
      assertEquals("left", deserialized.left().get().value());
      assertEquals("right", deserialized.right().get().value());
    }
  }

  @Test
  public void testLinkedListNodeKafkaRoundTrip() throws Exception {
    if (!kafkaAvailable) {
      System.out.println("Skipping Kafka test - Kafka not available");
      return;
    }

    String topicName = "linked-list-test-" + TEST_RUN_ID;
    createTopicIfNotExists(topicName);

    LinkedListNode node3 = new LinkedListNode(300, Optional.empty());
    LinkedListNode node2 = new LinkedListNode(200, Optional.of(node3));
    LinkedListNode original = new LinkedListNode(100, Optional.of(node2));

    byte[] serialized = serializeGenericRecord(original.toGenericRecord(), LinkedListNode.SCHEMA);

    try (KafkaProducer<String, byte[]> producer = createProducer()) {
      producer.send(new ProducerRecord<>(topicName, "list-key", serialized)).get();
      producer.flush();
    }

    try (KafkaConsumer<String, byte[]> consumer = createConsumer()) {
      consumer.subscribe(Collections.singletonList(topicName));

      ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(10));
      assertFalse("Should receive at least one record", records.isEmpty());

      ConsumerRecord<String, byte[]> received = records.iterator().next();
      GenericRecord genericRecord =
          deserializeGenericRecord(received.value(), LinkedListNode.SCHEMA);
      LinkedListNode deserialized = LinkedListNode.fromGenericRecord(genericRecord);

      assertEquals(100L, (long) deserialized.value());
      assertEquals(200L, (long) deserialized.next().get().value());
      assertEquals(300L, (long) deserialized.next().get().next().get().value());
    }
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

  @Test
  public void testUserServiceImplementationSuccess() {
    Map<String, User> userStore = new HashMap<>();

    UserService service =
        new UserService() {
          @Override
          public Result<User, UserNotFoundError> getUser(String userId) {
            User user = userStore.get(userId);
            if (user == null) {
              return new Result.Err<>(new UserNotFoundError(userId, "User not found: " + userId));
            }
            return new Result.Ok<>(user);
          }

          @Override
          public Result<User, ValidationError> createUser(String email, String name) {
            if (email == null || !email.contains("@")) {
              return new Result.Err<>(new ValidationError("email", "Invalid email format"));
            }
            String userId = UUID.randomUUID().toString();
            User user = new User(userId, email, name, Instant.now());
            userStore.put(userId, user);
            return new Result.Ok<>(user);
          }

          @Override
          public Result<Void, UserNotFoundError> deleteUser(String userId) {
            if (!userStore.containsKey(userId)) {
              return new Result.Err<>(
                  new UserNotFoundError(userId, "Cannot delete: user not found"));
            }
            userStore.remove(userId);
            return new Result.Ok<>(null);
          }

          @Override
          public void notifyUser(String userId, String message) {}
        };

    Result<User, ValidationError> createResult =
        service.createUser("test@example.com", "Test User");
    assertTrue(createResult instanceof Result.Ok);
    Result.Ok<User, ValidationError> ok = (Result.Ok<User, ValidationError>) createResult;
    assertEquals("test@example.com", ok.value().email());
    assertEquals("Test User", ok.value().name());

    String userId = ok.value().id();
    Result<User, UserNotFoundError> getResult = service.getUser(userId);
    assertTrue(getResult instanceof Result.Ok);
    assertEquals(userId, ((Result.Ok<User, UserNotFoundError>) getResult).value().id());

    Result<Void, UserNotFoundError> deleteResult = service.deleteUser(userId);
    assertTrue(deleteResult instanceof Result.Ok);

    Result<User, UserNotFoundError> getAfterDelete = service.getUser(userId);
    assertTrue(getAfterDelete instanceof Result.Err);
    assertEquals(userId, ((Result.Err<User, UserNotFoundError>) getAfterDelete).error().userId());
  }

  @Test
  public void testUserServiceImplementationErrors() {
    UserService service =
        new UserService() {
          @Override
          public Result<User, UserNotFoundError> getUser(String userId) {
            return new Result.Err<>(new UserNotFoundError(userId, "Not found"));
          }

          @Override
          public Result<User, ValidationError> createUser(String email, String name) {
            return new Result.Err<>(new ValidationError("email", "Invalid"));
          }

          @Override
          public Result<Void, UserNotFoundError> deleteUser(String userId) {
            return new Result.Err<>(new UserNotFoundError(userId, "Not found"));
          }

          @Override
          public void notifyUser(String userId, String message) {}
        };

    Result<User, ValidationError> createResult = service.createUser("bad-email", "Test");
    assertTrue(createResult instanceof Result.Err);
    Result.Err<User, ValidationError> err = (Result.Err<User, ValidationError>) createResult;
    assertEquals("email", err.error().field());
    assertEquals("Invalid", err.error().message());

    Result<User, UserNotFoundError> getResult = service.getUser("unknown-id");
    assertTrue(getResult instanceof Result.Err);
    Result.Err<User, UserNotFoundError> getErr = (Result.Err<User, UserNotFoundError>) getResult;
    assertEquals("unknown-id", getErr.error().userId());
  }

  @Test
  public void testUserServicePatternMatching() {
    UserService service =
        new UserService() {
          @Override
          public Result<User, UserNotFoundError> getUser(String userId) {
            if ("existing".equals(userId)) {
              return new Result.Ok<>(
                  new User("existing", "user@example.com", "Existing User", Instant.now()));
            }
            return new Result.Err<>(new UserNotFoundError(userId, "Not found"));
          }

          @Override
          public Result<User, ValidationError> createUser(String email, String name) {
            return new Result.Ok<>(
                new User(UUID.randomUUID().toString(), email, name, Instant.now()));
          }

          @Override
          public Result<Void, UserNotFoundError> deleteUser(String userId) {
            return new Result.Ok<>(null);
          }

          @Override
          public void notifyUser(String userId, String message) {}
        };

    String resultMessage =
        switch (service.getUser("existing")) {
          case Result.Ok(var user) -> "Found: " + ((User) user).name();
          case Result.Err(var error) -> "Error: " + ((UserNotFoundError) error).message();
        };
    assertEquals("Found: Existing User", resultMessage);

    String errorMessage =
        switch (service.getUser("nonexistent")) {
          case Result.Ok(var user) -> "Found: " + ((User) user).name();
          case Result.Err(var error) -> "Error: " + ((UserNotFoundError) error).message();
        };
    assertEquals("Error: Not found", errorMessage);
  }
}

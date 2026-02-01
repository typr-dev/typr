package com.example.events

import com.example.events.common.Money
import com.example.events.precisetypes.{Decimal10_2, Decimal18_4}
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.admin.{AdminClient, AdminClientConfig, NewTopic}
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer, StringDeserializer, StringSerializer}
import org.junit.{BeforeClass, Test}
import org.junit.Assert._

import java.io.ByteArrayOutputStream
import java.time.{Duration, Instant}
import java.util
import java.util.{Collections, Properties, UUID}
import scala.jdk.CollectionConverters._

/** Integration tests for Avro serialization/deserialization through Kafka.
  *
  * These tests are idempotent - they use unique topic names and random consumer group IDs so they can be safely re-run on the same Kafka instance.
  *
  * Requires Kafka running on localhost:9092 (use docker-compose up kafka).
  */
object AvroKafkaIntegrationTest {
  private val BOOTSTRAP_SERVERS = "localhost:9092"
  private val SCHEMA_REGISTRY_URL = "http://localhost:8081"
  val TEST_RUN_ID: String = UUID.randomUUID().toString.substring(0, 8)

  var kafkaAvailable: Boolean = false
  var schemaRegistryAvailable: Boolean = false

  @BeforeClass
  def checkKafkaAvailability(): Unit = {
    val props = new Properties()
    props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS)
    props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000")
    props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "5000")

    try {
      val admin = AdminClient.create(props)
      try {
        admin.listTopics().names().get()
        kafkaAvailable = true
        println(s"Kafka is available at $BOOTSTRAP_SERVERS")
      } finally {
        admin.close()
      }
    } catch {
      case e: Exception =>
        println(s"Kafka not available at $BOOTSTRAP_SERVERS: ${e.getMessage}")
        println("Skipping Kafka integration tests. Start Kafka with: docker-compose up -d kafka")
    }

    // Check Schema Registry availability
    try {
      val conn = new java.net.URL(s"$SCHEMA_REGISTRY_URL/subjects").openConnection().asInstanceOf[java.net.HttpURLConnection]
      conn.setConnectTimeout(5000)
      conn.setReadTimeout(5000)
      conn.setRequestMethod("GET")
      if (conn.getResponseCode == 200) {
        schemaRegistryAvailable = true
        println(s"Schema Registry is available at $SCHEMA_REGISTRY_URL")
      }
      conn.disconnect()
    } catch {
      case e: Exception =>
        println(s"Schema Registry not available at $SCHEMA_REGISTRY_URL: ${e.getMessage}")
        println("Skipping Schema Registry tests. Start with: docker-compose up -d schema-registry")
    }
  }
}

class AvroKafkaIntegrationTest {
  import AvroKafkaIntegrationTest._

  @Test
  def testOrderPlacedSerdeWithoutKafka(): Unit = {
    val original = OrderPlaced(
      orderId = UUID.randomUUID(),
      customerId = 12345L,
      totalAmount = Decimal10_2.unsafeForce(new java.math.BigDecimal("99.99")),
      placedAt = Instant.now(),
      items = List("item-1", "item-2", "item-3"),
      shippingAddress = Some("123 Main St")
    )

    val record = original.toGenericRecord
    val deserialized = OrderPlaced.fromGenericRecord(record)

    assertEquals(original.orderId, deserialized.orderId)
    assertEquals(original.customerId, deserialized.customerId)
    assertEquals(original.totalAmount, deserialized.totalAmount)
    assertEquals(original.placedAt.toEpochMilli, deserialized.placedAt.toEpochMilli)
    assertEquals(original.items, deserialized.items)
    assertEquals(original.shippingAddress, deserialized.shippingAddress)
  }

  @Test
  def testOrderPlacedWithNullOptionalField(): Unit = {
    val original = OrderPlaced(
      orderId = UUID.randomUUID(),
      customerId = 12345L,
      totalAmount = Decimal10_2.unsafeForce(new java.math.BigDecimal("50.00")),
      placedAt = Instant.now(),
      items = List("item-a"),
      shippingAddress = None
    )

    val record = original.toGenericRecord
    val deserialized = OrderPlaced.fromGenericRecord(record)

    assertEquals(original.orderId, deserialized.orderId)
    assertTrue(deserialized.shippingAddress.isEmpty)
  }

  @Test
  def testOrderUpdatedWithNestedRecord(): Unit = {
    val address = Address("456 Oak Ave", "Springfield", "12345", "US")
    val original = OrderUpdated(
      orderId = UUID.randomUUID(),
      previousStatus = OrderStatus.PENDING,
      newStatus = OrderStatus.SHIPPED,
      updatedAt = Instant.now(),
      shippingAddress = Some(address)
    )

    val record = original.toGenericRecord
    val deserialized = OrderUpdated.fromGenericRecord(record)

    assertEquals(original.orderId, deserialized.orderId)
    assertEquals(original.previousStatus, deserialized.previousStatus)
    assertEquals(original.newStatus, deserialized.newStatus)
    assertEquals(original.updatedAt.toEpochMilli, deserialized.updatedAt.toEpochMilli)
    assertTrue(deserialized.shippingAddress.isDefined)

    val deserializedAddr = deserialized.shippingAddress.get
    assertEquals(address.street, deserializedAddr.street)
    assertEquals(address.city, deserializedAddr.city)
    assertEquals(address.postalCode, deserializedAddr.postalCode)
    assertEquals(address.country, deserializedAddr.country)
  }

  @Test
  def testOrderUpdatedWithNullNestedRecord(): Unit = {
    val original = OrderUpdated(
      orderId = UUID.randomUUID(),
      previousStatus = OrderStatus.CONFIRMED,
      newStatus = OrderStatus.CANCELLED,
      updatedAt = Instant.now(),
      shippingAddress = None
    )

    val record = original.toGenericRecord
    val deserialized = OrderUpdated.fromGenericRecord(record)

    assertEquals(original.previousStatus, deserialized.previousStatus)
    assertEquals(original.newStatus, deserialized.newStatus)
    assertTrue(deserialized.shippingAddress.isEmpty)
  }

  @Test
  def testAllEnumValues(): Unit = {
    for (status <- OrderStatus.All) {
      val original = OrderUpdated(
        orderId = UUID.randomUUID(),
        previousStatus = status,
        newStatus = status,
        updatedAt = Instant.now(),
        shippingAddress = None
      )

      val record = original.toGenericRecord
      val deserialized = OrderUpdated.fromGenericRecord(record)

      assertEquals(status, deserialized.previousStatus)
      assertEquals(status, deserialized.newStatus)
    }
  }

  @Test
  def testKafkaRoundTripOrderPlaced(): Unit = {
    if (!kafkaAvailable) {
      println("Skipping Kafka test - Kafka not available")
      return
    }

    val topicName = s"order-placed-test-scala-$TEST_RUN_ID"
    createTopicIfNotExists(topicName)

    val original = OrderPlaced(
      orderId = UUID.randomUUID(),
      customerId = 99999L,
      totalAmount = Decimal10_2.unsafeForce(new java.math.BigDecimal("1234.56")),
      placedAt = Instant.now(),
      items = List("kafka-item-1", "kafka-item-2"),
      shippingAddress = Some("Kafka Test Address")
    )

    val serialized = serializeGenericRecord(original.toGenericRecord, OrderPlaced.SCHEMA)

    val producer = createProducer()
    try {
      producer.send(new ProducerRecord(topicName, original.orderId.toString, serialized)).get()
      producer.flush()
    } finally {
      producer.close()
    }

    val consumer = createConsumer()
    try {
      consumer.subscribe(Collections.singletonList(topicName))

      val records = consumer.poll(Duration.ofSeconds(10))
      assertFalse("Should receive at least one record", records.isEmpty)

      val received = records.iterator().next()
      val genericRecord = deserializeGenericRecord(received.value(), OrderPlaced.SCHEMA)
      val deserialized = OrderPlaced.fromGenericRecord(genericRecord)

      assertEquals(original.orderId, deserialized.orderId)
      assertEquals(original.customerId, deserialized.customerId)
      assertEquals(original.totalAmount, deserialized.totalAmount)
      assertEquals(original.items, deserialized.items)
      assertEquals(original.shippingAddress, deserialized.shippingAddress)
    } finally {
      consumer.close()
    }
  }

  @Test
  def testKafkaRoundTripOrderUpdated(): Unit = {
    if (!kafkaAvailable) {
      println("Skipping Kafka test - Kafka not available")
      return
    }

    val topicName = s"order-updated-test-scala-$TEST_RUN_ID"
    createTopicIfNotExists(topicName)

    val address = Address("789 Kafka St", "MessageCity", "54321", "KF")
    val original = OrderUpdated(
      orderId = UUID.randomUUID(),
      previousStatus = OrderStatus.PENDING,
      newStatus = OrderStatus.DELIVERED,
      updatedAt = Instant.now(),
      shippingAddress = Some(address)
    )

    val serialized = serializeGenericRecord(original.toGenericRecord, OrderUpdated.SCHEMA)

    val producer = createProducer()
    try {
      producer.send(new ProducerRecord(topicName, original.orderId.toString, serialized)).get()
      producer.flush()
    } finally {
      producer.close()
    }

    val consumer = createConsumer()
    try {
      consumer.subscribe(Collections.singletonList(topicName))

      val records = consumer.poll(Duration.ofSeconds(10))
      assertFalse("Should receive at least one record", records.isEmpty)

      val received = records.iterator().next()
      val genericRecord = deserializeGenericRecord(received.value(), OrderUpdated.SCHEMA)
      val deserialized = OrderUpdated.fromGenericRecord(genericRecord)

      assertEquals(original.orderId, deserialized.orderId)
      assertEquals(original.previousStatus, deserialized.previousStatus)
      assertEquals(original.newStatus, deserialized.newStatus)
      assertTrue(deserialized.shippingAddress.isDefined)
      assertEquals(address.street, deserialized.shippingAddress.get.street)
    } finally {
      consumer.close()
    }
  }

  @Test
  def testKafkaMultipleMessages(): Unit = {
    if (!kafkaAvailable) {
      println("Skipping Kafka test - Kafka not available")
      return
    }

    val topicName = s"order-batch-test-scala-$TEST_RUN_ID"
    createTopicIfNotExists(topicName)

    val originals = (0 until 10).map { i =>
      OrderPlaced(
        orderId = UUID.randomUUID(),
        customerId = i.toLong,
        totalAmount = Decimal10_2.unsafeForce(new java.math.BigDecimal(s"$i.99")),
        placedAt = Instant.now(),
        items = List(s"batch-item-$i"),
        shippingAddress = if (i % 2 == 0) Some(s"Address $i") else None
      )
    }.toList

    val producer = createProducer()
    try {
      for (order <- originals) {
        val serialized = serializeGenericRecord(order.toGenericRecord, OrderPlaced.SCHEMA)
        producer.send(new ProducerRecord(topicName, order.orderId.toString, serialized)).get()
      }
      producer.flush()
    } finally {
      producer.close()
    }

    val receivedOrders = scala.collection.mutable.Map[UUID, OrderPlaced]()
    val consumer = createConsumer()
    try {
      consumer.subscribe(Collections.singletonList(topicName))

      var attempts = 0
      while (receivedOrders.size < originals.size && attempts < 10) {
        val records = consumer.poll(Duration.ofSeconds(2))
        records.asScala.foreach { record =>
          val genericRecord = deserializeGenericRecord(record.value(), OrderPlaced.SCHEMA)
          val deserialized = OrderPlaced.fromGenericRecord(genericRecord)
          receivedOrders += (deserialized.orderId -> deserialized)
        }
        attempts += 1
      }
    } finally {
      consumer.close()
    }

    assertEquals("Should receive all messages", originals.size, receivedOrders.size)

    for (original <- originals) {
      val received = receivedOrders.get(original.orderId)
      assertTrue(s"Should find order ${original.orderId}", received.isDefined)
      assertEquals(original.customerId, received.get.customerId)
      assertEquals(original.shippingAddress, received.get.shippingAddress)
    }
  }

  // ========== SchemaValidator Tests ==========

  @Test
  def testSchemaValidatorBackwardCompatibility(): Unit = {
    val validator = new SchemaValidator()

    // Same schema should be backward compatible with itself
    assertTrue(validator.isBackwardCompatible(OrderPlaced.SCHEMA, OrderPlaced.SCHEMA))
    assertTrue(validator.isBackwardCompatible(OrderUpdated.SCHEMA, OrderUpdated.SCHEMA))
    assertTrue(validator.isBackwardCompatible(Address.SCHEMA, Address.SCHEMA))
  }

  @Test
  def testSchemaValidatorForwardCompatibility(): Unit = {
    val validator = new SchemaValidator()

    // Same schema should be forward compatible with itself
    assertTrue(validator.isForwardCompatible(OrderPlaced.SCHEMA, OrderPlaced.SCHEMA))
    assertTrue(validator.isForwardCompatible(OrderUpdated.SCHEMA, OrderUpdated.SCHEMA))
  }

  @Test
  def testSchemaValidatorFullCompatibility(): Unit = {
    val validator = new SchemaValidator()

    // Same schema should be fully compatible with itself
    assertTrue(validator.isFullyCompatible(OrderPlaced.SCHEMA, OrderPlaced.SCHEMA))
    assertTrue(validator.isFullyCompatible(OrderUpdated.SCHEMA, OrderUpdated.SCHEMA))
    assertTrue(validator.isFullyCompatible(Address.SCHEMA, Address.SCHEMA))
  }

  @Test
  def testSchemaValidatorCheckCompatibility(): Unit = {
    val validator = new SchemaValidator()

    val result = validator.checkCompatibility(OrderPlaced.SCHEMA, OrderPlaced.SCHEMA)
    assertNotNull(result)
    assertEquals(
      org.apache.avro.SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE,
      result.getType
    )
  }

  @Test
  def testSchemaValidatorGetMissingFields(): Unit = {
    val validator = new SchemaValidator()

    // Same schema should have no missing fields
    val missingFields = validator.getMissingFields(OrderPlaced.SCHEMA, OrderPlaced.SCHEMA)
    assertNotNull(missingFields)
    assertTrue("Same schema should have no missing fields", missingFields.isEmpty)
  }

  @Test
  def testSchemaValidatorGetSchemaByName(): Unit = {
    val validator = new SchemaValidator()

    // Should find known schemas
    assertEquals(OrderPlaced.SCHEMA, validator.getSchemaByName("com.example.events.OrderPlaced"))
    assertEquals(OrderUpdated.SCHEMA, validator.getSchemaByName("com.example.events.OrderUpdated"))
    assertEquals(OrderCancelled.SCHEMA, validator.getSchemaByName("com.example.events.OrderCancelled"))
    assertEquals(Address.SCHEMA, validator.getSchemaByName("com.example.events.Address"))

    // Should return null for unknown schemas
    assertNull(validator.getSchemaByName("com.example.events.Unknown"))
    assertNull(validator.getSchemaByName(""))
  }

  @Test
  def testSchemaValidatorValidateRequiredFields(): Unit = {
    val validator = new SchemaValidator()

    // Should validate required fields (currently returns true)
    assertTrue(validator.validateRequiredFields(OrderPlaced.SCHEMA))
    assertTrue(validator.validateRequiredFields(Address.SCHEMA))
  }

  // ========== Complex Union Types Tests (Feature 3) ==========

  @Test
  def testComplexUnionTypeStringOrIntOrBoolean(): Unit = {
    // Test creating union values with different types
    val stringValue = StringOrIntOrBoolean.of("hello")
    val intValue = StringOrIntOrBoolean.of(42)
    val boolValue = StringOrIntOrBoolean.of(true)

    // Test isXxx methods
    assertTrue(stringValue.isString)
    assertFalse(stringValue.isInt)
    assertFalse(stringValue.isBoolean)

    assertTrue(intValue.isInt)
    assertFalse(intValue.isString)
    assertFalse(intValue.isBoolean)

    assertTrue(boolValue.isBoolean)
    assertFalse(boolValue.isString)
    assertFalse(boolValue.isInt)

    // Test asXxx methods
    assertEquals("hello", stringValue.asString)
    assertEquals(42, intValue.asInt)
    assertEquals(true, boolValue.asBoolean)
  }

  @Test
  def testComplexUnionTypeThrowsOnWrongType(): Unit = {
    val stringValue = StringOrIntOrBoolean.of("hello")

    try {
      stringValue.asInt
      fail("Expected UnsupportedOperationException")
    } catch {
      case _: UnsupportedOperationException => // Expected
    }

    try {
      stringValue.asBoolean
      fail("Expected UnsupportedOperationException")
    } catch {
      case _: UnsupportedOperationException => // Expected
    }
  }

  @Test
  def testDynamicValueWithComplexUnions(): Unit = {
    // Test with string value
    val withString = DynamicValue(
      id = "id-1",
      value = StringOrIntOrBoolean.of("test-string"),
      optionalValue = None
    )

    val record1 = withString.toGenericRecord
    val deserialized1 = DynamicValue.fromGenericRecord(record1)

    assertEquals("id-1", deserialized1.id)
    assertTrue(deserialized1.value.isString)
    assertEquals("test-string", deserialized1.value.asString)
    assertTrue(deserialized1.optionalValue.isEmpty)

    // Test with int value
    val withInt = DynamicValue(
      id = "id-2",
      value = StringOrIntOrBoolean.of(123),
      optionalValue = Some(StringOrLong.of(456L))
    )

    val record2 = withInt.toGenericRecord
    val deserialized2 = DynamicValue.fromGenericRecord(record2)

    assertEquals("id-2", deserialized2.id)
    assertTrue(deserialized2.value.isInt)
    assertEquals(123, deserialized2.value.asInt)
    assertTrue(deserialized2.optionalValue.isDefined)
    assertTrue(deserialized2.optionalValue.get.isLong)
    assertEquals(456L, deserialized2.optionalValue.get.asLong)

    // Test with boolean value and optional string
    val withBool = DynamicValue(
      id = "id-3",
      value = StringOrIntOrBoolean.of(false),
      optionalValue = Some(StringOrLong.of("optional-str"))
    )

    val record3 = withBool.toGenericRecord
    val deserialized3 = DynamicValue.fromGenericRecord(record3)

    assertEquals("id-3", deserialized3.id)
    assertTrue(deserialized3.value.isBoolean)
    assertEquals(false, deserialized3.value.asBoolean)
    assertTrue(deserialized3.optionalValue.isDefined)
    assertTrue(deserialized3.optionalValue.get.isString)
    assertEquals("optional-str", deserialized3.optionalValue.get.asString)
  }

  @Test
  def testDynamicValueKafkaRoundTrip(): Unit = {
    if (!kafkaAvailable) {
      println("Skipping Kafka test - Kafka not available")
      return
    }

    val topicName = s"dynamic-value-test-scala-$TEST_RUN_ID"
    createTopicIfNotExists(topicName)

    val original = DynamicValue(
      id = "kafka-id",
      value = StringOrIntOrBoolean.of(999),
      optionalValue = Some(StringOrLong.of("kafka-string"))
    )

    val serialized = serializeGenericRecord(original.toGenericRecord, DynamicValue.SCHEMA)

    val producer = createProducer()
    try {
      producer.send(new ProducerRecord(topicName, original.id, serialized)).get()
      producer.flush()
    } finally {
      producer.close()
    }

    val consumer = createConsumer()
    try {
      consumer.subscribe(Collections.singletonList(topicName))

      val records = consumer.poll(Duration.ofSeconds(10))
      assertFalse("Should receive at least one record", records.isEmpty)

      val received = records.iterator().next()
      val genericRecord = deserializeGenericRecord(received.value(), DynamicValue.SCHEMA)
      val deserialized = DynamicValue.fromGenericRecord(genericRecord)

      assertEquals(original.id, deserialized.id)
      assertTrue(deserialized.value.isInt)
      assertEquals(999, deserialized.value.asInt)
      assertTrue(deserialized.optionalValue.isDefined)
      assertEquals("kafka-string", deserialized.optionalValue.get.asString)
    } finally {
      consumer.close()
    }
  }

  // ========== Avro $ref Support Tests (Feature 5) ==========

  @Test
  def testInvoiceWithMoneyRef(): Unit = {
    val total = Money(Decimal18_4.unsafeForce(new java.math.BigDecimal("1234.5678")), "USD")
    val original = Invoice(
      invoiceId = UUID.randomUUID(),
      customerId = 12345L,
      total = total,
      issuedAt = Instant.now()
    )

    val record = original.toGenericRecord
    val deserialized = Invoice.fromGenericRecord(record)

    assertEquals(original.invoiceId, deserialized.invoiceId)
    assertEquals(original.customerId, deserialized.customerId)
    assertEquals(original.total.amount, deserialized.total.amount)
    assertEquals(original.total.currency, deserialized.total.currency)
    assertEquals(original.issuedAt.toEpochMilli, deserialized.issuedAt.toEpochMilli)
  }

  @Test
  def testMoneyStandalone(): Unit = {
    val original = Money(Decimal18_4.unsafeForce(new java.math.BigDecimal("99999.9999")), "EUR")

    val record = original.toGenericRecord
    val deserialized = Money.fromGenericRecord(record)

    assertEquals(original.amount, deserialized.amount)
    assertEquals(original.currency, deserialized.currency)
  }

  @Test
  def testInvoiceKafkaRoundTrip(): Unit = {
    if (!kafkaAvailable) {
      println("Skipping Kafka test - Kafka not available")
      return
    }

    val topicName = s"invoice-test-scala-$TEST_RUN_ID"
    createTopicIfNotExists(topicName)

    val total = Money(Decimal18_4.unsafeForce(new java.math.BigDecimal("5000.00")), "GBP")
    val original = Invoice(
      invoiceId = UUID.randomUUID(),
      customerId = 67890L,
      total = total,
      issuedAt = Instant.now()
    )

    val serialized = serializeGenericRecord(original.toGenericRecord, Invoice.SCHEMA)

    val producer = createProducer()
    try {
      producer.send(new ProducerRecord(topicName, original.invoiceId.toString, serialized)).get()
      producer.flush()
    } finally {
      producer.close()
    }

    val consumer = createConsumer()
    try {
      consumer.subscribe(Collections.singletonList(topicName))

      val records = consumer.poll(Duration.ofSeconds(10))
      assertFalse("Should receive at least one record", records.isEmpty)

      val received = records.iterator().next()
      val genericRecord = deserializeGenericRecord(received.value(), Invoice.SCHEMA)
      val deserialized = Invoice.fromGenericRecord(genericRecord)

      assertEquals(original.invoiceId, deserialized.invoiceId)
      assertEquals(original.customerId, deserialized.customerId)
      assertEquals(original.total.amount, deserialized.total.amount)
      assertEquals("GBP", deserialized.total.currency)
    } finally {
      consumer.close()
    }
  }

  // ========== Topics/TypedTopic Tests (Feature 1 - Key Schemas) ==========

  @Test
  def testTopicsConstantsExist(): Unit = {
    // Verify that all topic bindings are defined
    assertNotNull(Topics.ADDRESS)
    assertNotNull(Topics.DYNAMIC_VALUE)
    assertNotNull(Topics.INVOICE)
    assertNotNull(Topics.MONEY)
    assertNotNull(Topics.ORDER_CANCELLED)
    assertNotNull(Topics.ORDER_EVENTS)
    assertNotNull(Topics.ORDER_PLACED)
    assertNotNull(Topics.ORDER_UPDATED)
  }

  @Test
  def testTypedTopicProperties(): Unit = {
    // Verify topic names
    assertEquals("address", Topics.ADDRESS.name)
    assertEquals("dynamic-value", Topics.DYNAMIC_VALUE.name)
    assertEquals("invoice", Topics.INVOICE.name)
    assertEquals("order-events", Topics.ORDER_EVENTS.name)

    // Verify serdes are not null
    assertNotNull(Topics.ADDRESS.keySerde)
    assertNotNull(Topics.ADDRESS.valueSerde)
    assertNotNull(Topics.DYNAMIC_VALUE.keySerde)
    assertNotNull(Topics.DYNAMIC_VALUE.valueSerde)
    assertNotNull(Topics.INVOICE.keySerde)
    assertNotNull(Topics.INVOICE.valueSerde)
  }

  @Test
  def testTypedTopicSerdeRoundTrip(): Unit = {
    if (!schemaRegistryAvailable) {
      println("Skipping Schema Registry test - Schema Registry not available")
      return
    }

    // Configure the serde with Schema Registry
    val config = new util.HashMap[String, AnyRef]()
    config.put("schema.registry.url", SCHEMA_REGISTRY_URL)

    val serializer = Topics.ADDRESS.valueSerde.serializer()
    val deserializer = Topics.ADDRESS.valueSerde.deserializer()
    serializer.configure(config, false)
    deserializer.configure(config, false)

    val original = Address("123 Test St", "TestCity", "12345", "US")

    val topicName = s"serde-test-address-scala-$TEST_RUN_ID"
    val serialized = serializer.serialize(topicName, original)
    val deserialized = deserializer.deserialize(topicName, serialized)

    assertEquals(original.street, deserialized.street)
    assertEquals(original.city, deserialized.city)
    assertEquals(original.postalCode, deserialized.postalCode)
    assertEquals(original.country, deserialized.country)
  }

  @Test
  def testTypedTopicDynamicValueSerde(): Unit = {
    if (!schemaRegistryAvailable) {
      println("Skipping Schema Registry test - Schema Registry not available")
      return
    }

    // Configure the serde with Schema Registry
    val config = new util.HashMap[String, AnyRef]()
    config.put("schema.registry.url", SCHEMA_REGISTRY_URL)

    val serializer = Topics.DYNAMIC_VALUE.valueSerde.serializer()
    val deserializer = Topics.DYNAMIC_VALUE.valueSerde.deserializer()
    serializer.configure(config, false)
    deserializer.configure(config, false)

    val original = DynamicValue(
      id = "serde-test",
      value = StringOrIntOrBoolean.of("value"),
      optionalValue = Some(StringOrLong.of(100L))
    )

    val topicName = s"serde-test-dynamic-value-scala-$TEST_RUN_ID"
    val serialized = serializer.serialize(topicName, original)
    val deserialized = deserializer.deserialize(topicName, serialized)

    assertEquals(original.id, deserialized.id)
    assertTrue(deserialized.value.isString)
    assertEquals("value", deserialized.value.asString)
    assertTrue(deserialized.optionalValue.isDefined)
    assertEquals(100L, deserialized.optionalValue.get.asLong)
  }

  @Test
  def testTypedTopicInvoiceSerde(): Unit = {
    if (!schemaRegistryAvailable) {
      println("Skipping Schema Registry test - Schema Registry not available")
      return
    }

    // Configure the serde with Schema Registry
    val config = new util.HashMap[String, AnyRef]()
    config.put("schema.registry.url", SCHEMA_REGISTRY_URL)

    val serializer = Topics.INVOICE.valueSerde.serializer()
    val deserializer = Topics.INVOICE.valueSerde.deserializer()
    serializer.configure(config, false)
    deserializer.configure(config, false)

    val total = Money(Decimal18_4.unsafeForce(new java.math.BigDecimal("250.00")), "CAD")
    val original = Invoice(
      invoiceId = UUID.randomUUID(),
      customerId = 11111L,
      total = total,
      issuedAt = Instant.now()
    )

    val topicName = s"serde-test-invoice-scala-$TEST_RUN_ID"
    val serialized = serializer.serialize(topicName, original)
    val deserialized = deserializer.deserialize(topicName, serialized)

    assertEquals(original.invoiceId, deserialized.invoiceId)
    assertEquals(original.customerId, deserialized.customerId)
    assertEquals(original.total.amount, deserialized.total.amount)
    assertEquals(original.total.currency, deserialized.total.currency)
  }

  // ========== Recursive Types Tests ==========

  @Test
  def testTreeNodeSimpleRoundTrip(): Unit = {
    // Test a simple leaf node
    val leaf = TreeNode(value = "leaf", left = None, right = None)

    val record = leaf.toGenericRecord
    val deserialized = TreeNode.fromGenericRecord(record)

    assertEquals(leaf.value, deserialized.value)
    assertEquals(leaf.left, deserialized.left)
    assertEquals(leaf.right, deserialized.right)
  }

  @Test
  def testTreeNodeRecursiveRoundTrip(): Unit = {
    // Test a tree with nested nodes
    val leftChild = TreeNode("left-child", None, None)
    val rightChild = TreeNode("right-child", None, None)
    val root = TreeNode("root", Some(leftChild), Some(rightChild))

    val record = root.toGenericRecord
    val deserialized = TreeNode.fromGenericRecord(record)

    assertEquals("root", deserialized.value)
    assertTrue(deserialized.left.isDefined)
    assertTrue(deserialized.right.isDefined)
    assertEquals("left-child", deserialized.left.get.value)
    assertEquals("right-child", deserialized.right.get.value)
    assertFalse(deserialized.left.get.left.isDefined)
    assertFalse(deserialized.right.get.right.isDefined)
  }

  @Test
  def testTreeNodeDeeplyNested(): Unit = {
    // Test a deeply nested structure (left-leaning tree)
    val level3 = TreeNode("level3", None, None)
    val level2 = TreeNode("level2", Some(level3), None)
    val level1 = TreeNode("level1", Some(level2), None)
    val root = TreeNode("root", Some(level1), None)

    val record = root.toGenericRecord
    val deserialized = TreeNode.fromGenericRecord(record)

    assertEquals("root", deserialized.value)
    assertEquals("level1", deserialized.left.get.value)
    assertEquals("level2", deserialized.left.get.left.get.value)
    assertEquals("level3", deserialized.left.get.left.get.left.get.value)
    assertFalse(deserialized.left.get.left.get.left.get.left.isDefined)
  }

  @Test
  def testLinkedListNodeSimpleRoundTrip(): Unit = {
    // Test a single node list
    val single = LinkedListNode(value = 42, next = None)

    val record = single.toGenericRecord
    val deserialized = LinkedListNode.fromGenericRecord(record)

    assertEquals(42, deserialized.value)
    assertFalse(deserialized.next.isDefined)
  }

  @Test
  def testLinkedListNodeChainRoundTrip(): Unit = {
    // Test a linked list: 1 -> 2 -> 3 -> null
    val node3 = LinkedListNode(3, None)
    val node2 = LinkedListNode(2, Some(node3))
    val node1 = LinkedListNode(1, Some(node2))

    val record = node1.toGenericRecord
    val deserialized = LinkedListNode.fromGenericRecord(record)

    assertEquals(1, deserialized.value)
    assertTrue(deserialized.next.isDefined)
    assertEquals(2, deserialized.next.get.value)
    assertTrue(deserialized.next.get.next.isDefined)
    assertEquals(3, deserialized.next.get.next.get.value)
    assertFalse(deserialized.next.get.next.get.next.isDefined)
  }

  @Test
  def testTreeNodeKafkaRoundTrip(): Unit = {
    if (!kafkaAvailable) {
      println("Skipping Kafka test - Kafka not available")
      return
    }

    val topicName = s"tree-node-test-scala-$TEST_RUN_ID"
    createTopicIfNotExists(topicName)

    val leftChild = TreeNode("left", None, None)
    val rightChild = TreeNode("right", None, None)
    val original = TreeNode("root", Some(leftChild), Some(rightChild))

    val serialized = serializeGenericRecord(original.toGenericRecord, TreeNode.SCHEMA)

    val producer = createProducer()
    try {
      producer.send(new ProducerRecord(topicName, "tree-key", serialized)).get()
      producer.flush()
    } finally {
      producer.close()
    }

    val consumer = createConsumer()
    try {
      consumer.subscribe(Collections.singletonList(topicName))

      val records = consumer.poll(java.time.Duration.ofSeconds(10))
      assertFalse("Should receive at least one record", records.isEmpty)

      val received = records.iterator().next()
      val genericRecord = deserializeGenericRecord(received.value(), TreeNode.SCHEMA)
      val deserialized = TreeNode.fromGenericRecord(genericRecord)

      assertEquals("root", deserialized.value)
      assertEquals("left", deserialized.left.get.value)
      assertEquals("right", deserialized.right.get.value)
    } finally {
      consumer.close()
    }
  }

  @Test
  def testLinkedListNodeKafkaRoundTrip(): Unit = {
    if (!kafkaAvailable) {
      println("Skipping Kafka test - Kafka not available")
      return
    }

    val topicName = s"linked-list-test-scala-$TEST_RUN_ID"
    createTopicIfNotExists(topicName)

    val node3 = LinkedListNode(300, None)
    val node2 = LinkedListNode(200, Some(node3))
    val original = LinkedListNode(100, Some(node2))

    val serialized = serializeGenericRecord(original.toGenericRecord, LinkedListNode.SCHEMA)

    val producer = createProducer()
    try {
      producer.send(new ProducerRecord(topicName, "list-key", serialized)).get()
      producer.flush()
    } finally {
      producer.close()
    }

    val consumer = createConsumer()
    try {
      consumer.subscribe(Collections.singletonList(topicName))

      val records = consumer.poll(java.time.Duration.ofSeconds(10))
      assertFalse("Should receive at least one record", records.isEmpty)

      val received = records.iterator().next()
      val genericRecord = deserializeGenericRecord(received.value(), LinkedListNode.SCHEMA)
      val deserialized = LinkedListNode.fromGenericRecord(genericRecord)

      assertEquals(100, deserialized.value)
      assertEquals(200, deserialized.next.get.value)
      assertEquals(300, deserialized.next.get.next.get.value)
    } finally {
      consumer.close()
    }
  }

  private def createTopicIfNotExists(topicName: String): Unit = {
    val props = new Properties()
    props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS)

    val admin = AdminClient.create(props)
    try {
      val existingTopics = admin.listTopics().names().get()
      if (!existingTopics.contains(topicName)) {
        val newTopic = new NewTopic(topicName, 1, 1.toShort)
        admin.createTopics(Collections.singletonList(newTopic)).all().get()
      }
    } finally {
      admin.close()
    }
  }

  private def createProducer(): KafkaProducer[String, Array[Byte]] = {
    val props = new Properties()
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS)
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[ByteArraySerializer].getName)
    props.put(ProducerConfig.ACKS_CONFIG, "all")
    new KafkaProducer[String, Array[Byte]](props)
  }

  private def createConsumer(): KafkaConsumer[String, Array[Byte]] = {
    val props = new Properties()
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS)
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName)
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, classOf[ByteArrayDeserializer].getName)
    props.put(ConsumerConfig.GROUP_ID_CONFIG, s"test-group-${UUID.randomUUID()}")
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
    new KafkaConsumer[String, Array[Byte]](props)
  }

  private def serializeGenericRecord(record: GenericRecord, schema: org.apache.avro.Schema): Array[Byte] = {
    val out = new ByteArrayOutputStream()
    val encoder = org.apache.avro.io.EncoderFactory.get().binaryEncoder(out, null)
    val writer = new org.apache.avro.generic.GenericDatumWriter[GenericRecord](schema)
    writer.write(record, encoder)
    encoder.flush()
    out.toByteArray
  }

  private def deserializeGenericRecord(data: Array[Byte], schema: org.apache.avro.Schema): GenericRecord = {
    val decoder = org.apache.avro.io.DecoderFactory.get().binaryDecoder(data, null)
    val reader = new org.apache.avro.generic.GenericDatumReader[GenericRecord](schema)
    reader.read(null, decoder)
  }
}

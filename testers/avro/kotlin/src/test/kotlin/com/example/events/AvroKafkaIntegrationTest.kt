package com.example.events

import com.example.events.common.Money
import com.example.events.precisetypes.Decimal10_2
import com.example.events.precisetypes.Decimal18_4
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.generic.GenericRecord
import org.apache.avro.io.DecoderFactory
import org.apache.avro.io.EncoderFactory
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.BeforeClass
import org.junit.Test
import org.junit.Assert.*

import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.*

/**
 * Integration tests for Avro serialization/deserialization through Kafka.
 *
 * These tests are idempotent - they use unique topic names and random consumer group IDs
 * so they can be safely re-run on the same Kafka instance.
 *
 * Requires Kafka running on localhost:9092 (use docker-compose up kafka).
 */
class AvroKafkaIntegrationTest {

    companion object {
        private const val BOOTSTRAP_SERVERS = "localhost:9092"
        private const val SCHEMA_REGISTRY_URL = "http://localhost:8081"
        val TEST_RUN_ID: String = UUID.randomUUID().toString().substring(0, 8)

        var kafkaAvailable: Boolean = false
        var schemaRegistryAvailable: Boolean = false

        @BeforeClass
        @JvmStatic
        fun checkKafkaAvailability() {
            val props = Properties().apply {
                put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS)
                put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000)
                put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 5000)
            }

            try {
                AdminClient.create(props).use { admin ->
                    admin.listTopics().names().get()
                    kafkaAvailable = true
                    println("Kafka is available at $BOOTSTRAP_SERVERS")
                }
            } catch (e: Exception) {
                println("Kafka not available at $BOOTSTRAP_SERVERS: ${e.message}")
                println("Skipping Kafka integration tests. Start Kafka with: docker-compose up -d kafka")
            }

            // Check Schema Registry availability
            try {
                val conn = java.net.URL("$SCHEMA_REGISTRY_URL/subjects").openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.requestMethod = "GET"
                if (conn.responseCode == 200) {
                    schemaRegistryAvailable = true
                    println("Schema Registry is available at $SCHEMA_REGISTRY_URL")
                }
                conn.disconnect()
            } catch (e: Exception) {
                println("Schema Registry not available at $SCHEMA_REGISTRY_URL: ${e.message}")
                println("Skipping Schema Registry tests. Start with: docker-compose up -d schema-registry")
            }
        }
    }

    @Test
    fun testOrderPlacedSerdeWithoutKafka() {
        val original = OrderPlaced(
            orderId = UUID.randomUUID(),
            customerId = 12345L,
            totalAmount = Decimal10_2.unsafeForce(BigDecimal("99.99")),
            placedAt = Instant.now(),
            items = listOf("item-1", "item-2", "item-3"),
            shippingAddress = "123 Main St"
        )

        val record = original.toGenericRecord()
        val deserialized = OrderPlaced.fromGenericRecord(record)

        assertEquals(original.orderId, deserialized.orderId)
        assertEquals(original.customerId, deserialized.customerId)
        assertEquals(original.totalAmount, deserialized.totalAmount)
        assertEquals(original.placedAt.toEpochMilli(), deserialized.placedAt.toEpochMilli())
        assertEquals(original.items, deserialized.items)
        assertEquals(original.shippingAddress, deserialized.shippingAddress)
    }

    @Test
    fun testOrderPlacedWithNullOptionalField() {
        val original = OrderPlaced(
            orderId = UUID.randomUUID(),
            customerId = 12345L,
            totalAmount = Decimal10_2.unsafeForce(BigDecimal("50.00")),
            placedAt = Instant.now(),
            items = listOf("item-a"),
            shippingAddress = null
        )

        val record = original.toGenericRecord()
        val deserialized = OrderPlaced.fromGenericRecord(record)

        assertEquals(original.orderId, deserialized.orderId)
        assertNull(deserialized.shippingAddress)
    }

    @Test
    fun testOrderUpdatedWithNestedRecord() {
        val address = Address("456 Oak Ave", "Springfield", "12345", "US")
        val original = OrderUpdated(
            orderId = UUID.randomUUID(),
            previousStatus = OrderStatus.PENDING,
            newStatus = OrderStatus.SHIPPED,
            updatedAt = Instant.now(),
            shippingAddress = address
        )

        val record = original.toGenericRecord()
        val deserialized = OrderUpdated.fromGenericRecord(record)

        assertEquals(original.orderId, deserialized.orderId)
        assertEquals(original.previousStatus, deserialized.previousStatus)
        assertEquals(original.newStatus, deserialized.newStatus)
        assertEquals(original.updatedAt.toEpochMilli(), deserialized.updatedAt.toEpochMilli())
        assertNotNull(deserialized.shippingAddress)

        val deserializedAddr = deserialized.shippingAddress!!
        assertEquals(address.street, deserializedAddr.street)
        assertEquals(address.city, deserializedAddr.city)
        assertEquals(address.postalCode, deserializedAddr.postalCode)
        assertEquals(address.country, deserializedAddr.country)
    }

    @Test
    fun testOrderUpdatedWithNullNestedRecord() {
        val original = OrderUpdated(
            orderId = UUID.randomUUID(),
            previousStatus = OrderStatus.CONFIRMED,
            newStatus = OrderStatus.CANCELLED,
            updatedAt = Instant.now(),
            shippingAddress = null
        )

        val record = original.toGenericRecord()
        val deserialized = OrderUpdated.fromGenericRecord(record)

        assertEquals(original.previousStatus, deserialized.previousStatus)
        assertEquals(original.newStatus, deserialized.newStatus)
        assertNull(deserialized.shippingAddress)
    }

    @Test
    fun testAllEnumValues() {
        for (status in OrderStatus.entries) {
            val original = OrderUpdated(
                orderId = UUID.randomUUID(),
                previousStatus = status,
                newStatus = status,
                updatedAt = Instant.now(),
                shippingAddress = null
            )

            val record = original.toGenericRecord()
            val deserialized = OrderUpdated.fromGenericRecord(record)

            assertEquals(status, deserialized.previousStatus)
            assertEquals(status, deserialized.newStatus)
        }
    }

    @Test
    fun testKafkaRoundTripOrderPlaced() {
        if (!kafkaAvailable) {
            println("Skipping Kafka test - Kafka not available")
            return
        }

        val topicName = "order-placed-test-kotlin-$TEST_RUN_ID"
        createTopicIfNotExists(topicName)

        val original = OrderPlaced(
            orderId = UUID.randomUUID(),
            customerId = 99999L,
            totalAmount = Decimal10_2.unsafeForce(BigDecimal("1234.56")),
            placedAt = Instant.now(),
            items = listOf("kafka-item-1", "kafka-item-2"),
            shippingAddress = "Kafka Test Address"
        )

        val serialized = serializeGenericRecord(original.toGenericRecord(), OrderPlaced.SCHEMA)

        createProducer().use { producer ->
            producer.send(ProducerRecord(topicName, original.orderId.toString(), serialized)).get()
            producer.flush()
        }

        createConsumer().use { consumer ->
            consumer.subscribe(listOf(topicName))

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
        }
    }

    @Test
    fun testKafkaRoundTripOrderUpdated() {
        if (!kafkaAvailable) {
            println("Skipping Kafka test - Kafka not available")
            return
        }

        val topicName = "order-updated-test-kotlin-$TEST_RUN_ID"
        createTopicIfNotExists(topicName)

        val address = Address("789 Kafka St", "MessageCity", "54321", "KF")
        val original = OrderUpdated(
            orderId = UUID.randomUUID(),
            previousStatus = OrderStatus.PENDING,
            newStatus = OrderStatus.DELIVERED,
            updatedAt = Instant.now(),
            shippingAddress = address
        )

        val serialized = serializeGenericRecord(original.toGenericRecord(), OrderUpdated.SCHEMA)

        createProducer().use { producer ->
            producer.send(ProducerRecord(topicName, original.orderId.toString(), serialized)).get()
            producer.flush()
        }

        createConsumer().use { consumer ->
            consumer.subscribe(listOf(topicName))

            val records = consumer.poll(Duration.ofSeconds(10))
            assertFalse("Should receive at least one record", records.isEmpty)

            val received = records.iterator().next()
            val genericRecord = deserializeGenericRecord(received.value(), OrderUpdated.SCHEMA)
            val deserialized = OrderUpdated.fromGenericRecord(genericRecord)

            assertEquals(original.orderId, deserialized.orderId)
            assertEquals(original.previousStatus, deserialized.previousStatus)
            assertEquals(original.newStatus, deserialized.newStatus)
            assertNotNull(deserialized.shippingAddress)
            assertEquals(address.street, deserialized.shippingAddress!!.street)
        }
    }

    @Test
    fun testKafkaMultipleMessages() {
        if (!kafkaAvailable) {
            println("Skipping Kafka test - Kafka not available")
            return
        }

        val topicName = "order-batch-test-kotlin-$TEST_RUN_ID"
        createTopicIfNotExists(topicName)

        val originals = (0 until 10).map { i ->
            OrderPlaced(
                orderId = UUID.randomUUID(),
                customerId = i.toLong(),
                totalAmount = Decimal10_2.unsafeForce(BigDecimal("$i.99")),
                placedAt = Instant.now(),
                items = listOf("batch-item-$i"),
                shippingAddress = if (i % 2 == 0) "Address $i" else null
            )
        }

        createProducer().use { producer ->
            for (order in originals) {
                val serialized = serializeGenericRecord(order.toGenericRecord(), OrderPlaced.SCHEMA)
                producer.send(ProducerRecord(topicName, order.orderId.toString(), serialized)).get()
            }
            producer.flush()
        }

        val receivedOrders = mutableMapOf<UUID, OrderPlaced>()
        createConsumer().use { consumer ->
            consumer.subscribe(listOf(topicName))

            var attempts = 0
            while (receivedOrders.size < originals.size && attempts < 10) {
                val records = consumer.poll(Duration.ofSeconds(2))
                for (record in records) {
                    val genericRecord = deserializeGenericRecord(record.value(), OrderPlaced.SCHEMA)
                    val deserialized = OrderPlaced.fromGenericRecord(genericRecord)
                    receivedOrders[deserialized.orderId] = deserialized
                }
                attempts++
            }
        }

        assertEquals("Should receive all messages", originals.size, receivedOrders.size)

        for (original in originals) {
            val received = receivedOrders[original.orderId]
            assertNotNull("Should find order ${original.orderId}", received)
            assertEquals(original.customerId, received!!.customerId)
            assertEquals(original.shippingAddress, received.shippingAddress)
        }
    }

    // ========== SchemaValidator Tests ==========

    @Test
    fun testSchemaValidatorBackwardCompatibility() {
        val validator = SchemaValidator()

        // Same schema should be backward compatible with itself
        assertTrue(validator.isBackwardCompatible(OrderPlaced.SCHEMA, OrderPlaced.SCHEMA))
        assertTrue(validator.isBackwardCompatible(OrderUpdated.SCHEMA, OrderUpdated.SCHEMA))
        assertTrue(validator.isBackwardCompatible(Address.SCHEMA, Address.SCHEMA))
    }

    @Test
    fun testSchemaValidatorForwardCompatibility() {
        val validator = SchemaValidator()

        // Same schema should be forward compatible with itself
        assertTrue(validator.isForwardCompatible(OrderPlaced.SCHEMA, OrderPlaced.SCHEMA))
        assertTrue(validator.isForwardCompatible(OrderUpdated.SCHEMA, OrderUpdated.SCHEMA))
    }

    @Test
    fun testSchemaValidatorFullCompatibility() {
        val validator = SchemaValidator()

        // Same schema should be fully compatible with itself
        assertTrue(validator.isFullyCompatible(OrderPlaced.SCHEMA, OrderPlaced.SCHEMA))
        assertTrue(validator.isFullyCompatible(OrderUpdated.SCHEMA, OrderUpdated.SCHEMA))
        assertTrue(validator.isFullyCompatible(Address.SCHEMA, Address.SCHEMA))
    }

    @Test
    fun testSchemaValidatorCheckCompatibility() {
        val validator = SchemaValidator()

        val result = validator.checkCompatibility(OrderPlaced.SCHEMA, OrderPlaced.SCHEMA)
        assertNotNull(result)
        assertEquals(
            org.apache.avro.SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE,
            result.type
        )
    }

    @Test
    fun testSchemaValidatorGetMissingFields() {
        val validator = SchemaValidator()

        // Same schema should have no missing fields
        val missingFields = validator.getMissingFields(OrderPlaced.SCHEMA, OrderPlaced.SCHEMA)
        assertNotNull(missingFields)
        assertTrue("Same schema should have no missing fields", missingFields.isEmpty())
    }

    @Test
    fun testSchemaValidatorGetSchemaByName() {
        val validator = SchemaValidator()

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
    fun testSchemaValidatorValidateRequiredFields() {
        val validator = SchemaValidator()

        // Should validate required fields (currently returns true)
        assertTrue(validator.validateRequiredFields(OrderPlaced.SCHEMA))
        assertTrue(validator.validateRequiredFields(Address.SCHEMA))
    }

    // ========== Complex Union Types Tests (Feature 3) ==========

    @Test
    fun testComplexUnionTypeStringOrIntOrBoolean() {
        // Test creating union values with different types
        val stringValue = StringOrIntOrBoolean.of("hello")
        val intValue = StringOrIntOrBoolean.of(42)
        val boolValue = StringOrIntOrBoolean.of(true)

        // Test isXxx methods
        assertTrue(stringValue.isString())
        assertFalse(stringValue.isInt())
        assertFalse(stringValue.isBoolean())

        assertTrue(intValue.isInt())
        assertFalse(intValue.isString())
        assertFalse(intValue.isBoolean())

        assertTrue(boolValue.isBoolean())
        assertFalse(boolValue.isString())
        assertFalse(boolValue.isInt())

        // Test asXxx methods
        assertEquals("hello", stringValue.asString())
        assertEquals(42, intValue.asInt())
        assertEquals(true, boolValue.asBoolean())
    }

    @Test
    fun testComplexUnionTypeThrowsOnWrongType() {
        val stringValue = StringOrIntOrBoolean.of("hello")

        try {
            stringValue.asInt()
            fail("Expected UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            // Expected
        }

        try {
            stringValue.asBoolean()
            fail("Expected UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            // Expected
        }
    }

    @Test
    fun testDynamicValueWithComplexUnions() {
        // Test with string value
        val withString = DynamicValue(
            id = "id-1",
            value = StringOrIntOrBoolean.of("test-string"),
            optionalValue = null
        )

        val record1 = withString.toGenericRecord()
        val deserialized1 = DynamicValue.fromGenericRecord(record1)

        assertEquals("id-1", deserialized1.id)
        assertTrue(deserialized1.value.isString())
        assertEquals("test-string", deserialized1.value.asString())
        assertNull(deserialized1.optionalValue)

        // Test with int value
        val withInt = DynamicValue(
            id = "id-2",
            value = StringOrIntOrBoolean.of(123),
            optionalValue = StringOrLong.of(456L)
        )

        val record2 = withInt.toGenericRecord()
        val deserialized2 = DynamicValue.fromGenericRecord(record2)

        assertEquals("id-2", deserialized2.id)
        assertTrue(deserialized2.value.isInt())
        assertEquals(123, deserialized2.value.asInt())
        assertNotNull(deserialized2.optionalValue)
        assertTrue(deserialized2.optionalValue!!.isLong())
        assertEquals(456L, deserialized2.optionalValue!!.asLong())

        // Test with boolean value and optional string
        val withBool = DynamicValue(
            id = "id-3",
            value = StringOrIntOrBoolean.of(false),
            optionalValue = StringOrLong.of("optional-str")
        )

        val record3 = withBool.toGenericRecord()
        val deserialized3 = DynamicValue.fromGenericRecord(record3)

        assertEquals("id-3", deserialized3.id)
        assertTrue(deserialized3.value.isBoolean())
        assertEquals(false, deserialized3.value.asBoolean())
        assertNotNull(deserialized3.optionalValue)
        assertTrue(deserialized3.optionalValue!!.isString())
        assertEquals("optional-str", deserialized3.optionalValue!!.asString())
    }

    @Test
    fun testDynamicValueKafkaRoundTrip() {
        if (!kafkaAvailable) {
            println("Skipping Kafka test - Kafka not available")
            return
        }

        val topicName = "dynamic-value-test-kotlin-$TEST_RUN_ID"
        createTopicIfNotExists(topicName)

        val original = DynamicValue(
            id = "kafka-id",
            value = StringOrIntOrBoolean.of(999),
            optionalValue = StringOrLong.of("kafka-string")
        )

        val serialized = serializeGenericRecord(original.toGenericRecord(), DynamicValue.SCHEMA)

        createProducer().use { producer ->
            producer.send(ProducerRecord(topicName, original.id, serialized)).get()
            producer.flush()
        }

        createConsumer().use { consumer ->
            consumer.subscribe(listOf(topicName))

            val records = consumer.poll(Duration.ofSeconds(10))
            assertFalse("Should receive at least one record", records.isEmpty)

            val received = records.iterator().next()
            val genericRecord = deserializeGenericRecord(received.value(), DynamicValue.SCHEMA)
            val deserialized = DynamicValue.fromGenericRecord(genericRecord)

            assertEquals(original.id, deserialized.id)
            assertTrue(deserialized.value.isInt())
            assertEquals(999, deserialized.value.asInt())
            assertNotNull(deserialized.optionalValue)
            assertEquals("kafka-string", deserialized.optionalValue!!.asString())
        }
    }

    // ========== Avro $ref Support Tests (Feature 5) ==========

    @Test
    fun testInvoiceWithMoneyRef() {
        val total = Money(Decimal18_4.unsafeForce(BigDecimal("1234.5678")), "USD")
        val original = Invoice(
            invoiceId = UUID.randomUUID(),
            customerId = 12345L,
            total = total,
            issuedAt = Instant.now()
        )

        val record = original.toGenericRecord()
        val deserialized = Invoice.fromGenericRecord(record)

        assertEquals(original.invoiceId, deserialized.invoiceId)
        assertEquals(original.customerId, deserialized.customerId)
        assertEquals(original.total.amount, deserialized.total.amount)
        assertEquals(original.total.currency, deserialized.total.currency)
        assertEquals(original.issuedAt.toEpochMilli(), deserialized.issuedAt.toEpochMilli())
    }

    @Test
    fun testMoneyStandalone() {
        val original = Money(Decimal18_4.unsafeForce(BigDecimal("99999.9999")), "EUR")

        val record = original.toGenericRecord()
        val deserialized = Money.fromGenericRecord(record)

        assertEquals(original.amount, deserialized.amount)
        assertEquals(original.currency, deserialized.currency)
    }

    @Test
    fun testInvoiceKafkaRoundTrip() {
        if (!kafkaAvailable) {
            println("Skipping Kafka test - Kafka not available")
            return
        }

        val topicName = "invoice-test-kotlin-$TEST_RUN_ID"
        createTopicIfNotExists(topicName)

        val total = Money(Decimal18_4.unsafeForce(BigDecimal("5000.00")), "GBP")
        val original = Invoice(
            invoiceId = UUID.randomUUID(),
            customerId = 67890L,
            total = total,
            issuedAt = Instant.now()
        )

        val serialized = serializeGenericRecord(original.toGenericRecord(), Invoice.SCHEMA)

        createProducer().use { producer ->
            producer.send(ProducerRecord(topicName, original.invoiceId.toString(), serialized)).get()
            producer.flush()
        }

        createConsumer().use { consumer ->
            consumer.subscribe(listOf(topicName))

            val records = consumer.poll(Duration.ofSeconds(10))
            assertFalse("Should receive at least one record", records.isEmpty)

            val received = records.iterator().next()
            val genericRecord = deserializeGenericRecord(received.value(), Invoice.SCHEMA)
            val deserialized = Invoice.fromGenericRecord(genericRecord)

            assertEquals(original.invoiceId, deserialized.invoiceId)
            assertEquals(original.customerId, deserialized.customerId)
            assertEquals(original.total.amount, deserialized.total.amount)
            assertEquals("GBP", deserialized.total.currency)
        }
    }

    // ========== Topics/TypedTopic Tests (Feature 1 - Key Schemas) ==========

    @Test
    fun testTopicsConstantsExist() {
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
    fun testTypedTopicProperties() {
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
    fun testTypedTopicSerdeRoundTrip() {
        if (!schemaRegistryAvailable) {
            println("Skipping Schema Registry test - Schema Registry not available")
            return
        }

        // Configure the serde with Schema Registry
        val config = mutableMapOf<String, Any>("schema.registry.url" to SCHEMA_REGISTRY_URL)

        val serializer = Topics.ADDRESS.valueSerde.serializer()
        val deserializer = Topics.ADDRESS.valueSerde.deserializer()
        serializer.configure(config, false)
        deserializer.configure(config, false)

        val original = Address("123 Test St", "TestCity", "12345", "US")

        val topicName = "serde-test-address-kotlin-$TEST_RUN_ID"
        val serialized = serializer.serialize(topicName, original)
        val deserialized = deserializer.deserialize(topicName, serialized)

        assertEquals(original.street, deserialized.street)
        assertEquals(original.city, deserialized.city)
        assertEquals(original.postalCode, deserialized.postalCode)
        assertEquals(original.country, deserialized.country)
    }

    @Test
    fun testTypedTopicDynamicValueSerde() {
        if (!schemaRegistryAvailable) {
            println("Skipping Schema Registry test - Schema Registry not available")
            return
        }

        // Configure the serde with Schema Registry
        val config = mutableMapOf<String, Any>("schema.registry.url" to SCHEMA_REGISTRY_URL)

        val serializer = Topics.DYNAMIC_VALUE.valueSerde.serializer()
        val deserializer = Topics.DYNAMIC_VALUE.valueSerde.deserializer()
        serializer.configure(config, false)
        deserializer.configure(config, false)

        val original = DynamicValue(
            id = "serde-test",
            value = StringOrIntOrBoolean.of("value"),
            optionalValue = StringOrLong.of(100L)
        )

        val topicName = "serde-test-dynamic-value-kotlin-$TEST_RUN_ID"
        val serialized = serializer.serialize(topicName, original)
        val deserialized = deserializer.deserialize(topicName, serialized)

        assertEquals(original.id, deserialized.id)
        assertTrue(deserialized.value.isString())
        assertEquals("value", deserialized.value.asString())
        assertNotNull(deserialized.optionalValue)
        assertEquals(100L, deserialized.optionalValue!!.asLong())
    }

    @Test
    fun testTypedTopicInvoiceSerde() {
        if (!schemaRegistryAvailable) {
            println("Skipping Schema Registry test - Schema Registry not available")
            return
        }

        // Configure the serde with Schema Registry
        val config = mutableMapOf<String, Any>("schema.registry.url" to SCHEMA_REGISTRY_URL)

        val serializer = Topics.INVOICE.valueSerde.serializer()
        val deserializer = Topics.INVOICE.valueSerde.deserializer()
        serializer.configure(config, false)
        deserializer.configure(config, false)

        val total = Money(Decimal18_4.unsafeForce(BigDecimal("250.00")), "CAD")
        val original = Invoice(
            invoiceId = UUID.randomUUID(),
            customerId = 11111L,
            total = total,
            issuedAt = Instant.now()
        )

        val topicName = "serde-test-invoice-kotlin-$TEST_RUN_ID"
        val serialized = serializer.serialize(topicName, original)
        val deserialized = deserializer.deserialize(topicName, serialized)

        assertEquals(original.invoiceId, deserialized.invoiceId)
        assertEquals(original.customerId, deserialized.customerId)
        assertEquals(original.total.amount, deserialized.total.amount)
        assertEquals(original.total.currency, deserialized.total.currency)
    }

    // ========== Recursive Type Tests ==========

    @Test
    fun testTreeNodeSimpleRoundTrip() {
        // Test a leaf node (no children)
        val leaf = TreeNode(
            value = "leaf",
            left = null,
            right = null
        )

        val record = leaf.toGenericRecord()
        val deserialized = TreeNode.fromGenericRecord(record)

        assertEquals("leaf", deserialized.value)
        assertNull(deserialized.left)
        assertNull(deserialized.right)
    }

    @Test
    fun testTreeNodeRecursiveRoundTrip() {
        // Test a tree with children
        val leftChild = TreeNode("left", null, null)
        val rightChild = TreeNode("right", null, null)
        val root = TreeNode(
            value = "root",
            left = leftChild,
            right = rightChild
        )

        val record = root.toGenericRecord()
        val deserialized = TreeNode.fromGenericRecord(record)

        assertEquals("root", deserialized.value)
        assertNotNull(deserialized.left)
        assertNotNull(deserialized.right)
        assertEquals("left", deserialized.left!!.value)
        assertEquals("right", deserialized.right!!.value)
        assertNull(deserialized.left!!.left)
        assertNull(deserialized.left!!.right)
    }

    @Test
    fun testTreeNodeDeeplyNested() {
        // Test a deeply nested tree (4 levels)
        val level4 = TreeNode("level4", null, null)
        val level3 = TreeNode("level3", level4, null)
        val level2 = TreeNode("level2", level3, null)
        val root = TreeNode("root", level2, null)

        val record = root.toGenericRecord()
        val deserialized = TreeNode.fromGenericRecord(record)

        assertEquals("root", deserialized.value)
        assertEquals("level2", deserialized.left!!.value)
        assertEquals("level3", deserialized.left!!.left!!.value)
        assertEquals("level4", deserialized.left!!.left!!.left!!.value)
        assertNull(deserialized.left!!.left!!.left!!.left)
    }

    @Test
    fun testLinkedListNodeSimpleRoundTrip() {
        // Test a single node
        val node = LinkedListNode(
            value = 42,
            next = null
        )

        val record = node.toGenericRecord()
        val deserialized = LinkedListNode.fromGenericRecord(record)

        assertEquals(42, deserialized.value)
        assertNull(deserialized.next)
    }

    @Test
    fun testLinkedListNodeChainRoundTrip() {
        // Test a chain of 3 nodes
        val node3 = LinkedListNode(3, null)
        val node2 = LinkedListNode(2, node3)
        val node1 = LinkedListNode(1, node2)

        val record = node1.toGenericRecord()
        val deserialized = LinkedListNode.fromGenericRecord(record)

        assertEquals(1, deserialized.value)
        assertNotNull(deserialized.next)
        assertEquals(2, deserialized.next!!.value)
        assertNotNull(deserialized.next!!.next)
        assertEquals(3, deserialized.next!!.next!!.value)
        assertNull(deserialized.next!!.next!!.next)
    }

    @Test
    fun testTreeNodeKafkaRoundTrip() {
        if (!kafkaAvailable) {
            println("Skipping Kafka test - Kafka not available")
            return
        }

        val topicName = "tree-node-test-kotlin-$TEST_RUN_ID"
        createTopicIfNotExists(topicName)

        val leftChild = TreeNode("left-child", null, null)
        val rightChild = TreeNode("right-child", null, null)
        val original = TreeNode("root-node", leftChild, rightChild)

        val serialized = serializeGenericRecord(original.toGenericRecord(), TreeNode.SCHEMA)

        createProducer().use { producer ->
            producer.send(ProducerRecord(topicName, "tree-key", serialized)).get()
            producer.flush()
        }

        createConsumer().use { consumer ->
            consumer.subscribe(listOf(topicName))

            val records = consumer.poll(Duration.ofSeconds(10))
            assertFalse("Should receive at least one record", records.isEmpty)

            val received = records.iterator().next()
            val genericRecord = deserializeGenericRecord(received.value(), TreeNode.SCHEMA)
            val deserialized = TreeNode.fromGenericRecord(genericRecord)

            assertEquals(original.value, deserialized.value)
            assertNotNull(deserialized.left)
            assertNotNull(deserialized.right)
            assertEquals("left-child", deserialized.left!!.value)
            assertEquals("right-child", deserialized.right!!.value)
        }
    }

    @Test
    fun testLinkedListNodeKafkaRoundTrip() {
        if (!kafkaAvailable) {
            println("Skipping Kafka test - Kafka not available")
            return
        }

        val topicName = "linked-list-test-kotlin-$TEST_RUN_ID"
        createTopicIfNotExists(topicName)

        val node3 = LinkedListNode(300, null)
        val node2 = LinkedListNode(200, node3)
        val original = LinkedListNode(100, node2)

        val serialized = serializeGenericRecord(original.toGenericRecord(), LinkedListNode.SCHEMA)

        createProducer().use { producer ->
            producer.send(ProducerRecord(topicName, "list-key", serialized)).get()
            producer.flush()
        }

        createConsumer().use { consumer ->
            consumer.subscribe(listOf(topicName))

            val records = consumer.poll(Duration.ofSeconds(10))
            assertFalse("Should receive at least one record", records.isEmpty)

            val received = records.iterator().next()
            val genericRecord = deserializeGenericRecord(received.value(), LinkedListNode.SCHEMA)
            val deserialized = LinkedListNode.fromGenericRecord(genericRecord)

            assertEquals(100, deserialized.value)
            assertNotNull(deserialized.next)
            assertEquals(200, deserialized.next!!.value)
            assertNotNull(deserialized.next!!.next)
            assertEquals(300, deserialized.next!!.next!!.value)
            assertNull(deserialized.next!!.next!!.next)
        }
    }

    private fun createTopicIfNotExists(topicName: String) {
        val props = Properties().apply {
            put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS)
        }

        AdminClient.create(props).use { admin ->
            val existingTopics = admin.listTopics().names().get()
            if (!existingTopics.contains(topicName)) {
                val newTopic = NewTopic(topicName, 1, 1.toShort())
                admin.createTopics(listOf(newTopic)).all().get()
            }
        }
    }

    private fun createProducer(): KafkaProducer<String, ByteArray> {
        val props = Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer::class.java.name)
            put(ProducerConfig.ACKS_CONFIG, "all")
        }
        return KafkaProducer(props)
    }

    private fun createConsumer(): KafkaConsumer<String, ByteArray> {
        val props = Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS)
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer::class.java.name)
            put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-${UUID.randomUUID()}")
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
        }
        return KafkaConsumer(props)
    }

    private fun serializeGenericRecord(record: GenericRecord, schema: org.apache.avro.Schema): ByteArray {
        val out = ByteArrayOutputStream()
        val encoder = EncoderFactory.get().binaryEncoder(out, null)
        val writer = GenericDatumWriter<GenericRecord>(schema)
        writer.write(record, encoder)
        encoder.flush()
        return out.toByteArray()
    }

    private fun deserializeGenericRecord(data: ByteArray, schema: org.apache.avro.Schema): GenericRecord {
        val decoder = DecoderFactory.get().binaryDecoder(data, null)
        val reader = GenericDatumReader<GenericRecord>(schema)
        return reader.read(null, decoder)
    }
}

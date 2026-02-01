---
title: Quick Start
---

# Quick Start

Get up and running with Typr Events in three steps.

## Step 1: Define Your Schemas

Create a `schemas/` directory with your Avro schemas:

```
schemas/
├── order-events/              # Directory = sealed interface
│   ├── OrderPlaced.avsc
│   ├── OrderUpdated.avsc
│   └── OrderCancelled.avsc
├── common/
│   └── Money.avsc             # Shared types
├── Address.avsc               # Standalone record
└── UserService.avpr           # RPC protocol
```

**Key insight:** Schemas in a subdirectory are grouped into a sealed interface. This is how you model topics with multiple event types.

**Example schema** (`schemas/order-events/OrderPlaced.avsc`):

```json
{
  "type": "record",
  "name": "OrderPlaced",
  "namespace": "com.example.events",
  "doc": "Emitted when a customer places an order",
  "fields": [
    {
      "name": "orderId",
      "type": {"type": "string", "logicalType": "uuid"},
      "doc": "Unique order identifier"
    },
    {
      "name": "customerId",
      "type": "long",
      "doc": "Customer who placed the order"
    },
    {
      "name": "totalAmount",
      "type": {
        "type": "bytes",
        "logicalType": "decimal",
        "precision": 10,
        "scale": 2
      },
      "doc": "Order total in dollars"
    },
    {
      "name": "placedAt",
      "type": {"type": "long", "logicalType": "timestamp-millis"},
      "doc": "When the order was placed"
    },
    {
      "name": "items",
      "type": {"type": "array", "items": "string"},
      "doc": "List of item SKUs"
    },
    {
      "name": "shippingAddress",
      "type": ["null", "string"],
      "default": null,
      "doc": "Optional shipping address override"
    }
  ]
}
```

## Step 2: Run the Generator

```scala
//> using dep "dev.typr::typo:0.31.0"
//> using scala "3.4.2"

import typr.avro.*
import typr.jvm
import typr.internal.codegen.LangJava
import java.nio.file.{Files, Path}

val options = AvroOptions.default(
  pkg = jvm.QIdent.parse("com.example.events"),
  schemaSource = SchemaSource.Directory(Path.of("schemas"))
).copy(
  generateSchemaValidator = true,
  enablePreciseTypes = true,
  headerSchemas = Map(
    "standard" -> HeaderSchema(List(
      HeaderField("correlationId", HeaderType.UUID, required = true),
      HeaderField("timestamp", HeaderType.Instant, required = true),
      HeaderField("source", HeaderType.String, required = false)
    ))
  ),
  defaultHeaderSchema = Some("standard")
)

val result = AvroCodegen.generate(options, LangJava)

result.files.foreach { file =>
  val path = Path.of("src/main/java").resolve(file.path)
  Files.createDirectories(path.getParent)
  Files.writeString(path, file.content)
}

println(s"Generated ${result.files.size} files")
```

## Step 3: Use the Generated Code

### Producing Events

```java
// Create typed producer
var props = new Properties();
props.put("bootstrap.servers", "localhost:9092");
props.put("schema.registry.url", "http://localhost:8081");

var producer = new KafkaProducer<>(props,
    new StringSerializer(),
    Topics.ORDER_EVENTS.valueSerde().serializer());

var typedProducer = new OrderEventsProducer(producer, Topics.ORDER_EVENTS.name());

// Send with full type safety
var event = new OrderPlaced(
    UUID.randomUUID(),
    12345L,
    Decimal10_2.unsafeForce(new BigDecimal("99.99")),
    Instant.now(),
    List.of("SKU-001", "SKU-002"),
    Optional.empty()
);

var headers = new StandardHeaders(
    UUID.randomUUID(),
    Instant.now(),
    Optional.of("order-service")
);

typedProducer.send("order-123", event, headers).get();
```

### Consuming Events

```java
// Implement handler interface - compiler ensures you handle all event types
class OrderHandler implements OrderEventsHandler {
    @Override
    public void handleOrderPlaced(String key, OrderPlaced event, StandardHeaders headers) {
        System.out.printf("Order %s placed by customer %d for %s%n",
            event.orderId(), event.customerId(), event.totalAmount().decimalValue());
    }

    @Override
    public void handleOrderUpdated(String key, OrderUpdated event, StandardHeaders headers) {
        System.out.printf("Order %s updated to status %s%n",
            event.orderId(), event.newStatus());
    }

    @Override
    public void handleOrderCancelled(String key, OrderCancelled event, StandardHeaders headers) {
        System.out.printf("Order %s cancelled: %s%n",
            event.orderId(), event.reason());
    }
}

// Create consumer
var props = new Properties();
props.put("bootstrap.servers", "localhost:9092");
props.put("schema.registry.url", "http://localhost:8081");
props.put("group.id", "order-processor");
props.put("auto.offset.reset", "earliest");

var consumer = new KafkaConsumer<>(props,
    new StringDeserializer(),
    Topics.ORDER_EVENTS.valueSerde().deserializer());

var typedConsumer = new OrderEventsConsumer(
    consumer, new OrderHandler(), Topics.ORDER_EVENTS.name());

consumer.subscribe(List.of(Topics.ORDER_EVENTS.name()));

// Poll loop - events automatically dispatched to correct handler method
while (running) {
    typedConsumer.poll(Duration.ofMillis(100));
}
```

## Dependencies

### Maven

```xml
<repositories>
    <repository>
        <id>confluent</id>
        <url>https://packages.confluent.io/maven/</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>org.apache.avro</groupId>
        <artifactId>avro</artifactId>
        <version>1.12.0</version>
    </dependency>
    <dependency>
        <groupId>org.apache.kafka</groupId>
        <artifactId>kafka-clients</artifactId>
        <version>3.9.0</version>
    </dependency>
    <dependency>
        <groupId>io.confluent</groupId>
        <artifactId>kafka-avro-serializer</artifactId>
        <version>7.8.0</version>
    </dependency>
</dependencies>
```

### Gradle (Kotlin)

```kotlin
repositories {
    mavenCentral()
    maven("https://packages.confluent.io/maven/")
}

dependencies {
    implementation("org.apache.avro:avro:1.12.0")
    implementation("org.apache.kafka:kafka-clients:3.9.0")
    implementation("io.confluent:kafka-avro-serializer:7.8.0")
}
```

### SBT (Scala)

```scala
resolvers += "Confluent" at "https://packages.confluent.io/maven/"

libraryDependencies ++= Seq(
    "org.apache.avro" % "avro" % "1.12.0",
    "org.apache.kafka" % "kafka-clients" % "3.9.0",
    "io.confluent" % "kafka-avro-serializer" % "7.8.0"
)
```

## Docker Compose

```yaml
services:
  kafka:
    image: confluentinc/cp-kafka:7.8.0
    hostname: kafka
    ports:
      - 9092:9092
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,EXTERNAL:PLAINTEXT
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:29092,CONTROLLER://0.0.0.0:9094,EXTERNAL://0.0.0.0:9092
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,EXTERNAL://localhost:9092
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9094
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
      CLUSTER_ID: typr-kafka-cluster

  schema-registry:
    image: confluentinc/cp-schema-registry:7.8.0
    hostname: schema-registry
    depends_on:
      kafka:
        condition: service_healthy
    ports:
      - 8081:8081
    environment:
      SCHEMA_REGISTRY_HOST_NAME: schema-registry
      SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS: kafka:29092
      SCHEMA_REGISTRY_LISTENERS: http://0.0.0.0:8081
```

```bash
docker-compose up -d kafka schema-registry
```

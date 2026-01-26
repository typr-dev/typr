---
title: Kafka/Avro Code Generation
---

# Kafka/Avro Code Generation

Typr generates type-safe code for Apache Kafka and Avro schemas. No more `Object` types, no more manual casting, no more runtime surprises.

## Feature Highlights

| Feature | Description |
|---------|-------------|
| **Immutable Records** | Java records, Kotlin data classes, Scala case classes |
| **Wrapper Types** | Type-safe wrappers via `x-typr-wrapper` field annotations |
| **Multi-Event Topics** | Sealed interfaces for topics with multiple event types |
| **Complex Unions** | Type-safe sealed interfaces for `["string", "int", "boolean"]` unions |
| **Recursive Types** | Self-referencing records like tree nodes and linked lists |
| **Avro $ref** | Cross-file schema references with `{"$ref": "./path/to/Schema.avsc"}` |
| **Precise Types** | Compile-time constraint validation with `Decimal10_2`, `Fixed16`, etc. |
| **Schema Evolution** | Versioned types (`OrderV1`, `OrderV2`) and migration helpers from Schema Registry |
| **Typed Headers** | Strongly-typed Kafka headers with custom schemas |
| **Protocol Support** | RPC-style service interfaces from `.avpr` files |
| **Wire Formats** | Confluent Schema Registry or vanilla Avro binary encoding |
| **Effect Types** | Blocking, CompletableFuture, Cats IO, ZIO, Reactor Mono |

## Why Not Just Use the Avro Maven Plugin?

The standard Avro tooling generates classes like this:

```java
// What Avro Maven Plugin generates
public class OrderPlaced extends SpecificRecordBase {
  private Object orderId;        // Actually a UUID, but typed as Object
  private Object customerId;     // Actually a Long
  private Object totalAmount;    // Actually a BigDecimal
  private Object items;          // Actually a List<String>

  public Object get(int field) { ... }  // No type safety
  public void put(int field, Object value) { ... }  // Mutation everywhere

  // Builder pattern with mutable state
  public static Builder newBuilder() { ... }
}

// Usage is painful
OrderPlaced order = OrderPlaced.newBuilder()
    .setOrderId(uuid)  // Hope you got the type right!
    .setCustomerId(123L)  // Will fail at runtime if wrong
    .build();

Object value = order.get(0);  // What type is this?
UUID id = (UUID) value;  // Runtime cast, might fail
```

**Typr generates this instead:**

```java
// What Typr generates
public record OrderPlaced(
    UUID orderId,
    Long customerId,
    BigDecimal totalAmount,
    List<String> items,
    Optional<String> shippingAddress
) implements OrderEvents {
    // Immutable, all fields properly typed

    // Wither methods for immutable updates
    public OrderPlaced withOrderId(UUID orderId) { ... }

    // Type-safe serialization
    public GenericRecord toGenericRecord() { ... }
    public static OrderPlaced fromGenericRecord(GenericRecord record) { ... }
}

// Usage is clean and type-safe
OrderPlaced order = new OrderPlaced(
    UUID.randomUUID(),
    123L,
    new BigDecimal("99.99"),
    List.of("item-1", "item-2"),
    Optional.empty()
);

UUID id = order.orderId();  // No casting needed
```

## The Problem with Kafka Boilerplate

A typical Kafka setup requires:

1. **Schema definitions** (`.avsc` files) - you write these
2. **Generated classes** - Avro plugin generates ugly mutable classes
3. **Wrapper classes** - you write these to make the generated classes usable
4. **Serializers/Deserializers** - you configure and wire these up
5. **Producer setup** - boilerplate to create and configure producers
6. **Consumer setup** - boilerplate for consumers with manual type dispatch
7. **Header handling** - manual byte array conversion for every header

Typr generates steps 2-7 from step 1. You write schemas, run the generator, get clean code.

---

## Quick Start

### Step 1: Define Your Schemas

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

Example schema (`schemas/order-events/OrderPlaced.avsc`):

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
    },
    {
      "name": "metadata",
      "type": ["null", "string", "long", "boolean"],
      "default": null,
      "doc": "Flexible metadata field - generates StringOrLongOrBoolean union type"
    }
  ]
}
```

### Step 2: Run the Generator

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

### Step 3: Use the Generated Code

**Producing events:**

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
    Decimal10_2.unsafeForce(new BigDecimal("99.99")),  // Precise type!
    Instant.now(),
    List.of("SKU-001", "SKU-002"),
    Optional.empty(),
    Optional.of(StringOrLongOrBoolean.of("promo-code-123"))  // Complex union!
);

var headers = new StandardHeaders(
    UUID.randomUUID(),
    Instant.now(),
    Optional.of("order-service")
);

typedProducer.send("order-123", event, headers).get();
```

**Consuming events:**

```java
// Implement handler interface - compiler ensures you handle all event types
class OrderHandler implements OrderEventsHandler {
    @Override
    public void handleOrderPlaced(String key, OrderPlaced event, StandardHeaders headers) {
        System.out.printf("Order %s placed by customer %d for %s%n",
            event.orderId(), event.customerId(), event.totalAmount().decimalValue());

        // Handle the complex union
        event.metadata().ifPresent(meta -> {
            if (meta.isString()) {
                System.out.println("Promo code: " + meta.asString());
            } else if (meta.isLong()) {
                System.out.println("Reference ID: " + meta.asLong());
            }
        });
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

---

## Generated Code Reference

### Record Classes

Every Avro record generates an immutable data class:

```java
// Java - record with all fields properly typed
public record OrderPlaced(
    UUID orderId,
    Long customerId,
    BigDecimal totalAmount,       // Or Decimal10_2 with precise types
    Instant placedAt,
    List<String> items,
    Optional<String> shippingAddress
) implements OrderEvents {

    // Wither methods for immutable updates
    public OrderPlaced withOrderId(UUID orderId) {
        return new OrderPlaced(orderId, this.customerId, this.totalAmount,
            this.placedAt, this.items, this.shippingAddress);
    }
    // ... withers for all fields

    // Serialization
    public GenericRecord toGenericRecord() {
        var record = new GenericData.Record(SCHEMA);
        record.put("orderId", orderId.toString());
        record.put("customerId", customerId);
        record.put("totalAmount",
            ByteBuffer.wrap(totalAmount.unscaledValue().toByteArray()));
        record.put("placedAt", placedAt.toEpochMilli());
        record.put("items", items);
        record.put("shippingAddress", shippingAddress.orElse(null));
        return record;
    }

    // Deserialization
    public static OrderPlaced fromGenericRecord(GenericRecord record) {
        return new OrderPlaced(
            UUID.fromString(record.get("orderId").toString()),
            (Long) record.get("customerId"),
            new BigDecimal(
                new java.math.BigInteger(
                    ((ByteBuffer) record.get("totalAmount")).array()),
                2  // scale from schema
            ),
            Instant.ofEpochMilli((Long) record.get("placedAt")),
            ((List<?>) record.get("items")).stream()
                .map(Object::toString)
                .collect(Collectors.toList()),
            Optional.ofNullable((CharSequence) record.get("shippingAddress"))
                .map(CharSequence::toString)
        );
    }

    // Embedded schema
    public static final Schema SCHEMA = new Schema.Parser().parse("""
        {"type":"record","name":"OrderPlaced",...}
        """);
}
```

```kotlin
// Kotlin - data class with nullable types for optionals
data class OrderPlaced(
    val orderId: UUID,
    val customerId: Long,
    val totalAmount: BigDecimal,
    val placedAt: Instant,
    val items: List<String>,
    val shippingAddress: String?    // Nullable instead of Optional
) : OrderEvents {

    fun toGenericRecord(): GenericRecord { ... }

    companion object {
        val SCHEMA: Schema = ...
        fun fromGenericRecord(record: GenericRecord): OrderPlaced { ... }
    }
}
```

```scala
// Scala - case class with Option types
case class OrderPlaced(
    orderId: UUID,
    customerId: Long,
    totalAmount: BigDecimal,
    placedAt: Instant,
    items: List[String],
    shippingAddress: Option[String]
) extends OrderEvents {

    def toGenericRecord: GenericRecord = { ... }
}

object OrderPlaced {
    val SCHEMA: Schema = ...
    def fromGenericRecord(record: GenericRecord): OrderPlaced = { ... }
}
```

### Sealed Interfaces for Multi-Event Topics

When schemas are in a subdirectory, a sealed interface unifies them:

```java
// Generated from schemas/order-events/
public sealed interface OrderEvents
    permits OrderPlaced, OrderUpdated, OrderCancelled {

    // Common serialization interface
    GenericRecord toGenericRecord();

    // Type-safe deserialization with automatic dispatch
    static OrderEvents fromGenericRecord(GenericRecord record) {
        String schemaName = record.getSchema().getFullName();
        return switch (schemaName) {
            case "com.example.events.OrderPlaced" ->
                OrderPlaced.fromGenericRecord(record);
            case "com.example.events.OrderUpdated" ->
                OrderUpdated.fromGenericRecord(record);
            case "com.example.events.OrderCancelled" ->
                OrderCancelled.fromGenericRecord(record);
            default -> throw new IllegalArgumentException(
                "Unknown schema: " + schemaName);
        };
    }
}
```

```scala
// Scala sealed trait
sealed trait OrderEvents {
    def toGenericRecord: GenericRecord
}

object OrderEvents {
    def fromGenericRecord(record: GenericRecord): OrderEvents = {
        record.getSchema.getFullName match {
            case "com.example.events.OrderPlaced" =>
                OrderPlaced.fromGenericRecord(record)
            case "com.example.events.OrderUpdated" =>
                OrderUpdated.fromGenericRecord(record)
            case "com.example.events.OrderCancelled" =>
                OrderCancelled.fromGenericRecord(record)
            case other =>
                throw new IllegalArgumentException(s"Unknown schema: $other")
        }
    }
}
```

### Complex Union Types

Avro unions with multiple non-null types generate sealed interfaces:

**Schema:**
```json
{
  "name": "value",
  "type": ["string", "int", "boolean"],
  "doc": "A flexible value field"
}
```

**Generated:**
```java
public sealed interface StringOrIntOrBoolean {
    // Type checking
    boolean isString();
    boolean isInt();
    boolean isBoolean();

    // Safe extraction (throws if wrong type)
    String asString();
    Integer asInt();
    Boolean asBoolean();

    // Factory methods
    static StringOrIntOrBoolean of(String value) {
        return new StringValue(value);
    }
    static StringOrIntOrBoolean of(int value) {
        return new IntValue(value);
    }
    static StringOrIntOrBoolean of(boolean value) {
        return new BooleanValue(value);
    }

    // Internal implementations
    record StringValue(String value) implements StringOrIntOrBoolean {
        @Override public boolean isString() { return true; }
        @Override public boolean isInt() { return false; }
        @Override public boolean isBoolean() { return false; }
        @Override public String asString() { return value; }
        @Override public Integer asInt() {
            throw new IllegalStateException("Not an int, is string: " + value);
        }
        @Override public Boolean asBoolean() {
            throw new IllegalStateException("Not a boolean, is string: " + value);
        }
    }

    record IntValue(int value) implements StringOrIntOrBoolean { ... }
    record BooleanValue(boolean value) implements StringOrIntOrBoolean { ... }
}
```

**Usage:**
```java
StringOrIntOrBoolean value = StringOrIntOrBoolean.of("hello");

// Pattern matching (Java 21+)
switch (value) {
    case StringOrIntOrBoolean.StringValue(var s) ->
        System.out.println("String: " + s);
    case StringOrIntOrBoolean.IntValue(var i) ->
        System.out.println("Int: " + i);
    case StringOrIntOrBoolean.BooleanValue(var b) ->
        System.out.println("Boolean: " + b);
}

// Or use the accessor methods
if (value.isString()) {
    String s = value.asString();
}
```

**Nullable complex unions** wrap in Optional:
```java
// Schema: ["null", "string", "int"]
// Field type: Optional<StringOrInt>
Optional<StringOrInt> optionalValue = record.value();
optionalValue.ifPresent(v -> {
    if (v.isString()) { ... }
});
```

### Recursive Types

Avro supports self-referencing records for tree structures, linked lists, and other recursive data:

**Schema:**
```json
{
  "type": "record",
  "name": "TreeNode",
  "namespace": "com.example.events",
  "fields": [
    {"name": "value", "type": "string"},
    {"name": "left", "type": ["null", "TreeNode"], "default": null},
    {"name": "right", "type": ["null", "TreeNode"], "default": null}
  ]
}
```

**Generated (Java):**
```java
public record TreeNode(
    String value,
    Optional<TreeNode> left,
    Optional<TreeNode> right
) {
    public static Schema SCHEMA = new Parser().parse(
        "{\"type\": \"record\",\"name\": \"TreeNode\",...}"
    );

    public GenericRecord toGenericRecord() {
        Record record = new Record(TreeNode.SCHEMA);
        record.put("value", this.value());
        record.put("left", this.left().isEmpty() ? null : this.left().get().toGenericRecord());
        record.put("right", this.right().isEmpty() ? null : this.right().get().toGenericRecord());
        return record;
    }

    public static TreeNode fromGenericRecord(GenericRecord record) {
        return new TreeNode(
            record.get("value").toString(),
            Optional.ofNullable(record.get("left") == null ? null
                : TreeNode.fromGenericRecord((GenericRecord) record.get("left"))),
            Optional.ofNullable(record.get("right") == null ? null
                : TreeNode.fromGenericRecord((GenericRecord) record.get("right")))
        );
    }
}
```

**Generated (Kotlin):**
```kotlin
data class TreeNode(
    val value: String,
    val left: TreeNode?,
    val right: TreeNode?
) {
    fun toGenericRecord(): GenericRecord { ... }

    companion object {
        val SCHEMA: Schema = ...
        fun fromGenericRecord(record: GenericRecord): TreeNode { ... }
    }
}
```

**Usage:**
```java
// Build a tree
TreeNode tree = new TreeNode(
    "root",
    Optional.of(new TreeNode("left", Optional.empty(), Optional.empty())),
    Optional.of(new TreeNode("right", Optional.empty(), Optional.empty()))
);

// Serialize
GenericRecord record = tree.toGenericRecord();

// Deserialize
TreeNode restored = TreeNode.fromGenericRecord(record);
```

Recursive types are automatically detected and handled by the code generator:
- The embedded schema JSON uses name references (e.g., `"TreeNode"`) instead of infinitely inlining
- `toGenericRecord()` and `fromGenericRecord()` correctly handle null termination
- Works with linked lists, trees, graphs, and any self-referential structure

### Avro $ref Support

Reference schemas from other files:

```json
// schemas/common/Money.avsc
{
  "type": "record",
  "name": "Money",
  "namespace": "com.example.common",
  "fields": [
    {"name": "amount", "type": {"type": "bytes", "logicalType": "decimal", "precision": 18, "scale": 4}},
    {"name": "currency", "type": "string"}
  ]
}
```

```json
// schemas/Invoice.avsc
{
  "type": "record",
  "name": "Invoice",
  "namespace": "com.example.events",
  "fields": [
    {"name": "invoiceId", "type": {"type": "string", "logicalType": "uuid"}},
    {"name": "lineItems", "type": {"type": "array", "items": {
      "type": "record",
      "name": "LineItem",
      "fields": [
        {"name": "description", "type": "string"},
        {"name": "amount", "type": {"$ref": "../common/Money.avsc"}}
      ]
    }}},
    {"name": "total", "type": {"$ref": "./common/Money.avsc"}}
  ]
}
```

**How $ref resolution works:**

1. Parser encounters `{"$ref": "./common/Money.avsc"}`
2. Path is resolved relative to the containing schema file
3. Referenced schema is loaded and inlined
4. Result is a valid Avro schema with the `Money` type embedded
5. Both `Invoice` and `Money` classes are generated
6. `Invoice.lineItems` and `Invoice.total` are typed as `Money`

**Supported $ref paths:**
- Relative: `{"$ref": "./common/Money.avsc"}`
- Parent directory: `{"$ref": "../shared/Address.avsc"}`
- Subdirectory: `{"$ref": "types/Currency.avsc"}`

**Not supported:**
- Absolute paths
- HTTP URLs
- Circular references

### Precise Types

When `enablePreciseTypes = true`, constrained Avro types generate wrapper classes:

**Schema:**
```json
{
  "name": "totalAmount",
  "type": {"type": "bytes", "logicalType": "decimal", "precision": 10, "scale": 2}
}
```

**Generated:**
```java
public final class Decimal10_2 {
    public static final int PRECISION = 10;
    public static final int SCALE = 2;

    private final BigDecimal value;

    private Decimal10_2(BigDecimal value) {
        this.value = value;
    }

    // Factory that validates constraints
    public static Optional<Decimal10_2> from(BigDecimal value) {
        if (value == null) return Optional.empty();

        // Check precision (total digits)
        int precision = value.precision();
        if (precision > PRECISION) return Optional.empty();

        // Check scale (digits after decimal)
        int scale = value.scale();
        if (scale > SCALE) return Optional.empty();

        // Adjust scale if needed
        BigDecimal adjusted = value.setScale(SCALE, RoundingMode.HALF_UP);
        return Optional.of(new Decimal10_2(adjusted));
    }

    // Factory that throws on invalid values
    public static Decimal10_2 unsafeForce(BigDecimal value) {
        return from(value).orElseThrow(() ->
            new IllegalArgumentException(
                "Value " + value + " exceeds precision " + PRECISION +
                " or scale " + SCALE));
    }

    // Access underlying value
    public BigDecimal decimalValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Decimal10_2 other)) return false;
        return value.compareTo(other.value) == 0;
    }

    @Override
    public int hashCode() {
        return value.stripTrailingZeros().hashCode();
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}
```

**Why use precise types?**

Without precise types:
```java
// Compiles fine, fails at runtime when serializing
var order = new OrderPlaced(
    uuid,
    customerId,
    new BigDecimal("99999999999.99"),  // Exceeds precision 10!
    ...
);
```

With precise types:
```java
// Compiler forces you to acknowledge the constraint
var order = new OrderPlaced(
    uuid,
    customerId,
    Decimal10_2.unsafeForce(new BigDecimal("99.99")),  // Explicit
    ...
);

// Or validate safely
Decimal10_2.from(userInput).ifPresentOrElse(
    amount -> createOrder(amount),
    () -> showError("Amount exceeds maximum precision")
);
```

**Fixed types:**
```java
// For Avro fixed(16) - generates Fixed16
public final class Fixed16 {
    public static final int SIZE = 16;
    private final byte[] value;

    public static Optional<Fixed16> from(byte[] value) {
        if (value == null || value.length != SIZE) return Optional.empty();
        return Optional.of(new Fixed16(value.clone()));
    }

    public byte[] bytes() {
        return value.clone();  // Defensive copy
    }
}
```

### Wrapper Types

Use the `x-typr-wrapper` field annotation to generate type-safe wrapper types for Avro fields. This prevents mixing up semantically different values that have the same underlying type.

**Schema with wrapper annotations:**
```json
{
  "type": "record",
  "name": "CustomerOrder",
  "namespace": "com.example.events",
  "doc": "Order with wrapper types for type-safe IDs",
  "fields": [
    {
      "name": "orderId",
      "type": "string",
      "doc": "Unique order identifier",
      "x-typr-wrapper": "OrderId"
    },
    {
      "name": "customerId",
      "type": "long",
      "doc": "Customer identifier",
      "x-typr-wrapper": "CustomerId"
    },
    {
      "name": "email",
      "type": ["null", "string"],
      "default": null,
      "doc": "Customer email address",
      "x-typr-wrapper": "Email"
    },
    {
      "name": "amount",
      "type": "long",
      "doc": "Order amount in cents (no wrapper)"
    }
  ]
}
```

**Generated wrapper types (Java):**
```java
public record OrderId(@JsonValue String value) {
    public String unwrap() { return this.value(); }
    public static OrderId valueOf(String v) { return new OrderId(v); }
}

public record CustomerId(@JsonValue Long value) {
    public Long unwrap() { return this.value(); }
    public static CustomerId valueOf(Long v) { return new CustomerId(v); }
}

public record Email(@JsonValue String value) {
    public String unwrap() { return this.value(); }
    public static Email valueOf(String v) { return new Email(v); }
}
```

**Generated record using wrapper types:**
```java
public record CustomerOrder(
    OrderId orderId,           // Wrapper type instead of String
    CustomerId customerId,     // Wrapper type instead of Long
    Optional<Email> email,     // Optional wrapper type
    Long amount                // No wrapper - plain Long
) {
    public GenericRecord toGenericRecord() {
        Record record = new Record(CustomerOrder.SCHEMA);
        record.put("orderId", this.orderId().unwrap());
        record.put("customerId", this.customerId().unwrap());
        record.put("email", this.email().isEmpty() ? null : this.email().get().unwrap());
        record.put("amount", this.amount());
        return record;
    }

    public static CustomerOrder fromGenericRecord(GenericRecord record) {
        return new CustomerOrder(
            OrderId.valueOf(record.get("orderId").toString()),
            CustomerId.valueOf((Long) record.get("customerId")),
            record.get("email") == null ? Optional.empty()
                : Optional.of(Email.valueOf(record.get("email").toString())),
            (Long) record.get("amount")
        );
    }
}
```

**Why use wrapper types?**

Without wrapper types:
```java
// Compiles fine, but semantically wrong - customerId passed as orderId!
void processOrder(String orderId, Long customerId) { ... }
processOrder(customerId.toString(), Long.parseLong(orderId));  // Oops!
```

With wrapper types:
```java
// Compiler catches the mistake
void processOrder(OrderId orderId, CustomerId customerId) { ... }
processOrder(orderId, customerId);  // Type-safe
```

**Wrapper types work across all languages:**

```kotlin
// Kotlin
data class OrderId(val value: String) {
    fun unwrap(): String = value
    companion object {
        fun valueOf(v: String): OrderId = OrderId(v)
    }
}
```

```scala
// Scala
case class OrderId(value: String) {
    def unwrap: String = value
}
object OrderId {
    def valueOf(v: String): OrderId = OrderId(v)
}
```

**JSON serialization:**

Wrapper types include `@JsonValue` annotations, so they serialize cleanly to JSON:
```json
{
  "orderId": "order-123",
  "customerId": 42,
  "email": "user@example.com",
  "amount": 9999
}
```

Not as nested objects:
```json
// NOT this
{"orderId": {"value": "order-123"}, ...}
```

### Typed Headers

Configure header schemas for type-safe Kafka headers:

```scala
val options = AvroOptions.default(...).copy(
  headerSchemas = Map(
    "standard" -> HeaderSchema(List(
      HeaderField("correlationId", HeaderType.UUID, required = true),
      HeaderField("timestamp", HeaderType.Instant, required = true),
      HeaderField("source", HeaderType.String, required = false),
      HeaderField("retryCount", HeaderType.Int, required = false)
    )),
    "audit" -> HeaderSchema(List(
      HeaderField("userId", HeaderType.String, required = true),
      HeaderField("action", HeaderType.String, required = true),
      HeaderField("ipAddress", HeaderType.String, required = false)
    ))
  ),
  defaultHeaderSchema = Some("standard"),
  topicHeaders = Map(
    "audit-events" -> "audit"  // Use audit headers for audit topic
  )
)
```

**Generated:**
```java
public record StandardHeaders(
    UUID correlationId,      // required
    Instant timestamp,       // required
    Optional<String> source, // optional
    Optional<Integer> retryCount
) {
    // Convert to Kafka Headers
    public Headers toHeaders() {
        var headers = new RecordHeaders();
        headers.add("correlationId",
            correlationId.toString().getBytes(StandardCharsets.UTF_8));
        headers.add("timestamp",
            ByteBuffer.allocate(8).putLong(timestamp.toEpochMilli()).array());
        source.ifPresent(s ->
            headers.add("source", s.getBytes(StandardCharsets.UTF_8)));
        retryCount.ifPresent(r ->
            headers.add("retryCount", ByteBuffer.allocate(4).putInt(r).array()));
        return headers;
    }

    // Parse from Kafka Headers
    public static StandardHeaders fromHeaders(Headers headers) {
        UUID correlationId = null;
        Instant timestamp = null;
        String source = null;
        Integer retryCount = null;

        for (Header header : headers) {
            switch (header.key()) {
                case "correlationId" -> correlationId =
                    UUID.fromString(new String(header.value(), StandardCharsets.UTF_8));
                case "timestamp" -> timestamp =
                    Instant.ofEpochMilli(ByteBuffer.wrap(header.value()).getLong());
                case "source" -> source =
                    new String(header.value(), StandardCharsets.UTF_8);
                case "retryCount" -> retryCount =
                    ByteBuffer.wrap(header.value()).getInt();
            }
        }

        if (correlationId == null)
            throw new IllegalArgumentException("Missing required header: correlationId");
        if (timestamp == null)
            throw new IllegalArgumentException("Missing required header: timestamp");

        return new StandardHeaders(
            correlationId, timestamp,
            Optional.ofNullable(source), Optional.ofNullable(retryCount));
    }
}
```

### Serializers and Serdes

For each record and sealed interface:

```java
// Individual record serializer (uses Schema Registry)
public class OrderPlacedSerializer implements Serializer<OrderPlaced> {
    private final KafkaAvroSerializer inner = new KafkaAvroSerializer();

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        inner.configure(configs, isKey);
    }

    @Override
    public byte[] serialize(String topic, OrderPlaced data) {
        if (data == null) return null;
        return inner.serialize(topic, data.toGenericRecord());
    }

    @Override
    public void close() {
        inner.close();
    }
}

// Sealed interface serializer (handles all subtypes)
public class OrderEventsSerializer implements Serializer<OrderEvents> {
    private final KafkaAvroSerializer inner = new KafkaAvroSerializer();

    @Override
    public byte[] serialize(String topic, OrderEvents data) {
        if (data == null) return null;
        return inner.serialize(topic, data.toGenericRecord());
    }
    // ...
}

// Serde combines serializer + deserializer
public class OrderEventsSerde implements Serde<OrderEvents> {
    @Override
    public Serializer<OrderEvents> serializer() {
        return new OrderEventsSerializer();
    }

    @Override
    public Deserializer<OrderEvents> deserializer() {
        return new OrderEventsDeserializer();
    }
}
```

**Binary encoding (without Schema Registry):**

When using `AvroWireFormat.BinaryEncoded`:

```java
public class OrderPlacedSerializer implements Serializer<OrderPlaced> {
    private final DatumWriter<GenericRecord> writer =
        new GenericDatumWriter<>(OrderPlaced.SCHEMA);

    @Override
    public byte[] serialize(String topic, OrderPlaced data) {
        if (data == null) return null;
        try {
            var out = new ByteArrayOutputStream();
            var encoder = EncoderFactory.get().binaryEncoder(out, null);
            writer.write(data.toGenericRecord(), encoder);
            encoder.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw new SerializationException("Failed to serialize", e);
        }
    }
}
```

### Producers and Consumers

**Typed Producer:**
```java
public class OrderEventsProducer implements AutoCloseable {
    private final Producer<String, OrderEvents> producer;
    private final String topic;

    public OrderEventsProducer(
            Producer<String, OrderEvents> producer,
            String topic) {
        this.producer = producer;
        this.topic = topic;
    }

    // Send without headers
    public Future<RecordMetadata> send(String key, OrderEvents value) {
        return producer.send(new ProducerRecord<>(topic, key, value));
    }

    // Send with typed headers
    public Future<RecordMetadata> send(
            String key,
            OrderEvents value,
            StandardHeaders headers) {
        var record = new ProducerRecord<>(topic, null, key, value, headers.toHeaders());
        return producer.send(record);
    }

    // Send with callback
    public Future<RecordMetadata> send(
            String key,
            OrderEvents value,
            StandardHeaders headers,
            Callback callback) {
        var record = new ProducerRecord<>(topic, null, key, value, headers.toHeaders());
        return producer.send(record, callback);
    }

    @Override
    public void close() {
        producer.close();
    }
}
```

**Handler Interface:**
```java
public interface OrderEventsHandler {
    // One method per event type - compiler ensures exhaustive handling
    void handleOrderPlaced(String key, OrderPlaced event, StandardHeaders headers);
    void handleOrderUpdated(String key, OrderUpdated event, StandardHeaders headers);
    void handleOrderCancelled(String key, OrderCancelled event, StandardHeaders headers);

    // Override to handle unknown events (new types added later)
    default void handleUnknown(String key, OrderEvents event, StandardHeaders headers) {
        throw new IllegalStateException(
            "Unknown event type: " + event.getClass().getName());
    }
}
```

**Typed Consumer:**
```java
public class OrderEventsConsumer implements Closeable {
    private final Consumer<String, OrderEvents> consumer;
    private final OrderEventsHandler handler;
    private final String topic;

    public OrderEventsConsumer(
            Consumer<String, OrderEvents> consumer,
            OrderEventsHandler handler,
            String topic) {
        this.consumer = consumer;
        this.handler = handler;
        this.topic = topic;
    }

    public void poll(Duration timeout) {
        var records = consumer.poll(timeout);
        for (var record : records) {
            var headers = StandardHeaders.fromHeaders(record.headers());
            var event = record.value();
            var key = record.key();

            // Dispatch to correct handler method
            switch (event) {
                case OrderPlaced e -> handler.handleOrderPlaced(key, e, headers);
                case OrderUpdated e -> handler.handleOrderUpdated(key, e, headers);
                case OrderCancelled e -> handler.handleOrderCancelled(key, e, headers);
                default -> handler.handleUnknown(key, event, headers);
            }
        }
    }

    @Override
    public void close() {
        consumer.close();
    }
}
```

### Topic Bindings

Type-safe topic constants:

```java
public final class Topics {
    private Topics() {}

    public static final TypedTopic<String, OrderEvents> ORDER_EVENTS =
        new TypedTopic<>(
            "order-events",
            new StringSerde(),
            new OrderEventsSerde()
        );

    public static final TypedTopic<String, Address> ADDRESS =
        new TypedTopic<>(
            "address",
            new StringSerde(),
            new AddressSerde()
        );

    // When using SchemaKey for typed keys
    public static final TypedTopic<OrderKey, OrderEvents> ORDER_EVENTS_KEYED =
        new TypedTopic<>(
            "order-events-keyed",
            new OrderKeySerde(),
            new OrderEventsSerde()
        );
}

// TypedTopic record
public record TypedTopic<K, V>(
    String name,
    Serde<K> keySerde,
    Serde<V> valueSerde
) {
    public Serializer<K> keySerializer() { return keySerde.serializer(); }
    public Deserializer<K> keyDeserializer() { return keySerde.deserializer(); }
    public Serializer<V> valueSerializer() { return valueSerde.serializer(); }
    public Deserializer<V> valueDeserializer() { return valueSerde.deserializer(); }
}
```

### Schema Evolution

When fetching schemas from Schema Registry with `schemaEvolution` enabled, Typr generates versioned types and migration helpers:

**Configuration:**
```scala
val options = AvroOptions.default(...).copy(
  schemaSource = SchemaSource.Registry("http://localhost:8081"),
  schemaEvolution = SchemaEvolution.WithMigrations  // or AllVersions
)
```

**Options:**
- `SchemaEvolution.LatestOnly` - Only latest version (default)
- `SchemaEvolution.AllVersions` - Generate versioned types (OrderV1, OrderV2, etc.)
- `SchemaEvolution.WithMigrations` - Versioned types + migration helpers

**Generated versioned types:**
```java
// For a schema with 3 versions in Schema Registry
public record OrderPlacedV1(String orderId, long customerId) { ... }
public record OrderPlacedV2(String orderId, long customerId, Instant placedAt) { ... }
public record OrderPlacedV3(String orderId, long customerId, Instant placedAt, List<String> items) { ... }

// Type alias for latest version
public interface OrderPlaced extends OrderPlacedV3 {}
```

**Generated migration helpers:**
```java
public final class OrderPlacedMigrations {
    // Migrate from V1 to V2
    public static OrderPlacedV2 migrateV1ToV2(OrderPlacedV1 source) {
        return new OrderPlacedV2(
            source.orderId(),
            source.customerId(),
            Instant.EPOCH  // default for new field
        );
    }

    // Migrate from V2 to V3
    public static OrderPlacedV3 migrateV2ToV3(OrderPlacedV2 source) {
        return new OrderPlacedV3(
            source.orderId(),
            source.customerId(),
            source.placedAt(),
            List.of()  // default for new field
        );
    }
}
```

Migration helpers automatically:
- Copy fields that exist in both versions
- Use schema defaults for new fields
- Use type defaults (empty string, 0, empty list, etc.) when no schema default exists

### Schema Validator

When `generateSchemaValidator = true`:

```java
public final class SchemaValidator {
    private SchemaValidator() {}

    // All known schemas
    private static final Map<String, Schema> SCHEMAS = Map.of(
        "com.example.events.OrderPlaced", OrderPlaced.SCHEMA,
        "com.example.events.OrderUpdated", OrderUpdated.SCHEMA,
        "com.example.events.OrderCancelled", OrderCancelled.SCHEMA,
        "com.example.events.Address", Address.SCHEMA
    );

    // Look up schema by fully-qualified name
    public static Optional<Schema> getSchemaByName(String fullName) {
        return Optional.ofNullable(SCHEMAS.get(fullName));
    }

    // Check if reader can read data written with writer schema
    // (reader has defaults for new fields, no removed required fields)
    public static boolean isBackwardCompatible(Schema reader, Schema writer) {
        return SchemaCompatibility
            .checkReaderWriterCompatibility(reader, writer)
            .getType() == SchemaCompatibilityType.COMPATIBLE;
    }

    // Check if writer can be read by old readers
    // (writer has defaults for new fields, no removed required fields)
    public static boolean isForwardCompatible(Schema reader, Schema writer) {
        return SchemaCompatibility
            .checkReaderWriterCompatibility(writer, reader)
            .getType() == SchemaCompatibilityType.COMPATIBLE;
    }

    // Both directions compatible
    public static boolean isFullyCompatible(Schema reader, Schema writer) {
        return isBackwardCompatible(reader, writer)
            && isForwardCompatible(reader, writer);
    }

    // Find required fields missing from a record
    public static List<String> getMissingRequiredFields(GenericRecord record) {
        var missing = new ArrayList<String>();
        for (var field : record.getSchema().getFields()) {
            if (field.defaultVal() == null && record.get(field.name()) == null) {
                missing.add(field.name());
            }
        }
        return missing;
    }

    // Validate a record has all required fields
    public static boolean validateRequiredFields(GenericRecord record) {
        return getMissingRequiredFields(record).isEmpty();
    }
}
```

### Protocol Interfaces

From `.avpr` files:

```json
{
  "protocol": "UserService",
  "namespace": "com.example.service",
  "types": [
    {
      "type": "record",
      "name": "User",
      "fields": [
        {"name": "id", "type": "string"},
        {"name": "email", "type": "string"},
        {"name": "name", "type": "string"}
      ]
    },
    {
      "type": "error",
      "name": "UserNotFoundError",
      "fields": [
        {"name": "userId", "type": "string"},
        {"name": "message", "type": "string"}
      ]
    }
  ],
  "messages": {
    "getUser": {
      "request": [{"name": "userId", "type": "string"}],
      "response": "User",
      "errors": ["UserNotFoundError"]
    },
    "createUser": {
      "request": [
        {"name": "email", "type": "string"},
        {"name": "name", "type": "string"}
      ],
      "response": "User"
    },
    "notifyUser": {
      "request": [
        {"name": "userId", "type": "string"},
        {"name": "message", "type": "string"}
      ],
      "one-way": true
    }
  }
}
```

**Generated:**
```java
// Service interface
public interface UserService {
    User getUser(String userId) throws UserNotFoundError;
    User createUser(String email, String name);
    void notifyUser(String userId, String message);  // one-way
}

// Handler marker interface
public interface UserServiceHandler extends UserService {
}

// Error type (Java records can't extend Exception)
public record UserNotFoundError(String userId, String message) {
    @Override
    public String toString() {
        return "UserNotFoundError{userId=" + userId + ", message=" + message + "}";
    }
}
```

```scala
// Scala - errors properly extend Exception
trait UserService {
    @throws[UserNotFoundError]
    def getUser(userId: String): User

    def createUser(email: String, name: String): User
    def notifyUser(userId: String, message: String): Unit
}

case class UserNotFoundError(userId: String, message: String)
    extends Exception(s"UserNotFoundError{userId=$userId, message=$message}")
```

---

## Configuration Reference

### AvroOptions

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `pkg` | `QIdent` | *required* | Base package for generated code |
| `schemaSource` | `SchemaSource` | *required* | Where to load schemas from |
| `avroWireFormat` | `AvroWireFormat` | `ConfluentRegistry` | Wire format for serialization |
| `generateRecords` | `Boolean` | `true` | Generate record/data classes |
| `generateSerdes` | `Boolean` | `true` | Generate Serializer/Deserializer/Serde |
| `generateProducers` | `Boolean` | `true` | Generate typed producer wrappers |
| `generateConsumers` | `Boolean` | `true` | Generate typed consumer wrappers |
| `generateTopicBindings` | `Boolean` | `true` | Generate Topics class |
| `generateHeaders` | `Boolean` | `true` | Generate typed header classes |
| `generateSchemaValidator` | `Boolean` | `false` | Generate SchemaValidator utility |
| `generateProtocols` | `Boolean` | `true` | Generate interfaces from .avpr files |
| `enablePreciseTypes` | `Boolean` | `false` | Generate Decimal/Fixed wrapper types |
| `headerSchemas` | `Map[String, HeaderSchema]` | `Map.empty` | Named header schemas |
| `topicHeaders` | `Map[String, String]` | `Map.empty` | Topic -> header schema mapping |
| `defaultHeaderSchema` | `Option[String]` | `None` | Default header schema name |
| `defaultKeyType` | `KeyType` | `StringKey` | Default message key type |
| `topicKeys` | `Map[String, KeyType]` | `Map.empty` | Per-topic key type overrides |
| `topicMapping` | `Map[String, String]` | `Map.empty` | Schema name -> topic name |
| `topicGroups` | `Map[String, List[String]]` | `Map.empty` | Manual schema grouping |
| `effectType` | `KafkaEffectType` | `Blocking` | Async effect type |

### Schema Sources

```scala
// Local directory
SchemaSource.Directory(Path.of("schemas"))

// Schema Registry (fetches latest versions)
SchemaSource.Registry("http://localhost:8081")

// Multiple sources (merged)
SchemaSource.Multi(List(
    SchemaSource.Directory(Path.of("local-schemas")),
    SchemaSource.Registry("http://registry:8081")
))
```

### Wire Formats

| Format | Description | Use Case |
|--------|-------------|----------|
| `ConfluentRegistry` | Uses Schema Registry for schema IDs | Production with Schema Registry |
| `BinaryEncoded` | Pure Avro binary, no Schema Registry | Testing, embedded systems |
| `JsonEncoded(jsonLib)` | JSON serialization via Jackson/Circe | REST-friendly, debugging, Spring/Quarkus integration |

**JSON Wire Format:**

When using `JsonEncoded`, records are generated as annotated DTOs without Avro-specific methods:

```scala
// Configuration
avroWireFormat = AvroWireFormat.JsonEncoded(JacksonSupport)
```

**What's different with JSON wire format:**
- Records get `@JsonProperty` annotations (Jackson) or implicit encoders/decoders (Circe)
- No `SCHEMA` field is generated (JSON is self-describing)
- No `toGenericRecord()`/`fromGenericRecord()` methods
- No Kafka Serializer/Deserializer classes are generated

**Why no generated Kafka serdes?** Spring Kafka provides `JsonSerializer<T>` and Quarkus auto-generates JSON serdes. These frameworks handle ObjectMapper configuration and lifecycle. Your annotated records work directly with these framework-provided serializers.

**Usage with Spring Kafka:**
```java
// Spring's JsonSerializer works with your generated records
var props = new Properties();
props.put("bootstrap.servers", "localhost:9092");
props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

var producer = new KafkaProducer<String, OrderPlaced>(props);
producer.send(new ProducerRecord<>("orders", order));  // Just works!
```

**Usage with Quarkus:**
```java
// Quarkus auto-detects types and generates JSON serdes
@Outgoing("orders")
public OrderPlaced produceOrder() {
    return new OrderPlaced(...);  // Automatically serialized to JSON
}
```

### Key Types

```scala
KeyType.StringKey                         // String (default)
KeyType.UUIDKey                           // java.util.UUID
KeyType.LongKey                           // Long
KeyType.IntKey                            // Int
KeyType.BytesKey                          // byte[]
KeyType.SchemaKey("com.example.OrderKey") // Avro record as key
```

### Header Types

```scala
HeaderType.String    // UTF-8 string
HeaderType.UUID      // UUID as string
HeaderType.Instant   // Epoch millis as long
HeaderType.Long      // 8-byte big-endian
HeaderType.Int       // 4-byte big-endian
HeaderType.Boolean   // 1 byte (0/1)
```

### Effect Types

| Effect | Description | Library |
|--------|-------------|---------|
| `Blocking` | Synchronous `Future` | Java stdlib |
| `CompletableFuture` | `CompletableFuture<T>` | Java stdlib |
| `CatsIO` | `IO[T]` | Cats Effect |
| `ZIO` | `Task[T]` | ZIO |
| `ReactorMono` | `Mono<T>` | Project Reactor |

### Framework Integration

| Framework | Annotation | Description |
|-----------|------------|-------------|
| `FrameworkIntegration.None` | None | Plain POJOs without DI annotations |
| `FrameworkIntegration.Spring` | `@Service` | Spring Boot with `ReplyingKafkaTemplate` |
| `FrameworkIntegration.Quarkus` | `@ApplicationScoped` | Quarkus with SmallRye Reactive Messaging |

---

## Framework Integration (Spring / Quarkus)

Typr generates framework-specific code for Spring Boot and Quarkus, providing:
- **Kafka RPC Client** - Call remote services via Kafka with request/reply pattern
- **Kafka RPC Server** - Expose service implementations over Kafka
- **Dependency Injection** - Proper annotations for each framework's DI container

### Configuration

```scala
val options = AvroOptions.default(...).copy(
  frameworkIntegration = FrameworkIntegration.Spring,  // or .Quarkus or .None
  generateKafkaRpc = true  // Generate RPC client/server from .avpr files
)
```

### Result Type

Protocol methods that can fail generate a `Result<T, E>` return type instead of throwing exceptions:

```java
// Generated Result type
public sealed interface Result<T, E> permits Result.Ok, Result.Err {
    record Ok<T, E>(T value) implements Result<T, E> {}
    record Err<T, E>(E error) implements Result<T, E> {}
}

// Service interface
public interface UserService {
    Result<User, UserNotFoundError> getUser(String userId);
    Result<User, ValidationError> createUser(String email, String name);
    Result<Void, UserNotFoundError> deleteUser(String userId);
    void notifyUser(String userId, String message);  // one-way, no result
}
```

**Why `Result` instead of exceptions?**
- Explicit error handling - compiler requires you to handle errors
- No try/catch boilerplate
- Pattern matching with Java 21 sealed types
- Clear separation between expected errors (in Result) and unexpected errors (exceptions)

### Spring Boot Integration

**Generated Server:**
```java
@Service
public record UserServiceServer(UserServiceHandler handler) {

    @KafkaListener(topics = "user-service-requests")
    @SendTo  // Replies to topic from REPLY_TOPIC header
    public Object handleRequest(UserServiceRequest request) {
        return switch (request) {
            case GetUserRequest r -> handleGetUser(r);
            case CreateUserRequest r -> handleCreateUser(r);
            case DeleteUserRequest r -> handleDeleteUser(r);
            case NotifyUserRequest r -> { handleNotifyUser(r); yield null; }
        };
    }

    private GetUserResponse handleGetUser(GetUserRequest request) {
        var result = handler.getUser(request.userId());
        return switch (result) {
            case Result.Ok ok -> new GetUserResponse.Success(request.correlationId(), ok.value());
            case Result.Err err -> new GetUserResponse.Error(request.correlationId(), err.error());
        };
    }
    // ... other handlers
}
```

**Generated Client:**
```java
@Service
public record UserServiceClient(ReplyingKafkaTemplate<String, Object, Object> replyingTemplate) {

    public Result<User, UserNotFoundError> getUser(String userId) throws Exception {
        GetUserRequest request = GetUserRequest.create(userId);
        var reply = replyingTemplate
            .sendAndReceive(new ProducerRecord<>("user-service-requests", request))
            .get()
            .value();
        return switch (reply) {
            case GetUserResponse.Success s -> new Result.Ok<>(s.value());
            case GetUserResponse.Error e -> new Result.Err<>(e.error());
            default -> throw new IllegalStateException("Unexpected response type");
        };
    }
}
```

**Spring Configuration:**
```java
@Configuration
public class KafkaRpcConfig {
    @Bean
    public ReplyingKafkaTemplate<String, Object, Object> replyingKafkaTemplate(
            ProducerFactory<String, Object> pf,
            ConcurrentMessageListenerContainer<String, Object> container) {
        return new ReplyingKafkaTemplate<>(pf, container);
    }

    @Bean
    public ConcurrentMessageListenerContainer<String, Object> repliesContainer(
            ConsumerFactory<String, Object> cf) {
        ContainerProperties props = new ContainerProperties("user-service-replies");
        return new ConcurrentMessageListenerContainer<>(cf, props);
    }
}
```

### Quarkus Integration

**Generated Server:**
```java
@ApplicationScoped
public record UserServiceServer(UserServiceHandler handler) {
    @Inject
    public UserServiceServer {}

    @Incoming("user-service-requests")
    @Outgoing("user-service-replies")
    public Object handleRequest(UserServiceRequest request) {
        return switch (request) {
            case GetUserRequest r -> handleGetUser(r);
            case CreateUserRequest r -> handleCreateUser(r);
            case DeleteUserRequest r -> handleDeleteUser(r);
            case NotifyUserRequest r -> { handleNotifyUser(r); yield null; }
        };
    }

    private GetUserResponse handleGetUser(GetUserRequest request) {
        var result = handler.getUser(request.userId());
        return switch (result) {
            case Result.Ok ok -> new GetUserResponse.Success(request.correlationId(), ok.value());
            case Result.Err err -> new GetUserResponse.Error(request.correlationId(), err.error());
        };
    }
}
```

**Generated Client:**
```java
@ApplicationScoped
public record UserServiceClient(KafkaRequestReply<Object, Object> replyingTemplate) {
    @Inject
    public UserServiceClient {}

    public Result<User, UserNotFoundError> getUser(String userId) throws Exception {
        GetUserRequest request = GetUserRequest.create(userId);
        var reply = replyingTemplate.request(request).await().indefinitely();
        return switch (reply) {
            case GetUserResponse.Success s -> new Result.Ok<>(s.value());
            case GetUserResponse.Error e -> new Result.Err<>(e.error());
            default -> throw new IllegalStateException("Unexpected response type");
        };
    }
}
```

**Quarkus Configuration (application.properties):**
```properties
mp.messaging.incoming.user-service-requests.connector=smallrye-kafka
mp.messaging.incoming.user-service-requests.topic=user-service-requests
mp.messaging.incoming.user-service-requests.value.deserializer=org.apache.kafka.common.serialization.JsonDeserializer
mp.messaging.incoming.user-service-requests.value.type=com.example.service.UserServiceRequest

mp.messaging.outgoing.user-service-replies.connector=smallrye-kafka
mp.messaging.outgoing.user-service-replies.topic=user-service-replies
mp.messaging.outgoing.user-service-replies.value.serializer=org.apache.kafka.common.serialization.JsonSerializer
```

### Implementing the Handler

Both frameworks require you to implement the `UserServiceHandler` interface:

```java
@Service  // Spring
// or @ApplicationScoped  // Quarkus
public class UserServiceHandlerImpl implements UserServiceHandler {

    private final UserRepository userRepository;

    public UserServiceHandlerImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Result<User, UserNotFoundError> getUser(String userId) {
        return userRepository.findById(userId)
            .<Result<User, UserNotFoundError>>map(Result.Ok::new)
            .orElseGet(() -> new Result.Err<>(
                new UserNotFoundError(userId, "User not found: " + userId)));
    }

    @Override
    public Result<User, ValidationError> createUser(String email, String name) {
        if (email == null || email.isBlank()) {
            return new Result.Err<>(new ValidationError("email", "Email is required"));
        }
        User user = new User(UUID.randomUUID().toString(), email, name);
        userRepository.save(user);
        return new Result.Ok<>(user);
    }

    @Override
    public Result<Void, UserNotFoundError> deleteUser(String userId) {
        if (userRepository.existsById(userId)) {
            userRepository.deleteById(userId);
            return new Result.Ok<>(null);
        }
        return new Result.Err<>(new UserNotFoundError(userId, "User not found"));
    }

    @Override
    public void notifyUser(String userId, String message) {
        // Fire-and-forget notification
        System.out.println("Notifying user " + userId + ": " + message);
    }
}
```

### Request/Response Types

Each RPC method generates request and response wrapper types:

```java
// Request wraps method parameters + correlation ID
public sealed interface UserServiceRequest
    permits GetUserRequest, CreateUserRequest, DeleteUserRequest, NotifyUserRequest {}

public record GetUserRequest(String correlationId, String userId) implements UserServiceRequest {
    public static GetUserRequest create(String userId) {
        return new GetUserRequest(UUID.randomUUID().toString(), userId);
    }
}

// Response wraps result + correlation ID
public sealed interface GetUserResponse {
    String correlationId();

    record Success(String correlationId, User value) implements GetUserResponse {}
    record Error(String correlationId, UserNotFoundError error) implements GetUserResponse {}
}
```

### Framework Comparison

| Feature | Spring | Quarkus |
|---------|--------|---------|
| DI Annotation | `@Service` | `@ApplicationScoped` |
| Request Handler | `@KafkaListener` | `@Incoming` |
| Response | `@SendTo` | `@Outgoing` |
| RPC Template | `ReplyingKafkaTemplate` | `KafkaRequestReply` |
| Blocking Call | `.get()` | `.await().indefinitely()` |

---

## Type Mappings

### Primitive Types

| Avro | Java | Kotlin | Scala |
|------|------|--------|-------|
| `null` | (in unions) | (in unions) | (in unions) |
| `boolean` | `Boolean` | `Boolean` | `Boolean` |
| `int` | `Integer` | `Int` | `Int` |
| `long` | `Long` | `Long` | `Long` |
| `float` | `Float` | `Float` | `Float` |
| `double` | `Double` | `Double` | `Double` |
| `bytes` | `ByteBuffer` | `ByteBuffer` | `ByteBuffer` |
| `string` | `String` | `String` | `String` |

### Complex Types

| Avro | Java | Kotlin | Scala |
|------|------|--------|-------|
| `array<T>` | `List<T>` | `List<T>` | `List[T]` |
| `map<T>` | `Map<String, T>` | `Map<String, T>` | `Map[String, T]` |
| `enum` | Generated enum | Generated enum | Generated enum |
| `record` | Generated record | Generated data class | Generated case class |
| `union [null, T]` | `Optional<T>` | `T?` | `Option[T]` |
| `union [A, B, C]` | Sealed interface | Sealed interface | Sealed trait |
| `fixed(N)` | `byte[]` or `FixedN` | `ByteArray` or `FixedN` | `Array[Byte]` or `FixedN` |

### Logical Types

| Avro Logical Type | Java | Kotlin | Scala |
|-------------------|------|--------|-------|
| `uuid` | `UUID` | `UUID` | `UUID` |
| `date` | `LocalDate` | `LocalDate` | `LocalDate` |
| `time-millis` | `LocalTime` | `LocalTime` | `LocalTime` |
| `time-micros` | `LocalTime` | `LocalTime` | `LocalTime` |
| `timestamp-millis` | `Instant` | `Instant` | `Instant` |
| `timestamp-micros` | `Instant` | `Instant` | `Instant` |
| `local-timestamp-millis` | `LocalDateTime` | `LocalDateTime` | `LocalDateTime` |
| `local-timestamp-micros` | `LocalDateTime` | `LocalDateTime` | `LocalDateTime` |
| `decimal(p, s)` | `BigDecimal` or `DecimalP_S` | `BigDecimal` or `DecimalP_S` | `BigDecimal` or `DecimalP_S` |
| `duration` | `byte[]` | `ByteArray` | `Array[Byte]` |

---

## Infrastructure

### Docker Compose Setup

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
    healthcheck:
      test: kafka-topics --bootstrap-server localhost:9092 --list
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 30s

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
    healthcheck:
      test: curl -f http://localhost:8081/subjects || exit 1
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 30s
```

```bash
docker-compose up -d kafka schema-registry
```

### Dependencies

**Maven:**
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

**Gradle (Kotlin):**
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

**SBT (Scala):**
```scala
resolvers += "Confluent" at "https://packages.confluent.io/maven/"

libraryDependencies ++= Seq(
    "org.apache.avro" % "avro" % "1.12.0",
    "org.apache.kafka" % "kafka-clients" % "3.9.0",
    "io.confluent" % "kafka-avro-serializer" % "7.8.0"
)
```

---

## Known Limitations

### Known Quirks

1. **Directory Naming** - Group names come from directory names with title-case conversion: `order-events` → `OrderEvents`, `user_notifications` → `UserNotifications`.

2. **Namespace Required** - The namespace in generated code comes from the schema's `namespace` field. All schemas in a group should share the same namespace.

3. **Protocol Errors in Java/Kotlin** - Error types are plain records/data classes because Java records cannot extend classes. Only Scala generates proper `Exception` subclasses.

4. **Avro String Handling** - Avro returns `Utf8` objects (implementing `CharSequence`) for strings. The generated `fromGenericRecord()` methods handle this conversion automatically.

5. **BigDecimal Scaling** - Values are automatically scaled to match schema precision using `HALF_UP` rounding. `99.999` in a `decimal(10,2)` becomes `100.00`.

6. **Complex Union Naming** - Union types are named by concatenating member type names: `["string", "int"]` → `StringOrInt`, `["string", "int", "boolean"]` → `StringOrIntOrBoolean`.

---

## See Also

- [Apache Avro Specification](https://avro.apache.org/docs/current/spec.html)
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Confluent Schema Registry](https://docs.confluent.io/platform/current/schema-registry/)
- [Typr Database Code Generation](../readme.md)

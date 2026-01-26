# Avro/Kafka Code Generation

Typr generates type-safe JVM code from Apache Avro schemas (`.avsc`) and protocols (`.avpr`), with full support for Kafka producers, consumers, and RPC patterns.

## Features

### Core Schema Support
- **Records** - Immutable data classes (Java records, Kotlin data classes, Scala case classes)
- **Enums** - Type-safe enumerations with JSON serialization
- **Complex Unions** - Sealed interfaces for `["string", "int", "boolean"]` unions
- **Recursive Types** - Self-referential schemas like trees and linked lists
- **Logical Types** - UUID, date, time, timestamp-millis, decimal with precision
- **$ref Support** - Reference schemas from other files

### Precise Types
Compile-time validated wrapper types for constrained values:
```java
// Generated from decimal(10,2) logical type
Decimal10_2 amount = Decimal10_2.of(new BigDecimal("99.99")); // Validates at construction
Decimal10_2 invalid = Decimal10_2.of(new BigDecimal("12345678901.00")); // Throws!
```

### Wire Formats
- **AvroEncoded** - Binary Avro with Confluent Schema Registry
- **JsonEncoded** - JSON serialization (Jackson, Circe, ZIO JSON)

### Kafka Integration
- **Typed Producers** - `OrderEventsProducer.send(OrderPlaced event)`
- **Typed Consumers** - Abstract handlers with methods per event type
- **Serializers/Deserializers** - Kafka Serde implementations
- **Topic Bindings** - Type-safe topic definitions

### Framework Integration
- **Spring Boot** - `@Service`, `KafkaTemplate`, `@KafkaListener`
- **Quarkus** - `@ApplicationScoped`, `Emitter`, `@Incoming`

### RPC Support (from .avpr protocols)
- **Service Interfaces** - Clean async interfaces
- **Result ADT** - `Result<User, UserNotFoundError>` for typed errors
- **Client/Server** - Generated Kafka request-reply implementations
- **Effect Types** - Blocking, CompletableFuture, Uni (Mutiny), IO (Cats), ZIO

## Generated Code Examples

### From Schema (OrderPlaced.avsc)
```java
// Immutable record with all Avro types
public record OrderPlaced(
    UUID orderId,
    long customerId,
    Decimal10_2 totalAmount,
    Instant placedAt,
    List<String> items,
    @Nullable String shippingAddress
) implements OrderEvents {}

// Type-safe producer
orderEventsProducer.send(new OrderPlaced(...));

// Type-safe consumer
public class MyOrderHandler extends OrderEventsHandler {
    @Override
    public void onOrderPlaced(OrderPlaced event, Headers headers) {
        // Handle event
    }
}
```

### From Protocol (UserService.avpr)
```java
// Clean service interface
public interface UserService {
    Result<User, UserNotFoundError> getUser(String userId);
    Result<User, ValidationError> createUser(String email, String name);
    Result<Void, UserNotFoundError> deleteUser(String userId);
    void notifyUser(String userId, String message); // One-way
}

// Result ADT for explicit error handling
switch (userService.getUser("123")) {
    case Result.Ok(var user) -> System.out.println("Found: " + user.name());
    case Result.Err(var error) -> System.out.println("Not found: " + error.userId());
}

// Generated RPC client (Spring)
@Service
public class UserServiceClient implements UserService {
    private final ReplyingKafkaTemplate<String, Object, Object> replyingTemplate;
    // ... implements all methods via Kafka request-reply
}

// Generated RPC server
@Service
public class UserServiceServer {
    @KafkaListener(topics = "user-service-requests")
    @SendTo
    public Object handleRequest(Object request) {
        // Dispatches to UserServiceHandler implementation
    }
}
```

### Union Types
```java
// Sealed interface for ["string", "int", "boolean"] union
public sealed interface StringOrIntOrBoolean {
    static StringOrIntOrBoolean of(String value) { ... }
    static StringOrIntOrBoolean of(int value) { ... }
    static StringOrIntOrBoolean of(boolean value) { ... }

    boolean isString();
    String asString(); // Throws if not string
    // ...
}
```

## Test Projects

| Project | Language | Framework | Wire Format | Features |
|---------|----------|-----------|-------------|----------|
| `java` | Java | - | Avro Binary | Full Kafka integration |
| `java-vanilla` | Java | - | Avro Binary | No framework deps |
| `java-async` | Java | - | Avro Binary | CompletableFuture |
| `java-json` | Java | - | JSON | Pure DTOs only |
| `java-spring` | Java | Spring Boot | Avro Binary | Full RPC with tests |
| `java-quarkus` | Java | Quarkus | Avro Binary | Full RPC with tests |
| `kotlin` | Kotlin | - | Avro Binary | Full Kafka integration |
| `kotlin-json` | Kotlin | - | JSON | Pure DTOs only |
| `kotlin-quarkus-mutiny` | Kotlin | Quarkus | Avro Binary | Uni effect type |
| `scala` | Scala 3 | - | Avro Binary | Full Kafka integration |
| `scala-cats` | Scala 3 | Cats Effect | Avro Binary | IO effect type |
| `scala-json` | Scala 3 | - | JSON | Pure DTOs only |

## Usage

```scala
import typr.avro._

val options = AvroOptions(
  pkg = "com.example.events",
  lang = Lang.Java,
  wireFormat = AvroWireFormat.AvroEncoded(
    schemaRegistryType = SchemaRegistryType.Confluent,
    schemaRegistrySupportType = SchemaRegistrySupportType.JavaDefault
  ),
  effectType = EffectType.Blocking,
  frameworkIntegration = FrameworkIntegration.Spring,
  generateKafkaBindings = true,
  generateKafkaRpc = true
)

AvroCodegen.generate(
  schemas = List(schemaDir),
  options = options,
  targetDir = Path.of("generated")
)
```

## Schema Examples

### Record with Logical Types
```json
{
  "type": "record",
  "name": "Invoice",
  "fields": [
    {"name": "invoiceId", "type": {"type": "string", "logicalType": "uuid"}},
    {"name": "amount", "type": {"type": "bytes", "logicalType": "decimal", "precision": 10, "scale": 2}},
    {"name": "issuedAt", "type": {"type": "long", "logicalType": "timestamp-millis"}}
  ]
}
```

### Multi-Event Topic (Sealed Interface)
```json
{
  "type": "record",
  "name": "OrderPlaced",
  "typr": {"event-group": "OrderEvents", "topic": "order-events"},
  "fields": [...]
}
```

### Protocol with Errors
```json
{
  "protocol": "UserService",
  "messages": {
    "getUser": {
      "request": [{"name": "userId", "type": "string"}],
      "response": "User",
      "errors": ["UserNotFoundError"]
    }
  }
}
```

## Running Tests

```bash
# Regenerate all Avro test code
bleep run generate-avro-test

# Run Java tests
bleep test testers/avro/java

# Run Spring integration tests (requires Kafka)
bleep test testers/avro/java-spring

# Run Quarkus tests
bleep test testers/avro/java-quarkus

# Run Kotlin Quarkus/Mutiny tests
./gradlew :testers:avro:kotlin-quarkus-mutiny:test
```

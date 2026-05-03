# Typr Bridge Architecture

## Vision: Domain as the Hub

```
                         ┌─────────────────────────┐
                         │      DOMAIN TYPES       │
                         │                         │
                         │   Customer              │
                         │   Order                 │
                         │   Product               │
                         │   ...                   │
                         └───────────┬─────────────┘
                                     │
            ┌────────────────────────┼────────────────────────┐
            │                        │                        │
            ▼                        ▼                        ▼
     ┌─────────────┐          ┌─────────────┐          ┌─────────────┐
     │  Database   │          │    APIs     │          │   Events    │
     │             │          │             │          │             │
     │ CustomerRow │          │ CustomerDto │          │ CustomerEvt │
     │ .toDomain() │          │ .toDomain() │          │ .toDomain() │
     │ .fromDom()  │          │ .fromDom()  │          │ .fromDom()  │
     └─────────────┘          └─────────────┘          └─────────────┘
           │                                                  │
     ┌─────┴─────┐                                     ┌──────┴──────┐
     │           │                                     │             │
     ▼           ▼                                     ▼             ▼
  PostgreSQL  MariaDB                              Avro/Kafka   gRPC/Proto
  Oracle      DuckDB
  SQL Server  DB2
```

**The Problem:** In typical enterprise systems, data flows through multiple representations:
- Database rows for persistence
- DTOs for REST APIs
- Protobuf messages for gRPC
- Avro records for Kafka events

Each boundary has its own type definitions, leading to:
- Scattered business logic across mappers
- Subtle bugs from mismatched field names or types
- Boilerplate conversion code that's error-prone
- Difficulty tracking what a "Customer" really means

**The Solution:** Define domain types once. Typr Bridge generates type-safe mappers to/from all boundaries, validates compatibility at build time, and ensures your domain model is the single source of truth.

---

## Completed Features (P0-P1)

### P0: Domain Type DSL

**What it is:** A declarative way to define your business domain types with their fields, relationships, and source mappings.

**Key Components:**

| Type | Purpose | Location |
|------|---------|----------|
| `DomainTypeDefinition` | Complete domain type spec | `bridge/CompositeType.scala` |
| `DomainField` | Field with type, nullability, array support | `bridge/CompositeType.scala` |
| `PrimarySource` | The "anchor" source for a domain type | `bridge/CompositeType.scala` |
| `AlignedSource` | Additional sources mapped to domain | `bridge/CompositeType.scala` |

**Domain Field Properties:**
```scala
case class DomainField(
    name: String,           // Field name (camelCase convention)
    typeName: String,       // Type: scalar, array, or another domain type
    nullable: Boolean,      // Can be null/absent
    array: Boolean,         // Is a collection
    description: Option[String]
)
```

**Supported Scalar Types:**
- Primitives: `String`, `Int`, `Long`, `Short`, `Byte`, `Float`, `Double`, `Boolean`
- Numeric: `BigDecimal`, `BigInteger`
- Temporal: `Instant`, `LocalDate`, `LocalTime`, `LocalDateTime`, `OffsetDateTime`, `ZonedDateTime`
- Special: `UUID`, `ByteArray`, `Json`

**Compact Type Syntax:**
```
String        → required string
String?       → optional string
String[]      → required array of strings
String?[]     → optional array of strings (rare)
Customer      → reference to another domain type
Customer?     → optional reference
```

**Generation Flags:**
```scala
case class DomainTypeDefinition(
    name: String,
    fields: List[DomainField],
    primary: Option[PrimarySource],
    alignedSources: List[AlignedSource],
    // Generation control:
    generateDomainType: Boolean = true,   // Generate the domain class
    generateMappers: Boolean = true,      // Generate toDomain/fromDomain
    generateInterface: Boolean = false,   // Generate trait/interface
    generateBuilder: Boolean = false,     // Generate builder pattern
    generateCopy: Boolean = true          // Generate copy/with methods
)
```

**Improvement Opportunities:**
- Add validation annotations (min/max, regex, custom validators)
- Support for computed/derived fields
- Versioning for schema evolution
- Better IDE support (LSP integration)

---

### P0: Name Alignment Engine

**What it is:** Automatically matches field names across naming conventions. `customer_email` (database) matches `customerEmail` (domain) matches `CustomerEmail` (proto).

**Key Components:**

| Type | Purpose | Location |
|------|---------|----------|
| `NameAligner` | Trait for name matching | `bridge/CompositeType.scala` |
| `DefaultNameAligner` | Implementation with tokenization | `bridge/CompositeType.scala` |
| `ColumnTokenizer` | Parses names into tokens | `bridge/ColumnTokenizer.scala` |
| `ColumnStemmer` | Normalizes and expands abbreviations | `bridge/ColumnStemmer.scala` |

**How It Works:**

```
Input: "customer_email" (source) vs "customerEmail" (domain)

Step 1: Tokenization
  "customer_email" → ["customer", "email"] (snake_case detected)
  "customerEmail"  → ["customer", "email"] (camelCase detected)

Step 2: Stemming
  ["customer", "email"] → ["customer", "email"] (no changes)

Step 3: Canonicalization
  "customeremail" == "customeremail" → MATCH!
```

**Pattern Detection:**
- `SnakeCase`: `customer_email`
- `CamelCase`: `customerEmail`
- `PascalCase`: `CustomerEmail`
- `ScreamingSnake`: `CUSTOMER_EMAIL`
- `Mixed`: `Customer_Email`
- `Ambiguous`: When multiple interpretations possible

**Built-in Abbreviation Expansions:**
```scala
val abbreviations = Map(
  "id"    → "identifier",
  "addr"  → "address",
  "usr"   → "user",
  "cust"  → "customer",
  "prod"  → "product",
  "qty"   → "quantity",
  "num"   → "number",
  "amt"   → "amount",
  "desc"  → "description",
  "dt"    → "date",
  "ts"    → "timestamp",
  "dttm"  → "datetime",
  // ... and more
)
```

**Custom Abbreviations:**
```scala
val aligner = DefaultNameAligner(customAbbreviations = Map(
  "sku" → "stockkeepingunit",
  "ean" → "europeararticlenumber"
))
```

**Improvement Opportunities:**
- Configurable abbreviation dictionaries per project
- Machine learning for abbreviation detection
- Fuzzy matching with configurable threshold
- Support for domain-specific naming conventions

---

### P0: Matching Rules Engine

**What it is:** Determines how domain fields map to source fields, handling exact matches, custom transformations, and explicit exclusions.

**Key Components:**

| Type | Purpose | Location |
|------|---------|----------|
| `SmartDefaults` | Auto-resolves field mappings | `bridge/validation/SmartDefaults.scala` |
| `FieldOverride` | Manual mapping control | `bridge/model/FieldOverride.scala` |
| `SourceDeclaration` | Source-level configuration | `bridge/model/SourceDeclaration.scala` |
| `ResolvedFieldAction` | Computed mapping result | `bridge/model/ResolvedFlow.scala` |

**Resolution Process:**
```
For each domain field:
  1. Check explicit override → Forward/Drop/Custom
  2. Check mappings config → explicit name mapping
  3. Try name alignment   → auto-match by tokenization
  4. No match?            → auto-drop with warning

For each source field:
  1. Already mapped?      → skip
  2. In exclude list?     → skip
  3. Not mapped?          → report as Unannotated
```

**Field Override Types:**

```scala
sealed trait FieldOverride

// Map domain field to source field
case class Forward(
    sourceFieldName: Option[String],  // None = same name
    typePolicy: TypePolicy,           // How strict on types
    directionOverride: Option[FlowDirection]
) extends FieldOverride

// Exclude field from this source
case class Drop(
    reason: Option[String]
) extends FieldOverride

// Custom transformation
case class Custom(kind: CustomKind) extends FieldOverride

sealed trait CustomKind
case class MergeFrom(sourceFields: List[String]) extends CustomKind
case class SplitFrom(sourceField: String) extends CustomKind
case class ComputedFrom(domainFields: List[String]) extends CustomKind
case class Enrichment(description: String) extends CustomKind
```

**Example Use Cases:**

```yaml
# Merge first + last name into fullName
fullName:
  custom:
    mergeFrom: [first_name, last_name]

# Split address into components
streetAddress:
  custom:
    splitFrom: full_address

# Computed field (not in source)
totalPrice:
  custom:
    computedFrom: [unitPrice, quantity]

# External enrichment
geoLocation:
  custom:
    enrichment: "Geocoded from address via external API"
```

**Source Declaration Options:**

```scala
case class SourceDeclaration(
    sourceName: String,
    entityPath: String,
    role: SourceRole,                    // Primary or Secondary
    direction: FlowDirection,            // In, Out, or InOut
    mode: CompatibilityMode,             // Exact, Superset, Subset
    mappings: Map[String, String],       // Explicit field name mappings
    exclude: Set[String],                // Source fields to ignore
    includeExtra: List[String],          // Include unmapped fields
    readonly: Boolean,                   // No fromDomain generation
    defaultTypePolicy: TypePolicy,       // Default type strictness
    fieldOverrides: Map[String, FieldOverride]
)
```

**Improvement Opportunities:**
- Bidirectional custom transformations (with inverse functions)
- Conditional mappings based on field values
- Template-based transformations
- Better error messages for common mistakes

---

### Contract Ownership and Data Flow

**The Key Insight:** Not all boundaries are equal. Some contracts you own, some you don't.

```
                    ┌─────────────────────────┐
                    │      YOUR DOMAIN        │
                    │      (you own this)     │
                    └───────────┬─────────────┘
                                │
        ┌───────────────────────┼───────────────────────┐
        │                       │                       │
        ▼                       ▼                       ▼
┌───────────────┐      ┌───────────────┐      ┌───────────────┐
│   Database    │      │  Your API     │      │ Partner API   │
│  (you own)    │      │  (you own)    │      │ (they own)    │
│               │      │               │      │               │
│  direction:   │      │  direction:   │      │  direction:   │
│    InOut      │      │    Out        │      │    In         │
│               │      │               │      │               │
│  Can change   │      │  Can change   │      │  Cannot       │
│  schema: YES  │      │  contract:YES │      │  change: NO   │
└───────────────┘      └───────────────┘      └───────────────┘
```

**Flow Direction Semantics:**

| Direction | You Control Contract? | Generated Code | Validation |
|-----------|----------------------|----------------|------------|
| `In` | NO - external input | `source.toDomain()` only | Warning if field missing |
| `Out` | YES - you define it | `domain.toSource()` only | Error if field missing |
| `InOut` | YES - full control | Both directions | Error if field missing |

**External Contracts (direction: In):**

These are schemas you **consume but don't control**:
- Partner/vendor API responses
- Third-party event streams (Kafka topics you subscribe to)
- Legacy system exports
- Industry standard formats (HL7, FHIR, FIX, etc.)

```yaml
# Example: Consuming a third-party payment webhook
alignedSources:
  "stripe:PaymentIntent":
    direction: in                    # We receive this, can't change it
    mode: superset                   # They may add fields we don't need
    readonly: true                   # Never generate toStripe()
    field_overrides:
      metadata: drop                 # We don't use their metadata field
      amount:
        forward: true
        type_policy: allow_widening  # They use int64, we use BigDecimal
```

**Your Contracts (direction: Out):**

These are schemas you **define and provide to consumers**:
- Your public REST API DTOs
- Events you publish to Kafka
- gRPC responses you serve

```yaml
# Example: Your public API contract
alignedSources:
  "api:CustomerResponse":
    direction: out                   # We produce this
    mode: exact                      # Contract must be precise
    field_overrides:
      internalNotes: drop            # Don't expose internal data
      legacyId: drop                 # Don't expose migration artifacts
```

**Bidirectional (direction: InOut):**

These are schemas you **fully control**:
- Your own database tables
- Internal microservice contracts
- Your own event schemas

```yaml
# Example: Your database
alignedSources:
  "pg:customers":
    direction: in_out               # Full read/write
    mode: exact                     # Schema should match
```

**Validation Differences by Direction:**

| Scenario | In | Out | InOut |
|----------|-----|------|-------|
| Domain field missing in source | Warning | Error | Error |
| Source field not mapped | OK (superset) | Must exclude | Must handle |
| Type mismatch | Adapt at read | You define contract | Must match |
| Nullable mismatch | Can coerce | You define | Must match |

**Why This Matters:**

1. **External contracts can't be "fixed"** - When Stripe changes their webhook format, you adapt. When you read from a legacy Oracle DB, you work around its quirks.

2. **Your contracts are promises** - When you publish an API, consumers depend on it. Missing fields are breaking changes.

3. **Code generation differs** - For `In` sources, you only need `toDomain()`. For `Out` sources, you only need `toSource()`. Generating both when you only need one creates dead code.

4. **Validation strictness differs** - External contracts: "best effort to consume". Your contracts: "must be complete and correct".

**Automatic Direction Detection (APIs):**

For OpenAPI/REST, direction can be **inferred** from schema usage:

```
┌─────────────────────────────────────────────────────────────────┐
│  POST /customers                                                │
│    requestBody: CustomerCreateRequest    ← Input schema         │
│    response 200: CustomerResponse        ← Output schema        │
│                                                                 │
│  GET /customers/{id}                                            │
│    response 200: CustomerResponse        ← Output schema        │
│                                                                 │
│  PUT /customers/{id}                                            │
│    requestBody: CustomerUpdateRequest    ← Input schema         │
│    response 200: CustomerResponse        ← Output schema        │
└─────────────────────────────────────────────────────────────────┘

Detected directions:
  CustomerCreateRequest  → In only   (request body)
  CustomerUpdateRequest  → In only   (request body)
  CustomerResponse       → Out only  (response body)
```

**Combined with Ownership:**

| API Type | Schema Position | Ownership | Direction | Generated |
|----------|-----------------|-----------|-----------|-----------|
| Your API | Request body | Internal | In | `toDomain()` |
| Your API | Response body | Internal | Out | `fromDomain()` |
| Partner API | Request body | External | Out* | `fromDomain()` |
| Partner API | Response body | External | In | `toDomain()` |

*When calling external API, request is what YOU send (Out from domain), response is what YOU receive (In to domain).

**Endpoints as Functions:**

```
Your API endpoint = function you implement
  Input:  Request schemas  → validate, toDomain()
  Output: Response schemas → fromDomain(), serialize

External API call = function you invoke
  Input:  Response schemas → toDomain(), use
  Output: Request schemas  → fromDomain(), send
```

**What We Can Infer Automatically:**

| Source | Can Detect | How |
|--------|------------|-----|
| OpenAPI | In/Out per schema | Scan all operations for schema refs |
| gRPC | In/Out per message | Request vs response in service defs |
| Avro | Topic direction | Producer vs consumer config |
| Database | Always InOut | Tables are read/write |

**Current Implementation:**

- `FlowDirection` enum: `In`, `Out`, `InOut`
- `readonly: true` suppresses `toSource()` generation
- Direction affects validation severity (warning vs error)
- `mode: superset` allows extra fields from external sources

**Future Enhancements (P1-P2):**

| Feature | Description |
|---------|-------------|
| Auto-detect direction | Scan OpenAPI/gRPC for schema usage positions |
| `ownership: external` | Explicit flag beyond direction |
| Contract versioning | Track schema versions for external contracts |
| Deprecation warnings | Warn when external contract changes detected |
| Adapter generation | Generate adapter layers for external → internal |
| Contract diff | Compare versions of external contracts |
| Migration helpers | Generate code to handle contract evolution |

---

### P0: toDomain/fromDomain Generation

**What it is:** Generates type-safe conversion code between domain types and all their aligned sources.

**Key Components:**

| Type | Purpose | Location |
|------|---------|----------|
| `FileBridgeProjectionMapper` | Generates mapper methods | `codegen/FileBridgeProjectionMapper.scala` |
| `ResolvedProjection` | Database mapping spec | `codegen/FileBridgeProjectionMapper.scala` |
| `ResolvedExternalProjection` | Avro/Proto mapping spec | `codegen/FileBridgeProjectionMapper.scala` |
| `ExternalRecord` | Abstract external source | `codegen/FileBridgeProjectionMapper.scala` |

**Generated Code Pattern (Kotlin example):**

```kotlin
// Domain type
data class Customer(
    val customerId: CustomerId,
    val name: String,
    val email: Email?,
    val createdAt: Instant
)

// Generated extension functions
fun CustomerRow.toDomain(): Customer = Customer(
    customerId = CustomerId(this.customerId),
    name = this.name,
    email = this.email?.let { Email(it) },
    createdAt = this.createdAt
)

fun Customer.toCustomerRow(): CustomerRow = CustomerRow(
    customerId = this.customerId.value,
    name = this.name,
    email = this.email?.value,
    createdAt = this.createdAt
)

// For Avro
fun CustomerEvent.toDomain(): Customer = Customer(
    customerId = CustomerId(this.customerId),
    name = this.name,
    email = this.email?.let { Email(it) },
    createdAt = this.timestamp.toInstant()
)
```

**Nullability Handling Matrix:**

| Domain | Source | Conversion |
|--------|--------|------------|
| Required | Required | Direct pass-through |
| Optional | Optional | Direct pass-through |
| Required | Optional | `.get()` or throw |
| Optional | Required | Wrap in `Some`/`Optional` |

**Type Wrapper Handling:**
```kotlin
// Wrapper type
@JvmInline value class CustomerId(val value: Long)

// Generated: unwrap for source, wrap for domain
customerId = CustomerId(source.customerId)  // source → domain
customerId = domain.customerId.value        // domain → source
```

**Language Support:**

| Language | Domain Type | Nullable | Collections |
|----------|-------------|----------|-------------|
| Scala | `case class` | `Option[T]` | `List[T]` |
| Kotlin | `data class` | `T?` | `List<T>` |
| Java | `record` | `Optional<T>` | `List<T>` |

**Improvement Opportunities:**
- Lazy conversion (convert fields on access)
- Streaming conversion for large collections
- Caching for repeated conversions
- Performance benchmarks and optimization
- Support for partial updates (patch semantics)

---

### P1: Field Coverage Checks

**What it is:** Validates that all domain fields are covered by at least one source, and all source fields are explicitly handled.

**Key Components:**

| Type | Purpose | Location |
|------|---------|----------|
| `FlowValidator` | Main validation orchestrator | `bridge/validation/FlowValidator.scala` |
| `CheckFinding` | Single validation issue | `bridge/model/CheckResult.scala` |
| `CheckReport` | Complete validation report | `bridge/model/CheckResult.scala` |

**Validation Rules:**

```
1. Domain Type Rules:
   ✓ Must have at least one field
   ✓ Must have a primary source
   ✓ All fields must be covered by Out/InOut sources

2. Source Rules:
   ✓ Declared sources must exist
   ✓ Forward fields must exist in source
   ✓ Custom transformation refs must exist

3. Direction-Based Rules:
   In:     Domain fields can be missing (read-only)
   Out:    All domain fields must be produced
   InOut:  All domain fields must be bidirectional
```

**Check Codes:**

| Code | Severity | Meaning |
|------|----------|---------|
| `NoFields` | Error | Domain type has no fields |
| `NoPrimarySource` | Error | No primary source declared |
| `SourceEntityNotFound` | Error | Declared source doesn't exist |
| `MissingRequiredField` | Error/Warn | Field not covered (severity depends on direction) |
| `UnannotatedField` | Error | Source field not mapped or excluded |
| `InvalidMergeFromRef` | Error | MergeFrom references non-existent field |
| `InvalidSplitFromRef` | Error | SplitFrom references non-existent field |
| `InvalidComputedFromRef` | Error | ComputedFrom references non-existent field |

**Example Report:**

```
Domain Type: Customer
━━━━━━━━━━━━━━━━━━━━
✓ 5 fields defined
✓ Primary source: postgres:customers

Source: postgres:customers (InOut)
  ✓ customerId → customer_id (exact)
  ✓ name → name (exact)
  ✓ email → email (exact, nullable)
  ⚠ created_by not mapped (add to exclude or forward)

Source: kafka:customer-events (In)
  ✓ customerId → customer_id (exact)
  ✓ name → name (exact)
  ⚠ email missing (OK for In direction)
```

**Improvement Opportunities:**
- Coverage percentage metrics
- Visual diff between sources
- Suggested fixes for common issues
- Integration with CI/CD pipelines
- IDE inline annotations

---

### P1: Type Compatibility Checks

**What it is:** Validates that field types are compatible across boundaries, with configurable strictness levels.

**Key Components:**

| Type | Purpose | Location |
|------|---------|----------|
| `TypePolicyValidator` | Validates type pairs | `bridge/validation/TypePolicyValidator.scala` |
| `TypeNarrower` | Normalizes and compares types | `bridge/TypeNarrower.scala` |
| `TypePolicy` | Strictness level | `bridge/model/TypePolicy.scala` |

**Type Policies:**

```scala
sealed trait TypePolicy

object TypePolicy {
  // Types must match exactly (after normalization)
  case object Exact extends TypePolicy

  // Source can be narrower (INT → BIGINT)
  case object AllowWidening extends TypePolicy

  // Source can be wider (BIGINT → INT) - data loss possible
  case object AllowNarrowing extends TypePolicy

  // Allow numeric precision changes (DOUBLE → INT)
  case object AllowPrecisionLoss extends TypePolicy

  // Allow string truncation (VARCHAR(255) → VARCHAR(100))
  case object AllowTruncation extends TypePolicy

  // Allow nullable source to required domain (runtime check)
  case object AllowNullableToRequired extends TypePolicy
}
```

**Type Families:**

```
Integer Family: SMALLINT < INTEGER < BIGINT
  - Widening: SMALLINT → INTEGER ✓
  - Narrowing: BIGINT → INTEGER (with AllowNarrowing)

Float Family: REAL < DOUBLE < DECIMAL
  - Widening: REAL → DOUBLE ✓
  - Precision loss: DECIMAL → INTEGER (with AllowPrecisionLoss)

String Family: VARCHAR (all sizes)
  - Truncation: VARCHAR(255) → VARCHAR(100) (with AllowTruncation)

Timestamp Family: TIMESTAMP < TIMESTAMPTZ
  - Widening: TIMESTAMP → TIMESTAMPTZ ✓
```

**Type Normalization:**

The `TypeNarrower` normalizes database-specific types to canonical forms:

```scala
// All map to INTEGER
"INT4" | "INTEGER" | "INT" | "SERIAL" → "INTEGER"

// All map to BIGINT
"INT8" | "BIGINT" | "BIGSERIAL" | "LONG" → "BIGINT"

// All map to VARCHAR
"VARCHAR" | "TEXT" | "CHAR" | "NVARCHAR" → "VARCHAR"

// Proto types
"INT32" | "SINT32" | "SFIXED32" → "INTEGER"
"INT64" | "SINT64" | "SFIXED64" → "BIGINT"
"GOOGLE.PROTOBUF.TIMESTAMP" → "TIMESTAMPTZ"

// Avro types
"STRING" → "VARCHAR"
"BYTES" → "BYTEA"
```

**Canonical Type Result:**

```scala
case class CanonicalTypeResult(
    canonicalType: String,    // e.g., "INTEGER"
    jvmType: String,          // e.g., "Int"
    nullable: Boolean,        // Union of all sources
    warnings: List[String],   // Type mismatches found
    comment: String           // e.g., "widened from INT to BIGINT"
)
```

**Improvement Opportunities:**
- Custom type mappings (domain-specific types)
- Semantic type validation (email format, UUID format)
- Cross-database type recommendations
- Migration path suggestions when types differ

---

## Boundary Integration Status

### Database Boundary ✅

| Database | Tables | Views | Types | Arrays | JSON |
|----------|--------|-------|-------|--------|------|
| PostgreSQL | ✅ | ✅ | ✅ | ✅ | ✅ |
| MariaDB/MySQL | ✅ | ✅ | ✅ | ❌ | ✅ |
| Oracle | ✅ | ✅ | ✅ | ❌ | ❌ |
| SQL Server | ✅ | ✅ | ❌ | ❌ | ❌ |
| DuckDB | ✅ | ✅ | ❌ | ✅ | ✅ |
| DB2 | ✅ | ✅ | ❌ | ❌ | ❌ |

### API Boundary (OpenAPI) ✅

| Feature | Status |
|---------|--------|
| Schema parsing | ✅ |
| Type extraction | ✅ |
| Field alignment | ✅ |
| Mapper generation | ✅ |
| TUI browsing | ✅ |

### Events Boundary (Avro/Kafka) ✅

| Feature | Status | Component |
|---------|--------|-----------|
| Schema parsing | ✅ | `AvroParser` |
| Type extraction | ✅ | `AvroTypeMapper` |
| TUI source loading | ✅ | `SourceLoader` |
| TUI field extraction | ✅ | `ProjectionFieldExtractor` |
| TUI schema browser | ✅ | `AvroBrowser` |
| Type compatibility | ✅ | `TypeNarrower` |
| Mapper generation | ✅ | `BridgeAvroAdapter` |
| Validation | ✅ | `FlowValidator` |

### RPC Boundary (gRPC/Protobuf) ✅

| Feature | Status | Component |
|---------|--------|-----------|
| Protobuf parsing | ✅ | `ProtobufParser` |
| TUI source loading | ✅ | `SourceLoader` |
| TUI field extraction | ✅ | `ProjectionFieldExtractor` |
| TUI schema browser | ✅ | `ProtoBrowser` |
| Type compatibility | ✅ | `TypeNarrower` |
| Mapper generation | ✅ | `BridgeProtoAdapter` |
| Validation | ✅ | `FlowValidator` |

---

## Remaining Work

### P1: Missing Value Analysis ⬚

**Vision:** Detect fields that need defaults or transformations when flowing between sources.

**Use Cases:**
- Database has `created_at DEFAULT NOW()` but API doesn't provide it
- Avro record has required field that's optional in domain
- gRPC message has fields with default values

**Potential Implementation:**
```scala
case class MissingValueAnalysis(
    field: String,
    sources: List[SourceMissing]
)

case class SourceMissing(
    sourceKey: String,
    direction: FlowDirection,
    hasDefault: Boolean,
    defaultValue: Option[String],
    recommendation: Recommendation
)

enum Recommendation {
  UseSourceDefault,      // DB/Proto has default
  RequireExplicitValue,  // Must provide at runtime
  AddDomainDefault,      // Add default to domain type
  MarkNullable           // Change to optional
}
```

### P1: Nested Type Resolution ⬚

**Vision:** Domain types can reference other domain types, with automatic resolution.

**Use Cases:**
- `Order` contains `List[OrderItem]`
- `Customer` has `Address` field
- Hierarchical domain models

**Challenges:**
- Circular references
- Lazy loading vs eager loading
- Source-level joins/includes

### P2: Domain-Centric Repositories ⬚

**Vision:** Generate repositories that work with domain types directly, not database rows.

**Example:**
```kotlin
// Instead of:
val row: CustomerRow = customerRepo.findById(id)
val customer: Customer = row.toDomain()

// Generate:
val customer: Customer = customerRepo.findById(id)  // Returns domain directly
```

**Considerations:**
- Lazy vs eager conversion
- Partial loading (select specific fields)
- Batch operations
- Transaction boundaries

### P2: Domain-Centric Services ⬚

**Vision:** Generate service interfaces that orchestrate domain operations across boundaries.

**Example:**
```kotlin
interface CustomerService {
    fun create(customer: Customer): Customer
    fun update(customer: Customer): Customer
    fun delete(customerId: CustomerId)

    // Multi-boundary operations
    fun createAndPublish(customer: Customer): Customer  // DB + Kafka
    fun syncFromLegacy(customerId: CustomerId): Customer  // gRPC → DB
}
```

### P3: CRUD Endpoints ⬚

**Vision:** Generate REST endpoints from domain types.

**Example Output:**
```kotlin
@RestController
class CustomerController(private val service: CustomerService) {

    @GetMapping("/customers/{id}")
    fun get(@PathVariable id: Long): Customer =
        service.findById(CustomerId(id))

    @PostMapping("/customers")
    fun create(@RequestBody dto: CustomerDto): Customer =
        service.create(dto.toDomain())
}
```

### P3: Event Publishers/Consumers ⬚

**Vision:** Generate Kafka producers/consumers with domain type serialization.

**Example:**
```kotlin
// Producer
fun publish(customer: Customer) {
    val record = customer.toAvro()
    kafkaTemplate.send("customer-events", record)
}

// Consumer
@KafkaListener(topics = ["customer-events"])
fun handle(record: CustomerEvent) {
    val customer = record.toDomain()
    service.process(customer)
}
```

### P3: gRPC Services ⬚

**Vision:** Generate gRPC service implementations from domain types.

**Example:**
```kotlin
class CustomerGrpcService : CustomerServiceGrpc.CustomerServiceImplBase() {

    override fun getCustomer(
        request: GetCustomerRequest,
        responseObserver: StreamObserver<CustomerResponse>
    ) {
        val customer = service.findById(CustomerId(request.customerId))
        responseObserver.onNext(customer.toProto())
        responseObserver.onCompleted()
    }
}
```

---

## Architecture Principles

### 1. Single Source of Truth
Domain types are THE definition. All boundaries adapt to them.

### 2. Fail Fast
Validation at build time, not runtime. Type mismatches are caught before deployment.

### 3. Explicit Over Magic
Mappings are visible and auditable. No hidden conventions.

### 4. Progressive Enhancement
Start with auto-detection, customize as needed. Simple cases stay simple.

### 5. Language Agnostic
Same domain model generates idiomatic code for Scala, Kotlin, and Java.

---

## File Reference

### Core Types
- `typr/src/scala/typr/bridge/CompositeType.scala` - Domain type definitions
- `typr/src/scala/typr/bridge/model/*.scala` - Configuration models

### Name Matching
- `typr/src/scala/typr/bridge/ColumnTokenizer.scala` - Name tokenization
- `typr/src/scala/typr/bridge/ColumnStemmer.scala` - Abbreviation expansion

### Validation
- `typr/src/scala/typr/bridge/validation/FlowValidator.scala` - Main validator
- `typr/src/scala/typr/bridge/validation/SmartDefaults.scala` - Auto-matching
- `typr/src/scala/typr/bridge/validation/TypePolicyValidator.scala` - Type checking
- `typr/src/scala/typr/bridge/TypeNarrower.scala` - Type normalization

### Code Generation
- `typr-codegen/src/scala/typr/internal/codegen/FileBridgeProjectionMapper.scala` - Mapper generation
- `typr-codegen/src/scala/typr/internal/codegen/FileBridgeCompositeType.scala` - Type generation

### External Adapters
- `typr/src/scala/typr/avro/BridgeAvroAdapter.scala` - Avro integration
- `typr/src/scala/typr/grpc/BridgeProtoAdapter.scala` - Protobuf integration

### TUI
- `typr/src/scala/typr/cli/tui/screens/AvroBrowser.scala` - Avro schema browser
- `typr/src/scala/typr/cli/tui/screens/ProtoBrowser.scala` - Proto schema browser
- `typr/src/scala/typr/cli/tui/util/SourceLoader.scala` - Multi-source loading

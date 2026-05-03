# Bridge Backend: Architecture & Implementation

The Bridge backend is a pure API layer that powers `typr check` and will later drive the TUI, Studio, and MCP server. It is entirely pure Scala -- no IO, no TUI dependency. Callers provide materialized data; IO lives at the CLI command level only.

## Directory Layout

```
typr/src/scala/typr/bridge/
  model/                          # Data model: declarations, policies, check results
    SourceDeclaration.scala       # FlowDirection, SourceRole, SourceDeclaration
    FieldOverride.scala           # FieldOverride, FieldDirection, CustomKind
    TypePolicy.scala              # TypePolicy enum
    CheckResult.scala             # Severity, CheckCode, CheckFinding, CheckReport, EntitySummary
    ResolvedFlow.scala            # ResolvedFieldAction, ResolvedEntityFlow, ResolvedSourceFlow
  validation/                     # Pure validation functions
    TypePolicyValidator.scala     # Type policy enforcement with family ordering
    SmartDefaults.scala           # Auto-resolution of field actions
    FlowValidator.scala           # Full entity validation producing CheckFindings
  api/                            # Public API surface
    BridgeApi.scala               # Trait: check(), resolveFlows()
    BridgeApiImpl.scala           # Composes validation modules
  ConfigToBridge.scala            # Config YAML -> bridge model conversion
  TypeNarrower.scala              # (modified) Type normalization + compatibility
  CompositeType.scala             # (modified) typeCompatible now real

typr/src/scala/typr/cli/
  commands/Check.scala            # `typr check` CLI command
  Main.scala                      # (modified) checkCmd added

tests/src/scala/typr/bridge/
  TypePolicyValidatorTest.scala   # 15 tests
  TypeNarrowerIntegrationTest.scala # 14 tests
  SmartDefaultsTest.scala         # 7 tests
  FlowValidatorTest.scala         # 14 tests

typr-config.schema.json           # (modified) direction, type_policy, field_overrides
```

---

## Core Concepts

### Domain Types

A **domain type** is an entity that exists across multiple sources (databases, APIs, event schemas). It has:

- A **primary source**: the anchor entity this type is grounded in (e.g. `pg:sales.customer`)
- **Fields**: named fields with canonical types (`Int`, `String`, `Long`, `Boolean`, etc.)
- **Aligned sources**: other source entities mapped to/from this domain type

These already existed in `CompositeType.scala` as `DomainTypeDefinition`, `DomainField`, `AlignedSource`, etc.

### Source Declarations

A **SourceDeclaration** (new) enriches an aligned source with Bridge-specific metadata:

```scala
case class SourceDeclaration(
    sourceName: String,              // "pg", "api", "kafka"
    entityPath: String,              // "sales.customer", "Customer"
    role: SourceRole,                // Primary | Aligned
    direction: FlowDirection,        // In | Out | InOut
    mode: CompatibilityMode,         // Exact | Superset | Subset
    mappings: Map[String, String],   // domain field -> source field
    exclude: Set[String],            // source fields to ignore
    includeExtra: List[String],      // extra source fields for toSource mapper
    readonly: Boolean,
    defaultTypePolicy: TypePolicy,   // default policy for all fields
    fieldOverrides: Map[String, FieldOverride]  // per-field exceptions
)
```

Key design: `fieldOverrides` is **sparse**. Most fields use smart defaults. Only explicitly annotated exceptions appear here.

### Flow Direction

Each source has a direction indicating how data flows relative to the domain type:

| Direction | Meaning | Missing field = |
|-----------|---------|-----------------|
| `In`      | Data flows from source into domain | Warning (source doesn't provide it) |
| `Out`     | Data flows from domain out to source | Error (you promised to produce it) |
| `InOut`   | Bidirectional | Error (must be covered both ways) |

### Type Policy

Each source (or individual field) has a type policy controlling what type differences are allowed:

| Policy | Rule |
|--------|------|
| `Exact` | Types must match exactly after normalization |
| `AllowWidening` | Source can be narrower than domain (SMALLINT -> INT ok) |
| `AllowNarrowing` | Source can be wider than domain (BIGINT -> INT ok) |
| `AllowPrecisionLoss` | Any numeric-to-numeric is ok (DECIMAL -> FLOAT) |
| `AllowTruncation` | Any string-to-string is ok |
| `AllowNullableToRequired` | Only validates nullability, skips type check |

Policies are enforced within **type families**. Cross-family (VARCHAR -> INTEGER) always fails regardless of policy.

### Field Overrides

Per-field exceptions to smart defaults:

```scala
sealed trait FieldOverride
  Forward(sourceFieldName, typePolicy, directionOverride)  // explicit forward mapping
  Drop(reason)                                              // explicitly excluded
  Custom(kind)                                              // special handling
```

Custom kinds:
- `MergeFrom(sourceFields)` -- combine multiple source fields into one domain field
- `SplitFrom(sourceField)` -- split one source field into this domain field
- `ComputedFrom(domainFields)` -- derived from other domain fields
- `Enrichment(description)` -- populated by external logic

---

## Type System

### Canonical Types

Domain fields use canonical type names: `Int`, `Long`, `String`, `Boolean`, `BigDecimal`, `UUID`, `LocalDate`, `OffsetDateTime`, `Instant`, `Json`, `ByteArray`, etc.

Source fields use database type names: `integer`, `bigint`, `varchar`, `text`, `timestamp with time zone`, `jsonb`, etc.

### Type Normalization

`TypeNarrower` bridges these two worlds:

**`mapCanonicalToNormalized(canonical: String): String`** (new) maps domain types to normalized DB names:
```
Int            -> INTEGER
Long           -> BIGINT
String         -> VARCHAR
BigDecimal     -> DECIMAL
OffsetDateTime -> TIMESTAMPTZ
Instant        -> TIMESTAMPTZ
UUID           -> UUID
ByteArray      -> BYTEA
```

**`normalizeDbType(dbType: String): String`** (now public) normalizes database-specific types:
```
int4, integer, int, serial   -> INTEGER
int8, bigint, bigserial      -> BIGINT
varchar, text, char, bpchar  -> VARCHAR
numeric, decimal             -> DECIMAL
timestamp with time zone     -> TIMESTAMPTZ
```

### Type Families

Types are grouped into families for compatibility checking:

| Family | Members | Ordering (narrow -> wide) |
|--------|---------|---------------------------|
| Integer | SMALLINT, INTEGER, BIGINT | 0, 1, 2 |
| Float | REAL, DOUBLE, DECIMAL | 0, 1, 2 |
| String | VARCHAR | (single member) |
| Timestamp | TIMESTAMP, TIMESTAMPTZ | 0, 1 |
| Time | TIME, TIMETZ | 0, 1 |

`AllowWidening` means: source ordinal <= domain ordinal (source is same or narrower).
`AllowNarrowing` means: source ordinal >= domain ordinal (source is same or wider).
`AllowPrecisionLoss` allows any integer <-> float crossing.

### typeCompatible Fix

`AlignmentComputer.typeCompatible` (previously a stub returning `true`) now uses real type checking:

```scala
private def typeCompatible(domainField: DomainField, sourceField: SourceField): Boolean = {
  val domainNorm = TypeNarrower.mapCanonicalToNormalized(domainField.typeName)
  val sourceNorm = TypeNarrower.normalizeDbType(sourceField.typeName)
  domainNorm == sourceNorm || TypeNarrower.areTypesCompatible(domainNorm, sourceNorm)
}
```

This means `AlignmentComputer.computeAlignment()` now correctly detects `TypeMismatch` for incompatible types (e.g. `String` domain field vs `integer` source field).

---

## Validation Pipeline

The validation pipeline has three layers, each pure:

### 1. SmartDefaults

```
SmartDefaults.resolveFieldActions(domainType, sourceDecl, sourceEntity, nameAligner)
  -> List[ResolvedFieldAction]
```

For each **domain field**, in order:

1. If an explicit `FieldOverride` exists for this field, use it directly
2. Else try name alignment (explicit mappings first, then `NameAligner` auto-matching)
   - **Match found**: `Forward(isAutoMatched=true)` with the source's default type policy
   - **No match**: `Drop(isAutoDropped=true)`

For each **source field** not consumed by any domain field:

- If in `exclude` or `includeExtra` set -> skip
- If mode is `Superset` or `Subset` -> skip (allowed)
- If mode is `Exact` -> `Unannotated` (needs human decision)

The result is a `List[ResolvedFieldAction]` with four variants:

```
Forward(domainField, sourceField, typePolicy, isAutoMatched)
Drop(domainField, reason, isAutoDropped)
Custom(domainField, kind)
Unannotated(sourceField, sourceType, sourceNullable)
```

### 2. TypePolicyValidator

```
TypePolicyValidator.validateWithCanonical(domainCanonicalType, sourceDbType, policy)
  -> Either[String, Unit]
```

Given a canonical domain type, a database source type, and a policy, this either passes or returns an error message. It:

1. Normalizes both types through `mapCanonicalToNormalized` and `normalizeDbType`
2. Applies the policy rules using family membership and ordering
3. Cross-family comparisons always fail (except `AllowNullableToRequired`)

### 3. FlowValidator

```
FlowValidator.validateEntity(domainType, sourceDeclarations, sourceEntities, nameAligner)
  -> List[CheckFinding]
```

This is the top-level validator that produces a flat list of findings. It checks:

**Structural checks:**
- Domain type has at least one field (`NoFields`)
- At least one source has `role = Primary` (`NoPrimarySource`)
- Each declared source entity exists in the provided data (`SourceEntityNotFound`)

**Per-source checks** (calls SmartDefaults, then validates each resolved action):

- `Forward` fields:
  - Source field must exist (`MissingRequiredField`)
  - Type must pass policy validation (`TypeIncompatible` for Exact, `TypePolicyViolation` for others)
  - Nullability must be compatible unless `AllowNullableToRequired` (`NullabilityMismatch`)

- `Drop` fields on out/inout sources: Warning that data will be lost

- `Custom(MergeFrom)`: all referenced source fields must exist (`InvalidMergeFromRef`)
- `Custom(SplitFrom)`: referenced source field must exist (`InvalidSplitFromRef`)
- `Custom(ComputedFrom)`: all referenced domain fields must exist (`InvalidComputedFromRef`)

- `Unannotated`: source field exists but has no mapping (`UnannotatedField`)

**Direction validation** (separate pass after SmartDefaults):

For each domain field NOT covered by a Forward or Custom action:
- `Out` source: Error -- you promised to produce this field
- `InOut` source: Error -- bidirectional requires coverage
- `In` source: Warning -- source doesn't provide it

---

## Check Codes

| Code | Severity | Meaning |
|------|----------|---------|
| `NoFields` | Error | Domain type has zero fields |
| `NoPrimarySource` | Error | No source declared as primary |
| `SourceEntityNotFound` | Error | Declared source entity doesn't exist |
| `UnannotatedField` | Error | Source field in exact mode not mapped to anything |
| `MissingRequiredField` | Error/Warning | Field not covered (error for out, warning for in) |
| `TypeIncompatible` | Error | Types don't match under Exact policy |
| `TypePolicyViolation` | Error | Types don't satisfy the declared policy |
| `NullabilityMismatch` | Error | Required in domain but nullable in source |
| `InvalidMergeFromRef` | Error | MergeFrom references nonexistent source field |
| `InvalidSplitFromRef` | Error | SplitFrom references nonexistent source field |
| `InvalidComputedFromRef` | Error | ComputedFrom references nonexistent domain field |

---

## BridgeApi

The public API surface:

```scala
trait BridgeApi {
  def check(
      domainTypes: Map[String, DomainTypeDefinition],
      sourceDeclarations: Map[String, Map[String, SourceDeclaration]],
      sourceEntities: Map[String, Map[String, SourceEntity]],
      nameAligner: NameAligner
  ): CheckReport

  def resolveFlows(
      domainType: DomainTypeDefinition,
      sourceDeclarations: Map[String, SourceDeclaration],
      sourceEntities: Map[String, SourceEntity],
      nameAligner: NameAligner
  ): ResolvedEntityFlow
}
```

**`check()`** iterates all domain types, runs `FlowValidator.validateEntity` for each, and collects:
- `findings`: flat list of all `CheckFinding` across all entities
- `entitySummaries`: per-entity summary with field/source/forward/drop/custom/error/warning counts
- `exitCode`: 0 if no errors, 1 if any errors

**`resolveFlows()`** returns the resolved field actions for a single domain type across all its sources, without generating findings. Useful for UI display (showing what will happen to each field).

**Data flow in `check()`:**

```
sourceDeclarations[entityName] -> Map[sourceKey, SourceDeclaration]
sourceEntities[sourceName][entityPath] -> SourceEntity
                                            |
    flattenSourceEntities() joins these into Map[sourceKey, SourceEntity]
                                            |
    FlowValidator.validateEntity() -------> List[CheckFinding]
    SmartDefaults.resolveFieldActions() --> action counts for EntitySummary
```

---

## Config Schema Extensions

Three new properties added to `alignedSource` in `typr-config.schema.json`:

```yaml
# typr.yaml example
types:
  Customer:
    kind: domain
    primary: "pg:sales.customer"
    fields:
      id: Long
      name: String
      email: "String?"
    alignedSources:
      "api:Customer":
        entity: Customer
        mode: superset
        direction: out              # NEW: in | out | in-out
        type_policy: allow_widening # NEW: controls default type checking
        field_overrides:            # NEW: per-field exceptions
          email: drop
          full_name:
            action: custom
            merge_from: [first_name, last_name]
          legacy_id:
            action: forward
            source_field: old_id
            type_policy: allow_narrowing
```

Field overrides support two syntaxes:

**Short form**: `"forward"` or `"drop"` as a string

**Long form**: object with `action`, `source_field`, `type_policy`, `reason`, `direction`, `merge_from`, `split_from`, `computed_from`, `enrichment`

---

## ConfigToBridge

Converts parsed config types into bridge model types. Handles:

- `convertAlignedSource()`: AlignedSource + new fields -> SourceDeclaration
- `convertPrimarySource()`: primary key -> SourceDeclaration with Primary role
- `convertDomainTypeDefinition()`: full domain type -> Map of all SourceDeclarations
- `parseFieldOverrides()`: JSON map -> Map[String, FieldOverride], handling both short and long forms

The generated config types (`typr.config.generated.AlignedSource`) don't yet have the new fields (`direction`, `type_policy`, `field_overrides`) since `bleep generate-sources` hasn't been re-run. The Check command currently defaults direction to `InOut`, type policy to `Exact`, and field overrides to empty. After regeneration, ConfigToBridge will be able to read them from the parsed YAML.

---

## `typr check` CLI Command

```
typr check [--config typr.yaml] [--quiet] [--debug]
```

What it does:

1. Reads and parses `typr.yaml` (with env var substitution)
2. Extracts all domain type definitions from the `types` section
3. Builds SourceDeclarations from primary + aligned sources
4. Calls `BridgeApi.check()` with empty source entities (config-only validation for now)
5. Renders findings grouped by errors/warnings
6. Returns exit code 0 (pass) or 1 (errors found)

Output format:
```
ERRORS (3):
  [TypeIncompatible] Customer > source=api:Customer > field=amount
    Exact policy requires matching types but got domain=VARCHAR, source=INTEGER
    Suggestion: Adjust the type policy or update the field type

WARNINGS (1):
  [MissingRequiredField] Customer > source=kafka:events > field=email
    In-source 'kafka:events' does not provide field 'email'

Entity Summary:
  Customer: 5 fields, 3 sources, 8 forward, 2 drop, 1 custom
    3 errors, 1 warnings

Check FAILED: 3 error(s), 1 warning(s)
```

Wired into Main.scala as the second subcommand after `generate`:
```
typr generate ...
typr check ...
typr interactive ...
typr watch ...
typr init
```

---

## Test Coverage

All tests are pure (no database needed). 50 tests across 4 suites:

### TypePolicyValidatorTest (15 tests)

- Exact: matching passes, mismatch fails, normalized equivalents pass
- AllowWidening: narrower source passes, wider source fails, cross-family fails
- AllowNarrowing: wider source passes, narrower source fails, cross-family fails
- AllowPrecisionLoss: numeric-to-numeric passes, string-to-int fails
- AllowTruncation: string-to-string passes, string-to-int fails
- AllowNullableToRequired: always passes type check
- `validateWithCanonical`: canonical `Int` + db `bigint` with policies
- Cross-family always fails regardless of policy (except AllowNullableToRequired)

### TypeNarrowerIntegrationTest (14 tests)

- `mapCanonicalToNormalized`: all canonical types map correctly (Int->INTEGER, String->VARCHAR, etc.)
- `normalizeDbType`: int4->INTEGER, serial->INTEGER, text->VARCHAR, numeric->DECIMAL
- `areTypesCompatible`: INTEGER/BIGINT compatible, VARCHAR/INTEGER not, TIMESTAMP/TIMESTAMPTZ compatible
- AlignmentComputer integration: Int domain + bigint source -> Aligned(true), String domain + integer source -> TypeMismatch

### SmartDefaultsTest (7 tests)

- Field present in both: auto-forward with isAutoMatched=true
- Field in domain but not source: auto-drop with isAutoDropped=true
- Explicit Forward override: uses specified source_field and type_policy
- Explicit Drop override: uses specified reason
- Source field not in domain, Exact mode: produces Unannotated
- Source field not in domain, Superset mode: silently ignored
- Source field in exclude: ignored even in Exact mode
- MergeFrom override: resolved as Custom

### FlowValidatorTest (14 tests)

- Clean config (all fields auto-forward, compatible types): zero errors
- Missing primary source: NoPrimarySource error
- Source entity not found: SourceEntityNotFound error
- Unannotated field in Exact mode: UnannotatedField error with suggestion
- Type mismatch with Exact policy: TypeIncompatible error
- Type mismatch with correct AllowWidening policy: no error
- Type mismatch with wrong AllowWidening policy: TypePolicyViolation error
- Drop on out-source: Warning
- MergeFrom referencing nonexistent source field: InvalidMergeFromRef error
- ComputedFrom referencing nonexistent domain field: InvalidComputedFromRef error
- Out source missing domain field: MissingRequiredField error
- In source missing domain field: Warning only
- InOut source missing domain field: MissingRequiredField error
- No fields: NoFields error
- Nullability mismatch: required domain + nullable source -> NullabilityMismatch
- End-to-end multi-source: Customer with pg (primary, InOut) + api (aligned, Out)

---

## What's Next

Things that need to happen to make this fully operational:

1. **`bleep generate-sources`**: Regenerate config types so `AlignedSource` gets the new `direction`, `type_policy`, `field_overrides` fields from the updated JSON schema. Then update Check.scala to read them instead of defaulting.

2. **Source entity loading in Check**: Currently `typr check` passes empty `sourceEntities`. The next step is to connect to databases (reusing Generate's source fetching) to build `SourceEntity` instances from MetaDb, so check validates against live schemas.

3. **TUI integration**: `BridgeApi.resolveFlows()` returns `ResolvedEntityFlow` which the TUI can render as a field-by-field mapping view.

4. **Studio / MCP**: The pure API is ready to be called from a web server or MCP endpoint. All IO stays at the caller level.

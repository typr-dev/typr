---
title: Limitations
---

# Limitations

Known limitations and workarounds.

## Schema Limitations

### Recursive Types

Recursive types are supported but have limitations:

```json
{
  "type": "record",
  "name": "TreeNode",
  "fields": [
    {"name": "value", "type": "string"},
    {"name": "children", "type": {"type": "array", "items": "TreeNode"}}
  ]
}
```

**Works:** Self-referential through arrays or maps.

**Limitation:** Direct self-reference without container may cause issues in some languages.

### Default Values

Not all Avro default value types are fully supported:

| Default Type | Support |
|--------------|---------|
| Primitives | Full |
| Null | Full |
| Empty array `[]` | Full |
| Empty map `{}` | Full |
| Complex defaults | Partial |

### Schema Evolution

Typr Events generates code from schemas at generation time. Schema evolution rules:

- **Adding optional fields:** Safe (use `["null", "T"]` with default)
- **Removing fields:** Requires regeneration
- **Renaming fields:** Breaking change
- **Changing types:** Breaking change

Use Confluent Schema Registry for runtime compatibility checking.

## Language Limitations

### Java

- Requires Java 21+ for pattern matching on sealed types
- Records are final (cannot extend)
- No inline/value classes (wrapper types are full objects)

### Kotlin

- `value class` has boxing overhead in some scenarios
- Nullable primitives box to wrapper types

### Scala

- Opaque types require Scala 3
- Cross-compilation between Scala 2 and 3 may have edge cases

## Wire Format Limitations

### Confluent Avro

- Requires Schema Registry infrastructure
- Schema ID overhead (5 bytes per message)
- Schema compatibility modes affect what changes are allowed

### Plain Avro

- No automatic schema evolution
- Reader must know writer schema
- No schema versioning

### JSON

- Larger message sizes
- No schema validation at wire level
- Union type discrimination can be ambiguous

## Framework Integration

### Spring

- `ReplyingKafkaTemplate` has timeout limitations
- Reply topic partitioning must be handled carefully for scaling

### Quarkus

- `KafkaRequestReply` is relatively new
- Native compilation may have reflection issues

## Performance Considerations

### Wrapper Types

Wrapper types add minimal overhead:
- Java: Object allocation
- Kotlin: Inline classes avoid allocation in most cases
- Scala: Opaque types have zero runtime overhead

### Precise Decimal Types

Validation on construction adds overhead. Use `unsafeForce` when you've already validated.

### Multi-Event Topics

Pattern matching on sealed types is highly optimized by JVM.

## Workarounds

### Schema Evolution

For breaking changes:
1. Create new topic/schema version
2. Deploy consumers first (handle both versions)
3. Deploy producers
4. Migrate data if needed

### Complex Defaults

If complex defaults aren't supported:
1. Use `["null", "T"]` with `null` default
2. Handle missing values in application code

### Recursive Structures

For deep recursion:
1. Consider depth limits
2. Use iterative algorithms instead of recursive
3. Watch for stack overflow on very deep structures

## Reporting Issues

If you encounter limitations not documented here:

1. Check GitHub issues for existing reports
2. Create minimal reproduction case
3. Include: Schema, options, generated code, error message

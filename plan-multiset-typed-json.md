# Plan: Typed JSON Support for MULTISET in typo-dsl-java

## Overview

Currently, `multisetOn` returns `Tuple2<Row, Json>` where `Json` is a raw string wrapper. This plan implements fully typed MULTISET that returns `Tuple2<Row, List<Row2>>` with automatic JSON parsing.

## Key Decisions

- **Return type**: `Tuple2<Row, List<Row2>>` - fully typed child rows
- **JSON integration**: Like Dialect - generated RepoImpl plugs in a generated JsonDecoder instance
- **JSON format**: jOOQ's compact array-of-arrays: `[[val1, val2], [val3, val4], ...]`
- **JSON library**: Exactly one required (change `jsonLibs: List[JsonLibName]` to `jsonLib: JsonLibName`)
- **JsonDecoder**: Separate generated class per row type
- **Column ordering**: Generate SQL and decoder from same column list
- **Nulls**: JSON null

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         typo-dsl-java                                   │
├─────────────────────────────────────────────────────────────────────────┤
│ Dialect (extended)                                                      │
│   + jsonBuildArray(List<Fragment> values) -> Fragment                   │
│   + jsonArrayAgg(Fragment arrayExpr) -> Fragment                        │
│   + emptyJsonArray() -> Fragment                                        │
├─────────────────────────────────────────────────────────────────────────┤
│ SelectBuilder                                                           │
│   multisetOn<F2,R2>(other, pred) ->                                     │
│       SelectBuilder<Tuple2<F,F2>, Tuple2<R, List<R2>>>                  │
│                                                                         │
│ SelectBuilderSql                                                        │
│   - Holds JsonDecoder<Row> for its row type                             │
│   - Uses child's JsonDecoder when building multiset parser              │
│   - Generates compact [[v1,v2],[v3,v4]] SQL format                      │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                       typo-runtime-java                                 │
├─────────────────────────────────────────────────────────────────────────┤
│ JsonDecoder<T>                                                          │
│   + decodeArray(Json json) -> List<T>                                   │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                     Generated Code (per table)                          │
├─────────────────────────────────────────────────────────────────────────┤
│ PersonRowJsonDecoder implements JsonDecoder<PersonRow>                  │
│   - Generated based on chosen jsonLib (Jackson/Gson/etc)                │
│   - Decodes [[v1,v2,...],[v3,v4,...]] into List<PersonRow>              │
│   - Column order matches SQL generation order                           │
├─────────────────────────────────────────────────────────────────────────┤
│ PersonRepoImpl                                                          │
│   select() {                                                            │
│     return SelectBuilder.of(                                            │
│       tableName, structure, rowParser, dialect,                         │
│       PersonRowJsonDecoder.INSTANCE  // NEW                             │
│     );                                                                   │
│   }                                                                      │
└─────────────────────────────────────────────────────────────────────────┘
```

## Implementation Steps

### Step 1: Change Options.jsonLibs to required jsonLib

**File**: `typo/src/scala/typo/Options.scala`

```scala
// Before:
jsonLibs: List[JsonLibName] = Nil

// After:
jsonLib: JsonLibName
```

Update all usages throughout codebase. This is a breaking change for Options API.

### Step 2: Extend Dialect with JSON array methods

**File**: `typo-dsl-java/src/java/typo/dsl/Dialect.java`

```java
/**
 * Build a JSON array from values: json_build_array(v1, v2, ...) or JSON_ARRAY(v1, v2, ...)
 */
Fragment jsonBuildArray(List<Fragment> values);

/**
 * Aggregate JSON arrays: json_agg(expr) or JSON_ARRAYAGG(expr)
 */
Fragment jsonArrayAgg(Fragment expr);

/**
 * Empty JSON array literal: '[]'::json or JSON_ARRAY()
 */
Fragment emptyJsonArray();

/**
 * COALESCE wrapper for null handling
 */
default Fragment coalesceJsonArray(Fragment jsonAgg) {
    return Fragment.lit("COALESCE(")
        .append(jsonAgg)
        .append(Fragment.lit(", "))
        .append(emptyJsonArray())
        .append(Fragment.lit(")"));
}
```

PostgreSQL implementation:
- `jsonBuildArray` -> `json_build_array(v1, v2, ...)`
- `jsonArrayAgg` -> `json_agg(expr)`
- `emptyJsonArray` -> `'[]'::json`

MariaDB implementation:
- `jsonBuildArray` -> `JSON_ARRAY(v1, v2, ...)`
- `jsonArrayAgg` -> `JSON_ARRAYAGG(expr)`
- `emptyJsonArray` -> `JSON_ARRAY()`

### Step 3: Create JsonDecoder interface

**File**: `typo-runtime-java/src/java/typo/runtime/JsonDecoder.java`

```java
package typo.runtime;

import typo.data.Json;
import java.util.List;

/**
 * Decodes JSON arrays into typed Java objects.
 * Used by MULTISET to parse nested collections from compact [[v1,v2],[v3,v4]] format.
 */
public interface JsonDecoder<T> {
    /**
     * Decode a JSON array-of-arrays into a list of typed objects.
     * @param json JSON in format [[v1,v2,...],[v3,v4,...],...]
     * @return List of decoded objects
     */
    List<T> decodeArray(Json json);
}
```

### Step 4: Update MULTISET SQL generation

**File**: `typo-dsl-java/src/java/typo/dsl/SelectBuilderSql.java`

Change `buildMultisetSubquery` to use compact format:

```sql
-- Before (object format):
SELECT COALESCE(json_agg(jsonb_build_object(
    'businessentityid', col0,
    'emailaddress', col1
)), '[]'::json)

-- After (array format):
SELECT COALESCE(json_agg(json_build_array(
    col0,
    col1
)), '[]'::json)
```

Use new Dialect methods instead of hardcoded SQL strings.

### Step 5: Update SelectBuilder to carry JsonDecoder

**File**: `typo-dsl-java/src/java/typo/dsl/SelectBuilder.java`

```java
// Factory method gains jsonDecoder parameter
static <Fields, Row> SelectBuilder<Fields, Row> of(
    String tableName,
    Structure<Fields, Row> structure,
    RowParser<Row> rowParser,
    Dialect dialect,
    JsonDecoder<Row> jsonDecoder  // NEW
);

// Interface needs method to access decoder
JsonDecoder<Row> jsonDecoder();
```

**File**: `typo-dsl-java/src/java/typo/dsl/SelectBuilderSql.java`

```java
// Constructor stores jsonDecoder
private final JsonDecoder<Row> jsonDecoder;

// multisetOn uses child's decoder
<Fields2, Row2> SelectBuilder<Tuple2<Fields, Fields2>, Tuple2<Row, List<Row2>>>
    multisetOn(SelectBuilder<Fields2, Row2> other, ...) {
    JsonDecoder<Row2> childDecoder = other.jsonDecoder();
    // Use childDecoder when building combined row parser
}
```

### Step 6: Update RowParser for List<Row2>

In `MultisetSelectBuilder.collectQuery()`, change the combined parser:

```java
// Before:
Tuple2<Row1, Json>

// After:
Tuple2<Row1, List<Row2>>

// The decode function:
Function<Object[], Tuple2<Row1, List<Row2>>> decode = values -> {
    Row1 parentRow = parentDecoder.apply(parentValues);
    Json jsonValue = (Json) values[parentColCount];
    List<Row2> children = childJsonDecoder.decodeArray(jsonValue);
    return Tuple2.of(parentRow, children);
};
```

### Step 7: Generate JsonDecoder implementations

**New file pattern**: `{RowName}JsonDecoder.java`

For Jackson (example):
```java
package adventureworks.person.emailaddress;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import typo.data.Json;
import typo.runtime.JsonDecoder;
import java.util.ArrayList;
import java.util.List;

public class EmailaddressRowJsonDecoder implements JsonDecoder<EmailaddressRow> {
    public static final EmailaddressRowJsonDecoder INSTANCE = new EmailaddressRowJsonDecoder();
    private static final ObjectMapper mapper = new ObjectMapper();

    private EmailaddressRowJsonDecoder() {}

    @Override
    public List<EmailaddressRow> decodeArray(Json json) {
        try {
            JsonNode arr = mapper.readTree(json.value());
            List<EmailaddressRow> result = new ArrayList<>();
            for (JsonNode row : arr) {
                result.add(new EmailaddressRow(
                    new BusinessentityId(row.get(0).asInt()),  // col 0: businessentityid
                    row.get(1).asInt(),                         // col 1: emailaddressid
                    row.get(2).isNull() ? null : row.get(2).asText(), // col 2: emailaddress (nullable)
                    java.util.UUID.fromString(row.get(3).asText()),   // col 3: rowguid
                    java.time.LocalDateTime.parse(row.get(4).asText()) // col 4: modifieddate
                ));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode EmailaddressRow from JSON", e);
        }
    }
}
```

Column order MUST match the order used in SQL generation (from `cols` list).

### Step 8: Update RepoImpl generation

**File**: `typo/src/scala/typo/internal/codegen/DbLibTypo.scala`

Update `RepoMethod.SelectBuilder` case:

```scala
case RepoMethod.SelectBuilder(relName, fieldsType, rowType) =>
  val structure = prop(code"$fieldsType", "structure")
  val jsonDecoder = code"${rowType}JsonDecoder.INSTANCE"
  jvm.Body.Expr(
    code"${jvm.Type.dsl.SelectBuilder}.of(${jvm.StrLit(quotedRelNameStr(relName))}, $structure, $rowType.$rowParserName, ${adapter.dialectRef}, $jsonDecoder)"
  )
```

### Step 9: Generate JsonDecoder file in code generation

Add new file generation in `FilesTable.scala` or similar:

```scala
// Generate JsonDecoder for each row type
val jsonDecoderFile = jsonLib match {
  case JsonLibName.Jackson => generateJacksonDecoder(table, cols)
  case JsonLibName.Circe => generateCirceDecoder(table, cols)
  case JsonLibName.PlayJson => generatePlayJsonDecoder(table, cols)
  case JsonLibName.ZioJson => generateZioJsonDecoder(table, cols)
}
```

## SQL Format Examples

### PostgreSQL

```sql
SELECT
  person0."businessentityid",
  person0."firstname",
  (SELECT COALESCE(json_agg(json_build_array(
      email0."businessentityid",
      email0."emailaddressid",
      email0."emailaddress",
      email0."rowguid",
      email0."modifieddate"
  )), '[]'::json)
   FROM person.emailaddress email0
   WHERE person0."businessentityid" = email0."businessentityid"
  ) as emails
FROM person.person person0
```

### MariaDB

```sql
SELECT
  person0.`businessentityid`,
  person0.`firstname`,
  (SELECT COALESCE(JSON_ARRAYAGG(JSON_ARRAY(
      email0.`businessentityid`,
      email0.`emailaddressid`,
      email0.`emailaddress`,
      email0.`rowguid`,
      email0.`modifieddate`
  )), JSON_ARRAY())
   FROM person.emailaddress email0
   WHERE person0.`businessentityid` = email0.`businessentityid`
  ) as emails
FROM person.person person0
```

## JSON Output Format

```json
[
  [1, 1, "bob@work.com", "550e8400-e29b-41d4-a716-446655440000", "2024-01-15T10:30:00"],
  [1, 2, "bob@home.com", "550e8400-e29b-41d4-a716-446655440001", "2024-01-15T10:31:00"]
]
```

- Array of arrays (not objects)
- Column order matches SELECT order
- Nulls represented as JSON `null`
- Dates/times as ISO strings (database default)

## Files to Create/Modify

### New Files
- `typo-runtime-java/src/java/typo/runtime/JsonDecoder.java`
- Generated: `{Package}/{RowName}JsonDecoder.java` (one per table)

### Modified Files
- `typo/src/scala/typo/Options.scala` - jsonLibs -> jsonLib
- `typo/src/scala/typo/internal/InternalOptions.scala` - update accordingly
- `typo-dsl-java/src/java/typo/dsl/Dialect.java` - add JSON methods
- `typo-dsl-java/src/java/typo/dsl/SelectBuilder.java` - add jsonDecoder parameter
- `typo-dsl-java/src/java/typo/dsl/SelectBuilderSql.java` - store decoder, use for multiset
- `typo/src/scala/typo/internal/codegen/DbLibTypo.scala` - update SelectBuilder generation
- `typo/src/scala/typo/internal/codegen/FilesTable.scala` - generate JsonDecoder files
- Various codegen files that reference jsonLibs

## Test Updates

Update existing multiset tests:
- Change assertions from `Json` to `List<Row>`
- Verify parsed rows have correct values
- Test empty child sets return empty List (not null)
- Test nullable columns parsed correctly

## Future Considerations (not in scope)

- Type conversions for complex types (BigDecimal, byte[], custom types)
- Nested multiset (multiset within multiset)
- Mock implementation for multiset

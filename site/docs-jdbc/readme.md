---
title: Introduction to Foundations JDBC
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Foundations JDBC

A JDBC wrapper library that makes JDBC actually usable. We've modeled JDBC to perfection so you can finally use all column types correctly across all supported databases.

## The Problem with JDBC

JDBC is notoriously difficult to use correctly. The API is verbose, error-prone, and makes it almost impossible to handle all column types properly. Most JDBC code in the wild has subtle bugs around:

- **Null handling** - Was that column nullable? Did you check for `wasNull()`?
- **Type conversions** - Does `getObject()` return what you expect? Spoiler: often not.
- **Resource management** - Did you close that ResultSet? Statement? Connection?
- **Database differences** - Code that works on PostgreSQL may fail on Oracle

Even experienced developers struggle to write JDBC code that correctly handles all edge cases.

## Our Solution

We've built a type-safe abstraction layer that models every column type across all supported databases with full roundtrip support. This means you can read a value from the database and write it back without loss or corruption - something that sounds obvious but is surprisingly hard to achieve with raw JDBC.

## Supported Databases

- **PostgreSQL** - including arrays, ranges, JSON, geometric types, network types, and all the exotic ones
- **MariaDB/MySQL** - including unsigned types, sets, spatial types, and JSON
- **DuckDB** - including lists, maps, structs, unions, and nested types
- **Oracle** - including OBJECT types, VARRAYs, nested tables, and intervals
- **SQL Server** - including geography, geometry, hierarchyid, and all standard types

## Key Features

### Type-Safe Database Types

Each database has its own type hierarchy that knows exactly how to read and write values:

<Tabs groupId="language">
<TabItem value="java" label="Java">

```java
// PostgreSQL types
PgType<int[]> intArray = PgTypes.int4.array();
PgType<Range<LocalDate>> dateRange = PgTypes.daterange;
PgType<PGpoint> point = PgTypes.point;
PgType<PGcircle> circle = PgTypes.circle;

// MariaDB types
MariaType<JsonNode> json = MariaTypes.json(mapper);
MariaType<Set<String>> set = MariaTypes.set(MyEnum.class);

// Oracle types
OracleType<List<MyObject>> nested = OracleTypes.nestedTable("MY_TYPE", myObjectType);
OracleType<MyStruct> object = OracleTypes.object("MY_OBJECT", myStructCodec);

// DuckDB types
DuckDbType<Map<String, Integer>> map = DuckDbTypes.map(DuckDbTypes.varchar, DuckDbTypes.integer);
DuckDbType<List<String>> list = DuckDbTypes.list(DuckDbTypes.varchar);
```

</TabItem>
<TabItem value="kotlin" label="Kotlin">

```kotlin
// PostgreSQL types
val intArray: PgType<IntArray> = PgTypes.int4.array()
val dateRange: PgType<Range<LocalDate>> = PgTypes.daterange
val point: PgType<PGpoint> = PgTypes.point

// MariaDB types
val json: MariaType<JsonNode> = MariaTypes.json(mapper)

// Oracle types
val nested: OracleType<List<MyObject>> = OracleTypes.nestedTable("MY_TYPE", myObjectType)

// DuckDB types
val map: DuckDbType<Map<String, Int>> = DuckDbTypes.map(DuckDbTypes.varchar, DuckDbTypes.integer)
```

</TabItem>
<TabItem value="scala" label="Scala">

```scala
// PostgreSQL types
val intArray: PgType[Array[Int]] = PgTypes.int4.array()
val dateRange: PgType[Range[LocalDate]] = PgTypes.daterange
val point: PgType[PGpoint] = PgTypes.point

// MariaDB types
val json: MariaType[JsonNode] = MariaTypes.json(mapper)

// Oracle types
val nested: OracleType[List[MyObject]] = OracleTypes.nestedTable("MY_TYPE", myObjectType)

// DuckDB types
val map: DuckDbType[Map[String, Int]] = DuckDbTypes.map(DuckDbTypes.varchar, DuckDbTypes.integer)
```

</TabItem>
</Tabs>

### Row Parsers

Row parsers define how to read a complete row from a ResultSet. They're composable and type-safe:

<Tabs groupId="language">
<TabItem value="java" label="Java">

```java
// Define a row parser for your data class
RowParser<Person> personParser = RowParsers.of(
    PgTypes.int4,           // id
    PgTypes.text,           // name
    PgTypes.timestamptz,    // createdAt
    Person::new,
    person -> new Object[]{person.id(), person.name(), person.createdAt()}
);

// Use it to parse results
List<Person> people = personParser.parseList(resultSet);
```

</TabItem>
<TabItem value="kotlin" label="Kotlin">

```kotlin
// Define a row parser for your data class
val personParser: RowParser<Person> = RowParsers.of(
    PgTypes.int4,           // id
    PgTypes.text,           // name
    PgTypes.timestamptz,    // createdAt
    ::Person,
    { person -> arrayOf(person.id, person.name, person.createdAt) }
)

// Use it to parse results
val people: List<Person> = personParser.parseList(resultSet)
```

</TabItem>
<TabItem value="scala" label="Scala">

```scala
// Define a row parser for your data class
val personParser: RowParser[Person] = RowParsers.of(
  PgTypes.int4,           // id
  PgTypes.text,           // name
  PgTypes.timestamptz,    // createdAt
  Person.apply,
  person => Array(person.id, person.name, person.createdAt)
)

// Use it to parse results
val people: List[Person] = personParser.parseList(resultSet)
```

</TabItem>
</Tabs>

### Result Set Parsers

Result set parsers handle the full lifecycle of reading from a ResultSet:

<Tabs groupId="language">
<TabItem value="java" label="Java">

```java
// Parse a single optional result
ResultSetParser<Optional<Person>> singleParser = personParser.singleOpt();

// Parse all results as a list
ResultSetParser<List<Person>> listParser = personParser.list();

// Execute with automatic resource management
Optional<Person> person = singleParser.parse(resultSet);
```

</TabItem>
<TabItem value="kotlin" label="Kotlin">

```kotlin
// Parse a single optional result
val singleParser: ResultSetParser<Person?> = personParser.singleOpt()

// Parse all results as a list
val listParser: ResultSetParser<List<Person>> = personParser.list()

// Execute with automatic resource management
val person: Person? = singleParser.parse(resultSet)
```

</TabItem>
<TabItem value="scala" label="Scala">

```scala
// Parse a single optional result
val singleParser: ResultSetParser[Option[Person]] = personParser.singleOpt()

// Parse all results as a list
val listParser: ResultSetParser[List[Person]] = personParser.list()

// Execute with automatic resource management
val person: Option[Person] = singleParser.parse(resultSet)
```

</TabItem>
</Tabs>

### SQL String Interpolation

Build SQL fragments safely with type-checked parameters:

<Tabs groupId="language">
<TabItem value="java" label="Java">

```java
Fragment query = Fragment.Builder()
    .sql("SELECT * FROM users WHERE id = ")
    .param(PgTypes.int4, userId)
    .sql(" AND status = ")
    .param(PgTypes.text, "active")
    .sql(" AND created_at > ")
    .param(PgTypes.timestamptz, cutoffDate)
    .done();

// Execute safely - parameters are bound, not interpolated
List<User> users = query.query(userParser).runUnchecked(connection);
```

</TabItem>
<TabItem value="kotlin" label="Kotlin">

```kotlin
val query = buildFragment {
    sql("SELECT * FROM users WHERE id = ")
    param(PgTypes.int4, userId)
    sql(" AND status = ")
    param(PgTypes.text, "active")
    sql(" AND created_at > ")
    param(PgTypes.timestamptz, cutoffDate)
}

// Execute safely - parameters are bound, not interpolated
val users: List<User> = query.query(userParser).runUnchecked(connection)
```

</TabItem>
<TabItem value="scala" label="Scala">

```scala
import dev.typr.foundations.scala.Fragment
import dev.typr.foundations.scala.Fragment.sql

// Use string interpolation with Fragment.encode for type-safe parameters
val query = sql"""SELECT * FROM users
  WHERE id = ${Fragment.encode(PgTypes.int4, userId)}
  AND status = ${Fragment.encode(PgTypes.text, "active")}
  AND created_at > ${Fragment.encode(PgTypes.timestamptz, cutoffDate)}"""

// Execute safely
val users: List[User] = query.query(userParser).runUnchecked(connection)
```

</TabItem>
</Tabs>

### Clear Error Messages

When things go wrong, you get helpful error messages that tell you exactly what happened:

```
Column type mismatch at index 3 (name):
  Expected: varchar (PgTypes.text)
  Actual: integer

Row parsing failed:
  Expected 5 columns, got 4
  Missing column at index 4

Type conversion error:
  Cannot read column 'created_at' as OffsetDateTime
  Database type: timestamp without time zone
  Hint: Use PgTypes.timestamp instead of PgTypes.timestamptz
```

### JSON Codecs

Every database type includes a JSON codec (`DbJson<A>`) that can serialize values to and from a dependency-free JSON representation. This powers advanced features like cross-database MULTISET functionality, where nested result sets are returned as JSON arrays.

The library uses its own `JsonValue` sealed interface - no Jackson, Gson, or other dependencies required:

```java
// JsonValue is a sealed interface with these variants:
// JNull, JBool, JNumber, JString, JArray, JObject

// Every type has a built-in JSON codec
PgType<Integer> intType = PgTypes.int4;
JsonValue json = intType.json().toJson(42);        // JNumber("42")
Integer value = intType.json().fromJson(json);     // 42

// Works for complex types too
PgType<int[]> arrayType = PgTypes.int4ArrayUnboxed;
JsonValue arrayJson = arrayType.json().toJson(new int[]{1, 2, 3});  // JArray([...])

// PostgreSQL JSON/JSONB columns
PgType<Json> jsonType = PgTypes.json;
PgType<Jsonb> jsonbType = PgTypes.jsonb;
```

#### Parsing Nested Results from JSON

A powerful use case is fetching nested data as JSON and parsing it with a RowParser. This enables MULTISET-like queries across all databases.

The RowParser can create a `DbType<List<Row>>` that knows how to parse JSON arrays:

```java
// Define a row parser for the nested data
RowParser<Email> emailParser = RowParsers.of(
    PgTypes.int4,   // id
    PgTypes.text,   // email
    Email::new
);

// Create a DbType for List<Email> from JSON, with column names for JSON object lookup
PgType<List<Email>> emailListType = emailParser.jsonListType(
    PgTypes.json,                // the JSON column type
    List.of("id", "email")       // column names in the JSON objects
);

// Now use it like any other DbType in a RowParser
RowParser<PersonWithEmails> personParser = RowParsers.of(
    PgTypes.int4,      // id
    PgTypes.text,      // name
    emailListType,     // emails as JSON array
    PersonWithEmails::new
);

// Query that returns child rows as JSON array
// PostgreSQL: json_agg(jsonb_build_object('id', e.id, 'email', e.email))
// MariaDB: JSON_ARRAYAGG(JSON_OBJECT('id', e.id, 'email', e.email))
String sql = """
    SELECT p.id, p.name,
           (SELECT COALESCE(json_agg(jsonb_build_object('id', e.id, 'email', e.email)), '[]')
            FROM emails e WHERE e.person_id = p.id) as emails
    FROM persons p WHERE p.id = ?
    """;

// Execute - the JSON parsing is handled automatically by the row parser
List<PersonWithEmails> results = Fragment.of(sql)
    .param(PgTypes.int4, personId)
    .query(personParser)
    .runUnchecked(connection);
```

The JSON codecs enable features like:
- **MULTISET emulation** - Nested collections serialized as JSON across all databases
- **Bulk operations** - Efficient batch processing with JSON intermediates
- **Cross-database compatibility** - Consistent serialization regardless of database

### Streaming Inserts

Efficiently insert large datasets without loading everything into memory:

<Tabs groupId="language">
<TabItem value="java" label="Java">

```java
// Stream millions of records without memory issues
Iterator<Person> people = loadPeopleFromFile();
int inserted = repo.insertStreaming(people, 1000); // batch size
```

</TabItem>
<TabItem value="kotlin" label="Kotlin">

```kotlin
// Stream millions of records without memory issues
val people: Iterator<Person> = loadPeopleFromFile()
val inserted: Int = repo.insertStreaming(people, batchSize = 1000)
```

</TabItem>
<TabItem value="scala" label="Scala">

```scala
// Stream millions of records without memory issues
val people: Iterator[Person] = loadPeopleFromFile()
val inserted: Int = repo.insertStreaming(people.iterator, batchSize = 1000)
```

</TabItem>
</Tabs>

### No Reflection

The entire library is reflection-free. All type information is preserved at compile time, making it fully compatible with:

- **GraalVM native-image** - Build native executables with instant startup
- **ProGuard/R8** - Full minification and optimization support
- **Static analysis tools** - Complete visibility into code paths

### Native Types for Kotlin and Scala

The library includes language-specific modules (`foundations-jdbc-dsl-kotlin` and `foundations-jdbc-dsl-scala`) that provide idiomatic wrappers:

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin">

```kotlin
// Nullable columns use Kotlin's nullable types
val personParser: RowParser<Person> = RowParsers.of(
    PgTypes.int4,
    PgTypes.text.nullable(),  // String? in Kotlin
    ::Person
)

// Extension functions for fluent API
val users: List<User> = fragment.query(userParser).runUnchecked(connection)
```

</TabItem>
<TabItem value="scala" label="Scala">

```scala
// Nullable columns use Option types
val personParser: RowParser[Person] = RowParsers.of(
  PgTypes.int4,
  PgTypes.text.nullable,  // Option[String] in Scala
  Person.apply
)

// Scala collections
val users: List[User] = fragment.query(userParser).runUnchecked(connection).toList
```

</TabItem>
</Tabs>

## Supported Database Types

Each database has comprehensive type support. See the detailed pages for complete type mappings:

- **[PostgreSQL](./postgresql)** - Arrays, ranges, geometric types, network types, JSON, text search, and more
- **[MariaDB/MySQL](./mariadb)** - Unsigned integers, spatial types, sets, JSON, and year types
- **[DuckDB](./duckdb)** - Lists, structs, maps, unions, enums, and 128-bit integers
- **[Oracle](./oracle)** - OBJECT types, nested tables, intervals, and LOB types
- **[SQL Server](./sqlserver)** - Geography, geometry, hierarchyid, and Unicode types

## Getting Started

Add the dependency to your project:

<Tabs groupId="build">
<TabItem value="sbt" label="sbt">

```scala
libraryDependencies += "com.olvind.typo" %% "foundations-jdbc" % "version"
```

</TabItem>
<TabItem value="gradle" label="Gradle">

```kotlin
implementation("com.olvind.typo:foundations-jdbc:version")
```

</TabItem>
<TabItem value="maven" label="Maven">

```xml
<dependency>
    <groupId>com.olvind.typo</groupId>
    <artifactId>foundations-jdbc</artifactId>
    <version>version</version>
</dependency>
```

</TabItem>
</Tabs>

This library provides the solid foundation that Typo's generated code builds upon, but it can also be used independently for projects that need reliable JDBC access without code generation.

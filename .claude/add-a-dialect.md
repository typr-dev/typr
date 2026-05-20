# Adding a new database dialect

A walkthrough of every file you need to touch, in roughly the order you should touch them. The
SQLite support PR is the cleanest recent reference — every step below points at a SQLite analogue.

## 0. What the upstream `foundations-jdbc` library must already provide

`typr` is the code generator; the runtime types and codecs live in the external
`dev.typr.foundations:foundations-jdbc` library. Before you start, make sure that library exposes:

- A typed types catalogue, e.g. `dev.typr.foundations.XxxTypes` with one entry per declarable type
  (integer/varchar/decimal/date/…). Each entry is a `XxxType<JvmType>` carrying the read/write
  codec and JSON codec.
- `dev.typr.foundations.connect.XxxConfig` for building a JDBC connection, and
  `DatabaseKind.XXX` enum value.
- Scala and Kotlin wrappers: `dev.typr.foundationssc.XxxTypes` and
  `dev.typr.foundationskt.XxxTypes`.

If any of these are missing, send the PR to foundations first.

## 1. Add a `Dialect` entry

File: `typr-dsl/src/java/dev/typr/dsl/Dialect.java` — the DSL's per-dialect SQL grammar bits
(identifier quoting, casts, null-safe comparison, LIMIT/OFFSET, tuple-IN support).

The `Dialect` interface has reasonable defaults for everything except `bigint()`, `quoteIdent()`,
`escapeIdent()`, `typeCast()`, `columnRef()`, `nullSafeEquals()`, `nullSafeNotEquals()` — override
just what differs from the default (PostgreSQL-shaped) behaviour.

Re-export it in the Scala wrapper at
`typr-dsl-scala/src/scala/dev/typr/dslsc/package.scala`'s `object Dialect`.

(Kotlin can use the Java static field directly — no wrapper needed.)

## 2. Define the `db.XxxType` ADT

File: `typr-codegen/src/scala/typr/db.scala`.

Add a `sealed trait XxxType extends Type` plus one `case object` (or `case class` for parameterised
types like `Decimal(precision, scale)`) per concrete declarable type. Add `XxxType` to the
`Unknown` mixin trait list at the bottom — that's the fallback for unrecognised JDBC type names.

## 3. Add `DbType.Xxx` + connection plumbing

Files:

- `typr-codegen/src/scala/typr/DbType.scala` — add `case object Xxx extends DbType` returning your
  adapter, plus a branch in `detect(...)` and `detectFromDriver(...)`.
- `typr-codegen/src/scala/typr/TypoDataSource.scala` — add a `hikariXxx*(...)` constructor and a
  `DatabaseKind.XXX` branch in `hikari(...)`.

## 4. Write the codegen adapter

File: `typr-codegen/src/scala/typr/internal/codegen/XxxAdapter.scala`.

Mirror `DuckDbAdapter` (closest in spirit for most embedded DBs) or `Db2Adapter` (single-schema,
no native arrays). Five layers:

1. **SQL syntax** — `quoteIdent`, `typeCast`, the various `columnReadCast`/`columnWriteCast` (most
   dialects can return `Code.Empty`).
2. **Runtime types** — point at `XxxTypes` / `XxxType` / `XxxText` and pick a `typeFieldName`
   (e.g. `xxxType`).
3. **Capabilities** — `supportsArrays`, `supportsReturning`, `supportsCopyStreaming`, the upsert
   strategy.
4. **SQL templates** — upsert (`ON CONFLICT` vs `MERGE`), conflict update clause, returning clause.
5. **Schema DDL** — `dropSchemaDdl` / `createSchemaDdl`. For dialects without schemas (SQLite,
   embedded DBs), return a SQL comment.

## 5. Write the metadata-extraction package

Directory: `typr-codegen/src/scala/typr/internal/xxx/`. Four files:

- `XxxJdbcMetadata.scala` — wraps `ResultSetMetaData` into `MetadataColumn`. This is essentially
  identical across dialects — copy `DuckDbJdbcMetadata` and rename.
- `XxxTypeMapperDb.scala` — maps the declared/JDBC type name string to a `db.XxxType`. Read your
  database's reference for type aliases; many dialects accept synonyms (BIGINT/INT8/LONG/…).
- `XxxSqlFileMetadata.scala` — analyses user-supplied `.sql` files using sqlglot. Copy
  `DuckDbSqlFileMetadata`; the only dialect-specific bits are (a) how you read the schema (the
  catalogue queries) and (b) the sqlglot `dialect` string.
- `XxxMetaDb.scala` — reads tables/columns/PKs/FKs/uniques/views from the database catalogue.
  Look at how your DB exposes metadata (information_schema, system catalog views, or PRAGMAs) and
  shape the queries to fit. Return a `MetaDb(dbType, relations, enums = Nil, domains = Nil, …)`.

## 6. Wire all dispatch sites

These are the files that have one `case DbType.X => …` arm per dialect. Each new dialect needs an
arm added everywhere:

- `typr-codegen/src/scala/typr/MetaDb.scala` — both the `typeMapperDb` match and the `fromDb`
  match.
- `typr-codegen/src/scala/typr/internal/InstanceRequirements.scala` — the heuristic name guess
  and the `dbTypeFieldNameFor` map.
- `typr-codegen/src/scala/typr/internal/generate.scala` — the `databaseName` string used as a
  TypeDefinition discriminator, **plus** the precision-types case list near the bottom that emits
  `PreciseConstraint.*` for `VarChar(Some(n))` etc.
- `typr-codegen/src/scala/typr/internal/codegen/DbLibFoundations.scala` — selectByIds /
  deleteByIds bodies. If your DB has no native arrays (most non-Postgres do), fold it into the
  existing `case DbType.SqlServer | DbType.DB2 | …` arms rather than copy-pasting.
- `typr-codegen/src/scala/typr/internal/sqlfiles/SqlFileReader.scala` — dispatch to your
  `XxxSqlFileMetadata`.
- `typr-codegen/src/scala/typr/internal/TypeMapperJvmNew.scala` — both the `baseType` and the
  precise-types match. (`TypeMapperJvmOld` is PostgreSQL-only legacy; skip.)
- `typr-codegen/src/scala/typr/internal/TypeMatcher.scala` — `typeName` (for the matcher's
  per-column name string).
- `typr-codegen/src/scala/typr/internal/TypeCompatibilityChecker.scala` — add a `CompatibilityClass`
  arm for every Java-equivalent class (String/Boolean/Int/Long/…) so cross-dialect Bridge type
  matching works.
- `typr-codegen/src/scala/typr/internal/ComputedTestInserts.scala` — the `case`s for max-length
  detection on text columns.
- `typr/src/scala/typr/bridge/TypeSuggester.scala` — group types into the "text/integer/numeric/
  boolean/temporal/uuid/json" buckets the bridge UI uses.

## 7. CLI wiring

- `typr-config.schema.json` — add `"xxx"` to the boundary `type` enum and add a `xxxBoundary`
  definition (use `duckdbBoundary` as a template for embedded DBs, or `databaseBoundary` for
  server-based ones).
- Run `bleep run generate-config-types` to regenerate the typed config classes
  (`XxxBoundary.scala` shows up under `typr-codegen/generated-and-checked-in-jsonschema/`).
- `typr/src/scala/typr/cli/config/ConfigParser.scala` — add `Some("xxx") => …` arms for source
  *and* boundary parsing, and add `ParsedSource.Xxx` / `ParsedBoundary.Xxx` cases.
- `typr/src/scala/typr/cli/config/ConfigToOptions.scala` — `convertXxxBoundary` + `convertXxxSource`.
- `typr/src/scala/typr/cli/commands/Generate.scala` — `fetchXxxBoundary`, `fetchXxxSource`,
  `generateXxxForOutput`, plus the two dispatch sites in `runTwoPhaseGeneration`. Watch for any
  driver-specific quirks loading the schema (SQLite's xerial driver, for example, can't run
  multi-statement strings in a single `execute()` — the SQLite path splits on `;` first).
- `typr/src/scala/typr/cli/app/MetaDbFetch.scala` — add a `ParsedSource.Xxx` arm + `fetchXxx`.
- `typr/src/scala/typr/cli/app/ConnectionTest.scala` — add a `ParsedSource.Xxx` arm + `tryXxx`,
  and include `Xxx` in `isTestable`'s pattern.
- `typr/src/scala/typr/cli/app/LoadedSource.scala` — include `ParsedSource.Xxx` in the database
  pattern arm.
- The TUI screens (`SchemaPicker`, `SourceForm`, `SourceList`, `MainMenu`) — fold the new kind
  string into the existing `"duckdb" | "xxx"` cases for path-style sources, or use the database
  patterns for host/port sources.

## 8. Build wiring

- `bleep.yaml` — add three tester project entries (`testers/xxx/java`, `testers/xxx/kotlin`,
  `testers/xxx/scala`), each with the right JDBC driver dependency. Also add the driver to the
  `typr-codegen` project's dependencies so the CLI can connect.
- `typr.yaml` — add a boundary entry (with `schema_sql`, `sql_scripts`, `path` or host/port) and
  three outputs (`xxx-java`, `xxx-kotlin`, `xxx-scala`).

## 9. Test data + scripts

- `sql-init/xxx/00-schema.sql` — exercise every column type your `db.XxxType` ADT models, plus
  composite PK, composite FK, UNIQUE, views, and `precision_types[_null]` for precise-type
  generation.
- `sql-scripts/xxx/*.sql` — half a dozen parameterised queries covering SELECT, INSERT-with-
  RETURNING, UPDATE, DELETE, JOIN.

## 10. Testers

Under `testers/xxx/{java,kotlin,scala}` add a `src/{lang}/testdb/XxxTestHelper` and a
`BasicCrudTest` that exercises the generated repos. Mirror `testers/duckdb/{java,kotlin,scala}` —
if your driver supports multi-statement `execute()`, you can use `connectionInitSql(schema)`
directly; otherwise split the schema yourself (SQLite shows this pattern).

## 11. Generate, fmt, test

```bash
bleep run typr -- generate --source xxx --accept
bleep fmt
bleep test testers/xxx
```

## Things that bit me on SQLite specifically

- **Single connection, in-memory.** `:memory:` databases live inside one JDBC connection; opening
  a second connection gives you an empty DB. Foundations' `singleConnectionMode()` handles the
  reuse but `connectionInitSql` runs once via a *single* `stmt.execute(...)` — for drivers that
  don't accept multi-statement strings (xerial SQLite is one) the schema only partially loads.
  The fix is to bypass `connectionInitSql` and run statements one at a time during a one-shot
  init, then switch the transactor to `rollbackOnly()` for tests.
- **No schemas.** Force `SchemaMode.SingleSchema("main")` in your `convertXxxBoundary`, and emit
  `RelationName(None, name)` in metadata. SQLite's `main` namespace isn't really a schema.
- **Type affinity, not declared type.** SQLite stores values in five storage classes regardless
  of column declaration — your `XxxTypeMapperDb` needs to match common synonyms (BIGINT, INT8,
  INT2, VARCHAR(n), CLOB, …) to a specific `db.XxxType` so the codecs round-trip. The full
  affinity-substring fallback at the bottom of `SqliteTypeMapperDb` handles "anything goes" cases
  (`VARYING CHARACTER`, `DOUBLE PRECISION`, etc.).
- **Foreign keys off by default.** SQLite needs `PRAGMA foreign_keys = ON` per connection. The
  foundations `SqliteConfig.Builder.foreignKeys(true)` sets it via a driver property, but if
  you're opening raw JDBC for tests, set it yourself.

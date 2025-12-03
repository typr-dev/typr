# MariaDB Support Implementation Plan

## Overview

Add MariaDB support to Typo, focusing initially on the Java runtime (`typo-runtime-java`).
Scala database libraries (Doobie, ZIO-JDBC, Anorm) will not get MariaDB support initially.

## Phase 1: Core Type System Refactoring

### 1.1 Refactor `db.Type` hierarchy

Current structure:
```scala
sealed trait Type
object Type {
  case object Boolean extends Type
  case object Int4 extends Type
  // ... all PostgreSQL types
}
```

New structure:
```scala
sealed trait Type

// PostgreSQL-specific types
sealed trait PgType extends Type
object PgType {
  case object Boolean extends PgType
  case object Int2 extends PgType
  case object Int4 extends PgType
  case object Int8 extends PgType
  case object Float4 extends PgType
  case object Float8 extends PgType
  case object Numeric extends PgType
  case object Text extends PgType
  case class VarChar(maxLength: Option[Int]) extends PgType
  case class Bpchar(maxLength: Option[Int]) extends PgType
  case object Char extends PgType
  case object Name extends PgType
  case object Bytea extends PgType
  case object Date extends PgType
  case object Time extends PgType
  case object TimeTz extends PgType
  case object Timestamp extends PgType
  case object TimestampTz extends PgType
  case object UUID extends PgType
  case object Json extends PgType
  case object Jsonb extends PgType
  case object Xml extends PgType
  case object Hstore extends PgType
  case object Inet extends PgType
  case class Array(tpe: Type) extends PgType
  case class DomainRef(name: RelationName, underlying: String, underlyingType: Type) extends PgType
  case class EnumRef(enm: StringEnum) extends PgType
  // PostgreSQL-specific geometric types
  case object PGbox extends PgType
  case object PGcircle extends PgType
  case object PGline extends PgType
  case object PGlseg extends PgType
  case object PGpath extends PgType
  case object PGpoint extends PgType
  case object PGpolygon extends PgType
  case object PGInterval extends PgType
  case object PGmoney extends PgType
  case object PGlsn extends PgType
  // PostgreSQL system types
  case object Oid extends PgType
  case object regclass extends PgType
  case object regtype extends PgType
  // ... other pg system types
  case object Vector extends PgType
}

// MariaDB-specific types
sealed trait MariaType extends Type
object MariaType {
  // Integer types
  case object TinyInt extends MariaType
  case object SmallInt extends MariaType
  case object MediumInt extends MariaType
  case object Int extends MariaType
  case object BigInt extends MariaType
  // Unsigned variants
  case object TinyIntUnsigned extends MariaType
  case object SmallIntUnsigned extends MariaType
  case object MediumIntUnsigned extends MariaType
  case object IntUnsigned extends MariaType
  case object BigIntUnsigned extends MariaType
  // Fixed-point
  case class Decimal(precision: Option[Int], scale: Option[Int]) extends MariaType
  // Floating-point
  case object Float extends MariaType
  case object Double extends MariaType
  // Boolean
  case object Boolean extends MariaType
  // Bit
  case class Bit(length: Option[Int]) extends MariaType
  // String types
  case class Char(length: Option[Int]) extends MariaType
  case class VarChar(length: Option[Int]) extends MariaType
  case object TinyText extends MariaType
  case object Text extends MariaType
  case object MediumText extends MariaType
  case object LongText extends MariaType
  // Binary types
  case class Binary(length: Option[Int]) extends MariaType
  case class VarBinary(length: Option[Int]) extends MariaType
  case object TinyBlob extends MariaType
  case object Blob extends MariaType
  case object MediumBlob extends MariaType
  case object LongBlob extends MariaType
  // Date/Time
  case object Date extends MariaType
  case class Time(fsp: Option[Int]) extends MariaType
  case class DateTime(fsp: Option[Int]) extends MariaType
  case class Timestamp(fsp: Option[Int]) extends MariaType
  case object Year extends MariaType
  // Special types
  case class Enum(values: List[String]) extends MariaType  // Inline enum, not a reference
  case class Set(values: List[String]) extends MariaType
  case object Inet4 extends MariaType
  case object Inet6 extends MariaType
  // Spatial types (use MariaDB Connector/J bundled types)
  case object Geometry extends MariaType
  case object Point extends MariaType
  case object LineString extends MariaType
  case object Polygon extends MariaType
  case object MultiPoint extends MariaType
  case object MultiLineString extends MariaType
  case object MultiPolygon extends MariaType
  case object GeometryCollection extends MariaType
  // JSON (alias for LONGTEXT in MariaDB)
  case object Json extends MariaType
}

// Shared/unknown type
case class Unknown(sqlType: String) extends Type
```

### 1.2 Add `Generated.AutoIncrement` for MariaDB

MariaDB uses `AUTO_INCREMENT` instead of PostgreSQL's `IDENTITY` or `SERIAL`:

```scala
object Generated {
  // Existing PostgreSQL cases
  case class IsGenerated(...) extends Generated
  case class Identity(...) extends Generated

  // New MariaDB case
  case object AutoIncrement extends Generated {
    override def ALWAYS: Boolean = true
    override def `BY DEFAULT`: Boolean = false
    override def asString: String = "AUTO_INCREMENT"
  }
}
```

## Phase 2: MetaDb Reorganization

### 2.1 Move PostgreSQL-specific code to `pg` package

Current structure:
```
typo/src/scala/typo/
├── MetaDb.scala           # Case class + companion with PostgreSQL logic
├── internal/
│   ├── metadb/
│   │   ├── Enums.scala
│   │   ├── ForeignKeys.scala
│   │   ├── PrimaryKeys.scala
│   │   ├── UniqueKeys.scala
│   │   └── OpenEnum.scala
│   └── TypeMapperDb.scala
```

New structure:
```
typo/src/scala/typo/
├── MetaDb.scala           # Just the case class (shared)
├── internal/
│   ├── pg/
│   │   ├── PgMetaDb.scala        # PostgreSQL MetaDb.Input, fromDb, fromInput
│   │   ├── PgTypeMapperDb.scala  # PostgreSQL type mapping
│   │   ├── Enums.scala
│   │   ├── ForeignKeys.scala
│   │   ├── PrimaryKeys.scala
│   │   ├── UniqueKeys.scala
│   │   └── OpenEnum.scala
│   ├── mariadb/
│   │   ├── MariaMetaDb.scala     # MariaDB MetaDb.Input, fromDb, fromInput
│   │   ├── MariaTypeMapperDb.scala
│   │   ├── ForeignKeys.scala     # Shared or MariaDB-specific
│   │   └── Enums.scala           # Parse inline ENUM from COLUMN_TYPE
│   └── ... (shared code)
```

### 2.2 Keep shared structures

These remain in the main `db` object:
- `Domain`, `StringEnum`, `ColName`, `Constraint`
- `Generated` (with new `AutoIncrement` case)
- `Col`, `RelationName`, `PrimaryKey`, `ForeignKey`, `UniqueKey`
- `Relation`, `Table`, `View`

## Phase 3: Database Detection

### 3.1 Add `DbType` enum

```scala
sealed trait DbType
object DbType {
  case object PostgreSQL extends DbType
  case object MariaDB extends DbType
  case object MySQL extends DbType  // For future
}
```

### 3.2 Detect database from JDBC connection

```scala
object DbType {
  def detect(connection: Connection): DbType = {
    val metadata = connection.getMetaData
    val productName = metadata.getDatabaseProductName.toLowerCase
    productName match {
      case name if name.contains("postgresql") => PostgreSQL
      case name if name.contains("mariadb") => MariaDB
      case name if name.contains("mysql") => MySQL  // Could be MariaDB too
      case other => sys.error(s"Unsupported database: $other")
    }
  }

  // Alternative: detect from driver class name
  def detectFromDriver(connection: Connection): DbType = {
    val driverName = connection.getMetaData.getDriverName.toLowerCase
    driverName match {
      case name if name.contains("postgresql") => PostgreSQL
      case name if name.contains("mariadb") => MariaDB
      case name if name.contains("mysql") => MySQL
      case other => sys.error(s"Unknown driver: $other")
    }
  }
}
```

### 3.3 Update TypoDataSource

```scala
case class TypoDataSource(ds: DataSource, dbType: DbType) {
  // ...
}

object TypoDataSource {
  def hikariPostgres(...): TypoDataSource = {
    // Existing code
    TypoDataSource(ds, DbType.PostgreSQL)
  }

  def hikariMariaDb(server: String, port: Int, databaseName: String, username: String, password: String): TypoDataSource = {
    val config = new HikariConfig
    config.setJdbcUrl(s"jdbc:mariadb://$server:$port/$databaseName")
    config.setUsername(username)
    config.setPassword(password)
    val ds = new HikariDataSource(config)
    TypoDataSource(ds, DbType.MariaDB)
  }

  // Auto-detect version
  def hikari(ds: DataSource): TypoDataSource = {
    val conn = ds.getConnection
    try {
      val dbType = DbType.detect(conn)
      TypoDataSource(ds, dbType)
    } finally conn.close()
  }
}
```

## Phase 4: Type Mapping Updates

### 4.1 TypeMapperJvm changes

The `TypeMapperJvmNew` needs to handle both `PgType` and `MariaType`:

```scala
override def baseType(tpe: db.Type): jvm.Type = tpe match {
  // PostgreSQL types
  case db.PgType.Boolean => lang.Boolean
  case db.PgType.Int4 => lang.Int
  // ...

  // MariaDB types
  case db.MariaType.TinyInt => lang.Byte
  case db.MariaType.SmallInt => lang.Short
  case db.MariaType.Int => lang.Int
  case db.MariaType.BigInt => lang.Long
  case db.MariaType.IntUnsigned => lang.Long  // Needs larger type
  case db.MariaType.BigIntUnsigned => TypesJava.BigInteger
  // ...

  // MariaDB spatial types -> MariaDB Connector/J types
  case db.MariaType.Point => TypesMariaDb.Point
  case db.MariaType.LineString => TypesMariaDb.LineString
  // ...

  case db.Unknown(_) => TypesJava.runtime.Unknown
}
```

### 4.2 Add TypesMariaDb

```scala
object TypesMariaDb {
  private def q(name: String) = jvm.Type.Qualified(jvm.QIdent(List("org", "mariadb", "jdbc", "type", name)))

  val Geometry = q("Geometry")
  val Point = q("Point")
  val LineString = q("LineString")
  val Polygon = q("Polygon")
  val MultiPoint = q("MultiPoint")
  val MultiLineString = q("MultiLineString")
  val MultiPolygon = q("MultiPolygon")
  val GeometryCollection = q("GeometryCollection")
}
```

## Phase 5: SQL Files and Code Generation (Future)

### 5.1 SQL syntax differences

MariaDB SQL differs from PostgreSQL in several ways:
- String escaping: `"` vs backticks
- RETURNING clause: Different syntax
- ON CONFLICT: Different syntax (INSERT ... ON DUPLICATE KEY UPDATE)
- LIMIT/OFFSET: Similar but some differences

This will require updates to:
- `SqlCast.scala`
- Generated repository implementations
- DSL (if ever supported)

### 5.2 Runtime library updates

`typo-runtime-java` will need MariaDB-specific:
- Type codecs for MariaDB types
- SQL generation helpers

## Implementation Order

1. **Phase 1.1**: Refactor `db.Type` → `PgType` + `MariaType` hierarchy
2. **Phase 1.2**: Add `Generated.AutoIncrement`
3. **Phase 2.1**: Move MetaDb companion to `pg` package
4. **Phase 3**: Add database detection
5. **Phase 4**: Update type mappers
6. **Phase 5**: (Future) SQL generation and runtime

## Files to Modify

### Phase 1
- `typo/src/scala/typo/db.scala` - Type hierarchy refactoring
- `typo/src/scala/typo/internal/TypeMapperDb.scala` - Update to use PgType
- `typo/src/scala/typo/internal/TypeMapperJvmNew.scala` - Handle both type hierarchies
- `typo/src/scala/typo/internal/TypeMapperJvmOld.scala` - Handle both type hierarchies

### Phase 2
- `typo/src/scala/typo/MetaDb.scala` - Keep case class, move companion
- Create `typo/src/scala/typo/internal/pg/` directory
- Move metadb files to pg package

### Phase 3
- `typo/src/scala/typo/DbType.scala` - New file
- `typo/src/scala/typo/TypoDataSource.scala` - Add dbType detection
- `typo/src/scala/typo/Options.scala` - May need updates

### Phase 4
- `typo/src/scala/typo/internal/TypesJava.scala` - May need MariaDB types
- Create `typo/src/scala/typo/internal/TypesMariaDb.scala`

## Testing Strategy

1. Existing PostgreSQL tests should continue to pass
2. Add MariaDB docker-compose service for testing
3. Add MariaDB-specific test database (similar to Adventureworks)
4. Integration tests for MariaDB metadata extraction

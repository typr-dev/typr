package typo
package internal
package codegen

import typo.jvm.Code

/** Database adapter for code generation - inspired by dbt's adapter pattern.
  *
  * Organized in layers:
  *   - Layer 1: SQL Syntax (quoting, casting)
  *   - Layer 2: Runtime Types (type class references)
  *   - Layer 3: Capabilities (feature flags)
  *   - Layer 4: SQL Templates (complete SQL statements)
  *   - Layer 5: Method Bodies (complex multi-statement implementations)
  */
trait DbAdapter {
  def dbType: DbType

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 1: SQL Syntax
  // ═══════════════════════════════════════════════════════════════════════════

  /** Quote identifier: PostgreSQL uses ", MariaDB uses ` */
  def quoteIdent(name: String): String

  /** Type cast in SQL: PostgreSQL uses ::type, MariaDB uses CAST() */
  def typeCast(value: Code, typeName: String): Code

  /** Type cast for column in SELECT (read) - returns cast suffix or empty */
  def columnReadCast(col: ComputedColumn): Code

  /** Type cast for column in INSERT/UPDATE (write) - returns cast suffix or empty */
  def columnWriteCast(col: ComputedColumn): Code

  /** Get the type name for write cast (used for array unnest casts) */
  def writeCastTypeName(col: ComputedColumn): Option[String]

  /** Write cast for a database type (for SQL file parameters) */
  def writeCast(dbType: db.Type, udtName: Option[String]): Option[SqlCastValue]

  /** Read cast for a database type (for SQL file columns) */
  def readCast(dbType: db.Type): Option[SqlCastValue]

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 2: Runtime Type System
  // ═══════════════════════════════════════════════════════════════════════════

  /** Runtime types object (PgTypes or MariaTypes) */
  def Types: jvm.Type.Qualified

  /** Runtime type class (PgType or MariaType) */
  def TypeClass: jvm.Type.Qualified

  /** Runtime text class (PgText or MariaText) */
  def TextClass: jvm.Type.Qualified

  /** Field name for type instance on generated types */
  def typeFieldName: jvm.Ident

  /** Field name for text instance on generated types */
  def textFieldName: jvm.Ident

  /** Dialect reference for DSL (Dialect.POSTGRESQL or Dialect.MARIADB) */
  def dialectRef(lang: Lang): Code

  /** Lookup runtime type instance for a TypoType */
  def lookupType(typoType: TypoType, naming: Naming, typeSupport: TypeSupport): Code

  /** Lookup runtime type instance for a WellKnownPrimitive */
  def lookupPrimitive(primitive: analysis.WellKnownPrimitive, typeSupport: TypeSupport): Code

  /** Lookup runtime type instance by database type */
  def lookupTypeByDbType(dbType: db.Type, Types: jvm.Type.Qualified, naming: Naming, typeSupport: TypeSupport): Code

  /** Get the database-specific text/string type (for string enum underlying types). NOTE: This is only used for PostgreSQL COPY streaming support. Oracle doesn't use this. Implementations should not
    * rely on this method being meaningful for all databases.
    */
  def textType: db.Type

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 3: Capabilities
  // ═══════════════════════════════════════════════════════════════════════════

  /** Whether database supports array types */
  def supportsArrays: Boolean

  /** Whether database supports RETURNING clause */
  def supportsReturning: Boolean

  /** Whether database supports COPY streaming */
  def supportsCopyStreaming: Boolean

  /** Whether COPY streaming supports DEFAULT keyword */
  def supportsDefaultInCopy: Boolean

  /** Determine the strategy for returning data after INSERT operations.
    *   - PostgreSQL/MariaDB: Use SQL RETURNING clause
    *   - Oracle (no STRUCT/ARRAY): Use getGeneratedKeys with all columns
    *   - Oracle (with STRUCT/ARRAY): Use getGeneratedKeys with ID columns only
    * Note: maybeId is None for tables without a primary key
    */
  def returningStrategy(cols: NonEmptyList[ComputedColumn], rowType: jvm.Type, maybeId: Option[IdComputed]): ReturningStrategy

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 4: SQL Templates
  // ═══════════════════════════════════════════════════════════════════════════

  /** Generate UPSERT SQL (ON CONFLICT vs ON DUPLICATE KEY) */
  def upsertSql(
      tableName: Code,
      columns: Code,
      idColumns: Code,
      values: Code,
      conflictUpdate: Code,
      returning: Option[Code]
  ): Code

  /** Generate conflict update clause for non-PK columns */
  def conflictUpdateClause(cols: List[ComputedColumn], quotedColName: ComputedColumn => Code): Code

  /** Generate no-op conflict update for tables where all columns are PK */
  def conflictNoOpClause(firstPkCol: ComputedColumn, quotedColName: ComputedColumn => Code): Code

  /** Generate streaming insert command (COPY or batch) */
  def streamingInsertSql(tableName: Code, columns: Code): Code

  /** Generate temp table creation */
  def createTempTableLike(tempName: String, sourceTable: Code): Code

  /** Format columns for use in RETURNING/OUTPUT clause. Most databases use columns as-is, SQL Server needs INSERTED. prefix */
  def returningColumns(cols: NonEmptyList[ComputedColumn]): Code =
    cols.map(c => quotedColName(c) ++ columnReadCast(c)).mkCode(", ")

  /** Quote a column name */
  protected def quotedColName(c: ComputedColumn): Code =
    jvm.Code.Str(quoteIdent(c.dbName.value))

  /** Generate RETURNING clause for INSERT statements. PostgreSQL/DuckDB use RETURNING, SQL Server uses OUTPUT INSERTED.* */
  def returningClause(columns: Code): Code

  /** Whether the RETURNING/OUTPUT clause goes before VALUES (SQL Server) or after (PostgreSQL/MariaDB/DuckDB) */
  def returningBeforeValues: Boolean = false

  /** Generate INSERT SQL with RETURNING/OUTPUT clause in the correct position */
  def insertReturning(
      tableName: Code,
      columns: Code,
      values: Code,
      returningCols: NonEmptyList[ComputedColumn]
  ): Code = {
    val returning = returningClause(returningColumns(returningCols))
    if (returningBeforeValues) {
      // SQL Server: INSERT ... OUTPUT ... VALUES ...
      code"""|insert into $tableName($columns)
             |$returning
             |values ($values)
             |""".stripMargin
    } else {
      // PostgreSQL/MariaDB/DuckDB: INSERT ... VALUES ... RETURNING ...
      code"""|insert into $tableName($columns)
             |values ($values)
             |$returning
             |""".stripMargin
    }
  }

  /** Generate INSERT DEFAULT VALUES with RETURNING/OUTPUT clause */
  def insertDefaultValuesReturning(
      tableName: Code,
      returningCols: NonEmptyList[ComputedColumn]
  ): Code = {
    val returning = returningClause(returningColumns(returningCols))
    if (returningBeforeValues) {
      // SQL Server: INSERT ... OUTPUT ... DEFAULT VALUES
      code"""|insert into $tableName
             |$returning
             |default values
             |""".stripMargin
    } else {
      // PostgreSQL/MariaDB/DuckDB: INSERT ... DEFAULT VALUES RETURNING ...
      code"""|insert into $tableName default values
             |$returning
             |""".stripMargin
    }
  }

}

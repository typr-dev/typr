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
  def dialectRef: Code

  /** Lookup runtime type instance for a JVM type */
  def lookupType(tpe: jvm.Type, pkg: jvm.QIdent, lang: Lang): Code

  /** Lookup runtime type instance for a database type */
  def lookupTypeByDbType(dbType: db.Type, Types: jvm.Type.Qualified, naming: Naming): Code

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

}

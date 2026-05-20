package typr
package internal
package sqlite

import scala.util.Try

/** Map a SQLite declared type name to a db.SqliteType.
  *
  * SQLite's "type affinity" rules let columns be declared with almost any text and assign a storage class by substring matching ([SQLite docs §3.1]). We pick a single canonical SqliteType per
  * declared name, treating the foundations SqliteTypes catalogue as the source of truth for what each name means in practice.
  *
  * Unknown declared names fall back to `Text` (TEXT affinity) — the same behaviour SQLite uses for an empty or unrecognised type declaration.
  */
case class SqliteTypeMapperDb() extends TypeMapperDb {

  private def toIntOption(s: String): Option[Int] = Try(s.toInt).toOption

  override def dbTypeFrom(typeName: String, characterMaximumLength: Option[Int])(logWarning: () => Unit): db.Type =
    parseType(typeName, characterMaximumLength)(logWarning)

  /** Parse a declared SQLite type string into db.SqliteType. */
  def parseType(typeStr: String, characterMaximumLength: Option[Int])(logWarning: () => Unit): db.Type = {
    val raw = typeStr.trim
    if (raw.isEmpty) return db.SqliteType.Text

    val upper = raw.toUpperCase
    val base = upper.takeWhile(c => c != '(' && !c.isWhitespace)

    // Parameterised types
    if (upper.startsWith("DECIMAL(") || upper.startsWith("NUMERIC(") || upper.startsWith("NUMBER(")) {
      val inner = upper.dropWhile(_ != '(').drop(1).takeWhile(_ != ')')
      val parts = inner.split(",").map(_.trim)
      val precision = parts.headOption.flatMap(toIntOption)
      val scale = parts.lift(1).flatMap(toIntOption)
      return db.SqliteType.Decimal(precision, scale)
    }
    if (upper.startsWith("VARCHAR(") || upper.startsWith("VARYING CHARACTER(") || upper.startsWith("NVARCHAR(")) {
      val inner = upper.dropWhile(_ != '(').drop(1).takeWhile(_ != ')')
      return db.SqliteType.VarChar(toIntOption(inner.trim))
    }
    if (upper.startsWith("CHAR(") || upper.startsWith("CHARACTER(") || upper.startsWith("NCHAR(") || upper.startsWith("NATIVE CHARACTER(")) {
      val inner = upper.dropWhile(_ != '(').drop(1).takeWhile(_ != ')')
      return db.SqliteType.Char(toIntOption(inner.trim))
    }

    // Affinity by substring (SQLite §3.1 — order matters):
    //   1. "INT"     -> INTEGER affinity
    //   2. "CHAR" / "CLOB" / "TEXT"  -> TEXT
    //   3. "BLOB" / "" (no decl)     -> BLOB
    //   4. "REAL" / "FLOA" / "DOUB"  -> REAL
    //   5. otherwise                 -> NUMERIC
    //
    // We refine that by keeping a more specific Java type where the foundations SqliteTypes
    // catalogue exposes one (smallint, tinyint, etc.).
    base match {
      // INTEGER affinity — specific
      case "INTEGER"          => db.SqliteType.Integer
      case "INT"              => db.SqliteType.Int
      case "INT2" | "INT4"    => db.SqliteType.Int
      case "INT8" | "BIGINT"  => db.SqliteType.BigInt
      case "SMALLINT"         => db.SqliteType.SmallInt
      case "TINYINT"          => db.SqliteType.TinyInt
      case "MEDIUMINT"        => db.SqliteType.Int
      case "UNSIGNED"         => db.SqliteType.BigInt // "UNSIGNED BIG INT"
      case "BOOLEAN" | "BOOL" => db.SqliteType.Boolean

      // REAL affinity
      case "REAL"             => db.SqliteType.Real
      case "DOUBLE"           => db.SqliteType.Double
      case "FLOAT" | "FLOAT4" => db.SqliteType.Float
      case "FLOAT8"           => db.SqliteType.Double

      // NUMERIC affinity
      case "NUMERIC" | "DECIMAL" | "NUMBER" => db.SqliteType.Decimal(None, None)

      // TEXT affinity — specific
      case "TEXT"                         => db.SqliteType.Text
      case "VARCHAR" | "NVARCHAR"         => db.SqliteType.VarChar(characterMaximumLength)
      case "CHAR" | "CHARACTER" | "NCHAR" => db.SqliteType.Char(characterMaximumLength)
      case "CLOB"                         => db.SqliteType.Clob

      // BLOB affinity
      case "BLOB"      => db.SqliteType.Blob
      case "BINARY"    => db.SqliteType.Binary
      case "VARBINARY" => db.SqliteType.VarBinary

      // Date/time (TEXT-affinity, but we track them as date/time)
      case "DATE"      => db.SqliteType.Date
      case "TIME"      => db.SqliteType.Time
      case "DATETIME"  => db.SqliteType.Timestamp
      case "TIMESTAMP" => db.SqliteType.Timestamp

      // Convenience
      case "UUID" => db.SqliteType.Uuid
      case "JSON" => db.SqliteType.Json

      case _ =>
        // Fall back to SQLite's affinity rules on substrings, since SQLite itself accepts
        // arbitrary declarations like "VARYING CHARACTER" or "DOUBLE PRECISION".
        if (upper.contains("INT"))
          db.SqliteType.Integer
        else if (upper.contains("CHAR") || upper.contains("CLOB") || upper.contains("TEXT"))
          db.SqliteType.Text
        else if (upper.contains("BLOB") || upper.isEmpty)
          db.SqliteType.Blob
        else if (upper.contains("REAL") || upper.contains("FLOA") || upper.contains("DOUB"))
          db.SqliteType.Real
        else if (upper.contains("DEC") || upper.contains("NUM"))
          db.SqliteType.Decimal(None, None)
        else if (upper.contains("DATE") || upper.contains("TIME"))
          db.SqliteType.Timestamp
        else {
          logWarning()
          db.SqliteType.Text
        }
    }
  }
}

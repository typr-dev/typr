package typr
package internal

/** Determines what typeclass instances need to be generated for each shared type.
  *
  * When a TypeEntry is used across multiple databases and API specs, it needs different typeclass instances depending on where it's used:
  *   - Database-specific instances (pgType, mariaType, etc.)
  *   - JSON library instances (Jackson, Circe, etc.)
  */
object InstanceRequirements {

  /** Requirements for a single TypeEntry */
  case class Requirements(
      entry: TypeEntry,
      underlyingType: db.Type,
      databases: Set[DbType],
      hasApiUsage: Boolean
  ) {

    /** Database-specific type instance field names needed */
    def dbTypeFieldNames: Set[String] =
      databases.map(dbTypeFieldNameFor)

    /** Whether the underlying type is an array type */
    def hasArrayType: Boolean = underlyingType match {
      case db.PgType.Array(_)            => true
      case db.DuckDbType.ListType(_)     => true
      case db.DuckDbType.ArrayType(_, _) => true
      case _                             => false
    }
  }

  /** Collect requirements from compatibility check results */
  def collect(
      results: List[TypeCompatibilityChecker.CheckResult.Compatible]
  ): List[Requirements] =
    results.map { result =>
      val databases = result.matches.flatMap { m =>
        m.source match {
          case TypeMatcher.MatchSource.DbColumn(dbName, _, _, _) =>
            guessDbTypeFromName(dbName)
          case TypeMatcher.MatchSource.ApiField(_, _, _, _) =>
            None
        }
      }.toSet

      val hasApiUsage = result.matches.exists {
        case TypeMatcher.MatchResult(_, TypeMatcher.MatchSource.ApiField(_, _, _, _), _) => true
        case _                                                                           => false
      }

      Requirements(
        entry = result.entry,
        underlyingType = result.canonicalType,
        databases = databases,
        hasApiUsage = hasApiUsage
      )
    }

  /** Try to guess DbType from database name. This is a heuristic - users can also explicitly specify the database type.
    */
  private def guessDbTypeFromName(name: String): Option[DbType] = {
    val lower = name.toLowerCase
    if (lower.contains("postgres") || lower.contains("pg"))
      Some(DbType.PostgreSQL)
    else if (lower.contains("maria") || lower.contains("mysql"))
      Some(DbType.MariaDB)
    else if (lower.contains("duck"))
      Some(DbType.DuckDB)
    else if (lower.contains("oracle"))
      Some(DbType.Oracle)
    else if (lower.contains("sqlserver") || lower.contains("mssql"))
      Some(DbType.SqlServer)
    else if (lower.contains("db2"))
      Some(DbType.DB2)
    else
      None
  }

  /** Get the database type field name for a DbType */
  private def dbTypeFieldNameFor(dbType: DbType): String = dbType match {
    case DbType.PostgreSQL => "pgType"
    case DbType.MariaDB    => "mariaType"
    case DbType.DuckDB     => "duckDbType"
    case DbType.Oracle     => "oracleType"
    case DbType.SqlServer  => "sqlServerType"
    case DbType.DB2        => "db2Type"
  }
}

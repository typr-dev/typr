package typo
package internal
package codegen

case class SqlCastValue(typeName: String) {
  val withColons = s"::$typeName"
  val asCode: jvm.Code = jvm.Code.Str(withColons)

  override def toString: String = sys.error("don't write directly")
}

class SqlCast(needsTimestampCasts: Boolean) {

  /** cast to correctly insert into PG
    */
  def toPg(dbCol: db.Col): Option[SqlCastValue] =
    toPg(dbCol.tpe, dbCol.udtName)

  def toPg(param: ComputedSqlFile.Param): Option[SqlCastValue] =
    toPg(param.dbType, Some(param.udtName))

  /** cast to correctly insert into PG/MariaDB
    */
  def toPg(dbType: db.Type, udtName: Option[String]): Option[SqlCastValue] =
    dbType match {
      // Unknown type (extends both PgType and MariaType) - must be first
      case db.Unknown(sqlType) => Some(SqlCastValue(sqlType))
      // MariaDB types - no cast needed, driver handles it
      case _: db.MariaType => None
      // DuckDB types - handle similar to PostgreSQL
      case db.DuckDbType.Enum(name, _)                                           => Some(SqlCastValue(name))
      case db.DuckDbType.Boolean | db.DuckDbType.Text | db.DuckDbType.VarChar(_) => None
      case _: db.DuckDbType =>
        udtName.map {
          case ArrayName(x) => SqlCastValue(x + "[]")
          case other        => SqlCastValue(other)
        }
      // PostgreSQL types
      case db.PgType.EnumRef(enm)                                    => Some(SqlCastValue(enm.name.value))
      case db.PgType.Boolean | db.PgType.Text | db.PgType.VarChar(_) => None
      case _: db.PgType =>
        udtName.map {
          case ArrayName(x) => SqlCastValue(x + "[]")
          case other        => SqlCastValue(other)
        }
      // Other database types - no cast needed
      case _ => None
    }

  def toPgCode(c: ComputedColumn): jvm.Code =
    toPg(c.dbCol).fold(jvm.Code.Empty)(_.asCode)

  /** avoid whatever the postgres driver does for these data formats by going through basic data types
    */
  def fromPg(dbType: db.Type): Option[SqlCastValue] =
    dbType match {
      case db.Unknown(_) =>
        Some(SqlCastValue("text"))
      case db.PgType.Array(db.Unknown(_)) | db.PgType.Array(db.PgType.DomainRef(_, _, db.Unknown(_))) =>
        Some(SqlCastValue("text[]"))
      case _: db.MariaType  => None
      case _: db.DuckDbType => None // DuckDB doesn't need special casts for reading
      case db.PgType.DomainRef(_, _, underlying) =>
        fromPg(underlying)
      case db.PgType.PGmoney =>
        Some(SqlCastValue("numeric"))
      case db.PgType.Vector =>
        if (needsTimestampCasts) Some(SqlCastValue("float4[]")) else None
      case db.PgType.Array(db.PgType.PGmoney) =>
        Some(SqlCastValue("numeric[]"))
      case db.PgType.Array(db.PgType.DomainRef(_, underlying, _)) =>
        Some(SqlCastValue(underlying + "[]"))
      case db.PgType.TimestampTz | db.PgType.Timestamp | db.PgType.TimeTz | db.PgType.Time | db.PgType.Date =>
        if (needsTimestampCasts) Some(SqlCastValue("text")) else None
      case db.PgType.Array(db.PgType.TimestampTz | db.PgType.Timestamp | db.PgType.TimeTz | db.PgType.Time | db.PgType.Date) =>
        if (needsTimestampCasts) Some(SqlCastValue("text[]")) else None
      case _ => None
    }

  def fromPgCode(c: ComputedColumn): jvm.Code =
    fromPg(c.dbCol.tpe).fold(jvm.Code.Empty)(_.asCode)
}

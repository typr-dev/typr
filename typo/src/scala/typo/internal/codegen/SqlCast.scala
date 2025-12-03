package typo
package internal
package codegen

case class SqlCast(typeName: String) {
  val withColons = s"::$typeName"
  val asCode: jvm.Code = jvm.Code.Str(withColons)

  override def toString: String = sys.error("don't write directly")
}

object SqlCast {

  /** cast to correctly insert into PG
    */
  def toPg(dbCol: db.Col): Option[SqlCast] =
    toPg(dbCol.tpe, dbCol.udtName)

  def toPg(param: ComputedSqlFile.Param): Option[SqlCast] =
    toPg(param.dbType, Some(param.udtName))

  /** cast to correctly insert into PG/MariaDB
    */
  def toPg(dbType: db.Type, udtName: Option[String]): Option[SqlCast] =
    dbType match {
      // Unknown type (extends both PgType and MariaType) - must be first
      case db.Unknown(sqlType) => Some(SqlCast(sqlType))
      // MariaDB types - no cast needed, driver handles it
      case _: db.MariaType => None
      // PostgreSQL types
      case db.PgType.EnumRef(enm)                                    => Some(SqlCast(enm.name.value))
      case db.PgType.Boolean | db.PgType.Text | db.PgType.VarChar(_) => None
      case _: db.PgType =>
        udtName.map {
          case ArrayName(x) => SqlCast(x + "[]")
          case other        => SqlCast(other)
        }
    }

  def toPgCode(c: ComputedColumn): jvm.Code =
    toPg(c.dbCol).fold(jvm.Code.Empty)(_.asCode)

  /** avoid whatever the postgres driver does for these data formats by going through basic data types
    */
  def fromPg(dbType: db.Type): Option[SqlCast] =
    dbType match {
      // Unknown type (extends both PgType and MariaType) - must be first
      case db.Unknown(_) =>
        Some(SqlCast("text"))
      // MariaDB types - no cast needed for reading
      case _: db.MariaType => None
      // PostgreSQL types
      case db.PgType.Array(db.Unknown(_)) | db.PgType.Array(db.PgType.DomainRef(_, _, db.Unknown(_))) =>
        Some(SqlCast("text[]"))
      case db.PgType.DomainRef(_, _, underlying) =>
        fromPg(underlying)
      case db.PgType.PGmoney =>
        Some(SqlCast("numeric"))
      case db.PgType.Vector =>
        Some(SqlCast("float4[]"))
      case db.PgType.Array(db.PgType.PGmoney) =>
        Some(SqlCast("numeric[]"))
      case db.PgType.Array(db.PgType.DomainRef(_, underlying, _)) =>
        Some(SqlCast(underlying + "[]"))
      case db.PgType.TimestampTz | db.PgType.Timestamp | db.PgType.TimeTz | db.PgType.Time | db.PgType.Date =>
        Some(SqlCast("text"))
      case db.PgType.Array(db.PgType.TimestampTz | db.PgType.Timestamp | db.PgType.TimeTz | db.PgType.Time | db.PgType.Date) =>
        Some(SqlCast("text[]"))
      case _ => None
    }

  def fromPgCode(c: ComputedColumn): jvm.Code =
    fromPg(c.dbCol.tpe).fold(jvm.Code.Empty)(_.asCode)
}

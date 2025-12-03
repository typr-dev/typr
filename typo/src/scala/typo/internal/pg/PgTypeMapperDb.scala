package typo
package internal
package pg

import typo.generated.information_schema.columns.ColumnsViewRow

/** PostgreSQL type mapper from database metadata to db.PgType */
case class PgTypeMapperDb(enums: List[db.StringEnum], domains: List[db.Domain]) extends TypeMapperDb {
  val domainsByName: Map[String, db.Domain] = domains.flatMap(e => List((e.name.name, e), (e.name.value, e))).toMap
  val enumsByName = enums.flatMap(e => List((e.name.name, e), (e.name.value, e))).toMap

  def col(c: ColumnsViewRow)(logWarning: () => Unit): db.PgType = {
    val fromDomain: Option[db.PgType.DomainRef] =
      c.domainName.map { domainName =>
        val domainRelationName = db.RelationName(c.domainSchema, domainName)
        val d = domainsByName(domainRelationName.value)
        db.PgType.DomainRef(d.name, d.originalType, dbTypeFrom(d.originalType, c.characterMaximumLength)(logWarning))
      }

    fromDomain.getOrElse(dbTypeFrom(c.udtName.get, c.characterMaximumLength)(logWarning))
  }
  override def dbTypeFrom(udtName: String, characterMaximumLength: Option[Int])(logWarning: () => Unit): db.PgType = {
    udtName.replaceAll("\"", "") match {
      case "bool"                              => db.PgType.Boolean
      case "box"                               => db.PgType.PGbox
      case "bpchar"                            => db.PgType.Bpchar(characterMaximumLength)
      case "bytea"                             => db.PgType.Bytea
      case "char"                              => db.PgType.Char
      case "cid"                               => db.PgType.Int4 // command identifier
      case "circle"                            => db.PgType.PGcircle
      case "date"                              => db.PgType.Date
      case "float4"                            => db.PgType.Float4
      case "float8"                            => db.PgType.Float8
      case "hstore"                            => db.PgType.Hstore
      case "inet"                              => db.PgType.Inet
      case "int2" | "smallint" | "smallserial" => db.PgType.Int2
      case "int4" | "integer" | "serial"       => db.PgType.Int4
      case "int8" | "bigint" | "bigserial"     => db.PgType.Int8
      case "interval"                          => db.PgType.PGInterval
      case "json"                              => db.PgType.Json
      case "jsonb"                             => db.PgType.Jsonb
      case "line"                              => db.PgType.PGline
      case "lseg"                              => db.PgType.PGlseg
      case "money"                             => db.PgType.PGmoney
      case "name"                              => db.PgType.Name
      case "numeric" | "decimal"               => db.PgType.Numeric
      case "oid"                               => db.PgType.Oid // numeric object identifier
      case "path"                              => db.PgType.PGpath
      case "pg_lsn"                            => db.PgType.PGlsn
      case "point"                             => db.PgType.PGpoint
      case "polygon"                           => db.PgType.PGpolygon
      case "text"                              => db.PgType.Text
      case "time"                              => db.PgType.Time
      case "timetz"                            => db.PgType.TimeTz
      case "timestamp"                         => db.PgType.Timestamp
      case "timestamptz"                       => db.PgType.TimestampTz
      case "uuid"                              => db.PgType.UUID
      case "varchar"                           => db.PgType.VarChar(characterMaximumLength)
      case "xml"                               => db.PgType.Xml
      case "aclitem"                           => db.PgType.aclitem
      case "anyarray"                          => db.PgType.anyarray
      case "int2vector"                        => db.PgType.int2vector
      case "oidvector"                         => db.PgType.oidvector
      case "pg_node_tree"                      => db.PgType.pg_node_tree
      case "record"                            => db.PgType.record
      case "regclass"                          => db.PgType.regclass
      case "regconfig"                         => db.PgType.regconfig
      case "regdictionary"                     => db.PgType.regdictionary
      case "regnamespace"                      => db.PgType.regnamespace
      case "regoper"                           => db.PgType.regoper
      case "regoperator"                       => db.PgType.regoperator
      case "regproc"                           => db.PgType.regproc
      case "regprocedure"                      => db.PgType.regprocedure
      case "regrole"                           => db.PgType.regrole
      case "regtype"                           => db.PgType.regtype
      case "xid"                               => db.PgType.xid
      case "vector"                            => db.PgType.Vector
      case ArrayName(underlying) =>
        db.PgType.Array(dbTypeFrom(underlying, characterMaximumLength)(logWarning))
      case typeName =>
        enumsByName
          .get(typeName)
          .map(db.PgType.EnumRef.apply)
          .orElse(domainsByName.get(typeName).map(domain => db.PgType.DomainRef(domain.name, domain.originalType, domain.tpe)))
          .getOrElse {
            logWarning()
            db.Unknown(udtName)
          }
    }
  }
}

package typo
package internal

case class TypeMapperJvmNew(
    lang: Lang,
    typeOverride: TypeOverride,
    nullabilityOverride: NullabilityOverride,
    naming: Naming
) extends TypeMapperJvm(lang, typeOverride, nullabilityOverride) {
  override def baseType(tpe: db.Type): jvm.Type = {
    tpe match {
      case db.Type.Array(_) => sys.error("no idea what to do with nested array types")
      case db.Type.Boolean  => lang.Boolean
      case db.Type.Bytea    => jvm.Type.ArrayOf(lang.Byte)
      case db.Type.Bpchar(maybeN) =>
        maybeN match {
          case Some(n) if n != 2147483647 => lang.String.withComment(s"bpchar, max $n chars")
          case _                          => lang.String.withComment(s"bpchar")
        }
      case db.Type.Char                  => lang.String
      case db.Type.Date                  => TypesJava.LocalDate
      case db.Type.DomainRef(name, _, _) => jvm.Type.Qualified(naming.domainName(name))
      case db.Type.Float4                => lang.Float
      case db.Type.Float8                => lang.Double
      case db.Type.Hstore                => jvm.Type.TApply(TypesJava.Map, List(lang.String, lang.String))
      case db.Type.Inet                  => TypesJava.runtime.Inet
      case db.Type.Int2                  => lang.Short
      case db.Type.Int4                  => lang.Int
      case db.Type.Int8                  => lang.Long
      case db.Type.Json                  => TypesJava.runtime.Json
      case db.Type.Jsonb                 => TypesJava.runtime.Jsonb
      case db.Type.Name                  => lang.String
      case db.Type.Numeric               => lang.BigDecimal
      case db.Type.Oid                   => lang.Long.withComment("oid")
      case db.Type.PGInterval            => TypesJava.PGInterval
      case db.Type.PGbox                 => TypesJava.PGbox
      case db.Type.PGcircle              => TypesJava.PGcircle
      case db.Type.PGline                => TypesJava.PGline
      case db.Type.PGlseg                => TypesJava.PGline
      case db.Type.PGlsn                 => lang.Long.withComment("pg_lsn")
      case db.Type.PGmoney               => TypesJava.runtime.Money
      case db.Type.PGpath                => TypesJava.PGpath
      case db.Type.PGpoint               => TypesJava.PGpoint
      case db.Type.PGpolygon             => TypesJava.PGpolygon
      case db.Type.aclitem               => TypesJava.runtime.AclItem
      case db.Type.anyarray              => TypesJava.runtime.AnyArray
      case db.Type.int2vector            => TypesJava.runtime.Int2Vector
      case db.Type.oidvector             => TypesJava.runtime.OidVector
      case db.Type.pg_node_tree          => TypesJava.runtime.PgNodeTree
      case db.Type.record                => TypesJava.runtime.Record
      case db.Type.regclass              => TypesJava.runtime.Regclass
      case db.Type.regconfig             => TypesJava.runtime.Regconfig
      case db.Type.regdictionary         => TypesJava.runtime.Regdictionary
      case db.Type.regnamespace          => TypesJava.runtime.Regnamespace
      case db.Type.regoper               => TypesJava.runtime.Regoper
      case db.Type.regoperator           => TypesJava.runtime.Regoperator
      case db.Type.regproc               => TypesJava.runtime.Regproc
      case db.Type.regprocedure          => TypesJava.runtime.Regprocedure
      case db.Type.regrole               => TypesJava.runtime.Regrole
      case db.Type.regtype               => TypesJava.runtime.Regtype
      case db.Type.xid                   => TypesJava.runtime.Xid
      case db.Type.EnumRef(enm)          => jvm.Type.Qualified(naming.enumName(enm.name))
      case db.Type.Text                  => lang.String
      case db.Type.Time                  => TypesJava.LocalTime
      case db.Type.TimeTz                => TypesJava.OffsetTime
      case db.Type.Timestamp             => TypesJava.LocalDateTime
      case db.Type.TimestampTz           => TypesJava.Instant
      case db.Type.UUID                  => TypesJava.UUID
      case db.Type.Xml                   => TypesJava.runtime.Xml
      case db.Type.VarChar(maybeN) =>
        maybeN match {
          case Some(n) if n != 2147483647 => lang.String.withComment(s"max $n chars")
          case _                          => lang.String
        }
      case db.Type.Vector     => TypesJava.runtime.Vector
      case db.Type.Unknown(_) => TypesJava.runtime.Unknown
    }
  }

}

package typo
package internal

case class TypeMapperJvmOld(lang: Lang, typeOverride: TypeOverride, nullabilityOverride: NullabilityOverride, naming: Naming, customTypes: CustomTypes)
    extends TypeMapperJvm(lang, typeOverride, nullabilityOverride) {

  override def baseType(tpe: db.Type): jvm.Type = {
    tpe match {
      // Unknown type - must be first as it extends all db type traits
      case db.Unknown(sqlType) => customTypes.TypoUnknown(sqlType).typoType

      // PostgreSQL types
      case pgType: db.PgType => pgBaseType(pgType)

      // MariaDB types
      case mariaType: db.MariaType => mariaBaseType(mariaType)
    }
  }

  private def pgBaseType(pgType: db.PgType): jvm.Type = pgType match {
    case db.Unknown(sqlType) => sys.error(s"Unknown type $sqlType should have been handled at top level")
    case db.PgType.Array(_)  => sys.error("no idea what to do with nested array types")
    case db.PgType.Boolean   => lang.Boolean
    case db.PgType.Bytea     => customTypes.TypoBytea.typoType
    case db.PgType.Bpchar(maybeN) =>
      maybeN match {
        case Some(n) if n != 2147483647 => lang.String.withComment(s"bpchar, max $n chars")
        case _                          => lang.String.withComment(s"bpchar")
      }
    case db.PgType.Char                  => lang.String
    case db.PgType.Date                  => customTypes.TypoLocalDate.typoType
    case db.PgType.DomainRef(name, _, _) => jvm.Type.Qualified(naming.domainName(name))
    case db.PgType.Float4                => lang.Float
    case db.PgType.Float8                => lang.Double
    case db.PgType.Hstore                => customTypes.TypoHStore.typoType
    case db.PgType.Inet                  => customTypes.TypoInet.typoType
    case db.PgType.Int2                  => customTypes.TypoShort.typoType
    case db.PgType.Int4                  => lang.Int
    case db.PgType.Int8                  => lang.Long
    case db.PgType.Json                  => customTypes.TypoJson.typoType
    case db.PgType.Jsonb                 => customTypes.TypoJsonb.typoType
    case db.PgType.Name                  => lang.String
    case db.PgType.Numeric               => lang.BigDecimal
    case db.PgType.Oid                   => lang.Long.withComment("oid")
    case db.PgType.PGInterval            => customTypes.TypoInterval.typoType
    case db.PgType.PGbox                 => customTypes.TypoBox.typoType
    case db.PgType.PGcircle              => customTypes.TypoCircle.typoType
    case db.PgType.PGline                => customTypes.TypoLine.typoType
    case db.PgType.PGlseg                => customTypes.TypoLineSegment.typoType
    case db.PgType.PGlsn                 => lang.Long.withComment("pg_lsn")
    case db.PgType.PGmoney               => customTypes.TypoMoney.typoType
    case db.PgType.PGpath                => customTypes.TypoPath.typoType
    case db.PgType.PGpoint               => customTypes.TypoPoint.typoType
    case db.PgType.PGpolygon             => customTypes.TypoPolygon.typoType
    case db.PgType.aclitem               => customTypes.TypoAclItem.typoType
    case db.PgType.anyarray              => customTypes.TypoAnyArray.typoType
    case db.PgType.int2vector            => customTypes.TypoInt2Vector.typoType
    case db.PgType.oidvector             => customTypes.TypoOidVector.typoType
    case db.PgType.pg_node_tree          => customTypes.TypoPgNodeTree.typoType
    case db.PgType.record                => customTypes.TypoRecord.typoType
    case db.PgType.regclass              => customTypes.TypoRegclass.typoType
    case db.PgType.regconfig             => customTypes.TypoRegconfig.typoType
    case db.PgType.regdictionary         => customTypes.TypoRegdictionary.typoType
    case db.PgType.regnamespace          => customTypes.TypoRegnamespace.typoType
    case db.PgType.regoper               => customTypes.TypoRegoper.typoType
    case db.PgType.regoperator           => customTypes.TypoRegoperator.typoType
    case db.PgType.regproc               => customTypes.TypoRegproc.typoType
    case db.PgType.regprocedure          => customTypes.TypoRegprocedure.typoType
    case db.PgType.regrole               => customTypes.TypoRegrole.typoType
    case db.PgType.regtype               => customTypes.TypoRegtype.typoType
    case db.PgType.xid                   => customTypes.TypoXid.typoType
    case db.PgType.EnumRef(enm)          => jvm.Type.Qualified(naming.enumName(enm.name))
    case db.PgType.Text                  => lang.String
    case db.PgType.Time                  => customTypes.TypoLocalTime.typoType
    case db.PgType.TimeTz                => customTypes.TypoOffsetTime.typoType
    case db.PgType.Timestamp             => customTypes.TypoLocalDateTime.typoType
    case db.PgType.TimestampTz           => customTypes.TypoInstant.typoType
    case db.PgType.UUID                  => customTypes.TypoUUID.typoType
    case db.PgType.Xml                   => customTypes.TypoXml.typoType
    case db.PgType.VarChar(maybeN) =>
      maybeN match {
        case Some(n) if n != 2147483647 => lang.String.withComment(s"max $n chars")
        case _                          => lang.String
      }
    case db.PgType.Vector => customTypes.TypoVector.typoType
  }

  private def mariaBaseType(mariaType: db.MariaType): jvm.Type = mariaType match {
    case db.Unknown(sqlType)             => sys.error(s"Unknown type $sqlType should have been handled at top level")
    case db.MariaType.TinyInt            => lang.Byte
    case db.MariaType.SmallInt           => lang.Short
    case db.MariaType.MediumInt          => lang.Int
    case db.MariaType.Int                => lang.Int
    case db.MariaType.BigInt             => lang.Long
    case db.MariaType.TinyIntUnsigned    => lang.Short
    case db.MariaType.SmallIntUnsigned   => lang.Int
    case db.MariaType.MediumIntUnsigned  => lang.Int
    case db.MariaType.IntUnsigned        => lang.Long
    case db.MariaType.BigIntUnsigned     => TypesJava.BigInteger
    case db.MariaType.Decimal(_, _)      => lang.BigDecimal
    case db.MariaType.Float              => lang.Float
    case db.MariaType.Double             => lang.Double
    case db.MariaType.Boolean            => lang.Boolean
    case db.MariaType.Bit(Some(1))       => lang.Boolean
    case db.MariaType.Bit(_)             => lang.ByteArrayType
    case db.MariaType.Char(_)            => lang.String
    case db.MariaType.VarChar(_)         => lang.String
    case db.MariaType.TinyText           => lang.String
    case db.MariaType.Text               => lang.String
    case db.MariaType.MediumText         => lang.String
    case db.MariaType.LongText           => lang.String
    case db.MariaType.Binary(_)          => lang.ByteArrayType
    case db.MariaType.VarBinary(_)       => lang.ByteArrayType
    case db.MariaType.TinyBlob           => lang.ByteArrayType
    case db.MariaType.Blob               => lang.ByteArrayType
    case db.MariaType.MediumBlob         => lang.ByteArrayType
    case db.MariaType.LongBlob           => lang.ByteArrayType
    case db.MariaType.Date               => TypesJava.LocalDate
    case db.MariaType.Time(_)            => TypesJava.LocalTime
    case db.MariaType.DateTime(_)        => TypesJava.LocalDateTime
    case db.MariaType.Timestamp(_)       => TypesJava.LocalDateTime
    case db.MariaType.Year               => TypesJava.Year
    case db.MariaType.Enum(_)            => lang.String // MariaDB inline ENUMs are stored as strings
    case db.MariaType.Set(_)             => TypesJava.maria.MariaSet
    case db.MariaType.Json               => TypesJava.runtime.Json
    case db.MariaType.Inet4              => TypesJava.maria.Inet4
    case db.MariaType.Inet6              => TypesJava.maria.Inet6
    case db.MariaType.Geometry           => TypesJava.maria.Geometry
    case db.MariaType.Point              => TypesJava.maria.Point
    case db.MariaType.LineString         => TypesJava.maria.LineString
    case db.MariaType.Polygon            => TypesJava.maria.Polygon
    case db.MariaType.MultiPoint         => TypesJava.maria.MultiPoint
    case db.MariaType.MultiLineString    => TypesJava.maria.MultiLineString
    case db.MariaType.MultiPolygon       => TypesJava.maria.MultiPolygon
    case db.MariaType.GeometryCollection => TypesJava.maria.GeometryCollection
  }
}

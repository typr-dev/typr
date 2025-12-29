package typr
package internal

case class TypeMapperJvmOld(lang: Lang, typeOverride: TypeOverride, nullabilityOverride: NullabilityOverride, naming: Naming, customTypes: CustomTypes)
    extends TypeMapperJvm(lang, typeOverride, nullabilityOverride) {

  override def needsTimestampCasts: Boolean = true

  override def baseType(tpe: db.Type): jvm.Type = {
    tpe match {
      case x: db.PgType =>
        x match {
          case db.PgType.Array(elementType) => jvm.Type.ArrayOf(baseType(elementType))
          case db.PgType.Boolean            => lang.Boolean
          case db.PgType.Bytea              => customTypes.TypoBytea.typoType
          case db.PgType.Bpchar(maybeN) =>
            maybeN match {
              case Some(n) if n > 0 && n != 2147483647 => lang.String.withComment(s"bpchar, max $n chars")
              case _                                   => lang.String.withComment(s"bpchar")
            }
          case db.PgType.Char                  => lang.String
          case db.PgType.Date                  => customTypes.TypoLocalDate.typoType
          case db.PgType.DomainRef(name, _, _) => jvm.Type.Qualified(naming.domainName(name))
          case db.PgType.Float4                => lang.Float
          case db.PgType.Float8                => lang.Double
          case db.PgType.Hstore                => customTypes.TypoHStore.typoType
          case db.PgType.Inet                  => customTypes.TypoInet.typoType
          case db.PgType.Cidr                  => customTypes.TypoCidr.typoType
          case db.PgType.MacAddr               => customTypes.TypoMacAddr.typoType
          case db.PgType.MacAddr8              => customTypes.TypoMacAddr8.typoType
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
              case Some(n) if n > 0 && n != 2147483647 => lang.String.withComment(s"max $n chars")
              case _                                   => lang.String
            }
          case db.PgType.Vector                 => customTypes.TypoVector.typoType
          case db.PgType.CompositeType(name, _) => jvm.Type.Qualified(naming.compositeTypeName(name))
          case db.Unknown(sqlType)              => customTypes.TypoUnknown(sqlType).typoType
        }

      case _ => sys.error("The library you chose can just be used with postgres")
    }
  }
}

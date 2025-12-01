package typo
package internal

case class TypeMapperJvmOld(lang: Lang, typeOverride: TypeOverride, nullabilityOverride: NullabilityOverride, naming: Naming, customTypes: CustomTypes)
    extends TypeMapperJvm(lang, typeOverride, nullabilityOverride) {
  override def baseType(tpe: db.Type): jvm.Type = {
    tpe match {
      case db.Type.Array(_) => sys.error("no idea what to do with nested array types")
      case db.Type.Boolean  => lang.Boolean
      case db.Type.Bytea    => customTypes.TypoBytea.typoType
      case db.Type.Bpchar(maybeN) =>
        maybeN match {
          case Some(n) if n != 2147483647 => lang.String.withComment(s"bpchar, max $n chars")
          case _                          => lang.String.withComment(s"bpchar")
        }
      case db.Type.Char                  => lang.String
      case db.Type.Date                  => customTypes.TypoLocalDate.typoType
      case db.Type.DomainRef(name, _, _) => jvm.Type.Qualified(naming.domainName(name))
      case db.Type.Float4                => lang.Float
      case db.Type.Float8                => lang.Double
      case db.Type.Hstore                => customTypes.TypoHStore.typoType
      case db.Type.Inet                  => customTypes.TypoInet.typoType
      case db.Type.Int2                  => customTypes.TypoShort.typoType
      case db.Type.Int4                  => lang.Int
      case db.Type.Int8                  => lang.Long
      case db.Type.Json                  => customTypes.TypoJson.typoType
      case db.Type.Jsonb                 => customTypes.TypoJsonb.typoType
      case db.Type.Name                  => lang.String
      case db.Type.Numeric               => lang.BigDecimal
      case db.Type.Oid                   => lang.Long.withComment("oid")
      case db.Type.PGInterval            => customTypes.TypoInterval.typoType
      case db.Type.PGbox                 => customTypes.TypoBox.typoType
      case db.Type.PGcircle              => customTypes.TypoCircle.typoType
      case db.Type.PGline                => customTypes.TypoLine.typoType
      case db.Type.PGlseg                => customTypes.TypoLineSegment.typoType
      case db.Type.PGlsn                 => lang.Long.withComment("pg_lsn")
      case db.Type.PGmoney               => customTypes.TypoMoney.typoType
      case db.Type.PGpath                => customTypes.TypoPath.typoType
      case db.Type.PGpoint               => customTypes.TypoPoint.typoType
      case db.Type.PGpolygon             => customTypes.TypoPolygon.typoType
      case db.Type.aclitem               => customTypes.TypoAclItem.typoType
      case db.Type.anyarray              => customTypes.TypoAnyArray.typoType
      case db.Type.int2vector            => customTypes.TypoInt2Vector.typoType
      case db.Type.oidvector             => customTypes.TypoOidVector.typoType
      case db.Type.pg_node_tree          => customTypes.TypoPgNodeTree.typoType
      case db.Type.record                => customTypes.TypoRecord.typoType
      case db.Type.regclass              => customTypes.TypoRegclass.typoType
      case db.Type.regconfig             => customTypes.TypoRegconfig.typoType
      case db.Type.regdictionary         => customTypes.TypoRegdictionary.typoType
      case db.Type.regnamespace          => customTypes.TypoRegnamespace.typoType
      case db.Type.regoper               => customTypes.TypoRegoper.typoType
      case db.Type.regoperator           => customTypes.TypoRegoperator.typoType
      case db.Type.regproc               => customTypes.TypoRegproc.typoType
      case db.Type.regprocedure          => customTypes.TypoRegprocedure.typoType
      case db.Type.regrole               => customTypes.TypoRegrole.typoType
      case db.Type.regtype               => customTypes.TypoRegtype.typoType
      case db.Type.xid                   => customTypes.TypoXid.typoType
      case db.Type.EnumRef(enm)          => jvm.Type.Qualified(naming.enumName(enm.name))
      case db.Type.Text                  => lang.String
      case db.Type.Time                  => customTypes.TypoLocalTime.typoType
      case db.Type.TimeTz                => customTypes.TypoOffsetTime.typoType
      case db.Type.Timestamp             => customTypes.TypoLocalDateTime.typoType
      case db.Type.TimestampTz           => customTypes.TypoInstant.typoType
      case db.Type.UUID                  => customTypes.TypoUUID.typoType
      case db.Type.Xml                   => customTypes.TypoXml.typoType
      case db.Type.VarChar(maybeN) =>
        maybeN match {
          case Some(n) if n != 2147483647 => lang.String.withComment(s"max $n chars")
          case _                          => lang.String
        }
      case db.Type.Vector           => customTypes.TypoVector.typoType
      case db.Type.Unknown(sqlType) => customTypes.TypoUnknown(sqlType).typoType
    }
  }

}

// Copyright (c) 2013-2020 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package typr.internal.analysis

import java.sql.Types

sealed abstract class JdbcType(val toInt: Int) extends Product with Serializable

object JdbcType {
  case object Array extends JdbcType(Types.ARRAY)
  case object BigInt extends JdbcType(Types.BIGINT)
  case object Binary extends JdbcType(Types.BINARY)
  case object Bit extends JdbcType(Types.BIT)
  case object Blob extends JdbcType(Types.BLOB)
  case object Boolean extends JdbcType(Types.BOOLEAN)
  case object Char extends JdbcType(Types.CHAR)
  case object Clob extends JdbcType(Types.CLOB)
  case object DataLink extends JdbcType(Types.DATALINK)
  case object Date extends JdbcType(Types.DATE)
  case object Decimal extends JdbcType(Types.DECIMAL)
  case object Distinct extends JdbcType(Types.DISTINCT)
  case object Double extends JdbcType(Types.DOUBLE)
  case object Float extends JdbcType(Types.FLOAT)
  case object Integer extends JdbcType(Types.INTEGER)
  case object JavaObject extends JdbcType(Types.JAVA_OBJECT)
  case object LongnVarChar extends JdbcType(Types.LONGNVARCHAR)
  case object LongVarBinary extends JdbcType(Types.LONGVARBINARY)
  case object LongVarChar extends JdbcType(Types.LONGVARCHAR)
  case object NChar extends JdbcType(Types.NCHAR)
  case object NClob extends JdbcType(Types.NCLOB)
  case object Null extends JdbcType(Types.NULL)
  case object Numeric extends JdbcType(Types.NUMERIC)
  case object NVarChar extends JdbcType(Types.NVARCHAR)
  case object Other extends JdbcType(Types.OTHER)
  case object Real extends JdbcType(Types.REAL)
  case object Ref extends JdbcType(Types.REF)
  case object RefCursor extends JdbcType(Types.REF_CURSOR)
  case object RowId extends JdbcType(Types.ROWID)
  case object SmallInt extends JdbcType(Types.SMALLINT)
  case object SqlXml extends JdbcType(Types.SQLXML)
  case object Struct extends JdbcType(Types.STRUCT)
  case object Time extends JdbcType(Types.TIME)
  case object TimeWithTimezone extends JdbcType(Types.TIME_WITH_TIMEZONE)
  case object Timestamp extends JdbcType(Types.TIMESTAMP)
  case object TimestampWithTimezone extends JdbcType(Types.TIMESTAMP_WITH_TIMEZONE)
  case object TinyInt extends JdbcType(Types.TINYINT)
  case object VarBinary extends JdbcType(Types.VARBINARY)
  case object VarChar extends JdbcType(Types.VARCHAR)

  /** A catch-all constructor for JDBC type constants outside the specification and known extensions. */
  final case class Unknown(override val toInt: Int) extends JdbcType(toInt)

  def fromInt(n: Int): JdbcType =
    n match {
      case Array.toInt                 => Array
      case BigInt.toInt                => BigInt
      case Binary.toInt                => Binary
      case Bit.toInt                   => Bit
      case Blob.toInt                  => Blob
      case Boolean.toInt               => Boolean
      case Char.toInt                  => Char
      case Clob.toInt                  => Clob
      case DataLink.toInt              => DataLink
      case Date.toInt                  => Date
      case Decimal.toInt               => Decimal
      case Distinct.toInt              => Distinct
      case Double.toInt                => Double
      case Float.toInt                 => Float
      case Integer.toInt               => Integer
      case JavaObject.toInt            => JavaObject
      case LongnVarChar.toInt          => LongnVarChar
      case LongVarBinary.toInt         => LongVarBinary
      case LongVarChar.toInt           => LongVarChar
      case NChar.toInt                 => NChar
      case NClob.toInt                 => NClob
      case Null.toInt                  => Null
      case Numeric.toInt               => Numeric
      case NVarChar.toInt              => NVarChar
      case Other.toInt                 => Other
      case Real.toInt                  => Real
      case Ref.toInt                   => Ref
      case RefCursor.toInt             => RefCursor
      case RowId.toInt                 => RowId
      case SmallInt.toInt              => SmallInt
      case SqlXml.toInt                => SqlXml
      case Struct.toInt                => Struct
      case Time.toInt                  => Time
      case TimeWithTimezone.toInt      => TimeWithTimezone
      case Timestamp.toInt             => Timestamp
      case TimestampWithTimezone.toInt => TimestampWithTimezone
      case TinyInt.toInt               => TinyInt
      case VarBinary.toInt             => VarBinary
      case VarChar.toInt               => VarChar

      // Gets a little iffy here. H2 reports NVarChar as -10 rather than -9 ... no idea. It's
      // definitely not in the spec. So let's just accept it here and call it good. What's the
      // worst thing that could happen? heh-heh
      case -10 => NVarChar

      // In the case of an unknown value we construct a catch-all
      case n => Unknown(n)

    }

}

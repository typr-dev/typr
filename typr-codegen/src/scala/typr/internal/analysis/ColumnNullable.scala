// Copyright (c) 2013-2020 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package typr.internal.analysis

import typr.Nullability

import java.sql.ResultSetMetaData

sealed abstract class ColumnNullable(val toInt: Int) extends Product with Serializable {
  def toNullability: Nullability =
    this match {
      case ColumnNullable.NoNulls         => Nullability.NoNulls
      case ColumnNullable.Nullable        => Nullability.Nullable
      case ColumnNullable.NullableUnknown => Nullability.NullableUnknown
    }
}

object ColumnNullable {
  case object NoNulls extends ColumnNullable(ResultSetMetaData.columnNoNulls)
  case object Nullable extends ColumnNullable(ResultSetMetaData.columnNullable)
  case object NullableUnknown extends ColumnNullable(ResultSetMetaData.columnNullableUnknown)

  def fromInt(n: Int): Option[ColumnNullable] =
    Some(n) collect {
      case NoNulls.toInt         => NoNulls
      case Nullable.toInt        => Nullable
      case NullableUnknown.toInt => NullableUnknown
    }
}

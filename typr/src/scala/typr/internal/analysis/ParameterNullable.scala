// Copyright (c) 2013-2020 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package typr.internal.analysis

import typr.Nullability

import java.sql.ParameterMetaData

sealed abstract class ParameterNullable(val toInt: Int) extends Product with Serializable {
  def toNullability: Nullability =
    this match {
      case ParameterNullable.NoNulls         => Nullability.NoNulls
      case ParameterNullable.Nullable        => Nullability.Nullable
      case ParameterNullable.NullableUnknown => Nullability.NullableUnknown
    }
}

object ParameterNullable {

  case object NoNulls extends ParameterNullable(ParameterMetaData.parameterNoNulls)

  case object Nullable extends ParameterNullable(ParameterMetaData.parameterNullable)

  case object NullableUnknown extends ParameterNullable(ParameterMetaData.parameterNullableUnknown)

  def fromInt(n: Int): Option[ParameterNullable] =
    Some(n) collect {
      case NoNulls.toInt         => NoNulls
      case Nullable.toInt        => Nullable
      case NullableUnknown.toInt => NullableUnknown
    }
}

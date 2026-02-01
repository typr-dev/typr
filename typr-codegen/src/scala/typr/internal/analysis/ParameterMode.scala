// Copyright (c) 2013-2020 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package typr.internal.analysis

import java.sql.ParameterMetaData

sealed abstract class ParameterMode(val toInt: Int) extends Product with Serializable

object ParameterMode {
  case object ModeIn extends ParameterMode(ParameterMetaData.parameterModeIn)
  case object ModeOut extends ParameterMode(ParameterMetaData.parameterModeOut)
  case object ModeInOut extends ParameterMode(ParameterMetaData.parameterModeInOut)
  case object ModeUnknown extends ParameterMode(ParameterMetaData.parameterModeUnknown)

  def fromInt(n: Int): Option[ParameterMode] =
    Some(n) collect {
      case ModeIn.toInt      => ModeIn
      case ModeOut.toInt     => ModeOut
      case ModeInOut.toInt   => ModeInOut
      case ModeUnknown.toInt => ModeUnknown
    }
}

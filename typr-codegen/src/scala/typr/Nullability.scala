// Copyright (c) 2013-2020 Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package typr

/** Generic nullability that subsumes JDBC's distinct parameter and column nullability.
  */
sealed abstract class Nullability extends Product with Serializable

object Nullability {
  sealed abstract class NullabilityKnown extends Nullability
  case object NoNulls extends NullabilityKnown
  case object Nullable extends NullabilityKnown
  case object NullableUnknown extends Nullability
}

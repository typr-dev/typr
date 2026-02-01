package typr
package internal
package analysis

/** Well-known primitive type names that can be used unqualified in SQL parameter annotations.
  *
  * Contract:
  *   - Unqualified names (no `.`) must be one of these primitives (case-insensitive)
  *   - Qualified names (contains `.`) must be types that provide their own `dbType` field
  */
sealed abstract class WellKnownPrimitive(val name: String)

object WellKnownPrimitive {
  case object String extends WellKnownPrimitive("String")
  case object Boolean extends WellKnownPrimitive("Boolean")
  case object Byte extends WellKnownPrimitive("Byte")
  case object Short extends WellKnownPrimitive("Short")
  case object Int extends WellKnownPrimitive("Int")
  case object Long extends WellKnownPrimitive("Long")
  case object Float extends WellKnownPrimitive("Float")
  case object Double extends WellKnownPrimitive("Double")
  case object BigDecimal extends WellKnownPrimitive("BigDecimal")
  case object LocalDate extends WellKnownPrimitive("LocalDate")
  case object LocalTime extends WellKnownPrimitive("LocalTime")
  case object LocalDateTime extends WellKnownPrimitive("LocalDateTime")
  case object Instant extends WellKnownPrimitive("Instant")
  case object UUID extends WellKnownPrimitive("UUID")

  /** Parse an unqualified type name to a well-known primitive (case-insensitive) */
  def fromName(name: String): Option[WellKnownPrimitive] = {
    name.toLowerCase match {
      case "string"                     => Some(String)
      case "boolean" | "bool"           => Some(Boolean)
      case "byte"                       => Some(Byte)
      case "short"                      => Some(Short)
      case "int" | "integer"            => Some(Int)
      case "long"                       => Some(Long)
      case "float"                      => Some(Float)
      case "double"                     => Some(Double)
      case "bigdecimal" | "decimal"     => Some(BigDecimal)
      case "localdate" | "date"         => Some(LocalDate)
      case "localtime" | "time"         => Some(LocalTime)
      case "localdatetime" | "datetime" => Some(LocalDateTime)
      case "instant" | "timestamp"      => Some(Instant)
      case "uuid"                       => Some(UUID)
      case _                            => None
    }
  }

  val all: List[WellKnownPrimitive] = List(
    String,
    Boolean,
    Byte,
    Short,
    Int,
    Long,
    Float,
    Double,
    BigDecimal,
    LocalDate,
    LocalTime,
    LocalDateTime,
    Instant,
    UUID
  )
}

package typr
package internal
package analysis

import play.api.libs.json.{Json, Writes}

/** Represents a user-specified type override in SQL parameter annotations.
  *
  * Contract:
  *   - Unqualified names (no `.`) must be well-known primitives (case-insensitive): String, Int, Boolean, etc.
  *   - Qualified names (contains `.`) must be types that provide their own `pgType`/`mariaType` field
  */
sealed trait OverriddenType {

  /** Get the JVM type for this override, given the target language */
  def jvmType(lang: Lang): jvm.Type

  /** Convert to Either form for TypoType.UserDefined */
  def toEither: Either[jvm.Type.Qualified, WellKnownPrimitive]
}

object OverriddenType {

  /** A well-known primitive type (unqualified name like "String", "Int", "Boolean") */
  case class Primitive(wellKnown: WellKnownPrimitive) extends OverriddenType {
    def jvmType(lang: Lang): jvm.Type = lang.typeSupport.forPrimitive(wellKnown)
    def toEither: Either[jvm.Type.Qualified, WellKnownPrimitive] = Right(wellKnown)
  }

  /** A qualified type that must provide its own pgType/mariaType field */
  case class Qualified(tpe: jvm.Type.Qualified) extends OverriddenType {
    def jvmType(lang: Lang): jvm.Type = jvm.Type.UserDefined(tpe)
    def toEither: Either[jvm.Type.Qualified, WellKnownPrimitive] = Left(tpe)
  }

  /** Parse a type string from SQL annotation.
    *   - If contains `.`, treat as qualified (user must provide pgType)
    *   - Otherwise, must be a well-known primitive name (case-insensitive)
    */
  def parse(typeStr: String): Either[String, OverriddenType] = {
    if (typeStr.contains(".")) {
      Right(Qualified(jvm.Type.Qualified(typeStr)))
    } else {
      WellKnownPrimitive.fromName(typeStr) match {
        case Some(primitive) => Right(Primitive(primitive))
        case None =>
          val validNames = WellKnownPrimitive.all.flatMap { p =>
            // Get the canonical names for error message
            p match {
              case WellKnownPrimitive.String        => List("String")
              case WellKnownPrimitive.Boolean       => List("Boolean", "Bool")
              case WellKnownPrimitive.Byte          => List("Byte")
              case WellKnownPrimitive.Short         => List("Short")
              case WellKnownPrimitive.Int           => List("Int", "Integer")
              case WellKnownPrimitive.Long          => List("Long")
              case WellKnownPrimitive.Float         => List("Float")
              case WellKnownPrimitive.Double        => List("Double")
              case WellKnownPrimitive.BigDecimal    => List("BigDecimal", "Decimal")
              case WellKnownPrimitive.LocalDate     => List("LocalDate", "Date")
              case WellKnownPrimitive.LocalTime     => List("LocalTime", "Time")
              case WellKnownPrimitive.LocalDateTime => List("LocalDateTime", "DateTime")
              case WellKnownPrimitive.Instant       => List("Instant", "Timestamp")
              case WellKnownPrimitive.UUID          => List("UUID")
            }
          }
          Left(s"Unknown type '$typeStr'. Unqualified names must be one of: ${validNames.mkString(", ")}. For custom types, use a fully qualified name (e.g., 'mypackage.MyType').")
      }
    }
  }
}

case class ParsedName(name: db.ColName, originalName: db.ColName, nullability: Option[Nullability], overriddenType: Option[OverriddenType]) {

  /** Get the JVM type if overridden, given the target language */
  def overriddenJvmType(lang: Lang): Option[jvm.Type] = overriddenType.map(_.jvmType(lang))
}

object ParsedName {
  def of(name: String): ParsedName = {
    val (shortened, nullability) =
      if (name.endsWith("?")) (name.dropRight(1), Some(Nullability.Nullable))
      else if (name.endsWith("!")) (name.dropRight(1), Some(Nullability.NoNulls))
      else (name, None)

    val (dbName, overriddenType) = shortened.split(":").toList match {
      case Nil         => sys.error("shouldn't happen (tm)")
      case name :: Nil => (db.ColName(name), None)
      case name :: tpeStr :: _ =>
        OverriddenType.parse(tpeStr) match {
          case Right(ot) => (db.ColName(name), Some(ot))
          case Left(err) => sys.error(s"Invalid type annotation in '$name': $err")
        }
    }
    ParsedName(dbName, originalName = db.ColName(name), nullability, overriddenType)
  }

  implicit val oformat: Writes[ParsedName] = (x: ParsedName) =>
    Json.obj(
      "name" -> x.name.value,
      "originalName" -> x.originalName.value,
      "nullability" -> x.nullability.map(_.toString),
      "overriddenType" -> x.overriddenType.map {
        case OverriddenType.Primitive(p) => p.name
        case OverriddenType.Qualified(q) => q.dotName
      }
    )
}

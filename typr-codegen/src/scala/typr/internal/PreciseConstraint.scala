package typr
package internal

sealed trait PreciseConstraint {
  def dbType: db.Type
}

object PreciseConstraint {
  case class StringN(maxLength: Int, dbType: db.Type) extends PreciseConstraint
  case class PaddedStringN(length: Int, dbType: db.Type) extends PreciseConstraint
  case class NonEmptyStringN(maxLength: Int, dbType: db.Type) extends PreciseConstraint
  case class NonEmptyPaddedStringN(length: Int, dbType: db.Type) extends PreciseConstraint
  case class BinaryN(maxLength: Int, dbType: db.Type) extends PreciseConstraint
  case class DecimalN(precision: Int, scale: Int, dbType: db.Type) extends PreciseConstraint
  case class LocalDateTimeN(fsp: Int, dbType: db.Type) extends PreciseConstraint
  case class LocalTimeN(fsp: Int, dbType: db.Type) extends PreciseConstraint
  case class OffsetDateTimeN(fsp: Int, dbType: db.Type) extends PreciseConstraint
  case class InstantN(fsp: Int, dbType: db.Type) extends PreciseConstraint
}

package typr

sealed trait JsonLibName

object JsonLibName {
  case object Circe extends JsonLibName
  case object PlayJson extends JsonLibName
  case object ZioJson extends JsonLibName
  case object Jackson extends JsonLibName
}

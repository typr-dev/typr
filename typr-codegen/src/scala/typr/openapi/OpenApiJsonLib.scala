package typr.openapi

sealed trait OpenApiJsonLib

object OpenApiJsonLib {
  case object Jackson extends OpenApiJsonLib
  case object Circe extends OpenApiJsonLib
}

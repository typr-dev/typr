package typr.jsonschema

import typr.jvm
import typr.openapi.OpenApiJsonLib

case class JsonSchemaOptions(
    pkg: jvm.QIdent,
    jsonLib: OpenApiJsonLib,
    generateWrapperTypes: Boolean,
    typeOverrides: Map[String, jvm.Type.Qualified],
    useOptionalForNullable: Boolean,
    generateValidation: Boolean
)

object JsonSchemaOptions {
  def default(pkg: jvm.QIdent): JsonSchemaOptions =
    JsonSchemaOptions(
      pkg = pkg,
      jsonLib = OpenApiJsonLib.Jackson,
      generateWrapperTypes = true,
      typeOverrides = Map.empty,
      useOptionalForNullable = false,
      generateValidation = false
    )
}

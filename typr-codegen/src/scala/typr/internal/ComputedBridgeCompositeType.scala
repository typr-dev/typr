package typr
package internal

/** Computed representation of a Bridge composite type for code generation. */
case class ComputedBridgeCompositeType(
    name: String,
    tpe: jvm.Type.Qualified,
    description: Option[String],
    fields: List[ComputedBridgeField],
    projections: Map[String, BridgeProjection],
    generateCanonical: Boolean,
    generateMappers: Boolean,
    generateInterface: Boolean,
    generateBuilder: Boolean,
    generateCopy: Boolean
)

case class ComputedBridgeField(
    name: jvm.Ident,
    tpe: jvm.Type,
    rawTypeName: String,
    nullable: Boolean,
    array: Boolean,
    description: Option[String]
)

object ComputedBridgeCompositeType {

  def fromConfig(
      compositeTypes: Map[String, BridgeCompositeType],
      pkg: jvm.QIdent,
      lang: Lang
  ): List[ComputedBridgeCompositeType] = {
    val bridgeTypesPackage = pkg / jvm.Ident("bridge")

    compositeTypes.toList.map { case (name, ct) =>
      val tpe = jvm.Type.Qualified(bridgeTypesPackage / jvm.Ident(name))

      val fields = ct.fields.map { field =>
        val baseType = resolveFieldType(field.typeName, bridgeTypesPackage, lang)
        val arrayType = if (field.array) jvm.Type.TApply(lang.ListType.tpe, List(baseType)) else baseType
        val finalType = if (field.nullable) lang.Optional.tpe(arrayType) else arrayType

        ComputedBridgeField(
          name = jvm.Ident(field.name),
          tpe = finalType,
          rawTypeName = field.typeName,
          nullable = field.nullable,
          array = field.array,
          description = field.description
        )
      }

      ComputedBridgeCompositeType(
        name = name,
        tpe = tpe,
        description = ct.description,
        fields = fields,
        projections = ct.projections,
        generateCanonical = ct.generateCanonical,
        generateMappers = ct.generateMappers,
        generateInterface = ct.generateInterface,
        generateBuilder = ct.generateBuilder,
        generateCopy = ct.generateCopy
      )
    }
  }

  private def resolveFieldType(typeName: String, bridgePackage: jvm.QIdent, lang: Lang): jvm.Type = {
    typeName.toLowerCase match {
      case "string"                         => lang.String
      case "int" | "integer"                => lang.Int
      case "long"                           => lang.Long
      case "short"                          => lang.Short
      case "float"                          => lang.Float
      case "double"                         => lang.Double
      case "boolean" | "bool"               => lang.Boolean
      case "bigdecimal" | "decimal"         => lang.BigDecimal
      case "instant"                        => TypesJava.Instant
      case "localdate"                      => TypesJava.LocalDate
      case "localtime"                      => TypesJava.LocalTime
      case "localdatetime"                  => TypesJava.LocalDateTime
      case "offsetdatetime"                 => TypesJava.OffsetDateTime
      case "uuid"                           => TypesJava.UUID
      case "bytearray" | "bytes" | "byte[]" => jvm.Type.ArrayOf(lang.Byte)
      case "json"                           => jvm.Type.Qualified("com.fasterxml.jackson.databind.JsonNode")
      case other =>
        if (other.contains(".")) {
          jvm.Type.Qualified(other)
        } else {
          jvm.Type.Qualified(bridgePackage / jvm.Ident(other))
        }
    }
  }
}

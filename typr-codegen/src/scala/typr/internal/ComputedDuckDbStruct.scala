package typr
package internal

/** Computed representation of a DuckDB STRUCT type for code generation.
  *
  * @param name
  *   The generated class name (e.g., "EmployeeContactInfo")
  * @param tpe
  *   The fully qualified type (e.g., "showcase.EmployeeContactInfo")
  * @param fields
  *   The computed fields with their JVM types
  * @param underlying
  *   The original DuckDbNamedStruct
  */
case class ComputedDuckDbStruct(
    name: String,
    tpe: jvm.Type.Qualified,
    fields: List[ComputedDuckDbStructField],
    underlying: DuckDbNamedStruct
)

/** A single field in a computed DuckDB STRUCT.
  *
  * @param name
  *   The field name as a JVM identifier
  * @param tpe
  *   The JVM type for the field
  * @param dbType
  *   The original DuckDB type
  */
case class ComputedDuckDbStructField(
    name: jvm.Ident,
    tpe: jvm.Type,
    dbType: db.Type
)

object ComputedDuckDbStruct {
  def apply(naming: Naming, typeMapper: TypeMapperJvm)(namedStruct: DuckDbNamedStruct): ComputedDuckDbStruct = {
    val tpe = jvm.Type.Qualified(naming.structTypeName(namedStruct.name))
    val fields = namedStruct.structType.fields.map { case (fieldName, fieldType) =>
      ComputedDuckDbStructField(
        name = Naming.camelCaseIdent(fieldName.split('_')),
        tpe = typeMapper.baseType(fieldType),
        dbType = fieldType
      )
    }
    new ComputedDuckDbStruct(namedStruct.name, tpe, fields, namedStruct)
  }
}

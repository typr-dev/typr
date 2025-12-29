package typr
package internal

/** Computed representation of a PostgreSQL composite type for code generation.
  *
  * @param name
  *   The generated class name (e.g., "Address")
  * @param tpe
  *   The fully qualified type (e.g., "showcase.Address")
  * @param fields
  *   The computed fields with their JVM types
  * @param underlying
  *   The original PgCompositeType
  */
case class ComputedPgCompositeType(
    name: String,
    tpe: jvm.Type.Qualified,
    fields: List[ComputedPgCompositeField],
    underlying: PgCompositeType
)

/** A single field in a computed PostgreSQL composite type.
  *
  * @param name
  *   The field name as a JVM identifier
  * @param tpe
  *   The JVM type for the field
  * @param dbType
  *   The original PostgreSQL type
  * @param nullable
  *   Whether the field is nullable
  */
case class ComputedPgCompositeField(
    name: jvm.Ident,
    tpe: jvm.Type,
    dbType: db.Type,
    nullable: Boolean
)

object ComputedPgCompositeType {
  def apply(naming: Naming, typeMapper: TypeMapperJvm)(pgComposite: PgCompositeType): ComputedPgCompositeType = {
    val compositeType = pgComposite.compositeType
    val tpe = jvm.Type.Qualified(naming.compositeTypeName(compositeType.name))
    val fields = compositeType.fields.map { field =>
      ComputedPgCompositeField(
        name = Naming.camelCaseIdent(field.name.split('_')),
        tpe = typeMapper.baseType(field.tpe),
        dbType = field.tpe,
        nullable = field.nullable
      )
    }
    new ComputedPgCompositeType(compositeType.name.name, tpe, fields, pgComposite)
  }
}

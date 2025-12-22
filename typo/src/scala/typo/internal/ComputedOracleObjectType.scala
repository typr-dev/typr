package typo
package internal

case class ComputedOracleObjectType(
    tpe: jvm.Type.Qualified,
    attributes: List[ComputedOracleAttribute],
    underlying: db.OracleType.ObjectType
)

case class ComputedOracleAttribute(
    name: jvm.Ident,
    tpe: jvm.Type,
    dbAttribute: db.OracleType.ObjectAttribute
)

object ComputedOracleObjectType {
  def apply(naming: Naming, typeMapper: TypeMapperJvm)(objType: db.OracleType.ObjectType): ComputedOracleObjectType = {
    val tpe = jvm.Type.Qualified(naming.objectTypeName(objType.name))
    val attributes = objType.attributes.sortBy(_.position).map { attr =>
      ComputedOracleAttribute(
        name = naming.field(db.ColName(attr.name)),
        tpe = typeMapper.baseType(attr.tpe),
        dbAttribute = attr
      )
    }
    new ComputedOracleObjectType(tpe, attributes, objType)
  }
}

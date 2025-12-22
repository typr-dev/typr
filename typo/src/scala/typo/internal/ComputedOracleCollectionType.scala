package typo
package internal

case class ComputedOracleCollectionType(
    tpe: jvm.Type.Qualified,
    elementType: jvm.Type,
    underlying: db.OracleType
) {
  require(
    underlying.isInstanceOf[db.OracleType.VArray] || underlying.isInstanceOf[db.OracleType.NestedTable],
    s"underlying must be VArray or NestedTable, got: $underlying"
  )
}

object ComputedOracleCollectionType {
  def apply(naming: Naming, typeMapper: TypeMapperJvm)(collType: db.OracleType): Option[ComputedOracleCollectionType] = {
    collType match {
      case varray: db.OracleType.VArray =>
        Some(
          new ComputedOracleCollectionType(
            tpe = jvm.Type.Qualified(naming.objectTypeName(varray.name)),
            elementType = typeMapper.baseType(varray.elementType),
            underlying = varray
          )
        )
      case nested: db.OracleType.NestedTable =>
        Some(
          new ComputedOracleCollectionType(
            tpe = jvm.Type.Qualified(naming.objectTypeName(nested.name)),
            elementType = typeMapper.baseType(nested.elementType),
            underlying = nested
          )
        )
      case _ => None
    }
  }
}

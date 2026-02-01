package typr
package internal
package codegen

object FileOracleCollectionType {
  def apply(computed: ComputedOracleCollectionType, options: InternalOptions): jvm.File = {
    val comments = (computed.underlying: @unchecked) match {
      case v: db.OracleType.VArray =>
        scaladoc(List(s"Oracle VARRAY Type: ${v.name.value} (max size: ${v.maxSize})"))
      case n: db.OracleType.NestedTable =>
        scaladoc(List(s"Oracle Nested Table Type: ${n.name.value}"))
    }

    val value = jvm.Ident("value")

    // Generate as a wrapper type around an Array of the element type
    val wrapperType = jvm.Type.ArrayOf(computed.elementType)

    val params = List(jvm.Param(value, wrapperType))

    // Generate Oracle collection type instances (VArray or NestedTable)
    val collectionInstances = options.dbLib.toList.flatMap { dbLib =>
      dbLib.collectionInstances(computed)
    }

    val record = jvm.Adt.Record(
      annotations = Nil,
      constructorAnnotations = Nil,
      isWrapper = false,
      privateConstructor = false,
      comments = comments,
      name = computed.tpe,
      tparams = Nil,
      params = params,
      implicitParams = Nil,
      `extends` = None,
      implements = Nil,
      members = Nil,
      staticMembers = collectionInstances
    )

    jvm.File(computed.tpe, record, secondaryTypes = Nil, scope = Scope.Main)
  }
}

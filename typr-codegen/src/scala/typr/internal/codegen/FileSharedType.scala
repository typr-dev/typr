package typr
package internal
package codegen

/** Generates wrapper type files for TypeDefinitions-based shared types. */
object FileSharedType {

  def apply(computed: ComputedSharedType, options: InternalOptions, lang: Lang): jvm.File = {
    val comments = scaladoc(
      List(
        s"Shared type `${computed.entry.name}`",
        "Generated from TypeDefinitions matching"
      )
    )
    val value = jvm.Ident("value")

    val bijection =
      if (options.enableDsl)
        Some {
          val thisBijection = lang.dsl.Bijection.of(computed.tpe, computed.underlyingJvmType)
          val expr = lang.bijection(computed.tpe, computed.underlyingJvmType, jvm.FieldGetterRef(computed.tpe, value), jvm.ConstructorMethodRef(computed.tpe))
          jvm.Given(Nil, jvm.Ident("bijection"), Nil, thisBijection, expr)
        }
      else None

    val jsonInstances = options.jsonLibs.map(_.wrapperTypeInstances(wrapperType = computed.tpe, fieldName = value, underlying = computed.underlyingJvmType))
    val instances = List(
      bijection.toList,
      jsonInstances.flatMap(_.givens),
      options.dbLib.toList.flatMap(
        _.wrapperTypeInstances(
          wrapperType = computed.tpe,
          underlyingJvmType = computed.underlyingJvmType,
          underlyingDbType = computed.underlyingDbType,
          overrideDbType = None
        )
      )
    ).flatten
    val fieldAnnotations = JsonLib.mergeFieldAnnotations(jsonInstances.flatMap(_.fieldAnnotations.toList))
    val typeAnnotations = jsonInstances.flatMap(_.typeAnnotations)

    val paramsWithAnnotations = List(jvm.Param(value, computed.underlyingJvmType)).map { p =>
      fieldAnnotations.get(p.name) match {
        case Some(anns) => p.copy(annotations = p.annotations ++ anns)
        case None       => p
      }
    }

    val cls = jvm.Adt.Record(
      annotations = typeAnnotations,
      constructorAnnotations = Nil,
      isWrapper = true,
      privateConstructor = false,
      comments = comments,
      name = computed.tpe,
      tparams = Nil,
      params = paramsWithAnnotations,
      implicitParams = Nil,
      `extends` = None,
      implements = Nil,
      members = Nil,
      staticMembers = instances
    )

    jvm.File(computed.tpe, cls, secondaryTypes = Nil, scope = Scope.Main)
  }
}

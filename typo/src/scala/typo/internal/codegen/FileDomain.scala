package typo
package internal
package codegen

object FileDomain {
  def apply(domain: ComputedDomain, options: InternalOptions, lang: Lang): jvm.File = {
    val comments = scaladoc(
      List(
        s"Domain `${domain.underlying.name.value}`",
        domain.underlying.constraintDefinition match {
          case Some(definition) => s"Constraint: $definition"
          case None             => "No constraint"
        }
      )
    )
    val value = jvm.Ident("value")

    val bijection =
      if (options.enableDsl)
        Some {
          val thisBijection = jvm.Type.dsl.Bijection.of(domain.tpe, domain.underlyingType)
          val expr = lang.bijection(domain.tpe, domain.underlyingType, jvm.FieldGetterRef(domain.tpe, value), jvm.ConstructorMethodRef(domain.tpe))
          jvm.Given(Nil, jvm.Ident("bijection"), Nil, thisBijection, expr)
        }
      else None

    val jsonInstances = options.jsonLibs.map(_.wrapperTypeInstances(wrapperType = domain.tpe, fieldName = value, underlying = domain.underlyingType))
    val instances = List(
      bijection.toList,
      jsonInstances.flatMap(_.givens),
      options.dbLib.toList.flatMap(_.wrapperTypeInstances(wrapperType = domain.tpe, underlying = domain.underlyingType, overrideDbType = Some(domain.underlying.name.quotedValue)))
    ).flatten
    val fieldAnnotations = JsonLib.mergeFieldAnnotations(jsonInstances.flatMap(_.fieldAnnotations.toList))
    val typeAnnotations = jsonInstances.flatMap(_.typeAnnotations)

    val paramsWithAnnotations = List(jvm.Param(value, domain.underlyingType)).map { p =>
      fieldAnnotations.get(p.name) match {
        case Some(anns) => p.copy(annotations = p.annotations ++ anns)
        case None       => p
      }
    }

    val cls = jvm.Adt.Record(
      annotations = typeAnnotations,
      constructorAnnotations = Nil,
      isWrapper = false,
      comments = comments,
      name = domain.tpe,
      tparams = Nil,
      params = paramsWithAnnotations,
      implicitParams = Nil,
      `extends` = None,
      implements = Nil,
      members = Nil,
      staticMembers = instances
    )

    jvm.File(domain.tpe, cls, secondaryTypes = Nil, scope = Scope.Main)
  }
}

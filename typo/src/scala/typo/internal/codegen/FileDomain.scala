package typo
package internal
package codegen

object FileDomain {
  def apply(domain: ComputedDomain, options: InternalOptions, genOrdering: GenOrdering): sc.File = {
    val comments = scaladoc(s"Domain `${domain.underlying.name.value}`")(
      domain.underlying.constraintDefinition match {
        case Some(definition) => List(s"Constraint: $definition")
        case None             => List("No constraint")
      }
    )
    val value = sc.Ident("value")

    val bijection =
      if (options.enableDsl)
        Some {
          val thisBijection = sc.Type.dsl.Bijection.of(domain.tpe, domain.underlyingType)
          sc.Given(Nil, sc.Ident("bijection"), Nil, thisBijection, code"$thisBijection(_.$value)(${domain.tpe}.apply)")
        }
      else None
    val instances = List(
      List(
        genOrdering.ordering(domain.tpe, NonEmptyList(sc.Param(value, domain.underlyingType, None)))
      ),
      bijection.toList,
      options.jsonLibs.flatMap(_.wrapperTypeInstances(wrapperType = domain.tpe, fieldName = value, underlying = domain.underlyingType)),
      options.dbLib.toList.flatMap(_.wrapperTypeInstances(wrapperType = domain.tpe, underlying = domain.underlyingType, overrideDbType = Some(domain.underlying.name.quotedValue)))
    ).flatten

    val str =
      code"""|$comments
             |case class ${domain.tpe.name}($value: ${domain.underlyingType})
             |${genObject(domain.tpe.value, instances)}""".stripMargin

    sc.File(domain.tpe, str, secondaryTypes = Nil, scope = Scope.Main)
  }
}

package typo
package internal
package codegen

object FileCustomType {
  def apply(options: InternalOptions, genOrdering: GenOrdering)(ct: CustomType): sc.File = {

    val comments = scaladoc(ct.comment)(Nil)

    val maybeBijection = ct.params match {
      case NonEmptyList(sc.Param(name, underlying, _), Nil) if options.enableDsl =>
        val bijection = {
          val thisBijection = sc.Type.dsl.Bijection.of(ct.typoType, underlying)
          sc.Given(Nil, sc.Ident("bijection"), Nil, thisBijection, code"$thisBijection(_.$name)(${ct.typoType}.apply)")
        }
        Some(bijection)
      case _ => None
    }

    val instances =
      maybeBijection.toList ++
        List(genOrdering.ordering(ct.typoType, ct.params)) ++
        options.jsonLibs.flatMap(_.customTypeInstances(ct)) ++
        options.dbLib.toList.flatMap(_.customTypeInstances(ct))

    val str =
      code"""$comments
            |case class ${ct.typoType.name}(${ct.params.map(_.code).mkCode(", ")})
            |
            |${sc.Obj(ct.typoType.value, instances, ct.objBody0)}
""".stripMargin

    sc.File(ct.typoType, str, secondaryTypes = Nil, scope = Scope.Main)
  }
}

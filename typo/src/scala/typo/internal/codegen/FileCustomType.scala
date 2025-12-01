package typo
package internal
package codegen

object FileCustomType {
  def apply(options: InternalOptions, lang: Lang)(ct: CustomType): jvm.File = {

    val maybeBijection = ct.params match {
      case NonEmptyList(jvm.Param(_, _, name, underlying, _), Nil) if options.enableDsl =>
        val bijection = {
          val thisBijection = jvm.Type.dsl.Bijection.of(ct.typoType, underlying)
          val expr = lang.bijection(ct.typoType, underlying, jvm.FieldGetterRef(ct.typoType, name), jvm.ConstructorMethodRef(ct.typoType))
          jvm.Given(Nil, jvm.Ident("bijection"), Nil, thisBijection, expr)
        }
        Some(bijection)
      case _ => None
    }

    val jsonInstances = options.jsonLibs.map(_.customTypeInstances(ct))
    val instances =
      maybeBijection.toList ++
        jsonInstances.flatMap(_.givens) ++
        options.dbLib.toList.flatMap(_.customTypeInstances(ct))
    val fieldAnnotations = JsonLib.mergeFieldAnnotations(jsonInstances.flatMap(_.fieldAnnotations.toList))
    val typeAnnotations = jsonInstances.flatMap(_.typeAnnotations)

    val paramsWithAnnotations = ct.params.toList.map { p =>
      fieldAnnotations.get(p.name) match {
        case Some(anns) => p.copy(annotations = p.annotations ++ anns)
        case None       => p
      }
    }

    val cls = jvm.Adt.Record(
      annotations = typeAnnotations,
      constructorAnnotations = Nil,
      isWrapper = false,
      comments = scaladoc(List(ct.comment)),
      name = ct.typoType,
      tparams = Nil,
      params = paramsWithAnnotations,
      implicitParams = Nil,
      `extends` = None,
      implements = Nil,
      members = Nil,
      staticMembers = instances ++ ct.objBody0
    )

    jvm.File(ct.typoType, cls, secondaryTypes = Nil, scope = Scope.Main)
  }
}

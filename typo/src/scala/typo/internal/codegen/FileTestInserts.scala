package typo
package internal
package codegen

object FileTestInserts {
  def apply(x: ComputedTestInserts, dbLib: DbLib, lang: Lang): List[jvm.File] = {
    val params = List(
      Some(jvm.Param(ComputedTestInserts.random, lang.Random.tpe)),
      x.maybeDomainMethods.map(x => jvm.Param(ComputedTestInserts.domainInsert, x.tpe))
    ).flatten

    val cls = jvm.Adt.Record(
      annotations = Nil,
      constructorAnnotations = Nil,
      isWrapper = false,
      comments = scaladoc(List(s"Methods to generate random data for `${x.tpe.name}`")),
      name = x.tpe,
      tparams = Nil,
      params = params,
      implicitParams = Nil,
      `extends` = None,
      implements = Nil,
      members = x.methods.map(x => dbLib.testInsertMethod(x)),
      staticMembers = Nil
    )

    val maybeDomainMethods =
      x.maybeDomainMethods.map { (x: ComputedTestInserts.GenerateDomainMethods) =>
        val methods = x.methods.map { (method: ComputedTestInserts.GenerateDomainMethod) =>
          val comments = scaladoc {
            val base = s"Domain `${method.dom.underlying.name.value}`"
            method.dom.underlying.constraintDefinition match {
              case Some(definition) => List(base, s"Constraint: $definition")
              case None             => List(base, "No constraint")
            }
          }
          jvm.Method(
            Nil,
            comments = comments,
            tparams = Nil,
            name = method.name,
            params = List(jvm.Param(ComputedTestInserts.random, lang.Random.tpe)),
            implicitParams = Nil,
            tpe = method.dom.tpe,
            throws = Nil,
            body = jvm.Body.Abstract,
            isOverride = false,
            isDefault = false
          )
        }

        val cls = jvm.Class(
          annotations = Nil,
          comments = scaladoc(List(s"Methods to generate random data for domain types")),
          classType = jvm.ClassType.Interface,
          name = x.tpe,
          tparams = Nil,
          params = Nil,
          implicitParams = Nil,
          `extends` = None,
          implements = Nil,
          members = methods.toList,
          staticMembers = Nil
        )
        jvm.File(x.tpe, cls, Nil, scope = Scope.Test)
      }

    List(Some(jvm.File(x.tpe, cls, Nil, scope = Scope.Test)), maybeDomainMethods).flatten
  }
}

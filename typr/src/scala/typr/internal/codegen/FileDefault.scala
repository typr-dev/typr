package typr
package internal
package codegen

case class FileDefault(default: ComputedDefault, jsonLibs: List[JsonLib], dbLib: Option[DbLib], lang: Lang) {
  private val jsonInstances = jsonLibs.map(_.defaultedInstance)
  val additionalFiles: List[jvm.File] = jsonInstances.flatMap(_.additionalFiles)

  val cls = {
    val typeAnnotations = jsonInstances.flatMap(_.typeAnnotations)
    val instances: List[jvm.Given] =
      jsonInstances.flatMap(_.givens) ++
        dbLib.toList.flatMap(_.defaultedInstance)

    val T = jvm.Type.Abstract(jvm.Ident("T"))
    val U = jvm.Type.Abstract(jvm.Ident("U"))
    val onDefaultName = jvm.Ident("onDefault")
    val onProvidedName = jvm.Ident("onProvided")
    val foldOnDefaultParam = jvm.Param(onDefaultName, jvm.Type.Function0(U))
    val foldOnProvidedParam = jvm.Param(onProvidedName, jvm.Type.Function1(T, U))
    val foldAbstract =
      jvm.Method(
        Nil,
        comments = jvm.Comments.Empty,
        name = jvm.Ident("fold"),
        tparams = List(U),
        params = List(
          foldOnDefaultParam,
          foldOnProvidedParam
        ),
        implicitParams = Nil,
        tpe = U,
        throws = Nil,
        body = jvm.Body.Abstract,
        isOverride = false,
        isDefault = false
      )

    val visitOnDefaultParam = jvm.Param(onDefaultName, jvm.Type.Function0(jvm.Type.Void))
    val visitOnProvidedParam = jvm.Param(onProvidedName, jvm.Type.Function1(T, jvm.Type.Void))
    val visitAbstract =
      jvm.Method(
        Nil,
        comments = jvm.Comments.Empty,
        name = jvm.Ident("visit"),
        tparams = Nil,
        params = List(visitOnDefaultParam, visitOnProvidedParam),
        implicitParams = Nil,
        tpe = jvm.Type.Void,
        throws = Nil,
        body = jvm.Body.Abstract,
        isOverride = false,
        isDefault = false
      )

    val getOrElseParam = jvm.Param(onDefaultName, jvm.Type.Function0(T))
    val getOrElseAbstract =
      jvm.Method(
        Nil,
        comments = jvm.Comments.Empty,
        name = jvm.Ident("getOrElse"),
        tparams = Nil,
        params = List(getOrElseParam),
        implicitParams = Nil,
        tpe = T,
        throws = Nil,
        body = jvm.Body.Abstract,
        isOverride = false,
        isDefault = false
      )
    val valueParam = jvm.Param(jvm.Ident("value"), T)

    jvm.Adt.Sum(
      annotations = typeAnnotations,
      comments = jvm.Comments(List("This signals a value where if you don't provide it, postgres will generate it for you")),
      name = default.Defaulted,
      tparams = List(T),
      members = List(foldAbstract, getOrElseAbstract, visitAbstract),
      implements = Nil,
      subtypes = List(
        jvm.Adt.Record(
          annotations = Nil,
          constructorAnnotations = Nil,
          isWrapper = false,
          comments = jvm.Comments.Empty,
          name = jvm.Type.Qualified(default.Provided),
          tparams = List(T),
          params = List(valueParam),
          implicitParams = Nil,
          `extends` = None,
          implements = List(default.Defaulted.of(T)),
          members = List(
            foldAbstract.copy(body = jvm.Body.Expr(jvm.Apply1(foldOnProvidedParam, valueParam.name.code)), isOverride = true),
            getOrElseAbstract.copy(body = jvm.Body.Expr(valueParam.name.code), isOverride = true),
            visitAbstract.copy(body = jvm.Body.Expr(jvm.Apply1(visitOnProvidedParam, valueParam.name.code)), isOverride = true)
          ),
          staticMembers = Nil
        ),
        jvm.Adt.Record(
          annotations = Nil,
          constructorAnnotations = Nil,
          isWrapper = false,
          comments = jvm.Comments.Empty,
          name = jvm.Type.Qualified(default.UseDefault),
          tparams = List(T),
          params = Nil,
          implicitParams = Nil,
          `extends` = None,
          implements = List(default.Defaulted.of(T)),
          members = List(
            foldAbstract.copy(body = jvm.Body.Expr(jvm.Apply0(foldOnDefaultParam)), isOverride = true),
            getOrElseAbstract.copy(body = jvm.Body.Expr(jvm.Apply0(getOrElseParam)), isOverride = true),
            visitAbstract.copy(body = jvm.Body.Expr(jvm.Apply0(visitOnDefaultParam)), isOverride = true)
          ),
          staticMembers = Nil
        )
      ),
      staticMembers = instances
    )
  }

  val file = jvm.File(default.Defaulted, cls, secondaryTypes = Nil, scope = Scope.Main)
}

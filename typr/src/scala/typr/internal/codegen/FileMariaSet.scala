package typr
package internal
package codegen

object FileMariaSet {
  val MariaTypes: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.MariaTypes")
  val MariaType: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.MariaType")
  val SetInterface: jvm.Type.Qualified = jvm.Type.Qualified("java.util.Set")

  def apply(options: InternalOptions, setType: ComputedMariaSet, adapter: DbAdapter): List[jvm.File] = {
    val lang = options.lang

    val memberEnumType = jvm.Type.Qualified(setType.tpe.value.parentOpt.get / jvm.Ident(setType.tpe.name.value + "Member"))

    val memberEnumFile = generateMemberEnum(setType, memberEnumType, lang)
    val wrapperFile = generateWrapper(options, setType, memberEnumType, adapter)

    List(memberEnumFile, wrapperFile)
  }

  private def generateMemberEnum(setType: ComputedMariaSet, memberEnumType: jvm.Type.Qualified, lang: Lang): jvm.File = {
    val memberExpressions = setType.members.map { case (name, value) =>
      (name, jvm.StrLit(value).code)
    }

    val comments = scaladoc(s"Members of MariaDB SET type with values: ${setType.sortedValues.mkString(", ")}" :: Nil)

    val enumDef = jvm.Enum(
      Nil,
      comments,
      memberEnumType,
      memberExpressions,
      Nil
    )

    jvm.File(memberEnumType, enumDef, secondaryTypes = Nil, scope = Scope.Main)
  }

  private def generateWrapper(options: InternalOptions, setType: ComputedMariaSet, memberEnumType: jvm.Type.Qualified, adapter: DbAdapter): jvm.File = {
    val lang = options.lang

    val dbTypeInstances: List[jvm.ClassMember] = options.dbLib.toList.flatMap { _ =>
      mariaTypeInstance(setType, memberEnumType, lang)
    }

    val comments = scaladoc(s"MariaDB SET type with values: ${setType.sortedValues.mkString(", ")}" :: Nil)

    val jsonInstances: List[jvm.ClassMember] = options.jsonLibs.flatMap { jsonLib =>
      jsonLib.stringEnumInstances(setType.tpe, lang.String, openEnum = false).givens
    }

    val instances = dbTypeInstances ++ jsonInstances

    val membersType = lang match {
      case _: LangScala  => jvm.Type.Qualified("scala.collection.immutable.Set").of(memberEnumType)
      case _: LangKotlin => jvm.Type.Qualified("kotlin.collections.Set").of(memberEnumType)
      case _             => SetInterface.of(memberEnumType)
    }

    val wrapper = jvm.Adt.Record(
      annotations = Nil,
      constructorAnnotations = Nil,
      isWrapper = false,
      privateConstructor = false,
      comments = comments,
      name = setType.tpe,
      tparams = Nil,
      params = List(jvm.Param(jvm.Ident("members"), membersType)),
      implicitParams = Nil,
      `extends` = None,
      implements = Nil,
      members = instanceMethods(setType, memberEnumType, lang),
      staticMembers = factoryMethods(setType, memberEnumType, lang) ++ instances
    )

    jvm.File(setType.tpe, wrapper, secondaryTypes = Nil, scope = Scope.Main)
  }

  private def mariaTypeInstance(setType: ComputedMariaSet, memberEnumType: jvm.Type, lang: Lang): List[jvm.ClassMember] = {
    val MariaSetType = jvm.Type.Qualified("dev.typr.foundations.data.maria.MariaSet")

    val msParam = jvm.LambdaParam.typed("ms", MariaSetType)
    val sParam = jvm.LambdaParam.typed("s", setType.tpe)

    val msToCommaSeparated = jvm.ApplyNullary(code"ms", jvm.Ident("toCommaSeparated"))
    val sToCommaSeparated = jvm.ApplyNullary(code"s", jvm.Ident("toCommaSeparated"))

    val fromMariaSet = jvm.Lambda(
      List(msParam),
      jvm.Body.Expr(code"${setType.tpe}.fromString($msToCommaSeparated)")
    )

    val toMariaSet = jvm.Lambda(
      List(sParam),
      jvm.Body.Expr(code"$MariaSetType.fromString($sToCommaSeparated)")
    )

    val body = code"$MariaTypes.set.bimap($fromMariaSet, $toMariaSet)"

    List(
      jvm.Given(
        tparams = Nil,
        name = jvm.Ident("dbType"),
        implicitParams = Nil,
        tpe = MariaType.of(setType.tpe),
        body = body
      )
    )
  }

  private def factoryMethods(setType: ComputedMariaSet, memberEnumType: jvm.Type, lang: Lang): List[jvm.ClassMember] = {
    val EnumSetType = jvm.Type.Qualified("java.util.EnumSet")
    val ScalaSet = jvm.Type.Qualified("scala.collection.immutable.Set")

    val fromStringBody: jvm.Body = lang match {
      case _: LangScala =>
        jvm.Body.Expr(
          code"""|{
                 |  if (str == null || str.isEmpty) ${setType.tpe}($ScalaSet.empty)
                 |  else ${setType.tpe}(str.split(",").flatMap(v => $memberEnumType.ByName.get(v.trim)).toSet)
                 |}""".stripMargin
        )
      case _: LangKotlin =>
        jvm.Body.Expr(
          code"""|run {
                 |  if (str.isNullOrEmpty()) ${setType.tpe}($EnumSetType.noneOf($memberEnumType::class.java).toSet())
                 |  else {
                 |    val set = $EnumSetType.noneOf($memberEnumType::class.java)
                 |    str.split(",").forEach { v ->
                 |      $memberEnumType.ByName[v.trim()]?.let { set.add(it) }
                 |    }
                 |    ${setType.tpe}(set.toSet())
                 |  }
                 |}""".stripMargin
        )
      case _ =>
        jvm.Body.Stmts(
          List(
            jvm.Stmt.compound(code"if (str == null || str.isEmpty()) { return new ${setType.tpe}($EnumSetType.noneOf($memberEnumType.class)); }").code,
            code"var set = $EnumSetType.noneOf($memberEnumType.class)",
            jvm.Stmt.compound(code"for (String v : str.split(\",\")) { var member = $memberEnumType.ByName.get(v.trim()); if (member != null) { set.add(member); } }").code,
            jvm.Return(code"new ${setType.tpe}(set)").code
          )
        )
    }

    val ofBody: jvm.Body = lang match {
      case _: LangScala =>
        jvm.Body.Expr(code"${setType.tpe}(members.toSet)")
      case _: LangKotlin =>
        jvm.Body.Expr(
          code"""|run {
                 |  if (members.isEmpty()) ${setType.tpe}($EnumSetType.noneOf($memberEnumType::class.java).toSet())
                 |  else ${setType.tpe}($EnumSetType.copyOf(members).toSet())
                 |}""".stripMargin
        )
      case _ =>
        jvm.Body.Stmts(
          List(
            jvm.Stmt.compound(code"if (members.isEmpty()) { return new ${setType.tpe}($EnumSetType.noneOf($memberEnumType.class)); }").code,
            jvm.Return(code"new ${setType.tpe}($EnumSetType.copyOf(members))").code
          )
        )
    }

    val emptyBody: jvm.Body = lang match {
      case _: LangScala =>
        jvm.Body.Expr(code"${setType.tpe}($ScalaSet.empty)")
      case _: LangKotlin =>
        jvm.Body.Expr(code"${setType.tpe}($EnumSetType.noneOf($memberEnumType::class.java).toSet())")
      case _ =>
        jvm.Body.Expr(code"new ${setType.tpe}($EnumSetType.noneOf($memberEnumType.class))")
    }

    List(
      jvm.Method(
        annotations = Nil,
        comments = jvm.Comments.Empty,
        tparams = Nil,
        name = jvm.Ident("fromString"),
        params = List(jvm.Param(jvm.Ident("str"), lang.String)),
        implicitParams = Nil,
        tpe = setType.tpe,
        throws = Nil,
        body = fromStringBody,
        isOverride = false,
        isDefault = false
      ),
      jvm.Method(
        annotations = Nil,
        comments = jvm.Comments.Empty,
        tparams = Nil,
        name = jvm.Ident("of"),
        params = List(jvm.Param(jvm.Ident("members"), lang.ListType.tpe.of(memberEnumType))),
        implicitParams = Nil,
        tpe = setType.tpe,
        throws = Nil,
        body = ofBody,
        isOverride = false,
        isDefault = false
      ),
      jvm.Method(
        annotations = Nil,
        comments = jvm.Comments.Empty,
        tparams = Nil,
        name = jvm.Ident("empty"),
        params = Nil,
        implicitParams = Nil,
        tpe = setType.tpe,
        throws = Nil,
        body = emptyBody,
        isOverride = false,
        isDefault = false
      )
    )
  }

  private def instanceMethods(setType: ComputedMariaSet, memberEnumType: jvm.Type, lang: Lang): List[jvm.ClassMember] = {
    val containsBody: jvm.Body = jvm.Body.Expr(code"members.contains(member)")

    val isEmptyBody: jvm.Body = lang match {
      case _: LangScala  => jvm.Body.Expr(code"members.isEmpty")
      case _: LangKotlin => jvm.Body.Expr(code"members.isEmpty()")
      case _             => jvm.Body.Expr(code"members.isEmpty()")
    }

    val sizeBody: jvm.Body = lang match {
      case _: LangScala  => jvm.Body.Expr(code"members.size")
      case _: LangKotlin => jvm.Body.Expr(code"members.size")
      case _             => jvm.Body.Expr(code"members.size()")
    }

    val StringJoiner = jvm.Type.Qualified("java.util.StringJoiner")
    val toCommaSeparatedBody: jvm.Body = lang match {
      case _: LangScala =>
        jvm.Body.Expr(code"members.map(_.value).mkString(\",\")")
      case _: LangKotlin =>
        jvm.Body.Expr(code"members.joinToString(\",\") { it.value }")
      case _ =>
        jvm.Body.Stmts(
          List(
            code"var joiner = new $StringJoiner(\",\")",
            jvm.Stmt.compound(code"for (var m : members) { joiner.add(m.value); }").code,
            jvm.Return(code"joiner.toString()").code
          )
        )
    }

    val toStringBody: jvm.Body = jvm.Body.Expr(jvm.SelfNullary(jvm.Ident("toCommaSeparated")).code)

    List(
      jvm.Method(
        annotations = Nil,
        comments = jvm.Comments.Empty,
        tparams = Nil,
        name = jvm.Ident("contains"),
        params = List(jvm.Param(jvm.Ident("member"), memberEnumType)),
        implicitParams = Nil,
        tpe = lang.Boolean,
        throws = Nil,
        body = containsBody,
        isOverride = false,
        isDefault = false
      ),
      jvm.Method(
        annotations = Nil,
        comments = jvm.Comments.Empty,
        tparams = Nil,
        name = jvm.Ident("isEmpty"),
        params = Nil,
        implicitParams = Nil,
        tpe = lang.Boolean,
        throws = Nil,
        body = isEmptyBody,
        isOverride = false,
        isDefault = false
      ),
      jvm.Method(
        annotations = Nil,
        comments = jvm.Comments.Empty,
        tparams = Nil,
        name = jvm.Ident("size"),
        params = Nil,
        implicitParams = Nil,
        tpe = lang.Int,
        throws = Nil,
        body = sizeBody,
        isOverride = false,
        isDefault = false
      ),
      jvm.Method(
        annotations = Nil,
        comments = jvm.Comments.Empty,
        tparams = Nil,
        name = jvm.Ident("toCommaSeparated"),
        params = Nil,
        implicitParams = Nil,
        tpe = lang.String,
        throws = Nil,
        body = toCommaSeparatedBody,
        isOverride = false,
        isDefault = false
      ),
      jvm.Method(
        annotations = Nil,
        comments = jvm.Comments.Empty,
        tparams = Nil,
        name = jvm.Ident("toString"),
        params = Nil,
        implicitParams = Nil,
        tpe = lang.String,
        throws = Nil,
        body = toStringBody,
        isOverride = true,
        isDefault = false
      )
    )
  }
}

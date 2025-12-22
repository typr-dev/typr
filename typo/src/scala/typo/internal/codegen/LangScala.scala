package typo
package internal
package codegen
import typo.jvm.Code.TreeOps
import typo.jvm.Type

case class LangScala(dialect: Dialect, typeSupport: TypeSupport, dsl: DslQualifiedNames) extends Lang {
  override val `;` : jvm.Code = code""

  // Type system types - Scala always uses Scala's type system
  override val nothingType: jvm.Type = TypesScala.Nothing
  override val voidType: jvm.Type = TypesScala.Unit
  override val topType: jvm.Type = TypesScala.Any

  override def s(content: jvm.Code): jvm.Code =
    jvm.StringInterpolate(TypesScala.StringContext, jvm.Ident("s"), content)

  override def docLink(cls: jvm.QIdent, value: jvm.Ident): String =
    s"Points to [[${cls.dotName}.${value.value}]]"

  // don't generate imports for these
  override val BuiltIn: Map[jvm.Ident, jvm.Type.Qualified] =
    Set(
      TypesJava.Character,
      TypesJava.Integer,
      TypesJava.Runnable,
      TypesJava.String,
      TypesJava.StringBuilder,
      TypesJava.Throwable,
      TypesScala.Any,
      TypesScala.AnyRef,
      TypesScala.AnyVal,
      TypesScala.Array,
      TypesScala.BigDecimal,
      TypesScala.Boolean,
      TypesScala.Byte,
      TypesScala.Double,
      TypesScala.Either,
      TypesScala.Float,
      TypesScala.Function1,
      TypesScala.Int,
      TypesScala.Iterable,
      TypesScala.Iterator,
      TypesScala.Left,
      TypesScala.List,
      TypesScala.Long,
      TypesScala.Map,
      TypesScala.Nil,
      TypesScala.None,
      TypesScala.Numeric,
      TypesScala.Option,
      TypesScala.Right,
      TypesScala.Short,
      TypesScala.Some,
      TypesScala.StringContext,
      TypesScala.Unit
    )
      .map(x => (x.value.name, x))
      .toMap

  override def extension: String = "scala"

  val Quote = '"'.toString
  val TripleQuote = Quote * 3
  val breakAfter = 3

  // Scala doesn't need visibility context - everything is public by default
  case class RenderCtx()
  override type Ctx = RenderCtx
  override object Ctx extends CtxCompanion {
    val Empty: RenderCtx = RenderCtx()
  }

  override def renderTree(tree: jvm.Tree, ctx: Ctx): jvm.Code =
    tree match {
      case jvm.IfExpr(cond, thenp, elsep) => code"(if ($cond) $thenp else $elsep)"
      case jvm.If(branches, elseBody) =>
        val ifParts = branches.zipWithIndex.map { case (jvm.If.Branch(cond, body), idx) =>
          val keyword = if (idx == 0) "if" else "else if"
          code"""|$keyword ($cond) {
                 |  $body
                 |}""".stripMargin
        }
        val elsePart = elseBody
          .map(e => code"""| else {
                                                |  $e
                                                |}""".stripMargin)
          .getOrElse(jvm.Code.Empty)
        ifParts.mkCode("") ++ elsePart
      case jvm.While(cond, body) =>
        val bodyCode = body.mkCode("\n")
        code"""|while ($cond) {
               |  $bodyCode
               |}""".stripMargin
      case jvm.IgnoreResult(expr)        => code"$expr: @${TypesScala.nowarn}"
      case jvm.NotNull(expr)             => expr // Scala doesn't need not-null assertions
      case jvm.ConstructorMethodRef(tpe) => code"$tpe.apply"
      case jvm.ClassOf(tpe)              => code"classOf[$tpe]"
      case jvm.JavaClassOf(tpe)          => code"classOf[$tpe]" // Same as ClassOf for Scala
      case jvm.Call(target, argGroups) =>
        val renderedGroups = argGroups.map { group =>
          val argsStr = group.args.map(_.value).mkCode(", ")
          if (group.isImplicit) code"(using $argsStr)" else code"($argsStr)"
        }
        code"$target${renderedGroups.mkCode("")}"
      case jvm.Apply0(jvm.Ref(name, jvm.Type.Function0(_)))                   => name.code
      case jvm.Apply1(jvm.Ref(name, jvm.Type.Function1(_, _)), arg1)          => code"$name($arg1)"
      case jvm.Apply2(jvm.Ref(name, jvm.Type.Function2(_, _, _)), arg1, arg2) => code"$name($arg1, $arg2)"
      case jvm.Select(target, name)                                           => code"$target.$name"
      case jvm.ArrayIndex(target, num)                                        => code"$target($num)"
      case jvm.ApplyNullary(target, name)                                     => code"$target.$name"
      case jvm.Arg.Named(name, value)                                         => code"$name = $value"
      case jvm.Arg.Pos(value)                                                 => value
      case jvm.Ident(value)                                                   => escapedIdent(value)
      case jvm.MethodRef(tpe, name)                                           => code"$tpe.$name"
      case jvm.QIdent(value)                                                  => value.map(t => renderTree(t, ctx)).mkCode(".")
      case jvm.StrLit(str) if str.contains(Quote) || str.contains('\n')       =>
        // Use triple-quotes for strings with quotes or newlines - no need to escape in triple-quotes
        TripleQuote + str + TripleQuote
      case jvm.StrLit(str) =>
        // Use regular quotes and escape special characters
        val escaped = str
          .replace("\\", "\\\\")
          .replace("\"", "\\\"")
          .replace("\n", "\\n")
          .replace("\r", "\\r")
          .replace("\t", "\\t")
        Quote + escaped + Quote
      case jvm.Summon(tpe) => code"implicitly[$tpe]"
      case jvm.Type.Abstract(value, variance) =>
        variance match {
          case jvm.Variance.Invariant     => value.code
          case jvm.Variance.Covariant     => code"+$value"
          case jvm.Variance.Contravariant => code"-$value"
        }
      case jvm.Type.ArrayOf(value)                    => code"Array[$value]"
      case jvm.Type.KotlinNullable(underlying)        => renderTree(underlying, ctx) // Scala doesn't have Kotlin's T? syntax, render underlying type
      case jvm.Type.Commented(underlying, comment)    => code"$comment $underlying"
      case jvm.Type.Annotated(underlying, annotation) => code"$underlying @$annotation"
      case jvm.Type.Function0(ret)                    => code"=> $ret"
      case jvm.Type.Function1(t1, ret)                => code"$t1 => $ret"
      case jvm.Type.Function2(t1, t2, ret)            => code"($t1, $t2) => $ret"
      case jvm.Type.Qualified(value)                  => value.code
      case jvm.Type.TApply(underlying, targs)         => code"$underlying[${targs.map(t => renderTree(t, ctx)).mkCode(", ")}]"
      case jvm.Type.UserDefined(underlying)           => code"/* user-picked */ $underlying"
      case jvm.Type.Void                              => code"Unit"
      case jvm.Type.Wildcard                          => code"?"
      case jvm.Type.Primitive(name)                   => javaPrimitiveToScala(name)
      case p: jvm.Param[jvm.Type]                     => renderParam(p, false)
      case jvm.RuntimeInterpolation(value)            => code"$${$value"
      case jvm.Import(_, _)                           => jvm.Code.Empty // Import node just triggers import, no code output
      case jvm.TypeSwitch(value, cases, nullCase, _, unchecked) =>
        val nullCaseCode = nullCase.map(body => code"case null => $body").toList
        val typeCases = cases.map { case jvm.TypeSwitch.Case(pat, ident, body) => code"case $ident: $pat => $body" }
        val allCases = nullCaseCode ++ typeCases
        // If unchecked is true, add @unchecked annotation to suppress type erasure warnings
        val scrutinee = if (unchecked) code"($value: @unchecked)" else value
        code"""|$scrutinee match {
               |  ${allCases.mkCode("\n")}
               |}""".stripMargin
      case jvm.TryCatch(tryBlock, catches, finallyBlock) =>
        val tryCode = code"""|try {
                             |  ${tryBlock.mkCode("\n")}
                             |}""".stripMargin
        val catchCodes = catches.map { case jvm.TryCatch.Catch(exType, ident, body) =>
          code"case $ident: $exType => ${body.mkCode("\n")}"
        }
        val catchCode =
          if (catches.isEmpty) jvm.Code.Str("")
          else
            code"""|catch {
                 |  ${catchCodes.mkCode("\n")}
                 |}""".stripMargin
        val finallyCode =
          if (finallyBlock.isEmpty) jvm.Code.Str("")
          else
            code"""|finally {
                 |  ${finallyBlock.mkCode("\n")}
                 |}""".stripMargin
        code"$tryCode $catchCode $finallyCode"
      case jvm.IfElseChain(cases, elseCase) =>
        val ifCases = cases.zipWithIndex.map { case ((cond, body), idx) =>
          if (idx == 0) code"if ($cond) $body"
          else code"else if ($cond) $body"
        }
        val elseCode = code"else $elseCase"
        (ifCases :+ elseCode).mkCode("\n")
      case jvm.Stmt(inner, _) => inner // Scala doesn't need semicolons, just render inner code
      case jvm.New(target, args) =>
        if (args.length > breakAfter)
          code"""|new $target(
                 |  ${args.map(a => renderTree(a, ctx)).mkCode(",\n")}
                 |)""".stripMargin
        else code"new $target(${args.map(a => renderTree(a, ctx)).mkCode(", ")})"
      case jvm.LocalVar(name, tpe, value)                            => tpe.fold(code"val $name = $value")(t => code"val $name: $t = $value")
      case jvm.InferredTargs(target)                                 => target // Scala doesn't need diamond operator
      case jvm.GenericMethodCall(target, methodName, typeArgs, args) =>
        // Scala: target.method[T1, T2](args)
        val typeArgStr = if (typeArgs.isEmpty) jvm.Code.Empty else code"[${typeArgs.map(t => renderTree(t, ctx)).mkCode(", ")}]"
        val argStr = if (args.isEmpty) code"()" else code"(${args.map(a => renderTree(a, ctx)).mkCode(", ")})"
        code"$target.$methodName$typeArgStr$argStr"
      case jvm.Return(expr) => code"return $expr"
      case jvm.Throw(expr)  => code"throw $expr"
      case jvm.Lambda(params, body) =>
        val paramsCode = params match {
          case Nil                                    => code"() => "
          case List(jvm.LambdaParam(name, None))      => code"$name => "
          case List(jvm.LambdaParam(name, Some(tpe))) => code"($name: $tpe) => "
          case _ if params.forall(_.tpe.isEmpty)      => code"(${params.map(p => p.name.code).mkCode(", ")}) => "
          case _                                      => code"(${params.map(p => p.tpe.fold(code"${p.name}")(t => code"${p.name}: $t")).mkCode(", ")}) => "
        }
        code"$paramsCode${renderBody(body)}"
      case jvm.SamLambda(_, lambda) =>
        // Scala: SAM conversion is automatic, just render the lambda
        renderTree(lambda, ctx)
      case jvm.Cast(targetType, expr) =>
        // Scala cast: expr.asInstanceOf[Type]
        code"$expr.asInstanceOf[$targetType]"
      case jvm.ByName(body)                          => renderBody(body) // Scala: by-name is just the body expression
      case jvm.FieldGetterRef(_, field)              => code"_.$field" // Scala uses underscore syntax
      case jvm.SelfNullary(name)                     => name.code // Scala: just the identifier (nullary methods don't need parens)
      case jvm.TypedFactoryCall(tpe, typeArgs, args) =>
        // Scala: Type[T1, T2](args)
        val typeArgStr = if (typeArgs.isEmpty) jvm.Code.Empty else code"[${typeArgs.map(t => renderTree(t, ctx)).mkCode(", ")}]"
        code"$tpe$typeArgStr(${args.map(a => renderTree(a, ctx)).mkCode(", ")})"

      case jvm.StringInterpolate(_, prefix, content) =>
        // Use linearize to properly extract RuntimeInterpolation nodes
        val linearized = jvm.Code.linearize(content)
        // Build the interpolated string: s"literal${value}literal${value}..."
        val stringContent = linearized.map {
          case jvm.Code.Tree(jvm.RuntimeInterpolation(value)) =>
            // Wrap runtime values in ${...}
            s"$${${value.render(this).asString}}"
          case jvm.Code.Str(s) =>
            // String literals stay as-is
            s
          case other =>
            // Render other code nodes and extract string
            other.render(this).asString
        }.mkString
        if (stringContent.contains(Quote) || stringContent.contains("\n")) {
          code"$prefix$TripleQuote$stringContent$TripleQuote"
        } else {
          code"$prefix$Quote$stringContent$Quote"
        }
      case jvm.Given(annotations, tparams, name, implicitParams, tpe, body) =>
        val annotationsCode = renderAnnotations(annotations)
        if (tparams.isEmpty && implicitParams.isEmpty)
          withBody(code"$annotationsCode${dialect.valDefinition} $name: $tpe", List(body))
        else {
          withBody(code"$annotationsCode${dialect.defDefinition} $name${renderTparams(tparams, ctx)}${renderImplicitParams(implicitParams, ctx)}: $tpe", List(body))
        }
      case jvm.Value(annotations, name, tpe, body, isLazy, isOverride, isImplicit) =>
        val annotationsCode = renderAnnotations(annotations)
        val overrideMod = if (isOverride) "override " else ""
        val lazyMod = if (isLazy) "lazy " else ""
        val implicitMod = if (isImplicit) "implicit " else ""
        withBody(code"$annotationsCode${overrideMod}${implicitMod}${lazyMod}val $name: $tpe", body.toList)
      case jvm.Method(annotations, comments, tparams, name, params, implicitParams, tpe, throws, body, isOverride, _) =>
        val annotationsCode = renderAnnotations(annotations)
        val throwsCode = throws.map(th => code"@throws[$th]\n").mkCode("")
        val commentCode = renderComments(comments).getOrElse(jvm.Code.Empty)
        val overrideMod = if (isOverride) "override " else ""

        val signature = renderWithParams(
          prefix = code"$commentCode$annotationsCode$throwsCode${overrideMod}def $name${renderTparams(tparams, ctx)}",
          params = params,
          implicitParams = implicitParams,
          isVal = false,
          forceParenthesis = false,
          ctx = ctx
        ) ++ code": $tpe"

        body match {
          case jvm.Body.Abstract => signature
          case jvm.Body.Expr(expr) =>
            val rendered = expr.render(this)
            if (rendered.lines.length == 1)
              code"$signature = ${rendered.asString}"
            else {
              val indentedBody = rendered.asString.linesIterator.map("  " + _).mkString("\n")
              jvm.Code.Combined(List(signature, code" = {\n", jvm.Code.Str(indentedBody), code"\n}"))
            }
          case jvm.Body.Stmts(stmts) =>
            val renderedBody = stmts.mkCode("\n").render(this)
            val indentedBody = renderedBody.asString.linesIterator.map("  " + _).mkString("\n")
            jvm.Code.Combined(List(signature, code" = {\n", jvm.Code.Str(indentedBody), code"\n}"))
        }
      case enm: jvm.Enum =>
        val members = enm.values.map { case (name, expr) => name -> code"case object $name extends ${enm.tpe.name}($expr)" }
        val str = jvm.Ident("str")
        val annotationsCode = renderAnnotations(enm.annotations)
        code"""|$annotationsCode${renderComments(enm.comments).getOrElse(jvm.Code.Empty)}
               |sealed abstract class ${enm.tpe.name}(val value: ${TypesJava.String})
               |
               |object ${enm.tpe.name} {
               |  ${enm.staticMembers.map(_.code).mkCode("\n\n")}
               |  def apply($str: ${TypesJava.String}): ${TypesScala.Either.of(TypesJava.String, enm.tpe)} =
               |    ByName.get($str).toRight(s"'$$str' does not match any of the following legal values: $$Names")
               |  def force($str: ${TypesJava.String}): ${enm.tpe} =
               |    apply($str) match {
               |      case ${TypesScala.Left}(msg) => sys.error(msg)
               |      case ${TypesScala.Right}(value) => value
               |    }
               |  ${members.map { case (_, definition) => definition }.mkCode("\n\n")}
               |  val All: ${TypesScala.List.of(enm.tpe)} = ${TypesScala.List}(${members.map { case (ident, _) => ident.code }.mkCode(", ")})
               |  val Names: ${TypesJava.String} = All.map(_.value).mkString(", ")
               |  val ByName: ${TypesScala.Map.of(TypesJava.String, enm.tpe)} = All.map(x => (x.value, x)).toMap
               |}""".stripMargin

      case enm: jvm.OpenEnum =>
        val members = enm.values.map { case (name, expr) => (name, code"case object $name extends ${enm.tpe.name}($expr)") }

        val underlying = jvm.Ident("underlying")
        val annotationsCode = renderAnnotations(enm.annotations)
        code"""$annotationsCode${renderComments(enm.comments).getOrElse(jvm.Code.Empty)}
              |sealed abstract class ${enm.tpe.name}(val value: ${enm.underlyingType})
              |
              |object ${enm.tpe.name} {
              |  def apply($underlying: ${enm.underlyingType}): ${enm.tpe} =
              |    ByName.getOrElse($underlying, Unknown($underlying))
              |  ${(enm.staticMembers.map(_.code) ++ members.toList.map { case (_, definition) => definition }).mkCode("\n\n")}
              |  case class Unknown(override val value: ${enm.underlyingType}) extends ${enm.tpe}(value)
              |  val All: ${TypesScala.List.of(enm.tpe)} = ${TypesScala.List}(${members.map { case (ident, _) => ident.code }.mkCode(", ")})
              |  val ByName: ${TypesScala.Map.of(enm.underlyingType, enm.tpe)} = All.map(x => (x.value, x)).toMap
              |
              |}""".stripMargin

      case sum: jvm.Adt.Sum =>
        val annotationsCode = if (sum.annotations.isEmpty) None else Some(renderAnnotations(sum.annotations))
        // Only use 'sealed' if subtypes are defined in the same file (non-empty subtypes list)
        // If subtypes is empty, the trait might be extended from other files
        val traitKeyword = if (sum.subtypes.nonEmpty) code"sealed trait " else code"trait "
        List[Option[jvm.Code]](
          annotationsCode,
          renderComments(sum.comments),
          Some(traitKeyword),
          Some(sum.name.name.value),
          sum.tparams match {
            case Nil      => None
            case nonEmpty => Some(renderTparams(nonEmpty, ctx))
          },
          sum.implements.headOption.map(extends_ => code" extends $extends_"),
          sum.implements.drop(1) match {
            case Nil      => None
            case nonEmpty => Some(nonEmpty.map(x => code" with $x").mkCode(" "))
          },
          sum.members match {
            case Nil => None
            case nonEmpty =>
              Some(
                code"""| {
                       |  ${nonEmpty.map(m => renderTree(m, ctx)).mkCode("\n\n")}
                       |}""".stripMargin
              )
          },
          sum.staticMembers.sortBy(_.name).map(m => renderTree(m, ctx)) ++ sum.flattenedSubtypes.sortBy(_.name.name).map(t => renderTree(t, ctx)) match {
            case Nil => None
            case nonEmpty =>
              Some(code"""|
                          |
                          |object ${sum.name.name.value} {
                          |  ${nonEmpty.mkCode("\n\n")}
                          |}""".stripMargin)
          }
        ).flatten.mkCode("")
      case cls: jvm.Adt.Record =>
        val (extends_, implements) = (cls.isWrapper, cls.`extends`.toList ++ cls.implements) match {
          case (true, types) => (Some(TypesScala.AnyVal), types)
          case (_, types)    => (types.headOption, types.drop(1))
        }
        // Combine class annotations and constructor annotations (for Scala, put them before case class)
        val allAnnotations = cls.annotations ++ cls.constructorAnnotations
        val annotationsCode = if (allAnnotations.isEmpty) None else Some(renderAnnotations(allAnnotations))
        val prefix = List[Option[jvm.Code]](
          annotationsCode,
          renderComments(cls.comments),
          Some(code"case class "),
          Some(cls.name.name.value),
          cls.tparams match {
            case Nil      => None
            case nonEmpty => Some(renderTparams(nonEmpty, ctx))
          }
        ).flatten.mkCode("")

        List[Option[jvm.Code]](
          Some(
            renderWithParams(
              prefix = prefix,
              params = cls.params,
              implicitParams = cls.implicitParams,
              isVal = false,
              forceParenthesis = true,
              ctx = ctx
            )
          ),
          extends_.map(x => code" extends $x"),
          implements match {
            case Nil      => None
            case nonEmpty => Some(nonEmpty.map(x => code" with $x").mkCode(" "))
          },
          cls.members match {
            case Nil => None
            case nonEmpty =>
              Some(
                code"""| {
                       |  ${nonEmpty.map(m => renderTree(m, ctx)).mkCode("\n\n")}
                       |}""".stripMargin
              )
          },
          cls.staticMembers
            .sortBy(_.name)
            .map(m => renderTree(m, ctx)) match {
            case Nil => None
            case nonEmpty =>
              Some(code"""|
                          |
                          |object ${cls.name.name.value} {
                          |  ${nonEmpty.mkCode("\n\n")}
                          |}""".stripMargin)
          }
        ).flatten.mkCode("")
      case cls: jvm.Class =>
        val (extends_, implements) = {
          val types = cls.`extends`.toList ++ cls.implements
          (types.headOption, types.drop(1))
        }

        val annotationsCode = if (cls.annotations.isEmpty) None else Some(renderAnnotations(cls.annotations))
        val prefix = List[Option[jvm.Code]](
          annotationsCode,
          renderComments(cls.comments),
          cls.classType match {
            case jvm.ClassType.Class         => Some(code"class ")
            case jvm.ClassType.AbstractClass => Some(code"abstract class ")
            case jvm.ClassType.Interface     => Some(code"trait ")
          },
          Some(cls.name.name.value),
          cls.tparams match {
            case Nil      => None
            case nonEmpty => Some(renderTparams(nonEmpty, ctx))
          }
        ).flatten.mkCode("")

        List[Option[jvm.Code]](
          Some(
            renderWithParams(
              prefix = prefix,
              params = cls.params,
              implicitParams = cls.implicitParams,
              isVal = true,
              forceParenthesis = false,
              ctx = ctx
            )
          ),
          extends_.map(x => code" extends $x"),
          implements match {
            case Nil      => None
            case nonEmpty => Some(nonEmpty.map(x => code" with $x").mkCode(" "))
          },
          cls.members match {
            case Nil => None
            case nonEmpty =>
              Some(
                code"""| {
                       |  ${nonEmpty.map(m => renderTree(m, ctx)).mkCode("\n\n")}
                       |}""".stripMargin
              )
          },
          cls.staticMembers
            .sortBy(_.name)
            .map(m => renderTree(m, ctx)) match {
            case Nil => None
            case nonEmpty =>
              Some(code"""|
                          |
                          |object ${cls.name.name.value} {
                          |  ${nonEmpty.mkCode("\n\n")}
                          |}""".stripMargin)
          }
        ).flatten.mkCode("")
      case ann: jvm.Annotation => renderAnnotation(ann)

      // Annotation array: Scala uses Array(a, b)
      // Nested annotations use 'new' instead of '@'
      case jvm.AnnotationArray(elements) =>
        val renderedElements = elements.map {
          case jvm.Code.Tree(ann: jvm.Annotation) => renderNestedAnnotation(ann)
          case other                              => other
        }
        code"Array(${renderedElements.mkCode(", ")})"

      // Anonymous class: new Trait { members }
      case jvm.NewWithBody(extendsClass, implementsInterface, members) =>
        val tpe = (extendsClass, implementsInterface) match {
          case (Some(cls), Some(iface)) => code"$cls with $iface"
          case (Some(cls), None)        => code"$cls"
          case (None, Some(iface))      => code"$iface"
          case (None, None)             => jvm.Code.Empty
        }
        if (members.isEmpty) code"new $tpe {}"
        else
          code"""|new $tpe {
                 |  ${members.map(m => renderTree(m, ctx)).mkCode("\n")}
                 |}""".stripMargin

      // Nested class: private final class Name(params) extends Parent(args) { members }
      case cls: jvm.NestedClass =>
        val privateMod = if (cls.isPrivate) "private " else ""
        val finalMod = if (cls.isFinal) "final " else ""
        val extendsClause = cls.`extends`
          .map { parent =>
            if (cls.superArgs.isEmpty) code" extends $parent"
            else code" extends $parent(${cls.superArgs.map(a => renderTree(a, ctx)).mkCode(", ")})"
          }
          .getOrElse(jvm.Code.Empty)
        val paramsStr = if (cls.params.isEmpty) jvm.Code.Empty else code"(${cls.params.map(p => renderParam(p, isVal = false)).mkCode(", ")})"
        if (cls.members.isEmpty) code"${privateMod}${finalMod}class ${cls.name}$paramsStr$extendsClause"
        else
          code"""|${privateMod}${finalMod}class ${cls.name}$paramsStr$extendsClause {
                 |
                 |  ${cls.members.map(m => renderTree(m, ctx)).mkCode("\n\n")}
                 |}""".stripMargin

      // Nested record: in Scala becomes case class Name(params) extends Interface { members }
      case rec: jvm.NestedRecord =>
        val privateMod = if (rec.isPrivate) "private " else ""
        val implementsClause = rec.implements match {
          case Nil      => jvm.Code.Empty
          case nonEmpty => code" extends ${nonEmpty.map(renderTree(_, ctx)).mkCode(" with ")}"
        }
        val paramsStr = if (rec.params.isEmpty) jvm.Code.Empty else code"(${rec.params.map(p => renderParam(p, isVal = true)).mkCode(", ")})"
        if (rec.members.isEmpty) code"${privateMod}case class ${rec.name}$paramsStr$implementsClause"
        else
          code"""|${privateMod}case class ${rec.name}$paramsStr$implementsClause {
                 |
                 |  ${rec.members.map(m => renderTree(m, ctx)).mkCode("\n\n")}
                 |}""".stripMargin
    }

  override def escapedIdent(value: String): String = {
    def isValidId(str: String) =
      str.head.isUnicodeIdentifierStart && str.drop(1).forall(_.isUnicodeIdentifierPart)

    def escape(str: String) = s"`$str`"
    val value1 = value.replace('-', '_')
    if (isKeyword(value1) || !isValidId(value1)) escape(value1) else value1
  }

  def renderAnnotation(ann: jvm.Annotation): jvm.Code = {
    val argsCode = renderAnnotationArgs(ann.args)
    code"@${ann.tpe}$argsCode"
  }

  /** Render a nested annotation (inside another annotation) - uses 'new' instead of '@' in Scala */
  def renderNestedAnnotation(ann: jvm.Annotation): jvm.Code = {
    val argsCode = renderAnnotationArgs(ann.args)
    code"new ${ann.tpe}$argsCode"
  }

  private def renderAnnotationArgs(args: List[jvm.Annotation.Arg]): jvm.Code = args match {
    case Nil => jvm.Code.Empty
    case args =>
      val rendered = args
        .map {
          case jvm.Annotation.Arg.Named(name, value) => code"$name = $value"
          case jvm.Annotation.Arg.Positional(value)  => value
        }
        .mkCode(", ")
      code"($rendered)"
  }

  def renderAnnotations(annotations: List[jvm.Annotation]): jvm.Code = {
    if (annotations.isEmpty) jvm.Code.Empty
    else annotations.map(renderAnnotation).mkCode("\n") ++ code"\n"
  }

  def renderAnnotationsInline(annotations: List[jvm.Annotation]): jvm.Code = {
    if (annotations.isEmpty) jvm.Code.Empty
    else annotations.map(renderAnnotation).mkCode(" ") ++ code" "
  }

  def renderParam(p: jvm.Param[jvm.Type], isVal: Boolean): jvm.Code = {
    val prefix = if (isVal) "val " else ""
    val annotationsPrefix = renderAnnotationsInline(p.annotations)
    val renderedDefault = p.default match {
      case Some(default) => " = " + default.render(this)
      case None          => ""
    }
    renderComments(p.comments) match {
      case Some(comment) =>
        // Comment includes trailing newline, so we need explicit indentation before param name
        // The 2-space indent matches renderWithParams's `(\n  ` prefix
        code"${jvm.Code.Indented(comment)}  $annotationsPrefix$prefix${p.name}: ${p.tpe}$renderedDefault"
      case None =>
        code"$annotationsPrefix$prefix${p.name}: ${p.tpe}$renderedDefault"
    }
  }

  def renderWithParams(prefix: jvm.Code, params: List[jvm.Param[jvm.Type]], implicitParams: List[jvm.Param[jvm.Type]], isVal: Boolean, forceParenthesis: Boolean, ctx: Ctx): jvm.Code = {
    val hasComments = params.exists(_.comments.lines.nonEmpty)
    val useMultiline = params.length > 1 || hasComments
    val base =
      if (useMultiline && params.nonEmpty)
        // Don't use stripMargin here - params already have explicit indentation, no smart indent needed
        jvm.Code.Combined(List(prefix, code"(\n  ", params.map(p => renderParam(p, isVal)).mkCode(",\n  "), code"\n)"))
      else if (params.nonEmpty || forceParenthesis) code"$prefix(${params.map(p => renderParam(p, isVal)).mkCode(", ")})"
      else prefix
    code"$base${renderImplicitParams(implicitParams, ctx)}"
  }

  def renderTparams(tparams: List[Type.Abstract], ctx: Ctx) =
    if (tparams.isEmpty) jvm.Code.Empty else code"[${tparams.map(t => renderTree(t, ctx)).mkCode(", ")}]"

  /** Render a Body for lambda expressions */
  def renderBody(body: jvm.Body): jvm.Code = body match {
    case jvm.Body.Abstract     => jvm.Code.Empty
    case jvm.Body.Expr(value)  => value
    case jvm.Body.Stmts(stmts) => code"{ ${stmts.mkCode("; ")} }"
  }

  def renderImplicitParams(implicitParams: List[jvm.Param[jvm.Type]], ctx: Ctx) =
    if (implicitParams.isEmpty) jvm.Code.Empty else code"(${dialect.implicitParamsPrefix}${implicitParams.map(p => renderTree(p, ctx)).mkCode(", ")})"

  def renderComments(comments: jvm.Comments): Option[jvm.Code] = {
    comments.lines match {
      case Nil => None
      case title :: Nil =>
        Some(code"""/** $title */
""")
      case title :: rest =>
        Some(code"""|/** $title
              |${rest.flatMap(_.linesIterator).map(line => s" * $line").mkString("\n")}
              | */
              |""".stripMargin)
    }
  }

  def withBody(init: jvm.Code, body: List[jvm.Code]) = {
    body match {
      case Nil => init
      case body =>
        val renderedBody = body.mkCode("\n").render(this)
        if (renderedBody.lines.length == 1)
          code"$init = ${renderedBody.asString}"
        else {
          // Add 2 spaces to the start of each line to indent body content
          // This preserves relative indentation within multi-line statements
          val indentedBody = renderedBody.asString.linesIterator.map("  " + _).mkString("\n")
          jvm.Code.Combined(List(init, code" = {\n", jvm.Code.Str(indentedBody), code"\n}"))
        }
    }
  }

  // Scala: Bijection.apply[W, U](getter)(constructor) - using apply with two curried parameter lists
  // Note: Scala 3 requires explicit .apply when passing type parameters
  override def bijection(wrapperType: jvm.Type, underlying: jvm.Type, getter: jvm.FieldGetterRef, constructor: jvm.ConstructorMethodRef): jvm.Code = {
    val bijection = dsl.Bijection
    code"$bijection.apply[$wrapperType, $underlying]($getter)($constructor)"
  }

  // Scala: (row, value) => row.copy(fieldName = value)
  override def rowSetter(fieldName: jvm.Ident): jvm.Code =
    code"(row, value) => row.copy($fieldName = value)"

  // Scala: array.foreach { elem => body }
  override def arrayForEach(array: jvm.Code, elemVar: jvm.Ident, body: jvm.Body): jvm.Code =
    body match {
      case jvm.Body.Expr(expr)   => code"$array.foreach { $elemVar => $expr }"
      case jvm.Body.Stmts(stmts) => code"$array.foreach { $elemVar => ${stmts.mkCode("; ")} }"
      case jvm.Body.Abstract     => code"$array.foreach { _ => () }"
    }

  // Scala: array.map(mapper)
  override def arrayMap(array: jvm.Code, mapper: jvm.Code, targetClass: jvm.Code): jvm.Code =
    code"$array.map($mapper)"

  // Scala: if (cond) thenExpr else elseExpr
  override def ternary(condition: jvm.Code, thenExpr: jvm.Code, elseExpr: jvm.Code): jvm.Code =
    code"(if ($condition) $thenExpr else $elseExpr)"

  // Scala: n => new Array[ClassName](n) or new Array[ClassName](length)
  override def newArray(elementType: jvm.Type, length: Option[jvm.Code]): jvm.Code =
    length match {
      case Some(len) => code"new Array[$elementType]($len)"
      case None      => code"(n => new Array[$elementType](n))"
    }

  // Scala: Array.ofDim[Byte](size)
  override def newByteArray(size: jvm.Code): jvm.Code =
    code"${TypesScala.Array}.ofDim[${TypesScala.Byte}]($size)"

  // Scala: Array[Byte]
  override val ByteArrayType: jvm.Type = jvm.Type.ArrayOf(TypesScala.Byte)

  // Scala/Java hybrid: When typeSupport is Java (java.lang.Byte), use MAX_VALUE; otherwise use MaxValue
  override def maxValue(tpe: jvm.Type): jvm.Code = tpe match {
    case TypesJava.Byte | TypesJava.Short | TypesJava.Integer | TypesJava.Long | TypesJava.Float | TypesJava.Double =>
      code"$tpe.MAX_VALUE"
    case _ =>
      code"$tpe.MaxValue"
  }

  // Scala: Array.fill(size)(factory)
  override def arrayFill(size: jvm.Code, factory: jvm.Code, elementType: jvm.Type): jvm.Code =
    code"${TypesScala.Array}.fill($size)($factory)"

  // Scala annotations use classOf[T]
  override def annotationClassRef(tpe: jvm.Type): jvm.Code =
    code"classOf[$tpe]"

  override def propertyGetterAccess(target: jvm.Code, name: jvm.Ident): jvm.Code =
    code"$target.$name"

  override def overrideValueAccess(target: jvm.Code, name: jvm.Ident): jvm.Code =
    code"$target.$name"

  override def nullaryMethodCall(target: jvm.Code, name: jvm.Ident): jvm.Code =
    code"$target.$name"

  override def arrayOf(elements: List[jvm.Code]): jvm.Code = {
    // Use Array[Any] for Scala - elements don't need explicit casting
    code"Array[Any](${elements.mkCode(", ")})"
  }

  override def typedArrayOf(elementType: jvm.Type, elements: List[jvm.Code]): jvm.Code =
    code"Array[$elementType](${elements.mkCode(", ")})"

  // Scala: == calls .equals() for structural equality
  override def equals(left: jvm.Code, right: jvm.Code): jvm.Code =
    code"($left == $right)"

  override def notEquals(left: jvm.Code, right: jvm.Code): jvm.Code =
    code"($left != $right)"

  override def castFromObject(targetType: jvm.Type, expr: jvm.Code): jvm.Code =
    code"$expr.asInstanceOf[$targetType]"

  override val isKeyword: Set[String] =
    Set(
      "abstract",
      "case",
      "class",
      "catch",
      "def",
      "do",
      "else",
      "enum",
      "extends",
      "export",
      "extension",
      "false",
      "final",
      "finally",
      "for",
      "forSome",
      "given",
      "if",
      "implicit",
      "import",
      "inline",
      "lazy",
      "macro",
      "match",
      "new",
      "null",
      "object",
      "override",
      "package",
      "private",
      "protected",
      "return",
      "sealed",
      "super",
      "then",
      "this",
      "throw",
      "trait",
      "true",
      "try",
      "type",
      "using",
      "val",
      "var",
      "with",
      "while",
      "yield",
      ".",
      "_",
      ":",
      "=",
      "=>",
      "<-",
      "<:",
      "<%",
      ">:",
      "#",
      "@"
    )

  /** Convert Java primitive type names to Scala type names */
  private def javaPrimitiveToScala(name: String) = name match {
    case "byte"    => TypesScala.Byte
    case "short"   => TypesScala.Short
    case "int"     => TypesScala.Int
    case "long"    => TypesScala.Long
    case "float"   => TypesScala.Float
    case "double"  => TypesScala.Double
    case "boolean" => TypesScala.Boolean
    case "char"    => TypesScala.Char
    case other     => sys.error(s"Unexpected: $other")
  }

}

object LangScala {

  /** LangScala using the Java DSL (typo.dsl) - for old DbLibs (Anorm, Doobie, ZioJdbc) */
  def javaDsl(dialect: Dialect, typeSupport: TypeSupport): LangScala =
    LangScala(dialect, typeSupport, DslQualifiedNames.Java)

  /** LangScala using the Java DSL (typo.dsl) - for DbLibTypo */
  def scalaDsl(dialect: Dialect, typeSupport: TypeSupport): LangScala =
    LangScala(dialect, typeSupport, DslQualifiedNames.Scala)
}

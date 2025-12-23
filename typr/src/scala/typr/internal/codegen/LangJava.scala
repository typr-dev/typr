package typr
package internal
package codegen

import typr.jvm.Code.TreeOps
import typr.jvm.Type

case object LangJava extends Lang {
  override val `;` : jvm.Code = code";"
  override val dsl: DslQualifiedNames = DslQualifiedNames.Java

  override val typeSupport: TypeSupport = TypeSupportJava

  // Type system types - Java uses Java's type system
  override val nothingType: jvm.Type = TypesJava.Void
  override val voidType: jvm.Type = TypesJava.Void
  override val topType: jvm.Type = TypesJava.Object

  def s(content: jvm.Code): jvm.Code = {
    // Java doesn't have string interpolation, so we build string concatenation directly
    val linearized = jvm.Code.linearize(content)
    val parts: List[jvm.Code] = linearized.map {
      case jvm.Code.Tree(jvm.RuntimeInterpolation(value)) =>
        // Runtime values need to be converted to string
        value
      case jvm.Code.Str(s) =>
        // String literals - wrap in Code
        jvm.StrLit(s).code
      case other =>
        // Fallback - render the code to get its string representation
        jvm.StrLit(other.render(LangJava).asString).code
    }

    // Build string concatenation: "lit" + value + "lit" + ...
    parts match {
      case Nil           => jvm.StrLit("").code
      case single :: Nil => single
      case multiple      => multiple.reduce((a, b) => code"$a + $b")
    }
  }

  def docLink(cls: jvm.QIdent, value: jvm.Ident): String =
    s"Points to {@link ${cls.dotName}#${value.value}()}"
  // don't generate imports for these
  override val BuiltIn: Map[jvm.Ident, jvm.Type.Qualified] =
    Set(
      TypesJava.Boolean,
      TypesJava.Byte,
      TypesJava.Character,
      TypesJava.Double,
      TypesJava.Float,
      TypesJava.Integer,
      TypesJava.Long,
      TypesJava.Short,
      TypesJava.String,
      TypesJava.Throwable
    )
      .map(x => (x.value.name, x))
      .toMap

  override def extension: String = "java"

  val Quote = '"'.toString
  val TripleQuote = Quote * 3

  case class RenderCtx(publicImplied: Boolean, staticImplied: Boolean, inInterface: Boolean) {
    def withPublic: RenderCtx = copy(publicImplied = true)
    def withStatic: RenderCtx = copy(staticImplied = true)
    def withInterface: RenderCtx = copy(inInterface = true, publicImplied = true)
    def public: String = if (publicImplied) "" else "public "
  }
  override type Ctx = RenderCtx
  override object Ctx extends CtxCompanion {
    val Empty: RenderCtx = RenderCtx(publicImplied = false, staticImplied = false, inInterface = false)
  }

  override def renderTree(tree: jvm.Tree, ctx: Ctx): jvm.Code =
    tree match {
      case jvm.If(branches, elseBody) =>
        val ifParts = branches.zipWithIndex.map { case (jvm.If.Branch(cond, body), idx) =>
          val keyword = if (idx == 0) "if" else "else if"
          code"""|$keyword ($cond) {
                 |  $body;
                 |}""".stripMargin
        }
        val elsePart = elseBody
          .map(e => code"""| else {
                                                |  $e;
                                                |}""".stripMargin)
          .getOrElse(jvm.Code.Empty)
        ifParts.mkCode("") ++ elsePart
      case jvm.While(cond, body) =>
        val bodyWithSemicolons = body.map(stmt => stmt ++ code";").mkCode("\n")
        code"""|while ($cond) {
               |  $bodyWithSemicolons
               |}""".stripMargin
      case jvm.IgnoreResult(expr)        => expr // Java: expression value is discarded automatically
      case jvm.NotNull(expr)             => expr // Java doesn't need not-null assertions
      case jvm.ConstructorMethodRef(tpe) => code"$tpe::new"
      case jvm.ClassOf(tpe)              =>
        // Java doesn't support generic type literals like Map<?, ?>.class - strip type parameters
        def stripTypeParams(t: jvm.Type): jvm.Type = t match {
          case jvm.Type.TApply(underlying, _)      => stripTypeParams(underlying)
          case jvm.Type.KotlinNullable(underlying) => jvm.Type.KotlinNullable(stripTypeParams(underlying))
          case other                               => other
        }
        code"${stripTypeParams(tpe)}.class"
      case jvm.JavaClassOf(tpe) =>
        // Same as ClassOf for Java
        def stripTypeParams(t: jvm.Type): jvm.Type = t match {
          case jvm.Type.TApply(underlying, _)      => stripTypeParams(underlying)
          case jvm.Type.KotlinNullable(underlying) => jvm.Type.KotlinNullable(stripTypeParams(underlying))
          case other                               => other
        }
        code"${stripTypeParams(tpe)}.class"
      case jvm.Call(target, argGroups) =>
        val allArgs = argGroups.flatMap(_.args)
        code"$target(${allArgs.map(_.value).mkCode(", ")})"
      case jvm.Apply0(jvm.Ref(name, jvm.Type.Function0(jvm.Type.Void)))                   => code"$name.run()"
      case jvm.Apply0(jvm.Ref(name, jvm.Type.Function0(_)))                               => code"$name.get()"
      case jvm.Apply1(jvm.Ref(name, jvm.Type.Function1(_, jvm.Type.Void)), arg1)          => code"$name.accept($arg1)"
      case jvm.Apply1(jvm.Ref(name, jvm.Type.Function1(_, _)), arg1)                      => code"$name.apply($arg1)"
      case jvm.Apply2(jvm.Ref(name, jvm.Type.Function2(_, _, jvm.Type.Void)), arg1, arg2) => code"$name.accept($arg1, $arg2)"
      case jvm.Apply2(jvm.Ref(name, jvm.Type.Function2(_, _, _)), arg1, arg2)             => code"$name.apply($arg1, $arg2)"
      case jvm.Select(target, name)                                                       => code"$target.$name"
      case jvm.ArrayIndex(target, num)                                                    => code"$target[$num]"
      case jvm.ApplyNullary(target, name)                                                 => code"$target.$name()"
      case jvm.Arg.Named(_, value)                                                        => value
      case jvm.Arg.Pos(value)                                                             => value
      case jvm.Ident(value)                                                               => escapedIdent(value)
      case jvm.MethodRef(tpe, name)                                                       => code"$tpe::$name"
      case jvm.New(target, args)                                                          => code"new $target(${args.map(a => renderTree(a, ctx)).mkCode(", ")})"
      case jvm.LocalVar(name, tpe, value)                                                 => tpe.fold(code"var $name = $value")(t => code"$t $name = $value")
      case jvm.InferredTargs(target)                                                      => code"$target<>"
      case jvm.GenericMethodCall(target, methodName, typeArgs, args)                      =>
        // Java: target.<T1, T2>method(args)
        val typeArgStr = if (typeArgs.isEmpty) jvm.Code.Empty else code"<${typeArgs.map(t => renderTree(t, ctx)).mkCode(", ")}>"
        val argStr = if (args.isEmpty) code"()" else code"(${args.map(a => renderTree(a, ctx)).mkCode(", ")})"
        code"$target.$typeArgStr$methodName$argStr"
      case jvm.Return(expr)                   => code"return $expr"
      case jvm.Throw(expr)                    => code"throw $expr"
      case jvm.Stmt(stmtCode, needsSemicolon) => if (needsSemicolon) code"$stmtCode;" else stmtCode
      case jvm.Lambda(params, body) =>
        val paramsCode = params match {
          case Nil                                    => code"()"
          case List(jvm.LambdaParam(name, None))      => name.code
          case List(jvm.LambdaParam(name, Some(tpe))) => code"($tpe $name)"
          case _ if params.forall(_.tpe.isEmpty)      => code"(${params.map(p => p.name.code).mkCode(", ")})"
          case _                                      => code"(${params.map(p => p.tpe.fold(code"${p.name}")(t => code"$t ${p.name}")).mkCode(", ")})"
        }
        code"$paramsCode -> ${renderBody(body)}"
      case jvm.SamLambda(_, lambda) =>
        // Java: SAM conversion is automatic, just render the lambda
        renderTree(lambda, ctx)
      case jvm.Cast(targetType, expr) =>
        // Java cast: (Type) expr
        code"(($targetType) $expr)"
      case jvm.ByName(body) =>
        // Java: by-name becomes Supplier/Runnable
        code"() -> ${renderBody(body)}"
      case jvm.FieldGetterRef(rowType, field)        => code"$rowType::$field"
      case jvm.SelfNullary(name)                     => code"$name()" // Java: method call on implicit this
      case jvm.TypedFactoryCall(tpe, typeArgs, args) =>
        // Java: Type.<T1, T2>of(args)
        val typeArgStr = if (typeArgs.isEmpty) jvm.Code.Empty else code"<${typeArgs.map(t => renderTree(t, ctx)).mkCode(", ")}>"
        code"$tpe.$typeArgStr${jvm.Ident("of")}(${args.map(a => renderTree(a, ctx)).mkCode(", ")})"
      case jvm.Param(anns, cs, name, tpe, _) => code"${renderComments(cs).getOrElse(jvm.Code.Empty)}${renderAnnotationsInline(anns)}$tpe $name"
      case jvm.QIdent(value)                 => value.map(i => renderTree(i, ctx)).mkCode(".")
      case jvm.StrLit(str) =>
        val escaped = str
          .replace("\\", "\\\\")
          .replace("\"", "\\\"")
          .replace("\n", "\\n")
          .replace("\r", "\\r")
          .replace("\t", "\\t")
        Quote + escaped + Quote
      case jvm.Summon(_)                                   => sys.error("java doesn't support `summon`")
      case jvm.Type.Abstract(value, _)                     => value.code // Java ignores variance
      case jvm.Type.ArrayOf(value)                         => code"$value[]"
      case jvm.Type.KotlinNullable(underlying)             => renderTree(underlying, ctx) // Java doesn't have Kotlin's T? syntax, render underlying type
      case jvm.Type.Commented(underlying, comment)         => code"$comment $underlying"
      case jvm.Type.Annotated(underlying, _)               => renderTree(underlying, ctx) // Java doesn't support Scala-style type annotations
      case jvm.Type.Function0(jvm.Type.Void)               => TypesJava.Runnable.code
      case jvm.Type.Function0(targ)                        => TypesJava.Supplier.of(targ).code
      case jvm.Type.Function1(targ, jvm.Type.Void)         => TypesJava.Consumer.of(targ).code
      case jvm.Type.Function1(targ, ret)                   => TypesJava.Function.of(targ, ret).code
      case jvm.Type.Function2(targ1, targ2, jvm.Type.Void) => TypesJava.BiConsumer.of(targ1, targ2).code
      case jvm.Type.Function2(targ1, targ2, ret)           => TypesJava.BiFunction.of(targ1, targ2, ret).code
      case jvm.Type.Qualified(value)                       => value.code
      case jvm.Type.TApply(underlying, targs)              => code"$underlying<${targs.map(t => renderTree(t, ctx)).mkCode(", ")}>"
      case jvm.Type.UserDefined(underlying)                => code"/* user-picked */ $underlying"
      case jvm.Type.Void                                   => code"void"
      case jvm.Type.Wildcard                               => code"?"
      case jvm.Type.Primitive(name)                        => name
      case jvm.RuntimeInterpolation(value)                 => value
      case jvm.Import(_, _)                                => jvm.Code.Empty // Import node just triggers import, no code output
      case jvm.IfExpr(pred, thenp, elsep) =>
        code"""|($pred
               |  ? $thenp
               |  : $elsep)""".stripMargin
      case jvm.TypeSwitch(value, cases, nullCase, defaultCase, _) =>
        // In Java switch expressions: blocks don't need semicolon, expressions do
        // Note: unchecked flag is Scala-only (for @unchecked annotation), ignored in Java
        def needsSemicolon(body: jvm.Code): String = {
          val rendered = body.render(LangJava).asString.trim
          if (rendered.endsWith("}")) "" else ";"
        }
        val nullCaseCode = nullCase.map(body => code"case null -> $body${needsSemicolon(body)}").toList
        val typeCases = cases.map { case jvm.TypeSwitch.Case(pat, ident, body) => code"case $pat $ident -> $body${needsSemicolon(body)}" }
        val defaultCaseCode = defaultCase.map(body => code"default -> $body${needsSemicolon(body)}").toList
        val allCases = nullCaseCode ++ typeCases ++ defaultCaseCode
        code"""|switch ($value) {
               |  ${allCases.mkCode("\n")}
               |}""".stripMargin
      case jvm.TryCatch(tryBlock, catches, finallyBlock) =>
        val tryCode = code"""|try {
                             |  ${tryBlock.map(s => renderStmt(s)).mkCode("\n")}
                             |}""".stripMargin
        val catchCodes = catches.map { case jvm.TryCatch.Catch(exType, ident, body) =>
          code"""|catch ($exType $ident) {
                 |  ${body.map(s => renderStmt(s)).mkCode("\n")}
                 |}""".stripMargin
        }
        val finallyCode =
          if (finallyBlock.isEmpty) jvm.Code.Str("")
          else
            code"""|finally {
                 |  ${finallyBlock.map(s => renderStmt(s)).mkCode("\n")}
                 |}""".stripMargin
        code"$tryCode ${catchCodes.mkCode(" ")} $finallyCode"
      case jvm.IfElseChain(cases, elseCase) =>
        // IfElseChain bodies are statements that need semicolons in Java
        val ifCases = cases.zipWithIndex.map { case ((cond, body), idx) =>
          if (idx == 0) code"if ($cond) { $body; }"
          else code"else if ($cond) { $body; }"
        }
        val elseCode = code"else { $elseCase; }"
        (ifCases :+ elseCode).mkCode("\n")
      case jvm.StringInterpolate(_, _, _) =>
        // StringInterpolate should never reach LangJava
        // DbLibTypo.SQL() should have already rewritten it to Fragment.interpolate() calls
        sys.error("StringInterpolate should not reach LangJava. DbLibTypo.SQL() should have rewritten it to Fragment.interpolate() calls.")

      case jvm.Given(annotations, tparams, name, implicitParams, tpe, body) =>
        val annotationsCode = renderAnnotations(annotations)
        val staticMod = if (ctx.staticImplied) "static " else ""
        if (tparams.isEmpty && implicitParams.isEmpty)
          code"""|${annotationsCode}${staticMod}${ctx.public}$tpe $name =
                 |  $body""".stripMargin
        else {
          val paramsCode = renderParams(implicitParams, ctx)
          code"${annotationsCode}${staticMod}${ctx.public}${renderTparams(tparams)} $tpe $name" ++ paramsCode ++ code"""| {
                |  return $body;
                |}""".stripMargin
        }
      case jvm.Value(annotations, name, tpe, None, _, _, _) =>
        val annotationsCode = renderAnnotations(annotations)
        val staticMod = if (ctx.staticImplied) "static " else ""
        code"$annotationsCode$staticMod$tpe $name;"
      case jvm.Value(annotations, name, tpe, Some(body), isLazy, isOverride, _) =>
        val overrideAnnotation = if (isOverride) "@Override\n" else ""
        val staticMod = if (ctx.staticImplied) "static " else ""
        val publicMod = if (ctx.staticImplied) ctx.public else ""
        if (isLazy) {
          // In Java, lazy vals become methods
          code"""|$overrideAnnotation$publicMod$staticMod$tpe $name() {
                 |  return $body;
                 |}""".stripMargin
        } else {
          val annotationsCode = renderAnnotations(annotations)
          code"$annotationsCode$publicMod$staticMod$tpe $name = $body;"
        }
      case jvm.Method(annotations, comments, tparams, name, params, implicitParams, tpe, throws, body, isOverride, isDefault) =>
        val overrideAnnotation = if (isOverride) "@Override\n" else ""
        val annotationsCode = renderAnnotations(annotations)
        val commentCode = renderComments(comments).getOrElse(jvm.Code.Empty)
        val allParams = params ++ implicitParams
        val paramsCode = renderParams(allParams, ctx)
        val throwsCode = if (throws.isEmpty) jvm.Code.Empty else code" throws ${throws.map(t => renderTree(t, ctx)).mkCode(", ")}"
        val defaultKeyword = if (isDefault) "default " else ""
        val staticMod = if (ctx.staticImplied) "static " else ""
        val signature = commentCode ++ code"$overrideAnnotation" ++ annotationsCode ++ code"$staticMod$defaultKeyword${ctx.public}${renderTparams(tparams)}$tpe $name" ++ paramsCode ++ throwsCode

        body match {
          case jvm.Body.Abstract =>
            signature
          case jvm.Body.Expr(expr) =>
            // TryCatch has internal returns, so don't add outer return
            val bodyCode =
              if (tpe == jvm.Type.Void) code"$expr;"
              else code"return $expr;"
            signature ++ code"""| {
                  |  $bodyCode
                  |}""".stripMargin
          case jvm.Body.Stmts(stmts) =>
            signature ++ code"""| {
                  |  ${stmts.map(s => renderStmt(s)).mkCode("\n")}
                  |}""".stripMargin
        }
      case enm: jvm.Enum =>
        val annotationsCode = renderAnnotations(enm.annotations)
        val enumMap = TypesJava.Map.of(TypesJava.String, enm.tpe)
        code"""|${annotationsCode}public enum ${enm.tpe.name} {
               |    ${enm.values.map { case (name, expr) => code"$name($expr)" }.mkCode(",\n")};
               |    final ${TypesJava.String} value;
               |
               |    public ${TypesJava.String} value() {
               |        return value;
               |    }
               |
               |    ${enm.tpe.name}(${TypesJava.String} value) {
               |        this.value = value;
               |    }
               |
               |    public static final ${TypesJava.String} Names = ${TypesJava.Arrays}.stream(${enm.tpe}.values()).map(x -> x.value).collect(${TypesJava.Collectors}.joining(", "));
               |    public static final $enumMap ByName = ${TypesJava.Arrays}.stream(${enm.tpe}.values()).collect(${TypesJava.Collectors}.toMap(n -> n.value, n -> n));
               |
               |    ${enm.staticMembers.map(t => code"static $t;").mkCode("\n")}
               |
               |    public static ${enm.tpe.name} force(${TypesJava.String} str) {
               |        if (ByName.containsKey(str)) {
               |            return ByName.get(str);
               |        } else {
               |            throw new RuntimeException("'" + str + "' does not match any of the following legal values: " + Names);
               |        }
               |    }
               |}
               |""".stripMargin
      case enm: jvm.OpenEnum =>
        val annotationsCode = renderAnnotations(enm.annotations)
        code"""|${annotationsCode}public sealed interface ${enm.tpe.name} permits ${enm.tpe.name}.Unknown, ${enm.tpe.name}.Known {
               |    ${enm.underlyingType} value();
               |
               |    record Unknown(${enm.underlyingType} value) implements ${enm.tpe.name} {
               |    }
               |
               |    enum Known implements ${enm.tpe.name} {
               |        ${enm.values.map { case (name, expr) => code"$name($expr)" }.mkCode(",\n")};
               |        final ${enm.underlyingType} value;
               |
               |        @Override
               |        public ${enm.underlyingType} value() {
               |            return value;
               |        }
               |
               |        Known(${enm.underlyingType} value) {
               |            this.value = value;
               |        }
               |    }
               |
               |    ${TypesJava.Map}<${enm.underlyingType}, Known> ByName = ${TypesJava.Arrays}.stream(Known.values()).collect(${TypesJava.Collectors}.toMap(n -> n.value, n -> n));
               |
               |    static ${enm.tpe.name} apply(${enm.underlyingType} str) {
               |        if (Known.ByName.containsKey(str)) {
               |            return Known.ByName.get(str);
               |        } else {
               |            return new Unknown(str);
               |        }
               |    }
               |    ${enm.staticMembers.map(x => code"static $x;").mkCode("\n")}
               |}""".stripMargin
      case cls: jvm.Adt.Record =>
        // Record methods always need `public` - even when nested in an interface,
        // they implement interface methods which require explicit public
        val memberCtx = Ctx.Empty
        val staticMemberCtx = memberCtx.withStatic
        val body: List[jvm.Code] =
          cls.staticMembers.sortBy(_.name).map(x => renderTree(x, staticMemberCtx)) ++ cls.members.sortBy(_.name).map(m => renderTree(m, memberCtx))

        // Inside the class, use the short name (no package prefix needed)
        val clsTypeBase = jvm.Type.Qualified(jvm.QIdent(List(cls.name.value.name)))
        // Apply type parameters if present (e.g., Provided<T> not raw Provided)
        val clsType: jvm.Type = cls.tparams match {
          case Nil      => clsTypeBase
          case nonEmpty => jvm.Type.TApply(clsTypeBase, nonEmpty)
        }
        val withers: List[jvm.Code] =
          cls.params.map { param =>
            val name = jvm.Ident(s"with${param.name.value.capitalize}")
            val target: jvm.Code = if (cls.tparams.nonEmpty) jvm.InferredTargs(clsTypeBase) else clsTypeBase
            val body = jvm.New(target, cls.params.map { p => jvm.Arg.Pos(p.name) })
            // Move param comment to method level, clear param comment
            val paramWithoutComment = param.copy(annotations = Nil, comments = jvm.Comments.Empty)
            renderTree(jvm.Method(Nil, param.comments, Nil, name, List(paramWithoutComment), Nil, clsType, Nil, jvm.Body.Expr(body.code), isOverride = false, isDefault = false), memberCtx)
          }

        // For wrapper types with a single value field, generate toString() that returns just the value
        val toStringMethod: Option[jvm.Code] =
          if (cls.isWrapper && cls.params.size == 1) {
            val valueParam = cls.params.head
            // Unwrap to base type (removes comments, annotations, etc.) to check if it's String
            val baseTpe = jvm.Type.base(valueParam.tpe)
            val toStringBody =
              if (baseTpe == TypesJava.String) code"return ${valueParam.name}"
              else code"return ${valueParam.name}.toString()"
            Some(
              renderTree(
                jvm.Method(Nil, jvm.Comments.Empty, Nil, jvm.Ident("toString"), Nil, Nil, TypesJava.String, Nil, jvm.Body.Stmts(List(toStringBody)), isOverride = true, isDefault = false),
                memberCtx
              )
            )
          } else None

        // Params with defaults can be omitted from the short constructor
        val requiredParams = cls.params.filter(_.default.isEmpty)
        val hasDefaultableParams = cls.params.exists(_.default.isDefined)

        // Generate short constructor if there are params that can be defaulted
        // (includes no-arg constructor when ALL params are defaultable)
        val shortConstructor: Option[jvm.Code] =
          if (hasDefaultableParams) {
            // Generate default value expressions for each param
            val defaultArgs = cls.params.map { param =>
              param.default.getOrElse(code"${param.name}")
            }
            Some(code"${memberCtx.public}${cls.name.name.value}" ++ renderParams(requiredParams, memberCtx) ++ code"""| {
                        |  this(${defaultArgs.mkCode(", ")});
                        |}""".stripMargin)
          } else None

        val annotationsCode = if (cls.annotations.isEmpty) None else Some(renderAnnotations(cls.annotations))

        List[Option[jvm.Code]](
          renderComments(cls.comments),
          annotationsCode,
          Some(code"${ctx.public}record "),
          Some(cls.name.name.value),
          cls.tparams match {
            case Nil      => None
            case nonEmpty => Some(renderTparams(nonEmpty))
          },
          Some(renderParams(cls.params, ctx)),
          cls.implements match {
            case Nil      => None
            case nonEmpty => Some(code" implements ${nonEmpty.map(x => code"$x").mkCode(", ")}")
          },
          Some(code"""| {
                      |  ${(shortConstructor.toList ++ withers ++ toStringMethod.toList ++ body).map(_ ++ code";").mkCode("\n\n")}
                      |}""".stripMargin)
        ).flatten.mkCode("")
      case sum: jvm.Adt.Sum =>
        // Inside a sealed interface, public is implied for all members including nested types
        val memberCtx = ctx.withPublic
        val staticMemberCtx = memberCtx.withStatic
        val body: List[jvm.Code] =
          List(
            sum.flattenedSubtypes.sortBy(_.name).map(t => renderTree(t, memberCtx)),
            sum.staticMembers.sortBy(_.name).map(x => renderTree(x, staticMemberCtx)),
            sum.members.sortBy(_.name).map(m => renderTree(m, memberCtx))
          ).flatten

        // Java sealed interfaces need a 'permits' clause listing all permitted subtypes
        // If permittedSubtypes is provided, use those; otherwise derive from nested subtypes
        val permitsClause: Option[jvm.Code] = sum.permittedSubtypes match {
          case Nil if sum.subtypes.isEmpty => None // No subtypes at all - this would be a compile error in Java
          case Nil                         =>
            // Use nested subtypes - for nested types, use simple name (Parent.Child format)
            val subtypeNames = sum.flattenedSubtypes.map { subtype =>
              // Get relative name from parent type
              val parentName = sum.name.name.value
              val childName = subtype.name.name.value
              code"$parentName.$childName"
            }
            Some(code" permits ${subtypeNames.mkCode(", ")}")
          case nonEmpty =>
            // Use explicitly provided permitted subtypes (for top-level classes in same file)
            val subtypeNames = nonEmpty.map(_.name.value).map(n => code"$n")
            Some(code" permits ${subtypeNames.mkCode(", ")}")
        }

        val annotationsCode = if (sum.annotations.isEmpty) None else Some(renderAnnotations(sum.annotations))
        List[Option[jvm.Code]](
          renderComments(sum.comments),
          annotationsCode,
          Some(code"${ctx.public}sealed interface "),
          Some(sum.name.name.value),
          sum.tparams match {
            case Nil      => None
            case nonEmpty => Some(renderTparams(nonEmpty))
          },
          permitsClause,
          sum.implements match {
            case Nil      => None
            case nonEmpty => Some(nonEmpty.map(x => code" extends $x").mkCode(" "))
          },
          Some(code"""| {
                      |  ${body.map(_ ++ code";").mkCode("\n\n")}
                      |}""".stripMargin)
        ).flatten.mkCode("")
      case cls: jvm.Class =>
        // Inside an interface, public is implied for members and methods with body need 'default'
        val memberCtx = if (cls.classType == jvm.ClassType.Interface) ctx.withInterface else ctx
        val staticMemberCtx = memberCtx.withStatic
        // Sort fields before methods for proper Java class structure
        val sortedMembers = cls.members.sortBy {
          case _: jvm.Value => 0
          case _            => 1
        }
        val body: List[jvm.Code] =
          cls.staticMembers.sortBy(_.name).map(x => renderTree(x, staticMemberCtx)) ++ sortedMembers.map(m => renderTree(m, memberCtx))

        // Generate fields and constructor from params if present
        val (fieldsCode, constructorCode) = cls.params match {
          case Nil => (Nil, Nil)
          case params =>
            val fields = params.map { param =>
              code"${param.tpe} ${param.name};"
            }
            val constructorAssignments = params.map { param =>
              code"this.${param.name} = ${param.name};"
            }
            val constructor = code"${memberCtx.public}${cls.name.name.value}" ++ renderParams(params, memberCtx) ++ code"""| {
                        |  ${constructorAssignments.mkCode("\n")}
                        |}""".stripMargin
            (fields, List(constructor))
        }

        val annotationsCode = if (cls.annotations.isEmpty) None else Some(renderAnnotations(cls.annotations))
        List[Option[jvm.Code]](
          renderComments(cls.comments),
          annotationsCode,
          Some(code"${ctx.public}"),
          cls.classType match {
            case jvm.ClassType.Class         => Some(code"class ")
            case jvm.ClassType.AbstractClass => Some(code"abstract class ")
            case jvm.ClassType.Interface     => Some(code"interface ")
          },
          Some(cls.name.name.value),
          cls.tparams match {
            case Nil      => None
            case nonEmpty => Some(renderTparams(nonEmpty))
          },
          cls.`extends`.map(x => code" extends $x"),
          cls.implements match {
            case Nil      => None
            case nonEmpty =>
              // In Java, interfaces use 'extends' to inherit from other interfaces,
              // while classes use 'implements' for interfaces
              val keyword = if (cls.classType == jvm.ClassType.Interface) "extends" else "implements"
              Some(nonEmpty.map(x => code" $keyword $x").mkCode(","))
          },
          Some {
            val allBody = fieldsCode ++ constructorCode ++ body
            code"""| {
                   |  ${allBody.map(c => c ++ code";").mkCode("\n\n")}
                   |}""".stripMargin
          }
        ).flatten.mkCode("")
      case ann: jvm.Annotation => renderAnnotation(ann)

      // Annotation array: Java uses { a, b }
      case jvm.AnnotationArray(elements) =>
        code"{ ${elements.mkCode(", ")} }"

      // Anonymous class: new Interface() { members }
      // In Java, anonymous classes can only extend one class OR implement one interface
      case jvm.NewWithBody(extendsClass, implementsInterface, members) =>
        val tpe: jvm.Code = extendsClass.orElse(implementsInterface) match {
          case Some(t) => t.code
          case None    => jvm.Code.Empty
        }
        if (members.isEmpty) code"new $tpe() {}"
        else {
          val memberCtx = Ctx.Empty // Inside anonymous class, public is required
          code"""|new $tpe() {
                 |  ${members.map(m => renderTree(m, memberCtx) ++ code";").mkCode("\n")}
                 |}""".stripMargin
        }

      // Nested class: final class Name extends Parent { Name(params) { super(args); } members }
      case cls: jvm.NestedClass =>
        val finalMod = if (cls.isFinal) "final " else ""
        val extendsClause = cls.`extends`.map(parent => code" extends $parent").getOrElse(jvm.Code.Empty)
        val memberCtx = Ctx.Empty
        // Generate constructor with super call
        val constructor: jvm.Code = {
          val paramsCode = renderParams(cls.params, memberCtx)
          val superCall = if (cls.superArgs.isEmpty) jvm.Code.Empty else code"super(${cls.superArgs.map(a => renderTree(a, memberCtx)).mkCode(", ")});"
          code"""|${cls.name}$paramsCode {
                 |  $superCall
                 |}""".stripMargin
        }
        val membersCode = cls.members.map(m => renderTree(m, memberCtx) ++ code";").mkCode("\n\n")
        code"""|${finalMod}class ${cls.name}$extendsClause {
               |  $constructor
               |
               |  $membersCode
               |}""".stripMargin

      // Nested record: record Name(params) implements Interface { members }
      case rec: jvm.NestedRecord =>
        val memberCtx = Ctx.Empty
        val paramsCode = rec.params.map(p => code"${p.tpe} ${p.name}").mkCode(", ")
        val implementsClause = rec.implements match {
          case Nil      => jvm.Code.Empty
          case nonEmpty => code" implements ${nonEmpty.map(renderTree(_, memberCtx)).mkCode(", ")}"
        }
        if (rec.members.isEmpty) {
          code"record ${rec.name}($paramsCode)$implementsClause {}"
        } else {
          val membersCode = rec.members.map(m => renderTree(m, memberCtx) ++ code";").mkCode("\n\n")
          code"""|record ${rec.name}($paramsCode)$implementsClause {
                 |  $membersCode
                 |}""".stripMargin
        }
    }

  override def escapedIdent(value: String): String = {
    val filtered = value.zipWithIndex.collect {
      case ('-', _)                             => "_"
      case (c, 0) if c.isUnicodeIdentifierStart => c
      case (c, _) if c.isUnicodeIdentifierPart  => c
    }.mkString
    val filtered2 = if (filtered.forall(_.isDigit)) "_" + filtered else filtered
    if (isKeyword(filtered2)) s"${filtered2}_" else filtered2
  }

  def renderParams(params: List[jvm.Param[jvm.Type]], ctx: Ctx): jvm.Code = {
    val hasComments = params.exists(_.comments.lines.nonEmpty)
    params match {
      case Nil                       => code"()"
      case List(one) if !hasComments => code"(${renderTree(one, ctx)})"
      case more =>
        code"""|(
               |  ${more.init.map(p => code"${renderTree(p, ctx)},").mkCode("\n")}
               |  ${more.lastOption.map(p => code"${renderTree(p, ctx)}").getOrElse(jvm.Code.Empty)}
               |)""".stripMargin
    }
  }
  def renderTparams(tparams: List[Type.Abstract]) =
    if (tparams.isEmpty) jvm.Code.Empty else code"<${tparams.map(t => renderTree(t, Ctx.Empty)).mkCode(", ")}>"

  /** Render a Body for lambda expressions */
  def renderBody(body: jvm.Body): jvm.Code = body match {
    case jvm.Body.Abstract     => jvm.Code.Empty
    case jvm.Body.Expr(value)  => value
    case jvm.Body.Stmts(stmts) => code"{\n  ${stmts.map(s => renderStmt(s)).mkCode("\n  ")}\n}"
  }

  /** Render a statement with semicolon if needed. Compound statements (if/try) don't need semicolons. */
  def renderStmt(stmt: jvm.Code): jvm.Code = stmt match {
    case jvm.Code.Tree(jvm.Stmt(inner, needsSemi)) => if (needsSemi) code"$inner;" else inner
    case jvm.Code.Tree(_: jvm.IfElseChain)         => stmt
    case jvm.Code.Tree(_: jvm.TryCatch)            => stmt
    case _                                         => code"$stmt;"
  }

  def renderComments(comments: jvm.Comments): Option[jvm.Code] = {
    comments.lines match {
      case Nil => None
      case title :: Nil =>
        Some(code"""/** $title */\n""")
      case title :: rest =>
        Some(code"""|/** $title
              |${rest.flatMap(_.linesIterator).map(line => s"  * $line").mkString("\n")}
              |  */\n""".stripMargin)
    }
  }

  def renderAnnotation(ann: jvm.Annotation): jvm.Code = {
    val argsCode = ann.args match {
      case Nil                                        => code""
      case List(jvm.Annotation.Arg.Positional(value)) => code"($value)"
      case args =>
        val rendered = args
          .map {
            case jvm.Annotation.Arg.Named(name, value) => code"$name = $value"
            case jvm.Annotation.Arg.Positional(value)  => value
          }
          .mkCode(", ")
        code"($rendered)"
    }
    code"@${ann.tpe}$argsCode"
  }

  def renderAnnotations(annotations: List[jvm.Annotation]): jvm.Code = {
    if (annotations.isEmpty) jvm.Code.Empty
    else annotations.map(renderAnnotation).mkCode("\n") ++ code"\n"
  }

  def renderAnnotationsInline(annotations: List[jvm.Annotation]): jvm.Code = {
    if (annotations.isEmpty) jvm.Code.Empty
    else annotations.map(renderAnnotation).mkCode(" ") ++ code" "
  }

  // Java: Bijection.of(getter, constructor)
  override def bijection(wrapperType: jvm.Type, underlying: jvm.Type, getter: jvm.FieldGetterRef, constructor: jvm.ConstructorMethodRef): jvm.Code = {
    val bijection = dsl.Bijection
    code"$bijection.of($getter, $constructor)"
  }

  // Java: (row, value) -> row.withFieldName(value)
  override def rowSetter(fieldName: jvm.Ident): jvm.Code = {
    val capitalizedName = fieldName.value.capitalize
    jvm.Lambda("row", "value", jvm.Body.Expr(code"row.with$capitalizedName(value)"))
  }

  // Java: for (var elem : array) { body; }
  override def arrayForEach(array: jvm.Code, elemVar: jvm.Ident, body: jvm.Body): jvm.Code =
    body match {
      case jvm.Body.Expr(expr) => code"for (var $elemVar : $array) { $expr; }"
      case _: jvm.Body.Stmts   => code"for (var $elemVar : $array) ${renderBody(body)}"
      case jvm.Body.Abstract   => code"for (var $elemVar : $array) {}"
    }

  // Java: arrayMap.map(array, mapper, targetClass)
  override def arrayMap(array: jvm.Code, mapper: jvm.Code, targetClass: jvm.Code): jvm.Code = {
    val arrayMapHelper = jvm.Type.Qualified("typr.runtime.internal.arrayMap")
    code"$arrayMapHelper.map($array, $mapper, $targetClass)"
  }

  // Java: cond ? thenExpr : elseExpr
  override def ternary(condition: jvm.Code, thenExpr: jvm.Code, elseExpr: jvm.Code): jvm.Code =
    code"($condition ? $thenExpr : $elseExpr)"

  // Java: ClassName[]::new or new ClassName[length]
  override def newArray(elementType: jvm.Type, length: Option[jvm.Code]): jvm.Code =
    length match {
      case Some(len) => code"new $elementType[$len]"
      case None      => code"$elementType[]::new"
    }

  // Java: new byte[size]
  override def newByteArray(size: jvm.Code): jvm.Code =
    code"new byte[$size]"

  // Java: byte[]
  override val ByteArrayType: jvm.Type = jvm.Type.ArrayOf(TypesJava.BytePrimitive)

  // Java uses MAX_VALUE for max value constants
  override def maxValue(tpe: jvm.Type): jvm.Code = code"$tpe.MAX_VALUE"

  // Java: Stream.generate(() -> factory).limit(size).toArray(elementType[]::new)
  override def arrayFill(size: jvm.Code, factory: jvm.Code, elementType: jvm.Type): jvm.Code =
    code"${TypesJava.Stream}.generate(() -> $factory).limit($size).toArray($elementType[]::new)"

  // Java annotations use Type.class
  override def annotationClassRef(tpe: jvm.Type): jvm.Code = {
    def stripTypeParams(t: jvm.Type): jvm.Type = t match {
      case jvm.Type.TApply(underlying, _)      => stripTypeParams(underlying)
      case jvm.Type.KotlinNullable(underlying) => jvm.Type.KotlinNullable(stripTypeParams(underlying))
      case other                               => other
    }
    code"${stripTypeParams(tpe)}.class"
  }

  override def propertyGetterAccess(target: jvm.Code, name: jvm.Ident): jvm.Code =
    code"$target.$name()"

  override def overrideValueAccess(target: jvm.Code, name: jvm.Ident): jvm.Code =
    code"$target.$name()"

  override def nullaryMethodCall(target: jvm.Code, name: jvm.Ident): jvm.Code =
    code"$target.$name()"

  override def arrayOf(elements: List[jvm.Code]): jvm.Code =
    code"new Object[]{${elements.mkCode(", ")}}"

  override def typedArrayOf(elementType: jvm.Type, elements: List[jvm.Code]): jvm.Code =
    code"new $elementType[]{${elements.mkCode(", ")}}"

  // Java: use .equals() for structural equality
  override def equals(left: jvm.Code, right: jvm.Code): jvm.Code =
    code"$left.equals($right)"

  override def notEquals(left: jvm.Code, right: jvm.Code): jvm.Code =
    code"!$left.equals($right)"

  override def castFromObject(targetType: jvm.Type, expr: jvm.Code): jvm.Code =
    code"($targetType) $expr"

  val isKeyword: Set[String] =
    Set(
      "abstract",
      "assert",
      "boolean",
      "break",
      "byte",
      "case",
      "catch",
      "char",
      "class",
      "const",
      "continue",
      "default",
      "do",
      "double",
      "else",
      "enum",
      "extends",
      "final",
      "finally",
      "float",
      "for",
      "goto",
      "if",
      "implements",
      "import",
      "instanceof",
      "int",
      "interface",
      "long",
      "native",
      "new",
      "package",
      "private",
      "protected",
      "public",
      "return",
      "short",
      "static",
      "strictfp",
      "super",
      "switch",
      "synchronized",
      "this",
      "throw",
      "throws",
      "transient",
      "try",
      "void",
      "volatile",
      "while",
      "true",
      "false",
      "null"
    )

}

package typo
package internal
package codegen

import typo.jvm.Code.TreeOps
import typo.jvm.Type

case object LangJava extends Lang {
  override val `;` : jvm.Code = code";"

  // Delegate type support to TypeSupportJava
  private val typeSupport: TypeSupport = TypeSupportJava
  override val BigDecimal: jvm.Type = typeSupport.BigDecimal
  override val Boolean: jvm.Type = typeSupport.Boolean
  override val Byte: jvm.Type = typeSupport.Byte
  override val Double: jvm.Type = typeSupport.Double
  override val Float: jvm.Type = typeSupport.Float
  override val Int: jvm.Type = typeSupport.Int
  override val IteratorType: jvm.Type = typeSupport.IteratorType
  override val Long: jvm.Type = typeSupport.Long
  override val Short: jvm.Type = typeSupport.Short
  override val Optional: OptionalSupport = typeSupport.Optional
  override val ListType: ListSupport = typeSupport.ListType
  override val Random: RandomSupport = typeSupport.Random
  override val MapOps: MapSupport = typeSupport.MapOps
  override def bigDecimalFromDouble(d: jvm.Code): jvm.Code = typeSupport.bigDecimalFromDouble(d)

  def s(content: jvm.Code): jvm.StringInterpolate =
    jvm.StringInterpolate(Type.Qualified("typo.runtime.internal.stringInterpolator") / jvm.Ident("str"), jvm.Ident("str"), content)

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
      case jvm.IgnoreResult(expr)        => expr // Java: expression value is discarded automatically
      case jvm.ConstructorMethodRef(tpe) => code"$tpe::new"
      case jvm.ClassOf(tpe)              =>
        // Java doesn't support generic type literals like Map<?, ?>.class - strip type parameters
        def stripTypeParams(t: jvm.Type): jvm.Type = t match {
          case jvm.Type.TApply(underlying, _) => stripTypeParams(underlying)
          case other                          => other
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
      case jvm.Lambda0(body)                 => code"() -> $body"
      case jvm.Lambda1(param, body)          => code"$param -> $body"
      case jvm.Lambda2(param1, param2, body) => code"($param1, $param2) -> $body"
      case jvm.ByName(body)                  =>
        // Java: by-name becomes Supplier/Runnable
        // Scala () (Unit) becomes {} in Java void lambda
        val rendered = body.render(LangJava).asString.trim
        val javaBody = if (rendered == "()") code"{}" else body
        code"() -> $javaBody"
      case jvm.TypedLambda1(paramType, param, body) =>
        // Java: (Type param) -> body
        code"(${renderTree(paramType, ctx)} $param) -> $body"
      case jvm.FieldGetterRef(rowType, field)        => code"$rowType::$field"
      case jvm.SelfNullary(name)                     => code"$name()" // Java: method call on implicit this
      case jvm.TypedFactoryCall(tpe, typeArgs, args) =>
        // Java: Type.<T1, T2>of(args)
        val typeArgStr = if (typeArgs.isEmpty) jvm.Code.Empty else code"<${typeArgs.map(t => renderTree(t, ctx)).mkCode(", ")}>"
        code"$tpe.$typeArgStr${jvm.Ident("of")}(${args.map(a => renderTree(a, ctx)).mkCode(", ")})"
      case jvm.Param(anns, cs, name, tpe, _)               => code"${renderComments(cs).getOrElse(jvm.Code.Empty)}${renderAnnotationsInline(anns)}$tpe $name"
      case jvm.QIdent(value)                               => value.map(i => renderTree(i, ctx)).mkCode(".")
      case jvm.StrLit(str) if str.contains(Quote)          => Quote + str.replace(Quote, "\\\"") + Quote
      case jvm.StrLit(str)                                 => Quote + str + Quote
      case jvm.Summon(_)                                   => sys.error("java doesn't support `summon`")
      case jvm.Type.Abstract(value)                        => value.code
      case jvm.Type.ArrayOf(value)                         => code"$value[]"
      case jvm.Type.Commented(underlying, comment)         => code"$comment $underlying"
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
      case jvm.RuntimeInterpolation(value)                 => value
      case jvm.IfExpr(pred, thenp, elsep) =>
        code"""|$pred
               |  ? $thenp
               |  : $elsep""".stripMargin
      case jvm.TypeSwitch(value, cases, nullCase, defaultCase) =>
        // In Java switch expressions: blocks don't need semicolon, expressions do
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
      case jvm.StringInterpolate(_, prefix, content) =>
        // For Java, determine if we need Fragment.lit() wrapping
        // - interpolate() needs Fragment.lit() for string parts (it expects Fragment varargs)
        // - str() is plain string concatenation and should NOT have Fragment.lit()
        val needsFragmentLit = prefix.value == "interpolate"
        val Fragment = jvm.Type.Qualified("typo.runtime.Fragment")
        val linearized = jvm.Code.linearize(content)
        val processedParts: List[jvm.Code] = linearized.map {
          case jvm.Code.Tree(jvm.RuntimeInterpolation(value)) =>
            // For str(), wrap in String.valueOf() to ensure String type
            // For interpolate(), pass Fragment values directly
            if (needsFragmentLit) value else code"${TypesJava.String}.valueOf($value)"
          case content =>
            val strLit: jvm.Code = content.render(this).lines match {
              case Array(one) if one.contains(Quote) || one.contains("\n") || one.contains("\r") =>
                code"""|$TripleQuote
                       |$one
                       |$TripleQuote""".stripMargin
              case Array(one) =>
                code"$Quote$one$Quote"
              case more =>
                val ret = more.iterator.zipWithIndex.map { case (line, _) =>
                  code"${" " * 3}$line"
                }
                jvm.Code.Combined(List(code"$TripleQuote", code"\n") ++ ret ++ List(code"$TripleQuote"))
            }
            // Only wrap in Fragment.lit() for interpolate(), not for str()
            if (needsFragmentLit) code"$Fragment.lit($strLit)" else strLit
        }

        // Ensure the interpolation ends with a lit() if needed
        val finalParts = linearized.lastOption match {
          case Some(jvm.Code.Tree(jvm.RuntimeInterpolation(_))) =>
            // If the last part is a runtime interpolation, add an empty string literal
            val emptyStr = if (needsFragmentLit) code"$Fragment.lit($Quote$Quote)" else code"$Quote$Quote"
            processedParts :+ emptyStr
          case _ =>
            processedParts
        }

        // For interpolate() calls with many arguments, use multi-line format for readability
        if (needsFragmentLit && finalParts.length > 1)
          code"""|$prefix(
                 |  ${finalParts.mkCode(",\n")}
                 |)""".stripMargin
        else
          jvm.Call(prefix, List(jvm.Call.ArgGroup(finalParts.map(jvm.Arg.Pos.apply), isImplicit = false)))

      case jvm.Given(annotations, tparams, name, implicitParams, tpe, body) =>
        val annotationsCode = renderAnnotations(annotations)
        if (tparams.isEmpty && implicitParams.isEmpty)
          code"""|$annotationsCode${ctx.public}$tpe $name =
                 |  $body""".stripMargin
        else {
          val paramsCode = renderParams(implicitParams, ctx)
          code"$annotationsCode${ctx.public}${renderTparams(tparams)} $tpe $name" ++ paramsCode ++ code"""| {
                |  return $body;
                |}""".stripMargin
        }
      case jvm.Value(annotations, name, tpe, None, _, _) =>
        val annotationsCode = renderAnnotations(annotations)
        code"$annotationsCode$tpe $name;"
      case jvm.Value(annotations, name, tpe, Some(body), isLazy, isOverride) =>
        val overrideAnnotation = if (isOverride) "@Override\n" else ""
        if (isLazy) {
          // In Java, lazy vals become methods
          code"""|$overrideAnnotation${ctx.public}$tpe $name() {
                 |  return $body;
                 |}""".stripMargin
        } else {
          val annotationsCode = renderAnnotations(annotations)
          code"$annotationsCode$tpe $name = $body;"
        }
      case jvm.Method(annotations, comments, tparams, name, params, implicitParams, tpe, throws, body) =>
        val annotationsCode = renderAnnotations(annotations)
        val commentCode = renderComments(comments).getOrElse(jvm.Code.Empty)
        val allParams = params ++ implicitParams
        val paramsCode = renderParams(allParams, ctx)
        val throwsCode = if (throws.isEmpty) jvm.Code.Empty else code" throws ${throws.map(t => renderTree(t, ctx)).mkCode(", ")}"
        // In interfaces, methods with bodies need 'default' keyword
        val defaultKeyword = if (ctx.inInterface && body.nonEmpty) "default " else ""
        val signature = annotationsCode ++ commentCode ++ code"$defaultKeyword${ctx.public}${renderTparams(tparams)}$tpe $name" ++ paramsCode ++ throwsCode

        (body: @unchecked) match {
          case Nil =>
            signature
          case List(one) =>
            val bodyCode = if (tpe == jvm.Type.Void) code"$one;" else code"return $one;"
            signature ++ code"""| {
                  |  $bodyCode
                  |}""".stripMargin

          case all @ init :+ last =>
            val returnBody =
              if (tpe == jvm.Type.Void)
                all.map(x => code"$x;").mkCode("\n")
              else
                code"""|${init.map(x => code"$x;").mkCode("\n")}
                     |return $last;""".stripMargin

            signature ++ code"""| {
                  |  $returnBody
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
        val body: List[jvm.Code] =
          cls.staticMembers.sortBy(_.name).map(x => code"static ${renderTree(x, memberCtx)}") ++ cls.members.sortBy(_.name).map(m => renderTree(m, memberCtx))

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
            renderTree(jvm.Method(Nil, param.comments, Nil, name, List(paramWithoutComment), Nil, clsType, Nil, List(body)), memberCtx)
          }

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
          annotationsCode,
          renderComments(cls.comments),
          Some(code"${ctx.public}record "),
          Some(cls.name.name.value),
          cls.tparams match {
            case Nil      => None
            case nonEmpty => Some(renderTparams(nonEmpty))
          },
          Some(renderParams(cls.params, ctx)),
          cls.implements match {
            case Nil      => None
            case nonEmpty => Some(nonEmpty.map(x => code" implements $x").mkCode(" "))
          },
          Some(code"""| {
                      |  ${(shortConstructor.toList ++ withers ++ body).map(_ ++ code";").mkCode("\n\n")}
                      |}""".stripMargin)
        ).flatten.mkCode("")
      case sum: jvm.Adt.Sum =>
        // Inside a sealed interface, public is implied for all members including nested types
        val memberCtx = ctx.withPublic
        val body: List[jvm.Code] =
          List(
            sum.flattenedSubtypes.sortBy(_.name).map(t => renderTree(t, memberCtx)),
            sum.staticMembers.sortBy(_.name).map(x => code"static ${renderTree(x, memberCtx)}"),
            sum.members.sortBy(_.name).map(m => renderTree(m, memberCtx))
          ).flatten

        val annotationsCode = if (sum.annotations.isEmpty) None else Some(renderAnnotations(sum.annotations))
        List[Option[jvm.Code]](
          annotationsCode,
          renderComments(sum.comments),
          Some(code"${ctx.public}sealed interface "),
          Some(sum.name.name.value),
          sum.tparams match {
            case Nil      => None
            case nonEmpty => Some(renderTparams(nonEmpty))
          },
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
        // Sort fields before methods for proper Java class structure
        val sortedMembers = cls.members.sortBy {
          case _: jvm.Value => 0
          case _            => 1
        }
        val body: List[jvm.Code] =
          cls.staticMembers.sortBy(_.name).map(x => code"static ${renderTree(x, memberCtx)}") ++ sortedMembers.map(m => renderTree(m, memberCtx))

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
          annotationsCode,
          renderComments(cls.comments),
          Some(code"${ctx.public}"),
          cls.classType match {
            case jvm.ClassType.Class     => Some(code"class ")
            case jvm.ClassType.Interface => Some(code"interface ")
          },
          Some(cls.name.name.value),
          cls.tparams match {
            case Nil      => None
            case nonEmpty => Some(renderTparams(nonEmpty))
          },
          cls.`extends`.map(x => code" extends $x"),
          cls.implements match {
            case Nil      => None
            case nonEmpty => Some(nonEmpty.map(x => code" implements $x").mkCode(" "))
          },
          Some {
            val allBody = fieldsCode ++ constructorCode ++ body
            code"""| {
                   |  ${allBody.map(c => c ++ code";").mkCode("\n\n")}
                   |}""".stripMargin
          }
        ).flatten.mkCode("")
      case ann: jvm.Annotation => renderAnnotation(ann)

      // Anonymous class: new Interface() { members }
      case jvm.NewWithBody(tpe, members) =>
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
    val bijection = jvm.Type.dsl.Bijection
    code"$bijection.of($getter, $constructor)"
  }

  // Java: (row, value) -> row.withFieldName(value)
  override def rowSetter(fieldName: jvm.Ident): jvm.Code = {
    val capitalizedName = fieldName.value.capitalize
    jvm.Lambda2(jvm.Ident("row"), jvm.Ident("value"), code"row.with$capitalizedName(value)")
  }

  // Java: for (var elem : array) { body }
  override def arrayForEach(array: jvm.Code, elemVar: jvm.Ident, body: jvm.Code): jvm.Code =
    code"for (var $elemVar : $array) { $body }"

  // Java: arrayMap.map(array, mapper, targetClass)
  override def arrayMap(array: jvm.Code, mapper: jvm.Code, targetClass: jvm.Code): jvm.Code = {
    val arrayMapHelper = jvm.Type.Qualified("typo.runtime.internal.arrayMap")
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

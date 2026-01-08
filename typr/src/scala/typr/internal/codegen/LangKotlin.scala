package typr
package internal
package codegen

import typr.jvm.Type
import typr.jvm.Code.TreeOps

case class LangKotlin(typeSupport: TypeSupport) extends Lang {
  override val `;` : jvm.Code = jvm.Code.Empty // Kotlin doesn't need semicolons
  override val dsl: DslQualifiedNames = DslQualifiedNames.Kotlin

  // Type system types - Kotlin uses Kotlin's type system
  override val nothingType: jvm.Type = TypesKotlin.Nothing
  override val voidType: jvm.Type = TypesKotlin.Unit
  override val topType: jvm.Type = TypesKotlin.Any

  def s(content: jvm.Code): jvm.Code = {
    // Kotlin has string interpolation, but we build concatenation to avoid creating StringInterpolate nodes
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
        jvm.StrLit(other.render(this).asString).code
    }

    // Build string concatenation: "lit" + value + "lit" + ...
    parts match {
      case Nil           => jvm.StrLit("").code
      case single :: Nil => single
      case multiple      => multiple.reduce((a, b) => code"$a + $b")
    }
  }

  def docLink(cls: jvm.QIdent, value: jvm.Ident): String =
    s"Points to [${cls.dotName}.${value.value}]"

  // don't generate imports for these
  override val BuiltIn: Map[jvm.Ident, jvm.Type.Qualified] =
    Set(
      TypesKotlin.Any,
      TypesKotlin.Array,
      TypesKotlin.Boolean,
      TypesKotlin.Byte,
      TypesKotlin.ByteArray,
      TypesKotlin.Char,
      TypesKotlin.Double,
      TypesKotlin.Float,
      TypesKotlin.Int,
      TypesKotlin.Long,
      TypesKotlin.Short,
      TypesKotlin.String,
      TypesKotlin.Unit
    )
      .map(x => (x.value.name, x))
      .toMap

  override def extension: String = "kt"

  override def rowSetter(fieldName: jvm.Ident): jvm.Code =
    jvm.Lambda("row", "value", jvm.Body.Expr(code"row.copy($fieldName = value)"))

  override def arrayForEach(array: jvm.Code, elemVar: jvm.Ident, body: jvm.Body): jvm.Code =
    body match {
      case jvm.Body.Expr(expr)   => code"for ($elemVar in $array) { $expr }"
      case jvm.Body.Stmts(stmts) => code"for ($elemVar in $array) {\n  ${stmts.mkCode("\n  ")}\n}"
      case jvm.Body.Abstract     => code"for ($elemVar in $array) {}"
    }

  override def arrayMap(array: jvm.Code, mapper: jvm.Code, targetClass: jvm.Code): jvm.Code =
    code"${FoundationsTypes.internal.arrayMap}.map($array, $mapper, $targetClass)"

  override def ternary(condition: jvm.Code, thenExpr: jvm.Code, elseExpr: jvm.Code): jvm.Code =
    code"if ($condition) $thenExpr else $elseExpr"

  override def newArray(elementType: jvm.Type, length: Option[jvm.Code]): jvm.Code =
    length match {
      case Some(len) => code"arrayOfNulls<$elementType>($len)"
      case None      => code"{ size -> arrayOfNulls<$elementType>(size) }"
    }

  // Kotlin: ByteArray(size)
  override def newByteArray(size: jvm.Code): jvm.Code =
    code"ByteArray($size)"

  // Kotlin: arr.size (property access)
  override def byteArrayLength(arr: jvm.Code): jvm.Code =
    code"$arr.size"

  // Kotlin: ByteArray (distinct primitive array type, not Array<Byte>)
  override val ByteArrayType: jvm.Type = TypesKotlin.ByteArray

  // Kotlin uses MAX_VALUE for max value constants
  // Byte/Short need .toInt() for use with nextInt(Int) bounds
  override def maxValue(tpe: jvm.Type): jvm.Code = tpe match {
    case TypesKotlin.Byte | TypesKotlin.Short => code"$tpe.MAX_VALUE.toInt()"
    case _                                    => code"$tpe.MAX_VALUE"
  }

  // Kotlin: Array(size) { factory }
  override def arrayFill(size: jvm.Code, factory: jvm.Code, elementType: jvm.Type): jvm.Code =
    code"Array($size) { $factory }"

  // Kotlin uses primitive arrays like ShortArray, FloatArray for performance
  override def shortArrayFill(size: jvm.Code, factory: jvm.Code): jvm.Code =
    code"ShortArray($size) { $factory }"

  override def floatArrayFill(size: jvm.Code, factory: jvm.Code): jvm.Code =
    code"FloatArray($size) { $factory }"

  // Kotlin annotations use KClass (::class), not Class (::class.java)
  override def annotationClassRef(tpe: jvm.Type): jvm.Code = {
    def stripTypeParams(t: jvm.Type): jvm.Type = t match {
      case jvm.Type.TApply(underlying, _) => stripTypeParams(underlying)
      case other                          => other
    }
    code"${stripTypeParams(tpe)}::class"
  }

  override def propertyGetterAccess(target: jvm.Code, name: jvm.Ident): jvm.Code =
    code"$target.$name"

  override def overrideValueAccess(target: jvm.Code, name: jvm.Ident): jvm.Code =
    code"$target.$name()"

  override def nullaryMethodCall(target: jvm.Code, name: jvm.Ident): jvm.Code =
    code"$target.$name()"

  override def arrayOf(elements: List[jvm.Code]): jvm.Code =
    code"arrayOf<Any?>(${elements.mkCode(", ")})"

  override def typedArrayOf(elementType: jvm.Type, elements: List[jvm.Code]): jvm.Code =
    code"arrayOf<$elementType>(${elements.mkCode(", ")})"

  override def equals(left: jvm.Code, right: jvm.Code): jvm.Code =
    code"($left == $right)"

  override def notEquals(left: jvm.Code, right: jvm.Code): jvm.Code =
    code"($left != $right)"

  override def needsExplicitNullCheck: Boolean = false

  override def toShort(expr: jvm.Code): jvm.Code = code"$expr.toShort()"

  override def toByte(expr: jvm.Code): jvm.Code = code"$expr.toByte()"

  override def toLong(expr: jvm.Code): jvm.Code = code"$expr.toLong()"

  override def enumAll(enumType: jvm.Type): jvm.Code = code"$enumType.entries"

  override def castFromObject(targetType: jvm.Type, expr: jvm.Code): jvm.Code =
    code"($expr as $targetType)"

  val Quote = '"'.toString
  val TripleQuote = Quote * 3

  case class RenderCtx(publicImplied: Boolean, staticImplied: Boolean) {
    def withPublic: RenderCtx = copy(publicImplied = true)
    def withStatic: RenderCtx = copy(staticImplied = true)
  }
  override type Ctx = RenderCtx
  override object Ctx extends CtxCompanion {
    val Empty: RenderCtx = RenderCtx(publicImplied = false, staticImplied = false)
  }

  override def renderTree(tree: jvm.Tree, ctx: Ctx): jvm.Code =
    tree match {
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
      case jvm.IgnoreResult(expr)        => expr
      case jvm.NotNull(expr)             => code"$expr!!"
      case jvm.ConstructorMethodRef(tpe) => code"::$tpe"
      case jvm.ClassOf(tpe) =>
        def stripTypeParams(t: jvm.Type): jvm.Type = t match {
          case jvm.Type.TApply(underlying, _) => stripTypeParams(underlying)
          case other                          => other
        }
        val stripped = stripTypeParams(tpe)
        // For Kotlin primitive types, use javaObjectType to get boxed class (e.g., Integer.class instead of int.class)
        // This is needed because Array.newInstance with primitive class creates primitive arrays which can't be cast to Object[]
        val kotlinPrimitives = Set("Int", "Long", "Short", "Byte", "Double", "Float", "Boolean", "Char")
        val typeName = stripped match {
          case jvm.Type.Qualified(q) => q.idents.lastOption.map(_.value).getOrElse("")
          case _                     => ""
        }
        if (kotlinPrimitives.contains(typeName))
          code"$stripped::class.javaObjectType"
        else
          code"$stripped::class.java"
      case jvm.JavaClassOf(tpe) =>
        def stripTypeParams(t: jvm.Type): jvm.Type = t match {
          case jvm.Type.TApply(underlying, _) => stripTypeParams(underlying)
          case other                          => other
        }
        code"${stripTypeParams(tpe)}::class.java"
      case jvm.Call(target, argGroups) =>
        val allArgs = argGroups.flatMap(_.args)
        code"$target(${allArgs.map(a => renderTree(a, ctx)).mkCode(", ")})"
      case jvm.Apply0(jvm.Ref(name, jvm.Type.Function0(jvm.Type.Void)))                   => code"$name()"
      case jvm.Apply0(jvm.Ref(name, jvm.Type.Function0(_)))                               => code"$name()"
      case jvm.Apply1(jvm.Ref(name, jvm.Type.Function1(_, jvm.Type.Void)), arg1)          => code"$name($arg1)"
      case jvm.Apply1(jvm.Ref(name, jvm.Type.Function1(_, _)), arg1)                      => code"$name($arg1)"
      case jvm.Apply2(jvm.Ref(name, jvm.Type.Function2(_, _, jvm.Type.Void)), arg1, arg2) => code"$name($arg1, $arg2)"
      case jvm.Apply2(jvm.Ref(name, jvm.Type.Function2(_, _, _)), arg1, arg2)             => code"$name($arg1, $arg2)"
      case jvm.Select(target, name)                                                       => code"$target.$name"
      case jvm.ArrayIndex(target, num)                                                    => code"$target[$num]"
      // In Kotlin, interface methods need parentheses (unlike Scala).
      // Data class properties should use Select instead of ApplyNullary.
      case jvm.ApplyNullary(target, name)                            => code"$target.$name()"
      case jvm.Arg.Named(name, value)                                => code"$name = $value"
      case jvm.Arg.Pos(value)                                        => value
      case jvm.Ident(value)                                          => escapedIdent(value)
      case jvm.MethodRef(tpe, name)                                  => code"$tpe::$name"
      case jvm.New(target, args)                                     => code"$target(${args.map(a => renderTree(a, ctx)).mkCode(", ")})"
      case jvm.LocalVar(name, tpe, value)                            => tpe.fold(code"val $name = $value")(t => code"val $name: $t = $value")
      case jvm.InferredTargs(target)                                 => target
      case jvm.GenericMethodCall(target, methodName, typeArgs, args) =>
        // Kotlin: target.method<T1, T2>(args)
        val typeArgStr = if (typeArgs.isEmpty) jvm.Code.Empty else code"<${typeArgs.map(t => renderTree(t, ctx)).mkCode(", ")}>"
        val argStr = if (args.isEmpty) code"()" else code"(${args.map(a => renderTree(a, ctx)).mkCode(", ")})"
        code"$target.$methodName$typeArgStr$argStr"
      case jvm.Return(expr)                   => code"return $expr"
      case jvm.Throw(expr)                    => code"throw $expr"
      case jvm.Stmt(stmtCode, needsSemicolon) => if (needsSemicolon) code"$stmtCode;" else stmtCode
      case jvm.Lambda(params, body) =>
        val paramsCode = params match {
          case Nil                                    => jvm.Code.Empty
          case List(jvm.LambdaParam(name, None))      => code"$name -> "
          case List(jvm.LambdaParam(name, Some(tpe))) => code"$name: $tpe -> "
          case _ if params.forall(_.tpe.isEmpty)      => code"${params.map(p => p.name.code).mkCode(", ")} -> "
          case _                                      => code"${params.map(p => p.tpe.fold(code"${p.name}")(t => code"${p.name}: $t")).mkCode(", ")} -> "
        }
        code"{ $paramsCode${renderBody(body)} }"
      case jvm.SamLambda(samType, lambda) =>
        // Kotlin SAM conversion can be unreliable with Java SAM interfaces due to overload resolution.
        // Use anonymous object implementation instead for better compatibility.
        // object : FunctionType { override fun apply(param): ReturnType = body }
        // Type may be shortened (e.g., java.util.function.Function -> Function), so check the simple name
        val (methodName, returnType) = samType match {
          case jvm.Type.TApply(jvm.Type.Qualified(base), _ :: ret :: Nil) if base.name.value == "Function" =>
            ("apply", ret)
          case jvm.Type.TApply(jvm.Type.Qualified(base), ret :: Nil) if base.name.value == "Supplier" =>
            ("get", ret)
          case _ =>
            // Fallback to SAM conversion syntax for unknown types
            val baseType = samType match {
              case jvm.Type.TApply(underlying, _) => underlying
              case other                          => other
            }
            val lambdaCode = renderTree(lambda, ctx)
            return code"$baseType $lambdaCode"
        }
        val params = lambda.params.map(p => code"${p.name}: ${p.tpe.getOrElse(sys.error("SamLambda param must have type"))}").mkCode(", ")
        val bodyCode = renderBody(lambda.body)
        code"object : $samType { override fun $methodName($params): $returnType = $bodyCode }"
      case jvm.Cast(targetType, expr) =>
        // Kotlin cast: expr as Type
        code"($expr as $targetType)"
      case jvm.FieldGetterRef(rowType, field) => code"$rowType::$field"
      case jvm.Param(_, cs, name, tpe, default) =>
        val defaultCode = default.map(d => code" = $d").getOrElse(jvm.Code.Empty)
        code"${renderComments(cs).getOrElse(jvm.Code.Empty)}$name: $tpe$defaultCode"
      case jvm.QIdent(value) => value.map(i => renderTree(i, ctx)).mkCode(".")
      case jvm.StrLit(str) =>
        val escaped = str
          .replace("\\", "\\\\")
          .replace("\"", "\\\"")
          .replace("\n", "\\n")
          .replace("\r", "\\r")
          .replace("\t", "\\t")
        Quote + escaped + Quote
      case jvm.Summon(_) => sys.error("kotlin doesn't support `summon`")
      case jvm.Type.Abstract(value, variance) =>
        variance match {
          case jvm.Variance.Invariant     => value.code
          case jvm.Variance.Covariant     => code"out $value"
          case jvm.Variance.Contravariant => code"in $value"
        }
      case jvm.Type.ArrayOf(value)                         => code"Array<$value>"
      case jvm.Type.KotlinNullable(underlying)             => code"$underlying?"
      case jvm.Type.Commented(underlying, comment)         => code"$comment $underlying"
      case jvm.Type.Annotated(underlying, _)               => renderTree(underlying, ctx) // Kotlin doesn't support Scala-style type annotations
      case jvm.Type.Function0(jvm.Type.Void)               => code"() -> Unit"
      case jvm.Type.Function0(targ)                        => code"() -> $targ"
      case jvm.Type.Function1(targ, jvm.Type.Void)         => code"($targ) -> Unit"
      case jvm.Type.Function1(targ, ret)                   => code"($targ) -> $ret"
      case jvm.Type.Function2(targ1, targ2, jvm.Type.Void) => code"($targ1, $targ2) -> Unit"
      case jvm.Type.Function2(targ1, targ2, ret)           => code"($targ1, $targ2) -> $ret"
      case jvm.Type.Qualified(value)                       =>
        // Map Java types to Kotlin equivalents
        value.dotName match {
          case "java.lang.String"    => code"String"
          case "java.lang.Boolean"   => code"Boolean"
          case "java.lang.Integer"   => code"Int"
          case "java.lang.Long"      => code"Long"
          case "java.lang.Short"     => code"Short"
          case "java.lang.Byte"      => code"Byte"
          case "java.lang.Float"     => code"Float"
          case "java.lang.Double"    => code"Double"
          case "java.lang.Character" => code"Char"
          case "java.lang.Object"    => code"Any"
          case _                     => value.code
        }
      case jvm.Type.TApply(underlying, targs) => code"$underlying<${targs.map(t => renderTree(t, ctx)).mkCode(", ")}>"
      case jvm.Type.UserDefined(underlying)   => code"/* user-picked */ $underlying"
      case jvm.Type.Void                      => code"Unit"
      case jvm.Type.Wildcard                  => code"*"
      case jvm.Type.Primitive(name)           => name
      case jvm.RuntimeInterpolation(value)    => value
      case jvm.Import(_, _)                   => jvm.Code.Empty // Import node just triggers import, no code output
      case jvm.IfExpr(pred, thenp, elsep) =>
        code"(if ($pred) $thenp else $elsep)"
      case jvm.TypeSwitch(value, cases, nullCase, defaultCase, _) =>
        // Use `when (val __r = value)` to bind value once and avoid double evaluation
        // Note: unchecked flag is Scala-only (for @unchecked annotation), ignored in Kotlin
        val boundIdent = jvm.Ident("__r")
        val nullCaseCode = nullCase.map(body => code"null -> $body").toList
        val typeCases = cases.map { case jvm.TypeSwitch.Case(pat, ident, body) =>
          // Convert type to wildcard version for pattern matching and cast
          // Ok<T> -> Ok<*>, Response2004XX5XX<T> -> Response2004XX5XX<*>
          val wildcardPat = toWildcardType(pat)
          // If body starts with {, unwrap it and merge with cast assignment
          val bodyStr = body.render(this).asString.trim
          if (bodyStr.startsWith("{") && bodyStr.endsWith("}")) {
            val innerBody = bodyStr.drop(1).dropRight(1).trim
            code"""|is $wildcardPat -> {
                   |  val $ident = $boundIdent as $wildcardPat
                   |  $innerBody
                   |}""".stripMargin
          } else {
            code"is $wildcardPat -> { val $ident = $boundIdent as $wildcardPat; $body }"
          }
        }
        val defaultCaseCode = defaultCase.map(body => code"else -> $body").toList
        val allCases = nullCaseCode ++ typeCases ++ defaultCaseCode
        code"""|when (val $boundIdent = $value) {
               |  ${allCases.mkCode("\n")}
               |}""".stripMargin
      case jvm.StringInterpolate(_, _, _) =>
        // StringInterpolate should never reach LangKotlin
        // DbLibFoundations.SQL() should have already rewritten it to Fragment.interpolate() calls
        sys.error("StringInterpolate should not reach LangKotlin. DbLibFoundations.SQL() should have rewritten it to Fragment.interpolate() calls.")

      case jvm.Given(annotations, tparams, name, implicitParams, tpe, body) =>
        val annotationsCode = renderAnnotations(annotations)
        if (tparams.isEmpty && implicitParams.isEmpty)
          code"""|${annotationsCode}val $name: $tpe =
                 |  $body""".stripMargin
        else {
          val paramsCode = renderParams(implicitParams, ctx)
          code"${annotationsCode}fun ${renderTparams(tparams)} $name" ++ paramsCode ++ code"""|: $tpe {
                |  return $body
                |}""".stripMargin
        }
      case jvm.Value(_, name, tpe, None, _, isOverride, _) =>
        val overrideMod = if (isOverride) "override " else ""
        // In Kotlin, override of Java methods must be fun, not val
        if (isOverride)
          code"${overrideMod}fun $name(): $tpe"
        else
          code"${overrideMod}abstract val $name: $tpe"
      case jvm.Value(_, name, tpe, Some(body), _, isOverride, _) =>
        val overrideMod = if (isOverride) "override " else ""
        // In Kotlin, override of Java methods must be fun, not val
        if (isOverride)
          code"${overrideMod}fun $name(): $tpe = $body"
        else
          code"${overrideMod}val $name: $tpe = $body"
      case jvm.Method(annotations, comments, tparams, name, params, implicitParams, tpe, throws, body, isOverride, _) =>
        val overrideMod = if (isOverride) "override " else ""
        // In Kotlin, abstract methods in abstract classes need the 'abstract' modifier
        // Even when overriding, if the body is abstract, we need the modifier
        val abstractMod = if (body == jvm.Body.Abstract) "abstract " else ""
        val annotationsCode = renderAnnotations(annotations)
        val throwsCode = throws.map(th => code"@Throws($th::class)\n").mkCode("")
        val commentCode = renderComments(comments).getOrElse(jvm.Code.Empty)
        val allParams = params ++ implicitParams
        // Kotlin override methods can't specify default values - they inherit from the interface
        val effectiveParams = if (isOverride) allParams.map(_.copy(default = None)) else allParams
        val paramsCode = renderParams(effectiveParams, ctx)
        val returnType = if (tpe == jvm.Type.Void) jvm.Code.Empty else code": $tpe"
        val signature = commentCode ++ annotationsCode ++ throwsCode ++ code"${abstractMod}${overrideMod}fun ${renderTparams(tparams)}$name" ++ paramsCode ++ returnType

        body match {
          case jvm.Body.Abstract =>
            signature
          case jvm.Body.Expr(expr) if tpe == jvm.Type.Void =>
            signature ++ code"""| {
                  |  $expr
                  |}""".stripMargin
          case jvm.Body.Expr(expr) =>
            signature ++ code" = $expr"
          case jvm.Body.Stmts(stmts) =>
            signature ++ code"""| {
                  |  ${stmts.map(s => renderStmt(s)).mkCode("\n")}
                  |}""".stripMargin
        }
      case enm: jvm.Enum =>
        code"""|enum class ${enm.tpe.name}(val value: ${TypesKotlin.String}) {
               |    ${enm.values.map { case (name, expr) => code"$name($expr)" }.mkCode(",\n")};
               |
               |    companion object {
               |        val Names: ${TypesKotlin.String} = entries.joinToString(", ") { it.value }
               |        val ByName: ${TypesKotlin.Map}<${TypesKotlin.String}, ${enm.tpe}> = entries.associateBy { it.value }
               |        ${enm.staticMembers.map(t => code"$t").mkCode("\n")}
               |
               |        fun force(str: ${TypesKotlin.String}): ${enm.tpe.name} =
               |            ByName[str] ?: throw RuntimeException("'$$str' does not match any of the following legal values: $$Names")
               |    }
               |}
               |""".stripMargin
      case enm: jvm.OpenEnum =>
        code"""|sealed interface ${enm.tpe.name} {
               |    val value: ${enm.underlyingType}
               |
               |    data class Unknown(override val value: ${enm.underlyingType}) : ${enm.tpe.name}
               |
               |    enum class Known(override val value: ${enm.underlyingType}) : ${enm.tpe.name} {
               |        ${enm.values.map { case (name, expr) => code"$name($expr)" }.mkCode(",\n")};
               |
               |        companion object {
               |            val ByName: ${TypesKotlin.Map}<${enm.underlyingType}, Known> = entries.associateBy { it.value }
               |        }
               |    }
               |
               |    companion object {
               |        fun apply(str: ${enm.underlyingType}): ${enm.tpe.name} =
               |            Known.ByName[str] ?: Unknown(str)
               |        ${enm.staticMembers.map(x => code"$x").mkCode("\n")}
               |    }
               |}""".stripMargin
      case cls: jvm.Adt.Record =>
        val memberCtx = Ctx.Empty
        val body: List[jvm.Code] =
          cls.members.sortBy(_.name).map(m => renderTree(m, memberCtx))

        val staticMembers = cls.staticMembers.sortBy(_.name).map(x => renderTree(x, memberCtx))

        // For wrapper types with a single value field, generate toString() that returns just the value
        val toStringMethod: Option[jvm.Code] =
          if (cls.isWrapper && cls.params.size == 1) {
            val valueParam = cls.params.head
            // Unwrap to base type (removes comments, annotations, etc.) to check if it's String
            val baseTpe = jvm.Type.base(valueParam.tpe)
            val toStringBody =
              if (baseTpe == TypesKotlin.String) code"return ${valueParam.name}"
              else code"return ${valueParam.name}.toString()"
            Some(
              renderTree(
                jvm.Method(Nil, jvm.Comments.Empty, Nil, jvm.Ident("toString"), Nil, Nil, TypesKotlin.String, Nil, jvm.Body.Stmts(List(toStringBody)), isOverride = true, isDefault = false),
                memberCtx
              )
            )
          } else None

        // Kotlin data classes have named parameters for copy(), so withers are unnecessary

        // Kotlin data classes must have at least one constructor parameter.
        // If no params: use object (if no type params) or class (if type params)
        if (cls.params.isEmpty) {
          val companionBody = if (staticMembers.nonEmpty) {
            Some(code"""|companion object {
                        |  ${staticMembers.mkCode("\n\n")}
                        |}""".stripMargin)
          } else None

          val allBody = body ++ toStringMethod.toList ++ companionBody.toList

          val classKeyword = if (cls.tparams.isEmpty) "object" else "class"

          List[Option[jvm.Code]](
            Some(renderAnnotations(cls.annotations)),
            renderComments(cls.comments),
            Some(code"$classKeyword "),
            Some(cls.name.name.value),
            cls.tparams match {
              case Nil      => None
              case nonEmpty => Some(renderTparams(nonEmpty))
            },
            cls.implements match {
              case Nil      => None
              case nonEmpty => Some(code" : " ++ nonEmpty.map(x => code"$x").mkCode(", "))
            },
            if (allBody.nonEmpty)
              Some(code"""| {
                          |  ${allBody.mkCode("\n\n")}
                          |}""".stripMargin)
            else None
          ).flatten.mkCode("")
        } else {
          // Kotlin uses default parameters directly in primary constructor, no secondary constructor needed
          val companionBody = if (staticMembers.nonEmpty) {
            Some(code"""|companion object {
                        |  ${staticMembers.mkCode("\n\n")}
                        |}""".stripMargin)
          } else None

          val allBody = body ++ toStringMethod.toList ++ companionBody.toList

          // Constructor annotations render as: data class Name @Annotation constructor(params)
          // Private constructor renders as: data class Name private constructor(params)
          val constructorModifier = if (cls.privateConstructor) " private constructor" else ""
          val constructorAnnotationsCode = if (cls.constructorAnnotations.nonEmpty) {
            Some(code" ${cls.constructorAnnotations.map(renderAnnotation).mkCode(" ")}$constructorModifier")
          } else if (cls.privateConstructor) {
            Some(code"$constructorModifier")
          } else None

          // Add @ConsistentCopyVisibility when privateConstructor is true to avoid Kotlin 2.2+ error
          val allAnnotations = if (cls.privateConstructor) {
            val consistentCopyVisibility = jvm.Annotation(jvm.Type.Qualified("kotlin.ConsistentCopyVisibility"), Nil)
            consistentCopyVisibility :: cls.annotations
          } else cls.annotations

          List[Option[jvm.Code]](
            Some(renderAnnotations(allAnnotations)),
            renderComments(cls.comments),
            Some(code"data class "),
            Some(cls.name.name.value),
            cls.tparams match {
              case Nil      => None
              case nonEmpty => Some(renderTparams(nonEmpty))
            },
            constructorAnnotationsCode,
            Some(renderDataClassParams(cls.params, ctx)),
            cls.implements match {
              case Nil      => None
              case nonEmpty => Some(code" : " ++ nonEmpty.map(x => code"$x").mkCode(", "))
            },
            if (allBody.nonEmpty)
              Some(code"""| {
                          |  ${allBody.mkCode("\n\n")}
                          |}""".stripMargin)
            else None
          ).flatten.mkCode("")
        }
      case sum: jvm.Adt.Sum =>
        val memberCtx = ctx.withPublic
        // In Kotlin, static members must be in a companion object
        val companionBody = sum.staticMembers.sortBy(_.name).map(x => renderTree(x, memberCtx.withStatic))
        val companion: Option[jvm.Code] =
          if (companionBody.nonEmpty) {
            Some(
              code"""|companion object {
                     |  ${companionBody.mkCode("\n\n")}
                     |}""".stripMargin
            )
          } else None

        val body: List[jvm.Code] =
          List(
            sum.flattenedSubtypes.sortBy(_.name).map(t => renderTree(t, memberCtx)),
            companion.toList,
            sum.members.sortBy(_.name).map(m => renderTree(m, memberCtx))
          ).flatten

        List[Option[jvm.Code]](
          Some(renderAnnotations(sum.annotations)),
          renderComments(sum.comments),
          Some(code"sealed interface "),
          Some(sum.name.name.value),
          sum.tparams match {
            case Nil      => None
            case nonEmpty => Some(renderTparams(nonEmpty))
          },
          sum.implements match {
            case Nil      => None
            case nonEmpty => Some(nonEmpty.map(x => code" : $x").mkCode(", "))
          },
          Some(code"""| {
                      |  ${body.mkCode("\n\n")}
                      |}""".stripMargin)
        ).flatten.mkCode("")
      // Annotation: @Annotation(args)
      case ann: jvm.Annotation => renderAnnotation(ann)

      // Annotation array: Kotlin uses [ a, b ]
      // Nested annotations don't have @ prefix
      case jvm.AnnotationArray(elements) =>
        val renderedElements = elements.map {
          case jvm.Code.Tree(ann: jvm.Annotation) => renderNestedAnnotation(ann)
          case other                              => other
        }
        code"[${renderedElements.mkCode(", ")}]"

      // Anonymous class: object : Type { members }
      // In Kotlin: classes need (), interfaces don't
      case jvm.NewWithBody(extendsClass, implementsInterface, members) =>
        val typeClause = (extendsClass, implementsInterface) match {
          case (Some(cls), Some(iface)) => code"$cls(), $iface"
          case (Some(cls), None)        => code"$cls()"
          case (None, Some(iface))      => code"$iface"
          case (None, None)             => jvm.Code.Empty
        }
        if (members.isEmpty) code"object : $typeClause {}"
        else {
          val memberCtx = Ctx.Empty
          code"""|object : $typeClause {
                 |  ${members.map(m => renderTree(m, memberCtx)).mkCode("\n")}
                 |}""".stripMargin
        }

      // Nested class: class Name(params) : Parent(args) { ... }
      case cls: jvm.NestedClass =>
        val privateMod = if (cls.isPrivate) "private " else ""
        val extendsClause = cls.`extends`.map(parent => code" : $parent(${cls.superArgs.map(a => renderTree(a, ctx)).mkCode(", ")})").getOrElse(jvm.Code.Empty)
        val memberCtx = Ctx.Empty
        // Use regular class when extending (avoids copy() conflict with data class)
        val hasExtends = cls.`extends`.isDefined
        val classKeyword = if (hasExtends) "class" else "data class"
        // When extending Java class, don't add val - just pass params to super constructor
        val paramsCode = renderDataClassParams(cls.params, memberCtx, addVal = !hasExtends)
        val membersCode =
          if (cls.members.isEmpty) jvm.Code.Empty
          else
            code"""|
               |  ${cls.members.map(m => renderTree(m, memberCtx)).mkCode("\n\n")}
               |""".stripMargin
        code"$privateMod$classKeyword ${cls.name}$paramsCode$extendsClause {$membersCode}"

      // Nested record: in Kotlin, becomes data class Name(params) : Interface, SuperClass() { ... }
      case rec: jvm.NestedRecord =>
        val memberCtx = Ctx.Empty
        val paramsCode = renderDataClassParams(rec.params, memberCtx, addVal = true)
        val implementsClause = rec.implements match {
          case Nil      => jvm.Code.Empty
          case nonEmpty =>
            // Logical order in jvm model is preserved (Fields first, then RelationStructure)
            // Both are interfaces now (no parent class), so no () needed
            // Kotlin syntax: Interface1, Interface2 (no parentheses for interfaces)
            val rendered = nonEmpty.map(tpe => renderTree(tpe, memberCtx))
            code" : ${rendered.mkCode(", ")}"
        }
        val membersCode =
          if (rec.members.isEmpty) jvm.Code.Empty
          else
            code"""|
               |  ${rec.members.map(m => renderTree(m, memberCtx)).mkCode("\n\n")}
               |""".stripMargin
        code"data class ${rec.name}$paramsCode$implementsClause {$membersCode}"

      // By-name becomes lambda in Kotlin
      case jvm.ByName(body) =>
        // Kotlin: { body } - wrap in lambda
        code"{ ${renderBody(body)} }"

      // Self-nullary: method call on implicit this (Kotlin needs parentheses)
      case jvm.SelfNullary(name) => code"$name()"

      // TypedFactoryCall: Type<T1, T2>(args) or Type.of<T1, T2>(args)
      case jvm.TypedFactoryCall(tpe, typeArgs, args) =>
        // Kotlin: Type<T1, T2>(args) - use constructor syntax
        val typeArgStr = if (typeArgs.isEmpty) jvm.Code.Empty else code"<${typeArgs.map(t => renderTree(t, ctx)).mkCode(", ")}>"
        code"$tpe$typeArgStr(${args.map(a => renderTree(a, ctx)).mkCode(", ")})"

      case cls: jvm.Class =>
        val memberCtx = if (cls.classType == jvm.ClassType.Interface) ctx.withPublic else ctx
        val regularMembers: List[jvm.Code] = cls.members.sortBy(_.name).map(m => renderTree(m, memberCtx))
        val staticMembers: List[jvm.Code] = cls.staticMembers.sortBy(_.name).map(x => renderTree(x, memberCtx))

        // In Kotlin, ALL static members must go in companion object (for any class type)
        val body: List[jvm.Code] = if (staticMembers.nonEmpty) {
          regularMembers :+ code"""|companion object {
                                   |  ${staticMembers.mkCode("\n\n")}
                                   |}""".stripMargin
        } else {
          regularMembers
        }

        // In Kotlin, data classes must have at least one constructor parameter
        // If no params: use regular class instead of data class
        val classKeyword = cls.classType match {
          case jvm.ClassType.Class if cls.params.isEmpty => "class "
          case jvm.ClassType.Class                       => "data class "
          case jvm.ClassType.AbstractClass               => "abstract class "
          case jvm.ClassType.Interface                   => "interface "
        }

        // In Kotlin, extends comes first with () for constructor call (for classes), then implements
        // Interfaces don't need () when extending other interfaces
        val isInterface = cls.classType == jvm.ClassType.Interface
        val extendsAndImplements: Option[jvm.Code] = (cls.`extends`, cls.implements) match {
          case (None, Nil) => None
          case (Some(ext), Nil) =>
            if (isInterface) Some(code" : $ext")
            else Some(code" : $ext()")
          case (None, impls) => Some(code" : " ++ impls.map(x => code"$x").mkCode(", "))
          case (Some(ext), impls) =>
            if (isInterface) Some(code" : $ext, " ++ impls.map(x => code"$x").mkCode(", "))
            else Some(code" : $ext(), " ++ impls.map(x => code"$x").mkCode(", "))
        }

        List[Option[jvm.Code]](
          renderComments(cls.comments),
          Some(classKeyword),
          Some(cls.name.name.value),
          cls.tparams match {
            case Nil      => None
            case nonEmpty => Some(renderTparams(nonEmpty))
          },
          cls.classType match {
            case jvm.ClassType.Class if cls.params.nonEmpty         => Some(renderDataClassParams(cls.params, ctx))
            case jvm.ClassType.Class                                => Some(code"()")
            case jvm.ClassType.AbstractClass if cls.params.nonEmpty => Some(renderDataClassParams(cls.params, ctx, addVal = false))
            case jvm.ClassType.AbstractClass                        => None
            case jvm.ClassType.Interface                            => None
          },
          extendsAndImplements,
          Some(code"""| {
                      |  ${body.mkCode("\n\n")}
                      |}""".stripMargin)
        ).flatten.mkCode("")
      case jvm.TryCatch(tryBlock, catches, finallyBlock) =>
        val tryCode = code"""|try {
                             |  ${tryBlock.map(s => renderStmt(s)).mkCode("\n")}
                             |}""".stripMargin
        val catchCodes = catches.map { case jvm.TryCatch.Catch(exType, ident, body) =>
          code"""|catch ($ident: $exType) {
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
        val ifCases = cases.zipWithIndex.map { case ((cond, body), idx) =>
          if (idx == 0) code"if ($cond) { $body }"
          else code"else if ($cond) { $body }"
        }
        val elseCode = code"else { $elseCase }"
        (ifCases :+ elseCode).mkCode("\n")
    }

  override def escapedIdent(value: String): String = {
    val filtered = value.zipWithIndex.collect {
      case ('-', _)                             => "_"
      case (c, 0) if c.isUnicodeIdentifierStart => c
      case (c, _) if c.isUnicodeIdentifierPart  => c
    }.mkString
    val filtered2 = if (filtered.forall(_.isDigit)) "_" + filtered else filtered
    if (isKeyword(filtered2)) s"`$filtered2`" else filtered2
  }

  def renderParams(params: List[jvm.Param[jvm.Type]], ctx: Ctx): jvm.Code = {
    val hasComments = params.exists(_.comments.lines.nonEmpty)
    params match {
      case Nil                       => code"()"
      case List(one) if !hasComments => code"(${renderTree(one, ctx)})"
      case List(one)                 =>
        // Single param with comment - no empty init line
        code"""|(
               |  ${renderTree(one, ctx)}
               |)""".stripMargin
      case more =>
        code"""|(
               |  ${more.init.map(p => code"${renderTree(p, ctx)},").mkCode("\n")}
               |  ${more.lastOption.map(p => code"${renderTree(p, ctx)}").getOrElse(jvm.Code.Empty)}
               |)""".stripMargin
    }
  }

  def renderAnnotationsInline(annotations: List[jvm.Annotation]): jvm.Code = {
    if (annotations.isEmpty) jvm.Code.Empty
    else annotations.map(renderAnnotation).mkCode(" ") ++ code" "
  }

  def renderDataClassParams(params: List[jvm.Param[jvm.Type]], @annotation.nowarn ctx: Ctx, addVal: Boolean = true): jvm.Code = {
    val withVal = params.map { p =>
      // For data class params, add @field: target for annotations without useTarget (Kotlin 2.3+ requirement)
      val annotationsWithTarget = p.annotations.map { ann =>
        if (ann.useTarget.isEmpty) ann.copy(useTarget = Some(jvm.Annotation.UseTarget.Field))
        else ann
      }
      val annotationsCode = renderAnnotationsInline(annotationsWithTarget)
      val commentCode = renderComments(p.comments).getOrElse(jvm.Code.Empty)
      val defaultCode = p.default.map(d => code" = $d").getOrElse(jvm.Code.Empty)
      // When extending a Java class, don't add val - just pass params to super constructor
      val valCode = if (addVal) "val " else ""
      code"${commentCode}${annotationsCode}${valCode}${p.name}: ${p.tpe}$defaultCode"
    }
    withVal match {
      case Nil       => code"()"
      case List(one) => code"($one)"
      case more =>
        code"""|(
               |  ${more.init.map(p => code"$p,").mkCode("\n")}
               |  ${more.lastOption.getOrElse(jvm.Code.Empty)}
               |)""".stripMargin
    }
  }

  def renderTparams(tparams: List[Type.Abstract]) =
    if (tparams.isEmpty) jvm.Code.Empty else code"<${tparams.map(t => renderTree(t, Ctx.Empty)).mkCode(", ")}>"

  /** Render a Body for lambda expressions */
  def renderBody(body: jvm.Body): jvm.Code = body match {
    case jvm.Body.Abstract     => jvm.Code.Empty
    case jvm.Body.Expr(value)  => value
    case jvm.Body.Stmts(stmts) => stmts.map(s => renderStmt(s)).mkCode("\n")
  }

  /** Render a statement. Kotlin doesn't require semicolons, but we still handle Stmt wrapper. */
  def renderStmt(stmt: jvm.Code): jvm.Code = stmt match {
    case jvm.Code.Tree(jvm.Stmt(inner, _)) => inner // Kotlin doesn't need semicolons
    case _                                 => stmt
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
    val argsCode = renderAnnotationArgs(ann.args)
    // Kotlin use-site targets: @get:JsonValue, @field:JsonCreator etc
    val useTargetPrefix = ann.useTarget.map(t => code"${t.name}:").getOrElse(jvm.Code.Empty)
    code"@$useTargetPrefix${ann.tpe}$argsCode"
  }

  /** Render a nested annotation (inside another annotation) - no @ prefix */
  def renderNestedAnnotation(ann: jvm.Annotation): jvm.Code = {
    val argsCode = renderAnnotationArgs(ann.args)
    code"${ann.tpe}$argsCode"
  }

  /** Render annotation arguments, converting ClassOf to KClass for Kotlin */
  private def renderAnnotationArgs(args: List[jvm.Annotation.Arg]): jvm.Code = args match {
    case Nil                                        => jvm.Code.Empty
    case List(jvm.Annotation.Arg.Positional(value)) => code"(${renderAnnotationValue(value)})"
    case args =>
      val rendered = args
        .map {
          case jvm.Annotation.Arg.Named(name, value) => code"$name = ${renderAnnotationValue(value)}"
          case jvm.Annotation.Arg.Positional(value)  => renderAnnotationValue(value)
        }
        .mkCode(", ")
      code"($rendered)"
  }

  /** Render annotation argument value, converting ClassOf to KClass and JavaClassOf to Java Class */
  private def renderAnnotationValue(value: jvm.Code): jvm.Code = value match {
    case jvm.Code.Tree(jvm.ClassOf(tpe)) =>
      // For Kotlin annotations, use KClass (::class) not Java Class (::class.java)
      code"$tpe::class"
    case jvm.Code.Tree(jvm.JavaClassOf(tpe)) =>
      // Explicit Java Class reference - use ::class.java
      code"$tpe::class.java"
    case other => other
  }

  def renderAnnotations(annotations: List[jvm.Annotation]): jvm.Code = {
    if (annotations.isEmpty) jvm.Code.Empty
    else annotations.map(renderAnnotation).mkCode("\n") ++ code"\n"
  }

  // Kotlin: Bijection.of({ it.field }, ::Type)
  override def bijection(wrapperType: jvm.Type, underlying: jvm.Type, getter: jvm.FieldGetterRef, constructor: jvm.ConstructorMethodRef): jvm.Code = {
    val bijection = dsl.Bijection
    code"$bijection.of($getter, $constructor)"
  }

  val isKeyword: Set[String] =
    Set(
      "as",
      "break",
      "class",
      "continue",
      "do",
      "else",
      "false",
      "for",
      "fun",
      "if",
      "in",
      "interface",
      "is",
      "null",
      "object",
      "package",
      "return",
      "super",
      "this",
      "throw",
      "true",
      "try",
      "typealias",
      "typeof",
      "val",
      "var",
      "when",
      "while"
    )

  /** Convert a type to wildcard version for pattern matching. Ok<T> -> Ok<*>, Response2004XX5XX<T> -> Response2004XX5XX<*> Non-generic types are returned unchanged.
    */
  private def toWildcardType(tpe: jvm.Type): jvm.Type = tpe match {
    case jvm.Type.TApply(underlying, targs) =>
      jvm.Type.TApply(underlying, targs.map(_ => jvm.Type.Wildcard))
    case other => other
  }
}

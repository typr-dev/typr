package typr
package internal
package codegen

import scala.collection.mutable

/** imports are automatically written based on the qualified idents found in the code
  */
object addPackageAndImports {
  def apply(language: Lang, knownNamesByPkg: Map[jvm.QIdent, Map[jvm.Ident, jvm.Type.Qualified]], file: jvm.File): jvm.File = {
    val newImports = mutable.Map.empty[jvm.Ident, jvm.Type.Qualified]
    val newStaticImports = mutable.Map.empty[jvm.Ident, jvm.Type.Qualified]

    def addImport(currentQName: jvm.Type.Qualified, isStatic: Boolean): jvm.Type.Qualified = {
      if (currentQName.value.idents.length <= 1) currentQName
      else {
        val currentName = currentQName.value.name
        val shortenedQName = jvm.Type.Qualified(currentName)
        val targetMap = if (isStatic) newStaticImports else newImports
        val otherMap = if (isStatic) newImports else newStaticImports
        knownNamesByPkg.get(file.pkg).flatMap(_.get(currentName)).orElse(language.BuiltIn.get(currentName)).orElse(targetMap.get(currentName)).orElse(otherMap.get(currentName)) match {
          case Some(alreadyAvailable) =>
            if (alreadyAvailable == currentQName) shortenedQName else currentQName
          case None =>
            targetMap += ((currentName, currentQName))
            shortenedQName
        }
      }
    }

    val contents = file.contents
    val withShortenedNames = contents.mapTrees { tree =>
      shortenNames(
        tree,
        typeImport = qname => addImport(qname, isStatic = false),
        staticImport = qname => addImport(qname, isStatic = true)
      )
    }

    def sortedImports(imports: scala.collection.mutable.Map[jvm.Ident, jvm.Type.Qualified]): List[jvm.Type.Qualified] =
      imports.values.toList.sorted

    val renderedImports = sortedImports(newImports).map { i =>
      language match {
        case LangJava                     => code"import $i;"
        case _: LangScala | _: LangKotlin => code"import $i"
        case other                        => sys.error(s"Unsupported language: $other")
      }
    }
    val renderedStaticImports = sortedImports(newStaticImports).map { i =>
      language match {
        case LangJava                     => code"import static $i;"
        case _: LangScala | _: LangKotlin => code"import $i"
        case other                        => sys.error(s"Unsupported language: $other")
      }
    }
    // Render additional imports (wildcard imports like "org.http4s.circe._")
    val renderedAdditionalImports = file.additionalImports.map { imp =>
      language match {
        case LangJava                     => code"import $imp;"
        case _: LangScala | _: LangKotlin => code"import $imp"
        case other                        => sys.error(s"Unsupported language: $other")
      }
    }

    val allImports = renderedImports ++ renderedStaticImports ++ renderedAdditionalImports
    val withPrefix =
      code"""|package ${file.pkg}${language.`;`}
             |
             |${allImports.mkCode("\n")}
             |
             |$withShortenedNames""".stripMargin

    file.copy(contents = withPrefix)
  }

  // traverse tree and rewrite qualified names
  def shortenNames(tree: jvm.Tree, typeImport: jvm.Type.Qualified => jvm.Type.Qualified, staticImport: jvm.Type.Qualified => jvm.Type.Qualified): jvm.Tree =
    tree match {
      case jvm.Import(imp, isStatic) =>
        val importFn = if (isStatic) staticImport else typeImport
        val _ = importFn(imp) // Register the import, discard shortened version
        jvm.Import(imp, isStatic) // Return unchanged
      case jvm.IgnoreResult(expr) => jvm.IgnoreResult(expr.mapTrees(shortenNames(_, typeImport, staticImport)))
      case jvm.NotNull(expr)      => jvm.NotNull(expr.mapTrees(shortenNames(_, typeImport, staticImport)))
      case jvm.IfExpr(pred, thenp, elsep) =>
        jvm.IfExpr(
          pred.mapTrees(t => shortenNames(t, typeImport, staticImport)),
          thenp.mapTrees(t => shortenNames(t, typeImport, staticImport)),
          elsep.mapTrees(t => shortenNames(t, typeImport, staticImport))
        )
      case jvm.If(branches, elseBody) =>
        jvm.If(
          branches.map { case jvm.If.Branch(cond, body) =>
            jvm.If.Branch(
              cond.mapTrees(t => shortenNames(t, typeImport, staticImport)),
              body.mapTrees(t => shortenNames(t, typeImport, staticImport))
            )
          },
          elseBody.map(_.mapTrees(t => shortenNames(t, typeImport, staticImport)))
        )
      case jvm.While(cond, body) =>
        jvm.While(
          cond.mapTrees(t => shortenNames(t, typeImport, staticImport)),
          body.map(_.mapTrees(t => shortenNames(t, typeImport, staticImport)))
        )
      case jvm.ConstructorMethodRef(tpe) => jvm.ConstructorMethodRef(shortenNamesType(tpe, typeImport))
      case jvm.ClassOf(tpe)              => jvm.ClassOf(shortenNamesType(tpe, typeImport))
      case jvm.JavaClassOf(tpe)          => jvm.JavaClassOf(shortenNamesType(tpe, typeImport))
      case adt: jvm.Adt                  => shortenNamesAdt(adt, typeImport, staticImport)
      case cls: jvm.Class                => shortenNamesClass(cls, typeImport, staticImport)
      case jvm.Call(target, argGroups) =>
        jvm.Call(
          target.mapTrees(t => shortenNames(t, typeImport, staticImport)),
          argGroups.map(group =>
            jvm.Call.ArgGroup(
              group.args.map(t => shortenNamesArg(t, typeImport, staticImport)),
              group.isImplicit
            )
          )
        )
      case jvm.Apply0(ref)       => jvm.Apply0(shortenNamesParam(ref, typeImport, staticImport).narrow)
      case jvm.Apply1(ref, arg1) => jvm.Apply1(shortenNamesParam(ref, typeImport, staticImport).narrow, arg1.mapTrees(t => shortenNames(t, typeImport, staticImport)))
      case jvm.Apply2(ref, arg1, arg2) =>
        jvm.Apply2(
          shortenNamesParam(ref, typeImport, staticImport).narrow,
          arg1.mapTrees(t => shortenNames(t, typeImport, staticImport)),
          arg2.mapTrees(t => shortenNames(t, typeImport, staticImport))
        )
      case jvm.Select(target, name)                          => jvm.Select(target.mapTrees(t => shortenNames(t, typeImport, staticImport)), name)
      case jvm.ArrayIndex(target, num)                       => jvm.ArrayIndex(target.mapTrees(t => shortenNames(t, typeImport, staticImport)), num)
      case jvm.ApplyNullary(target, name)                    => jvm.ApplyNullary(target.mapTrees(t => shortenNames(t, typeImport, staticImport)), name)
      case jvm.Arg.Named(name, value)                        => jvm.Arg.Named(name, value.mapTrees(t => shortenNames(t, typeImport, staticImport)))
      case jvm.Arg.Pos(value)                                => jvm.Arg.Pos(value.mapTrees(t => shortenNames(t, typeImport, staticImport)))
      case jvm.Enum(anns, comments, tpe, members, instances) => jvm.Enum(anns, comments, typeImport(tpe), members, instances.map(shortenNamesClassMember(_, typeImport, staticImport)))
      case jvm.OpenEnum(anns, comments, tpe, underlyingType, values, staticMembers) =>
        jvm.OpenEnum(
          annotations = anns,
          comments = comments,
          tpe = typeImport(tpe),
          underlyingType = typeImport(underlyingType),
          values = values.map { case (name, expr) => (name, expr.mapTrees(t => shortenNames(t, typeImport, staticImport))) },
          staticMembers = staticMembers.map(shortenNamesStaticMember(_, typeImport, staticImport))
        )
      case jvm.MethodRef(tpe, name) => jvm.MethodRef(shortenNamesType(tpe, typeImport), name)
      case jvm.New(target, args)    => jvm.New(target.mapTrees(t => shortenNames(t, typeImport, staticImport)), args.map(shortenNamesArg(_, typeImport, staticImport)))
      case jvm.NewWithBody(extendsClass, implementsInterface, members) =>
        jvm.NewWithBody(
          extendsClass.map(shortenNamesType(_, typeImport)),
          implementsInterface.map(shortenNamesType(_, typeImport)),
          members.map(shortenNamesClassMember(_, typeImport, staticImport))
        )
      case jvm.InferredTargs(target) => jvm.InferredTargs(target.mapTrees(t => shortenNames(t, typeImport, staticImport)))
      case jvm.GenericMethodCall(target, methodName, typeArgs, args) =>
        jvm.GenericMethodCall(
          target.mapTrees(t => shortenNames(t, typeImport, staticImport)),
          methodName,
          typeArgs.map(shortenNamesType(_, typeImport)),
          args.map(shortenNamesArg(_, typeImport, staticImport))
        )
      case jvm.Return(expr) => jvm.Return(expr.mapTrees(t => shortenNames(t, typeImport, staticImport)))
      case jvm.Throw(expr)  => jvm.Throw(expr.mapTrees(t => shortenNames(t, typeImport, staticImport)))
      case jvm.Lambda(params, body) =>
        val newParams = params.map(p => jvm.LambdaParam(p.name, p.tpe.map(shortenNamesType(_, typeImport))))
        jvm.Lambda(newParams, shortenNamesBody(body, typeImport, staticImport))
      case jvm.SamLambda(samType, lambda) =>
        val newSamType = shortenNamesType(samType, typeImport)
        val newLambda = shortenNames(lambda, typeImport, staticImport).asInstanceOf[jvm.Lambda]
        jvm.SamLambda(newSamType, newLambda)
      case jvm.Cast(targetType, expr) =>
        jvm.Cast(shortenNamesType(targetType, typeImport), expr.mapTrees(t => shortenNames(t, typeImport, staticImport)))
      case jvm.ByName(body)                 => jvm.ByName(shortenNamesBody(body, typeImport, staticImport))
      case jvm.FieldGetterRef(rowType, fld) => jvm.FieldGetterRef(shortenNamesType(rowType, typeImport), fld)
      case jvm.SelfNullary(name)            => jvm.SelfNullary(name)
      case jvm.TypedFactoryCall(tpe, typeArgs, args) =>
        jvm.TypedFactoryCall(shortenNamesType(tpe, typeImport), typeArgs.map(shortenNamesType(_, typeImport)), args.map(shortenNamesArg(_, typeImport, staticImport)))
      case jvm.StringInterpolate(i, prefix, content) => jvm.StringInterpolate(shortenNamesType(i, staticImport), prefix, content.mapTrees(t => shortenNames(t, typeImport, staticImport)))
      case tpe: jvm.Type                             => shortenNamesType(tpe, typeImport)
      case jvm.RuntimeInterpolation(value)           => jvm.RuntimeInterpolation(value.mapTrees(t => shortenNames(t, typeImport, staticImport)))
      case x: jvm.ClassMember                        => shortenNamesClassMember(x, typeImport, staticImport)
      case x: jvm.Ident                              => x
      case x: jvm.Param[jvm.Type]                    => shortenNamesParam(x, typeImport, staticImport)
      case x: jvm.QIdent                             => x
      case x: jvm.StrLit                             => x
      case x: jvm.Summon                             => jvm.Summon(shortenNamesType(x.tpe, typeImport))
      case jvm.LocalVar(name, tpe, value) =>
        jvm.LocalVar(name, tpe.map(shortenNamesType(_, typeImport)), value.mapTrees(t => shortenNames(t, typeImport, staticImport)))
      case jvm.TypeSwitch(value, cases, nullCase, defaultCase, unchecked) =>
        jvm.TypeSwitch(
          value.mapTrees(t => shortenNames(t, typeImport, staticImport)),
          cases.map { c => jvm.TypeSwitch.Case(shortenNamesType(c.tpe, typeImport), c.ident, c.body.mapTrees(t => shortenNames(t, typeImport, staticImport))) },
          nullCase.map(_.mapTrees(t => shortenNames(t, typeImport, staticImport))),
          defaultCase.map(_.mapTrees(t => shortenNames(t, typeImport, staticImport))),
          unchecked
        )
      case jvm.TryCatch(tryBlock, catches, finallyBlock) =>
        jvm.TryCatch(
          tryBlock.map(_.mapTrees(t => shortenNames(t, typeImport, staticImport))),
          catches.map { c =>
            jvm.TryCatch.Catch(
              typeImport(c.exceptionType),
              c.ident,
              c.body.map(_.mapTrees(t => shortenNames(t, typeImport, staticImport)))
            )
          },
          finallyBlock.map(_.mapTrees(t => shortenNames(t, typeImport, staticImport)))
        )
      case jvm.IfElseChain(cases, elseCase) =>
        jvm.IfElseChain(
          cases.map { case (cond, body) =>
            (cond.mapTrees(t => shortenNames(t, typeImport, staticImport)), body.mapTrees(t => shortenNames(t, typeImport, staticImport)))
          },
          elseCase.mapTrees(t => shortenNames(t, typeImport, staticImport))
        )
      case jvm.Stmt(inner, needsSemicolon) =>
        jvm.Stmt(inner.mapTrees(t => shortenNames(t, typeImport, staticImport)), needsSemicolon)
      case jvm.Annotation(tpe, args, useTarget) =>
        jvm.Annotation(
          typeImport(tpe),
          args.map {
            case jvm.Annotation.Arg.Named(name, value) => jvm.Annotation.Arg.Named(name, value.mapTrees(t => shortenNames(t, typeImport, staticImport)))
            case jvm.Annotation.Arg.Positional(value)  => jvm.Annotation.Arg.Positional(value.mapTrees(t => shortenNames(t, typeImport, staticImport)))
          },
          useTarget
        )
      case jvm.AnnotationArray(elements) =>
        jvm.AnnotationArray(elements.map(_.mapTrees(t => shortenNames(t, typeImport, staticImport))))
    }

  def shortenNamesParam(param: jvm.Param[jvm.Type], typeImport: jvm.Type.Qualified => jvm.Type.Qualified, staticImport: jvm.Type.Qualified => jvm.Type.Qualified): jvm.Param[jvm.Type] =
    jvm.Param(
      param.annotations.map(shortenNamesAnnotation(_, typeImport, staticImport)),
      param.comments,
      param.name,
      shortenNamesType(param.tpe, typeImport),
      param.default.map(code => code.mapTrees(t => shortenNames(t, typeImport, staticImport)))
    )

  def shortenNamesAnnotation(ann: jvm.Annotation, typeImport: jvm.Type.Qualified => jvm.Type.Qualified, staticImport: jvm.Type.Qualified => jvm.Type.Qualified): jvm.Annotation =
    jvm.Annotation(
      typeImport(ann.tpe),
      ann.args.map {
        case jvm.Annotation.Arg.Named(name, value) => jvm.Annotation.Arg.Named(name, value.mapTrees(t => shortenNames(t, typeImport, staticImport)))
        case jvm.Annotation.Arg.Positional(value)  => jvm.Annotation.Arg.Positional(value.mapTrees(t => shortenNames(t, typeImport, staticImport)))
      }
    )

  def shortenNamesArg(arg: jvm.Arg, typeImport: jvm.Type.Qualified => jvm.Type.Qualified, staticImport: jvm.Type.Qualified => jvm.Type.Qualified): jvm.Arg =
    arg match {
      case jvm.Arg.Pos(value)         => jvm.Arg.Pos(value.mapTrees(t => shortenNames(t, typeImport, staticImport)))
      case jvm.Arg.Named(name, value) => jvm.Arg.Named(name, value.mapTrees(t => shortenNames(t, typeImport, staticImport)))
    }

  def shortenNamesBody(body: jvm.Body, typeImport: jvm.Type.Qualified => jvm.Type.Qualified, staticImport: jvm.Type.Qualified => jvm.Type.Qualified): jvm.Body =
    body match {
      case jvm.Body.Abstract     => jvm.Body.Abstract
      case jvm.Body.Expr(value)  => jvm.Body.Expr(value.mapTrees(t => shortenNames(t, typeImport, staticImport)))
      case jvm.Body.Stmts(stmts) => jvm.Body.Stmts(stmts.map(s => s.mapTrees(t => shortenNames(t, typeImport, staticImport))))
    }

  def shortenNamesClass(cls: jvm.Class, typeImport: jvm.Type.Qualified => jvm.Type.Qualified, staticImport: jvm.Type.Qualified => jvm.Type.Qualified): jvm.Class =
    jvm.Class(
      annotations = cls.annotations.map(shortenNamesAnnotation(_, typeImport, staticImport)),
      comments = cls.comments,
      classType = cls.classType,
      name = cls.name,
      tparams = cls.tparams,
      params = cls.params.map(shortenNamesParam(_, typeImport, staticImport)),
      implicitParams = cls.implicitParams.map(shortenNamesParam(_, typeImport, staticImport)),
      `extends` = cls.`extends`.map(shortenNamesType(_, typeImport)),
      implements = cls.implements.map(shortenNamesType(_, typeImport)),
      members = cls.members.map(shortenNamesClassMember(_, typeImport, staticImport)),
      staticMembers = cls.staticMembers.map(shortenNamesClassMember(_, typeImport, staticImport))
    )

  def shortenNamesAdt(x: jvm.Adt, typeImport: jvm.Type.Qualified => jvm.Type.Qualified, staticImport: jvm.Type.Qualified => jvm.Type.Qualified): jvm.Adt =
    x match {
      case x: jvm.Adt.Record => shortenNamesAdtRecord(x, typeImport, staticImport)
      case x: jvm.Adt.Sum    => shortenNamesAdtSum(x, typeImport, staticImport)
    }

  def shortenNamesAdtRecord(x: jvm.Adt.Record, typeImport: jvm.Type.Qualified => jvm.Type.Qualified, staticImport: jvm.Type.Qualified => jvm.Type.Qualified): jvm.Adt.Record =
    jvm.Adt.Record(
      annotations = x.annotations.map(shortenNamesAnnotation(_, typeImport, staticImport)),
      constructorAnnotations = x.constructorAnnotations.map(shortenNamesAnnotation(_, typeImport, staticImport)),
      isWrapper = x.isWrapper,
      privateConstructor = x.privateConstructor,
      comments = x.comments,
      name = x.name,
      tparams = x.tparams,
      params = x.params.map(shortenNamesParam(_, typeImport, staticImport)),
      implicitParams = x.implicitParams.map(shortenNamesParam(_, typeImport, staticImport)),
      `extends` = x.`extends`.map(shortenNamesType(_, typeImport)),
      implements = x.implements.map(shortenNamesType(_, typeImport)),
      members = x.members.map(shortenNamesClassMember(_, typeImport, staticImport)),
      staticMembers = x.staticMembers.map(shortenNamesClassMember(_, typeImport, staticImport))
    )

  def shortenNamesAdtSum(x: jvm.Adt.Sum, typeImport: jvm.Type.Qualified => jvm.Type.Qualified, staticImport: jvm.Type.Qualified => jvm.Type.Qualified): jvm.Adt.Sum =
    jvm.Adt.Sum(
      annotations = x.annotations.map(shortenNamesAnnotation(_, typeImport, staticImport)),
      comments = x.comments,
      name = x.name,
      tparams = x.tparams,
      implements = x.implements.map(shortenNamesType(_, typeImport)),
      members = x.members.map(shortenNamesMethod(_, typeImport, staticImport)),
      staticMembers = x.staticMembers.map(shortenNamesClassMember(_, typeImport, staticImport)),
      subtypes = x.subtypes.map(shortenNamesAdt(_, typeImport, staticImport))
    )

  def shortenNamesClassMember(cm: jvm.ClassMember, typeImport: jvm.Type.Qualified => jvm.Type.Qualified, staticImport: jvm.Type.Qualified => jvm.Type.Qualified): jvm.ClassMember =
    cm match {
      case jvm.Given(anns, tparams, name, implicitParams, tpe, body) =>
        jvm.Given(
          anns.map(shortenNamesAnnotation(_, typeImport, staticImport)),
          tparams,
          name,
          implicitParams.map(p => shortenNamesParam(p, typeImport, staticImport)),
          shortenNamesType(tpe, typeImport),
          body.mapTrees(t => shortenNames(t, typeImport, staticImport))
        )
      case jvm.Value(anns, name, tpe, body, isLazy, isOverride, isImplicit) =>
        jvm.Value(
          anns,
          name,
          shortenNamesType(tpe, typeImport),
          body.map(_.mapTrees(t => shortenNames(t, typeImport, staticImport))),
          isLazy,
          isOverride,
          isImplicit
        )
      case x: jvm.Method =>
        shortenNamesMethod(x, typeImport, staticImport)
      case cls: jvm.NestedClass =>
        jvm.NestedClass(
          isPrivate = cls.isPrivate,
          isFinal = cls.isFinal,
          name = cls.name,
          params = cls.params.map(shortenNamesParam(_, typeImport, staticImport)),
          `extends` = cls.`extends`.map(shortenNamesType(_, typeImport)),
          superArgs = cls.superArgs.map(shortenNamesArg(_, typeImport, staticImport)),
          members = cls.members.map(shortenNamesClassMember(_, typeImport, staticImport))
        )
      case rec: jvm.NestedRecord =>
        jvm.NestedRecord(
          isPrivate = rec.isPrivate,
          name = rec.name,
          params = rec.params.map(shortenNamesParam(_, typeImport, staticImport)),
          implements = rec.implements.map(shortenNamesType(_, typeImport)),
          members = rec.members.map(shortenNamesClassMember(_, typeImport, staticImport))
        )
    }

  def shortenNamesStaticMember(cm: jvm.StaticMember, typeImport: jvm.Type.Qualified => jvm.Type.Qualified, staticImport: jvm.Type.Qualified => jvm.Type.Qualified): jvm.StaticMember =
    cm match {
      case x: jvm.ClassMember => shortenNamesClassMember(x, typeImport, staticImport)
      case x: jvm.Class       => shortenNamesClass(x, typeImport, staticImport)
    }

  def shortenNamesMethod(x: jvm.Method, typeImport: jvm.Type.Qualified => jvm.Type.Qualified, staticImport: jvm.Type.Qualified => jvm.Type.Qualified): jvm.Method =
    jvm.Method(
      Nil,
      x.comments,
      x.tparams,
      x.name,
      x.params.map(p => shortenNamesParam(p, typeImport, staticImport)),
      x.implicitParams.map(p => shortenNamesParam(p, typeImport, staticImport)),
      shortenNamesType(x.tpe, typeImport),
      x.throws.map(t => shortenNamesType(t, typeImport)),
      shortenNamesBody(x.body, typeImport, staticImport),
      x.isOverride,
      x.isDefault
    )

  // traverse type tree and rewrite qualified names
  def shortenNamesType(tpe: jvm.Type, f: jvm.Type.Qualified => jvm.Type.Qualified): jvm.Type =
    tpe match {
      case q @ jvm.Type.Qualified(_)                  => f(q)
      case jvm.Type.Abstract(value, variance)         => jvm.Type.Abstract(value, variance)
      case jvm.Type.ArrayOf(value)                    => jvm.Type.ArrayOf(shortenNamesType(value, f))
      case jvm.Type.KotlinNullable(underlying)        => jvm.Type.KotlinNullable(shortenNamesType(underlying, f))
      case jvm.Type.Commented(underlying, comment)    => jvm.Type.Commented(shortenNamesType(underlying, f), comment)
      case jvm.Type.Annotated(underlying, annotation) => jvm.Type.Annotated(shortenNamesType(underlying, f), f(annotation).asInstanceOf[jvm.Type.Qualified])
      case jvm.Type.TApply(underlying, targs)         => jvm.Type.TApply(shortenNamesType(underlying, f), targs.map(targ => shortenNamesType(targ, f)))
      case jvm.Type.UserDefined(underlying)           => jvm.Type.UserDefined(shortenNamesType(underlying, f))
      case jvm.Type.Void                              => jvm.Type.Void
      case jvm.Type.Wildcard                          => jvm.Type.Wildcard
      case jvm.Type.Function0(ret)                    => jvm.Type.Function0(shortenNamesType(ret, f))
      case jvm.Type.Function1(tpe1, ret)              => jvm.Type.Function1(shortenNamesType(tpe1, f), shortenNamesType(ret, f))
      case jvm.Type.Function2(tpe1, tpe2, ret)        => jvm.Type.Function2(shortenNamesType(tpe1, f), shortenNamesType(tpe2, f), shortenNamesType(ret, f))
      case p: jvm.Type.Primitive                      => p
    }
}

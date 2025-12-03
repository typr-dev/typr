package typo
package internal

/** Keep those files among `allFiles` which are part of or reachable (through type references) from `entryPoints`. */
object minimize {
  def apply(allFiles: List[jvm.File], entryPoints: Iterable[jvm.File]): List[jvm.File] = {
    val filesByQident = allFiles.iterator.map(x => (x.tpe.value, x)).toMap
    val toKeep: Set[jvm.QIdent] = {
      val b = collection.mutable.HashSet.empty[jvm.QIdent]
      b ++= entryPoints.map(_.tpe.value)
      entryPoints.foreach { f =>
        def goTree(tree: jvm.Tree): Unit = {
          tree match {
            case jvm.IgnoreResult(expr) =>
              go(expr)
            case jvm.NotNull(expr) =>
              go(expr)
            case jvm.TypeSwitch(value, cases, nullCase, defaultCase, _) =>
              go(value)
              cases.foreach { c =>
                goTree(c.tpe)
                goTree(c.ident)
                go(c.body)
              }
              nullCase.foreach(go)
              defaultCase.foreach(go)
            case jvm.TryCatch(tryBlock, catches, finallyBlock) =>
              tryBlock.foreach(go)
              catches.foreach { c =>
                goTree(c.exceptionType)
                goTree(c.ident)
                c.body.foreach(go)
              }
              finallyBlock.foreach(go)
            case jvm.IfElseChain(cases, elseCase) =>
              cases.foreach { case (cond, body) =>
                go(cond)
                go(body)
              }
              go(elseCase)
            case jvm.Stmt(inner, _) =>
              go(inner)
            case jvm.IfExpr(pred, thenp, elsep) =>
              go(pred)
              go(thenp)
              go(elsep)
            case jvm.If(branches, elseBody) =>
              branches.foreach { case jvm.If.Branch(cond, body) =>
                go(cond)
                go(body)
              }
              elseBody.foreach(go)
            case jvm.While(cond, body) =>
              go(cond)
              body.foreach(go)
            case jvm.ConstructorMethodRef(tpe) =>
              goTree(tpe)
            case jvm.ClassOf(tpe) =>
              goTree(tpe)
            case jvm.JavaClassOf(tpe) =>
              goTree(tpe)
            case jvm.Call(target, argGroups) =>
              go(target)
              argGroups.foreach(group => group.args.foreach(goTree))
            case jvm.Ident(_) => ()
            case x: jvm.QIdent =>
              if (!b(x)) {
                b += x
                filesByQident.get(x).foreach(f => go(f.contents))
              }
            case jvm.New(target, args) =>
              go(target)
              args.foreach(goTree)
            case jvm.NewWithBody(extendsClass, implementsInterface, members) =>
              extendsClass.foreach(goTree)
              implementsInterface.foreach(goTree)
              members.foreach(goTree)
            case jvm.InferredTargs(target) =>
              go(target)
            case jvm.GenericMethodCall(target, _, typeArgs, args) =>
              go(target)
              typeArgs.foreach(goTree)
              args.foreach(goTree)
            case jvm.Lambda(params, body) =>
              params.foreach(p => p.tpe.foreach(goTree))
              goBody(body)
            case jvm.SamLambda(samType, lambda) =>
              goTree(samType)
              goTree(lambda)
            case jvm.Cast(targetType, expr) =>
              goTree(targetType)
              go(expr)
            case jvm.ByName(body) =>
              goBody(body)
            case jvm.FieldGetterRef(rowType, _) =>
              goTree(rowType)
            case jvm.SelfNullary(_) =>
              ()
            case jvm.TypedFactoryCall(tpe, typeArgs, args) =>
              goTree(tpe)
              typeArgs.foreach(goTree)
              args.foreach(goTree)
            case jvm.Select(target, name) =>
              go(target)
              goTree(name)
            case jvm.ArrayIndex(target, _) =>
              go(target)
            case jvm.ApplyNullary(target, name) =>
              go(target)
              goTree(name)
            case jvm.Arg.Pos(value) =>
              go(value)
            case jvm.Arg.Named(_, value) =>
              go(value)
            case jvm.Param(_, _, _, tpe, maybeCode) =>
              goTree(tpe)
              maybeCode.foreach(go)
            case jvm.StrLit(_) => ()
            case jvm.LocalVar(name, tpe, value) =>
              goTree(name)
              tpe.foreach(goTree)
              go(value)
            case jvm.MethodRef(tpe, name) =>
              goTree(tpe)
              goTree(name)
            case jvm.Summon(tpe) =>
              goTree(tpe)
            case jvm.StringInterpolate(i, prefix, content) =>
              goTree(i)
              goTree(prefix)
              go(content)
            case jvm.Given(_, tparams, name, implicitParams, tpe, body) =>
              tparams.foreach(goTree)
              goTree(name)
              implicitParams.foreach(goTree)
              goTree(tpe)
              go(body)
            case jvm.Value(_, name, tpe, body, _, _, _) =>
              goTree(name)
              goTree(tpe)
              body.foreach(go)
            case jvm.Method(_, _, tparams, name, params, implicitParams, tpe, throws, body, _, _) =>
              tparams.foreach(goTree)
              goTree(name)
              params.foreach(goTree)
              implicitParams.foreach(goTree)
              goTree(tpe)
              throws.foreach(goTree)
              goBody(body)
            case jvm.Enum(_, _, tpe, _, instances) =>
              goTree(tpe)
              instances.foreach(goTree)
            case jvm.Class(_, _, _, _, tparams, params, implicitParams, extends_, implements, members, staticMembers) =>
              tparams.foreach(goTree)
              params.foreach(goTree)
              implicitParams.foreach(goTree)
              extends_.foreach(goTree)
              implements.foreach(goTree)
              members.foreach(goTree)
              staticMembers.foreach(goTree)
            case jvm.NestedClass(_, _, _, params, extends_, superArgs, members) =>
              params.foreach(goTree)
              extends_.foreach(goTree)
              superArgs.foreach(goTree)
              members.foreach(goTree)
            case jvm.Adt.Record(_, _, _, _, _, tparams, params, implicitParams, extends_, implements, members, staticMembers) =>
              tparams.foreach(goTree)
              params.foreach(goTree)
              implicitParams.foreach(goTree)
              extends_.foreach(goTree)
              implements.foreach(goTree)
              members.foreach(goTree)
              staticMembers.foreach(goTree)
            case jvm.Adt.Sum(annotations, _, _, tparams, members, implements, subtypes, staticMembers, permittedSubtypes) =>
              annotations.foreach(goTree)
              tparams.foreach(goTree)
              members.foreach(goTree)
              implements.foreach(goTree)
              subtypes.foreach(goTree)
              staticMembers.foreach(goTree)
              permittedSubtypes.foreach(goTree)
            case jvm.Type.Wildcard =>
              ()
            case jvm.Type.TApply(underlying, targs) =>
              goTree(underlying)
              targs.foreach(goTree)
            case jvm.Type.ArrayOf(value) =>
              goTree(value)
            case jvm.Type.Qualified(value) =>
              goTree(value)
            case jvm.Type.Abstract(_, _) =>
              ()
            case jvm.Type.Commented(underlying, _) =>
              goTree(underlying)
            case jvm.Type.Annotated(underlying, ann) =>
              goTree(underlying)
              goTree(ann)
            case jvm.Type.UserDefined(underlying) =>
              goTree(underlying)
            case jvm.Type.Void =>
              ()
            case jvm.Type.Primitive(_) =>
              ()
            case jvm.Apply0(f) =>
              goTree(f)
            case jvm.Apply1(f, arg1) =>
              goTree(f)
              go(arg1)
            case jvm.Apply2(f, arg1, arg2) =>
              goTree(f)
              go(arg1)
              go(arg2)
            case jvm.Type.Function0(ret) =>
              goTree(ret)
            case jvm.Type.Function1(tpe1, ret) =>
              goTree(tpe1)
              goTree(ret)
            case jvm.Type.Function2(tpe1, tpe2, ret) =>
              goTree(tpe1)
              goTree(tpe2)
              goTree(ret)
            case jvm.OpenEnum(_, _, tpe, underlyingType, values, staticMembers) => {
              goTree(tpe)
              goTree(underlyingType)
              values.toList.foreach { case (_, expr) => go(expr) }
              staticMembers.foreach(goTree)
            }
            case jvm.RuntimeInterpolation(value) =>
              go(value)
            case jvm.Annotation(tpe, args, _) =>
              goTree(tpe)
              args.foreach {
                case jvm.Annotation.Arg.Named(_, value)   => go(value)
                case jvm.Annotation.Arg.Positional(value) => go(value)
              }
            case jvm.AnnotationArray(elements) =>
              elements.foreach(go)
            case jvm.Return(value) =>
              go(value)
            case jvm.Throw(value) =>
              go(value)
          }
        }

        def goBody(body: jvm.Body): Unit = body match {
          case jvm.Body.Abstract     => ()
          case jvm.Body.Expr(value)  => go(value)
          case jvm.Body.Stmts(stmts) => stmts.foreach(go)
        }

        def go(code: jvm.Code): Unit = {
          code match {
            case jvm.Code.Interpolated(_, args) =>
              args.foreach(go)
            case jvm.Code.Combined(codes) =>
              codes.foreach(go)
            case jvm.Code.Str(_) =>
              ()
            case jvm.Code.Tree(tree) =>
              goTree(tree)
            case jvm.Code.Indented(content) =>
              go(content)
          }
        }

        go(f.contents)
      }

      b.toSet
    }

    allFiles.filter(f => toKeep(f.tpe.value))
  }
}

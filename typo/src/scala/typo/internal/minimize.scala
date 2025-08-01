package typo
package internal

/** Keep those files among `allFiles` which are part of or reachable (through type references) from `entryPoints`. */
object minimize {
  def apply(allFiles: List[sc.File], entryPoints: Iterable[sc.File]): List[sc.File] = {
    val filesByQident = allFiles.iterator.map(x => (x.tpe.value, x)).toMap
    val toKeep: Set[sc.QIdent] = {
      val b = collection.mutable.HashSet.empty[sc.QIdent]
      b ++= entryPoints.map(_.tpe.value)
      entryPoints.foreach { f =>
        def goTree(tree: sc.Tree): Unit = {
          tree match {
            case sc.Ident(_) => ()
            case x: sc.QIdent =>
              if (!b(x)) {
                b += x
                filesByQident.get(x).foreach(f => go(f.contents))
              }

            case sc.Param(_, tpe, maybeCode) =>
              goTree(tpe)
              maybeCode.foreach(go)
            case sc.Params(params) =>
              params.foreach(goTree)
            case sc.StrLit(_) => ()
            case sc.Summon(tpe, _) =>
              goTree(tpe)
            case sc.StringInterpolate(i, prefix, content) =>
              goTree(i)
              goTree(prefix)
              go(content)
            case sc.Given(tparams, name, implicitParams, tpe, body, _) =>
              tparams.foreach(goTree)
              goTree(name)
              implicitParams.foreach(goTree)
              goTree(tpe)
              go(body)
            case sc.Value(tparams, name, params, implicitParams, tpe, body) =>
              tparams.foreach(goTree)
              goTree(name)
              params.foreach(goTree)
              implicitParams.foreach(goTree)
              goTree(tpe)
              go(body)
            case sc.Obj(name, members, body) =>
              goTree(name)
              members.foreach(goTree)
              body.foreach(go)
            case sc.Type.Wildcard =>
              ()
            case sc.Type.TApply(underlying, targs) =>
              goTree(underlying)
              targs.foreach(goTree)
            case sc.Type.ArrayOf(value)           => goTree(value)
            case sc.Type.Qualified(value)         => goTree(value)
            case sc.Type.Abstract(_)              => ()
            case sc.Type.Commented(underlying, _) => goTree(underlying)
            case sc.Type.UserDefined(underlying)  => goTree(underlying)
            case sc.Type.ByName(underlying)       => goTree(underlying)
          }
        }

        def go(code: sc.Code): Unit = {
          code match {
            case sc.Code.Interpolated(_, args) =>
              args.foreach(go)
            case sc.Code.Combined(codes) =>
              codes.foreach(go)
            case sc.Code.Str(_) =>
              ()
            case sc.Code.Tree(tree) =>
              goTree(tree)
          }
        }

        go(f.contents)
      }

      b.toSet
    }

    allFiles.filter(f => toKeep(f.tpe.value))
  }
}

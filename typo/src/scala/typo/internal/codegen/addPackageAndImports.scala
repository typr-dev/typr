package typo
package internal
package codegen

import scala.collection.mutable

/** imports are automatically written based on the qualified idents found in the code
  */
object addPackageAndImports {
  def apply(knownNamesByPkg: Map[sc.QIdent, Map[sc.Ident, sc.Type.Qualified]], file: sc.File): sc.File = {
    val newImports = mutable.Map.empty[sc.Ident, sc.Type.Qualified]

    val contents = file.contents
    val withShortenedNames = contents.mapTrees { tree =>
      shortenNames(
        tree,
        {
          case currentQName if currentQName.value.idents.length <= 1 => currentQName
          case currentQName =>
            val currentName = currentQName.value.name
            val shortenedQName = sc.Type.Qualified(currentName)
            knownNamesByPkg.get(file.pkg).flatMap(_.get(currentName)).orElse(sc.Type.BuiltIn.get(currentName)).orElse(newImports.get(currentName)) match {
              case Some(alreadyAvailable) =>
                if (alreadyAvailable == currentQName) shortenedQName else currentQName
              case None =>
                newImports += ((currentName, currentQName))
                shortenedQName
            }
        }
      )
    }

    val withPrefix =
      code"""${NonEmptyList.fromList(file.pkg.idents).fold(sc.Code.Empty)(is => is.map(i => code"package $i").mkCode("\n"))}
            |
            |${newImports.values.toList.sorted.map { i => code"import $i" }.mkCode("\n")}
            |
            |$withShortenedNames""".stripMargin

    file.copy(contents = withPrefix)
  }

  // traverse tree and rewrite qualified names
  def shortenNames(tree: sc.Tree, f: sc.Type.Qualified => sc.Type.Qualified): sc.Tree =
    tree match {
      case x: sc.Ident =>
        x
      case x: sc.QIdent =>
        x
      case x: sc.Param =>
        shortenNamesParam(x, f)
      case sc.Params(params) =>
        sc.Params(params.map(p => shortenNamesParam(p, f)))
      case x: sc.StrLit =>
        x
      case sc.Summon(tpe, implicitOrUsing) =>
        sc.Summon(shortenNamesType(tpe, f), implicitOrUsing)
      case tpe: sc.Type =>
        shortenNamesType(tpe, f)
      case sc.StringInterpolate(i, prefix, content) =>
        sc.StringInterpolate(shortenNamesType(i, f), prefix, content.mapTrees(t => shortenNames(t, f)))
      case x: sc.ClassMember =>
        shortenNamesClassMember(x, f)
      case sc.Obj(name, members, body) =>
        sc.Obj(name, members.map(cm => shortenNamesClassMember(cm, f)), body.map(_.mapTrees(t => shortenNames(t, f))))
    }

  def shortenNamesParam(param: sc.Param, f: sc.Type.Qualified => sc.Type.Qualified): sc.Param =
    sc.Param(param.name, shortenNamesType(param.tpe, f), param.default.map(code => code.mapTrees(t => shortenNames(t, f))))

  def shortenNamesClassMember(cm: sc.ClassMember, f: sc.Type.Qualified => sc.Type.Qualified): sc.ClassMember =
    cm match {
      case sc.Given(tparams, name, implicitParams, tpe, body, implicitOrUsing) =>
        sc.Given(tparams, name, implicitParams.map(p => shortenNamesParam(p, f)), shortenNamesType(tpe, f), body.mapTrees(t => shortenNames(t, f)), implicitOrUsing)
      case sc.Value(tparams, name, params, implicitParams, tpe, body) =>
        sc.Value(
          tparams,
          name,
          params.map(p => shortenNamesParam(p, f)),
          implicitParams.map(p => shortenNamesParam(p, f)),
          shortenNamesType(tpe, f),
          body.mapTrees(t => shortenNames(t, f))
        )
    }

  // traverse type tree and rewrite qualified names
  def shortenNamesType(tpe: sc.Type, f: sc.Type.Qualified => sc.Type.Qualified): sc.Type =
    tpe match {
      case sc.Type.ArrayOf(value)                 => sc.Type.ArrayOf(shortenNamesType(value, f))
      case sc.Type.Abstract(value)                => sc.Type.Abstract(value)
      case sc.Type.Wildcard                       => sc.Type.Wildcard
      case sc.Type.TApply(underlying, targs)      => sc.Type.TApply(shortenNamesType(underlying, f), targs.map(targ => shortenNamesType(targ, f)))
      case sc.Type.Qualified(value)               => f(sc.Type.Qualified(value))
      case sc.Type.Commented(underlying, comment) => sc.Type.Commented(shortenNamesType(underlying, f), comment)
      case sc.Type.ByName(underlying)             => sc.Type.ByName(shortenNamesType(underlying, f))
      case sc.Type.UserDefined(underlying)        => sc.Type.UserDefined(shortenNamesType(underlying, f))
    }
}

package typr
package internal
package codegen

object FilePackageObject {
  def packageObject(options: InternalOptions): Option[jvm.File] = {
    val parentPkg = NonEmptyList.fromList(options.pkg.idents.dropRight(1))
    val instances = options.dbLib.toList.flatMap(_.missingInstances) ++ options.jsonLibs.flatMap(_.missingInstances)
    if (instances.isEmpty) None
    else {
      val content =
        code"""|${parentPkg.fold(jvm.Code.Empty)(nonEmpty => code"package ${nonEmpty.map(_.code).mkCode(".")}")}
               |
               |package object ${options.pkg.name} {
               |  ${instances.sortBy(_.name).map(_.code).mkCode("\n")}
               |}
               |""".stripMargin

      Some(jvm.File(jvm.Type.Qualified(options.pkg / jvm.Ident("package")), content, secondaryTypes = Nil, scope = Scope.Main))
    }
  }
}

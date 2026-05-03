package typr
package internal
package codegen

import typr.internal.codegen.DbLib.RowType

case class FilesSqlFile(lang: Lang, script: ComputedSqlFile, naming: Naming, options: InternalOptions) {
  val relation = FilesRelation(lang, naming, script.names, script.maybeCols.toOption, None, options, fks = Nil)
  val all: List[jvm.File] = List(
    relation.RowFile(RowType.Readable, comment = None, maybeUnsavedRow = None),
    relation.FieldsFile,
    for {
      dbLib <- options.dbLib
    } yield relation.RepoTraitFile(dbLib, script.repoMethods),
    for {
      dbLib <- options.dbLib
    } yield relation.RepoImplFile(dbLib, script.repoMethods)
  ).flatten
}

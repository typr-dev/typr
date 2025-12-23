package typr
package internal
package codegen

import typr.internal.codegen.DbLib.RowType

case class FilesView(lang: Lang, view: ComputedView, options: InternalOptions) {
  val relation = FilesRelation(lang, view.naming, view.names, Some(view.cols), None, options, fks = Nil)
  val all: List[jvm.File] = List(
    relation.RowFile(RowType.Readable, view.view.comment, maybeUnsavedRow = None),
    relation.FieldsFile,
    for {
      dbLib <- options.dbLib
    } yield relation.RepoTraitFile(dbLib, view.repoMethods),
    for {
      dbLib <- options.dbLib
    } yield relation.RepoImplFile(dbLib, view.repoMethods),
    relation.FieldValueFile
  ).flatten
}

package typo

import typo.internal.FileSync
import typo.internal.FileSync.SoftWrite

import java.nio.file.Path

case class Generated(folder: Path, scope: Scope, files: Map[RelPath, sc.Code]) {
  def mapFiles(f: Map[RelPath, sc.Code] => Map[RelPath, sc.Code]): Generated =
    copy(files = f(files))

  def overwriteFolder(dialect: Dialect, softWrite: SoftWrite = SoftWrite.Yes(Set.empty)): Map[Path, FileSync.Synced] =
    FileSync.syncStrings(
      folder = folder,
      fileRelMap = files.map { case (relPath, code) => (relPath, code.render(dialect).asString) },
      deleteUnknowns = FileSync.DeleteUnknowns.Yes(maxDepth = None),
      softWrite = softWrite
    )
}

object Generated {
  def apply(folder: Path, testFolder: Option[Path], files: Iterator[sc.File]): List[Generated] =
    testFolder match {
      case Some(testFolder) =>
        val (mainFiles, testFiles) = files.partition(_.scope == Scope.Main)
        List(
          Generated(folder, Scope.Main, asRelativePaths(mainFiles)),
          Generated(testFolder, Scope.Test, asRelativePaths(testFiles))
        )
      case None =>
        List(Generated(folder, Scope.Main, asRelativePaths(files)))
    }

  def asRelativePaths(files: Iterator[sc.File]): Map[RelPath, sc.Code] =
    files.map { case sc.File(sc.Type.Qualified(sc.QIdent(idents)), code, _, _) =>
      val path = idents.init
      val name = idents.last
      val relPath = RelPath(path.map(_.value) :+ s"${name.value}.scala")
      relPath -> code
    }.toMap
}

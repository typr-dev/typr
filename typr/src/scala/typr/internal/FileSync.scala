package typr
package internal

import typr.RelPath.PathOps

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}
import java.util

object FileSync {
  sealed trait Synced

  object Synced {
    case object New extends Synced
    case object Changed extends Synced
    case object Unchanged extends Synced
    case object Deleted extends Synced
  }

  sealed trait DeleteUnknowns

  object DeleteUnknowns {
    case object No extends DeleteUnknowns
    case class Yes(maxDepth: Option[Int]) extends DeleteUnknowns
  }

  sealed trait SoftWrite
  object SoftWrite {
    case class Yes(knownUnchanged: Set[RelPath]) extends SoftWrite
    case object No extends SoftWrite
  }

  /** @param soft
    *   compare to existing content in order to not change timestamps. tooling may care a lot about this
    */
  def syncStrings(folder: Path, fileRelMap: Map[RelPath, String], deleteUnknowns: DeleteUnknowns, softWrite: SoftWrite): Map[Path, Synced] =
    syncBytes(folder, fileRelMap.map { case (k, v) => (k, v.getBytes(StandardCharsets.UTF_8)) }, deleteUnknowns, softWrite)

  val longestFirst: Ordering[Path] = Ordering.by[Path, Int](_.getNameCount).reverse

  /** @param soft
    *   compare to existing content in order to not change timestamps. tooling may care a lot about this
    */
  def syncBytes(folder: Path, fileRelMap: Map[RelPath, Array[Byte]], deleteUnknowns: DeleteUnknowns, softWrite: SoftWrite): Map[Path, Synced] = {
    val ret = scala.collection.mutable.Map.empty[Path, Synced]
    val fileMap = fileRelMap.map { case (relPath, content) => (folder / relPath, content) }

    deleteUnknowns match {
      case DeleteUnknowns.Yes(maybeMaxDepth) if folder.toFile.exists() =>
        val stream = maybeMaxDepth match {
          case Some(maxDepth) => Files.walk(folder, maxDepth)
          case None           => Files.walk(folder)
        }

        stream.sorted(longestFirst).forEach {
          case p if Files.isRegularFile(p) && !fileMap.contains(p) =>
            Files.delete(p)
            ret(p) = Synced.Deleted
          case p if Files.isDirectory(p) && !fileMap.keys.exists(_.startsWith(p)) =>
            try Files.delete(p)
            catch {
              case _: java.nio.file.DirectoryNotEmptyException => ()
            }
          case _ => ()
        }

      case _ => ()
    }
    softWrite match {
      case SoftWrite.Yes(knownUnchanged) =>
        val knownUnchanged1 = knownUnchanged.map(folder / _)
        fileMap.foreach { case (file, bytes) =>
          ret(file) =
            if (knownUnchanged1(file)) Synced.Unchanged
            else softWriteBytes(file, bytes)
        }

      case SoftWrite.No =>
        fileMap.foreach { case (file, bytes) =>
          writeBytes(file, bytes)
          ret(file) = Synced.New
        }
    }
    ret.toMap
  }

  def softWriteBytes(path: Path, newContent: Array[Byte]): Synced =
    if (path.toFile.exists()) {
      val existingContent = Files.readAllBytes(path)
      if (util.Arrays.equals(existingContent, newContent)) Synced.Unchanged
      else {
        writeBytes(path, newContent)
        Synced.Changed
      }
    } else {
      writeBytes(path, newContent)
      Synced.New
    }

  def writeBytes(path: Path, newContent: Array[Byte]): Unit = {
    Files.createDirectories(path.getParent)
    Files.write(path, newContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    ()
  }

}

package adventureworks

import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.Assertion
import org.scalatest.funsuite.AnyFunSuite
import zio.jdbc.SqlFragment
import zio.jdbc.SqlFragment.Segment

import java.nio.file.{Files, Path}
import scala.annotation.nowarn

trait SnapshotTest extends AnyFunSuite with TypeCheckedTripleEquals {
  val outFolder: Path =
    SnapshotTest.projectRoot.resolve("snapshot-tests/zio-jdbc-sql")

  def asString(frag: SqlFragment): String = {
    val sql = new StringBuilder()

    def go(frag: SqlFragment): Unit =
      frag.segments.foreach {
        case Segment.Empty          => ()
        case syntax: Segment.Syntax => sql.append(syntax.value)
        case _: Segment.Param       => sql.append('?')
        case nested: Segment.Nested => go(nested.sql)
      }

    go(frag)
    sql.result()
  }

  def compareFragment(fragmentname: String)(ot: Option[SqlFragment]): Unit =
    ot.foreach(sql => writeAndCompare(outFolder.resolve(s"${getClass.getSimpleName}/$fragmentname.sql"), asString(sql)))

  def writeAndCompare(in: Path, contents: String): Assertion = {
    if (SnapshotTest.isCi) {
      if (Files.exists(in)) {
        val existing = Files.readString(in)
        assert(existing == contents)
      } else {
        fail(s"Expected $in to exist")
      }
    } else {
      Files.createDirectories(in.getParent)
      Files.writeString(in, contents)
      // burde sikkert ta en fil-lås, men dette dekker tester i samme JVM
      SnapshotTest.GitLock.synchronized {
        import scala.sys.process.*
        List("git", "add", in.toString).!! : @nowarn
        ()
      }
      succeed
    }
  }
}

object SnapshotTest {
  val `.git`: Path = Path.of(".git")
  val cwd: Path = Path.of(sys.props("user.dir"))

  /** intellij and sbt uses different working directories. This will place us somewhere predictable
    */
  val projectRoot: Path = {
    def lookUpwards(p: Path): Option[Path] =
      if (Files.list(p).anyMatch(p => p.getFileName == `.git`)) Some(p)
      else Option(p.getParent).flatMap(lookUpwards)

    lookUpwards(cwd).getOrElse {
      sys.error(s"Cannot find root of repo (uses ${`.git`} to determine where)")
    }
  }

  val isCi: Boolean =
    sys.env.contains("BUILD_NUMBER") || sys.env.contains("CI") // from sbt

  private object GitLock
}

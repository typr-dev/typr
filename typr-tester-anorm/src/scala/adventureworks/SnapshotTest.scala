package adventureworks

import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.Assertion
import org.scalatest.funsuite.AnyFunSuite
import typr.dsl.Fragment

import java.nio.file.{Files, Path}
import scala.annotation.nowarn

trait SnapshotTest extends AnyFunSuite with TypeCheckedTripleEquals {
  val outFolder: Path =
    SnapshotTest.projectRoot.resolve("snapshot-tests/anorm-sql")

  def compareFragment(fragmentname: String)(ot: Option[Fragment]): Unit = {
    ot.foreach(sql => {
      val str = sql.sql.replaceAll("\\{param[\\d]+\\}", "?")
      writeAndCompare(outFolder.resolve(s"${getClass.getSimpleName}/$fragmentname.sql"), str)
    })
  }

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

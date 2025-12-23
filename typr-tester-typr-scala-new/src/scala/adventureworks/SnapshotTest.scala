package adventureworks

import org.junit.Assert.*
import typr.runtime.Fragment

import java.nio.file.{Files, Path}

trait SnapshotTest {
  private val DOT_GIT = Path.of(".git")
  private val CWD = Path.of(System.getProperty("user.dir"))
  private val GIT_LOCK = new Object

  protected val PROJECT_ROOT: Path = findProjectRoot()
  protected val IS_CI: Boolean = System.getenv("BUILD_NUMBER") != null || System.getenv("CI") != null

  protected def outFolder(): Path = PROJECT_ROOT.resolve("snapshot-tests/scala-new-sql")

  private def findProjectRoot(): Path = {
    var p: Path = CWD
    while (p != null) {
      try {
        val stream = Files.list(p)
        try {
          if (stream.anyMatch(path => path.getFileName == DOT_GIT)) {
            return p
          }
        } finally {
          stream.close()
        }
      } catch {
        case _: Exception => // ignore
      }
      p = p.getParent
    }
    throw new RuntimeException(s"Cannot find root of repo (uses $DOT_GIT to determine where)")
  }

  protected def compareFragment(fragmentName: String, fragmentOpt: Option[Fragment]): Unit = {
    fragmentOpt.foreach { fragment =>
      val str = fragment.render().replaceAll("\\{param[\\d]+\\}", "?")
      writeAndCompare(outFolder().resolve(s"${getClass.getSimpleName}/$fragmentName.sql"), str)
    }
  }

  protected def writeAndCompare(in: Path, contents: String): Unit = {
    if (IS_CI) {
      if (Files.exists(in)) {
        val existing = Files.readString(in)
        assertEquals(existing, contents)
      } else {
        fail(s"Expected $in to exist")
      }
    } else {
      Files.createDirectories(in.getParent)
      Files.writeString(in, contents)
      GIT_LOCK.synchronized {
        val _ = new ProcessBuilder("git", "add", in.toString).inheritIO().start().waitFor()
      }
    }
  }
}

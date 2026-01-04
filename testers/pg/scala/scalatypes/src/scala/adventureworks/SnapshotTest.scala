package adventureworks

import dev.typr.testutils.SnapshotTestUtils
import dev.typr.foundations.Fragment
import org.junit.Assert.*

import java.nio.file.Path

trait SnapshotTest {
  protected def outFolder(): Path = SnapshotTestUtils.projectRoot().resolve("snapshot-tests/scala-new-sql")

  protected def compareFragment(fragmentName: String, fragmentOpt: Option[Fragment]): Unit = {
    fragmentOpt.foreach { fragment =>
      val str = fragment.render().replaceAll("\\{param[\\d]+\\}", "?")
      writeAndCompare(outFolder().resolve(s"${getClass.getSimpleName}/$fragmentName.sql"), str)
    }
  }

  protected def writeAndCompare(in: Path, contents: String): Unit = {
    SnapshotTestUtils.writeAndCompare(in, contents)
  }
}

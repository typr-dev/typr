package adventureworks

import dev.typr.testutils.SnapshotTestUtils
import dev.typr.foundations.Fragment
import org.junit.Assert.*

import java.nio.file.Path

trait SnapshotTest {
  protected def outFolder(): Path = SnapshotTestUtils.projectRoot().resolve("snapshot-tests/scala-old-sql")

  protected def compareFragment(fragmentName: String, fragmentOpt: java.util.Optional[Fragment]): Unit = {
    if (fragmentOpt.isPresent) {
      val fragment = fragmentOpt.get()
      val str = fragment.render().replaceAll("\\{param[\\d]+\\}", "?")
      writeAndCompare(outFolder().resolve(s"${getClass.getSimpleName}/$fragmentName.sql"), str)
    }
  }

  protected def writeAndCompare(in: Path, contents: String): Unit = {
    SnapshotTestUtils.writeAndCompare(in, contents)
  }
}

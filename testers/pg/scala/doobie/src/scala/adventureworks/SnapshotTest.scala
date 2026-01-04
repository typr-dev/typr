package adventureworks

import dev.typr.testutils.SnapshotTestUtils
import doobie.util.fragment.Fragment
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.Assertion
import org.scalatest.funsuite.AnyFunSuite

import java.nio.file.Path

trait SnapshotTest extends AnyFunSuite with TypeCheckedTripleEquals {
  val outFolder: Path =
    SnapshotTestUtils.projectRoot().resolve("snapshot-tests/dooble-sql")

  def compareFragment(fragmentname: String)(ot: Option[Fragment]): Unit = {
    ot.foreach(sql => {
      writeAndCompare(outFolder.resolve(s"${getClass.getSimpleName}/$fragmentname.sql"), sql.internals.sql)
    })
  }

  def writeAndCompare(in: Path, contents: String): Assertion = {
    SnapshotTestUtils.writeAndCompare(in, contents)
    succeed
  }
}

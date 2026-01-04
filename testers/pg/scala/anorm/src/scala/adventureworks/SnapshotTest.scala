package adventureworks

import dev.typr.testutils.SnapshotTestUtils
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.Assertion
import org.scalatest.funsuite.AnyFunSuite
import typr.dsl.Fragment

import java.nio.file.Path

trait SnapshotTest extends AnyFunSuite with TypeCheckedTripleEquals {
  val outFolder: Path =
    SnapshotTestUtils.projectRoot().resolve("snapshot-tests/anorm-sql")

  def compareFragment(fragmentname: String)(ot: Option[Fragment]): Unit = {
    ot.foreach(sql => {
      val str = sql.sql.replaceAll("\\{param[\\d]+\\}", "?")
      writeAndCompare(outFolder.resolve(s"${getClass.getSimpleName}/$fragmentname.sql"), str)
    })
  }

  def writeAndCompare(in: Path, contents: String): Assertion = {
    SnapshotTestUtils.writeAndCompare(in, contents)
    succeed
  }
}

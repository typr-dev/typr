package adventureworks

import dev.typr.testutils.SnapshotTestUtils
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.Assertion
import org.scalatest.funsuite.AnyFunSuite
import zio.jdbc.SqlFragment
import zio.jdbc.SqlFragment.Segment

import java.nio.file.Path

trait SnapshotTest extends AnyFunSuite with TypeCheckedTripleEquals {
  val outFolder: Path =
    SnapshotTestUtils.projectRoot().resolve("snapshot-tests/zio-jdbc-sql")

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
    SnapshotTestUtils.writeAndCompare(in, contents)
    succeed
  }
}

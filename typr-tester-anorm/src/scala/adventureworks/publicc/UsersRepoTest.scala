package adventureworks.publicc

import adventureworks.customtypes.*
import adventureworks.public.users.*
import adventureworks.withConnection
import anorm.{SqlParser, SqlStringInterpolation}
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.Assertion
import org.scalatest.funsuite.AnyFunSuite

class UsersRepoTest extends AnyFunSuite with TypeCheckedTripleEquals {
  def testRoundtrip(usersRepo: UsersRepo): Assertion = {
    withConnection { implicit c =>
      val before = UsersRowUnsaved(
        userId = UsersId(TypoUUID.randomUUID),
        name = "name",
        lastName = Some("last_name"),
        email = TypoUnknownCitext("email@asd.no"),
        password = "password",
        verifiedOn = Some(TypoInstant.now),
        createdAt = Defaulted.Provided(TypoInstant.now)
      )
      val _ = usersRepo.insert(before)
      val after = usersRepo.select.where(p => p.userId === before.userId).toList.head
      assert(before.toRow(after.createdAt) === after)
    }
  }

  def testInsertUnsavedStreaming(usersRepo: UsersRepo): Assertion = {
    val before = List.tabulate(10)(idx =>
      UsersRowUnsaved(
        userId = UsersId(TypoUUID.randomUUID),
        name = "name",
        lastName = Some("last_name"),
        email = TypoUnknownCitext(s"email-$idx@asd.no"),
        password = "password",
        verifiedOn = Some(TypoInstant.now)
      )
    )

    withConnection { implicit c =>
      val _ = usersRepo.insertUnsavedStreaming(before.iterator, 2)
      val after = usersRepo.selectByIds(before.map(_.userId).toArray)
      val beforeById = before.map(row => row.userId -> row).toMap
      after.foreach { after =>
        assert(after === beforeById(after.userId).toRow(after.createdAt))
      }
      succeed
    }
  }

  test("testRoundtrip in-memory") {
    testRoundtrip(usersRepo = new UsersRepoMock(_.toRow(???)))
  }
  test("testRoundtrip pg") {
    testRoundtrip(usersRepo = new UsersRepoImpl)
  }

  test("testInsertUnsavedStreaming in-memory") {
    testInsertUnsavedStreaming(usersRepo = new UsersRepoMock(_.toRow(TypoInstant.now)))
  }

  test("testInsertUnsavedStreaming pg") {
    val versionString = withConnection(implicit c => SQL"SELECT VERSION()".as(SqlParser.get[String](1).single))
    val version = versionString.split(' ')(1)
    version.toDouble match {
      case n if n >= 16 => testInsertUnsavedStreaming(new UsersRepoImpl)
      case _            => System.err.println(s"Skipping testInsertUnsavedStreaming pg because version $version < 16")
    }
  }
}

package adventureworks.publicc

import adventureworks.customtypes.*
import adventureworks.public.users.*
import adventureworks.withConnection
import doobie.implicits.toSqlInterpolator
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.Assertion
import org.scalatest.funsuite.AnyFunSuite

class UsersRepoTest extends AnyFunSuite with TypeCheckedTripleEquals {
  def testRoundtrip(usersRepo: UsersRepo): Assertion = {
    withConnection {
      val before = UsersRowUnsaved(
        userId = UsersId(TypoUUID.randomUUID),
        name = "name",
        lastName = Some("last_name"),
        email = TypoUnknownCitext("email@asd.no"),
        password = "password",
        verifiedOn = Some(TypoInstant.now),
        createdAt = Defaulted.Provided(TypoInstant.now)
      )

      for {
        _ <- usersRepo.insert(before)
        after <- usersRepo.select.where(p => p.userId === before.userId).toList.map(_.head)
      } yield assert(before.toRow(after.createdAt) === after)
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

    withConnection {
      for {
        _ <- usersRepo.insertUnsavedStreaming(fs2.Stream.emits(before), 2)
        after <- usersRepo.selectByIds(before.map(_.userId).toArray).compile.toList
      } yield {
        val beforeById = before.map(row => row.userId -> row).toMap
        after.foreach { after =>
          assert(after === beforeById(after.userId).toRow(after.createdAt))
        }
        succeed
      }
    }
  }

  test("testRoundtrip in-memory") {
    testRoundtrip(usersRepo = new UsersRepoMock(_.toRow(TypoInstant.now)))
  }

  test("testRoundtrip pg") {
    testRoundtrip(usersRepo = new UsersRepoImpl)
  }

  test("testInsertUnsavedStreaming in-memory") {
    testInsertUnsavedStreaming(usersRepo = new UsersRepoMock(_.toRow(TypoInstant.now)))
  }

  test("testInsertUnsavedStreaming pg") {
    val versionString = withConnection(sql"SELECT VERSION()".query[String].unique)
    val version = versionString.split(' ')(1)
    version.toDouble match {
      case n if n >= 16 => testInsertUnsavedStreaming(new UsersRepoImpl)
      case _            => System.err.println(s"Skipping testInsertUnsavedStreaming pg because version $version < 16")
    }
  }
}

package adventureworks.publicc

import adventureworks.customtypes.*
import adventureworks.public.users.*
import adventureworks.withConnection
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.Assertion
import org.scalatest.funsuite.AnyFunSuite
import zio.Chunk
import zio.jdbc.sqlInterpolator
import zio.stream.ZStream

class UsersRepoTest extends AnyFunSuite with TypeCheckedTripleEquals {
  def testRoundtrip(usersRepo: UsersRepo): Assertion = {
    withConnection {
      val unsaved = UsersRowUnsaved(
        userId = UsersId(TypoUUID.randomUUID),
        name = "name",
        lastName = Some("last_name"),
        email = TypoUnknownCitext("email@asd.no"),
        password = "password",
        verifiedOn = Some(TypoInstant.now),
        createdAt = Defaulted.Provided(TypoInstant.now)
      )
      for {
        _ <- usersRepo.insert(unsaved)
        all <- usersRepo.select.where(p => p.userId === unsaved.userId).toChunk
      } yield {
        assert(unsaved.toRow(???) === all.head)
      }
    }
  }

  def testInsertUnsavedStreaming(usersRepo: UsersRepo): Assertion = {
    val before = Chunk.tabulate(10)(idx =>
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
        _ <- usersRepo.insertUnsavedStreaming(ZStream.fromChunk(before), 2)
        after <- usersRepo.selectByIds(before.map(_.userId).toArray).runCollect
      } yield {
        after.foreach { after =>
          val beforeById = before.map(row => row.userId -> row).toMap
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
    val versionString = withConnection(sql"SELECT VERSION()".query[String].selectOne.map(_.get))
    val version = versionString.split(' ')(1)
    version.toDouble match {
      case n if n >= 16 => testInsertUnsavedStreaming(new UsersRepoImpl)
      case _            => System.err.println(s"Skipping testInsertUnsavedStreaming pg because version $version < 16")
    }
  }

}

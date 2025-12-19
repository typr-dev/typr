package adventureworks.public_.users

import adventureworks.{DbNow, WithConnection}
import adventureworks.customtypes.Defaulted
import adventureworks.public.users.*
import org.junit.Assert.*
import org.junit.Test
import typo.data.Unknown

import java.sql.Connection
import java.util.{Optional, UUID}
import scala.jdk.CollectionConverters.*

class UsersRepoTest {

  private def testRoundtrip(usersRepo: UsersRepo): Unit = {
    WithConnection {
      val before = UsersRowUnsaved(
        userId = UsersId(UUID.randomUUID()),
        name = "name",
        email = Unknown("email@asd.no"),
        password = "password"
      ).copy(
        lastName = Optional.of("last_name"),
        verifiedOn = Optional.of(DbNow.instant()),
        createdAt = Defaulted.Provided(DbNow.instant())
      )

      val _ = usersRepo.insert(before)

      val foundList = usersRepo.select
        .where(p => p.userId.isEqual(before.userId))
        .toList(summon[Connection])
        .asScala
        .toList

      assertEquals(1, foundList.size)
      val after = foundList.head

      assertEquals(before.toRow(after.createdAt), after)
    }
  }

  private def testInsertUnsavedStreaming(usersRepo: UsersRepo): Unit = {
    WithConnection {
      val before = (0 until 10).map { idx =>
        UsersRowUnsaved(
          userId = UsersId(UUID.randomUUID()),
          name = "name",
          email = Unknown(s"email-$idx@asd.no"),
          password = "password"
        ).copy(
          lastName = Optional.of("last_name"),
          verifiedOn = Optional.of(DbNow.instant())
        )
      }.toList

      val _ = usersRepo.insertUnsavedStreaming(before.iterator.asJava, 2)

      val ids = before.map(_.userId).toArray
      val afterList = usersRepo.selectByIds(ids).asScala.toList

      val beforeById = before.map(row => row.userId -> row).toMap

      assertEquals(before.size, afterList.size)

      afterList.foreach { after =>
        val beforeRow = beforeById(after.userId)
        assertNotNull(beforeRow)
        assertEquals(beforeRow.toRow(after.createdAt), after)
      }
    }
  }

  @Test
  def testRoundtripInMemory(): Unit = {
    testRoundtrip(new UsersRepoMock(unsaved => unsaved.toRow(DbNow.instant())))
  }

  @Test
  def testRoundtripPg(): Unit = {
    testRoundtrip(new UsersRepoImpl)
  }

  @Test
  def testInsertUnsavedStreamingInMemory(): Unit = {
    testInsertUnsavedStreaming(new UsersRepoMock(unsaved => unsaved.toRow(DbNow.instant())))
  }

  @Test
  def testInsertUnsavedStreamingPg(): Unit = {
    val shouldRun = WithConnection {
      val versionResult = typo.runtime.Fragment
        .lit("SELECT VERSION()")
        .query(
          typo.runtime.RowParsers
            .of(
              typo.runtime.PgTypes.text,
              (s: String) => s,
              (s: String) => Array[Object](s)
            )
            .first()
        )
        .runUnchecked(summon[Connection])

      if (versionResult.isEmpty) {
        System.err.println("Could not determine PostgreSQL version")
        false
      } else {
        val versionString = versionResult.get()
        val parts = versionString.split(" ")
        val version = parts(1).split("\\.")(0).toDouble

        if (version < 16) {
          System.err.println(s"Skipping testInsertUnsavedStreaming pg because version $version < 16")
          false
        } else {
          true
        }
      }
    }

    if (shouldRun) {
      testInsertUnsavedStreaming(new UsersRepoImpl)
    }
  }
}

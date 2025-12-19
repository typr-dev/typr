package adventureworks.public

import adventureworks.DbNow
import adventureworks.WithConnection
import adventureworks.customtypes.Defaulted
import java.time.Instant
import typo.data.Unknown
import java.util.UUID
import adventureworks.public.users.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.Connection

class UsersRepoTest {
    private fun testRoundtrip(usersRepo: UsersRepo) {
        WithConnection.run { c ->
            val before = UsersRowUnsaved(
                userId = UsersId(UUID.randomUUID()),
                name = "name",
                lastName = "last_name",
                email = Unknown("email@asd.no"),
                password = "password",
                verifiedOn = DbNow.instant(),
                createdAt = Defaulted.Provided(DbNow.instant())
            )
            usersRepo.insert(before, c)
            val after = usersRepo.select().where { it.userId().isEqual(before.userId) }.toList(c).first()
            assertEquals(before.toRow { after.createdAt }, after)
        }
    }

    private fun testInsertUnsavedStreaming(usersRepo: UsersRepo) {
        val before = (0 until 10).map { idx ->
            UsersRowUnsaved(
                userId = UsersId(UUID.randomUUID()),
                name = "name",
                lastName = "last_name",
                email = Unknown("email-$idx@asd.no"),
                password = "password",
                verifiedOn = DbNow.instant()
            )
        }

        WithConnection.run { c ->
            usersRepo.insertUnsavedStreaming(before.iterator(), 2, c)
            val after = usersRepo.selectByIds(before.map { it.userId }.toTypedArray(), c)
            val beforeById = before.associateBy { it.userId }
            after.forEach { afterRow ->
                assertEquals(beforeById[afterRow.userId]?.toRow { afterRow.createdAt }, afterRow)
            }
        }
    }

    @Test
    fun testRoundtripInMemory() {
        testRoundtrip(usersRepo = UsersRepoMock(toRow = { it.toRow { Instant.EPOCH } }))
    }

    @Test
    fun testRoundtripPg() {
        testRoundtrip(usersRepo = UsersRepoImpl())
    }

    @Test
    fun testInsertUnsavedStreamingInMemory() {
        testInsertUnsavedStreaming(usersRepo = UsersRepoMock(toRow = { it.toRow { DbNow.instant() } }))
    }

    @Test
    fun testInsertUnsavedStreamingPg() {
        val versionString = WithConnection.apply { c: Connection ->
            val stmt = c.createStatement()
            val rs = stmt.executeQuery("SELECT VERSION()")
            rs.next()
            rs.getString(1)
        }
        val version = versionString.split(" ")[1]
        val versionNumber = version.toDouble()
        if (versionNumber >= 16) {
            testInsertUnsavedStreaming(UsersRepoImpl())
        } else {
            System.err.println("Skipping testInsertUnsavedStreaming pg because version $version < 16")
        }
    }
}

package adventureworks.public_.users

import adventureworks.WithConnection
import adventureworks.customtypes.Defaulted
import adventureworks.customtypes.TypoInstant
import adventureworks.customtypes.TypoUUID
import adventureworks.customtypes.TypoUnknownCitext
import adventureworks.public.users.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.Optional

/**
 * Tests for UsersRepo functionality with UUID and custom types.
 */
class UsersRepoTest {

    private fun testRoundtrip(usersRepo: UsersRepo) {
        WithConnection.run { c ->
            val before = UsersRowUnsaved(
                userId = UsersId(TypoUUID.randomUUID()),
                name = "name",
                lastName = Optional.of("last_name"),
                email = TypoUnknownCitext("email@asd.no"),
                password = "password",
                verifiedOn = Optional.of(TypoInstant.now()),
                createdAt = Defaulted.Provided(TypoInstant.now())
            )

            usersRepo.insert(before, c)

            // Use DSL to query - DSL fields use method calls
            val foundList = usersRepo.select()
                .where { p -> p.userId().isEqual(before.userId) }
                .toList(c)

            assertEquals(1, foundList.size)
            val after = foundList[0]

            // Compare using toRow with the actual createdAt from the database
            assertEquals(before.toRow { after.createdAt }, after)
        }
    }

    private fun testInsertUnsavedStreaming(usersRepo: UsersRepo) {
        WithConnection.run { c ->
            val before = mutableListOf<UsersRowUnsaved>()
            for (idx in 0 until 10) {
                before.add(UsersRowUnsaved(
                    userId = UsersId(TypoUUID.randomUUID()),
                    name = "name",
                    lastName = Optional.of("last_name"),
                    email = TypoUnknownCitext("email-$idx@asd.no"),
                    password = "password",
                    verifiedOn = Optional.of(TypoInstant.now())
                ))
            }

            usersRepo.insertUnsavedStreaming(before.iterator(), 2, c)

            val ids = before.map { it.userId }.toTypedArray()
            val afterList = usersRepo.selectByIds(ids, c)

            val beforeById = before.associateBy { it.userId }

            assertEquals(before.size, afterList.size)

            for (after in afterList) {
                val beforeRow = beforeById[after.userId]
                assertNotNull(beforeRow, "Should find matching before row")
                assertEquals(beforeRow!!.toRow { after.createdAt }, after)
            }
        }
    }

    @Test
    fun testRoundtripInMemory() {
        testRoundtrip(UsersRepoMock({ unsaved -> unsaved.toRow { TypoInstant.now() } }))
    }

    @Test
    fun testRoundtripPg() {
        testRoundtrip(UsersRepoImpl())
    }

    @Test
    fun testInsertUnsavedStreamingInMemory() {
        testInsertUnsavedStreaming(UsersRepoMock({ unsaved -> unsaved.toRow { TypoInstant.now() } }))
    }

    @Test
    fun testInsertUnsavedStreamingPg() {
        // Check PostgreSQL version first - streaming insert requires PG >= 16
        val shouldRun = WithConnection.apply { c ->
            val versionResult = typo.runtime.Fragment.lit("SELECT VERSION()")
                .query(typo.runtime.RowParsers.of(typo.runtime.PgTypes.text, { s -> s }, { s -> arrayOf<Any?>(s) }).first())
                .runUnchecked(c)

            if (versionResult.isEmpty) {
                System.err.println("Could not determine PostgreSQL version")
                return@apply false
            }

            val versionString = versionResult.get()
            val parts = versionString.split(" ")
            val version = parts[1].split(".")[0].toDouble()

            if (version < 16) {
                System.err.println("Skipping testInsertUnsavedStreaming pg because version $version < 16")
                return@apply false
            }
            true
        }

        if (shouldRun) {
            testInsertUnsavedStreaming(UsersRepoImpl())
        }
    }
}

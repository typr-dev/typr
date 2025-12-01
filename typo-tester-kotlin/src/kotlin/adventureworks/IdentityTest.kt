package adventureworks

import adventureworks.public.identity_test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class IdentityTest {
    private val repo = IdentityTestRepoImpl()

    @Test
    fun works() {
        WithConnection.run { c ->
            // Use short ctor
            val unsaved = IdentityTestRowUnsaved(IdentityTestId("a"))

            // insert
            val inserted = repo.insert(unsaved, c)

            // upsert
            val upserted = repo.upsert(inserted, c)
            assertEquals(inserted, upserted)

            // DSL is disabled for Kotlin - use selectAll instead
            val rows = repo.selectAll(c)

            assertEquals(1, rows.size)
            val row = rows[0]
            assertEquals(IdentityTestId("a"), row.name)
            // always_generated and default_generated are auto-generated, just verify they exist
            assertNotNull(row.alwaysGenerated)
            assertNotNull(row.defaultGenerated)
        }
    }
}

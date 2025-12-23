package oracledb.contacts

import oracledb.EmailTableT
import oracledb.OracleTestHelper
import oracledb.TagVarrayT
import oracledb.customtypes.Defaulted
import org.junit.Assert.*
import org.junit.Test

class ContactsTest {
    private val repo = ContactsRepoImpl()

    @Test
    fun testInsertContactWithNestedTableAndVarray() {
        OracleTestHelper.run { c ->
            val emails = EmailTableT(arrayOf("john@example.com", "john.doe@work.com", "jdoe@personal.net"))
            val tags = TagVarrayT(arrayOf("customer", "vip"))

            val unsaved = ContactsRowUnsaved(
                "John Doe",
                emails,
                tags,
                Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)

            assertNotNull(insertedId)
            val inserted = repo.selectById(insertedId, c)!!
            assertEquals("John Doe", inserted.name)
            assertNotNull(inserted.emails)
            assertEquals(3, inserted.emails!!.value.size)
            assertNotNull(inserted.tags)
            assertEquals(2, inserted.tags!!.value.size)
        }
    }

    @Test
    fun testInsertContactWithOnlyEmails() {
        OracleTestHelper.run { c ->
            val emails = EmailTableT(arrayOf("jane@example.com"))

            val unsaved = ContactsRowUnsaved(
                "Jane Smith",
                emails,
                null,
                Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)
            val inserted = repo.selectById(insertedId, c)!!

            assertNotNull(inserted.emails)
            assertEquals(1, inserted.emails!!.value.size)
            assertNull(inserted.tags)
        }
    }

    @Test
    fun testInsertContactWithOnlyTags() {
        OracleTestHelper.run { c ->
            val tags = TagVarrayT(arrayOf("partner", "active"))

            val unsaved = ContactsRowUnsaved(
                "No Email Contact",
                null,
                tags,
                Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)
            val inserted = repo.selectById(insertedId, c)!!

            assertNull(inserted.emails)
            assertNotNull(inserted.tags)
        }
    }

    @Test
    fun testInsertContactWithNoCollections() {
        OracleTestHelper.run { c ->
            val unsaved = ContactsRowUnsaved(
                "Minimal Contact",
                null,
                null,
                Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)
            val inserted = repo.selectById(insertedId, c)!!

            assertEquals("Minimal Contact", inserted.name)
            assertNull(inserted.emails)
            assertNull(inserted.tags)
        }
    }

    @Test
    fun testUpdateContactEmails() {
        OracleTestHelper.run { c ->
            val originalEmails = EmailTableT(arrayOf("old@example.com"))
            val tags = TagVarrayT(arrayOf("test"))

            val unsaved = ContactsRowUnsaved(
                "Email Update Test",
                originalEmails,
                tags,
                Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)
            val inserted = repo.selectById(insertedId, c)!!

            val newEmails = EmailTableT(arrayOf("new1@example.com", "new2@example.com"))
            val updated = inserted.copy(emails = newEmails)

            val wasUpdated = repo.update(updated, c)
            assertTrue(wasUpdated)

            val fetched = repo.selectById(insertedId, c)!!
            assertEquals(2, fetched.emails!!.value.size)
            assertTrue(fetched.emails!!.value.contains("new1@example.com"))
            assertTrue(fetched.emails!!.value.contains("new2@example.com"))
        }
    }

    @Test
    fun testUpdateContactTags() {
        OracleTestHelper.run { c ->
            val emails = EmailTableT(arrayOf("tags@example.com"))
            val originalTags = TagVarrayT(arrayOf("old", "tags"))

            val unsaved = ContactsRowUnsaved(
                "Tags Update Test",
                emails,
                originalTags,
                Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)
            val inserted = repo.selectById(insertedId, c)!!

            val newTags = TagVarrayT(arrayOf("new", "updated", "tags"))
            val updated = inserted.copy(tags = newTags)

            val wasUpdated = repo.update(updated, c)
            assertTrue(wasUpdated)

            val fetched = repo.selectById(insertedId, c)!!
            assertNotNull(fetched.tags)
            assertEquals(3, fetched.tags!!.value.size)
        }
    }

    @Test
    fun testDeleteContact() {
        OracleTestHelper.run { c ->
            val emails = EmailTableT(arrayOf("delete@example.com"))
            val tags = TagVarrayT(arrayOf("delete"))

            val unsaved = ContactsRowUnsaved(
                "To Delete",
                emails,
                tags,
                Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)

            val deleted = repo.deleteById(insertedId, c)
            assertTrue(deleted)

            val found = repo.selectById(insertedId, c)
            assertNull(found)
        }
    }

    @Test
    fun testSelectAllContacts() {
        OracleTestHelper.run { c ->
            val emails1 = EmailTableT(arrayOf("contact1@example.com"))
            val emails2 = EmailTableT(arrayOf("contact2@example.com"))

            repo.insert(ContactsRowUnsaved("Contact 1", emails1, null, Defaulted.UseDefault()), c)
            repo.insert(ContactsRowUnsaved("Contact 2", emails2, null, Defaulted.UseDefault()), c)

            val all = repo.selectAll(c)
            assertTrue(all.size >= 2)
        }
    }

    @Test
    fun testEmailTableRoundtrip() {
        OracleTestHelper.run { c ->
            val emailArray = arrayOf("email1@test.com", "email2@test.com", "email3@test.com", "email4@test.com", "email5@test.com")
            val emails = EmailTableT(emailArray)

            val unsaved = ContactsRowUnsaved(
                "Email Roundtrip Test",
                emails,
                null,
                Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)
            val inserted = repo.selectById(insertedId, c)!!

            assertArrayEquals(emailArray, inserted.emails!!.value)
        }
    }

    @Test
    fun testClearTags() {
        OracleTestHelper.run { c ->
            val emails = EmailTableT(arrayOf("clear@example.com"))
            val originalTags = TagVarrayT(arrayOf("tag1", "tag2"))

            val unsaved = ContactsRowUnsaved(
                "Clear Tags Test",
                emails,
                originalTags,
                Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)
            val inserted = repo.selectById(insertedId, c)!!
            assertNotNull(inserted.tags)

            val cleared = inserted.copy(tags = null)
            val wasUpdated = repo.update(cleared, c)

            assertTrue(wasUpdated)
            val fetched = repo.selectById(insertedId, c)!!
            assertNull(fetched.tags)
        }
    }

    @Test
    fun testUpdateBothCollections() {
        OracleTestHelper.run { c ->
            val originalEmails = EmailTableT(arrayOf("original@test.com"))
            val originalTags = TagVarrayT(arrayOf("original"))

            val unsaved = ContactsRowUnsaved(
                "Update Both Test",
                originalEmails,
                originalTags,
                Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)
            val inserted = repo.selectById(insertedId, c)!!

            val newEmails = EmailTableT(arrayOf("updated1@test.com", "updated2@test.com"))
            val newTags = TagVarrayT(arrayOf("updated1", "updated2", "updated3"))

            val updated = inserted.copy(emails = newEmails, tags = newTags)

            val wasUpdated = repo.update(updated, c)
            assertTrue(wasUpdated)
            val fetched = repo.selectById(insertedId, c)!!
            assertNotNull(fetched.emails)
            assertNotNull(fetched.tags)
            assertEquals(2, fetched.emails!!.value.size)
            assertEquals(3, fetched.tags!!.value.size)
        }
    }

    @Test
    fun testClearEmails() {
        OracleTestHelper.run { c ->
            val emails = EmailTableT(arrayOf("clear@test.com"))
            val unsaved = ContactsRowUnsaved(
                "Clear Emails Test",
                emails,
                null,
                Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)
            val inserted = repo.selectById(insertedId, c)!!
            assertNotNull(inserted.emails)

            val cleared = inserted.copy(emails = null)
            val wasUpdated = repo.update(cleared, c)

            assertTrue(wasUpdated)
            val fetched = repo.selectById(insertedId, c)!!
            assertNull(fetched.emails)
        }
    }

    @Test
    fun testNestedTableWithManyEmails() {
        OracleTestHelper.run { c ->
            val manyEmails = Array(20) { i -> "email$i@test.com" }
            val emails = EmailTableT(manyEmails)

            val unsaved = ContactsRowUnsaved(
                "Many Emails Test",
                emails,
                null,
                Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)
            val inserted = repo.selectById(insertedId, c)!!
            assertNotNull(inserted.emails)
            assertEquals(20, inserted.emails!!.value.size)
        }
    }

    @Test
    fun testNestedTableVsVarrayDifference() {
        val nestedTable1 = EmailTableT(arrayOf("a@test.com", "b@test.com"))
        val nestedTable2 = EmailTableT(arrayOf("a@test.com", "b@test.com"))

        // Different instances with same content - equals uses reference equality for arrays
        assertFalse(nestedTable1 == nestedTable2)
        assertArrayEquals(nestedTable1.value, nestedTable2.value)

        val varray1 = TagVarrayT(arrayOf("tag1", "tag2"))
        val varray2 = TagVarrayT(arrayOf("tag1", "tag2"))

        assertFalse(varray1 == varray2)
        assertArrayEquals(varray1.value, varray2.value)
    }

    @Test
    fun testEmptyEmailArray() {
        OracleTestHelper.run { c ->
            val emptyEmails = EmailTableT(arrayOf())

            val unsaved = ContactsRowUnsaved(
                "Empty Emails Test",
                emptyEmails,
                null,
                Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)
            val inserted = repo.selectById(insertedId, c)!!
            assertNotNull(inserted.emails)
            assertEquals(0, inserted.emails!!.value.size)
        }
    }
}

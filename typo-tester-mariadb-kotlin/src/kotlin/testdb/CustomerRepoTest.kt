package testdb

import org.junit.Assert.*
import org.junit.Test
import testdb.customer_status.CustomerStatusId
import testdb.customer_status.CustomerStatusRepoImpl
import testdb.customers.CustomersId
import testdb.customers.CustomersRepoImpl
import testdb.customers.CustomersRow
import testdb.customers.CustomersRowUnsaved
import testdb.customtypes.Defaulted.Provided
import testdb.customtypes.Defaulted.UseDefault

/**
 * Full repository test for customers - tests all CRUD operations and more.
 * Note: The database has seeded customer_status data including 'active', 'pending', 'suspended', 'closed'.
 * Tests use these existing statuses rather than inserting new ones.
 */
class CustomerRepoTest {
    private val customersRepo = CustomersRepoImpl()
    private val customerStatusRepo = CustomerStatusRepoImpl()

    companion object {
        private val ACTIVE_STATUS = CustomerStatusId("active")
        private val PENDING_STATUS = CustomerStatusId("pending")
    }

    @Test
    fun testSelectAll() {
        WithConnection.run { c ->
            customersRepo.insert(
                CustomersRowUnsaved(
                    email = "c1@example.com",
                    passwordHash = "hash1".toByteArray(),
                    firstName = "Customer",
                    lastName = "One"
                ),
                c
            )
            customersRepo.insert(
                CustomersRowUnsaved(
                    email = "c2@example.com",
                    passwordHash = "hash2".toByteArray(),
                    firstName = "Customer",
                    lastName = "Two"
                ),
                c
            )

            val all = customersRepo.selectAll(c)
            assertEquals(2, all.size)
        }
    }

    @Test
    fun testSelectById() {
        WithConnection.run { c ->
            val unsaved = CustomersRowUnsaved(
                email = "test@example.com",
                passwordHash = "hash".toByteArray(),
                firstName = "Test",
                lastName = "User"
            )
            val inserted = customersRepo.insert(unsaved, c)

            val selected = customersRepo.selectById(inserted.customerId, c)
            assertNotNull(selected)
            assertEquals("test@example.com", selected!!.email)
            assertEquals("Test", selected.firstName)
            assertEquals("User", selected.lastName)
        }
    }

    @Test
    fun testSelectByIds() {
        WithConnection.run { c ->
            val customer1 = customersRepo.insert(
                CustomersRowUnsaved(
                    email = "c1@example.com",
                    passwordHash = "hash1".toByteArray(),
                    firstName = "Customer",
                    lastName = "One"
                ),
                c
            )
            val customer2 = customersRepo.insert(
                CustomersRowUnsaved(
                    email = "c2@example.com",
                    passwordHash = "hash2".toByteArray(),
                    firstName = "Customer",
                    lastName = "Two"
                ),
                c
            )
            val customer3 = customersRepo.insert(
                CustomersRowUnsaved(
                    email = "c3@example.com",
                    passwordHash = "hash3".toByteArray(),
                    firstName = "Customer",
                    lastName = "Three"
                ),
                c
            )

            val ids = arrayOf(customer1.customerId, customer3.customerId)
            val selected = customersRepo.selectByIds(ids, c)

            assertEquals(2, selected.size)
        }
    }

    @Test
    fun testSelectByIdsTracked() {
        WithConnection.run { c ->
            val customer1 = customersRepo.insert(
                CustomersRowUnsaved(
                    email = "c1@example.com",
                    passwordHash = "hash1".toByteArray(),
                    firstName = "Customer",
                    lastName = "One"
                ),
                c
            )
            val customer2 = customersRepo.insert(
                CustomersRowUnsaved(
                    email = "c2@example.com",
                    passwordHash = "hash2".toByteArray(),
                    firstName = "Customer",
                    lastName = "Two"
                ),
                c
            )

            val ids = arrayOf(customer1.customerId, customer2.customerId)
            val tracked = customersRepo.selectByIdsTracked(ids, c)

            assertEquals(2, tracked.size)
            assertEquals("Customer", tracked[customer1.customerId]?.firstName)
            assertEquals("Two", tracked[customer2.customerId]?.lastName)
        }
    }

    @Test
    fun testSelectByUniqueEmail() {
        WithConnection.run { c ->
            val inserted = customersRepo.insert(
                CustomersRowUnsaved(
                    email = "unique@example.com",
                    passwordHash = "hash".toByteArray(),
                    firstName = "Unique",
                    lastName = "User"
                ),
                c
            )

            val selected = customersRepo.selectByUniqueEmail("unique@example.com", c)
            assertNotNull(selected)
            assertEquals(inserted.customerId, selected!!.customerId)

            val notFound = customersRepo.selectByUniqueEmail("nonexistent@example.com", c)
            assertNull(notFound)
        }
    }

    @Test
    fun testInsertRow() {
        WithConnection.run { c ->
            val unsaved = CustomersRowUnsaved(
                email = "insert@example.com",
                passwordHash = "password".toByteArray(),
                firstName = "Insert",
                lastName = "Test"
            )
            val inserted = customersRepo.insert(unsaved, c)

            assertNotNull(inserted.customerId)
            assertEquals("insert@example.com", inserted.email)
            assertEquals("Insert", inserted.firstName)
            assertEquals("Test", inserted.lastName)
            assertEquals("pending", inserted.status.value)
            assertEquals("bronze", inserted.tier)
        }
    }

    @Test
    fun testInsertRowWithAllFields() {
        WithConnection.run { c ->
            val unsaved = CustomersRowUnsaved(
                email = "premium@example.com",
                passwordHash = "securepass".toByteArray(),
                firstName = "Premium",
                lastName = "Customer",
                phone = Provided("+1234567890"),
                status = Provided(CustomerStatusId("suspended")),
                tier = Provided("gold"),
                preferences = Provided("{\"lang\": \"en\"}"),
                notes = Provided("Important customer")
            )

            val inserted = customersRepo.insert(unsaved, c)

            assertEquals("premium@example.com", inserted.email)
            assertEquals("+1234567890", inserted.phone)
            assertEquals("suspended", inserted.status.value)
            assertEquals("gold", inserted.tier)
            assertEquals("{\"lang\": \"en\"}", inserted.preferences)
            assertEquals("Important customer", inserted.notes)
        }
    }

    @Test
    fun testUpdate() {
        WithConnection.run { c ->
            val inserted = customersRepo.insert(
                CustomersRowUnsaved(
                    email = "update@example.com",
                    passwordHash = "hash".toByteArray(),
                    firstName = "Before",
                    lastName = "Update"
                ),
                c
            )

            val updated = inserted.copy(
                firstName = "After",
                lastName = "Changed",
                tier = "silver"
            )

            val success = customersRepo.update(updated, c)
            assertTrue(success)

            val selected = customersRepo.selectById(inserted.customerId, c)
            assertNotNull(selected)
            assertEquals("After", selected!!.firstName)
            assertEquals("Changed", selected.lastName)
            assertEquals("silver", selected.tier)
        }
    }

    @Test
    fun testUpsertInsert() {
        WithConnection.run { c ->
            val initial = customersRepo.insert(
                CustomersRowUnsaved(
                    email = "upsert@example.com",
                    passwordHash = "hash".toByteArray(),
                    firstName = "Upsert",
                    lastName = "Test"
                ),
                c
            )

            customersRepo.deleteById(initial.customerId, c)

            val row = CustomersRow(
                customerId = initial.customerId,
                email = "upsert2@example.com",
                passwordHash = "newhash".toByteArray(),
                firstName = "New",
                lastName = "Upsert",
                phone = null,
                status = CustomerStatusId("pending"),
                tier = "bronze",
                preferences = null,
                marketingFlags = null,
                notes = null,
                createdAt = initial.createdAt,
                updatedAt = initial.updatedAt,
                lastLoginAt = null
            )

            val upserted = customersRepo.upsert(row, c)
            assertEquals("upsert2@example.com", upserted.email)
        }
    }

    @Test
    fun testUpsertUpdate() {
        WithConnection.run { c ->
            val inserted = customersRepo.insert(
                CustomersRowUnsaved(
                    email = "upsert.update@example.com",
                    passwordHash = "hash".toByteArray(),
                    firstName = "Before",
                    lastName = "Upsert"
                ),
                c
            )

            val modified = inserted.copy(
                firstName = "After",
                tier = "gold"
            )

            val upserted = customersRepo.upsert(modified, c)
            assertEquals("After", upserted.firstName)
            assertEquals("gold", upserted.tier)
            assertEquals(inserted.customerId, upserted.customerId)
        }
    }

    @Test
    fun testUpsertBatch() {
        WithConnection.run { c ->
            val c1 = customersRepo.insert(
                CustomersRowUnsaved(
                    email = "batch1@example.com",
                    passwordHash = "hash1".toByteArray(),
                    firstName = "Batch",
                    lastName = "One"
                ),
                c
            )
            val c2 = customersRepo.insert(
                CustomersRowUnsaved(
                    email = "batch2@example.com",
                    passwordHash = "hash2".toByteArray(),
                    firstName = "Batch",
                    lastName = "Two"
                ),
                c
            )

            val m1 = c1.copy(firstName = "Modified1")
            val m2 = c2.copy(firstName = "Modified2")

            val upserted = customersRepo.upsertBatch(mutableListOf(m1, m2).iterator(), c)
            assertEquals(2, upserted.size)

            val s1 = customersRepo.selectById(c1.customerId, c)
            val s2 = customersRepo.selectById(c2.customerId, c)
            assertEquals("Modified1", s1!!.firstName)
            assertEquals("Modified2", s2!!.firstName)
        }
    }

    @Test
    fun testDeleteById() {
        WithConnection.run { c ->
            val inserted = customersRepo.insert(
                CustomersRowUnsaved(
                    email = "delete@example.com",
                    passwordHash = "hash".toByteArray(),
                    firstName = "Delete",
                    lastName = "Me"
                ),
                c
            )

            val deleted = customersRepo.deleteById(inserted.customerId, c)
            assertTrue(deleted)

            val selected = customersRepo.selectById(inserted.customerId, c)
            assertNull(selected)
        }
    }

    @Test
    fun testDeleteByIds() {
        WithConnection.run { c ->
            val c1 = customersRepo.insert(
                CustomersRowUnsaved(
                    email = "del1@example.com",
                    passwordHash = "hash1".toByteArray(),
                    firstName = "Del",
                    lastName = "One"
                ),
                c
            )
            val c2 = customersRepo.insert(
                CustomersRowUnsaved(
                    email = "del2@example.com",
                    passwordHash = "hash2".toByteArray(),
                    firstName = "Del",
                    lastName = "Two"
                ),
                c
            )
            val c3 = customersRepo.insert(
                CustomersRowUnsaved(
                    email = "del3@example.com",
                    passwordHash = "hash3".toByteArray(),
                    firstName = "Del",
                    lastName = "Three"
                ),
                c
            )

            val ids = arrayOf(c1.customerId, c3.customerId)
            val count = customersRepo.deleteByIds(ids, c)
            assertEquals(2, count)

            val remaining = customersRepo.selectAll(c)
            assertEquals(1, remaining.size)
            assertEquals(c2.customerId, remaining[0].customerId)
        }
    }

    @Test
    fun testDSLSelect() {
        WithConnection.run { c ->
            customersRepo.insert(
                CustomersRowUnsaved(
                    email = "dsl1@example.com",
                    passwordHash = "hash1".toByteArray(),
                    firstName = "Alice",
                    lastName = "Smith"
                ),
                c
            )
            customersRepo.insert(
                CustomersRowUnsaved(
                    email = "dsl2@example.com",
                    passwordHash = "hash2".toByteArray(),
                    firstName = "Bob",
                    lastName = "Jones"
                ),
                c
            )
            customersRepo.insert(
                CustomersRowUnsaved(
                    email = "dsl3@example.com",
                    passwordHash = "hash3".toByteArray(),
                    firstName = "Alice",
                    lastName = "Brown"
                ),
                c
            )

            val alices = customersRepo.select()
                .where { f -> f.firstName().isEqual("Alice") }
                .toList(c)
            assertEquals(2, alices.size)

            val specificAlice = customersRepo.select()
                .where { f -> f.firstName().isEqual("Alice") }
                .where { f -> f.lastName().isEqual("Smith") }
                .toList(c)
            assertEquals(1, specificAlice.size)
            assertEquals("Alice", specificAlice[0].firstName)
            assertEquals("Smith", specificAlice[0].lastName)
        }
    }

    @Test
    fun testDSLUpdate() {
        WithConnection.run { c ->
            val customer = customersRepo.insert(
                CustomersRowUnsaved(
                    email = "dsl.update@example.com",
                    passwordHash = "hash".toByteArray(),
                    firstName = "Before",
                    lastName = "Update"
                ),
                c
            )

            customersRepo.update()
                .setValue({ f -> f.firstName() }, "After")
                .setValue({ f -> f.tier() }, "platinum")
                .where { f -> f.customerId().isEqual(customer.customerId) }
                .execute(c)

            val updated = customersRepo.selectById(customer.customerId, c)
            assertNotNull(updated)
            assertEquals("After", updated!!.firstName)
            assertEquals("platinum", updated.tier)
        }
    }

    @Test
    fun testDSLDelete() {
        WithConnection.run { c ->
            customersRepo.insert(
                CustomersRowUnsaved(
                    email = "dsl.del1@example.com",
                    passwordHash = "hash1".toByteArray(),
                    firstName = "ToDelete",
                    lastName = "One"
                ),
                c
            )
            customersRepo.insert(
                CustomersRowUnsaved(
                    email = "dsl.del2@example.com",
                    passwordHash = "hash2".toByteArray(),
                    firstName = "ToDelete",
                    lastName = "Two"
                ),
                c
            )
            customersRepo.insert(
                CustomersRowUnsaved(
                    email = "dsl.keep@example.com",
                    passwordHash = "hash3".toByteArray(),
                    firstName = "ToKeep",
                    lastName = "One"
                ),
                c
            )

            customersRepo.delete()
                .where { f -> f.firstName().isEqual("ToDelete") }
                .execute(c)

            val remaining = customersRepo.selectAll(c)
            assertEquals(1, remaining.size)
            assertEquals("ToKeep", remaining[0].firstName)
        }
    }
}

package testdb

import org.junit.Assert.*
import org.junit.Test
import testdb.simple_customer_lookup.SimpleCustomerLookupSqlRepoImpl
import java.util.Random

class SimpleCustomerLookupSqlRepoTest {
    private val repo = SimpleCustomerLookupSqlRepoImpl()

    @Test
    fun lookupCustomerByEmail() {
        WithConnection.run { c ->
            val testInsert = TestInsert(Random(0))

            val testEmail = "test@example.com"
            val customer = testInsert.Customers(
                passwordHash = byteArrayOf(1, 2, 3),
                email = testEmail,
                c = c
            )

            val results = repo.apply(testEmail, c)

            assertEquals(1, results.size)
            val result = results[0]
            assertEquals(customer.customerId, result.customerId)
            assertEquals(testEmail, result.email)
            assertEquals(customer.firstName, result.firstName)
            assertEquals(customer.lastName, result.lastName)
        }
    }

    @Test
    fun lookupNonExistentCustomerReturnsEmptyList() {
        WithConnection.run { c ->
            val results = repo.apply("nonexistent@example.com", c)
            assertTrue(results.isEmpty())
        }
    }
}

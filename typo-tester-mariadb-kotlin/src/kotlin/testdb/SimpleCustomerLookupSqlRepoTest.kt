package testdb

import org.junit.Assert.*
import org.junit.Test
import testdb.customtypes.Defaulted
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
                email = testEmail,
                passwordHash = byteArrayOf(1, 2, 3),
                firstName = "John",
                lastName = "Doe",
                phone = Defaulted.UseDefault(),
                status = Defaulted.UseDefault(),
                tier = Defaulted.UseDefault(),
                preferences = Defaulted.UseDefault(),
                marketingFlags = Defaulted.UseDefault(),
                notes = Defaulted.UseDefault(),
                createdAt = Defaulted.UseDefault(),
                updatedAt = Defaulted.UseDefault(),
                lastLoginAt = Defaulted.UseDefault(),
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

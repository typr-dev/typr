package oracledb.customers

import oracledb.AddressT
import oracledb.CoordinatesT
import oracledb.MoneyT
import oracledb.OracleTestHelper
import oracledb.customtypes.Defaulted
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class CustomersTest {
    private val repo = CustomersRepoImpl()

    @Test
    fun testInsertCustomerWithAddress() {
        OracleTestHelper.run { c ->
            val coords = CoordinatesT(BigDecimal("40.7128"), BigDecimal("-74.0060"))
            val address = AddressT("123 Main St", "New York", coords)

            val unsaved = CustomersRowUnsaved(
                "John Doe",
                address,
                null,
                Defaulted.UseDefault(),
                Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)

            assertNotNull(insertedId)
            val inserted = repo.selectById(insertedId, c)!!
            assertEquals("John Doe", inserted.name)
            assertEquals("123 Main St", inserted.billingAddress.street)
            assertEquals("New York", inserted.billingAddress.city)
            assertNotNull(inserted.createdAt)
        }
    }

    @Test
    fun testCustomerWithCreditLimit() {
        OracleTestHelper.run { c ->
            val coords = CoordinatesT(BigDecimal("34.0522"), BigDecimal("-118.2437"))
            val address = AddressT("456 Oak Ave", "Los Angeles", coords)
            val creditLimit = MoneyT(BigDecimal("10000.01"), "USD")

            val unsaved = CustomersRowUnsaved(
                "Jane Smith",
                address,
                creditLimit,
                Defaulted.UseDefault(),
                Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)
            val inserted = repo.selectById(insertedId, c)!!

            assertNotNull(inserted.creditLimit)
            assertEquals(BigDecimal("10000.01"), inserted.creditLimit!!.amount)
            assertEquals("USD", inserted.creditLimit!!.currency)
        }
    }

    @Test
    fun testUpdateCustomerAddress() {
        OracleTestHelper.run { c ->
            val coords = CoordinatesT(BigDecimal("41.8781"), BigDecimal("-87.6298"))
            val address = AddressT("789 Pine Rd", "Chicago", coords)

            val unsaved = CustomersRowUnsaved(
                "Bob Wilson",
                address,
                null,
                Defaulted.UseDefault(),
                Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)
            val inserted = repo.selectById(insertedId, c)!!

            val newCoords = CoordinatesT(BigDecimal("47.6062"), BigDecimal("-122.3321"))
            val newAddress = AddressT("999 New St", "Seattle", newCoords)
            val updated = inserted.copy(billingAddress = newAddress)

            val wasUpdated = repo.update(updated, c)
            assertTrue(wasUpdated)

            val fetched = repo.selectById(insertedId, c)!!
            assertEquals("999 New St", fetched.billingAddress.street)
            assertEquals("Seattle", fetched.billingAddress.city)
        }
    }

    @Test
    fun testDeleteCustomer() {
        OracleTestHelper.run { c ->
            val coords = CoordinatesT(BigDecimal("25.7617"), BigDecimal("-80.1918"))
            val address = AddressT("Delete St", "Miami", coords)

            val unsaved = CustomersRowUnsaved(
                "To Delete",
                address,
                null,
                Defaulted.UseDefault(),
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
    fun testSelectAllCustomers() {
        OracleTestHelper.run { c ->
            val coords1 = CoordinatesT(BigDecimal("33.4484"), BigDecimal("-112.0740"))
            val address1 = AddressT("Street A", "Phoenix", coords1)

            val coords2 = CoordinatesT(BigDecimal("29.7604"), BigDecimal("-95.3698"))
            val address2 = AddressT("Street B", "Houston", coords2)

            repo.insert(CustomersRowUnsaved("Customer A", address1, null, Defaulted.UseDefault(), Defaulted.UseDefault()), c)
            repo.insert(CustomersRowUnsaved("Customer B", address2, null, Defaulted.UseDefault(), Defaulted.UseDefault()), c)

            val all = repo.selectAll(c)
            assertTrue(all.size >= 2)
        }
    }

    @Test
    fun testCoordinatesInAddress() {
        OracleTestHelper.run { c ->
            val coords = CoordinatesT(BigDecimal("51.5074"), BigDecimal("-0.1278"))
            val address = AddressT("Piccadilly", "London", coords)

            val unsaved = CustomersRowUnsaved(
                "UK Customer",
                address,
                null,
                Defaulted.UseDefault(),
                Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)
            val inserted = repo.selectById(insertedId, c)!!

            assertEquals(BigDecimal("51.5074"), inserted.billingAddress.location.latitude)
            assertEquals(BigDecimal("-0.1278"), inserted.billingAddress.location.longitude)
        }
    }

    @Test
    fun testUpdateCreditLimit() {
        OracleTestHelper.run { c ->
            val coords = CoordinatesT(BigDecimal("35.6762"), BigDecimal("139.6503"))
            val address = AddressT("Tokyo St", "Tokyo", coords)

            val unsaved = CustomersRowUnsaved(
                "Tokyo Customer",
                address,
                null,
                Defaulted.UseDefault(),
                Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)
            val inserted = repo.selectById(insertedId, c)!!
            assertNull(inserted.creditLimit)

            val creditLimit = MoneyT(BigDecimal("50000.01"), "JPY")
            val updated = inserted.copy(creditLimit = creditLimit)
            repo.update(updated, c)

            val fetched = repo.selectById(insertedId, c)!!
            assertNotNull(fetched.creditLimit)
            assertEquals(BigDecimal("50000.01"), fetched.creditLimit!!.amount)
            assertEquals("JPY", fetched.creditLimit!!.currency)
        }
    }

    @Test
    fun testRemoveCreditLimit() {
        OracleTestHelper.run { c ->
            val coords = CoordinatesT(BigDecimal("48.8566"), BigDecimal("2.3522"))
            val address = AddressT("Paris St", "Paris", coords)
            val creditLimit = MoneyT(BigDecimal("25000.00"), "EUR")

            val unsaved = CustomersRowUnsaved(
                "Paris Customer",
                address,
                creditLimit,
                Defaulted.UseDefault(),
                Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)
            val inserted = repo.selectById(insertedId, c)!!
            assertNotNull(inserted.creditLimit)

            val updated = inserted.copy(creditLimit = null)
            repo.update(updated, c)

            val fetched = repo.selectById(insertedId, c)!!
            assertNull(fetched.creditLimit)
        }
    }
}

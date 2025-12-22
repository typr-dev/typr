package oracledb.products

import oracledb.MoneyT
import oracledb.OracleTestHelper
import oracledb.TagVarrayT
import oracledb.customtypes.Defaulted
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class ProductsTest {
    private val repo = ProductsRepoImpl()

    @Test
    fun testInsertProductWithVarrayTags() {
        OracleTestHelper.run { c ->
            val price = MoneyT(BigDecimal("99.99"), "USD")
            val tags = TagVarrayT(arrayOf("electronics", "gadget", "new"))
            val uniqueSku = "PROD-${System.currentTimeMillis()}"

            val unsaved = ProductsRowUnsaved(
                uniqueSku,
                "Test Product",
                price,
                tags,
                Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)

            assertNotNull(insertedId)
            val inserted = repo.selectById(insertedId, c)!!
            assertEquals(uniqueSku, inserted.sku)
            assertEquals("Test Product", inserted.name)
            assertEquals(price, inserted.price)
            assertNotNull(inserted.tags)
            assertArrayEquals(arrayOf("electronics", "gadget", "new"), inserted.tags!!.value)
        }
    }

    @Test
    fun testInsertProductWithoutTags() {
        OracleTestHelper.run { c ->
            val price = MoneyT(BigDecimal("49.99"), "EUR")

            val unsaved = ProductsRowUnsaved(
                "PROD-002",
                "Product Without Tags",
                price,
                null,
                Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)

            assertNotNull(insertedId)
            val inserted = repo.selectById(insertedId, c)!!
            assertEquals("PROD-002", inserted.sku)
            assertNull(inserted.tags)
        }
    }

    @Test
    fun testVarrayRoundtrip() {
        OracleTestHelper.run { c ->
            val tagArray = arrayOf("tag1", "tag2", "tag3", "tag4", "tag5")
            val tags = TagVarrayT(tagArray)

            val price = MoneyT(BigDecimal("199.99"), "USD")

            val unsaved = ProductsRowUnsaved(
                "PROD-VARRAY",
                "Varray Test Product",
                price,
                tags,
                Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)

            val found = repo.selectById(insertedId, c)
            assertNotNull(found)
            assertNotNull(found!!.tags)
            assertArrayEquals(tagArray, found.tags!!.value)
        }
    }

    @Test
    fun testUpdateTags() {
        OracleTestHelper.run { c ->
            val originalTags = TagVarrayT(arrayOf("old", "tags"))
            val price = MoneyT(BigDecimal("99.99"), "USD")

            val unsaved = ProductsRowUnsaved(
                "PROD-UPDATE",
                "Update Tags Test",
                price,
                originalTags,
                Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)
            val inserted = repo.selectById(insertedId, c)!!

            val newTags = TagVarrayT(arrayOf("new", "updated", "tags"))
            val updatedRow = inserted.copy(tags = newTags)

            val wasUpdated = repo.update(updatedRow, c)
            assertTrue(wasUpdated)
            val fetched = repo.selectById(insertedId, c)!!
            assertNotNull(fetched.tags)
            assertArrayEquals(arrayOf("new", "updated", "tags"), fetched.tags!!.value)
        }
    }

    @Test
    fun testUpdatePrice() {
        OracleTestHelper.run { c ->
            val originalPrice = MoneyT(BigDecimal("100.00"), "USD")
            val unsaved = ProductsRowUnsaved(
                "PROD-PRICE",
                "Price Update Test",
                originalPrice,
                null,
                Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)
            val inserted = repo.selectById(insertedId, c)!!

            val newPrice = MoneyT(BigDecimal("150.01"), "EUR")
            val updatedRow = inserted.copy(price = newPrice)

            val wasUpdated = repo.update(updatedRow, c)
            assertTrue(wasUpdated)
            val fetched = repo.selectById(insertedId, c)!!
            assertEquals(BigDecimal("150.01"), fetched.price.amount)
            assertEquals("EUR", fetched.price.currency)
        }
    }

    @Test
    fun testVarrayWithSingleElement() {
        OracleTestHelper.run { c ->
            val tags = TagVarrayT(arrayOf("single"))
            val price = MoneyT(BigDecimal("10.00"), "USD")

            val unsaved = ProductsRowUnsaved(
                "PROD-SINGLE",
                "Single Tag Product",
                price,
                tags,
                Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)
            val inserted = repo.selectById(insertedId, c)!!
            assertNotNull(inserted.tags)
            assertEquals(1, inserted.tags!!.value.size)
            assertEquals("single", inserted.tags!!.value[0])
        }
    }

    @Test
    fun testVarrayWithMaxSize() {
        OracleTestHelper.run { c ->
            val maxTags = arrayOf("tag1", "tag2", "tag3", "tag4", "tag5", "tag6", "tag7", "tag8", "tag9", "tag10")
            val tags = TagVarrayT(maxTags)
            val price = MoneyT(BigDecimal("299.99"), "USD")

            val unsaved = ProductsRowUnsaved(
                "PROD-MAX",
                "Max Tags Product",
                price,
                tags,
                Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)
            val inserted = repo.selectById(insertedId, c)!!
            assertNotNull(inserted.tags)
            assertEquals(10, inserted.tags!!.value.size)
            assertArrayEquals(maxTags, inserted.tags!!.value)
        }
    }

    @Test
    fun testVarrayEquality() {
        val tags1 = TagVarrayT(arrayOf("a", "b", "c"))
        val tags2 = TagVarrayT(arrayOf("a", "b", "c"))

        assertFalse(tags1 == tags2)
        assertArrayEquals(tags1.value, tags2.value)
    }

    @Test
    fun testDeleteProduct() {
        OracleTestHelper.run { c ->
            val price = MoneyT(BigDecimal("99.99"), "USD")
            val unsaved = ProductsRowUnsaved(
                "PROD-DELETE",
                "To Delete",
                price,
                TagVarrayT(arrayOf("delete")),
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
    fun testSelectAll() {
        OracleTestHelper.run { c ->
            val price1 = MoneyT(BigDecimal("10.00"), "USD")
            val price2 = MoneyT(BigDecimal("20.00"), "EUR")

            val unsaved1 = ProductsRowUnsaved(
                "PROD-ALL-1",
                "Product 1",
                price1,
                TagVarrayT(arrayOf("tag1")),
                Defaulted.UseDefault()
            )

            val unsaved2 = ProductsRowUnsaved(
                "PROD-ALL-2",
                "Product 2",
                price2,
                null,
                Defaulted.UseDefault()
            )

            repo.insert(unsaved1, c)
            repo.insert(unsaved2, c)

            val all = repo.selectAll(c)
            assertTrue(all.size >= 2)
        }
    }

    @Test
    fun testClearTags() {
        OracleTestHelper.run { c ->
            val originalTags = TagVarrayT(arrayOf("tag1", "tag2"))
            val price = MoneyT(BigDecimal("50.00"), "USD")

            val unsaved = ProductsRowUnsaved(
                "PROD-CLEAR",
                "Clear Tags Test",
                price,
                originalTags,
                Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)
            val inserted = repo.selectById(insertedId, c)!!
            assertNotNull(inserted.tags)

            val cleared = inserted.copy(tags = null)
            val wasUpdated = repo.update(cleared, c)

            assertTrue(wasUpdated)
            assertNull(cleared.tags)
        }
    }
}

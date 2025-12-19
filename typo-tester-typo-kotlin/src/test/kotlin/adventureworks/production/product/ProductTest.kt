package adventureworks.production.product

import adventureworks.DbNow
import adventureworks.WithConnection
import adventureworks.public.Name
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class ProductTest {
    private val productRepo = ProductRepoImpl()

    @Test
    fun basicCrud() {
        WithConnection.run { c ->
            val unsaved = ProductRowUnsaved(
                name = Name("Test Product"),
                productnumber = "TEST-001",
                safetystocklevel = 10,
                reorderpoint = 5,
                standardcost = BigDecimal("100.00"),
                listprice = BigDecimal("150.00"),
                daystomanufacture = 5,
                sellstartdate = DbNow.localDateTime()
            )

            val inserted = productRepo.insert(unsaved, c)
            assertNotNull(inserted)
            assertEquals(Name("Test Product"), inserted.name)

            val found = productRepo.selectById(inserted.productid, c)
            assertNotNull(found)
            assertEquals(inserted.productid, found?.productid)

            val updated = inserted.copy(name = Name("Updated Product"))
            assertTrue(productRepo.update(updated, c))

            val afterUpdate = productRepo.selectById(inserted.productid, c)
            assertEquals(Name("Updated Product"), afterUpdate?.name)

            assertTrue(productRepo.deleteById(inserted.productid, c))
            assertNull(productRepo.selectById(inserted.productid, c))
        }
    }

    @Test
    fun testDslQueries() {
        WithConnection.run { c ->
            val unsaved1 = ProductRowUnsaved(
                name = Name("Product A"),
                productnumber = "PROD-A",
                safetystocklevel = 10,
                reorderpoint = 5,
                standardcost = BigDecimal("100.00"),
                listprice = BigDecimal("150.00"),
                daystomanufacture = 5,
                sellstartdate = DbNow.localDateTime()
            )
            val unsaved2 = ProductRowUnsaved(
                name = Name("Product B"),
                productnumber = "PROD-B",
                safetystocklevel = 20,
                reorderpoint = 10,
                standardcost = BigDecimal("200.00"),
                listprice = BigDecimal("250.00"),
                daystomanufacture = 10,
                sellstartdate = DbNow.localDateTime()
            )

            val product1 = productRepo.insert(unsaved1, c)
            val product2 = productRepo.insert(unsaved2, c)

            // Test where clause
            val found = productRepo.select()
                .where { f -> f.name().isEqual(Name("Product A")) }
                .toList(c)

            assertEquals(1, found.size)
            assertEquals(product1.productid, found[0].productid)

            // Test orderBy
            val ordered = productRepo.select()
                .where { f -> f.productid().`in`(arrayOf(product1.productid, product2.productid), ProductId.pgType) }
                .orderBy { f -> typo.dsl.SortOrder.desc(f.listprice().underlying) }
                .toList(c)

            assertEquals(2, ordered.size)
            assertEquals(product2.productid, ordered[0].productid)
            assertEquals(product1.productid, ordered[1].productid)
        }
    }
}

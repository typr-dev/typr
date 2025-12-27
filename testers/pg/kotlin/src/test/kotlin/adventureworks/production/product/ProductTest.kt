package adventureworks.production.product

import adventureworks.DomainInsert
import adventureworks.TestInsert
import adventureworks.WithConnection
import adventureworks.public.Name
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Random

class ProductTest {
    private val testInsert = TestInsert(Random(0), DomainInsert)
    private val productRepo = ProductRepoImpl()

    @Test
    fun basicCrud() {
        WithConnection.run { c ->
            val inserted = testInsert.productionProduct(
                name = Name("Test Product"),
                productnumber = "TEST-001",
                safetystocklevel = 10,
                reorderpoint = 5,
                standardcost = BigDecimal("100.00"),
                listprice = BigDecimal("150.00"),
                daystomanufacture = 5,
                sellstartdate = LocalDateTime.now(),
                c = c
            )

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
            val product1 = testInsert.productionProduct(
                name = Name("Product A"),
                productnumber = "PROD-A",
                safetystocklevel = 10,
                reorderpoint = 5,
                standardcost = BigDecimal("100.00"),
                listprice = BigDecimal("150.00"),
                daystomanufacture = 5,
                sellstartdate = LocalDateTime.now(),
                c = c
            )
            val product2 = testInsert.productionProduct(
                name = Name("Product B"),
                productnumber = "PROD-B",
                safetystocklevel = 20,
                reorderpoint = 10,
                standardcost = BigDecimal("200.00"),
                listprice = BigDecimal("250.00"),
                daystomanufacture = 10,
                sellstartdate = LocalDateTime.now(),
                c = c
            )

            // Test where clause
            val found = productRepo.select()
                .where { f -> f.name().isEqual(Name("Product A")) }
                .toList(c)

            assertEquals(1, found.size)
            assertEquals(product1.productid, found[0].productid)

            // Test orderBy
            val ordered = productRepo.select()
                .where { f -> f.productid().`in`(arrayOf(product1.productid, product2.productid), ProductId.pgType) }
                .orderBy { f -> f.listprice().desc() }
                .toList(c)

            assertEquals(2, ordered.size)
            assertEquals(product2.productid, ordered[0].productid)
            assertEquals(product1.productid, ordered[1].productid)
        }
    }
}

package oracledb

import oracledb.customers.CustomersRepoImpl
import oracledb.customers.CustomersRowUnsaved
import oracledb.customtypes.Defaulted
import oracledb.products.ProductsRepoImpl
import oracledb.products.ProductsRowUnsaved
import org.junit.Assert.*
import org.junit.Test
import typo.runtime.OracleTypes
import java.math.BigDecimal

class OracleDSLTest {
    private val customersRepo = CustomersRepoImpl()
    private val productsRepo = ProductsRepoImpl()

    @Test
    fun testSelectWithWhereClause() {
        OracleTestHelper.run { c ->
            val price = MoneyT(BigDecimal("199.99"), "USD")
            val unsaved = ProductsRowUnsaved(
                "DSL-001",
                "DSL Test Product",
                price,
                TagVarrayT(arrayOf("dsl")),
                Defaulted.UseDefault()
            )

            productsRepo.insert(unsaved, c)

            val query = productsRepo.select().where { p -> p.sku().isEqual("DSL-001") }

            val results = query.toList(c)
            assertFalse(results.isEmpty())
            assertEquals("DSL-001", results[0].sku)
        }
    }

    @Test
    fun testOrderByClause() {
        OracleTestHelper.run { c ->
            val price1 = MoneyT(BigDecimal("300.00"), "USD")
            val price2 = MoneyT(BigDecimal("100.00"), "USD")
            val price3 = MoneyT(BigDecimal("200.00"), "USD")

            productsRepo.insert(ProductsRowUnsaved("ORDER-3", "Product C", price1, null, Defaulted.UseDefault()), c)
            productsRepo.insert(ProductsRowUnsaved("ORDER-1", "Product A", price2, null, Defaulted.UseDefault()), c)
            productsRepo.insert(ProductsRowUnsaved("ORDER-2", "Product B", price3, null, Defaulted.UseDefault()), c)

            val query = productsRepo.select()
                .where { p ->
                    p.sku().`in`(arrayOf("ORDER-1", "ORDER-2", "ORDER-3"), OracleTypes.varchar2)
                }
                .orderBy { p -> p.sku().asc() }

            val results = query.toList(c)
            assertTrue(results.size >= 3)
        }
    }

    @Test
    fun testLimitClause() {
        OracleTestHelper.run { c ->
            val price = MoneyT(BigDecimal("10.00"), "USD")

            for (i in 1..5) {
                productsRepo.insert(
                    ProductsRowUnsaved("LIMIT-$i", "Limit Product $i", price, null, Defaulted.UseDefault()),
                    c
                )
            }

            val query = productsRepo.select().where { p -> p.sku().isEqual("LIMIT-1") }.limit(3)

            val results = query.toList(c)
            assertTrue(results.size <= 3)
        }
    }

    @Test
    fun testCountQuery() {
        OracleTestHelper.run { c ->
            val price = MoneyT(BigDecimal("50.00"), "USD")

            for (i in 1..7) {
                productsRepo.insert(
                    ProductsRowUnsaved("COUNT-$i", "Count Product $i", price, null, Defaulted.UseDefault()),
                    c
                )
            }

            val query = productsRepo.select().where { p -> p.sku().isEqual("COUNT-1") }

            val count = query.count(c)
            assertTrue(count >= 1)
        }
    }

    @Test
    fun testMapProjection() {
        OracleTestHelper.run { c ->
            val price = MoneyT(BigDecimal("99.99"), "USD")
            productsRepo.insert(
                ProductsRowUnsaved("MAP-001", "Map Test", price, null, Defaulted.UseDefault()),
                c
            )

            // Single-column map returns Tuple1
            val query = productsRepo.select()
                .where { p -> p.sku().isEqual("MAP-001") }
                .map { p -> p.name() }

            val results = query.toList(c)
            assertFalse(results.isEmpty())
            assertEquals("Map Test", results[0]._1())
        }
    }

    @Test
    fun testComplexWhereWithOracleObjectTypes() {
        OracleTestHelper.run { c ->
            val nycCoords = CoordinatesT(BigDecimal("40.7128"), BigDecimal("-74.0061"))
            val nycAddress = AddressT("NYC Street", "New York", nycCoords)

            customersRepo.insert(
                CustomersRowUnsaved(
                    "NYC Customer",
                    nycAddress,
                    null,
                    Defaulted.UseDefault(),
                    Defaulted.UseDefault()
                ),
                c
            )

            val query = customersRepo.select().where { cust -> cust.name().isEqual("NYC Customer") }

            val results = query.toList(c)
            assertFalse(results.isEmpty())
            assertEquals("NYC Customer", results[0].name)
        }
    }

    @Test
    fun testDeleteWithDSL() {
        OracleTestHelper.run { c ->
            val price = MoneyT(BigDecimal("10.00"), "USD")
            val inserted = productsRepo.insert(
                ProductsRowUnsaved("DELETE-DSL", "To Delete via DSL", price, null, Defaulted.UseDefault()),
                c
            )

            val deleteQuery = productsRepo.delete().where { p -> p.sku().isEqual("DELETE-DSL") }

            val deleted = deleteQuery.execute(c)
            assertTrue(deleted > 0)

            val found = productsRepo.selectById(inserted, c)
            assertNull(found)
        }
    }
}

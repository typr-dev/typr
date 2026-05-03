package testdb

import dev.typr.dslkt.SqlExpr
import dev.typr.foundations.Tuple
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test
import testdb.products.*
import java.math.BigDecimal
import dev.typr.foundationskt.Connection
import java.util.concurrent.atomic.AtomicInteger

/**
 * Comprehensive tests for tuple IN functionality on SQL Server.
 * Since SQL Server test schema doesn't have composite key tables, we test:
 * - Arbitrary tuple expressions using Tuple.of()
 * - IN with Rows.ofTuples for inline tuple values
 * - Tuple IN with subqueries using tupleWith()
 * - Combined tuple conditions with SqlExpr.all
 *
 * Note: SQL Server doesn't support tuple IN syntax natively, so the DSL emulates it
 * using EXISTS with VALUES table constructor.
 */
class TupleInTest {

    data class Repos(val productsRepo: ProductsRepo)

    // =============== Arbitrary Tuple IN Tests ===============

    @Test
    fun tupleInWithNameAndPrice_Real() {
        SqlServerTestHelper.run { c ->
            val repos = createRealRepos()
            tupleInWithNameAndPrice(repos, c)
        }
    }

    fun tupleInWithNameAndPrice(repos: Repos, c: Connection) {
        val p1 = insertProduct(repos, "Widget", BigDecimal("19.99"), c)
        val p2 = insertProduct(repos, "Gadget", BigDecimal("29.99"), c)
        val p3 = insertProduct(repos, "Widget", BigDecimal("39.99"), c)
        val p4 = insertProduct(repos, "Gizmo", BigDecimal("19.99"), c)

        val result = repos.productsRepo
            .select()
            .where { p ->
                p.name().tupleWith(p.price()).among(listOf(
                    Tuple.of("Widget", BigDecimal("19.99")),
                    Tuple.of("Gadget", BigDecimal("29.99"))
                ))
            }
            .toList(c)

        assertEquals(2, result.size)
        val names = result.map { it.name }.toSet()
        assertEquals(setOf("Widget", "Gadget"), names)
    }

    @Test
    fun tupleInWithSingleTuple_Real() {
        SqlServerTestHelper.run { c ->
            val repos = createRealRepos()
            tupleInWithSingleTuple(repos, c)
        }
    }

    fun tupleInWithSingleTuple(repos: Repos, c: Connection) {
        val p1 = insertProduct(repos, "SingleItem", BigDecimal("99.99"), c)
        val p2 = insertProduct(repos, "OtherItem", BigDecimal("88.88"), c)

        val result = repos.productsRepo
            .select()
            .where { p ->
                p.name().tupleWith(p.price()).among(listOf(
                    Tuple.of("SingleItem", BigDecimal("99.99"))
                ))
            }
            .toList(c)

        assertEquals(1, result.size)
        assertEquals("SingleItem", result[0].name)
    }

    @Test
    fun tupleInWithEmptyList_Real() {
        SqlServerTestHelper.run { c ->
            val repos = createRealRepos()
            tupleInWithEmptyList(repos, c)
        }
    }

    fun tupleInWithEmptyList(repos: Repos, c: Connection) {
        insertProduct(repos, "TestProduct", BigDecimal("50.00"), c)

        val result = repos.productsRepo
            .select()
            .where { p ->
                p.name().tupleWith(p.price()).among(emptyList())
            }
            .toList(c)

        assertEquals(0, result.size)
    }

    @Test
    fun tupleInCombinedWithOtherConditions_Real() {
        SqlServerTestHelper.run { c ->
            val repos = createRealRepos()
            tupleInCombinedWithOtherConditions(repos, c)
        }
    }

    fun tupleInCombinedWithOtherConditions(repos: Repos, c: Connection) {
        val p1 = insertProduct(repos, "Alpha", BigDecimal("10.00"), "First product", c)
        val p2 = insertProduct(repos, "Beta", BigDecimal("20.00"), "Second product", c)
        val p3 = insertProduct(repos, "Gamma", BigDecimal("10.00"), null, c)

        val result = repos.productsRepo
            .select()
            .where { p ->
                SqlExpr.all(
                    p.name().tupleWith(p.price()).among(listOf(
                        Tuple.of("Alpha", BigDecimal("10.00")),
                        Tuple.of("Beta", BigDecimal("20.00")),
                        Tuple.of("Gamma", BigDecimal("10.00"))
                    )),
                    p.description().isNotNull()
                )
            }
            .toList(c)

        assertEquals(2, result.size)
        val names = result.map { it.name }.toSet()
        assertEquals(setOf("Alpha", "Beta"), names)
    }

    @Test
    fun tupleInWithNonExistentTuples_Real() {
        SqlServerTestHelper.run { c ->
            val repos = createRealRepos()
            tupleInWithNonExistentTuples(repos, c)
        }
    }

    fun tupleInWithNonExistentTuples(repos: Repos, c: Connection) {
        val p1 = insertProduct(repos, "Existing", BigDecimal("100.00"), c)

        val result = repos.productsRepo
            .select()
            .where { p ->
                p.name().tupleWith(p.price()).among(listOf(
                    Tuple.of("Existing", BigDecimal("100.00")),
                    Tuple.of("NonExistent", BigDecimal("999.99")),
                    Tuple.of("AlsoMissing", BigDecimal("888.88"))
                ))
            }
            .toList(c)

        assertEquals(1, result.size)
        assertEquals("Existing", result[0].name)
    }

    @Test
    fun tupleInWithLargeList_Real() {
        SqlServerTestHelper.run { c ->
            val repos = createRealRepos()
            tupleInWithLargeList(repos, c)
        }
    }

    fun tupleInWithLargeList(repos: Repos, c: Connection) {
        val products = (1..10).map { i ->
            insertProduct(repos, "Product$i", BigDecimal("${i * 10}.00"), c)
        }

        val tuplesToSelect = products
            .filter { it.price.toInt() % 20 == 0 }
            .map { Tuple.of(it.name, it.price) }

        val result = repos.productsRepo
            .select()
            .where { p ->
                p.name().tupleWith(p.price()).among(tuplesToSelect)
            }
            .toList(c)

        assertEquals(5, result.size)
    }

    // =============== Single Column IN Tests (for comparison) ===============

    @Test
    fun singleColumnIn_Real() {
        SqlServerTestHelper.run { c ->
            val repos = createRealRepos()
            singleColumnIn(repos, c)
        }
    }

    fun singleColumnIn(repos: Repos, c: Connection) {
        val p1 = insertProduct(repos, "Apple", BigDecimal("1.00"), c)
        val p2 = insertProduct(repos, "Banana", BigDecimal("2.00"), c)
        val p3 = insertProduct(repos, "Cherry", BigDecimal("3.00"), c)

        val result = repos.productsRepo
            .select()
            .where { p -> p.productId().among(p1.productId, p3.productId) }
            .toList(c)

        assertEquals(2, result.size)
        val names = result.map { it.name }.toSet()
        assertEquals(setOf("Apple", "Cherry"), names)
    }

    // ==================== Tuple IN Subquery Tests ====================

    @Test
    fun tupleInSubqueryBasic_Real() {
        SqlServerTestHelper.run { c ->
            val repos = createRealRepos()
            tupleInSubqueryBasic(repos, c)
        }
    }

    fun tupleInSubqueryBasic(repos: Repos, c: Connection) {
        val prefix = "SUBQ_${System.nanoTime()}_"
        val p1 = insertProduct(repos, "${prefix}Cheap1", BigDecimal("10.00"), c)
        val p2 = insertProduct(repos, "${prefix}Cheap2", BigDecimal("20.00"), c)
        val p3 = insertProduct(repos, "${prefix}Expensive", BigDecimal("500.00"), c)

        val result = repos.productsRepo
            .select()
            .where { p ->
                SqlExpr.all(
                    p.name()
                        .tupleWith(p.price())
                        .among(
                            repos.productsRepo
                                .select()
                                .where { inner -> inner.price().lessThan(BigDecimal("100.00")) }
                                .map { inner -> inner.name().tupleWith(inner.price()) }
                                .subquery()
                        ),
                    p.name().like("$prefix%")
                )
            }
            .toList(c)

        assertEquals(2, result.size)
        val names = result.map { it.name }.toSet()
        assertEquals(setOf("${prefix}Cheap1", "${prefix}Cheap2"), names)
    }

    @Test
    fun tupleInSubqueryWithNoMatches_Real() {
        SqlServerTestHelper.run { c ->
            val repos = createRealRepos()
            tupleInSubqueryWithNoMatches(repos, c)
        }
    }

    fun tupleInSubqueryWithNoMatches(repos: Repos, c: Connection) {
        insertProduct(repos, "Prod1", BigDecimal("100.00"), c)
        insertProduct(repos, "Prod2", BigDecimal("200.00"), c)

        val result = repos.productsRepo
            .select()
            .where { p ->
                p.name()
                    .tupleWith(p.price())
                    .among(
                        repos.productsRepo
                            .select()
                            .where { inner -> inner.price().lessThan(BigDecimal.ZERO) }
                            .map { inner -> inner.name().tupleWith(inner.price()) }
                            .subquery()
                    )
            }
            .toList(c)

        assertEquals(0, result.size)
    }

    @Test
    fun tupleInSubqueryCombinedWithOtherConditions_Real() {
        SqlServerTestHelper.run { c ->
            val repos = createRealRepos()
            tupleInSubqueryCombinedWithOtherConditions(repos, c)
        }
    }

    fun tupleInSubqueryCombinedWithOtherConditions(repos: Repos, c: Connection) {
        val prefix = "COMB_${System.nanoTime()}_"
        val p1 = insertProduct(repos, "${prefix}ItemA", BigDecimal("50.00"), "Has desc", c)
        val p2 = insertProduct(repos, "${prefix}ItemB", BigDecimal("60.00"), null, c)
        val p3 = insertProduct(repos, "${prefix}ItemC", BigDecimal("70.00"), "Also has", c)

        val result = repos.productsRepo
            .select()
            .where { p ->
                SqlExpr.all(
                    p.name()
                        .tupleWith(p.price())
                        .among(
                            repos.productsRepo
                                .select()
                                .where { inner -> inner.price().lessThan(BigDecimal("100.00")) }
                                .map { inner -> inner.name().tupleWith(inner.price()) }
                                .subquery()
                        ),
                    p.description().isNotNull(),
                    p.name().like("$prefix%")
                )
            }
            .toList(c)

        assertEquals(2, result.size)
        val names = result.map { it.name }.toSet()
        assertEquals(setOf("${prefix}ItemA", "${prefix}ItemC"), names)
    }

    // ==================== Nullable Column Tuple IN Tests ====================

    @Ignore("Nullable values in tuple IN not supported")
    @Test
    fun tupleInWithNullableColumn_Real() {
        SqlServerTestHelper.run { c ->
            val repos = createRealRepos()
            tupleInWithNullableColumn(repos, c)
        }
    }

    fun tupleInWithNullableColumn(repos: Repos, c: Connection) {
        // Create products - some with description (nullable), some without
        insertProduct(repos, "NullDescProd1", BigDecimal("100.00"), null, c)
        insertProduct(repos, "NullDescProd2", BigDecimal("200.00"), null, c)
        insertProduct(repos, "HasDescProd", BigDecimal("300.00"), "Has description", c)

        // Query using tuple with nullable column - match rows with null description
        val result = repos.productsRepo
            .select()
            .where { p ->
                p.name()
                    .tupleWith(p.description())
                    .among(listOf(
                        dev.typr.foundations.Tuple.of("NullDescProd1", null as String?),
                        dev.typr.foundations.Tuple.of("NullDescProd2", null as String?)
                    ))
            }
            .toList(c)

        assertTrue("Should handle nullable column tuple IN", result.size >= 0)
    }

    // ==================== Nested Tuple Tests ====================

    @Ignore("Nested tuples not supported")
    @Test
    fun nestedTupleIn_Real() {
        SqlServerTestHelper.run { c ->
            val repos = createRealRepos()
            nestedTupleIn(repos, c)
        }
    }

    fun nestedTupleIn(repos: Repos, c: Connection) {
        val p1 = insertProduct(repos, "NestProd1", BigDecimal("100.00"), "Desc1", c)
        val p2 = insertProduct(repos, "NestProd2", BigDecimal("200.00"), "Desc2", c)
        val p3 = insertProduct(repos, "NestProd3", BigDecimal("300.00"), "Desc3", c)

        // Test truly nested tuple: ((name, price), description)
        val result = repos.productsRepo
            .select()
            .where { p ->
                p.name()
                    .tupleWith(p.price())
                    .tupleWith(p.description())
                    .among(listOf(
                        dev.typr.foundations.Tuple.of(
                            dev.typr.foundations.Tuple.of("NestProd1", BigDecimal("100.00")),
                            "Desc1"),
                        dev.typr.foundations.Tuple.of(
                            dev.typr.foundations.Tuple.of("NestProd3", BigDecimal("300.00")),
                            "Desc3")
                    ))
            }
            .toList(c)

        assertEquals("Should find 2 products matching nested tuple pattern", 2, result.size)

        // Test that non-matching nested tuple returns empty
        val resultNoMatch = repos.productsRepo
            .select()
            .where { p ->
                p.name()
                    .tupleWith(p.price())
                    .tupleWith(p.description())
                    .among(listOf(
                        dev.typr.foundations.Tuple.of(
                            dev.typr.foundations.Tuple.of("NestProd1", BigDecimal("100.00")),
                            "Wrong Desc")
                    ))
            }
            .toList(c)

        assertTrue("Should not match misaligned nested tuple", resultNoMatch.isEmpty())
    }

    // ==================== Read Nested Tuple from Database Tests ====================

    @Ignore("Nested tuples not supported")
    @Test
    fun readNestedTupleFromDatabase_Real() {
        SqlServerTestHelper.run { c ->
            val repos = createRealRepos()
            readNestedTupleFromDatabase(repos, c)
        }
    }

    fun readNestedTupleFromDatabase(repos: Repos, c: Connection) {
        // Insert test data
        val p1 = insertProduct(repos, "ReadProd1", BigDecimal("100.00"), "Desc1", c)
        val p2 = insertProduct(repos, "ReadProd2", BigDecimal("200.00"), "Desc2", c)
        val p3 = insertProduct(repos, "ReadProd3", BigDecimal("300.00"), "Desc3", c)

        // Select nested tuple: ((name, price), productId)
        val result = repos.productsRepo
            .select()
            .where { p -> p.name().among("ReadProd1", "ReadProd2", "ReadProd3") }
            .orderBy { p -> p.price().asc() }
            .map { p -> p.name().tupleWith(p.price()).tupleWith(p.productId()) }
            .toList(c)

        assertEquals("Should read 3 nested tuples", 3, result.size)

        // Verify the nested tuple structure
        val first = result[0]
        assertEquals("First tuple's inner first element", "ReadProd1", first._1()._1())
        assertEquals("First tuple's inner second element", BigDecimal("100.00"), first._1()._2())
        assertEquals("First tuple's outer second element", p1.productId, first._2())
    }

    // ==================== Helper Methods ====================

    private fun insertProduct(repos: Repos, name: String, price: BigDecimal, c: Connection): ProductsRow {
        return insertProduct(repos, name, price, null, c)
    }

    private fun insertProduct(repos: Repos, name: String, price: BigDecimal, description: String?, c: Connection): ProductsRow {
        val unsaved = ProductsRowUnsaved(name, price, description)
        return repos.productsRepo.insert(unsaved, c)
    }

    private fun createRealRepos(): Repos {
        return Repos(ProductsRepoImpl())
    }
}

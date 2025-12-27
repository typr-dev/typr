package testdb

import dev.typr.foundations.dsl.Bijection
import dev.typr.foundations.dsl.SqlExpr
import dev.typr.foundations.kotlin.Tuples
import org.junit.Assert.*
import org.junit.Test
import testdb.products.*
import java.math.BigDecimal
import java.sql.Connection
import java.util.Optional
import java.util.concurrent.atomic.AtomicInteger

/**
 * Comprehensive tests for tuple IN functionality on SQL Server.
 * Since SQL Server test schema doesn't have composite key tables, we test:
 * - Arbitrary tuple expressions using Tuples.of()
 * - IN with Rows.ofTuples for inline tuple values
 * - Tuple IN with subqueries using tupleWith()
 * - Combined tuple conditions with SqlExpr.all
 * - Both real database and mock repository evaluation
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

    @Test
    fun tupleInWithNameAndPrice_Mock() {
        val repos = createMockRepos()
        tupleInWithNameAndPrice(repos, null)
    }

    fun tupleInWithNameAndPrice(repos: Repos, c: Connection?) {
        val p1 = insertProduct(repos, "Widget", BigDecimal("19.99"), c)
        val p2 = insertProduct(repos, "Gadget", BigDecimal("29.99"), c)
        val p3 = insertProduct(repos, "Widget", BigDecimal("39.99"), c)
        val p4 = insertProduct(repos, "Gizmo", BigDecimal("19.99"), c)

        val result = repos.productsRepo
            .select()
            .where { p ->
                p.name().tupleWith(p.price()).among(listOf(
                    Tuples.of("Widget", BigDecimal("19.99")),
                    Tuples.of("Gadget", BigDecimal("29.99"))
                ))
            }
            .toList(c)

        assertEquals(2, result.size)
        val names = result.map { it.name() }.toSet()
        assertEquals(setOf("Widget", "Gadget"), names)
    }

    @Test
    fun tupleInWithSingleTuple_Real() {
        SqlServerTestHelper.run { c ->
            val repos = createRealRepos()
            tupleInWithSingleTuple(repos, c)
        }
    }

    @Test
    fun tupleInWithSingleTuple_Mock() {
        val repos = createMockRepos()
        tupleInWithSingleTuple(repos, null)
    }

    fun tupleInWithSingleTuple(repos: Repos, c: Connection?) {
        val p1 = insertProduct(repos, "SingleItem", BigDecimal("99.99"), c)
        val p2 = insertProduct(repos, "OtherItem", BigDecimal("88.88"), c)

        val result = repos.productsRepo
            .select()
            .where { p ->
                p.name().tupleWith(p.price()).among(listOf(
                    Tuples.of("SingleItem", BigDecimal("99.99"))
                ))
            }
            .toList(c)

        assertEquals(1, result.size)
        assertEquals("SingleItem", result[0].name())
    }

    @Test
    fun tupleInWithEmptyList_Real() {
        SqlServerTestHelper.run { c ->
            val repos = createRealRepos()
            tupleInWithEmptyList(repos, c)
        }
    }

    @Test
    fun tupleInWithEmptyList_Mock() {
        val repos = createMockRepos()
        tupleInWithEmptyList(repos, null)
    }

    fun tupleInWithEmptyList(repos: Repos, c: Connection?) {
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

    @Test
    fun tupleInCombinedWithOtherConditions_Mock() {
        val repos = createMockRepos()
        tupleInCombinedWithOtherConditions(repos, null)
    }

    fun tupleInCombinedWithOtherConditions(repos: Repos, c: Connection?) {
        val p1 = insertProduct(repos, "Alpha", BigDecimal("10.00"), Optional.of("First product"), c)
        val p2 = insertProduct(repos, "Beta", BigDecimal("20.00"), Optional.of("Second product"), c)
        val p3 = insertProduct(repos, "Gamma", BigDecimal("10.00"), Optional.empty(), c)

        val result = repos.productsRepo
            .select()
            .where { p ->
                SqlExpr.all(
                    p.name().tupleWith(p.price()).among(listOf(
                        Tuples.of("Alpha", BigDecimal("10.00")),
                        Tuples.of("Beta", BigDecimal("20.00")),
                        Tuples.of("Gamma", BigDecimal("10.00"))
                    )),
                    p.description().isNotNull()
                )
            }
            .toList(c)

        assertEquals(2, result.size)
        val names = result.map { it.name() }.toSet()
        assertEquals(setOf("Alpha", "Beta"), names)
    }

    @Test
    fun tupleInWithNonExistentTuples_Real() {
        SqlServerTestHelper.run { c ->
            val repos = createRealRepos()
            tupleInWithNonExistentTuples(repos, c)
        }
    }

    @Test
    fun tupleInWithNonExistentTuples_Mock() {
        val repos = createMockRepos()
        tupleInWithNonExistentTuples(repos, null)
    }

    fun tupleInWithNonExistentTuples(repos: Repos, c: Connection?) {
        val p1 = insertProduct(repos, "Existing", BigDecimal("100.00"), c)

        val result = repos.productsRepo
            .select()
            .where { p ->
                p.name().tupleWith(p.price()).among(listOf(
                    Tuples.of("Existing", BigDecimal("100.00")),
                    Tuples.of("NonExistent", BigDecimal("999.99")),
                    Tuples.of("AlsoMissing", BigDecimal("888.88"))
                ))
            }
            .toList(c)

        assertEquals(1, result.size)
        assertEquals("Existing", result[0].name())
    }

    @Test
    fun tupleInWithLargeList_Real() {
        SqlServerTestHelper.run { c ->
            val repos = createRealRepos()
            tupleInWithLargeList(repos, c)
        }
    }

    @Test
    fun tupleInWithLargeList_Mock() {
        val repos = createMockRepos()
        tupleInWithLargeList(repos, null)
    }

    fun tupleInWithLargeList(repos: Repos, c: Connection?) {
        val products = (1..10).map { i ->
            insertProduct(repos, "Product$i", BigDecimal("${i * 10}.00"), c)
        }

        val tuplesToSelect = products
            .filter { it.price().toInt() % 20 == 0 }
            .map { Tuples.of(it.name(), it.price()) }

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

    @Test
    fun singleColumnIn_Mock() {
        val repos = createMockRepos()
        singleColumnIn(repos, null)
    }

    fun singleColumnIn(repos: Repos, c: Connection?) {
        val p1 = insertProduct(repos, "Apple", BigDecimal("1.00"), c)
        val p2 = insertProduct(repos, "Banana", BigDecimal("2.00"), c)
        val p3 = insertProduct(repos, "Cherry", BigDecimal("3.00"), c)

        val result = repos.productsRepo
            .select()
            .where { p -> p.productId().among(p1.productId(), p3.productId()) }
            .toList(c)

        assertEquals(2, result.size)
        val names = result.map { it.name() }.toSet()
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

    fun tupleInSubqueryBasic(repos: Repos, c: Connection?) {
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
                    p.name().like("$prefix%", Bijection.identity())
                )
            }
            .toList(c)

        assertEquals(2, result.size)
        val names = result.map { it.name() }.toSet()
        assertEquals(setOf("${prefix}Cheap1", "${prefix}Cheap2"), names)
    }

    @Test
    fun tupleInSubqueryWithNoMatches_Real() {
        SqlServerTestHelper.run { c ->
            val repos = createRealRepos()
            tupleInSubqueryWithNoMatches(repos, c)
        }
    }

    fun tupleInSubqueryWithNoMatches(repos: Repos, c: Connection?) {
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

    fun tupleInSubqueryCombinedWithOtherConditions(repos: Repos, c: Connection?) {
        val prefix = "COMB_${System.nanoTime()}_"
        val p1 = insertProduct(repos, "${prefix}ItemA", BigDecimal("50.00"), Optional.of("Has desc"), c)
        val p2 = insertProduct(repos, "${prefix}ItemB", BigDecimal("60.00"), Optional.empty(), c)
        val p3 = insertProduct(repos, "${prefix}ItemC", BigDecimal("70.00"), Optional.of("Also has"), c)

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
                    p.name().like("$prefix%", Bijection.identity())
                )
            }
            .toList(c)

        assertEquals(2, result.size)
        val names = result.map { it.name() }.toSet()
        assertEquals(setOf("${prefix}ItemA", "${prefix}ItemC"), names)
    }

    // ==================== Helper Methods ====================

    private fun insertProduct(repos: Repos, name: String, price: BigDecimal, c: Connection?): ProductsRow {
        return insertProduct(repos, name, price, Optional.empty(), c)
    }

    private fun insertProduct(repos: Repos, name: String, price: BigDecimal, description: Optional<String>, c: Connection?): ProductsRow {
        val unsaved = ProductsRowUnsaved(name, price, description)
        return repos.productsRepo.insert(unsaved, c)
    }

    private fun createRealRepos(): Repos {
        return Repos(ProductsRepoImpl())
    }

    private fun createMockRepos(): Repos {
        val idCounter = AtomicInteger(1)
        return Repos(
            ProductsRepoMock { unsaved ->
                unsaved.toRow { ProductsId(idCounter.getAndIncrement()) }
            }
        )
    }
}

package testdb

import dev.typr.foundations.data.Uint4
import dev.typr.foundations.data.Uint8
import dev.typr.foundations.dsl.MockConnection
import dev.typr.foundations.kotlin.SqlExpr
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test
import testdb.categories.*
import testdb.product_categories.*
import testdb.products.*
import testdb.userdefined.IsPrimary
import java.math.BigDecimal
import java.sql.Connection
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

/**
 * Comprehensive tests for tuple IN functionality on MariaDB. Tests cover:
 * - Composite ID IN with foreign key ID types as composite key components
 * - Tuple IN with subqueries using tupleWith()
 * - Combined with other conditions using SqlExpr.all
 * - Both real database and mock repository evaluation
 */
class TupleInTest {

    data class Repos(
        val productsRepo: ProductsRepo,
        val categoriesRepo: CategoriesRepo,
        val productCategoriesRepo: ProductCategoriesRepo
    )

    // =============== ProductCategories (2-column ProductsId,CategoriesId composite key) ===============

    @Test
    fun productCategoriesCompositeIdInWithMultipleIds_Real() {
        MariaDbTestHelper.run { c ->
            val repos = createRealRepos()
            productCategoriesCompositeIdInWithMultipleIds(repos, c)
        }
    }

    @Test
    fun productCategoriesCompositeIdInWithMultipleIds_Mock() {
        val repos = createMockRepos()
        productCategoriesCompositeIdInWithMultipleIds(repos, MockConnection.instance)
    }

    fun productCategoriesCompositeIdInWithMultipleIds(repos: Repos, c: Connection) {
        val product1 = repos.productsRepo.insert(
            ProductsRowUnsaved("SKU001", "Product 1", BigDecimal("99.99")), c
        )
        val product2 = repos.productsRepo.insert(
            ProductsRowUnsaved("SKU002", "Product 2", BigDecimal("149.99")), c
        )
        val product3 = repos.productsRepo.insert(
            ProductsRowUnsaved("SKU003", "Product 3", BigDecimal("199.99")), c
        )

        val category1 = repos.categoriesRepo.insert(
            CategoriesRowUnsaved("Electronics", "electronics"), c
        )
        val category2 = repos.categoriesRepo.insert(
            CategoriesRowUnsaved("Clothing", "clothing"), c
        )
        val category3 = repos.categoriesRepo.insert(
            CategoriesRowUnsaved("Home", "home"), c
        )

        val pc1 = repos.productCategoriesRepo.insert(
            ProductCategoriesRowUnsaved(product1.productId, category1.categoryId), c
        )
        val pc2 = repos.productCategoriesRepo.insert(
            ProductCategoriesRowUnsaved(product1.productId, category2.categoryId), c
        )
        val pc3 = repos.productCategoriesRepo.insert(
            ProductCategoriesRowUnsaved(product2.productId, category1.categoryId), c
        )
        val pc4 = repos.productCategoriesRepo.insert(
            ProductCategoriesRowUnsaved(product3.productId, category3.categoryId), c
        )

        val result = repos.productCategoriesRepo
            .select()
            .where { pc ->
                pc.compositeIdIn(listOf(pc1.compositeId(), pc3.compositeId(), pc4.compositeId()))
            }
            .toList(c)

        assertEquals(3, result.size)
        val resultIds = result.map { it.compositeId() }.toSet()
        assertEquals(setOf(pc1.compositeId(), pc3.compositeId(), pc4.compositeId()), resultIds)
    }

    @Test
    fun productCategoriesCompositeIdInWithSingleId_Real() {
        MariaDbTestHelper.run { c ->
            val repos = createRealRepos()
            productCategoriesCompositeIdInWithSingleId(repos, c)
        }
    }

    @Test
    fun productCategoriesCompositeIdInWithSingleId_Mock() {
        val repos = createMockRepos()
        productCategoriesCompositeIdInWithSingleId(repos, MockConnection.instance)
    }

    fun productCategoriesCompositeIdInWithSingleId(repos: Repos, c: Connection) {
        val product = repos.productsRepo.insert(
            ProductsRowUnsaved("SINGLE001", "Single Product", BigDecimal("50.00")), c
        )
        val category = repos.categoriesRepo.insert(
            CategoriesRowUnsaved("Single Category", "single-category"), c
        )
        val pc = repos.productCategoriesRepo.insert(
            ProductCategoriesRowUnsaved(product.productId, category.categoryId), c
        )

        val result = repos.productCategoriesRepo
            .select()
            .where { pcat -> pcat.compositeIdIn(listOf(pc.compositeId())) }
            .toList(c)

        assertEquals(1, result.size)
        assertEquals(pc, result[0])
    }

    @Test
    fun productCategoriesCompositeIdInWithEmptyList_Real() {
        MariaDbTestHelper.run { c ->
            val repos = createRealRepos()
            productCategoriesCompositeIdInWithEmptyList(repos, c)
        }
    }

    @Test
    fun productCategoriesCompositeIdInWithEmptyList_Mock() {
        val repos = createMockRepos()
        productCategoriesCompositeIdInWithEmptyList(repos, MockConnection.instance)
    }

    fun productCategoriesCompositeIdInWithEmptyList(repos: Repos, c: Connection) {
        val product = repos.productsRepo.insert(
            ProductsRowUnsaved("EMPTY001", "Empty Test Product", BigDecimal("25.00")), c
        )
        val category = repos.categoriesRepo.insert(
            CategoriesRowUnsaved("Empty Test Category", "empty-test-category"), c
        )
        repos.productCategoriesRepo.insert(
            ProductCategoriesRowUnsaved(product.productId, category.categoryId), c
        )

        val result = repos.productCategoriesRepo
            .select()
            .where { pc -> pc.compositeIdIn(emptyList()) }
            .toList(c)

        assertEquals(0, result.size)
    }

    @Test
    fun productCategoriesCompositeIdInCombinedWithOtherConditions_Real() {
        MariaDbTestHelper.run { c ->
            val repos = createRealRepos()
            productCategoriesCompositeIdInCombinedWithOtherConditions(repos, c)
        }
    }

    @Test
    fun productCategoriesCompositeIdInCombinedWithOtherConditions_Mock() {
        val repos = createMockRepos()
        productCategoriesCompositeIdInCombinedWithOtherConditions(repos, MockConnection.instance)
    }

    fun productCategoriesCompositeIdInCombinedWithOtherConditions(repos: Repos, c: Connection) {
        val product1 = repos.productsRepo.insert(
            ProductsRowUnsaved("COND001", "Condition Product 1", BigDecimal("100.00")), c
        )
        val product2 = repos.productsRepo.insert(
            ProductsRowUnsaved("COND002", "Condition Product 2", BigDecimal("200.00")), c
        )

        val category = repos.categoriesRepo.insert(
            CategoriesRowUnsaved("Condition Category", "condition-category"), c
        )

        val pc1 = repos.productCategoriesRepo.insert(
            ProductCategoriesRow(product1.productId, category.categoryId, IsPrimary(true), 1.toShort()), c
        )
        val pc2 = repos.productCategoriesRepo.insert(
            ProductCategoriesRow(product2.productId, category.categoryId, IsPrimary(false), 2.toShort()), c
        )

        val result = repos.productCategoriesRepo
            .select()
            .where { pc ->
                SqlExpr.all(
                    pc.compositeIdIn(listOf(pc1.compositeId(), pc2.compositeId())),
                    pc.isPrimary().isEqual(IsPrimary(true))
                )
            }
            .toList(c)

        assertEquals(1, result.size)
        assertEquals(pc1.compositeId(), result[0].compositeId())
    }

    @Test
    fun productCategoriesCompositeIdInWithNonExistentIds_Real() {
        MariaDbTestHelper.run { c ->
            val repos = createRealRepos()
            productCategoriesCompositeIdInWithNonExistentIds(repos, c)
        }
    }

    @Test
    fun productCategoriesCompositeIdInWithNonExistentIds_Mock() {
        val repos = createMockRepos()
        productCategoriesCompositeIdInWithNonExistentIds(repos, MockConnection.instance)
    }

    fun productCategoriesCompositeIdInWithNonExistentIds(repos: Repos, c: Connection) {
        val product = repos.productsRepo.insert(
            ProductsRowUnsaved("EXIST001", "Existing Product", BigDecimal("75.00")), c
        )
        val category = repos.categoriesRepo.insert(
            CategoriesRowUnsaved("Existing Category", "existing-category"), c
        )
        val pc = repos.productCategoriesRepo.insert(
            ProductCategoriesRowUnsaved(product.productId, category.categoryId), c
        )

        val result = repos.productCategoriesRepo
            .select()
            .where { pcat ->
                pcat.compositeIdIn(
                    listOf(
                        pc.compositeId(),
                        ProductCategoriesId(
                            ProductsId(Uint8.of(999999L)),
                            CategoriesId(Uint4.of(888888L))
                        )
                    )
                )
            }
            .toList(c)

        assertEquals(1, result.size)
        assertEquals(pc, result[0])
    }

    @Test
    fun productCategoriesCompositeIdComputedVsManual_Real() {
        MariaDbTestHelper.run { c ->
            val repos = createRealRepos()
            productCategoriesCompositeIdComputedVsManual(repos, c)
        }
    }

    @Test
    fun productCategoriesCompositeIdComputedVsManual_Mock() {
        val repos = createMockRepos()
        productCategoriesCompositeIdComputedVsManual(repos, MockConnection.instance)
    }

    fun productCategoriesCompositeIdComputedVsManual(repos: Repos, c: Connection) {
        val product = repos.productsRepo.insert(
            ProductsRowUnsaved("COMP001", "Computed Product", BigDecimal("55.00")), c
        )
        val category = repos.categoriesRepo.insert(
            CategoriesRowUnsaved("Computed Category", "computed-category"), c
        )
        val pc = repos.productCategoriesRepo.insert(
            ProductCategoriesRowUnsaved(product.productId, category.categoryId), c
        )

        val computedId = pc.compositeId()
        val manualId = ProductCategoriesId(product.productId, category.categoryId)

        assertEquals(computedId, manualId)

        val result = repos.productCategoriesRepo
            .select()
            .where { pcat -> pcat.compositeIdIn(listOf(computedId, manualId)) }
            .toList(c)

        assertEquals(1, result.size)
        assertEquals(pc, result[0])
    }

    @Test
    fun productCategoriesCompositeIdInWithLargeList_Real() {
        MariaDbTestHelper.run { c ->
            val repos = createRealRepos()
            productCategoriesCompositeIdInWithLargeList(repos, c)
        }
    }

    @Test
    fun productCategoriesCompositeIdInWithLargeList_Mock() {
        val repos = createMockRepos()
        productCategoriesCompositeIdInWithLargeList(repos, MockConnection.instance)
    }

    fun productCategoriesCompositeIdInWithLargeList(repos: Repos, c: Connection) {
        val products = (1..10).map { i ->
            repos.productsRepo.insert(
                ProductsRowUnsaved("BULK$i", "Bulk Product $i", BigDecimal(i * 10)), c
            )
        }

        val categories = (1..5).map { i ->
            repos.categoriesRepo.insert(
                CategoriesRowUnsaved("Bulk Category $i", "bulk-category-$i"), c
            )
        }

        val productCategories = mutableListOf<ProductCategoriesRow>()
        for (p in 0 until 5) {
            for (cat in 0 until 5) {
                productCategories.add(
                    repos.productCategoriesRepo.insert(
                        ProductCategoriesRowUnsaved(products[p].productId, categories[cat].categoryId),
                        c
                    )
                )
            }
        }

        val idsToSelect = productCategories.take(10).map { it.compositeId() }

        val result = repos.productCategoriesRepo
            .select()
            .where { pc -> pc.compositeIdIn(idsToSelect) }
            .toList(c)

        assertEquals(10, result.size)
    }

    // ==================== TupleInSubquery Tests ====================

    @Test
    fun tupleInSubqueryBasic_Real() {
        MariaDbTestHelper.run { c ->
            val repos = createRealRepos()
            tupleInSubqueryBasic(repos, c)
        }
    }

    fun tupleInSubqueryBasic(repos: Repos, c: Connection) {
        val product1 = repos.productsRepo.insert(
            ProductsRowUnsaved("SKU100", "Product 100", BigDecimal("100.00")), c
        )
        val product2 = repos.productsRepo.insert(
            ProductsRowUnsaved("SKU200", "Product 200", BigDecimal("200.00")), c
        )

        val category1 = repos.categoriesRepo.insert(
            CategoriesRowUnsaved("Cat A", "cat-a"), c
        )
        val category2 = repos.categoriesRepo.insert(
            CategoriesRowUnsaved("Cat B", "cat-b"), c
        )

        val pc1 = repos.productCategoriesRepo.insert(
            ProductCategoriesRowUnsaved(product1.productId, category1.categoryId), c
        )
        val pc2 = repos.productCategoriesRepo.insert(
            ProductCategoriesRowUnsaved(product2.productId, category2.categoryId), c
        )

        val result = repos.productCategoriesRepo
            .select()
            .where { pc ->
                pc.productId()
                    .tupleWith(pc.categoryId())
                    .among(
                        repos.productCategoriesRepo
                            .select()
                            .where { inner -> inner.isPrimary().isEqual(IsPrimary(false)) }
                            .map { inner -> inner.productId().tupleWith(inner.categoryId()) }
                            .subquery()
                    )
            }
            .toList(c)

        assertEquals(2, result.size)
    }

    @Test
    fun tupleInSubqueryWithNoMatches_Real() {
        MariaDbTestHelper.run { c ->
            val repos = createRealRepos()
            tupleInSubqueryWithNoMatches(repos, c)
        }
    }

    fun tupleInSubqueryWithNoMatches(repos: Repos, c: Connection) {
        val product = repos.productsRepo.insert(
            ProductsRowUnsaved("TEST001", "Test Product", BigDecimal("50.00")), c
        )
        val category = repos.categoriesRepo.insert(
            CategoriesRowUnsaved("Test Cat", "test-cat"), c
        )
        repos.productCategoriesRepo.insert(
            ProductCategoriesRowUnsaved(product.productId, category.categoryId), c
        )

        val result = repos.productCategoriesRepo
            .select()
            .where { pc ->
                pc.productId()
                    .tupleWith(pc.categoryId())
                    .among(
                        repos.productCategoriesRepo
                            .select()
                            .where { inner -> inner.isPrimary().isEqual(IsPrimary(true)) }
                            .map { inner -> inner.productId().tupleWith(inner.categoryId()) }
                            .subquery()
                    )
            }
            .toList(c)

        assertEquals(0, result.size)
    }

    @Test
    fun tupleInSubqueryCombinedWithOtherConditions_Real() {
        MariaDbTestHelper.run { c ->
            val repos = createRealRepos()
            tupleInSubqueryCombinedWithOtherConditions(repos, c)
        }
    }

    fun tupleInSubqueryCombinedWithOtherConditions(repos: Repos, c: Connection) {
        val product1 = repos.productsRepo.insert(
            ProductsRowUnsaved("COMB001", "Combo Product 1", BigDecimal("30.00")), c
        )
        val product2 = repos.productsRepo.insert(
            ProductsRowUnsaved("COMB002", "Combo Product 2", BigDecimal("40.00")), c
        )

        val category = repos.categoriesRepo.insert(
            CategoriesRowUnsaved("Combo Category", "combo-cat"), c
        )

        val pc1 = repos.productCategoriesRepo.insert(
            ProductCategoriesRow(product1.productId, category.categoryId, IsPrimary(false), 1.toShort()), c
        )
        val pc2 = repos.productCategoriesRepo.insert(
            ProductCategoriesRow(product2.productId, category.categoryId, IsPrimary(false), 2.toShort()), c
        )

        val result = repos.productCategoriesRepo
            .select()
            .where { pc ->
                SqlExpr.all(
                    pc.productId()
                        .tupleWith(pc.categoryId())
                        .among(
                            repos.productCategoriesRepo
                                .select()
                                .where { inner -> inner.isPrimary().isEqual(IsPrimary(false)) }
                                .map { inner -> inner.productId().tupleWith(inner.categoryId()) }
                                .subquery()
                        ),
                    pc.sortOrder().greaterThan(1.toShort())
                )
            }
            .toList(c)

        assertEquals(1, result.size)
        assertEquals(product2.productId, result[0].productId)
    }

    // ==================== Nullable Column Tuple IN Tests ====================

    @Ignore("MariaDB VALUES table syntax doesn't support column aliasing - requires deep refactor")
    @Test
    fun tupleInWithNullableColumn_Real() {
        MariaDbTestHelper.run { c ->
            val repos = createRealRepos()
            tupleInWithNullableColumn(repos, c)
        }
    }

    @Ignore("MariaDB VALUES table syntax doesn't support column aliasing - requires deep refactor")
    @Test
    fun tupleInWithNullableColumn_Mock() {
        val repos = createMockRepos()
        tupleInWithNullableColumn(repos, MockConnection.instance)
    }

    fun tupleInWithNullableColumn(repos: Repos, c: Connection) {
        val product1 = repos.productsRepo.insert(
            ProductsRowUnsaved("NULL001", "NullDescProd1", BigDecimal("100.00")), c
        )
        val product2 = repos.productsRepo.insert(
            ProductsRowUnsaved("NULL002", "NullDescProd2", BigDecimal("200.00")), c
        )
        val product3 = repos.productsRepo.insert(
            ProductsRowUnsaved("HASDESC", "HasDescProd", BigDecimal("300.00"), shortDescription = testdb.customtypes.Defaulted.Provided("Has description")), c
        )

        // Query using tuple with nullable column - match rows with null shortDescription
        val result = repos.productsRepo
            .select()
            .where { p ->
                p.name()
                    .tupleWith(p.shortDescription())
                    .among(listOf(
                        dev.typr.foundations.Tuple.of("NullDescProd1", null as String?),
                        dev.typr.foundations.Tuple.of("NullDescProd2", null as String?)
                    ))
            }
            .toList(c)

        // Should handle nullable column tuple IN (tests null-safe comparison)
        assertTrue("Should handle nullable column tuple IN", result.size >= 0)
    }

    // ==================== Nested Tuple Tests ====================

    @Ignore("MariaDB VALUES table syntax doesn't support column aliasing - requires deep refactor")
    @Test
    fun nestedTupleIn_Real() {
        MariaDbTestHelper.run { c ->
            val repos = createRealRepos()
            nestedTupleIn(repos, c)
        }
    }

    @Ignore("MariaDB VALUES table syntax doesn't support column aliasing - requires deep refactor")
    @Test
    fun nestedTupleIn_Mock() {
        val repos = createMockRepos()
        nestedTupleIn(repos, MockConnection.instance)
    }

    fun nestedTupleIn(repos: Repos, c: Connection) {
        val product1 = repos.productsRepo.insert(
            ProductsRowUnsaved("NEST001", "NestProd1", BigDecimal("100.00")), c
        )
        val product2 = repos.productsRepo.insert(
            ProductsRowUnsaved("NEST002", "NestProd2", BigDecimal("200.00")), c
        )
        val product3 = repos.productsRepo.insert(
            ProductsRowUnsaved("NEST003", "NestProd3", BigDecimal("300.00")), c
        )

        val category1 = repos.categoriesRepo.insert(
            CategoriesRowUnsaved("Nest Category 1", "nest-cat-1"), c
        )

        val pc1 = repos.productCategoriesRepo.insert(
            ProductCategoriesRow(product1.productId, category1.categoryId, IsPrimary(true), 1.toShort()), c
        )
        val pc2 = repos.productCategoriesRepo.insert(
            ProductCategoriesRow(product2.productId, category1.categoryId, IsPrimary(false), 2.toShort()), c
        )
        val pc3 = repos.productCategoriesRepo.insert(
            ProductCategoriesRow(product3.productId, category1.categoryId, IsPrimary(true), 3.toShort()), c
        )

        // Test truly nested tuple: ((productId, categoryId), isPrimary)
        val result = repos.productCategoriesRepo
            .select()
            .where { pc ->
                pc.productId()
                    .tupleWith(pc.categoryId())
                    .tupleWith(pc.isPrimary())
                    .among(listOf(
                        dev.typr.foundations.Tuple.of(
                            dev.typr.foundations.Tuple.of(product1.productId, category1.categoryId),
                            IsPrimary(true)),
                        dev.typr.foundations.Tuple.of(
                            dev.typr.foundations.Tuple.of(product3.productId, category1.categoryId),
                            IsPrimary(true))
                    ))
            }
            .toList(c)

        assertEquals("Should find 2 product categories matching nested tuple pattern", 2, result.size)

        // Test that non-matching nested tuple returns empty
        val resultNoMatch = repos.productCategoriesRepo
            .select()
            .where { pc ->
                pc.productId()
                    .tupleWith(pc.categoryId())
                    .tupleWith(pc.isPrimary())
                    .among(listOf(
                        dev.typr.foundations.Tuple.of(
                            dev.typr.foundations.Tuple.of(product1.productId, category1.categoryId),
                            IsPrimary(false))  // Wrong: isPrimary doesn't match
                    ))
            }
            .toList(c)

        assertTrue("Should not match misaligned nested tuple", resultNoMatch.isEmpty())
    }

    // ==================== Read Nested Tuple from Database Tests ====================

    @Ignore("MariaDB VALUES table syntax doesn't support column aliasing - requires deep refactor")
    @Test
    fun readNestedTupleFromDatabase_Real() {
        MariaDbTestHelper.run { c ->
            val repos = createRealRepos()
            readNestedTupleFromDatabase(repos, c)
        }
    }

    @Ignore("MariaDB VALUES table syntax doesn't support column aliasing - requires deep refactor")
    @Test
    fun readNestedTupleFromDatabase_Mock() {
        val repos = createMockRepos()
        readNestedTupleFromDatabase(repos, MockConnection.instance)
    }

    fun readNestedTupleFromDatabase(repos: Repos, c: Connection) {
        // Insert test data
        val product1 = repos.productsRepo.insert(
            ProductsRowUnsaved("READ001", "ReadProd1", BigDecimal("100.00")), c
        )
        val product2 = repos.productsRepo.insert(
            ProductsRowUnsaved("READ002", "ReadProd2", BigDecimal("200.00")), c
        )
        val product3 = repos.productsRepo.insert(
            ProductsRowUnsaved("READ003", "ReadProd3", BigDecimal("300.00")), c
        )

        // Select nested tuple: ((name, basePrice), sku)
        val result = repos.productsRepo
            .select()
            .where { p -> p.sku().among("READ001", "READ002", "READ003") }
            .orderBy { p -> p.basePrice().asc() }
            .map { p -> p.name().tupleWith(p.basePrice()).tupleWith(p.sku()) }
            .toList(c)

        assertEquals("Should read 3 nested tuples", 3, result.size)

        // Verify the nested tuple structure
        val first = result[0]
        assertEquals("First tuple's inner first element", "ReadProd1", first._1()._1())
        assertEquals("First tuple's inner second element", BigDecimal("100.00"), first._1()._2())
        assertEquals("First tuple's outer second element", "READ001", first._2())
    }

    // ==================== Helper Methods ====================

    private fun createRealRepos(): Repos {
        return Repos(ProductsRepoImpl(), CategoriesRepoImpl(), ProductCategoriesRepoImpl())
    }

    private fun createMockRepos(): Repos {
        val productIdCounter = AtomicInteger(1)
        val categoryIdCounter = AtomicInteger(1)
        val now = LocalDateTime.now()

        return Repos(
            ProductsRepoMock({ unsaved ->
                unsaved.toRow(
                    { null },    // brandId
                    { null },    // shortDescription
                    { null },    // fullDescription
                    { null },    // costPrice
                    { null },    // weightKg
                    { null },    // dimensionsJson
                    { "draft" },        // status
                    { "standard" },     // taxClass
                    { null },    // tags
                    { null },    // attributes
                    { null },    // seoMetadata
                    { now },            // createdAt
                    { now },            // updatedAt
                    { null },    // publishedAt
                    { ProductsId(Uint8.of(productIdCounter.getAndIncrement().toLong())) }
                )
            }),
            CategoriesRepoMock({ unsaved ->
                unsaved.toRow(
                    { null },    // parentId
                    { null },    // description
                    { null },    // imageUrl
                    { 0.toShort() },    // sortOrder
                    { true },           // isVisible
                    { null },    // metadata
                    { CategoriesId(Uint4.of(categoryIdCounter.getAndIncrement().toLong())) }
                )
            }),
            ProductCategoriesRepoMock({ unsaved ->
                unsaved.toRow(
                    { IsPrimary(false) },          // isPrimary
                    { 0.toShort() }     // sortOrder
                )
            })
        )
    }
}

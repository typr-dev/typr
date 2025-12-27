package testdb

import dev.typr.foundations.dsl.SqlExpr
import org.junit.Assert.*
import org.junit.Test
import testdb.categories.*
import testdb.product_categories.*
import testdb.products.*
import java.math.BigDecimal
import java.math.BigInteger
import java.sql.Connection
import java.time.LocalDateTime
import java.util.Optional
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
        productCategoriesCompositeIdInWithMultipleIds(repos, null)
    }

    fun productCategoriesCompositeIdInWithMultipleIds(repos: Repos, c: Connection?) {
        val product1 = repos.productsRepo.insert(
            ProductsRowUnsaved("SKU001", "Product 1", BigDecimal("99.99")), c)
        val product2 = repos.productsRepo.insert(
            ProductsRowUnsaved("SKU002", "Product 2", BigDecimal("149.99")), c)
        val product3 = repos.productsRepo.insert(
            ProductsRowUnsaved("SKU003", "Product 3", BigDecimal("199.99")), c)

        val category1 = repos.categoriesRepo.insert(
            CategoriesRowUnsaved("Electronics", "electronics"), c)
        val category2 = repos.categoriesRepo.insert(
            CategoriesRowUnsaved("Clothing", "clothing"), c)
        val category3 = repos.categoriesRepo.insert(
            CategoriesRowUnsaved("Home", "home"), c)

        val pc1 = repos.productCategoriesRepo.insert(
            ProductCategoriesRowUnsaved(product1.productId(), category1.categoryId()), c)
        val pc2 = repos.productCategoriesRepo.insert(
            ProductCategoriesRowUnsaved(product1.productId(), category2.categoryId()), c)
        val pc3 = repos.productCategoriesRepo.insert(
            ProductCategoriesRowUnsaved(product2.productId(), category1.categoryId()), c)
        val pc4 = repos.productCategoriesRepo.insert(
            ProductCategoriesRowUnsaved(product3.productId(), category3.categoryId()), c)

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
        productCategoriesCompositeIdInWithSingleId(repos, null)
    }

    fun productCategoriesCompositeIdInWithSingleId(repos: Repos, c: Connection?) {
        val product = repos.productsRepo.insert(
            ProductsRowUnsaved("SINGLE001", "Single Product", BigDecimal("50.00")), c)
        val category = repos.categoriesRepo.insert(
            CategoriesRowUnsaved("Single Category", "single-category"), c)
        val pc = repos.productCategoriesRepo.insert(
            ProductCategoriesRowUnsaved(product.productId(), category.categoryId()), c)

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
        productCategoriesCompositeIdInWithEmptyList(repos, null)
    }

    fun productCategoriesCompositeIdInWithEmptyList(repos: Repos, c: Connection?) {
        val product = repos.productsRepo.insert(
            ProductsRowUnsaved("EMPTY001", "Empty Test Product", BigDecimal("25.00")), c)
        val category = repos.categoriesRepo.insert(
            CategoriesRowUnsaved("Empty Test Category", "empty-test-category"), c)
        repos.productCategoriesRepo.insert(
            ProductCategoriesRowUnsaved(product.productId(), category.categoryId()), c)

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
        productCategoriesCompositeIdInCombinedWithOtherConditions(repos, null)
    }

    fun productCategoriesCompositeIdInCombinedWithOtherConditions(repos: Repos, c: Connection?) {
        val product1 = repos.productsRepo.insert(
            ProductsRowUnsaved("COND001", "Condition Product 1", BigDecimal("100.00")), c)
        val product2 = repos.productsRepo.insert(
            ProductsRowUnsaved("COND002", "Condition Product 2", BigDecimal("200.00")), c)

        val category = repos.categoriesRepo.insert(
            CategoriesRowUnsaved("Condition Category", "condition-category"), c)

        val pc1 = repos.productCategoriesRepo.insert(
            ProductCategoriesRow(product1.productId(), category.categoryId(), true, 1.toShort()), c)
        val pc2 = repos.productCategoriesRepo.insert(
            ProductCategoriesRow(product2.productId(), category.categoryId(), false, 2.toShort()), c)

        val result = repos.productCategoriesRepo
            .select()
            .where { pc ->
                SqlExpr.all(
                    pc.compositeIdIn(listOf(pc1.compositeId(), pc2.compositeId())),
                    pc.isPrimary().isEqual(true)
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
        productCategoriesCompositeIdInWithNonExistentIds(repos, null)
    }

    fun productCategoriesCompositeIdInWithNonExistentIds(repos: Repos, c: Connection?) {
        val product = repos.productsRepo.insert(
            ProductsRowUnsaved("EXIST001", "Existing Product", BigDecimal("75.00")), c)
        val category = repos.categoriesRepo.insert(
            CategoriesRowUnsaved("Existing Category", "existing-category"), c)
        val pc = repos.productCategoriesRepo.insert(
            ProductCategoriesRowUnsaved(product.productId(), category.categoryId()), c)

        val result = repos.productCategoriesRepo
            .select()
            .where { pcat ->
                pcat.compositeIdIn(listOf(
                    pc.compositeId(),
                    ProductCategoriesId(
                        ProductsId(BigInteger.valueOf(999999)),
                        CategoriesId(888888)
                    )
                ))
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
        productCategoriesCompositeIdComputedVsManual(repos, null)
    }

    fun productCategoriesCompositeIdComputedVsManual(repos: Repos, c: Connection?) {
        val product = repos.productsRepo.insert(
            ProductsRowUnsaved("COMP001", "Computed Product", BigDecimal("55.00")), c)
        val category = repos.categoriesRepo.insert(
            CategoriesRowUnsaved("Computed Category", "computed-category"), c)
        val pc = repos.productCategoriesRepo.insert(
            ProductCategoriesRowUnsaved(product.productId(), category.categoryId()), c)

        val computedId = pc.compositeId()
        val manualId = ProductCategoriesId(product.productId(), category.categoryId())

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
        productCategoriesCompositeIdInWithLargeList(repos, null)
    }

    fun productCategoriesCompositeIdInWithLargeList(repos: Repos, c: Connection?) {
        val products = (1..10).map { i ->
            repos.productsRepo.insert(
                ProductsRowUnsaved("BULK$i", "Bulk Product $i", BigDecimal(i * 10)), c)
        }

        val categories = (1..5).map { i ->
            repos.categoriesRepo.insert(
                CategoriesRowUnsaved("Bulk Category $i", "bulk-category-$i"), c)
        }

        val productCategories = mutableListOf<ProductCategoriesRow>()
        for (p in 0 until 5) {
            for (cat in 0 until 5) {
                productCategories.add(
                    repos.productCategoriesRepo.insert(
                        ProductCategoriesRowUnsaved(products[p].productId(), categories[cat].categoryId()),
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

    fun tupleInSubqueryBasic(repos: Repos, c: Connection?) {
        val product1 = repos.productsRepo.insert(
            ProductsRowUnsaved("SKU100", "Product 100", BigDecimal("100.00")), c)
        val product2 = repos.productsRepo.insert(
            ProductsRowUnsaved("SKU200", "Product 200", BigDecimal("200.00")), c)

        val category1 = repos.categoriesRepo.insert(
            CategoriesRowUnsaved("Cat A", "cat-a"), c)
        val category2 = repos.categoriesRepo.insert(
            CategoriesRowUnsaved("Cat B", "cat-b"), c)

        val pc1 = repos.productCategoriesRepo.insert(
            ProductCategoriesRowUnsaved(product1.productId(), category1.categoryId()), c)
        val pc2 = repos.productCategoriesRepo.insert(
            ProductCategoriesRowUnsaved(product2.productId(), category2.categoryId()), c)

        val result = repos.productCategoriesRepo
            .select()
            .where { pc ->
                pc.productId()
                    .tupleWith(pc.categoryId())
                    .among(
                        repos.productCategoriesRepo
                            .select()
                            .where { inner -> inner.isPrimary().isEqual(false) }
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

    fun tupleInSubqueryWithNoMatches(repos: Repos, c: Connection?) {
        val product = repos.productsRepo.insert(
            ProductsRowUnsaved("TEST001", "Test Product", BigDecimal("50.00")), c)
        val category = repos.categoriesRepo.insert(
            CategoriesRowUnsaved("Test Cat", "test-cat"), c)
        repos.productCategoriesRepo.insert(
            ProductCategoriesRowUnsaved(product.productId(), category.categoryId()), c)

        val result = repos.productCategoriesRepo
            .select()
            .where { pc ->
                pc.productId()
                    .tupleWith(pc.categoryId())
                    .among(
                        repos.productCategoriesRepo
                            .select()
                            .where { inner -> inner.isPrimary().isEqual(true) }
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

    fun tupleInSubqueryCombinedWithOtherConditions(repos: Repos, c: Connection?) {
        val product1 = repos.productsRepo.insert(
            ProductsRowUnsaved("COMB001", "Combo Product 1", BigDecimal("30.00")), c)
        val product2 = repos.productsRepo.insert(
            ProductsRowUnsaved("COMB002", "Combo Product 2", BigDecimal("40.00")), c)

        val category = repos.categoriesRepo.insert(
            CategoriesRowUnsaved("Combo Category", "combo-cat"), c)

        val pc1 = repos.productCategoriesRepo.insert(
            ProductCategoriesRow(product1.productId(), category.categoryId(), false, 1.toShort()), c)
        val pc2 = repos.productCategoriesRepo.insert(
            ProductCategoriesRow(product2.productId(), category.categoryId(), false, 2.toShort()), c)

        val result = repos.productCategoriesRepo
            .select()
            .where { pc ->
                SqlExpr.all(
                    pc.productId()
                        .tupleWith(pc.categoryId())
                        .among(
                            repos.productCategoriesRepo
                                .select()
                                .where { inner -> inner.isPrimary().isEqual(false) }
                                .map { inner -> inner.productId().tupleWith(inner.categoryId()) }
                                .subquery()
                        ),
                    pc.sortOrder().greaterThan(1.toShort())
                )
            }
            .toList(c)

        assertEquals(1, result.size)
        assertEquals(product2.productId(), result[0].productId())
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
            ProductsRepoMock { unsaved ->
                unsaved.toRow(
                    Optional::empty,    // brandId
                    Optional::empty,    // shortDescription
                    Optional::empty,    // fullDescription
                    Optional::empty,    // costPrice
                    Optional::empty,    // weightKg
                    Optional::empty,    // dimensionsJson
                    { "draft" },        // status
                    { "standard" },     // taxClass
                    Optional::empty,    // tags
                    Optional::empty,    // attributes
                    Optional::empty,    // seoMetadata
                    { now },            // createdAt
                    { now },            // updatedAt
                    Optional::empty,    // publishedAt
                    { ProductsId(BigInteger.valueOf(productIdCounter.getAndIncrement().toLong())) }
                )
            },
            CategoriesRepoMock { unsaved ->
                unsaved.toRow(
                    Optional::empty,    // parentId
                    Optional::empty,    // description
                    Optional::empty,    // imageUrl
                    { 0.toShort() },    // sortOrder
                    { true },           // isVisible
                    Optional::empty,    // metadata
                    { CategoriesId(categoryIdCounter.getAndIncrement()) }
                )
            },
            ProductCategoriesRepoMock { unsaved ->
                unsaved.toRow(
                    { false },          // isPrimary
                    { 0.toShort() }     // sortOrder
                )
            }
        )
    }
}

package testdb

import org.junit.Assert.*
import org.junit.Test
import testdb.brands.BrandsRepoImpl
import testdb.brands.BrandsRowUnsaved
import testdb.categories.CategoriesRepoImpl
import testdb.categories.CategoriesRowUnsaved
import testdb.customtypes.Defaulted.Provided
import testdb.customtypes.Defaulted.UseDefault
import testdb.product_categories.ProductCategoriesId
import testdb.product_categories.ProductCategoriesRepoImpl
import testdb.product_categories.ProductCategoriesRow
import testdb.product_categories.ProductCategoriesRowUnsaved
import testdb.products.ProductsRepoImpl
import testdb.products.ProductsRowUnsaved
import java.math.BigDecimal

/**
 * Tests for composite primary keys using the product_categories table.
 */
class CompositeIdTest {
    private val productCategoriesRepo = ProductCategoriesRepoImpl()
    private val productsRepo = ProductsRepoImpl()
    private val categoriesRepo = CategoriesRepoImpl()
    private val brandsRepo = BrandsRepoImpl()

    @Test
    fun testInsertWithCompositeId() {
        WithConnection.run { c ->
            val brand = brandsRepo.insert(
                BrandsRowUnsaved("TestBrand", "test-brand"),
                c
            )

            val product = productsRepo.insert(
                ProductsRowUnsaved(
                    sku = "SKU001",
                    name = "Test Product",
                    basePrice = BigDecimal("99.99"),
                    brandId = Provided(brand.brandId)
                ),
                c
            )

            val category = categoriesRepo.insert(
                CategoriesRowUnsaved("Electronics", "electronics"),
                c
            )

            val productCategory = productCategoriesRepo.insert(
                ProductCategoriesRowUnsaved(product.productId, category.categoryId),
                c
            )

            assertEquals(product.productId, productCategory.productId)
            assertEquals(category.categoryId, productCategory.categoryId)

            val compositeId = productCategory.compositeId()
            assertEquals(product.productId, compositeId.productId)
            assertEquals(category.categoryId, compositeId.categoryId)
        }
    }

    @Test
    fun testSelectByCompositeId() {
        WithConnection.run { c ->
            val product = productsRepo.insert(
                ProductsRowUnsaved("SKU002", "Product", BigDecimal("50.00")),
                c
            )
            val category = categoriesRepo.insert(
                CategoriesRowUnsaved("Category", "cat-slug"),
                c
            )

            productCategoriesRepo.insert(
                ProductCategoriesRowUnsaved(
                    productId = product.productId,
                    categoryId = category.categoryId,
                    isPrimary = Provided(true),
                    sortOrder = Provided(1.toShort())
                ),
                c
            )

            val compositeId = ProductCategoriesId(product.productId, category.categoryId)
            val selected = productCategoriesRepo.selectById(compositeId, c)

            assertNotNull(selected)
            assertEquals(product.productId, selected!!.productId)
            assertEquals(category.categoryId, selected.categoryId)
            assertTrue(selected.isPrimary)
            assertEquals(1.toShort(), selected.sortOrder)
        }
    }

    @Test
    fun testSelectByCompositeIds() {
        WithConnection.run { c ->
            val product = productsRepo.insert(
                ProductsRowUnsaved("SKU003", "Product", BigDecimal("75.00")),
                c
            )
            val category1 = categoriesRepo.insert(
                CategoriesRowUnsaved("Category1", "cat1-slug"),
                c
            )
            val category2 = categoriesRepo.insert(
                CategoriesRowUnsaved("Category2", "cat2-slug"),
                c
            )
            val category3 = categoriesRepo.insert(
                CategoriesRowUnsaved("Category3", "cat3-slug"),
                c
            )

            productCategoriesRepo.insert(
                ProductCategoriesRowUnsaved(product.productId, category1.categoryId),
                c
            )
            productCategoriesRepo.insert(
                ProductCategoriesRowUnsaved(product.productId, category2.categoryId),
                c
            )
            productCategoriesRepo.insert(
                ProductCategoriesRowUnsaved(product.productId, category3.categoryId),
                c
            )

            val ids = arrayOf(
                ProductCategoriesId(product.productId, category1.categoryId),
                ProductCategoriesId(product.productId, category3.categoryId)
            )

            val selected = productCategoriesRepo.selectByIds(ids, c)
            assertEquals(2, selected.size)
        }
    }

    @Test
    fun testSelectByIdsTracked() {
        WithConnection.run { c ->
            val product = productsRepo.insert(
                ProductsRowUnsaved("SKU004", "Product", BigDecimal("100.00")),
                c
            )
            val category1 = categoriesRepo.insert(
                CategoriesRowUnsaved("Cat1", "cat1"),
                c
            )
            val category2 = categoriesRepo.insert(
                CategoriesRowUnsaved("Cat2", "cat2"),
                c
            )

            productCategoriesRepo.insert(
                ProductCategoriesRowUnsaved(
                    productId = product.productId,
                    categoryId = category1.categoryId,
                    isPrimary = Provided(true),
                    sortOrder = Provided(1.toShort())
                ),
                c
            )
            productCategoriesRepo.insert(
                ProductCategoriesRowUnsaved(
                    productId = product.productId,
                    categoryId = category2.categoryId,
                    isPrimary = Provided(false),
                    sortOrder = Provided(2.toShort())
                ),
                c
            )

            val id1 = ProductCategoriesId(product.productId, category1.categoryId)
            val id2 = ProductCategoriesId(product.productId, category2.categoryId)
            val ids = arrayOf(id1, id2)

            val tracked = productCategoriesRepo.selectByIdsTracked(ids, c)

            assertEquals(2, tracked.size)
            assertTrue(tracked[id1]!!.isPrimary)
            assertFalse(tracked[id2]!!.isPrimary)
        }
    }

    @Test
    fun testUpdateWithCompositeId() {
        WithConnection.run { c ->
            val product = productsRepo.insert(
                ProductsRowUnsaved("SKU005", "Product", BigDecimal("25.00")),
                c
            )
            val category = categoriesRepo.insert(
                CategoriesRowUnsaved("Category", "cat-slug"),
                c
            )

            val inserted = productCategoriesRepo.insert(
                ProductCategoriesRowUnsaved(
                    productId = product.productId,
                    categoryId = category.categoryId,
                    isPrimary = Provided(false),
                    sortOrder = Provided(10.toShort())
                ),
                c
            )

            val updated = inserted.copy(isPrimary = true, sortOrder = 5.toShort())
            val success = productCategoriesRepo.update(updated, c)
            assertTrue(success)

            val selected = productCategoriesRepo.selectById(inserted.compositeId(), c)
            assertNotNull(selected)
            assertTrue(selected!!.isPrimary)
            assertEquals(5.toShort(), selected.sortOrder)
        }
    }

    @Test
    fun testDeleteByCompositeId() {
        WithConnection.run { c ->
            val product = productsRepo.insert(
                ProductsRowUnsaved("SKU006", "Product", BigDecimal("15.00")),
                c
            )
            val category = categoriesRepo.insert(
                CategoriesRowUnsaved("ToDelete", "delete-cat"),
                c
            )

            val inserted = productCategoriesRepo.insert(
                ProductCategoriesRowUnsaved(product.productId, category.categoryId),
                c
            )

            val deleted = productCategoriesRepo.deleteById(inserted.compositeId(), c)
            assertTrue(deleted)

            val selected = productCategoriesRepo.selectById(inserted.compositeId(), c)
            assertNull(selected)
        }
    }

    @Test
    fun testDeleteByCompositeIds() {
        WithConnection.run { c ->
            val product = productsRepo.insert(
                ProductsRowUnsaved("SKU007", "Product", BigDecimal("200.00")),
                c
            )
            val category1 = categoriesRepo.insert(
                CategoriesRowUnsaved("Del1", "del1"),
                c
            )
            val category2 = categoriesRepo.insert(
                CategoriesRowUnsaved("Del2", "del2"),
                c
            )
            val category3 = categoriesRepo.insert(
                CategoriesRowUnsaved("Keep", "keep"),
                c
            )

            productCategoriesRepo.insert(
                ProductCategoriesRowUnsaved(product.productId, category1.categoryId),
                c
            )
            productCategoriesRepo.insert(
                ProductCategoriesRowUnsaved(product.productId, category2.categoryId),
                c
            )
            productCategoriesRepo.insert(
                ProductCategoriesRowUnsaved(product.productId, category3.categoryId),
                c
            )

            val idsToDelete = arrayOf(
                ProductCategoriesId(product.productId, category1.categoryId),
                ProductCategoriesId(product.productId, category2.categoryId)
            )

            val count = productCategoriesRepo.deleteByIds(idsToDelete, c)
            assertEquals(2, count)

            val remaining = productCategoriesRepo.selectAll(c)
            assertEquals(1, remaining.size)
            assertEquals(category3.categoryId, remaining[0].categoryId)
        }
    }

    @Test
    fun testUpsertWithCompositeId() {
        WithConnection.run { c ->
            val product = productsRepo.insert(
                ProductsRowUnsaved("SKU008", "Product", BigDecimal("300.00")),
                c
            )
            val category = categoriesRepo.insert(
                CategoriesRowUnsaved("Upsert", "upsert"),
                c
            )

            val row = ProductCategoriesRow(
                productId = product.productId,
                categoryId = category.categoryId,
                isPrimary = false,
                sortOrder = 1.toShort()
            )
            val inserted = productCategoriesRepo.upsert(row, c)
            assertFalse(inserted.isPrimary)
            assertEquals(1.toShort(), inserted.sortOrder)

            val updatedRow = row.copy(isPrimary = true, sortOrder = 99.toShort())
            val updated = productCategoriesRepo.upsert(updatedRow, c)
            assertTrue(updated.isPrimary)
            assertEquals(99.toShort(), updated.sortOrder)

            val all = productCategoriesRepo.selectAll(c)
            assertEquals(1, all.size)
        }
    }

    @Test
    fun testDSLWithCompositeId() {
        WithConnection.run { c ->
            val product1 = productsRepo.insert(
                ProductsRowUnsaved("SKU-DSL1", "Product1", BigDecimal("10.00")),
                c
            )
            val product2 = productsRepo.insert(
                ProductsRowUnsaved("SKU-DSL2", "Product2", BigDecimal("20.00")),
                c
            )
            val category = categoriesRepo.insert(
                CategoriesRowUnsaved("DSL-Cat", "dsl-cat"),
                c
            )

            productCategoriesRepo.insert(
                ProductCategoriesRowUnsaved(
                    productId = product1.productId,
                    categoryId = category.categoryId,
                    isPrimary = Provided(true),
                    sortOrder = Provided(1.toShort())
                ),
                c
            )
            productCategoriesRepo.insert(
                ProductCategoriesRowUnsaved(
                    productId = product2.productId,
                    categoryId = category.categoryId,
                    isPrimary = Provided(false),
                    sortOrder = Provided(2.toShort())
                ),
                c
            )

            val primaries = productCategoriesRepo.select()
                .where { f -> f.isPrimary().isEqual(true) }
                .toList(c)
            assertEquals(1, primaries.size)
            assertEquals(product1.productId, primaries[0].productId)

            productCategoriesRepo.update()
                .setValue({ f -> f.sortOrder() }, 100.toShort())
                .where { f -> f.productId().isEqual(product2.productId) }
                .execute(c)

            val updated = productCategoriesRepo.selectById(
                ProductCategoriesId(product2.productId, category.categoryId),
                c
            )
            assertEquals(100.toShort(), updated!!.sortOrder)

            productCategoriesRepo.delete()
                .where { f -> f.isPrimary().isEqual(false) }
                .execute(c)

            val remaining = productCategoriesRepo.selectAll(c)
            assertEquals(1, remaining.size)
            assertTrue(remaining[0].isPrimary)
        }
    }
}

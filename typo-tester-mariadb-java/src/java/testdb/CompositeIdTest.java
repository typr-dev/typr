package testdb;

import org.junit.Test;
import testdb.brands.BrandsRepoImpl;
import testdb.brands.BrandsRowUnsaved;
import testdb.categories.CategoriesId;
import testdb.categories.CategoriesRepoImpl;
import testdb.categories.CategoriesRowUnsaved;
import testdb.customtypes.Defaulted.Provided;
import testdb.customtypes.Defaulted.UseDefault;
import testdb.product_categories.ProductCategoriesId;
import testdb.product_categories.ProductCategoriesRepoImpl;
import testdb.product_categories.ProductCategoriesRow;
import testdb.product_categories.ProductCategoriesRowUnsaved;
import testdb.products.ProductsId;
import testdb.products.ProductsRepoImpl;
import testdb.products.ProductsRowUnsaved;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests for composite primary keys using the product_categories table.
 */
public class CompositeIdTest {
    private final ProductCategoriesRepoImpl productCategoriesRepo = new ProductCategoriesRepoImpl();
    private final ProductsRepoImpl productsRepo = new ProductsRepoImpl();
    private final CategoriesRepoImpl categoriesRepo = new CategoriesRepoImpl();
    private final BrandsRepoImpl brandsRepo = new BrandsRepoImpl();

    @Test
    public void testInsertWithCompositeId() {
        WithConnection.run(c -> {
            // Create a brand (optional FK for product)
            var brand = brandsRepo.insert(
                new BrandsRowUnsaved("TestBrand", "test-brand"),
                c
            );

            // Create a product
            var product = productsRepo.insert(
                new ProductsRowUnsaved(
                    "SKU001",
                    "Test Product",
                    new BigDecimal("99.99"),
                    new Provided<>(java.util.Optional.of(brand.brandId())),
                    new UseDefault<>(),
                    new UseDefault<>(),
                    new UseDefault<>(),
                    new UseDefault<>(),
                    new UseDefault<>(),
                    new UseDefault<>(),
                    new UseDefault<>(),
                    new UseDefault<>(),
                    new UseDefault<>(),
                    new UseDefault<>(),
                    new UseDefault<>(),
                    new UseDefault<>(),
                    new UseDefault<>()
                ),
                c
            );

            // Create a category
            var category = categoriesRepo.insert(
                new CategoriesRowUnsaved("Electronics", "electronics"),
                c
            );

            // Create a product-category relationship
            var productCategory = productCategoriesRepo.insert(
                new ProductCategoriesRowUnsaved(product.productId(), category.categoryId()),
                c
            );

            // Verify the composite ID
            assertEquals(product.productId(), productCategory.productId());
            assertEquals(category.categoryId(), productCategory.categoryId());

            var compositeId = productCategory.compositeId();
            assertEquals(product.productId(), compositeId.productId());
            assertEquals(category.categoryId(), compositeId.categoryId());
        });
    }

    @Test
    public void testSelectByCompositeId() {
        WithConnection.run(c -> {
            var brand = brandsRepo.insert(new BrandsRowUnsaved("Brand", "brand-slug"), c);
            var product = productsRepo.insert(
                new ProductsRowUnsaved("SKU002", "Product", new BigDecimal("50.00")),
                c
            );
            var category = categoriesRepo.insert(
                new CategoriesRowUnsaved("Category", "cat-slug"),
                c
            );

            var inserted = productCategoriesRepo.insert(
                new ProductCategoriesRowUnsaved(
                    product.productId(),
                    category.categoryId(),
                    new Provided<>(true),
                    new Provided<>((short) 1)
                ),
                c
            );

            var compositeId = new ProductCategoriesId(product.productId(), category.categoryId());
            var selected = productCategoriesRepo.selectById(compositeId, c);

            assertTrue(selected.isPresent());
            assertEquals(product.productId(), selected.get().productId());
            assertEquals(category.categoryId(), selected.get().categoryId());
            assertTrue(selected.get().isPrimary());
            assertEquals(Short.valueOf((short) 1), selected.get().sortOrder());
        });
    }

    @Test
    public void testSelectByCompositeIds() {
        WithConnection.run(c -> {
            var product = productsRepo.insert(
                new ProductsRowUnsaved("SKU003", "Product", new BigDecimal("75.00")),
                c
            );
            var category1 = categoriesRepo.insert(
                new CategoriesRowUnsaved("Category1", "cat1-slug"),
                c
            );
            var category2 = categoriesRepo.insert(
                new CategoriesRowUnsaved("Category2", "cat2-slug"),
                c
            );
            var category3 = categoriesRepo.insert(
                new CategoriesRowUnsaved("Category3", "cat3-slug"),
                c
            );

            // Create multiple product-category relationships
            productCategoriesRepo.insert(
                new ProductCategoriesRowUnsaved(product.productId(), category1.categoryId()),
                c
            );
            productCategoriesRepo.insert(
                new ProductCategoriesRowUnsaved(product.productId(), category2.categoryId()),
                c
            );
            productCategoriesRepo.insert(
                new ProductCategoriesRowUnsaved(product.productId(), category3.categoryId()),
                c
            );

            // Select by multiple composite IDs
            var ids = new ProductCategoriesId[]{
                new ProductCategoriesId(product.productId(), category1.categoryId()),
                new ProductCategoriesId(product.productId(), category3.categoryId())
            };

            var selected = productCategoriesRepo.selectByIds(ids, c);
            assertEquals(2, selected.size());
        });
    }

    @Test
    public void testSelectByIdsTracked() {
        WithConnection.run(c -> {
            var product = productsRepo.insert(
                new ProductsRowUnsaved("SKU004", "Product", new BigDecimal("100.00")),
                c
            );
            var category1 = categoriesRepo.insert(
                new CategoriesRowUnsaved("Cat1", "cat1"),
                c
            );
            var category2 = categoriesRepo.insert(
                new CategoriesRowUnsaved("Cat2", "cat2"),
                c
            );

            productCategoriesRepo.insert(
                new ProductCategoriesRowUnsaved(
                    product.productId(),
                    category1.categoryId(),
                    new Provided<>(true),
                    new Provided<>((short) 1)
                ),
                c
            );
            productCategoriesRepo.insert(
                new ProductCategoriesRowUnsaved(
                    product.productId(),
                    category2.categoryId(),
                    new Provided<>(false),
                    new Provided<>((short) 2)
                ),
                c
            );

            var id1 = new ProductCategoriesId(product.productId(), category1.categoryId());
            var id2 = new ProductCategoriesId(product.productId(), category2.categoryId());
            var ids = new ProductCategoriesId[]{id1, id2};

            Map<ProductCategoriesId, ProductCategoriesRow> tracked = productCategoriesRepo.selectByIdsTracked(ids, c);

            assertEquals(2, tracked.size());
            assertTrue(tracked.get(id1).isPrimary());
            assertFalse(tracked.get(id2).isPrimary());
        });
    }

    @Test
    public void testUpdateWithCompositeId() {
        WithConnection.run(c -> {
            var product = productsRepo.insert(
                new ProductsRowUnsaved("SKU005", "Product", new BigDecimal("25.00")),
                c
            );
            var category = categoriesRepo.insert(
                new CategoriesRowUnsaved("Category", "cat-slug"),
                c
            );

            var inserted = productCategoriesRepo.insert(
                new ProductCategoriesRowUnsaved(
                    product.productId(),
                    category.categoryId(),
                    new Provided<>(false),
                    new Provided<>((short) 10)
                ),
                c
            );

            // Update the row
            var updated = inserted.withIsPrimary(true).withSortOrder((short) 5);
            var success = productCategoriesRepo.update(updated, c);
            assertTrue(success);

            // Verify the update
            var selected = productCategoriesRepo.selectById(inserted.compositeId(), c);
            assertTrue(selected.isPresent());
            assertTrue(selected.get().isPrimary());
            assertEquals(Short.valueOf((short) 5), selected.get().sortOrder());
        });
    }

    @Test
    public void testDeleteByCompositeId() {
        WithConnection.run(c -> {
            var product = productsRepo.insert(
                new ProductsRowUnsaved("SKU006", "Product", new BigDecimal("15.00")),
                c
            );
            var category = categoriesRepo.insert(
                new CategoriesRowUnsaved("ToDelete", "delete-cat"),
                c
            );

            var inserted = productCategoriesRepo.insert(
                new ProductCategoriesRowUnsaved(product.productId(), category.categoryId()),
                c
            );

            var deleted = productCategoriesRepo.deleteById(inserted.compositeId(), c);
            assertTrue(deleted);

            var selected = productCategoriesRepo.selectById(inserted.compositeId(), c);
            assertFalse(selected.isPresent());
        });
    }

    @Test
    public void testDeleteByCompositeIds() {
        WithConnection.run(c -> {
            var product = productsRepo.insert(
                new ProductsRowUnsaved("SKU007", "Product", new BigDecimal("200.00")),
                c
            );
            var category1 = categoriesRepo.insert(
                new CategoriesRowUnsaved("Del1", "del1"),
                c
            );
            var category2 = categoriesRepo.insert(
                new CategoriesRowUnsaved("Del2", "del2"),
                c
            );
            var category3 = categoriesRepo.insert(
                new CategoriesRowUnsaved("Keep", "keep"),
                c
            );

            productCategoriesRepo.insert(
                new ProductCategoriesRowUnsaved(product.productId(), category1.categoryId()),
                c
            );
            productCategoriesRepo.insert(
                new ProductCategoriesRowUnsaved(product.productId(), category2.categoryId()),
                c
            );
            productCategoriesRepo.insert(
                new ProductCategoriesRowUnsaved(product.productId(), category3.categoryId()),
                c
            );

            var idsToDelete = new ProductCategoriesId[]{
                new ProductCategoriesId(product.productId(), category1.categoryId()),
                new ProductCategoriesId(product.productId(), category2.categoryId())
            };

            var count = productCategoriesRepo.deleteByIds(idsToDelete, c);
            assertEquals(Integer.valueOf(2), count);

            var remaining = productCategoriesRepo.selectAll(c);
            assertEquals(1, remaining.size());
            assertEquals(category3.categoryId(), remaining.get(0).categoryId());
        });
    }

    @Test
    public void testUpsertWithCompositeId() {
        WithConnection.run(c -> {
            var product = productsRepo.insert(
                new ProductsRowUnsaved("SKU008", "Product", new BigDecimal("300.00")),
                c
            );
            var category = categoriesRepo.insert(
                new CategoriesRowUnsaved("Upsert", "upsert"),
                c
            );

            // Insert via upsert
            var row = new ProductCategoriesRow(
                product.productId(),
                category.categoryId(),
                false,
                (short) 1
            );
            var inserted = productCategoriesRepo.upsert(row, c);
            assertFalse(inserted.isPrimary());
            assertEquals(Short.valueOf((short) 1), inserted.sortOrder());

            // Update via upsert
            var updatedRow = row.withIsPrimary(true).withSortOrder((short) 99);
            var updated = productCategoriesRepo.upsert(updatedRow, c);
            assertTrue(updated.isPrimary());
            assertEquals(Short.valueOf((short) 99), updated.sortOrder());

            // Verify only one row exists
            var all = productCategoriesRepo.selectAll(c);
            assertEquals(1, all.size());
        });
    }

    @Test
    public void testDSLWithCompositeId() {
        WithConnection.run(c -> {
            var product1 = productsRepo.insert(
                new ProductsRowUnsaved("SKU-DSL1", "Product1", new BigDecimal("10.00")),
                c
            );
            var product2 = productsRepo.insert(
                new ProductsRowUnsaved("SKU-DSL2", "Product2", new BigDecimal("20.00")),
                c
            );
            var category = categoriesRepo.insert(
                new CategoriesRowUnsaved("DSL-Cat", "dsl-cat"),
                c
            );

            productCategoriesRepo.insert(
                new ProductCategoriesRowUnsaved(
                    product1.productId(),
                    category.categoryId(),
                    new Provided<>(true),
                    new Provided<>((short) 1)
                ),
                c
            );
            productCategoriesRepo.insert(
                new ProductCategoriesRowUnsaved(
                    product2.productId(),
                    category.categoryId(),
                    new Provided<>(false),
                    new Provided<>((short) 2)
                ),
                c
            );

            // Test select with DSL
            var primaries = productCategoriesRepo.select()
                .where(f -> f.isPrimary().isEqual(true))
                .toList(c);
            assertEquals(1, primaries.size());
            assertEquals(product1.productId(), primaries.get(0).productId());

            // Test update with DSL
            productCategoriesRepo.update()
                .setValue(f -> f.sortOrder(), (short) 100)
                .where(f -> f.productId().isEqual(product2.productId()))
                .execute(c);

            var updated = productCategoriesRepo.selectById(
                new ProductCategoriesId(product2.productId(), category.categoryId()),
                c
            );
            assertEquals(Short.valueOf((short) 100), updated.get().sortOrder());

            // Test delete with DSL
            productCategoriesRepo.delete()
                .where(f -> f.isPrimary().isEqual(false))
                .execute(c);

            var remaining = productCategoriesRepo.selectAll(c);
            assertEquals(1, remaining.size());
            assertTrue(remaining.get(0).isPrimary());
        });
    }
}

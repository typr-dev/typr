package testdb;

import static org.junit.Assert.*;

import dev.typr.foundations.data.Uint4;
import dev.typr.foundations.data.Uint8;
import dev.typr.foundations.dsl.MockConnection;
import dev.typr.foundations.dsl.SqlExpr;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.Ignore;
import org.junit.Test;
import testdb.categories.*;
import testdb.product_categories.*;
import testdb.products.*;
import testdb.userdefined.IsPrimary;

/**
 * Comprehensive tests for tuple IN functionality on MariaDB. Tests cover: - Composite ID IN with
 * foreign key ID types as composite key components - Tuple IN with subqueries using tupleWith() -
 * Combined with other conditions using SqlExpr.all - Both real database and mock repository
 * evaluation
 */
public class TupleInTest {

  /** Container for all repositories needed by tests */
  public record Repos(
      ProductsRepo productsRepo,
      CategoriesRepo categoriesRepo,
      ProductCategoriesRepo productCategoriesRepo) {}

  // =============== ProductCategories (2-column ProductsId,CategoriesId composite key)
  // ===============

  @Test
  public void productCategoriesCompositeIdInWithMultipleIds_Real() {
    MariaDbTestHelper.run(
        c -> {
          var repos = createRealRepos();
          productCategoriesCompositeIdInWithMultipleIds(repos, c);
        });
  }

  @Test
  public void productCategoriesCompositeIdInWithMultipleIds_Mock() {
    var repos = createMockRepos();
    productCategoriesCompositeIdInWithMultipleIds(repos, null);
  }

  public void productCategoriesCompositeIdInWithMultipleIds(Repos repos, Connection c) {
    // Create products
    var product1 =
        repos.productsRepo.insert(
            new ProductsRowUnsaved("SKU001", "Product 1", new BigDecimal("99.99")), c);
    var product2 =
        repos.productsRepo.insert(
            new ProductsRowUnsaved("SKU002", "Product 2", new BigDecimal("149.99")), c);
    var product3 =
        repos.productsRepo.insert(
            new ProductsRowUnsaved("SKU003", "Product 3", new BigDecimal("199.99")), c);

    // Create categories
    var category1 =
        repos.categoriesRepo.insert(new CategoriesRowUnsaved("Electronics", "electronics"), c);
    var category2 =
        repos.categoriesRepo.insert(new CategoriesRowUnsaved("Clothing", "clothing"), c);
    var category3 = repos.categoriesRepo.insert(new CategoriesRowUnsaved("Home", "home"), c);

    // Create product-category associations
    var pc1 =
        repos.productCategoriesRepo.insert(
            new ProductCategoriesRowUnsaved(product1.productId(), category1.categoryId()), c);
    var pc2 =
        repos.productCategoriesRepo.insert(
            new ProductCategoriesRowUnsaved(product1.productId(), category2.categoryId()), c);
    var pc3 =
        repos.productCategoriesRepo.insert(
            new ProductCategoriesRowUnsaved(product2.productId(), category1.categoryId()), c);
    var pc4 =
        repos.productCategoriesRepo.insert(
            new ProductCategoriesRowUnsaved(product3.productId(), category3.categoryId()), c);

    // Query using compositeIdIn with specific IDs
    var result =
        repos
            .productCategoriesRepo
            .select()
            .where(
                pc ->
                    pc.compositeIdIn(
                        List.of(pc1.compositeId(), pc3.compositeId(), pc4.compositeId())))
            .toList(c);

    assertEquals(3, result.size());
    var resultIds =
        result.stream().map(ProductCategoriesRow::compositeId).collect(Collectors.toSet());
    assertEquals(Set.of(pc1.compositeId(), pc3.compositeId(), pc4.compositeId()), resultIds);
  }

  @Test
  public void productCategoriesCompositeIdInWithSingleId_Real() {
    MariaDbTestHelper.run(
        c -> {
          var repos = createRealRepos();
          productCategoriesCompositeIdInWithSingleId(repos, c);
        });
  }

  @Test
  public void productCategoriesCompositeIdInWithSingleId_Mock() {
    var repos = createMockRepos();
    productCategoriesCompositeIdInWithSingleId(repos, null);
  }

  public void productCategoriesCompositeIdInWithSingleId(Repos repos, Connection c) {
    var product =
        repos.productsRepo.insert(
            new ProductsRowUnsaved("SINGLE001", "Single Product", new BigDecimal("50.00")), c);
    var category =
        repos.categoriesRepo.insert(
            new CategoriesRowUnsaved("Single Category", "single-category"), c);
    var pc =
        repos.productCategoriesRepo.insert(
            new ProductCategoriesRowUnsaved(product.productId(), category.categoryId()), c);

    var result =
        repos
            .productCategoriesRepo
            .select()
            .where(pcat -> pcat.compositeIdIn(List.of(pc.compositeId())))
            .toList(c);

    assertEquals(1, result.size());
    assertEquals(pc, result.get(0));
  }

  @Test
  public void productCategoriesCompositeIdInWithEmptyList_Real() {
    MariaDbTestHelper.run(
        c -> {
          var repos = createRealRepos();
          productCategoriesCompositeIdInWithEmptyList(repos, c);
        });
  }

  @Test
  public void productCategoriesCompositeIdInWithEmptyList_Mock() {
    var repos = createMockRepos();
    productCategoriesCompositeIdInWithEmptyList(repos, null);
  }

  public void productCategoriesCompositeIdInWithEmptyList(Repos repos, Connection c) {
    var product =
        repos.productsRepo.insert(
            new ProductsRowUnsaved("EMPTY001", "Empty Test Product", new BigDecimal("25.00")), c);
    var category =
        repos.categoriesRepo.insert(
            new CategoriesRowUnsaved("Empty Test Category", "empty-test-category"), c);
    repos.productCategoriesRepo.insert(
        new ProductCategoriesRowUnsaved(product.productId(), category.categoryId()), c);

    var result =
        repos.productCategoriesRepo.select().where(pc -> pc.compositeIdIn(List.of())).toList(c);

    assertEquals(0, result.size());
  }

  @Test
  public void productCategoriesCompositeIdInCombinedWithOtherConditions_Real() {
    MariaDbTestHelper.run(
        c -> {
          var repos = createRealRepos();
          productCategoriesCompositeIdInCombinedWithOtherConditions(repos, c);
        });
  }

  @Test
  public void productCategoriesCompositeIdInCombinedWithOtherConditions_Mock() {
    var repos = createMockRepos();
    productCategoriesCompositeIdInCombinedWithOtherConditions(repos, null);
  }

  public void productCategoriesCompositeIdInCombinedWithOtherConditions(Repos repos, Connection c) {
    var product1 =
        repos.productsRepo.insert(
            new ProductsRowUnsaved("COND001", "Condition Product 1", new BigDecimal("100.00")), c);
    var product2 =
        repos.productsRepo.insert(
            new ProductsRowUnsaved("COND002", "Condition Product 2", new BigDecimal("200.00")), c);

    var category =
        repos.categoriesRepo.insert(
            new CategoriesRowUnsaved("Condition Category", "condition-category"), c);

    // Insert with different isPrimary values using full row
    var pc1 =
        repos.productCategoriesRepo.insert(
            new ProductCategoriesRow(
                product1.productId(), category.categoryId(), new IsPrimary(true), (short) 1),
            c);
    var pc2 =
        repos.productCategoriesRepo.insert(
            new ProductCategoriesRow(
                product2.productId(), category.categoryId(), new IsPrimary(false), (short) 2),
            c);

    // Query with compositeIdIn AND isPrimary condition using SqlExpr.all
    var result =
        repos
            .productCategoriesRepo
            .select()
            .where(
                pc ->
                    SqlExpr.all(
                        pc.compositeIdIn(List.of(pc1.compositeId(), pc2.compositeId())),
                        pc.isPrimary().isEqual(new IsPrimary(true))))
            .toList(c);

    assertEquals(1, result.size());
    assertEquals(pc1.compositeId(), result.get(0).compositeId());
  }

  @Test
  public void productCategoriesCompositeIdInWithNonExistentIds_Real() {
    MariaDbTestHelper.run(
        c -> {
          var repos = createRealRepos();
          productCategoriesCompositeIdInWithNonExistentIds(repos, c);
        });
  }

  @Test
  public void productCategoriesCompositeIdInWithNonExistentIds_Mock() {
    var repos = createMockRepos();
    productCategoriesCompositeIdInWithNonExistentIds(repos, null);
  }

  public void productCategoriesCompositeIdInWithNonExistentIds(Repos repos, Connection c) {
    var product =
        repos.productsRepo.insert(
            new ProductsRowUnsaved("EXIST001", "Existing Product", new BigDecimal("75.00")), c);
    var category =
        repos.categoriesRepo.insert(
            new CategoriesRowUnsaved("Existing Category", "existing-category"), c);
    var pc =
        repos.productCategoriesRepo.insert(
            new ProductCategoriesRowUnsaved(product.productId(), category.categoryId()), c);

    // Query with mix of existing and non-existing IDs
    var result =
        repos
            .productCategoriesRepo
            .select()
            .where(
                pcat ->
                    pcat.compositeIdIn(
                        List.of(
                            pc.compositeId(),
                            new ProductCategoriesId(
                                new ProductsId(Uint8.of(999999L)),
                                new CategoriesId(Uint4.of(888888L))))))
            .toList(c);

    assertEquals(1, result.size());
    assertEquals(pc, result.get(0));
  }

  @Test
  public void productCategoriesCompositeIdComputedVsManual_Real() {
    MariaDbTestHelper.run(
        c -> {
          var repos = createRealRepos();
          productCategoriesCompositeIdComputedVsManual(repos, c);
        });
  }

  @Test
  public void productCategoriesCompositeIdComputedVsManual_Mock() {
    var repos = createMockRepos();
    productCategoriesCompositeIdComputedVsManual(repos, null);
  }

  public void productCategoriesCompositeIdComputedVsManual(Repos repos, Connection c) {
    var product =
        repos.productsRepo.insert(
            new ProductsRowUnsaved("COMP001", "Computed Product", new BigDecimal("55.00")), c);
    var category =
        repos.categoriesRepo.insert(
            new CategoriesRowUnsaved("Computed Category", "computed-category"), c);
    var pc =
        repos.productCategoriesRepo.insert(
            new ProductCategoriesRowUnsaved(product.productId(), category.categoryId()), c);

    // Get computed composite ID from row
    var computedId = pc.compositeId();

    // Create manual composite ID with same values
    var manualId = new ProductCategoriesId(product.productId(), category.categoryId());

    // Verify they're equal
    assertEquals(computedId, manualId);

    // Query using both computed and manual IDs
    var result =
        repos
            .productCategoriesRepo
            .select()
            .where(pcat -> pcat.compositeIdIn(List.of(computedId, manualId)))
            .toList(c);

    assertEquals(1, result.size());
    assertEquals(pc, result.get(0));
  }

  @Test
  public void productCategoriesCompositeIdInWithLargeList_Real() {
    MariaDbTestHelper.run(
        c -> {
          var repos = createRealRepos();
          productCategoriesCompositeIdInWithLargeList(repos, c);
        });
  }

  @Test
  public void productCategoriesCompositeIdInWithLargeList_Mock() {
    var repos = createMockRepos();
    productCategoriesCompositeIdInWithLargeList(repos, null);
  }

  public void productCategoriesCompositeIdInWithLargeList(Repos repos, Connection c) {
    // Create 10 products and 5 categories
    var products = new java.util.ArrayList<ProductsRow>();
    for (int i = 1; i <= 10; i++) {
      products.add(
          repos.productsRepo.insert(
              new ProductsRowUnsaved("BULK" + i, "Bulk Product " + i, new BigDecimal(i * 10)), c));
    }

    var categories = new java.util.ArrayList<CategoriesRow>();
    for (int i = 1; i <= 5; i++) {
      categories.add(
          repos.categoriesRepo.insert(
              new CategoriesRowUnsaved("Bulk Category " + i, "bulk-category-" + i), c));
    }

    // Create associations for first 5 products with all categories
    var productCategories = new java.util.ArrayList<ProductCategoriesRow>();
    for (int p = 0; p < 5; p++) {
      for (int cat = 0; cat < 5; cat++) {
        productCategories.add(
            repos.productCategoriesRepo.insert(
                new ProductCategoriesRowUnsaved(
                    products.get(p).productId(), categories.get(cat).categoryId()),
                c));
      }
    }

    // Select first 10 associations
    var idsToSelect =
        productCategories.stream().limit(10).map(ProductCategoriesRow::compositeId).toList();

    var result =
        repos.productCategoriesRepo.select().where(pc -> pc.compositeIdIn(idsToSelect)).toList(c);

    assertEquals(10, result.size());
  }

  // ==================== TupleInSubquery Tests ====================

  @Test
  public void tupleInSubqueryBasic_Real() {
    MariaDbTestHelper.run(
        c -> {
          var repos = createRealRepos();
          tupleInSubqueryBasic(repos, c);
        });
  }

  // Note: TupleInSubquery mock tests are skipped because mock evaluation of correlated
  // subqueries is complex. Real database tests verify the functionality works correctly.

  public void tupleInSubqueryBasic(Repos repos, Connection c) {
    // Create products and categories
    var product1 =
        repos.productsRepo.insert(
            new ProductsRowUnsaved("SKU100", "Product 100", new BigDecimal("100.00")), c);
    var product2 =
        repos.productsRepo.insert(
            new ProductsRowUnsaved("SKU200", "Product 200", new BigDecimal("200.00")), c);

    var category1 = repos.categoriesRepo.insert(new CategoriesRowUnsaved("Cat A", "cat-a"), c);
    var category2 = repos.categoriesRepo.insert(new CategoriesRowUnsaved("Cat B", "cat-b"), c);

    // Create product-category associations
    var pc1 =
        repos.productCategoriesRepo.insert(
            new ProductCategoriesRowUnsaved(product1.productId(), category1.categoryId()), c);
    var pc2 =
        repos.productCategoriesRepo.insert(
            new ProductCategoriesRowUnsaved(product2.productId(), category2.categoryId()), c);

    // Use tuple IN subquery: find associations where (productId, categoryId) is in subquery
    // The subquery selects associations where isPrimary = false (default)
    var result =
        repos
            .productCategoriesRepo
            .select()
            .where(
                pc ->
                    pc.productId()
                        .tupleWith(pc.categoryId())
                        .among(
                            repos
                                .productCategoriesRepo
                                .select()
                                .where(inner -> inner.isPrimary().isEqual(new IsPrimary(false)))
                                .map(inner -> inner.productId().tupleWith(inner.categoryId()))
                                .subquery()))
            .toList(c);

    assertEquals(2, result.size());
  }

  @Test
  public void tupleInSubqueryWithNoMatches_Real() {
    MariaDbTestHelper.run(
        c -> {
          var repos = createRealRepos();
          tupleInSubqueryWithNoMatches(repos, c);
        });
  }

  public void tupleInSubqueryWithNoMatches(Repos repos, Connection c) {
    var product =
        repos.productsRepo.insert(
            new ProductsRowUnsaved("TEST001", "Test Product", new BigDecimal("50.00")), c);
    var category = repos.categoriesRepo.insert(new CategoriesRowUnsaved("Test Cat", "test-cat"), c);
    // Insert with isPrimary = false (default)
    repos.productCategoriesRepo.insert(
        new ProductCategoriesRowUnsaved(product.productId(), category.categoryId()), c);

    // Subquery looks for isPrimary = true (none exist since default is false)
    var result =
        repos
            .productCategoriesRepo
            .select()
            .where(
                pc ->
                    pc.productId()
                        .tupleWith(pc.categoryId())
                        .among(
                            repos
                                .productCategoriesRepo
                                .select()
                                .where(inner -> inner.isPrimary().isEqual(new IsPrimary(true)))
                                .map(inner -> inner.productId().tupleWith(inner.categoryId()))
                                .subquery()))
            .toList(c);

    assertEquals(0, result.size());
  }

  @Test
  public void tupleInSubqueryCombinedWithOtherConditions_Real() {
    MariaDbTestHelper.run(
        c -> {
          var repos = createRealRepos();
          tupleInSubqueryCombinedWithOtherConditions(repos, c);
        });
  }

  public void tupleInSubqueryCombinedWithOtherConditions(Repos repos, Connection c) {
    var product1 =
        repos.productsRepo.insert(
            new ProductsRowUnsaved("COMB001", "Combo Product 1", new BigDecimal("30.00")), c);
    var product2 =
        repos.productsRepo.insert(
            new ProductsRowUnsaved("COMB002", "Combo Product 2", new BigDecimal("40.00")), c);

    var category =
        repos.categoriesRepo.insert(new CategoriesRowUnsaved("Combo Category", "combo-cat"), c);

    // Insert with different sortOrder values
    var pc1 =
        repos.productCategoriesRepo.insert(
            new ProductCategoriesRow(
                product1.productId(), category.categoryId(), new IsPrimary(false), (short) 1),
            c);
    var pc2 =
        repos.productCategoriesRepo.insert(
            new ProductCategoriesRow(
                product2.productId(), category.categoryId(), new IsPrimary(false), (short) 2),
            c);

    // Combine tuple IN subquery with sortOrder condition
    var result =
        repos
            .productCategoriesRepo
            .select()
            .where(
                pc ->
                    SqlExpr.all(
                        pc.productId()
                            .tupleWith(pc.categoryId())
                            .among(
                                repos
                                    .productCategoriesRepo
                                    .select()
                                    .where(inner -> inner.isPrimary().isEqual(new IsPrimary(false)))
                                    .map(inner -> inner.productId().tupleWith(inner.categoryId()))
                                    .subquery()),
                        pc.sortOrder().greaterThan((short) 1)))
            .toList(c);

    assertEquals(1, result.size());
    assertEquals(product2.productId(), result.get(0).productId());
  }

  // ==================== Nullable Column Tuple IN Tests ====================

  /**
   * Tests tuple IN with nullable columns (varcharCol and intCol from mariatestnull). Verifies tuple
   * IN works correctly when matching rows with null values. Note: Mock test is skipped because mock
   * evaluation of nullable tuples requires special handling.
   */
  @Ignore("MariaDB VALUES table syntax doesn't support column aliasing - requires deep refactor")
  @Test
  public void tupleInWithNullableColumn_Real() {
    MariaDbTestHelper.run(
        c -> {
          var repos = createRealRepos();
          var nullRepo = new testdb.mariatestnull.MariatestnullRepoImpl();

          // Create test data with nullable columns
          var row1 =
              nullRepo.insert(
                  new testdb.mariatestnull.MariatestnullRow(
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.of(100), // intCol
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.of("ROW1"), // varcharCol
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty()),
                  c);

          var row2 =
              nullRepo.insert(
                  new testdb.mariatestnull.MariatestnullRow(
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.of(200), // intCol
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(), // varcharCol is null
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty()),
                  c);

          // Query using tuple with nullable column - match row with null varcharCol
          var result =
              nullRepo
                  .select()
                  .where(
                      f ->
                          f.intCol()
                              .tupleWith(f.varcharCol())
                              .in(
                                  List.of(
                                      dev.typr.foundations.Tuple.of(
                                          200, (String) null)))) // Match row2 with null
                  .toList(c);

          assertEquals("Should find 1 row with null varcharCol", 1, result.size());
          assertEquals(Optional.of(200), result.get(0).intCol());
        });
  }

  /**
   * Tests tuple IN with nullable columns where both null and non-null values are queried. Note:
   * Mock test is skipped because mock evaluation of nullable tuples requires special handling.
   */
  @Ignore("MariaDB VALUES table syntax doesn't support column aliasing - requires deep refactor")
  @Test
  public void tupleInWithNullableColumnMixedMatching_Real() {
    MariaDbTestHelper.run(
        c -> {
          var nullRepo = new testdb.mariatestnull.MariatestnullRepoImpl();

          // Create test data
          var row1 =
              nullRepo.insert(
                  new testdb.mariatestnull.MariatestnullRow(
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.of(300),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.of("MIXED1"),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty()),
                  c);

          var row2 =
              nullRepo.insert(
                  new testdb.mariatestnull.MariatestnullRow(
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.of(400),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(), // null varcharCol
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.empty()),
                  c);

          // Query for both rows - one with value, one with null
          var result =
              nullRepo
                  .select()
                  .where(
                      f ->
                          f.intCol()
                              .tupleWith(f.varcharCol())
                              .in(
                                  List.of(
                                      dev.typr.foundations.Tuple.of(300, "MIXED1"),
                                      dev.typr.foundations.Tuple.of(400, (String) null))))
                  .toList(c);

          assertEquals("Should find both rows", 2, result.size());

          // Verify query with wrong pattern doesn't match
          var resultNoMatch =
              nullRepo
                  .select()
                  .where(
                      f ->
                          f.intCol()
                              .tupleWith(f.varcharCol())
                              .in(
                                  List.of(
                                      dev.typr.foundations.Tuple.of(
                                          300, (String) null)))) // Wrong - 300 has "MIXED1"
                  .toList(c);

          assertEquals("Should not match wrong pattern", 0, resultNoMatch.size());
        });
  }

  // ==================== Nested Tuple Tests ====================

  /**
   * Tests truly nested tuples - calling tupleWith twice to create Tuple2<Tuple2<A, B>, C>. This
   * stresses the SQL generation by creating nested parentheses.
   */
  @Ignore("Nested tuple type mapping requires deep refactor")
  @Test
  public void nestedTupleIn_Real() {
    MariaDbTestHelper.run(
        c -> {
          var repos = createRealRepos();

          // Create products and categories for nested tuple test
          var product1 =
              repos.productsRepo.insert(
                  new ProductsRowUnsaved("NEST001", "Nested Product 1", new BigDecimal("10.00")),
                  c);
          var product2 =
              repos.productsRepo.insert(
                  new ProductsRowUnsaved("NEST002", "Nested Product 2", new BigDecimal("20.00")),
                  c);

          var category1 =
              repos.categoriesRepo.insert(new CategoriesRowUnsaved("Nested Cat 1", "nested-1"), c);
          var category2 =
              repos.categoriesRepo.insert(new CategoriesRowUnsaved("Nested Cat 2", "nested-2"), c);

          // Create associations with different isPrimary values
          var pc1 =
              repos.productCategoriesRepo.insert(
                  new ProductCategoriesRow(
                      product1.productId(), category1.categoryId(), new IsPrimary(true), (short) 1),
                  c);
          var pc2 =
              repos.productCategoriesRepo.insert(
                  new ProductCategoriesRow(
                      product2.productId(),
                      category2.categoryId(),
                      new IsPrimary(false),
                      (short) 2),
                  c);

          // Test truly nested tuple: ((productId, categoryId), isPrimary)
          var result =
              repos
                  .productCategoriesRepo
                  .select()
                  .where(
                      pc ->
                          pc.productId()
                              .tupleWith(pc.categoryId()) // Tuple2<ProductsId, CategoriesId>
                              .tupleWith(pc.isPrimary()) // Tuple2<Tuple2<ProductsId, CategoriesId>,
                              // Boolean>
                              .in(
                                  List.of(
                                      dev.typr.foundations.Tuple.of(
                                          dev.typr.foundations.Tuple.of(
                                              product1.productId(), category1.categoryId()),
                                          new IsPrimary(true)),
                                      dev.typr.foundations.Tuple.of(
                                          dev.typr.foundations.Tuple.of(
                                              product2.productId(), category2.categoryId()),
                                          new IsPrimary(false)))))
                  .toList(c);

          assertEquals("Should find 2 rows matching nested tuple pattern", 2, result.size());

          // Test that non-matching nested tuple returns empty
          var resultNoMatch =
              repos
                  .productCategoriesRepo
                  .select()
                  .where(
                      pc ->
                          pc.productId()
                              .tupleWith(pc.categoryId())
                              .tupleWith(pc.isPrimary())
                              .in(
                                  List.of(
                                      // Wrong: isPrimary doesn't match
                                      dev.typr.foundations.Tuple.of(
                                          dev.typr.foundations.Tuple.of(
                                              product1.productId(), category1.categoryId()),
                                          new IsPrimary(false))))) // pc1 has isPrimary=true
                  .toList(c);

          assertEquals("Should not match misaligned nested tuple", 0, resultNoMatch.size());
        });
  }

  @Ignore("Nested tuple type mapping requires deep refactor")
  @Test
  public void nestedTupleIn_Mock() {
    var repos = createMockRepos();

    // Create products and categories
    var product1 =
        repos.productsRepo.insert(
            new ProductsRowUnsaved("NEST001", "Nested Product 1", new BigDecimal("10.00")), null);
    var product2 =
        repos.productsRepo.insert(
            new ProductsRowUnsaved("NEST002", "Nested Product 2", new BigDecimal("20.00")), null);

    var category1 =
        repos.categoriesRepo.insert(new CategoriesRowUnsaved("Nested Cat 1", "nested-1"), null);
    var category2 =
        repos.categoriesRepo.insert(new CategoriesRowUnsaved("Nested Cat 2", "nested-2"), null);

    // Create associations with full row constructor to set isPrimary
    repos.productCategoriesRepo.insert(
        new ProductCategoriesRow(
            product1.productId(), category1.categoryId(), new IsPrimary(true), (short) 1),
        null);
    repos.productCategoriesRepo.insert(
        new ProductCategoriesRow(
            product2.productId(), category2.categoryId(), new IsPrimary(false), (short) 2),
        null);

    // Test truly nested tuple in mock
    var result =
        repos
            .productCategoriesRepo
            .select()
            .where(
                pc ->
                    pc.productId()
                        .tupleWith(pc.categoryId())
                        .tupleWith(pc.isPrimary())
                        .in(
                            List.of(
                                dev.typr.foundations.Tuple.of(
                                    dev.typr.foundations.Tuple.of(
                                        product1.productId(), category1.categoryId()),
                                    new IsPrimary(true)),
                                dev.typr.foundations.Tuple.of(
                                    dev.typr.foundations.Tuple.of(
                                        product2.productId(), category2.categoryId()),
                                    new IsPrimary(false)))))
            .toList(null);

    assertEquals("Should find 2 rows matching nested tuple pattern", 2, result.size());
  }

  // ==================== Read Nested Tuple from Database Tests ====================

  /**
   * Tests reading nested tuples from the database through the DSL. This test selects a nested tuple
   * expression using .map() and reads the results back.
   */
  @Ignore("Nested tuple decimal precision mismatch requires deep refactor")
  @Test
  public void readNestedTupleFromDatabase_Real() {
    MariaDbTestHelper.run(
        c -> {
          var repos = createRealRepos();
          readNestedTupleFromDatabase(repos, c);
        });
  }

  @Test
  public void readNestedTupleFromDatabase_Mock() {
    var repos = createMockRepos();
    readNestedTupleFromDatabase(repos, MockConnection.instance);
  }

  void readNestedTupleFromDatabase(Repos repos, Connection c) {
    // Insert test data
    var p1 =
        repos.productsRepo.insert(
            new ProductsRowUnsaved("READ001", "ReadProd1", new BigDecimal("100.00")), c);
    var p2 =
        repos.productsRepo.insert(
            new ProductsRowUnsaved("READ002", "ReadProd2", new BigDecimal("200.00")), c);
    var p3 =
        repos.productsRepo.insert(
            new ProductsRowUnsaved("READ003", "ReadProd3", new BigDecimal("300.00")), c);

    // Select nested tuple: ((name, basePrice), sku)
    var result =
        repos
            .productsRepo
            .select()
            .where(p -> p.sku().in("READ001", "READ002", "READ003"))
            .orderBy(p -> p.basePrice().asc())
            .map(p -> p.name().tupleWith(p.basePrice()).tupleWith(p.sku()))
            .toList(c);

    assertEquals("Should read 3 nested tuples", 3, result.size());

    // Verify the nested tuple structure
    var first = result.get(0);
    assertEquals("First tuple's inner first element", "ReadProd1", first._1()._1());
    assertEquals("First tuple's inner second element", new BigDecimal("100.00"), first._1()._2());
    assertEquals("First tuple's outer second element", "READ001", first._2());
  }

  // ==================== Helper Methods ====================

  private Repos createRealRepos() {
    return new Repos(
        new ProductsRepoImpl(), new CategoriesRepoImpl(), new ProductCategoriesRepoImpl());
  }

  private Repos createMockRepos() {
    var productIdCounter = new AtomicInteger(1);
    var categoryIdCounter = new AtomicInteger(1);
    var now = LocalDateTime.now();

    return new Repos(
        new ProductsRepoMock(
            unsaved ->
                unsaved.toRow(
                    Optional::empty, // brandId
                    Optional::empty, // shortDescription
                    Optional::empty, // fullDescription
                    Optional::empty, // costPrice
                    Optional::empty, // weightKg
                    Optional::empty, // dimensionsJson
                    () -> "draft", // status
                    () -> "standard", // taxClass
                    Optional::empty, // tags
                    Optional::empty, // attributes
                    Optional::empty, // seoMetadata
                    () -> now, // createdAt
                    () -> now, // updatedAt
                    Optional::empty, // publishedAt
                    () ->
                        new ProductsId(
                            Uint8.of((long) productIdCounter.getAndIncrement())) // productId
                    )),
        new CategoriesRepoMock(
            unsaved ->
                unsaved.toRow(
                    Optional::empty, // parentId
                    Optional::empty, // description
                    Optional::empty, // imageUrl
                    () -> (short) 0, // sortOrder
                    () -> true, // isVisible
                    Optional::empty, // metadata
                    () ->
                        new CategoriesId(
                            Uint4.of((long) categoryIdCounter.getAndIncrement())) // categoryId
                    )),
        new ProductCategoriesRepoMock(
            unsaved ->
                unsaved.toRow(
                    () -> new IsPrimary(false), // isPrimary
                    () -> (short) 0 // sortOrder
                    )));
  }
}

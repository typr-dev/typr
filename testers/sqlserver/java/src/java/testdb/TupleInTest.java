package testdb;

import static org.junit.Assert.assertEquals;

import dev.typr.dsl.SqlExpr;
import dev.typr.foundations.Bijection;
import dev.typr.foundations.Connection;
import dev.typr.foundations.Tuple;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.Ignore;
import org.junit.Test;
import testdb.products.*;

/**
 * Comprehensive tests for arbitrary tuple IN functionality on SQL Server. Since SQL Server test
 * schema doesn't have composite key tables, we test: - Arbitrary tuple expressions using Tuple.of()
 * - IN with Rows.ofTuples for inline tuple values - Combined tuple conditions with SqlExpr.all -
 * Both real database and mock repository evaluation
 *
 * <p>Note: SQL Server doesn't support tuple IN syntax natively, so the DSL emulates it using EXISTS
 * with VALUES table constructor.
 */
public class TupleInTest {

  /** Container for all repositories needed by tests */
  public record Repos(ProductsRepo productsRepo) {}

  // =============== Arbitrary Tuple IN Tests ===============

  @Test
  public void tupleInWithNameAndPrice_Real() {
    SqlServerTestHelper.run(
        c -> {
          var repos = createRealRepos();
          tupleInWithNameAndPrice(repos, c);
        });
  }

  @Test
  public void tupleInWithNameAndPrice_Mock() {
    var repos = createMockRepos();
    tupleInWithNameAndPrice(repos, null);
  }

  public void tupleInWithNameAndPrice(Repos repos, Connection c) {
    // Insert products with specific name/price combinations
    var p1 = insertProduct(repos, "Widget", new BigDecimal("19.99"), c);
    var p2 = insertProduct(repos, "Gadget", new BigDecimal("29.99"), c);
    var p3 = insertProduct(repos, "Widget", new BigDecimal("39.99"), c);
    var p4 = insertProduct(repos, "Gizmo", new BigDecimal("19.99"), c);

    // Query using tuple IN: (name, price) IN (('Widget', 19.99), ('Gadget', 29.99))
    var result =
        repos
            .productsRepo
            .select()
            .where(
                p ->
                    p.name()
                        .tupleWith(p.price())
                        .among(
                            Tuple.of("Widget", new BigDecimal("19.99")),
                            Tuple.of("Gadget", new BigDecimal("29.99"))))
            .toList(c);

    assertEquals(2, result.size());
    var names = result.stream().map(ProductsRow::name).collect(Collectors.toSet());
    assertEquals(Set.of("Widget", "Gadget"), names);
  }

  @Test
  public void tupleInWithSingleTuple_Real() {
    SqlServerTestHelper.run(
        c -> {
          var repos = createRealRepos();
          tupleInWithSingleTuple(repos, c);
        });
  }

  @Test
  public void tupleInWithSingleTuple_Mock() {
    var repos = createMockRepos();
    tupleInWithSingleTuple(repos, null);
  }

  public void tupleInWithSingleTuple(Repos repos, Connection c) {
    var p1 = insertProduct(repos, "SingleItem", new BigDecimal("99.99"), c);
    var p2 = insertProduct(repos, "OtherItem", new BigDecimal("88.88"), c);

    // Query with single tuple
    var result =
        repos
            .productsRepo
            .select()
            .where(
                p ->
                    p.name()
                        .tupleWith(p.price())
                        .among(List.of(Tuple.of("SingleItem", new BigDecimal("99.99")))))
            .toList(c);

    assertEquals(1, result.size());
    assertEquals("SingleItem", result.get(0).name());
  }

  @Test
  public void tupleInWithEmptyList_Real() {
    SqlServerTestHelper.run(
        c -> {
          var repos = createRealRepos();
          tupleInWithEmptyList(repos, c);
        });
  }

  @Test
  public void tupleInWithEmptyList_Mock() {
    var repos = createMockRepos();
    tupleInWithEmptyList(repos, null);
  }

  public void tupleInWithEmptyList(Repos repos, Connection c) {
    insertProduct(repos, "TestProduct", new BigDecimal("50.00"), c);

    // Query with empty tuple list - should return no results
    var result =
        repos
            .productsRepo
            .select()
            .where(p -> p.name().tupleWith(p.price()).among(List.of()))
            .toList(c);

    assertEquals(0, result.size());
  }

  @Test
  public void tupleInCombinedWithOtherConditions_Real() {
    SqlServerTestHelper.run(
        c -> {
          var repos = createRealRepos();
          tupleInCombinedWithOtherConditions(repos, c);
        });
  }

  @Test
  public void tupleInCombinedWithOtherConditions_Mock() {
    var repos = createMockRepos();
    tupleInCombinedWithOtherConditions(repos, null);
  }

  public void tupleInCombinedWithOtherConditions(Repos repos, Connection c) {
    var p1 =
        insertProduct(repos, "Alpha", new BigDecimal("10.00"), Optional.of("First product"), c);
    var p2 =
        insertProduct(repos, "Beta", new BigDecimal("20.00"), Optional.of("Second product"), c);
    var p3 = insertProduct(repos, "Gamma", new BigDecimal("10.00"), Optional.empty(), c);

    // Query with tuple IN AND description condition
    var result =
        repos
            .productsRepo
            .select()
            .where(
                p ->
                    SqlExpr.all(
                        p.name()
                            .tupleWith(p.price())
                            .among(
                                Tuple.of("Alpha", new BigDecimal("10.00")),
                                Tuple.of("Beta", new BigDecimal("20.00")),
                                Tuple.of("Gamma", new BigDecimal("10.00"))),
                        p.description().isNotNull()))
            .toList(c);

    assertEquals(2, result.size());
    var names = result.stream().map(ProductsRow::name).collect(Collectors.toSet());
    assertEquals(Set.of("Alpha", "Beta"), names);
  }

  @Test
  public void tupleInWithNonExistentTuples_Real() {
    SqlServerTestHelper.run(
        c -> {
          var repos = createRealRepos();
          tupleInWithNonExistentTuples(repos, c);
        });
  }

  @Test
  public void tupleInWithNonExistentTuples_Mock() {
    var repos = createMockRepos();
    tupleInWithNonExistentTuples(repos, null);
  }

  public void tupleInWithNonExistentTuples(Repos repos, Connection c) {
    var p1 = insertProduct(repos, "Existing", new BigDecimal("100.00"), c);

    // Query with mix of existing and non-existing tuples
    var result =
        repos
            .productsRepo
            .select()
            .where(
                p ->
                    p.name()
                        .tupleWith(p.price())
                        .among(
                            List.of(
                                Tuple.of("Existing", new BigDecimal("100.00")),
                                Tuple.of("NonExistent", new BigDecimal("999.99")),
                                Tuple.of("AlsoMissing", new BigDecimal("888.88")))))
            .toList(c);

    assertEquals(1, result.size());
    assertEquals("Existing", result.get(0).name());
  }

  @Test
  public void tupleInWithLargeList_Real() {
    SqlServerTestHelper.run(
        c -> {
          var repos = createRealRepos();
          tupleInWithLargeList(repos, c);
        });
  }

  @Test
  public void tupleInWithLargeList_Mock() {
    var repos = createMockRepos();
    tupleInWithLargeList(repos, null);
  }

  public void tupleInWithLargeList(Repos repos, Connection c) {
    // Insert 10 products
    var products = new java.util.ArrayList<ProductsRow>();
    for (int i = 1; i <= 10; i++) {
      products.add(insertProduct(repos, "Product" + i, new BigDecimal(i * 10 + ".00"), c));
    }

    // Select half using tuple IN (even numbered prices)
    var tuplesToSelect =
        products.stream()
            .filter(p -> p.price().intValue() % 20 == 0)
            .map(p -> Tuple.of(p.name(), p.price()))
            .toList();

    var result =
        repos
            .productsRepo
            .select()
            .where(p -> p.name().tupleWith(p.price()).among(tuplesToSelect))
            .toList(c);

    assertEquals(5, result.size());
  }

  // =============== Single Column IN Tests (for comparison) ===============

  @Test
  public void singleColumnIn_Real() {
    SqlServerTestHelper.run(
        c -> {
          var repos = createRealRepos();
          singleColumnIn(repos, c);
        });
  }

  @Test
  public void singleColumnIn_Mock() {
    var repos = createMockRepos();
    singleColumnIn(repos, null);
  }

  public void singleColumnIn(Repos repos, Connection c) {
    var p1 = insertProduct(repos, "Apple", new BigDecimal("1.00"), c);
    var p2 = insertProduct(repos, "Banana", new BigDecimal("2.00"), c);
    var p3 = insertProduct(repos, "Cherry", new BigDecimal("3.00"), c);

    // Single column IN using productId().among()
    var result =
        repos
            .productsRepo
            .select()
            .where(p -> p.productId().among(p1.productId(), p3.productId()))
            .toList(c);

    assertEquals(2, result.size());
    var names = result.stream().map(ProductsRow::name).collect(Collectors.toSet());
    assertEquals(Set.of("Apple", "Cherry"), names);
  }

  // ==================== Tuple IN Subquery Tests ====================

  @Test
  public void tupleInSubqueryBasic_Real() {
    SqlServerTestHelper.run(
        c -> {
          var repos = createRealRepos();
          tupleInSubqueryBasic(repos, c);
        });
  }

  // Note: Tuple IN subquery mock tests are skipped because mock evaluation of correlated
  // subqueries is complex. Real database tests verify the functionality works correctly.

  public void tupleInSubqueryBasic(Repos repos, Connection c) {
    // Create test data with unique prefix for isolation
    var prefix = "SUBQ_" + System.nanoTime() + "_";
    var p1 = insertProduct(repos, prefix + "Cheap1", new BigDecimal("10.00"), c);
    var p2 = insertProduct(repos, prefix + "Cheap2", new BigDecimal("20.00"), c);
    var p3 = insertProduct(repos, prefix + "Expensive", new BigDecimal("500.00"), c);

    // Use tuple IN subquery: find products where (name, price) is in subquery selecting cheap
    // products with this prefix
    var result =
        repos
            .productsRepo
            .select()
            .where(
                p ->
                    SqlExpr.all(
                        p.name()
                            .tupleWith(p.price())
                            .among(
                                repos
                                    .productsRepo
                                    .select()
                                    .where(
                                        inner -> inner.price().lessThan(new BigDecimal("100.00")))
                                    .map(inner -> inner.name().tupleWith(inner.price()))
                                    .subquery()),
                        p.name().like(prefix + "%", Bijection.identity())))
            .toList(c);

    assertEquals(2, result.size());
    var names = result.stream().map(ProductsRow::name).collect(Collectors.toSet());
    assertEquals(Set.of(prefix + "Cheap1", prefix + "Cheap2"), names);
  }

  @Test
  public void tupleInSubqueryWithNoMatches_Real() {
    SqlServerTestHelper.run(
        c -> {
          var repos = createRealRepos();
          tupleInSubqueryWithNoMatches(repos, c);
        });
  }

  public void tupleInSubqueryWithNoMatches(Repos repos, Connection c) {
    // Create products all above threshold
    insertProduct(repos, "Prod1", new BigDecimal("100.00"), c);
    insertProduct(repos, "Prod2", new BigDecimal("200.00"), c);

    // Subquery looks for price < 0 (none exist)
    var result =
        repos
            .productsRepo
            .select()
            .where(
                p ->
                    p.name()
                        .tupleWith(p.price())
                        .among(
                            repos
                                .productsRepo
                                .select()
                                .where(inner -> inner.price().lessThan(BigDecimal.ZERO))
                                .map(inner -> inner.name().tupleWith(inner.price()))
                                .subquery()))
            .toList(c);

    assertEquals(0, result.size());
  }

  @Test
  public void tupleInSubqueryCombinedWithOtherConditions_Real() {
    SqlServerTestHelper.run(
        c -> {
          var repos = createRealRepos();
          tupleInSubqueryCombinedWithOtherConditions(repos, c);
        });
  }

  public void tupleInSubqueryCombinedWithOtherConditions(Repos repos, Connection c) {
    // Create test data with unique prefix for isolation
    var prefix = "COMB_" + System.nanoTime() + "_";
    var p1 =
        insertProduct(repos, prefix + "ItemA", new BigDecimal("50.00"), Optional.of("Has desc"), c);
    var p2 = insertProduct(repos, prefix + "ItemB", new BigDecimal("60.00"), Optional.empty(), c);
    var p3 =
        insertProduct(repos, prefix + "ItemC", new BigDecimal("70.00"), Optional.of("Also has"), c);

    // Combine tuple IN subquery with description condition and prefix filter
    var result =
        repos
            .productsRepo
            .select()
            .where(
                p ->
                    SqlExpr.all(
                        p.name()
                            .tupleWith(p.price())
                            .among(
                                repos
                                    .productsRepo
                                    .select()
                                    .where(
                                        inner -> inner.price().lessThan(new BigDecimal("100.00")))
                                    .map(inner -> inner.name().tupleWith(inner.price()))
                                    .subquery()),
                        p.description().isNotNull(),
                        p.name().like(prefix + "%", Bijection.identity())))
            .toList(c);

    assertEquals(2, result.size());
    var names = result.stream().map(ProductsRow::name).collect(Collectors.toSet());
    assertEquals(Set.of(prefix + "ItemA", prefix + "ItemC"), names);
  }

  // ==================== Nullable Column Tuple IN Tests ====================

  /**
   * Tests tuple IN with a nullable column (description). Verifies tuple IN works correctly when
   * matching rows with null description. Note: Mock test is skipped because mock evaluation of
   * nullable tuples requires special handling.
   */
  @Ignore("Nullable values in tuple IN not supported")
  @Test
  public void tupleInWithNullableColumn_Real() {
    SqlServerTestHelper.run(
        c -> {
          var repos = createRealRepos();

          // Create products - some with description, some without
          var p1 = insertProduct(repos, "NullDesc1", new BigDecimal("100.00"), Optional.empty(), c);
          var p2 = insertProduct(repos, "NullDesc2", new BigDecimal("200.00"), Optional.empty(), c);
          var p3 =
              insertProduct(
                  repos, "HasDesc", new BigDecimal("300.00"), Optional.of("Has description"), c);

          // Query using tuple with nullable column - match rows with null description
          var result =
              repos
                  .productsRepo
                  .select()
                  .where(
                      p ->
                          p.name()
                              .tupleWith(p.description())
                              .in(
                                  List.of(
                                      Tuple.of("NullDesc1", (String) null),
                                      Tuple.of("NullDesc2", (String) null))))
                  .toList(c);

          assertEquals("Should find 2 products with null description", 2, result.size());
          var names = result.stream().map(ProductsRow::name).collect(Collectors.toSet());
          assertEquals(Set.of("NullDesc1", "NullDesc2"), names);
        });
  }

  /**
   * Tests tuple IN with nullable columns where both null and non-null values are queried. Note:
   * Mock test is skipped because mock evaluation of nullable tuples requires special handling.
   */
  @Ignore("Nullable values in tuple IN not supported")
  @Test
  public void tupleInWithNullableColumnMixedMatching_Real() {
    SqlServerTestHelper.run(
        c -> {
          var repos = createRealRepos();

          // Create products
          var p1 =
              insertProduct(
                  repos, "MixProd1", new BigDecimal("100.00"), Optional.of("Has desc"), c);
          var p2 = insertProduct(repos, "MixProd2", new BigDecimal("200.00"), Optional.empty(), c);

          // Query for both - one with value, one with null
          var result =
              repos
                  .productsRepo
                  .select()
                  .where(
                      p ->
                          p.name()
                              .tupleWith(p.description())
                              .in(
                                  List.of(
                                      Tuple.of("MixProd1", "Has desc"),
                                      Tuple.of("MixProd2", (String) null))))
                  .toList(c);

          assertEquals("Should find both products", 2, result.size());

          // Verify query with wrong pattern doesn't match
          var resultNoMatch =
              repos
                  .productsRepo
                  .select()
                  .where(
                      p ->
                          p.name()
                              .tupleWith(p.description())
                              .in(
                                  List.of(
                                      Tuple.of(
                                          "MixProd1", (String) null)))) // Wrong - MixProd1 has desc
                  .toList(c);

          assertEquals("Should not match wrong pattern", 0, resultNoMatch.size());
        });
  }

  // ==================== Nested Tuple Tests ====================

  /**
   * Tests truly nested tuples - calling tupleWith twice to create Tuple2<Tuple2<A, B>, C>. This
   * stresses the SQL generation by creating nested parentheses.
   */
  @Ignore("Nested tuples not supported")
  @Test
  public void nestedTupleIn_Real() {
    SqlServerTestHelper.run(
        c -> {
          var repos = createRealRepos();

          // Create products for nested tuple test
          var p1 =
              insertProduct(repos, "NestProd1", new BigDecimal("100.00"), Optional.of("Desc1"), c);
          var p2 =
              insertProduct(repos, "NestProd2", new BigDecimal("200.00"), Optional.of("Desc2"), c);
          var p3 =
              insertProduct(repos, "NestProd3", new BigDecimal("300.00"), Optional.of("Desc3"), c);

          // Test truly nested tuple: ((name, price), description)
          var result =
              repos
                  .productsRepo
                  .select()
                  .where(
                      p ->
                          p.name()
                              .tupleWith(p.price()) // Tuple2<String, BigDecimal>
                              .tupleWith(
                                  p.description()) // Tuple2<Tuple2<String, BigDecimal>, String>
                              .in(
                                  List.of(
                                      Tuple.of(
                                          Tuple.of("NestProd1", new BigDecimal("100.00")), "Desc1"),
                                      Tuple.of(
                                          Tuple.of("NestProd3", new BigDecimal("300.00")),
                                          "Desc3"))))
                  .toList(c);

          assertEquals("Should find 2 products matching nested tuple pattern", 2, result.size());
          var names = result.stream().map(ProductsRow::name).collect(Collectors.toSet());
          assertEquals(Set.of("NestProd1", "NestProd3"), names);

          // Test that non-matching nested tuple returns empty
          var resultNoMatch =
              repos
                  .productsRepo
                  .select()
                  .where(
                      p ->
                          p.name()
                              .tupleWith(p.price())
                              .tupleWith(p.description())
                              .in(
                                  List.of(
                                      // Wrong: description doesn't match
                                      Tuple.of(
                                          Tuple.of("NestProd1", new BigDecimal("100.00")),
                                          "Wrong Desc"))))
                  .toList(c);

          assertEquals("Should not match misaligned nested tuple", 0, resultNoMatch.size());
        });
  }

  @Ignore("Nested tuples not supported")
  @Test
  public void nestedTupleIn_Mock() {
    var repos = createMockRepos();

    // Create products
    var p1 =
        insertProduct(repos, "NestProd1", new BigDecimal("100.00"), Optional.of("Desc1"), null);
    var p2 =
        insertProduct(repos, "NestProd2", new BigDecimal("200.00"), Optional.of("Desc2"), null);
    var p3 =
        insertProduct(repos, "NestProd3", new BigDecimal("300.00"), Optional.of("Desc3"), null);

    // Test truly nested tuple in mock
    var result =
        repos
            .productsRepo
            .select()
            .where(
                p ->
                    p.name()
                        .tupleWith(p.price())
                        .tupleWith(p.description())
                        .in(
                            List.of(
                                Tuple.of(Tuple.of("NestProd1", new BigDecimal("100.00")), "Desc1"),
                                Tuple.of(
                                    Tuple.of("NestProd3", new BigDecimal("300.00")), "Desc3"))))
            .toList(null);

    assertEquals("Should find 2 products matching nested tuple pattern", 2, result.size());
  }

  // ==================== Read Nested Tuple from Database Tests ====================

  /**
   * Tests reading nested tuples from the database through the DSL. This test selects a nested tuple
   * expression using .map() and reads the results back.
   */
  @Ignore("Nested tuples not supported")
  @Test
  public void readNestedTupleFromDatabase_Real() {
    SqlServerTestHelper.run(
        c -> {
          var repos = createRealRepos();
          readNestedTupleFromDatabase(repos, c);
        });
  }

  void readNestedTupleFromDatabase(Repos repos, Connection c) {
    // Insert test data
    var p1 = insertProduct(repos, "ReadProd1", new BigDecimal("100.00"), Optional.of("Desc1"), c);
    var p2 = insertProduct(repos, "ReadProd2", new BigDecimal("200.00"), Optional.of("Desc2"), c);
    var p3 = insertProduct(repos, "ReadProd3", new BigDecimal("300.00"), Optional.of("Desc3"), c);

    // Select nested tuple: ((name, price), productId)
    var result =
        repos
            .productsRepo
            .select()
            .where(p -> p.name().among("ReadProd1", "ReadProd2", "ReadProd3"))
            .orderBy(p -> p.price().asc())
            .map(p -> p.name().tupleWith(p.price()).tupleWith(p.productId()))
            .toList(c);

    assertEquals("Should read 3 nested tuples", 3, result.size());

    // Verify the nested tuple structure
    var first = result.get(0);
    assertEquals("First tuple's inner first element", "ReadProd1", first._1()._1());
    assertEquals("First tuple's inner second element", new BigDecimal("100.00"), first._1()._2());
    assertEquals("First tuple's outer second element", p1.productId(), first._2());
  }

  // ==================== Helper Methods ====================

  private ProductsRow insertProduct(Repos repos, String name, BigDecimal price, Connection c) {
    return insertProduct(repos, name, price, Optional.empty(), c);
  }

  private ProductsRow insertProduct(
      Repos repos, String name, BigDecimal price, Optional<String> description, Connection c) {
    var unsaved = new ProductsRowUnsaved(name, price, description);
    return repos.productsRepo.insert(unsaved, c);
  }

  private Repos createRealRepos() {
    return new Repos(new ProductsRepoImpl());
  }

  private Repos createMockRepos() {
    var idCounter = new AtomicInteger(1);
    return new Repos(
        new ProductsRepoMock(
            unsaved -> unsaved.toRow(() -> new ProductsId(idCounter.getAndIncrement()))));
  }
}

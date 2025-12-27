package testdb;

import static org.junit.Assert.*;

import dev.typr.foundations.dsl.Bijection;
import dev.typr.foundations.dsl.SqlExpr;
import dev.typr.foundations.dsl.SqlExpr.In;
import dev.typr.foundations.dsl.SqlExpr.Rows;
import dev.typr.foundations.dsl.Tuples;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.Test;
import testdb.products.*;

/**
 * Comprehensive tests for arbitrary tuple IN functionality on SQL Server. Since SQL Server test
 * schema doesn't have composite key tables, we test: - Arbitrary tuple expressions using
 * Tuples.of() - IN with Rows.ofTuples for inline tuple values - Combined tuple conditions with
 * SqlExpr.all - Both real database and mock repository evaluation
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
                    new In<>(
                        Tuples.of(p.name(), p.price()),
                        Rows.ofTuples(
                            Tuples.of(p.name(), p.price()),
                            List.of(
                                Tuples.Tuple2.of("Widget", new BigDecimal("19.99")),
                                Tuples.Tuple2.of("Gadget", new BigDecimal("29.99"))))))
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
                    new In<>(
                        Tuples.of(p.name(), p.price()),
                        Rows.ofTuples(
                            Tuples.of(p.name(), p.price()),
                            List.of(Tuples.Tuple2.of("SingleItem", new BigDecimal("99.99"))))))
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
            .where(
                p ->
                    new In<>(
                        Tuples.of(p.name(), p.price()),
                        Rows.ofTuples(Tuples.of(p.name(), p.price()), List.of())))
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
                        new In<>(
                            Tuples.of(p.name(), p.price()),
                            Rows.ofTuples(
                                Tuples.of(p.name(), p.price()),
                                List.of(
                                    Tuples.Tuple2.of("Alpha", new BigDecimal("10.00")),
                                    Tuples.Tuple2.of("Beta", new BigDecimal("20.00")),
                                    Tuples.Tuple2.of("Gamma", new BigDecimal("10.00"))))),
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
                    new In<>(
                        Tuples.of(p.name(), p.price()),
                        Rows.ofTuples(
                            Tuples.of(p.name(), p.price()),
                            List.of(
                                Tuples.Tuple2.of("Existing", new BigDecimal("100.00")),
                                Tuples.Tuple2.of("NonExistent", new BigDecimal("999.99")),
                                Tuples.Tuple2.of("AlsoMissing", new BigDecimal("888.88"))))))
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
            .map(p -> Tuples.Tuple2.of(p.name(), p.price()))
            .toList();

    var result =
        repos
            .productsRepo
            .select()
            .where(
                p ->
                    new In<>(
                        Tuples.of(p.name(), p.price()),
                        Rows.ofTuples(Tuples.of(p.name(), p.price()), tuplesToSelect)))
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

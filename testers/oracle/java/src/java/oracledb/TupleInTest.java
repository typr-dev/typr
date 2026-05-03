package oracledb;

import static org.junit.Assert.*;

import dev.typr.dsl.MockConnection;
import dev.typr.dsl.SqlExpr;
import dev.typr.foundations.Connection;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import oracledb.departments.*;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Comprehensive tests for tuple IN functionality on Oracle. Tests cover: - Composite ID IN with
 * 2-column String,String keys - Tuple IN with subqueries using tupleWith() - Combined with other
 * conditions using SqlExpr.all - Both real database and mock repository evaluation
 */
public class TupleInTest {

  /** Container for all repositories needed by tests */
  public record Repos(DepartmentsRepo departmentsRepo) {}

  // =============== Departments (2-column String,String composite key) ===============

  @Test
  public void departmentsCompositeIdInWithMultipleIds_Real() {
    OracleTestHelper.run(
        c -> {
          var repos = createRealRepos();
          departmentsCompositeIdInWithMultipleIds(repos, c);
        });
  }

  @Test
  public void departmentsCompositeIdInWithMultipleIds_Mock() {
    var repos = createMockRepos();
    departmentsCompositeIdInWithMultipleIds(repos, null);
  }

  public void departmentsCompositeIdInWithMultipleIds(Repos repos, Connection c) {
    // Insert test departments (insert returns DepartmentsId, store rows for verification)
    var row1 =
        new DepartmentsRow(
            "ENG",
            "US",
            "Engineering US",
            Optional.of(new MoneyT(new BigDecimal("1000000"), "USD")));
    var row2 =
        new DepartmentsRow(
            "ENG",
            "EU",
            "Engineering EU",
            Optional.of(new MoneyT(new BigDecimal("800000"), "EUR")));
    var row3 =
        new DepartmentsRow(
            "HR",
            "US",
            "Human Resources US",
            Optional.of(new MoneyT(new BigDecimal("500000"), "USD")));
    var row4 =
        new DepartmentsRow(
            "HR",
            "EU",
            "Human Resources EU",
            Optional.of(new MoneyT(new BigDecimal("400000"), "EUR")));

    repos.departmentsRepo.insert(row1, c);
    repos.departmentsRepo.insert(row2, c);
    repos.departmentsRepo.insert(row3, c);
    repos.departmentsRepo.insert(row4, c);

    // Query using compositeIdIn with 2 IDs
    var result =
        repos
            .departmentsRepo
            .select()
            .where(d -> d.compositeIdIn(List.of(row1.compositeId(), row3.compositeId())))
            .toList(c);

    assertEquals(2, result.size());
    var resultIds = result.stream().map(DepartmentsRow::compositeId).collect(Collectors.toSet());
    assertEquals(Set.of(row1.compositeId(), row3.compositeId()), resultIds);
  }

  @Test
  public void departmentsCompositeIdInWithSingleId_Real() {
    OracleTestHelper.run(
        c -> {
          var repos = createRealRepos();
          departmentsCompositeIdInWithSingleId(repos, c);
        });
  }

  @Test
  public void departmentsCompositeIdInWithSingleId_Mock() {
    var repos = createMockRepos();
    departmentsCompositeIdInWithSingleId(repos, null);
  }

  public void departmentsCompositeIdInWithSingleId(Repos repos, Connection c) {
    var row1 = new DepartmentsRow("SALES", "APAC", "Sales APAC", Optional.empty());
    var row2 = new DepartmentsRow("SALES", "EMEA", "Sales EMEA", Optional.empty());

    repos.departmentsRepo.insert(row1, c);
    repos.departmentsRepo.insert(row2, c);

    // Query with single ID - should still work
    var result =
        repos
            .departmentsRepo
            .select()
            .where(d -> d.compositeIdIn(List.of(row1.compositeId())))
            .toList(c);

    assertEquals(1, result.size());
    assertEquals(row1, result.get(0));
  }

  @Test
  public void departmentsCompositeIdInWithEmptyList_Real() {
    OracleTestHelper.run(
        c -> {
          var repos = createRealRepos();
          departmentsCompositeIdInWithEmptyList(repos, c);
        });
  }

  @Test
  public void departmentsCompositeIdInWithEmptyList_Mock() {
    var repos = createMockRepos();
    departmentsCompositeIdInWithEmptyList(repos, null);
  }

  public void departmentsCompositeIdInWithEmptyList(Repos repos, Connection c) {
    var row = new DepartmentsRow("TEST", "REGION", "Test Dept", Optional.empty());
    repos.departmentsRepo.insert(row, c);

    // Query with empty list - should return no results
    var result = repos.departmentsRepo.select().where(d -> d.compositeIdIn(List.of())).toList(c);

    assertEquals(0, result.size());
  }

  @Test
  public void departmentsCompositeIdInCombinedWithOtherConditions_Real() {
    OracleTestHelper.run(
        c -> {
          var repos = createRealRepos();
          departmentsCompositeIdInCombinedWithOtherConditions(repos, c);
        });
  }

  @Test
  public void departmentsCompositeIdInCombinedWithOtherConditions_Mock() {
    var repos = createMockRepos();
    departmentsCompositeIdInCombinedWithOtherConditions(repos, null);
  }

  public void departmentsCompositeIdInCombinedWithOtherConditions(Repos repos, Connection c) {
    var row1 =
        new DepartmentsRow(
            "DEV",
            "US",
            "Development US",
            Optional.of(new MoneyT(new BigDecimal("2000000"), "USD")));
    var row2 =
        new DepartmentsRow(
            "DEV",
            "EU",
            "Development EU",
            Optional.of(new MoneyT(new BigDecimal("100000"), "EUR")));
    var row3 =
        new DepartmentsRow(
            "QA", "US", "QA US", Optional.of(new MoneyT(new BigDecimal("500000"), "USD")));

    repos.departmentsRepo.insert(row1, c);
    repos.departmentsRepo.insert(row2, c);
    repos.departmentsRepo.insert(row3, c);

    // Query with compositeIdIn AND deptName condition using SqlExpr.all
    var result =
        repos
            .departmentsRepo
            .select()
            .where(
                d ->
                    SqlExpr.all(
                        d.compositeIdIn(
                            List.of(row1.compositeId(), row2.compositeId(), row3.compositeId())),
                        d.deptName().isEqual("Development US")))
            .toList(c);

    assertEquals(1, result.size());
    assertEquals(row1.compositeId(), result.get(0).compositeId());
  }

  @Test
  public void departmentsCompositeIdInWithNonExistentIds_Real() {
    OracleTestHelper.run(
        c -> {
          var repos = createRealRepos();
          departmentsCompositeIdInWithNonExistentIds(repos, c);
        });
  }

  @Test
  public void departmentsCompositeIdInWithNonExistentIds_Mock() {
    var repos = createMockRepos();
    departmentsCompositeIdInWithNonExistentIds(repos, null);
  }

  public void departmentsCompositeIdInWithNonExistentIds(Repos repos, Connection c) {
    var row1 = new DepartmentsRow("EXISTING", "DEPT", "Existing Dept", Optional.empty());
    repos.departmentsRepo.insert(row1, c);

    // Query with mix of existing and non-existing IDs
    var result =
        repos
            .departmentsRepo
            .select()
            .where(
                d ->
                    d.compositeIdIn(
                        List.of(
                            row1.compositeId(),
                            new DepartmentsId("NONEXISTENT", "DEPT"),
                            new DepartmentsId("ALSO", "MISSING"))))
            .toList(c);

    assertEquals(1, result.size());
    assertEquals(row1, result.get(0));
  }

  @Test
  public void departmentsCompositeIdComputedVsManual_Real() {
    OracleTestHelper.run(
        c -> {
          var repos = createRealRepos();
          departmentsCompositeIdComputedVsManual(repos, c);
        });
  }

  @Test
  public void departmentsCompositeIdComputedVsManual_Mock() {
    var repos = createMockRepos();
    departmentsCompositeIdComputedVsManual(repos, null);
  }

  public void departmentsCompositeIdComputedVsManual(Repos repos, Connection c) {
    var row = new DepartmentsRow("COMPUTED", "TEST", "Computed Test", Optional.empty());
    repos.departmentsRepo.insert(row, c);

    // Get computed composite ID from row
    var computedId = row.compositeId();

    // Create manual composite ID with same values
    var manualId = new DepartmentsId("COMPUTED", "TEST");

    // Verify they're equal
    assertEquals(computedId, manualId);

    // Query using both computed and manual IDs
    var result =
        repos
            .departmentsRepo
            .select()
            .where(d -> d.compositeIdIn(List.of(computedId, manualId)))
            .toList(c);

    assertEquals(1, result.size());
    assertEquals(row, result.get(0));
  }

  // ==================== TupleInSubquery Tests ====================

  @Test
  public void tupleInSubqueryBasic_Real() {
    OracleTestHelper.run(
        c -> {
          var repos = createRealRepos();
          tupleInSubqueryBasic(repos, c);
        });
  }

  // Note: TupleInSubquery mock tests are skipped because mock evaluation of correlated
  // subqueries is complex. Real database tests verify the functionality works correctly.

  public void tupleInSubqueryBasic(Repos repos, Connection c) {
    // Create test departments with different regions
    var row1 =
        new DepartmentsRow(
            "SMALL1",
            "MATCH",
            "Small Dept 1",
            Optional.of(new MoneyT(new BigDecimal("10000"), "USD")));
    var row2 =
        new DepartmentsRow(
            "SMALL2",
            "MATCH",
            "Small Dept 2",
            Optional.of(new MoneyT(new BigDecimal("20000"), "USD")));
    var row3 =
        new DepartmentsRow(
            "LARGE",
            "OTHER",
            "Large Dept",
            Optional.of(new MoneyT(new BigDecimal("1000000"), "USD")));

    repos.departmentsRepo.insert(row1, c);
    repos.departmentsRepo.insert(row2, c);
    repos.departmentsRepo.insert(row3, c);

    // Use tuple IN subquery: find departments where (code, region) is in subquery
    // The subquery selects departments in "MATCH" region
    var result =
        repos
            .departmentsRepo
            .select()
            .where(
                d ->
                    d.deptCode()
                        .tupleWith(d.deptRegion())
                        .among(
                            repos
                                .departmentsRepo
                                .select()
                                .where(inner -> inner.deptRegion().isEqual("MATCH"))
                                .map(inner -> inner.deptCode().tupleWith(inner.deptRegion()))
                                .subquery()))
            .toList(c);

    assertEquals(2, result.size());
    var codes = result.stream().map(DepartmentsRow::deptCode).collect(Collectors.toSet());
    assertEquals(Set.of("SMALL1", "SMALL2"), codes);
  }

  @Test
  public void tupleInSubqueryWithNoMatches_Real() {
    OracleTestHelper.run(
        c -> {
          var repos = createRealRepos();
          tupleInSubqueryWithNoMatches(repos, c);
        });
  }

  public void tupleInSubqueryWithNoMatches(Repos repos, Connection c) {
    var row = new DepartmentsRow("TEST1", "REGION1", "Test Dept 1", Optional.empty());
    repos.departmentsRepo.insert(row, c);

    // Subquery looks for region "NONEXISTENT" (none exist)
    var result =
        repos
            .departmentsRepo
            .select()
            .where(
                d ->
                    d.deptCode()
                        .tupleWith(d.deptRegion())
                        .among(
                            repos
                                .departmentsRepo
                                .select()
                                .where(inner -> inner.deptRegion().isEqual("NONEXISTENT"))
                                .map(inner -> inner.deptCode().tupleWith(inner.deptRegion()))
                                .subquery()))
            .toList(c);

    assertEquals(0, result.size());
  }

  @Test
  public void tupleInSubqueryCombinedWithOtherConditions_Real() {
    OracleTestHelper.run(
        c -> {
          var repos = createRealRepos();
          tupleInSubqueryCombinedWithOtherConditions(repos, c);
        });
  }

  public void tupleInSubqueryCombinedWithOtherConditions(Repos repos, Connection c) {
    var row1 = new DepartmentsRow("A", "X", "Dept A", Optional.empty());
    var row2 = new DepartmentsRow("B", "X", "Dept B", Optional.empty());
    var row3 = new DepartmentsRow("C", "X", "Dept C", Optional.empty());

    repos.departmentsRepo.insert(row1, c);
    repos.departmentsRepo.insert(row2, c);
    repos.departmentsRepo.insert(row3, c);

    // Combine tuple IN subquery with name condition (all in region X)
    var result =
        repos
            .departmentsRepo
            .select()
            .where(
                d ->
                    SqlExpr.all(
                        d.deptCode()
                            .tupleWith(d.deptRegion())
                            .among(
                                repos
                                    .departmentsRepo
                                    .select()
                                    .where(inner -> inner.deptRegion().isEqual("X"))
                                    .map(inner -> inner.deptCode().tupleWith(inner.deptRegion()))
                                    .subquery()),
                        d.deptCode().isNotEqual("A")))
            .toList(c);

    assertEquals(2, result.size());
    var codes = result.stream().map(DepartmentsRow::deptCode).collect(Collectors.toSet());
    assertEquals(Set.of("B", "C"), codes);
  }

  // ==================== Nullable Column Tuple IN Tests ====================

  /**
   * Tests tuple IN with a nullable column (budget). Verifies tuple IN works correctly when matching
   * rows with null budget. Note: Mock test is skipped because mock evaluation of nullable tuples
   * requires special handling.
   */
  @Ignore("Oracle does not support nullable values in tuple IN")
  @Test
  public void tupleInWithNullableColumn_Real() {
    OracleTestHelper.run(
        c -> {
          var repos = createRealRepos();

          // Create departments - some with budget, some without
          var row1 = new DepartmentsRow("NULL1", "REG1", "Dept With No Budget 1", Optional.empty());
          var row2 = new DepartmentsRow("NULL2", "REG2", "Dept With No Budget 2", Optional.empty());
          var row3 =
              new DepartmentsRow(
                  "BUDGET",
                  "REG3",
                  "Dept With Budget",
                  Optional.of(new MoneyT(new BigDecimal("500000"), "USD")));

          repos.departmentsRepo.insert(row1, c);
          repos.departmentsRepo.insert(row2, c);
          repos.departmentsRepo.insert(row3, c);

          // Query using tuple with nullable column - match rows with null budget
          var result =
              repos
                  .departmentsRepo
                  .select()
                  .where(
                      d ->
                          d.deptCode()
                              .tupleWith(d.budget())
                              .in(
                                  List.of(
                                      dev.typr.foundations.Tuple.of("NULL1", (MoneyT) null),
                                      dev.typr.foundations.Tuple.of("NULL2", (MoneyT) null))))
                  .toList(c);

          assertEquals("Should find 2 departments with null budget", 2, result.size());
          var codes = result.stream().map(DepartmentsRow::deptCode).collect(Collectors.toSet());
          assertEquals(Set.of("NULL1", "NULL2"), codes);
        });
  }

  /**
   * Tests tuple IN with nullable columns where both null and non-null values are queried. Note:
   * Mock test is skipped because mock evaluation of nullable tuples requires special handling.
   */
  @Ignore("Oracle does not support nullable values in tuple IN")
  @Test
  public void tupleInWithNullableColumnMixedMatching_Real() {
    OracleTestHelper.run(
        c -> {
          var repos = createRealRepos();

          // Create departments
          var row1 =
              new DepartmentsRow(
                  "MIX1",
                  "REG1",
                  "Mixed 1",
                  Optional.of(new MoneyT(new BigDecimal("100000"), "USD")));
          var row2 = new DepartmentsRow("MIX2", "REG2", "Mixed 2", Optional.empty());

          repos.departmentsRepo.insert(row1, c);
          repos.departmentsRepo.insert(row2, c);

          // Query for both - one with value, one with null
          var result =
              repos
                  .departmentsRepo
                  .select()
                  .where(
                      d ->
                          d.deptCode()
                              .tupleWith(d.budget())
                              .in(
                                  List.of(
                                      dev.typr.foundations.Tuple.of(
                                          "MIX1", new MoneyT(new BigDecimal("100000"), "USD")),
                                      dev.typr.foundations.Tuple.of("MIX2", (MoneyT) null))))
                  .toList(c);

          assertEquals("Should find both departments", 2, result.size());

          // Verify query with wrong pattern doesn't match
          var resultNoMatch =
              repos
                  .departmentsRepo
                  .select()
                  .where(
                      d ->
                          d.deptCode()
                              .tupleWith(d.budget())
                              .in(
                                  List.of(
                                      dev.typr.foundations.Tuple.of(
                                          "MIX1", (MoneyT) null)))) // Wrong - MIX1 has budget
                  .toList(c);

          assertEquals("Should not match wrong pattern", 0, resultNoMatch.size());
        });
  }

  // ==================== Nested Tuple Tests ====================

  /**
   * Tests truly nested tuples - calling tupleWith twice to create Tuple2<Tuple2<A, B>, C>. This
   * stresses the SQL generation by creating nested parentheses.
   */
  @Ignore("Oracle does not support nested tuples in IN clause")
  @Test
  public void nestedTupleIn_Real() {
    OracleTestHelper.run(
        c -> {
          var repos = createRealRepos();

          // Create departments for nested tuple test
          var row1 = new DepartmentsRow("NEST1", "R1", "Nested Dept 1", Optional.empty());
          var row2 = new DepartmentsRow("NEST2", "R2", "Nested Dept 2", Optional.empty());
          var row3 = new DepartmentsRow("NEST3", "R3", "Nested Dept 3", Optional.empty());

          repos.departmentsRepo.insert(row1, c);
          repos.departmentsRepo.insert(row2, c);
          repos.departmentsRepo.insert(row3, c);

          // Test truly nested tuple: ((deptCode, deptRegion), deptName)
          var result =
              repos
                  .departmentsRepo
                  .select()
                  .where(
                      d ->
                          d.deptCode()
                              .tupleWith(d.deptRegion()) // Tuple2<String, String>
                              .tupleWith(d.deptName()) // Tuple2<Tuple2<String, String>, String>
                              .in(
                                  List.of(
                                      dev.typr.foundations.Tuple.of(
                                          dev.typr.foundations.Tuple.of("NEST1", "R1"),
                                          "Nested Dept 1"),
                                      dev.typr.foundations.Tuple.of(
                                          dev.typr.foundations.Tuple.of("NEST3", "R3"),
                                          "Nested Dept 3"))))
                  .toList(c);

          assertEquals("Should find 2 departments matching nested tuple pattern", 2, result.size());
          var codes = result.stream().map(DepartmentsRow::deptCode).collect(Collectors.toSet());
          assertEquals(Set.of("NEST1", "NEST3"), codes);

          // Test that non-matching nested tuple returns empty
          var resultNoMatch =
              repos
                  .departmentsRepo
                  .select()
                  .where(
                      d ->
                          d.deptCode()
                              .tupleWith(d.deptRegion())
                              .tupleWith(d.deptName())
                              .in(
                                  List.of(
                                      // Wrong: name doesn't match
                                      dev.typr.foundations.Tuple.of(
                                          dev.typr.foundations.Tuple.of("NEST1", "R1"),
                                          "Wrong Name"))))
                  .toList(c);

          assertEquals("Should not match misaligned nested tuple", 0, resultNoMatch.size());
        });
  }

  @Ignore("Oracle does not support nested tuples in IN clause")
  @Test
  public void nestedTupleIn_Mock() {
    var repos = createMockRepos();

    // Create departments
    var row1 = new DepartmentsRow("NEST1", "R1", "Nested Dept 1", Optional.empty());
    var row2 = new DepartmentsRow("NEST2", "R2", "Nested Dept 2", Optional.empty());
    var row3 = new DepartmentsRow("NEST3", "R3", "Nested Dept 3", Optional.empty());

    repos.departmentsRepo.insert(row1, null);
    repos.departmentsRepo.insert(row2, null);
    repos.departmentsRepo.insert(row3, null);

    // Test truly nested tuple in mock
    var result =
        repos
            .departmentsRepo
            .select()
            .where(
                d ->
                    d.deptCode()
                        .tupleWith(d.deptRegion())
                        .tupleWith(d.deptName())
                        .in(
                            List.of(
                                dev.typr.foundations.Tuple.of(
                                    dev.typr.foundations.Tuple.of("NEST1", "R1"), "Nested Dept 1"),
                                dev.typr.foundations.Tuple.of(
                                    dev.typr.foundations.Tuple.of("NEST3", "R3"),
                                    "Nested Dept 3"))))
            .toList(null);

    assertEquals("Should find 2 departments matching nested tuple pattern", 2, result.size());
  }

  // ==================== Read Nested Tuple from Database Tests ====================

  /**
   * Tests reading nested tuples from the database through the DSL. This test selects a nested tuple
   * expression using .map() and reads the results back.
   */
  @Ignore("Oracle does not support nested tuples")
  @Test
  public void readNestedTupleFromDatabase_Real() {
    OracleTestHelper.run(
        c -> {
          var repos = createRealRepos();
          readNestedTupleFromDatabase(repos, c);
        });
  }

  @Ignore("Oracle does not support nested tuples")
  @Test
  public void readNestedTupleFromDatabase_Mock() {
    var repos = createMockRepos();
    readNestedTupleFromDatabase(repos, MockConnection.instance);
  }

  void readNestedTupleFromDatabase(Repos repos, Connection c) {
    // Insert test data
    var row1 = new DepartmentsRow("READ1", "REG1", "Read Dept 1", Optional.empty());
    var row2 = new DepartmentsRow("READ2", "REG2", "Read Dept 2", Optional.empty());
    var row3 = new DepartmentsRow("READ3", "REG3", "Read Dept 3", Optional.empty());

    repos.departmentsRepo.insert(row1, c);
    repos.departmentsRepo.insert(row2, c);
    repos.departmentsRepo.insert(row3, c);

    // Select nested tuple: ((deptCode, deptRegion), deptName)
    var result =
        repos
            .departmentsRepo
            .select()
            .where(d -> d.deptCode().in("READ1", "READ2", "READ3"))
            .orderBy(d -> d.deptCode().asc())
            .map(d -> d.deptCode().tupleWith(d.deptRegion()).tupleWith(d.deptName()))
            .toList(c);

    assertEquals("Should read 3 nested tuples", 3, result.size());

    // Verify the nested tuple structure
    var first = result.get(0);
    assertEquals("First tuple's inner first element", "READ1", first._1()._1());
    assertEquals("First tuple's inner second element", "REG1", first._1()._2());
    assertEquals("First tuple's outer second element", "Read Dept 1", first._2());
  }

  // ==================== Helper Methods ====================

  private Repos createRealRepos() {
    return new Repos(new DepartmentsRepoImpl());
  }

  private Repos createMockRepos() {
    return new Repos(new DepartmentsRepoMock());
  }
}

package testdb;

import static org.junit.Assert.*;

import dev.typr.foundations.dsl.SqlExpr;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import testdb.departments.*;

/**
 * Comprehensive tests for tuple IN functionality on DuckDB. Tests cover: - Composite ID IN with
 * 2-column String,String keys - Tuple IN with subqueries using tupleWith() - Combined with other
 * conditions using SqlExpr.all - Both real database and mock repository evaluation
 */
public class TupleInTest {

  /** Container for all repositories needed by tests */
  public record Repos(DepartmentsRepo departmentsRepo) {}

  // =============== Departments (2-column String,String composite key) ===============

  @Test
  public void departmentsCompositeIdInWithMultipleIds_Real() {
    DuckDbTestHelper.run(
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
    // Insert test departments
    var dept1 =
        repos.departmentsRepo.insert(
            new DepartmentsRow(
                "ENG", "US", "Engineering US", Optional.of(new BigDecimal("1000000"))),
            c);
    var dept2 =
        repos.departmentsRepo.insert(
            new DepartmentsRow(
                "ENG", "EU", "Engineering EU", Optional.of(new BigDecimal("800000"))),
            c);
    var dept3 =
        repos.departmentsRepo.insert(
            new DepartmentsRow(
                "HR", "US", "Human Resources US", Optional.of(new BigDecimal("500000"))),
            c);
    var dept4 =
        repos.departmentsRepo.insert(
            new DepartmentsRow(
                "HR", "EU", "Human Resources EU", Optional.of(new BigDecimal("400000"))),
            c);

    // Query using compositeIdIn with 2 IDs
    var result =
        repos
            .departmentsRepo
            .select()
            .where(d -> d.compositeIdIn(List.of(dept1.compositeId(), dept3.compositeId())))
            .toList(c);

    assertEquals(2, result.size());
    var resultIds = result.stream().map(DepartmentsRow::compositeId).collect(Collectors.toSet());
    assertEquals(Set.of(dept1.compositeId(), dept3.compositeId()), resultIds);
  }

  @Test
  public void departmentsCompositeIdInWithSingleId_Real() {
    DuckDbTestHelper.run(
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
    var dept1 =
        repos.departmentsRepo.insert(
            new DepartmentsRow("SALES", "APAC", "Sales APAC", Optional.empty()), c);
    var dept2 =
        repos.departmentsRepo.insert(
            new DepartmentsRow("SALES", "EMEA", "Sales EMEA", Optional.empty()), c);

    // Query with single ID - should still work
    var result =
        repos
            .departmentsRepo
            .select()
            .where(d -> d.compositeIdIn(List.of(dept1.compositeId())))
            .toList(c);

    assertEquals(1, result.size());
    assertEquals(dept1, result.get(0));
  }

  @Test
  public void departmentsCompositeIdInWithEmptyList_Real() {
    DuckDbTestHelper.run(
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
    repos.departmentsRepo.insert(
        new DepartmentsRow("TEST", "REGION", "Test Dept", Optional.empty()), c);

    // Query with empty list - should return no results
    var result = repos.departmentsRepo.select().where(d -> d.compositeIdIn(List.of())).toList(c);

    assertEquals(0, result.size());
  }

  @Test
  public void departmentsCompositeIdInCombinedWithOtherConditions_Real() {
    DuckDbTestHelper.run(
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
    var dept1 =
        repos.departmentsRepo.insert(
            new DepartmentsRow(
                "DEV", "US", "Development US", Optional.of(new BigDecimal("2000000"))),
            c);
    var dept2 =
        repos.departmentsRepo.insert(
            new DepartmentsRow(
                "DEV", "EU", "Development EU", Optional.of(new BigDecimal("100000"))),
            c);
    var dept3 =
        repos.departmentsRepo.insert(
            new DepartmentsRow("QA", "US", "QA US", Optional.of(new BigDecimal("500000"))), c);

    // Query with compositeIdIn AND budget condition using SqlExpr.all
    var result =
        repos
            .departmentsRepo
            .select()
            .where(
                d ->
                    SqlExpr.all(
                        d.compositeIdIn(
                            List.of(dept1.compositeId(), dept2.compositeId(), dept3.compositeId())),
                        d.budget().greaterThan(new BigDecimal("200000"))))
            .toList(c);

    assertEquals(2, result.size());
    var resultIds = result.stream().map(DepartmentsRow::compositeId).collect(Collectors.toSet());
    assertEquals(Set.of(dept1.compositeId(), dept3.compositeId()), resultIds);
  }

  @Test
  public void departmentsCompositeIdInWithNonExistentIds_Real() {
    DuckDbTestHelper.run(
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
    var dept1 =
        repos.departmentsRepo.insert(
            new DepartmentsRow("EXISTING", "DEPT", "Existing Dept", Optional.empty()), c);

    // Query with mix of existing and non-existing IDs
    var result =
        repos
            .departmentsRepo
            .select()
            .where(
                d ->
                    d.compositeIdIn(
                        List.of(
                            dept1.compositeId(),
                            new DepartmentsId("NONEXISTENT", "DEPT"),
                            new DepartmentsId("ALSO", "MISSING"))))
            .toList(c);

    assertEquals(1, result.size());
    assertEquals(dept1, result.get(0));
  }

  @Test
  public void departmentsCompositeIdComputedVsManual_Real() {
    DuckDbTestHelper.run(
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
    var dept =
        repos.departmentsRepo.insert(
            new DepartmentsRow("COMPUTED", "TEST", "Computed Test", Optional.empty()), c);

    // Get computed composite ID from row
    var computedId = dept.compositeId();

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
    assertEquals(dept, result.get(0));
  }

  // ==================== TupleInSubquery Tests ====================

  @Test
  public void tupleInSubqueryBasic_Real() {
    DuckDbTestHelper.run(
        c -> {
          var repos = createRealRepos();
          tupleInSubqueryBasic(repos, c);
        });
  }

  // Note: TupleInSubquery mock tests are skipped because mock evaluation of correlated
  // subqueries is complex. Real database tests verify the functionality works correctly.

  public void tupleInSubqueryBasic(Repos repos, Connection c) {
    // Create test departments with different budgets
    var dept1 =
        repos.departmentsRepo.insert(
            new DepartmentsRow(
                "SMALL1", "REG1", "Small Dept 1", Optional.of(new BigDecimal("10000"))),
            c);
    var dept2 =
        repos.departmentsRepo.insert(
            new DepartmentsRow(
                "SMALL2", "REG2", "Small Dept 2", Optional.of(new BigDecimal("20000"))),
            c);
    var dept3 =
        repos.departmentsRepo.insert(
            new DepartmentsRow(
                "LARGE", "REG3", "Large Dept", Optional.of(new BigDecimal("1000000"))),
            c);

    // Use tuple IN subquery: find departments where (code, region) is in subquery of small budget
    // depts
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
                                .where(inner -> inner.budget().lessThan(new BigDecimal("100000")))
                                .map(inner -> inner.deptCode().tupleWith(inner.deptRegion()))
                                .subquery()))
            .toList(c);

    assertEquals(2, result.size());
    var codes = result.stream().map(DepartmentsRow::deptCode).collect(Collectors.toSet());
    assertEquals(Set.of("SMALL1", "SMALL2"), codes);
  }

  @Test
  public void tupleInSubqueryWithNoMatches_Real() {
    DuckDbTestHelper.run(
        c -> {
          var repos = createRealRepos();
          tupleInSubqueryWithNoMatches(repos, c);
        });
  }

  public void tupleInSubqueryWithNoMatches(Repos repos, Connection c) {
    // Create departments all with budgets
    repos.departmentsRepo.insert(
        new DepartmentsRow("TEST1", "REGION1", "Test Dept 1", Optional.of(new BigDecimal("50000"))),
        c);
    repos.departmentsRepo.insert(
        new DepartmentsRow("TEST2", "REGION2", "Test Dept 2", Optional.of(new BigDecimal("60000"))),
        c);

    // Subquery looks for budget < 0 (none exist)
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
                                .where(inner -> inner.budget().lessThan(BigDecimal.ZERO))
                                .map(inner -> inner.deptCode().tupleWith(inner.deptRegion()))
                                .subquery()))
            .toList(c);

    assertEquals(0, result.size());
  }

  @Test
  public void tupleInSubqueryCombinedWithOtherConditions_Real() {
    DuckDbTestHelper.run(
        c -> {
          var repos = createRealRepos();
          tupleInSubqueryCombinedWithOtherConditions(repos, c);
        });
  }

  public void tupleInSubqueryCombinedWithOtherConditions(Repos repos, Connection c) {
    var dept1 =
        repos.departmentsRepo.insert(
            new DepartmentsRow("A", "X", "Dept A", Optional.of(new BigDecimal("30000"))), c);
    var dept2 =
        repos.departmentsRepo.insert(
            new DepartmentsRow("B", "X", "Dept B", Optional.of(new BigDecimal("40000"))), c);
    var dept3 =
        repos.departmentsRepo.insert(
            new DepartmentsRow("C", "X", "Dept C", Optional.of(new BigDecimal("50000"))), c);

    // Combine tuple IN subquery with name condition
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
                                    .where(
                                        inner -> inner.budget().lessThan(new BigDecimal("100000")))
                                    .map(inner -> inner.deptCode().tupleWith(inner.deptRegion()))
                                    .subquery()),
                        d.deptCode().isNotEqual("A")))
            .toList(c);

    assertEquals(2, result.size());
    var codes = result.stream().map(DepartmentsRow::deptCode).collect(Collectors.toSet());
    assertEquals(Set.of("B", "C"), codes);
  }

  // ==================== Helper Methods ====================

  private Repos createRealRepos() {
    return new Repos(new DepartmentsRepoImpl());
  }

  private Repos createMockRepos() {
    return new Repos(new DepartmentsRepoMock());
  }
}

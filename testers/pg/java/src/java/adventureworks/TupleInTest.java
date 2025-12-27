package adventureworks;

import static org.junit.Assert.*;

import adventureworks.public_.ShortText;
import adventureworks.public_.flaff.*;
import adventureworks.public_.only_pk_columns.*;
import dev.typr.foundations.dsl.SqlExpr;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * Comprehensive tests for the new tuple IN functionality using In/Rows/Tuples pattern.
 *
 * <p>Tests cover: - Composite ID IN with various key sizes (2, 4 columns) - Computed vs manually
 * created composite IDs - Combined with other conditions - Empty list edge case - Large list - Both
 * real database and mock repository evaluation
 */
public class TupleInTest {

  /** Container for all repositories needed by tests */
  public record Repos(OnlyPkColumnsRepo onlyPkColumnsRepo, FlaffRepo flaffRepo) {}

  // ==================== Composite ID tests (2-column) ====================

  @Test
  public void compositeIdInWithMultipleIds_Real() {
    WithConnection.run(
        c -> {
          var repos = createRealRepos();
          compositeIdInWithMultipleIds(repos, c);
        });
  }

  @Test
  public void compositeIdInWithMultipleIds_Mock() {
    var repos = createMockRepos();
    compositeIdInWithMultipleIds(repos, null);
  }

  public void compositeIdInWithMultipleIds(Repos repos, Connection c) {
    // Create test data
    var row1 = repos.onlyPkColumnsRepo.insert(new OnlyPkColumnsRow("A", 1), c);
    var row2 = repos.onlyPkColumnsRepo.insert(new OnlyPkColumnsRow("B", 2), c);
    var row3 = repos.onlyPkColumnsRepo.insert(new OnlyPkColumnsRow("C", 3), c);
    var row4 = repos.onlyPkColumnsRepo.insert(new OnlyPkColumnsRow("D", 4), c);

    // Query using compositeIdIn with specific IDs
    var result =
        repos
            .onlyPkColumnsRepo
            .select()
            .where(r -> r.compositeIdIn(List.of(row1.compositeId(), row3.compositeId())))
            .toList(c);

    assertEquals(2, result.size());
    var resultIds = result.stream().map(OnlyPkColumnsRow::compositeId).collect(Collectors.toSet());
    assertEquals(Set.of(row1.compositeId(), row3.compositeId()), resultIds);
  }

  @Test
  public void compositeIdInWithSingleId_Real() {
    WithConnection.run(
        c -> {
          var repos = createRealRepos();
          compositeIdInWithSingleId(repos, c);
        });
  }

  @Test
  public void compositeIdInWithSingleId_Mock() {
    var repos = createMockRepos();
    compositeIdInWithSingleId(repos, null);
  }

  public void compositeIdInWithSingleId(Repos repos, Connection c) {
    var row1 = repos.onlyPkColumnsRepo.insert(new OnlyPkColumnsRow("SINGLE", 100), c);
    repos.onlyPkColumnsRepo.insert(new OnlyPkColumnsRow("OTHER", 200), c);

    var result =
        repos
            .onlyPkColumnsRepo
            .select()
            .where(r -> r.compositeIdIn(List.of(row1.compositeId())))
            .toList(c);

    assertEquals(1, result.size());
    assertEquals(row1, result.get(0));
  }

  @Test
  public void compositeIdInWithEmptyList_Real() {
    WithConnection.run(
        c -> {
          var repos = createRealRepos();
          compositeIdInWithEmptyList(repos, c);
        });
  }

  @Test
  public void compositeIdInWithEmptyList_Mock() {
    var repos = createMockRepos();
    compositeIdInWithEmptyList(repos, null);
  }

  public void compositeIdInWithEmptyList(Repos repos, Connection c) {
    repos.onlyPkColumnsRepo.insert(new OnlyPkColumnsRow("TEST", 999), c);

    // Empty list should return no results
    var result = repos.onlyPkColumnsRepo.select().where(r -> r.compositeIdIn(List.of())).toList(c);

    assertEquals(0, result.size());
  }

  @Test
  public void compositeIdInWithComputedVsManual_Real() {
    WithConnection.run(
        c -> {
          var repos = createRealRepos();
          compositeIdInWithComputedVsManual(repos, c);
        });
  }

  @Test
  public void compositeIdInWithComputedVsManual_Mock() {
    var repos = createMockRepos();
    compositeIdInWithComputedVsManual(repos, null);
  }

  public void compositeIdInWithComputedVsManual(Repos repos, Connection c) {
    var row1 = repos.onlyPkColumnsRepo.insert(new OnlyPkColumnsRow("A", 1), c);
    var row2 = repos.onlyPkColumnsRepo.insert(new OnlyPkColumnsRow("B", 2), c);
    var row3 = repos.onlyPkColumnsRepo.insert(new OnlyPkColumnsRow("A", 3), c);

    // Get computed composite ID from row
    var computedId = row1.compositeId();

    // Create manual composite ID with same values
    var manualId = new OnlyPkColumnsId("A", 1);

    // Verify they're equal
    assertEquals(computedId, manualId);

    // Query using both computed and manual IDs
    var result =
        repos
            .onlyPkColumnsRepo
            .select()
            .where(
                r ->
                    r.compositeIdIn(
                        List.of(
                            computedId, // computed from row
                            manualId, // manually created (same as computedId)
                            new OnlyPkColumnsId("B", 2), // manual
                            row3.compositeId() // computed from another row
                            )))
            .toList(c);

    // Should find 3 unique rows
    assertEquals(3, result.size());
  }

  @Test
  public void compositeIdInWithNonExistentIds_Real() {
    WithConnection.run(
        c -> {
          var repos = createRealRepos();
          compositeIdInWithNonExistentIds(repos, c);
        });
  }

  @Test
  public void compositeIdInWithNonExistentIds_Mock() {
    var repos = createMockRepos();
    compositeIdInWithNonExistentIds(repos, null);
  }

  public void compositeIdInWithNonExistentIds(Repos repos, Connection c) {
    var row1 = repos.onlyPkColumnsRepo.insert(new OnlyPkColumnsRow("EXISTING", 1), c);

    // Query with mix of existing and non-existing IDs
    var result =
        repos
            .onlyPkColumnsRepo
            .select()
            .where(
                r ->
                    r.compositeIdIn(
                        List.of(
                            row1.compositeId(),
                            new OnlyPkColumnsId("NONEXISTENT", 999),
                            new OnlyPkColumnsId("ALSO", 888))))
            .toList(c);

    assertEquals(1, result.size());
    assertEquals(row1, result.get(0));
  }

  @Test
  public void compositeIdInWithLargeList_Real() {
    WithConnection.run(
        c -> {
          var repos = createRealRepos();
          compositeIdInWithLargeList(repos, c);
        });
  }

  @Test
  public void compositeIdInWithLargeList_Mock() {
    var repos = createMockRepos();
    compositeIdInWithLargeList(repos, null);
  }

  public void compositeIdInWithLargeList(Repos repos, Connection c) {
    // Create 20 rows
    var insertedRows = new java.util.ArrayList<OnlyPkColumnsRow>();
    for (int i = 0; i < 20; i++) {
      insertedRows.add(repos.onlyPkColumnsRepo.insert(new OnlyPkColumnsRow("KEY" + i, i), c));
    }

    // Select half using compositeIdIn
    var idsToSelect =
        insertedRows.stream()
            .filter(row -> row.keyColumn2() % 2 == 0) // Only even
            .map(OnlyPkColumnsRow::compositeId)
            .toList();

    var result =
        repos.onlyPkColumnsRepo.select().where(r -> r.compositeIdIn(idsToSelect)).toList(c);

    assertEquals(10, result.size()); // 10 even numbers from 0-19
  }

  // ==================== 4-Column Composite ID (Flaff table) ====================

  @Test
  public void compositeIdInWith4ColumnKey_Real() {
    WithConnection.run(
        c -> {
          var repos = createRealRepos();
          compositeIdInWith4ColumnKey(repos, c);
        });
  }

  @Test
  public void compositeIdInWith4ColumnKey_Mock() {
    var repos = createMockRepos();
    compositeIdInWith4ColumnKey(repos, null);
  }

  public void compositeIdInWith4ColumnKey(Repos repos, Connection c) {
    // Create test data in flaff table (4-column composite key)
    // Note: parentspecifier is a self-referential FK, so we use Optional.empty() to avoid FK
    // constraint issues
    var row1 =
        repos.flaffRepo.insert(
            new FlaffRow(
                new ShortText("CODE1"), "OTHER1", 100, new ShortText("SPEC1"), Optional.empty()),
            c);
    var row2 =
        repos.flaffRepo.insert(
            new FlaffRow(
                new ShortText("CODE2"), "OTHER2", 200, new ShortText("SPEC2"), Optional.empty()),
            c);
    var row3 =
        repos.flaffRepo.insert(
            new FlaffRow(
                new ShortText("CODE1"), "OTHER1", 100, new ShortText("SPEC2"), Optional.empty()),
            c);
    var row4 =
        repos.flaffRepo.insert(
            new FlaffRow(
                new ShortText("CODE3"), "OTHER3", 300, new ShortText("SPEC3"), Optional.empty()),
            c);

    // Query using compositeIdIn with 4-tuple composite IDs
    var result =
        repos
            .flaffRepo
            .select()
            .where(
                f ->
                    f.compositeIdIn(
                        List.of(
                            row1.compositeId(),
                            new FlaffId(
                                new ShortText("CODE2"), "OTHER2", 200, new ShortText("SPEC2")),
                            row4.compositeId())))
            .toList(c);

    assertEquals(3, result.size());
    var specifiers = result.stream().map(r -> r.specifier().value()).collect(Collectors.toSet());
    assertEquals(Set.of("SPEC1", "SPEC2", "SPEC3"), specifiers);
  }

  @Test
  public void compositeIdInWith4ColumnKeyComputedVsManual_Real() {
    WithConnection.run(
        c -> {
          var repos = createRealRepos();
          compositeIdInWith4ColumnKeyComputedVsManual(repos, c);
        });
  }

  @Test
  public void compositeIdInWith4ColumnKeyComputedVsManual_Mock() {
    var repos = createMockRepos();
    compositeIdInWith4ColumnKeyComputedVsManual(repos, null);
  }

  public void compositeIdInWith4ColumnKeyComputedVsManual(Repos repos, Connection c) {
    var row =
        repos.flaffRepo.insert(
            new FlaffRow(
                new ShortText("COMP"), "MANUAL", 999, new ShortText("TEST"), Optional.empty()),
            c);

    // Get computed composite ID from row
    var computedId = row.compositeId();

    // Create manual composite ID with same values
    var manualId = new FlaffId(new ShortText("COMP"), "MANUAL", 999, new ShortText("TEST"));

    // Verify they're equal
    assertEquals(computedId, manualId);

    // Query using both
    var result =
        repos
            .flaffRepo
            .select()
            .where(f -> f.compositeIdIn(List.of(computedId, manualId)))
            .toList(c);

    assertEquals(1, result.size());
    assertEquals(row, result.get(0));
  }

  // ==================== Combined with other conditions ====================

  @Test
  public void compositeIdInCombinedWithOtherConditions_Real() {
    WithConnection.run(
        c -> {
          var repos = createRealRepos();
          compositeIdInCombinedWithOtherConditions(repos, c);
        });
  }

  @Test
  public void compositeIdInCombinedWithOtherConditions_Mock() {
    var repos = createMockRepos();
    compositeIdInCombinedWithOtherConditions(repos, null);
  }

  public void compositeIdInCombinedWithOtherConditions(Repos repos, Connection c) {
    // Create flaff rows with different specifiers
    var row1 =
        repos.flaffRepo.insert(
            new FlaffRow(new ShortText("A"), "X", 1, new ShortText("S1"), Optional.empty()), c);
    var row2 =
        repos.flaffRepo.insert(
            new FlaffRow(new ShortText("B"), "X", 2, new ShortText("S2"), Optional.empty()), c);
    var row3 =
        repos.flaffRepo.insert(
            new FlaffRow(new ShortText("C"), "X", 3, new ShortText("S3"), Optional.empty()), c);

    // Combine compositeIdIn with specifier filter using SqlExpr.all
    var result =
        repos
            .flaffRepo
            .select()
            .where(
                f ->
                    SqlExpr.all(
                        f.compositeIdIn(
                            List.of(row1.compositeId(), row2.compositeId(), row3.compositeId())),
                        f.specifier().isEqual(new ShortText("S2"))))
            .toList(c);

    assertEquals(1, result.size());
    assertEquals("S2", result.get(0).specifier().value());
  }

  // ==================== TupleInSubquery tests ====================

  @Test
  public void tupleInSubqueryBasic_Real() {
    WithConnection.run(
        c -> {
          var repos = createRealRepos();
          tupleInSubqueryBasic(repos, c);
        });
  }

  // Note: TupleInSubquery mock tests are skipped because mock evaluation of correlated
  // subqueries is complex. Real database tests verify the functionality works correctly.

  public void tupleInSubqueryBasic(Repos repos, Connection c) {
    // Create test data - some rows that will match and some that won't
    var row1 = repos.onlyPkColumnsRepo.insert(new OnlyPkColumnsRow("MATCH1", 1), c);
    var row2 = repos.onlyPkColumnsRepo.insert(new OnlyPkColumnsRow("MATCH2", 2), c);
    var row3 = repos.onlyPkColumnsRepo.insert(new OnlyPkColumnsRow("NOMATCH", 99), c);

    // Use tuple IN subquery: find rows where (key1, key2) is in the subquery result
    // The subquery selects rows where keyColumn2 < 10
    var result =
        repos
            .onlyPkColumnsRepo
            .select()
            .where(
                r ->
                    r.keyColumn1()
                        .tupleWith(r.keyColumn2())
                        .among(
                            repos
                                .onlyPkColumnsRepo
                                .select()
                                .where(inner -> inner.keyColumn2().lessThan(10))
                                .map(inner -> inner.keyColumn1().tupleWith(inner.keyColumn2()))
                                .subquery()))
            .toList(c);

    assertEquals(2, result.size());
    var resultKeys = result.stream().map(OnlyPkColumnsRow::keyColumn1).collect(Collectors.toSet());
    assertEquals(Set.of("MATCH1", "MATCH2"), resultKeys);
  }

  @Test
  public void tupleInSubqueryWithNoMatches_Real() {
    WithConnection.run(
        c -> {
          var repos = createRealRepos();
          tupleInSubqueryWithNoMatches(repos, c);
        });
  }

  public void tupleInSubqueryWithNoMatches(Repos repos, Connection c) {
    // Create data where subquery will return empty
    repos.onlyPkColumnsRepo.insert(new OnlyPkColumnsRow("TEST1", 100), c);
    repos.onlyPkColumnsRepo.insert(new OnlyPkColumnsRow("TEST2", 200), c);

    // Subquery looks for keyColumn2 < 0 (none exist)
    var result =
        repos
            .onlyPkColumnsRepo
            .select()
            .where(
                r ->
                    r.keyColumn1()
                        .tupleWith(r.keyColumn2())
                        .among(
                            repos
                                .onlyPkColumnsRepo
                                .select()
                                .where(inner -> inner.keyColumn2().lessThan(0))
                                .map(inner -> inner.keyColumn1().tupleWith(inner.keyColumn2()))
                                .subquery()))
            .toList(c);

    assertEquals(0, result.size());
  }

  @Test
  public void tupleInSubqueryCombinedWithOtherConditions_Real() {
    WithConnection.run(
        c -> {
          var repos = createRealRepos();
          tupleInSubqueryCombinedWithOtherConditions(repos, c);
        });
  }

  public void tupleInSubqueryCombinedWithOtherConditions(Repos repos, Connection c) {
    var row1 = repos.onlyPkColumnsRepo.insert(new OnlyPkColumnsRow("A", 1), c);
    var row2 = repos.onlyPkColumnsRepo.insert(new OnlyPkColumnsRow("B", 2), c);
    var row3 = repos.onlyPkColumnsRepo.insert(new OnlyPkColumnsRow("C", 3), c);

    // Combine tuple IN subquery with another condition
    var result =
        repos
            .onlyPkColumnsRepo
            .select()
            .where(
                r ->
                    SqlExpr.all(
                        r.keyColumn1()
                            .tupleWith(r.keyColumn2())
                            .among(
                                repos
                                    .onlyPkColumnsRepo
                                    .select()
                                    .where(inner -> inner.keyColumn2().lessThan(10))
                                    .map(inner -> inner.keyColumn1().tupleWith(inner.keyColumn2()))
                                    .subquery()),
                        r.keyColumn1().isNotEqual("A")))
            .toList(c);

    assertEquals(2, result.size());
    var resultKeys = result.stream().map(OnlyPkColumnsRow::keyColumn1).collect(Collectors.toSet());
    assertEquals(Set.of("B", "C"), resultKeys);
  }

  // ==================== Helper Methods ====================

  private Repos createRealRepos() {
    return new Repos(new OnlyPkColumnsRepoImpl(), new FlaffRepoImpl());
  }

  private Repos createMockRepos() {
    return new Repos(new OnlyPkColumnsRepoMock(), new FlaffRepoMock());
  }
}

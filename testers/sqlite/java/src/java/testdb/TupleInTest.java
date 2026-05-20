package testdb;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import testdb.departments.*;

/**
 * Tuple IN against composite IDs.
 *
 * <p>{@code Dialect.SQLITE.supportsTupleIn = true} (since SQLite 3.7.11) — this exercises that
 * claim by running {@code (a, b) IN ((1,2), (3,4))} via the generated {@code compositeIdIn} field
 * on departments (string×string composite PK).
 */
public class TupleInTest {

  private final DepartmentsRepoImpl repo = new DepartmentsRepoImpl();

  @Test
  public void compositeIdInWithMultipleIds() {
    SqliteTestHelper.run(
        c -> {
          var d1 = repo.insert(new DepartmentsRow("TI_A", "US", "A US", Optional.empty()), c);
          var d2 = repo.insert(new DepartmentsRow("TI_A", "EU", "A EU", Optional.empty()), c);
          var d3 = repo.insert(new DepartmentsRow("TI_B", "US", "B US", Optional.empty()), c);
          repo.insert(new DepartmentsRow("TI_B", "EU", "B EU", Optional.empty()), c);

          var result =
              repo.select()
                  .where(d -> d.compositeIdIn(List.of(d1.compositeId(), d3.compositeId())))
                  .toList(c);

          assertEquals(2, result.size());
          var ids = result.stream().map(DepartmentsRow::compositeId).collect(Collectors.toSet());
          assertEquals(Set.of(d1.compositeId(), d3.compositeId()), ids);
          assertFalse(ids.contains(d2.compositeId()));
        });
  }

  @Test
  public void compositeIdInWithSingleId() {
    SqliteTestHelper.run(
        c -> {
          var d1 = repo.insert(new DepartmentsRow("TI_S", "APAC", "S APAC", Optional.empty()), c);
          repo.insert(new DepartmentsRow("TI_S", "EMEA", "S EMEA", Optional.empty()), c);

          var result =
              repo.select().where(d -> d.compositeIdIn(List.of(d1.compositeId()))).toList(c);

          assertEquals(1, result.size());
          assertEquals(d1, result.get(0));
        });
  }

  @Test
  public void compositeIdInWithEmptyList() {
    SqliteTestHelper.run(
        c -> {
          var result = repo.select().where(d -> d.compositeIdIn(List.of())).toList(c);
          assertTrue(result.isEmpty());
        });
  }

  @Test
  public void compositeIdInWithNonExistentIds() {
    SqliteTestHelper.run(
        c -> {
          var result =
              repo.select()
                  .where(
                      d ->
                          d.compositeIdIn(
                              List.of(
                                  new DepartmentsId("NOPE", "NEVER"),
                                  new DepartmentsId("ALSO", "MISSING"))))
                  .toList(c);
          assertTrue(result.isEmpty());
        });
  }

  @Test
  public void compositeIdInCombinedWithOtherConditions() {
    SqliteTestHelper.run(
        c -> {
          var d1 =
              repo.insert(
                  new DepartmentsRow(
                      "TI_C", "US", "Combined US", Optional.of(new BigDecimal("100000"))),
                  c);
          var d2 =
              repo.insert(
                  new DepartmentsRow(
                      "TI_C", "EU", "Combined EU", Optional.of(new BigDecimal("50000"))),
                  c);

          var result =
              repo.select()
                  .where(d -> d.compositeIdIn(List.of(d1.compositeId(), d2.compositeId())))
                  .where(d -> d.budget().greaterThan(new BigDecimal("75000")))
                  .toList(c);

          assertEquals(1, result.size());
          assertEquals(d1, result.get(0));
        });
  }
}

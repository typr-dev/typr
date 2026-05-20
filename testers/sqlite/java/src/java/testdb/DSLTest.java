package testdb;

import static org.junit.Assert.*;

import dev.typr.foundations.Bijection;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.Test;
import testdb.customers.*;
import testdb.userdefined.Email;

/**
 * Exercises the generated type-safe DSL through {@code Dialect.SQLITE}: the rendered SQL passes
 * through the SQLite query planner without errors and produces the expected results.
 */
public class DSLTest {
  private final CustomersRepoImpl repo = new CustomersRepoImpl();

  private static CustomersRow row(long id, String name) {
    return new CustomersRow(
        new CustomersId(id), name, Optional.empty(), LocalDateTime.of(2025, 1, 1, 0, 0));
  }

  @Test
  public void testSelectWithWhere() {
    SqliteTestHelper.run(
        c -> {
          repo.insert(
              new CustomersRow(
                  new CustomersId(5001L),
                  "DSL Test User",
                  Optional.of(new Email("dsl@test.com")),
                  LocalDateTime.of(2025, 1, 1, 0, 0)),
              c);

          var results = repo.select().where(cu -> cu.name().isEqual("DSL Test User")).toList(c);
          assertEquals(1, results.size());
          assertEquals("DSL Test User", results.get(0).name());
        });
  }

  @Test
  public void testSelectWithOrderByAsc() {
    SqliteTestHelper.run(
        c -> {
          repo.insert(row(5002L, "Zebra"), c);
          repo.insert(row(5003L, "Alpha"), c);
          repo.insert(row(5004L, "Mike"), c);

          var results =
              repo.select()
                  .where(cu -> cu.customerId().greaterThan(new CustomersId(5001L)))
                  .orderBy(cu -> cu.name().asc())
                  .toList(c);

          assertEquals(3, results.size());
          assertEquals("Alpha", results.get(0).name());
          assertEquals("Mike", results.get(1).name());
          assertEquals("Zebra", results.get(2).name());
        });
  }

  @Test
  public void testSelectWithOrderByDesc() {
    SqliteTestHelper.run(
        c -> {
          repo.insert(row(5005L, "DescA"), c);
          repo.insert(row(5006L, "DescB"), c);
          repo.insert(row(5007L, "DescC"), c);

          var results =
              repo.select()
                  .where(cu -> cu.name().like("Desc%", Bijection.asString()))
                  .orderBy(cu -> cu.name().desc())
                  .toList(c);

          assertEquals(3, results.size());
          assertEquals("DescC", results.get(0).name());
          assertEquals("DescA", results.get(2).name());
        });
  }

  @Test
  public void testSelectWithLimit() {
    SqliteTestHelper.run(
        c -> {
          for (int i = 0; i < 10; i++) {
            repo.insert(row(5100L + i, "Limit" + i), c);
          }
          var results =
              repo.select()
                  .where(cu -> cu.name().like("Limit%", Bijection.asString()))
                  .limit(3)
                  .toList(c);
          assertEquals(3, results.size());
        });
  }

  @Test
  public void testSelectWithOffset() {
    SqliteTestHelper.run(
        c -> {
          repo.insert(row(5200L, "OffsetA"), c);
          repo.insert(row(5201L, "OffsetB"), c);
          repo.insert(row(5202L, "OffsetC"), c);
          repo.insert(row(5203L, "OffsetD"), c);

          var results =
              repo.select()
                  .where(cu -> cu.name().like("Offset%", Bijection.asString()))
                  .orderBy(cu -> cu.name().asc())
                  .offset(2)
                  .limit(10)
                  .toList(c);

          assertEquals(2, results.size());
          assertEquals("OffsetC", results.get(0).name());
          assertEquals("OffsetD", results.get(1).name());
        });
  }

  @Test
  public void testSelectWithCount() {
    SqliteTestHelper.run(
        c -> {
          repo.insert(row(5300L, "CountA"), c);
          repo.insert(row(5301L, "CountB"), c);
          repo.insert(row(5302L, "CountC"), c);

          var count =
              repo.select().where(cu -> cu.name().like("Count%", Bijection.asString())).count(c);

          assertEquals(3, count);
        });
  }

  @Test
  public void testSelectWithLike() {
    SqliteTestHelper.run(
        c -> {
          repo.insert(row(5400L, "LikeTest_ABC"), c);
          repo.insert(row(5401L, "LikeTest_XYZ"), c);
          repo.insert(row(5402L, "OtherName"), c);

          var results =
              repo.select()
                  .where(cu -> cu.name().like("LikeTest%", Bijection.asString()))
                  .toList(c);

          assertEquals(2, results.size());
        });
  }

  @Test
  public void testSelectWithIn() {
    SqliteTestHelper.run(
        c -> {
          repo.insert(row(5500L, "InTest1"), c);
          repo.insert(row(5501L, "InTest2"), c);
          repo.insert(row(5502L, "InTest3"), c);

          var results =
              repo.select()
                  .where(cu -> cu.customerId().in(new CustomersId(5500L), new CustomersId(5502L)))
                  .toList(c);

          assertEquals(2, results.size());
        });
  }

  @Test
  public void testUpdateBuilder() {
    SqliteTestHelper.run(
        c -> {
          repo.insert(row(5600L, "UpdateMe"), c);

          int updated =
              repo.update()
                  .where(cu -> cu.customerId().isEqual(new CustomersId(5600L)))
                  .setValue(CustomersFields::name, "Updated")
                  .execute(c);

          assertEquals(1, updated);

          var found = repo.selectById(new CustomersId(5600L), c).orElseThrow();
          assertEquals("Updated", found.name());
        });
  }

  @Test
  public void testDeleteBuilder() {
    SqliteTestHelper.run(
        c -> {
          repo.insert(row(5700L, "DeleteA"), c);
          repo.insert(row(5701L, "DeleteB"), c);
          repo.insert(row(5702L, "DeleteC"), c);

          int deleted =
              repo.delete().where(cu -> cu.name().like("Delete%", Bijection.asString())).execute(c);

          assertEquals(3, deleted);
        });
  }
}

package testdb;

import static org.junit.Assert.*;

import dev.typr.foundations.Bijection;
import dev.typr.foundations.data.maria.Inet4;
import dev.typr.foundations.data.maria.Inet6;
import java.math.BigDecimal;
import java.time.Year;
import java.util.Random;
import org.junit.Test;
import testdb.mariatest.*;
import testdb.mariatest_identity.*;

/** Tests for DSL (Domain-Specific Language) operations in MariaDB. */
public class DSLTest {
  private final MariatestRepoImpl mariatestRepo = new MariatestRepoImpl();
  private final MariatestIdentityRepoImpl identityRepo = new MariatestIdentityRepoImpl();
  private final TestInsert testInsert = new TestInsert(new Random(141839624));

  @Test
  public void testSelectWithWhere() {
    MariaDbTestHelper.run(
        c -> {
          // Insert test data
          var row1 =
              testInsert
                  .Mariatest(
                      new byte[] {0x01},
                      new byte[] {0x01},
                      new byte[16],
                      new byte[] {1, 2},
                      new byte[] {3, 4},
                      new byte[] {5, 6},
                      new byte[] {7, 8},
                      new byte[] {9, 10},
                      Year.of(2025),
                      new Inet4("192.168.1.1"),
                      new Inet6("::1"))
                  .with(r -> r.withVarcharCol("test_varchar"))
                  .insert(c);

          // Use DSL to select
          var query =
              mariatestRepo
                  .select()
                  .where(m -> m.intCol().isEqual(row1.intCol()))
                  .where(m -> m.varcharCol().isEqual("test_varchar"));

          var results = query.toList(c);
          assertEquals(1, results.size());
          assertEquals(row1.intCol(), results.get(0).intCol());
          assertEquals("test_varchar", results.get(0).varcharCol());
        });
  }

  @Test
  public void testSelectWithOrdering() {
    MariaDbTestHelper.run(
        c -> {
          // Insert test data
          identityRepo.insert(new MariatestIdentityRowUnsaved("Zulu"), c);
          identityRepo.insert(new MariatestIdentityRowUnsaved("Alpha"), c);
          identityRepo.insert(new MariatestIdentityRowUnsaved("Mike"), c);

          // Select with ordering
          var query =
              identityRepo
                  .select()
                  .where(
                      m ->
                          m.name()
                              .isEqual("Zulu")
                              .or(
                                  m.name().isEqual("Alpha"),
                                  dev.typr.foundations.Bijection.asBool())
                              .or(
                                  m.name().isEqual("Mike"),
                                  dev.typr.foundations.Bijection.asBool()))
                  .orderBy(m -> m.name().asc());

          var results = query.toList(c);
          assertTrue(results.size() >= 3);
          // First result should be "Alpha" (alphabetically first)
          var sorted = results.stream().filter(r -> r.name().equals("Alpha")).findFirst();
          assertTrue(sorted.isPresent());
        });
  }

  @Test
  public void testSelectWithOrderByDesc() {
    MariaDbTestHelper.run(
        c -> {
          // Insert test data
          identityRepo.insert(new MariatestIdentityRowUnsaved("OrderDescA"), c);
          identityRepo.insert(new MariatestIdentityRowUnsaved("OrderDescB"), c);
          identityRepo.insert(new MariatestIdentityRowUnsaved("OrderDescC"), c);

          // Select with descending order
          var query =
              identityRepo
                  .select()
                  .where(m -> m.name().like("OrderDesc%", Bijection.asString()))
                  .orderBy(m -> m.name().desc());

          var results = query.toList(c);
          assertEquals(3, results.size());
          assertEquals("OrderDescC", results.get(0).name());
          assertEquals("OrderDescB", results.get(1).name());
          assertEquals("OrderDescA", results.get(2).name());
        });
  }

  @Test
  public void testSelectWithLimit() {
    MariaDbTestHelper.run(
        c -> {
          // Insert test data
          for (int i = 0; i < 10; i++) {
            identityRepo.insert(new MariatestIdentityRowUnsaved("Limit" + i), c);
          }

          // Select with limit
          var query =
              identityRepo
                  .select()
                  .where(m -> m.name().like("Limit%", Bijection.asString()))
                  .limit(3);

          var results = query.toList(c);
          assertEquals(3, results.size());
        });
  }

  @Test
  public void testSelectWithOffset() {
    MariaDbTestHelper.run(
        c -> {
          // Insert test data
          identityRepo.insert(new MariatestIdentityRowUnsaved("OffsetA"), c);
          identityRepo.insert(new MariatestIdentityRowUnsaved("OffsetB"), c);
          identityRepo.insert(new MariatestIdentityRowUnsaved("OffsetC"), c);
          identityRepo.insert(new MariatestIdentityRowUnsaved("OffsetD"), c);

          // Select with offset
          var query =
              identityRepo
                  .select()
                  .where(m -> m.name().like("Offset%", Bijection.asString()))
                  .orderBy(m -> m.name().asc())
                  .offset(2)
                  .limit(10);

          var results = query.toList(c);
          assertEquals(2, results.size());
          assertEquals("OffsetC", results.get(0).name());
          assertEquals("OffsetD", results.get(1).name());
        });
  }

  @Test
  public void testSelectWithCount() {
    MariaDbTestHelper.run(
        c -> {
          // Insert test data
          identityRepo.insert(new MariatestIdentityRowUnsaved("CountA"), c);
          identityRepo.insert(new MariatestIdentityRowUnsaved("CountB"), c);
          identityRepo.insert(new MariatestIdentityRowUnsaved("CountC"), c);

          // Count
          var query =
              identityRepo.select().where(m -> m.name().like("Count%", Bijection.asString()));

          long count = query.count(c);
          assertEquals(3, count);
        });
  }

  @Test
  public void testSelectWithGreaterThan() {
    MariaDbTestHelper.run(
        c -> {
          // Insert test data
          var row1 = identityRepo.insert(new MariatestIdentityRowUnsaved("GT1"), c);
          var row2 = identityRepo.insert(new MariatestIdentityRowUnsaved("GT2"), c);
          var row3 = identityRepo.insert(new MariatestIdentityRowUnsaved("GT3"), c);

          // Select with greater than
          var query =
              identityRepo
                  .select()
                  .where(m -> m.id().greaterThan(row1.id()))
                  .where(m -> m.name().like("GT%", Bijection.asString()));

          var results = query.toList(c);
          assertEquals(2, results.size());
        });
  }

  @Test
  public void testSelectWithLessThan() {
    MariaDbTestHelper.run(
        c -> {
          // Insert test data
          var row1 = identityRepo.insert(new MariatestIdentityRowUnsaved("LT1"), c);
          var row2 = identityRepo.insert(new MariatestIdentityRowUnsaved("LT2"), c);
          var row3 = identityRepo.insert(new MariatestIdentityRowUnsaved("LT3"), c);

          // Select with less than
          var query =
              identityRepo
                  .select()
                  .where(m -> m.id().lessThan(row3.id()))
                  .where(m -> m.name().like("LT%", Bijection.asString()));

          var results = query.toList(c);
          assertEquals(2, results.size());
        });
  }

  @Test
  public void testSelectWithLike() {
    MariaDbTestHelper.run(
        c -> {
          // Insert test data
          identityRepo.insert(new MariatestIdentityRowUnsaved("LikeTest_ABC"), c);
          identityRepo.insert(new MariatestIdentityRowUnsaved("LikeTest_XYZ"), c);
          identityRepo.insert(new MariatestIdentityRowUnsaved("OtherName"), c);

          // Select with LIKE
          var query =
              identityRepo.select().where(m -> m.name().like("LikeTest%", Bijection.asString()));

          var results = query.toList(c);
          assertEquals(2, results.size());
        });
  }

  @Test
  public void testSelectWithIn() {
    MariaDbTestHelper.run(
        c -> {
          // Insert test data
          var row1 = identityRepo.insert(new MariatestIdentityRowUnsaved("InTest1"), c);
          var row2 = identityRepo.insert(new MariatestIdentityRowUnsaved("InTest2"), c);
          var row3 = identityRepo.insert(new MariatestIdentityRowUnsaved("InTest3"), c);

          // Select with IN
          var query = identityRepo.select().where(m -> m.id().in(row1.id(), row3.id()));

          var results = query.toList(c);
          assertEquals(2, results.size());
        });
  }

  @Test
  public void testSelectWithProjection() {
    MariaDbTestHelper.run(
        c -> {
          // Insert test data
          var row = identityRepo.insert(new MariatestIdentityRowUnsaved("ProjectionTest"), c);

          // Select with projection (map to specific columns)
          var query =
              identityRepo
                  .select()
                  .where(m -> m.id().isEqual(row.id()))
                  .map(m -> m.name().tupleWith(m.id()));

          var results = query.toList(c);
          assertEquals(1, results.size());
          assertEquals("ProjectionTest", results.get(0)._1());
          assertEquals(row.id(), results.get(0)._2());
        });
  }

  @Test
  public void testSelectWithMultipleConditions() {
    MariaDbTestHelper.run(
        c -> {
          // Insert test data
          var row =
              testInsert
                  .Mariatest(
                      new byte[] {0x01},
                      new byte[] {0x01},
                      new byte[16],
                      new byte[] {1, 2},
                      new byte[] {3, 4},
                      new byte[] {5, 6},
                      new byte[] {7, 8},
                      new byte[] {9, 10},
                      Year.of(2025),
                      new Inet4("10.0.0.1"),
                      new Inet6("fe80::1"))
                  .with(
                      r ->
                          r.withVarcharCol("multi_cond")
                              .withBoolCol(true)
                              .withDecimalCol(new BigDecimal("100.00")))
                  .insert(c);

          // Select with multiple conditions
          var query =
              mariatestRepo
                  .select()
                  .where(m -> m.varcharCol().isEqual("multi_cond"))
                  .where(m -> m.boolCol().isEqual(true));

          var results = query.toList(c);
          assertEquals(1, results.size());
        });
  }

  @Test
  public void testUpdateWithDSL() {
    MariaDbTestHelper.run(
        c -> {
          // Insert test data
          var row = identityRepo.insert(new MariatestIdentityRowUnsaved("ToUpdate"), c);

          // Update via repo (DSL update setValue pattern)
          var updated = row.withName("UpdatedName");
          identityRepo.update(updated, c);

          // Verify update
          var query = identityRepo.select().where(m -> m.id().isEqual(row.id()));

          var results = query.toList(c);
          assertEquals(1, results.size());
          assertEquals("UpdatedName", results.get(0).name());
        });
  }

  @Test
  public void testDeleteWithDSL() {
    MariaDbTestHelper.run(
        c -> {
          // Insert test data
          var row = identityRepo.insert(new MariatestIdentityRowUnsaved("ToDelete"), c);

          // Verify exists
          var beforeCount = identityRepo.select().where(m -> m.id().isEqual(row.id())).count(c);
          assertEquals(1, beforeCount);

          // Delete
          identityRepo.deleteById(row.id(), c);

          // Verify deleted
          var afterCount = identityRepo.select().where(m -> m.id().isEqual(row.id())).count(c);
          assertEquals(0, afterCount);
        });
  }
}

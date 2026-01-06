package testdb;

import static org.junit.Assert.*;

import dev.typr.foundations.dsl.Bijection;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.Test;
import testdb.customers.*;
import testdb.userdefined.Email;

/** Tests for DSL query building in DuckDB. */
public class DSLTest {
  private final CustomersRepoImpl customersRepo = new CustomersRepoImpl();

  @Test
  public void testSelectWithWhere() {
    DuckDbTestHelper.run(
        c -> {
          // Insert test data
          customersRepo.insert(
              new CustomersRow(
                  new CustomersId(5001),
                  "DSL Test User",
                  Optional.of(new Email("dsl@test.com")),
                  LocalDateTime.now(),
                  Optional.of(Priority.high)),
              c);

          var results =
              customersRepo.select().where(cust -> cust.name().isEqual("DSL Test User")).toList(c);

          assertEquals(1, results.size());
          assertEquals("DSL Test User", results.get(0).name());
        });
  }

  @Test
  public void testSelectWithOrderBy() {
    DuckDbTestHelper.run(
        c -> {
          customersRepo.insert(
              new CustomersRow(
                  new CustomersId(5002),
                  "Zebra",
                  Optional.empty(),
                  LocalDateTime.now(),
                  Optional.empty()),
              c);
          customersRepo.insert(
              new CustomersRow(
                  new CustomersId(5003),
                  "Alpha",
                  Optional.empty(),
                  LocalDateTime.now(),
                  Optional.empty()),
              c);
          customersRepo.insert(
              new CustomersRow(
                  new CustomersId(5004),
                  "Mike",
                  Optional.empty(),
                  LocalDateTime.now(),
                  Optional.empty()),
              c);

          var results =
              customersRepo
                  .select()
                  .where(cust -> cust.customerId().greaterThan(new CustomersId(5001)))
                  .orderBy(cust -> cust.name().asc())
                  .toList(c);

          assertTrue(results.size() >= 3);
          assertEquals("Alpha", results.get(0).name());
        });
  }

  @Test
  public void testSelectWithOrderByDesc() {
    DuckDbTestHelper.run(
        c -> {
          customersRepo.insert(
              new CustomersRow(
                  new CustomersId(5005),
                  "DescA",
                  Optional.empty(),
                  LocalDateTime.now(),
                  Optional.empty()),
              c);
          customersRepo.insert(
              new CustomersRow(
                  new CustomersId(5006),
                  "DescB",
                  Optional.empty(),
                  LocalDateTime.now(),
                  Optional.empty()),
              c);
          customersRepo.insert(
              new CustomersRow(
                  new CustomersId(5007),
                  "DescC",
                  Optional.empty(),
                  LocalDateTime.now(),
                  Optional.empty()),
              c);

          var results =
              customersRepo
                  .select()
                  .where(cust -> cust.name().like("Desc%", Bijection.asString()))
                  .orderBy(cust -> cust.name().desc())
                  .toList(c);

          assertEquals(3, results.size());
          assertEquals("DescC", results.get(0).name());
          assertEquals("DescB", results.get(1).name());
          assertEquals("DescA", results.get(2).name());
        });
  }

  @Test
  public void testSelectWithLimit() {
    DuckDbTestHelper.run(
        c -> {
          for (int i = 0; i < 10; i++) {
            customersRepo.insert(
                new CustomersRow(
                    new CustomersId(5100 + i),
                    "Limit" + i,
                    Optional.empty(),
                    LocalDateTime.now(),
                    Optional.empty()),
                c);
          }

          var results =
              customersRepo
                  .select()
                  .where(cust -> cust.name().like("Limit%", Bijection.asString()))
                  .limit(3)
                  .toList(c);

          assertEquals(3, results.size());
        });
  }

  @Test
  public void testSelectWithOffset() {
    DuckDbTestHelper.run(
        c -> {
          customersRepo.insert(
              new CustomersRow(
                  new CustomersId(5200),
                  "OffsetA",
                  Optional.empty(),
                  LocalDateTime.now(),
                  Optional.empty()),
              c);
          customersRepo.insert(
              new CustomersRow(
                  new CustomersId(5201),
                  "OffsetB",
                  Optional.empty(),
                  LocalDateTime.now(),
                  Optional.empty()),
              c);
          customersRepo.insert(
              new CustomersRow(
                  new CustomersId(5202),
                  "OffsetC",
                  Optional.empty(),
                  LocalDateTime.now(),
                  Optional.empty()),
              c);
          customersRepo.insert(
              new CustomersRow(
                  new CustomersId(5203),
                  "OffsetD",
                  Optional.empty(),
                  LocalDateTime.now(),
                  Optional.empty()),
              c);

          var results =
              customersRepo
                  .select()
                  .where(cust -> cust.name().like("Offset%", Bijection.asString()))
                  .orderBy(cust -> cust.name().asc())
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
    DuckDbTestHelper.run(
        c -> {
          customersRepo.insert(
              new CustomersRow(
                  new CustomersId(5300),
                  "CountA",
                  Optional.empty(),
                  LocalDateTime.now(),
                  Optional.empty()),
              c);
          customersRepo.insert(
              new CustomersRow(
                  new CustomersId(5301),
                  "CountB",
                  Optional.empty(),
                  LocalDateTime.now(),
                  Optional.empty()),
              c);
          customersRepo.insert(
              new CustomersRow(
                  new CustomersId(5302),
                  "CountC",
                  Optional.empty(),
                  LocalDateTime.now(),
                  Optional.empty()),
              c);

          var count =
              customersRepo
                  .select()
                  .where(cust -> cust.name().like("Count%", Bijection.asString()))
                  .count(c);

          assertEquals(3, count);
        });
  }

  @Test
  public void testSelectWithLike() {
    DuckDbTestHelper.run(
        c -> {
          customersRepo.insert(
              new CustomersRow(
                  new CustomersId(5400),
                  "LikeTest_ABC",
                  Optional.empty(),
                  LocalDateTime.now(),
                  Optional.empty()),
              c);
          customersRepo.insert(
              new CustomersRow(
                  new CustomersId(5401),
                  "LikeTest_XYZ",
                  Optional.empty(),
                  LocalDateTime.now(),
                  Optional.empty()),
              c);
          customersRepo.insert(
              new CustomersRow(
                  new CustomersId(5402),
                  "OtherName",
                  Optional.empty(),
                  LocalDateTime.now(),
                  Optional.empty()),
              c);

          var results =
              customersRepo
                  .select()
                  .where(cust -> cust.name().like("LikeTest%", Bijection.asString()))
                  .toList(c);

          assertEquals(2, results.size());
        });
  }

  @Test
  public void testSelectWithIn() {
    DuckDbTestHelper.run(
        c -> {
          var c1 =
              customersRepo.insert(
                  new CustomersRow(
                      new CustomersId(5500),
                      "InTest1",
                      Optional.empty(),
                      LocalDateTime.now(),
                      Optional.empty()),
                  c);
          customersRepo.insert(
              new CustomersRow(
                  new CustomersId(5501),
                  "InTest2",
                  Optional.empty(),
                  LocalDateTime.now(),
                  Optional.empty()),
              c);
          var c3 =
              customersRepo.insert(
                  new CustomersRow(
                      new CustomersId(5502),
                      "InTest3",
                      Optional.empty(),
                      LocalDateTime.now(),
                      Optional.empty()),
                  c);

          var results =
              customersRepo
                  .select()
                  .where(cust -> cust.customerId().in(c1.customerId(), c3.customerId()))
                  .toList(c);

          assertEquals(2, results.size());
        });
  }

  @Test
  public void testSelectWithGreaterThan() {
    DuckDbTestHelper.run(
        c -> {
          var c1 =
              customersRepo.insert(
                  new CustomersRow(
                      new CustomersId(5600),
                      "GT1",
                      Optional.empty(),
                      LocalDateTime.now(),
                      Optional.empty()),
                  c);
          customersRepo.insert(
              new CustomersRow(
                  new CustomersId(5601),
                  "GT2",
                  Optional.empty(),
                  LocalDateTime.now(),
                  Optional.empty()),
              c);
          customersRepo.insert(
              new CustomersRow(
                  new CustomersId(5602),
                  "GT3",
                  Optional.empty(),
                  LocalDateTime.now(),
                  Optional.empty()),
              c);

          var results =
              customersRepo
                  .select()
                  .where(cust -> cust.customerId().greaterThan(c1.customerId()))
                  .where(cust -> cust.name().like("GT%", Bijection.asString()))
                  .toList(c);

          assertEquals(2, results.size());
        });
  }

  @Test
  public void testSelectWithProjection() {
    DuckDbTestHelper.run(
        c -> {
          customersRepo.insert(
              new CustomersRow(
                  new CustomersId(5700),
                  "ProjectionTest",
                  Optional.of(new Email("projection@test.com")),
                  LocalDateTime.now(),
                  Optional.empty()),
              c);

          var results =
              customersRepo
                  .select()
                  .where(cust -> cust.customerId().isEqual(new CustomersId(5700)))
                  .map(cust -> cust.name().tupleWith(cust.email()))
                  .toList(c);

          assertEquals(1, results.size());
          assertEquals("ProjectionTest", results.get(0)._1());
          assertEquals(new Email("projection@test.com"), results.get(0)._2());
        });
  }
}

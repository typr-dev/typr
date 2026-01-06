package testdb

import dev.typr.foundations.dsl.Bijection
import org.junit.Assert._
import org.junit.Test
import testdb.customers._
import testdb.userdefined.Email

import java.time.LocalDateTime

/** Tests for DSL query building in DuckDB.
  */
class DSLTest {
  private val customersRepo = CustomersRepoImpl()

  @Test
  def testSelectWithWhere(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val _ = customersRepo.insert(
      CustomersRow(
        CustomersId(5001),
        "DSL Test User",
        Some(Email("dsl@test.com")),
        LocalDateTime.now(),
        Some(Priority.high)
      )
    )

    val results = customersRepo.select
      .where(_.name.isEqual("DSL Test User"))
      .toList

    assertEquals(1, results.size)
    assertEquals("DSL Test User", results.head.name)
  }

  @Test
  def testSelectWithOrderBy(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val _ = customersRepo.insert(
      CustomersRow(
        CustomersId(5002),
        "Zebra",
        None,
        LocalDateTime.now(),
        None
      )
    )
    val _ = customersRepo.insert(
      CustomersRow(
        CustomersId(5003),
        "Alpha",
        None,
        LocalDateTime.now(),
        None
      )
    )
    val _ = customersRepo.insert(
      CustomersRow(
        CustomersId(5004),
        "Mike",
        None,
        LocalDateTime.now(),
        None
      )
    )

    val results = customersRepo.select
      .where(_.customerId.greaterThan(CustomersId(5001)))
      .orderBy(_.name.asc)
      .toList

    assertTrue(results.size >= 3)
    assertEquals("Alpha", results.head.name)
  }

  @Test
  def testSelectWithOrderByDesc(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val _ = customersRepo.insert(
      CustomersRow(
        CustomersId(5005),
        "DescA",
        None,
        LocalDateTime.now(),
        None
      )
    )
    val _ = customersRepo.insert(
      CustomersRow(
        CustomersId(5006),
        "DescB",
        None,
        LocalDateTime.now(),
        None
      )
    )
    val _ = customersRepo.insert(
      CustomersRow(
        CustomersId(5007),
        "DescC",
        None,
        LocalDateTime.now(),
        None
      )
    )

    val results = customersRepo.select
      .where(_.name.like("Desc%", Bijection.asString()))
      .orderBy(_.name.desc)
      .toList

    assertEquals(3, results.size)
    assertEquals("DescC", results.head.name)
    assertEquals("DescB", results(1).name)
    assertEquals("DescA", results(2).name)
  }

  @Test
  def testSelectWithLimit(): Unit = withConnection { c =>
    given java.sql.Connection = c

    for (i <- 0 until 10) {
      val _ = customersRepo.insert(
        CustomersRow(
          CustomersId(5100 + i),
          s"Limit$i",
          None,
          LocalDateTime.now(),
          None
        )
      )
    }

    val results = customersRepo.select
      .where(_.name.like("Limit%", Bijection.asString()))
      .limit(3)
      .toList

    assertEquals(3, results.size)
  }

  @Test
  def testSelectWithOffset(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val _ = customersRepo.insert(
      CustomersRow(
        CustomersId(5200),
        "OffsetA",
        None,
        LocalDateTime.now(),
        None
      )
    )
    val _ = customersRepo.insert(
      CustomersRow(
        CustomersId(5201),
        "OffsetB",
        None,
        LocalDateTime.now(),
        None
      )
    )
    val _ = customersRepo.insert(
      CustomersRow(
        CustomersId(5202),
        "OffsetC",
        None,
        LocalDateTime.now(),
        None
      )
    )
    val _ = customersRepo.insert(
      CustomersRow(
        CustomersId(5203),
        "OffsetD",
        None,
        LocalDateTime.now(),
        None
      )
    )

    val results = customersRepo.select
      .where(_.name.like("Offset%", Bijection.asString()))
      .orderBy(_.name.asc)
      .offset(2)
      .limit(10)
      .toList

    assertEquals(2, results.size)
    assertEquals("OffsetC", results.head.name)
    assertEquals("OffsetD", results(1).name)
  }

  @Test
  def testSelectWithCount(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val _ = customersRepo.insert(
      CustomersRow(
        CustomersId(5300),
        "CountA",
        None,
        LocalDateTime.now(),
        None
      )
    )
    val _ = customersRepo.insert(
      CustomersRow(
        CustomersId(5301),
        "CountB",
        None,
        LocalDateTime.now(),
        None
      )
    )
    val _ = customersRepo.insert(
      CustomersRow(
        CustomersId(5302),
        "CountC",
        None,
        LocalDateTime.now(),
        None
      )
    )

    val count = customersRepo.select
      .where(_.name.like("Count%", Bijection.asString()))
      .count

    assertEquals(3, count)
  }

  @Test
  def testSelectWithLike(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val _ = customersRepo.insert(
      CustomersRow(
        CustomersId(5400),
        "LikeTest_ABC",
        None,
        LocalDateTime.now(),
        None
      )
    )
    val _ = customersRepo.insert(
      CustomersRow(
        CustomersId(5401),
        "LikeTest_XYZ",
        None,
        LocalDateTime.now(),
        None
      )
    )
    val _ = customersRepo.insert(
      CustomersRow(
        CustomersId(5402),
        "OtherName",
        None,
        LocalDateTime.now(),
        None
      )
    )

    val results = customersRepo.select
      .where(_.name.like("LikeTest%", Bijection.asString()))
      .toList

    assertEquals(2, results.size)
  }

  @Test
  def testSelectWithIn(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val c1 = customersRepo.insert(
      CustomersRow(
        CustomersId(5500),
        "InTest1",
        None,
        LocalDateTime.now(),
        None
      )
    )
    val _ = customersRepo.insert(
      CustomersRow(
        CustomersId(5501),
        "InTest2",
        None,
        LocalDateTime.now(),
        None
      )
    )
    val c3 = customersRepo.insert(
      CustomersRow(
        CustomersId(5502),
        "InTest3",
        None,
        LocalDateTime.now(),
        None
      )
    )

    val results = customersRepo.select
      .where(_.customerId.in(c1.customerId, c3.customerId))
      .toList

    assertEquals(2, results.size)
  }

  @Test
  def testSelectWithGreaterThan(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val c1 = customersRepo.insert(
      CustomersRow(
        CustomersId(5600),
        "GT1",
        None,
        LocalDateTime.now(),
        None
      )
    )
    val _ = customersRepo.insert(
      CustomersRow(
        CustomersId(5601),
        "GT2",
        None,
        LocalDateTime.now(),
        None
      )
    )
    val _ = customersRepo.insert(
      CustomersRow(
        CustomersId(5602),
        "GT3",
        None,
        LocalDateTime.now(),
        None
      )
    )

    val results = customersRepo.select
      .where(_.customerId.greaterThan(c1.customerId))
      .where(_.name.like("GT%", Bijection.asString()))
      .toList

    assertEquals(2, results.size)
  }

  @Test
  def testSelectWithProjection(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val _ = customersRepo.insert(
      CustomersRow(
        CustomersId(5700),
        "ProjectionTest",
        Some(Email("projection@test.com")),
        LocalDateTime.now(),
        None
      )
    )

    val results = customersRepo.select
      .where(_.customerId.isEqual(CustomersId(5700)))
      .map(cust => cust.name.tupleWith(cust.email))
      .toList

    assertEquals(1, results.size)
    assertEquals("ProjectionTest", results.head._1)
    assertEquals(Email("projection@test.com"), results.head._2)
  }
}

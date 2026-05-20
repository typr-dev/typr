package testdb

import dev.typr.foundations.Bijection
import org.junit.Assert.*
import org.junit.Test
import testdb.customers.*

import java.time.LocalDateTime

class DSLTest {
  private val repo = new CustomersRepoImpl()
  private def row(id: Long, name: String) =
    CustomersRow(CustomersId(id), name, None, LocalDateTime.of(2025, 1, 1, 0, 0))

  @Test def selectWithWhere(): Unit = withConnection {
    repo.insert(row(5001L, "DSL Test User"))
    val results = repo.select.where(_.name.isEqual("DSL Test User")).toList
    assertEquals(1, results.size)
  }

  @Test def selectWithOrderByAsc(): Unit = withConnection {
    repo.insert(row(5002L, "Zebra"))
    repo.insert(row(5003L, "Alpha"))
    repo.insert(row(5004L, "Mike"))
    val r = repo.select
      .where(_.customerId.greaterThan(CustomersId(5001L)))
      .orderBy(_.name.asc)
      .toList
    assertEquals("Alpha", r.head.name)
    assertEquals("Zebra", r.last.name)
  }

  @Test def selectWithOrderByDesc(): Unit = withConnection {
    repo.insert(row(5005L, "DescA"))
    repo.insert(row(5006L, "DescB"))
    repo.insert(row(5007L, "DescC"))
    val r = repo.select
      .where(_.name.like("Desc%", Bijection.asString()))
      .orderBy(_.name.desc)
      .toList
    assertEquals("DescC", r.head.name)
  }

  @Test def selectWithLimit(): Unit = withConnection {
    (0 until 10).foreach(i => repo.insert(row(5100L + i, s"Limit$i")))
    val r = repo.select.where(_.name.like("Limit%", Bijection.asString())).limit(3).toList
    assertEquals(3, r.size)
  }

  @Test def selectWithOffset(): Unit = withConnection {
    repo.insert(row(5200L, "OffsetA"))
    repo.insert(row(5201L, "OffsetB"))
    repo.insert(row(5202L, "OffsetC"))
    repo.insert(row(5203L, "OffsetD"))
    val r = repo.select
      .where(_.name.like("Offset%", Bijection.asString()))
      .orderBy(_.name.asc)
      .offset(2)
      .limit(10)
      .toList
    assertEquals(List("OffsetC", "OffsetD"), r.map(_.name))
  }

  @Test def selectWithCount(): Unit = withConnection {
    repo.insert(row(5300L, "CountA"))
    repo.insert(row(5301L, "CountB"))
    repo.insert(row(5302L, "CountC"))
    val count = repo.select.where(_.name.like("Count%", Bijection.asString())).count
    assertEquals(3L, count)
  }

  @Test def selectWithLike(): Unit = withConnection {
    repo.insert(row(5400L, "LikeTest_ABC"))
    repo.insert(row(5401L, "LikeTest_XYZ"))
    repo.insert(row(5402L, "Other"))
    val r = repo.select.where(_.name.like("LikeTest%", Bijection.asString())).toList
    assertEquals(2, r.size)
  }

  @Test def selectWithIn(): Unit = withConnection {
    repo.insert(row(5500L, "In1"))
    repo.insert(row(5501L, "In2"))
    repo.insert(row(5502L, "In3"))
    val r = repo.select.where(_.customerId.in(CustomersId(5500L), CustomersId(5502L))).toList
    assertEquals(2, r.size)
  }

  @Test def updateBuilder(): Unit = withConnection {
    repo.insert(row(5600L, "UpdateMe"))
    val n = repo.update.where(_.customerId.isEqual(CustomersId(5600L))).setValue(_.name, "Updated").execute
    assertEquals(1, n)
    assertEquals("Updated", repo.selectById(CustomersId(5600L)).get.name)
  }

  @Test def deleteBuilder(): Unit = withConnection {
    repo.insert(row(5700L, "DeleteA"))
    repo.insert(row(5701L, "DeleteB"))
    repo.insert(row(5702L, "DeleteC"))
    val n = repo.delete.where(_.name.like("Delete%", Bijection.asString())).execute
    assertEquals(3, n)
  }
}

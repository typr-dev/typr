package testdb

import org.junit.Assert.*
import org.junit.Test
import testdb.departments.*

class TupleInTest {
  private val repo = new DepartmentsRepoImpl()

  @Test def compositeIdInWithMultipleIds(): Unit = withConnection {
    val d1 = repo.insert(DepartmentsRow("TI_A", "US", "A US", None))
    repo.insert(DepartmentsRow("TI_A", "EU", "A EU", None))
    val d3 = repo.insert(DepartmentsRow("TI_B", "US", "B US", None))
    repo.insert(DepartmentsRow("TI_B", "EU", "B EU", None))

    val result = repo.select.where(d => d.compositeIdIn(List(d1.compositeId, d3.compositeId))).toList
    assertEquals(2, result.size)
    val ids = result.map(_.compositeId).toSet
    assertEquals(Set(d1.compositeId, d3.compositeId), ids)
  }

  @Test def compositeIdInWithSingleId(): Unit = withConnection {
    val d1 = repo.insert(DepartmentsRow("TI_S", "APAC", "S APAC", None))
    repo.insert(DepartmentsRow("TI_S", "EMEA", "S EMEA", None))
    val result = repo.select.where(d => d.compositeIdIn(List(d1.compositeId))).toList
    assertEquals(1, result.size)
    assertEquals(d1, result.head)
  }

  @Test def compositeIdInWithEmptyList(): Unit = withConnection {
    val result = repo.select.where(d => d.compositeIdIn(Nil)).toList
    assertTrue(result.isEmpty)
  }

  @Test def compositeIdInWithNonExistent(): Unit = withConnection {
    val result = repo.select.where(d => d.compositeIdIn(List(DepartmentsId("NOPE", "NEVER"), DepartmentsId("ALSO", "MISSING")))).toList
    assertTrue(result.isEmpty)
  }

  @Test def compositeIdInCombinedWithOther(): Unit = withConnection {
    val d1 = repo.insert(DepartmentsRow("TI_C", "US", "Combined US", Some(BigDecimal("100000"))))
    val d2 = repo.insert(DepartmentsRow("TI_C", "EU", "Combined EU", Some(BigDecimal("50000"))))
    val result = repo.select
      .where(d => d.compositeIdIn(List(d1.compositeId, d2.compositeId)))
      .where(d => d.budget.greaterThan(BigDecimal("75000")))
      .toList
    assertEquals(1, result.size)
    assertEquals(d1, result.head)
  }
}

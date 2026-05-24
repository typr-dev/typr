package testdb

import org.junit.Assert.*
import org.junit.Test
import testdb.departments.*
import java.math.BigDecimal

class TupleInTest {
    private val repo = DepartmentsRepoImpl()

    @Test
    fun compositeIdInMultiple() {
        SqliteTestHelper.run { c ->
            val d1 = repo.insert(DepartmentsRow("TI_A", "US", "A US", null), c)
            repo.insert(DepartmentsRow("TI_A", "EU", "A EU", null), c)
            val d3 = repo.insert(DepartmentsRow("TI_B", "US", "B US", null), c)
            repo.insert(DepartmentsRow("TI_B", "EU", "B EU", null), c)

            val result = repo.select()
                .where { it.compositeIdIn(listOf(d1.compositeId(), d3.compositeId())) }
                .toList(c)
            assertEquals(2, result.size)
            val ids = result.map { it.compositeId() }.toSet()
            assertEquals(setOf(d1.compositeId(), d3.compositeId()), ids)
        }
    }

    @Test
    fun compositeIdInSingle() {
        SqliteTestHelper.run { c ->
            val d1 = repo.insert(DepartmentsRow("TI_S", "APAC", "S APAC", null), c)
            repo.insert(DepartmentsRow("TI_S", "EMEA", "S EMEA", null), c)
            val result = repo.select().where { it.compositeIdIn(listOf(d1.compositeId())) }.toList(c)
            assertEquals(1, result.size)
            assertEquals(d1, result[0])
        }
    }

    @Test
    fun compositeIdInEmpty() {
        SqliteTestHelper.run { c ->
            val result = repo.select().where { it.compositeIdIn(emptyList()) }.toList(c)
            assertTrue(result.isEmpty())
        }
    }

    @Test
    fun compositeIdInNonExistent() {
        SqliteTestHelper.run { c ->
            val result = repo.select().where {
                it.compositeIdIn(listOf(DepartmentsId("NOPE", "NEVER"), DepartmentsId("ALSO", "MISSING")))
            }.toList(c)
            assertTrue(result.isEmpty())
        }
    }

    @Test
    fun compositeIdInCombinedWithOther() {
        SqliteTestHelper.run { c ->
            val d1 = repo.insert(DepartmentsRow("TI_C", "US", "Combined US", BigDecimal("100000")), c)
            val d2 = repo.insert(DepartmentsRow("TI_C", "EU", "Combined EU", BigDecimal("50000")), c)
            val result = repo.select()
                .where { it.compositeIdIn(listOf(d1.compositeId(), d2.compositeId())) }
                .where { it.budget().greaterThan(BigDecimal("75000")) }
                .toList(c)
            assertEquals(1, result.size)
            assertEquals(d1, result[0])
        }
    }
}

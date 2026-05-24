package testdb

import org.junit.Assert.*
import org.junit.Test
import testdb.customers.*
import java.time.LocalDateTime

class DSLTest {
    private val repo = CustomersRepoImpl()
    private fun row(id: Long, name: String) =
        CustomersRow(CustomersId(id), name, null, LocalDateTime.of(2025, 1, 1, 0, 0))

    @Test
    fun selectWithWhere() {
        SqliteTestHelper.run { c ->
            repo.insert(row(5001L, "DSL Test User"), c)
            val results = repo.select().where { it.name().isEqual("DSL Test User") }.toList(c)
            assertEquals(1, results.size)
        }
    }

    @Test
    fun selectWithOrderByAsc() {
        SqliteTestHelper.run { c ->
            repo.insert(row(5002L, "Zebra"), c)
            repo.insert(row(5003L, "Alpha"), c)
            repo.insert(row(5004L, "Mike"), c)
            val r = repo.select()
                .where { it.customerId().greaterThan(CustomersId(5001L)) }
                .orderBy { it.name().asc() }
                .toList(c)
            assertEquals("Alpha", r[0].name)
            assertEquals("Zebra", r.last().name)
        }
    }

    @Test
    fun selectWithOrderByDesc() {
        SqliteTestHelper.run { c ->
            repo.insert(row(5005L, "DescA"), c)
            repo.insert(row(5006L, "DescB"), c)
            repo.insert(row(5007L, "DescC"), c)
            val r = repo.select()
                .where { it.name().like("Desc%") }
                .orderBy { it.name().desc() }
                .toList(c)
            assertEquals("DescC", r[0].name)
        }
    }

    @Test
    fun selectWithLimit() {
        SqliteTestHelper.run { c ->
            (0 until 10).forEach { i -> repo.insert(row(5100L + i, "Limit$i"), c) }
            val r = repo.select().where { it.name().like("Limit%") }.limit(3).toList(c)
            assertEquals(3, r.size)
        }
    }

    @Test
    fun selectWithOffset() {
        SqliteTestHelper.run { c ->
            repo.insert(row(5200L, "OffsetA"), c)
            repo.insert(row(5201L, "OffsetB"), c)
            repo.insert(row(5202L, "OffsetC"), c)
            repo.insert(row(5203L, "OffsetD"), c)
            val r = repo.select()
                .where { it.name().like("Offset%") }
                .orderBy { it.name().asc() }
                .offset(2).limit(10).toList(c)
            assertEquals(listOf("OffsetC", "OffsetD"), r.map { it.name })
        }
    }

    @Test
    fun selectWithCount() {
        SqliteTestHelper.run { c ->
            repo.insert(row(5300L, "CountA"), c)
            repo.insert(row(5301L, "CountB"), c)
            repo.insert(row(5302L, "CountC"), c)
            val count = repo.select().where { it.name().like("Count%") }.count(c)
            assertEquals(3, count)
        }
    }

    @Test
    fun selectWithLike() {
        SqliteTestHelper.run { c ->
            repo.insert(row(5400L, "LikeTest_ABC"), c)
            repo.insert(row(5401L, "LikeTest_XYZ"), c)
            repo.insert(row(5402L, "Other"), c)
            val r = repo.select().where { it.name().like("LikeTest%") }.toList(c)
            assertEquals(2, r.size)
        }
    }

    @Test
    fun selectWithIn() {
        SqliteTestHelper.run { c ->
            repo.insert(row(5500L, "In1"), c)
            repo.insert(row(5501L, "In2"), c)
            repo.insert(row(5502L, "In3"), c)
            val r = repo.select().where { it.customerId().`in`(CustomersId(5500L), CustomersId(5502L)) }.toList(c)
            assertEquals(2, r.size)
        }
    }

    @Test
    fun updateBuilder() {
        SqliteTestHelper.run { c ->
            repo.insert(row(5600L, "UpdateMe"), c)
            val n = repo.update()
                .where { it.customerId().isEqual(CustomersId(5600L)) }
                .setValue({ it.name() }, "Updated")
                .execute(c)
            assertEquals(1, n)
            assertEquals("Updated", repo.selectById(CustomersId(5600L), c)!!.name)
        }
    }

    @Test
    fun deleteBuilder() {
        SqliteTestHelper.run { c ->
            repo.insert(row(5700L, "DeleteA"), c)
            repo.insert(row(5701L, "DeleteB"), c)
            repo.insert(row(5702L, "DeleteC"), c)
            val n = repo.delete().where { cust -> cust.name().like("Delete%") }.execute(c)
            assertEquals(3, n)
        }
    }
}

package testdb

import org.junit.Assert.*
import org.junit.Test
import testdb.customers.*
import testdb.userdefined.Email
import java.time.LocalDateTime

class DSLTest {
    private val customersRepo = CustomersRepoImpl()

    @Test
    fun testSelectWithWhere() {
        DuckDbTestHelper.run { c ->
            customersRepo.insert(
                CustomersRow(
                    CustomersId(5001), "DSL Test User", Email("dsl@test.com"),
                    LocalDateTime.now(), Priority.high
                ), c
            )

            val results = customersRepo.select()
                .where { cust -> cust.name().isEqual("DSL Test User") }
                .toList(c)

            assertEquals(1, results.size)
            assertEquals("DSL Test User", results[0].name)
        }
    }

    @Test
    fun testSelectWithOrderBy() {
        DuckDbTestHelper.run { c ->
            customersRepo.insert(CustomersRow(CustomersId(5002), "Zebra", null, LocalDateTime.now(), null), c)
            customersRepo.insert(CustomersRow(CustomersId(5003), "Alpha", null, LocalDateTime.now(), null), c)
            customersRepo.insert(CustomersRow(CustomersId(5004), "Mike", null, LocalDateTime.now(), null), c)

            val results = customersRepo.select()
                .where { cust -> cust.customerId().greaterThan(CustomersId(5001)) }
                .orderBy { cust -> cust.name().asc() }
                .toList(c)

            assertTrue(results.size >= 3)
            assertEquals("Alpha", results[0].name)
        }
    }

    @Test
    fun testSelectWithLimit() {
        DuckDbTestHelper.run { c ->
            for (i in 0 until 10) {
                customersRepo.insert(
                    CustomersRow(CustomersId(5100 + i), "Limit$i", null, LocalDateTime.now(), null), c
                )
            }

            val results = customersRepo.select()
                .where { cust -> cust.name().like("Limit%") }
                .limit(3)
                .toList(c)

            assertEquals(3, results.size)
        }
    }

    @Test
    fun testSelectWithCount() {
        DuckDbTestHelper.run { c ->
            customersRepo.insert(CustomersRow(CustomersId(5300), "CountA", null, LocalDateTime.now(), null), c)
            customersRepo.insert(CustomersRow(CustomersId(5301), "CountB", null, LocalDateTime.now(), null), c)
            customersRepo.insert(CustomersRow(CustomersId(5302), "CountC", null, LocalDateTime.now(), null), c)

            val count = customersRepo.select()
                .where { cust -> cust.name().like("Count%") }
                .count(c)

            assertEquals(3, count)
        }
    }

    @Test
    fun testSelectWithIn() {
        DuckDbTestHelper.run { c ->
            val c1 = customersRepo.insert(CustomersRow(CustomersId(5500), "InTest1", null, LocalDateTime.now(), null), c)
            customersRepo.insert(CustomersRow(CustomersId(5501), "InTest2", null, LocalDateTime.now(), null), c)
            val c3 = customersRepo.insert(CustomersRow(CustomersId(5502), "InTest3", null, LocalDateTime.now(), null), c)

            val results = customersRepo.select()
                .where { cust -> cust.customerId().`in`(c1.customerId, c3.customerId) }
                .toList(c)

            assertEquals(2, results.size)
        }
    }

    @Test
    fun testSelectWithProjection() {
        DuckDbTestHelper.run { c ->
            customersRepo.insert(
                CustomersRow(
                    CustomersId(5700), "ProjectionTest", Email("projection@test.com"),
                    LocalDateTime.now(), null
                ), c
            )

            val results = customersRepo.select()
                .where { cust -> cust.customerId().isEqual(CustomersId(5700)) }
                .map { cust -> cust.name().tupleWith(cust.email()) }
                .toList(c)

            assertEquals(1, results.size)
            assertEquals("ProjectionTest", results[0]._1())
            assertEquals(Email("projection@test.com"), results[0]._2())
        }
    }
}

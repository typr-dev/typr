package adventureworks

import adventureworks.humanresources.employee.EmployeeRepoImpl
import adventureworks.person.businessentity.BusinessentityRepoImpl
import adventureworks.person.emailaddress.EmailaddressRepoImpl
import adventureworks.person.person.PersonRepoImpl
import adventureworks.sales.salesperson.SalespersonRepoImpl
import adventureworks.userdefined.FirstName
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Random

class DSLTest {
    private val testInsert = TestInsert(Random(0), DomainInsert)
    private val businessentityRepoImpl = BusinessentityRepoImpl()
    private val personRepoImpl = PersonRepoImpl()
    private val employeeRepoImpl = EmployeeRepoImpl()
    private val salespersonRepoImpl = SalespersonRepoImpl()
    private val emailaddressRepoImpl = EmailaddressRepoImpl()

    @Test
    fun works() {
        WithConnection.run { c ->
            val businessentityRow = testInsert.personBusinessentity(c = c)
            val personRow = testInsert.personPerson(
                businessentityid = businessentityRow.businessentityid,
                persontype = "EM",
                firstname = FirstName("a"),
                c = c
            )
            testInsert.personEmailaddress(
                businessentityid = personRow.businessentityid,
                emailaddress = "a@b.c",
                c = c
            )
            val employeeRow = testInsert.humanresourcesEmployee(
                businessentityid = personRow.businessentityid,
                nationalidnumber = "123456789",
                loginid = "adventure-works\\test",
                jobtitle = "Test Job",
                birthdate = LocalDate.of(1990, 1, 1),
                maritalstatus = "S",
                gender = "M",
                hiredate = LocalDate.of(2020, 1, 1),
                c = c
            )
            val salespersonRow = testInsert.salesSalesperson(
                businessentityid = employeeRow.businessentityid,
                c = c
            )

            val q = salespersonRepoImpl.select()
                .where { it.rowguid().isEqual(salespersonRow.rowguid) }
                .joinFk({ it.fkHumanresourcesEmployee() }, employeeRepoImpl.select())
                .joinFk({ it._2().fkPersonPerson() }, personRepoImpl.select())
                .joinFk({ it._2().fkBusinessentity() }, businessentityRepoImpl.select())
                .join(emailaddressRepoImpl.select().orderBy { it.rowguid().asc() })
                .on { sp_e_p_b_email -> sp_e_p_b_email._2().businessentityid().underlying.isEqual(sp_e_p_b_email._1()._2().businessentityid().underlying) }
                .joinOn(salespersonRepoImpl.select()) { sp_e_p_b_email_s2 ->
                    sp_e_p_b_email_s2._1()._1()._1()._2().businessentityid().underlying.isEqual(sp_e_p_b_email_s2._2().businessentityid().underlying)
                }

            val doubled = q.join(q).on { left_right ->
                left_right._1()._1()._1()._1()._2().businessentityid().underlying.isEqual(left_right._2()._1()._1()._1()._2().businessentityid().underlying)
            }

            doubled.toList(c).forEach { println(it) }
            val count = doubled.count(c)
            assertEquals(1, count.toInt())

            SnapshotTest.compareFragment("DSLTest", "doubled", doubled.sql())
        }
    }
}

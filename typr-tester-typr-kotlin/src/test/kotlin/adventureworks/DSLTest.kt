package adventureworks

import java.time.LocalDate
import adventureworks.customtypes.Defaulted
import adventureworks.humanresources.employee.EmployeeRepoImpl
import adventureworks.person.businessentity.BusinessentityRepoImpl
import adventureworks.person.emailaddress.EmailaddressRepoImpl
import adventureworks.person.person.PersonRepoImpl
import adventureworks.sales.salesperson.SalespersonRepoImpl
import adventureworks.userdefined.FirstName
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Random

class DSLTest {
    private val businessentityRepoImpl = BusinessentityRepoImpl()
    private val personRepoImpl = PersonRepoImpl()
    private val employeeRepoImpl = EmployeeRepoImpl()
    private val salespersonRepoImpl = SalespersonRepoImpl()
    private val emailaddressRepoImpl = EmailaddressRepoImpl()

    @Test
    fun works() {
        WithConnection.run { c ->
            val testInsert = TestInsert(Random(0), DomainInsert)
            val businessentityRow = testInsert.personBusinessentity(businessentityid = Defaulted.UseDefault(), rowguid = Defaulted.UseDefault(), modifieddate = Defaulted.UseDefault(), c = c)
            val personRow = testInsert.personPerson(
                businessentityid = businessentityRow.businessentityid,
                persontype = "EM",
                firstname = FirstName("a"),
                title = null,
                middlename = null,
                lastname = testInsert.domainInsert.publicName(testInsert.random),
                suffix = null,
                additionalcontactinfo = null,
                demographics = null,
                namestyle = Defaulted.UseDefault(),
                emailpromotion = Defaulted.UseDefault(),
                rowguid = Defaulted.UseDefault(),
                modifieddate = Defaulted.UseDefault(),
                c = c
            )
            testInsert.personEmailaddress(businessentityid = personRow.businessentityid, emailaddress = "a@b.c", emailaddressid = Defaulted.UseDefault(), rowguid = Defaulted.UseDefault(), modifieddate = Defaulted.UseDefault(), c = c)
            val employeeRow = testInsert.humanresourcesEmployee(
                businessentityid = personRow.businessentityid,
                nationalidnumber = testInsert.random.nextInt(999999999).toString(),
                loginid = "adventure-works\\test" + testInsert.random.nextInt(9999),
                jobtitle = "Test Job Title",
                gender = "M",
                maritalstatus = "M",
                birthdate = LocalDate.parse("1998-01-01"),
                hiredate = LocalDate.parse("1997-01-01"),
                salariedflag = Defaulted.UseDefault(),
                vacationhours = Defaulted.UseDefault(),
                sickleavehours = Defaulted.UseDefault(),
                currentflag = Defaulted.UseDefault(),
                rowguid = Defaulted.UseDefault(),
                modifieddate = Defaulted.UseDefault(),
                organizationnode = Defaulted.UseDefault(),
                c = c
            )
            val salespersonRow = testInsert.salesSalesperson(businessentityid = employeeRow.businessentityid, territoryid = null, salesquota = null, bonus = Defaulted.UseDefault(), commissionpct = Defaulted.UseDefault(), salesytd = Defaulted.UseDefault(), saleslastyear = Defaulted.UseDefault(), rowguid = Defaulted.UseDefault(), modifieddate = Defaulted.UseDefault(), c = c)

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

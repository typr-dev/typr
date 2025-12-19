package adventureworks

import java.time.LocalDate
import adventureworks.customtypes.Defaulted
import adventureworks.person_row_join.PersonRowJoinSqlRepoImpl
import adventureworks.userdefined.FirstName
import org.junit.Test
import java.util.Random

class RecordTest {
    private val personRowJoinSqlRepo = PersonRowJoinSqlRepoImpl()

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
            testInsert.salesSalesperson(businessentityid = employeeRow.businessentityid, territoryid = null, salesquota = null, bonus = Defaulted.UseDefault(), commissionpct = Defaulted.UseDefault(), salesytd = Defaulted.UseDefault(), saleslastyear = Defaulted.UseDefault(), rowguid = Defaulted.UseDefault(), modifieddate = Defaulted.UseDefault(), c = c)
            personRowJoinSqlRepo.apply(c).forEach { println(it) }
        }
    }
}

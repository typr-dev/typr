package adventureworks

import adventureworks.person_row_join.PersonRowJoinSqlRepoImpl
import adventureworks.userdefined.FirstName
import org.junit.Test
import java.time.LocalDate
import java.util.Random

class RecordTest {
    private val testInsert = TestInsert(Random(0), DomainInsert)
    private val personRowJoinSqlRepo = PersonRowJoinSqlRepoImpl()

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
            testInsert.salesSalesperson(
                businessentityid = employeeRow.businessentityid,
                c = c
            )
            personRowJoinSqlRepo.apply(c).forEach { println(it) }
        }
    }
}

package adventureworks.humanresources.employee

import adventureworks.DomainInsert
import adventureworks.TestInsert
import adventureworks.WithConnection
import adventureworks.customtypes.*
import adventureworks.person.businessentity.*
import adventureworks.public.Flag
import adventureworks.userdefined.FirstName
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.Random

class EmployeeTest {
    private val testInsert = TestInsert(Random(0), DomainInsert)
    private val employeeRepo = EmployeeRepoImpl()

    @Test
    fun works() {
        WithConnection.run { c ->
            // Create person dependency using TestInsert
            val businessentityRow = testInsert.personBusinessentity(c = c)
            val personRow = testInsert.personPerson(
                businessentityid = businessentityRow.businessentityid,
                persontype = "SC",
                firstname = FirstName("firstname"),
                c = c
            )

            // Insert employee with explicit values
            val saved1 = testInsert.humanresourcesEmployee(
                businessentityid = personRow.businessentityid,
                nationalidnumber = "9912312312",
                loginid = "loginid",
                jobtitle = "jobtitle",
                birthdate = LocalDate.of(1950, 1, 1),
                maritalstatus = "M",
                gender = "F",
                hiredate = LocalDate.now().minusYears(1),
                c = c
            )

            // check field values
            val updated = employeeRepo.update(saved1.copy(gender = "M"), c)
            assertTrue(updated)

            val allEmployees = employeeRepo.selectAll(c)
            assertEquals(1, allEmployees.size)
            val saved3 = allEmployees[0]
            val byIds = employeeRepo.selectByIds(arrayOf(saved1.businessentityid, BusinessentityId(22)), c)
            assertEquals(1, byIds.size)
            assertEquals(saved3, byIds[0])
            assertEquals("M", saved3.gender)

            // delete
            employeeRepo.deleteById(saved1.businessentityid, c)

            val emptyList = employeeRepo.selectAll(c)
            assertTrue(emptyList.isEmpty())

            // Test inserting again with default values
            val inserted = testInsert.humanresourcesEmployee(
                businessentityid = personRow.businessentityid,
                nationalidnumber = "987654321",
                loginid = "adventure-works\\test2",
                jobtitle = "Test Job 2",
                birthdate = LocalDate.of(1985, 6, 15),
                maritalstatus = "M",
                gender = "F",
                hiredate = LocalDate.of(2021, 3, 1),
                c = c
            )
            assertEquals(personRow.businessentityid, inserted.businessentityid)
            // check static default values
            assertEquals(Flag(true), inserted.salariedflag)
            assertEquals(0.toShort(), inserted.vacationhours)
            assertEquals(0.toShort(), inserted.sickleavehours)
            assertEquals(Flag(true), inserted.currentflag)
            assertNotNull(inserted.rowguid)
            assertNotNull(inserted.modifieddate)
            assertEquals("/", inserted.organizationnode)
        }
    }
}

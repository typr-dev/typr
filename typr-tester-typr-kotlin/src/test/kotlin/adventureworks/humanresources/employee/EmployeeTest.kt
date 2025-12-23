package adventureworks.humanresources.employee

import adventureworks.DbNow
import adventureworks.WithConnection
import adventureworks.customtypes.*
import adventureworks.person.businessentity.*
import adventureworks.person.person.PersonRepoImpl
import adventureworks.person.person.PersonRowUnsaved
import adventureworks.public.Flag
import adventureworks.public.Name
import adventureworks.userdefined.FirstName
import org.junit.Assert.*
import org.junit.Test
import typr.data.Xml
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class EmployeeTest {
    private val employeeRepo = EmployeeRepoImpl()
    private val personRepo = PersonRepoImpl()
    private val businessentityRepo = BusinessentityRepoImpl()

    @Test
    fun works() {
        WithConnection.run { c ->
            val businessentityRow: BusinessentityRow =
                businessentityRepo.insert(
                    BusinessentityRowUnsaved(),
                    c
                )

            val personRow = personRepo.insert(
                PersonRowUnsaved(
                    businessentityid = businessentityRow.businessentityid,
                    persontype = "SC",
                    title = null,
                    firstname = FirstName("firstname"),
                    middlename = Name("middlename"),
                    lastname = Name("lastname"),
                    suffix = "suffix",
                    additionalcontactinfo = Xml("<additionalcontactinfo/>"),
                    demographics = null
                ),
                c
            )

            // setup
            val unsaved = EmployeeRowUnsaved(
                businessentityid = personRow.businessentityid,
                nationalidnumber = "9912312312",
                loginid = "loginid",
                jobtitle = "jobtitle",
                birthdate = LocalDate.of(1950, 1, 1),
                maritalstatus = "M",
                gender = "F",
                hiredate = LocalDate.now().minusYears(1),
                salariedflag = Defaulted.Provided(Flag(true)),
                vacationhours = Defaulted.Provided(1.toShort()),
                sickleavehours = Defaulted.Provided(2.toShort()),
                currentflag = Defaulted.Provided(Flag(true)),
                rowguid = Defaulted.Provided(UUID.randomUUID()),
                modifieddate = Defaulted.Provided(DbNow.localDateTime()),
                organizationnode = Defaulted.Provided("/")
            )

            // insert and round trip check
            val saved1 = employeeRepo.insert(unsaved, c)
            val saved2 = unsaved.toRow(
                salariedflagDefault = { saved1.salariedflag },
                vacationhoursDefault = { saved1.vacationhours },
                sickleavehoursDefault = { saved1.sickleavehours },
                currentflagDefault = { saved1.currentflag },
                rowguidDefault = { saved1.rowguid },
                modifieddateDefault = { saved1.modifieddate },
                organizationnodeDefault = { saved1.organizationnode }
            )
            assertEquals(saved1, saved2)

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

            // Test with default values
            val unsaved2 = EmployeeRowUnsaved(
                businessentityid = personRow.businessentityid,
                nationalidnumber = "9912312312",
                loginid = "loginid",
                jobtitle = "jobtitle",
                birthdate = LocalDate.of(1950, 1, 1),
                maritalstatus = "M",
                gender = "F",
                hiredate = LocalDate.now().minusYears(1)
            )

            // insert and check static default values
            val inserted = employeeRepo.insert(unsaved2, c)
            assertEquals(personRow.businessentityid, inserted.businessentityid)
            assertEquals(unsaved2.nationalidnumber, inserted.nationalidnumber)
            assertEquals(unsaved2.loginid, inserted.loginid)
            assertEquals(unsaved2.jobtitle, inserted.jobtitle)
            assertEquals(unsaved2.birthdate, inserted.birthdate)
            assertEquals(unsaved2.maritalstatus, inserted.maritalstatus)
            assertEquals(unsaved2.gender, inserted.gender)
            assertEquals(unsaved2.hiredate, inserted.hiredate)
            // below: these are assertions for the static default values
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

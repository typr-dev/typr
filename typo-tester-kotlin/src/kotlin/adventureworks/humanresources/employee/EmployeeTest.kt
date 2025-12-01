package adventureworks.humanresources.employee

import adventureworks.WithConnection
import adventureworks.customtypes.*
import adventureworks.person.businessentity.BusinessentityId
import adventureworks.person.businessentity.BusinessentityRepoImpl
import adventureworks.person.businessentity.BusinessentityRowUnsaved
import adventureworks.person.person.PersonRepoImpl
import adventureworks.person.person.PersonRowUnsaved
import adventureworks.public.Flag
import adventureworks.public.Name
import adventureworks.userdefined.FirstName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDate
import java.util.Optional

class EmployeeTest {
    private val employeeRepo = EmployeeRepoImpl()
    private val personRepo = PersonRepoImpl()
    private val businessentityRepo = BusinessentityRepoImpl()

    @Test
    fun works() {
        WithConnection.run { c ->
            // Create businessentity first (all fields Defaulted, no short ctor)
            val businessentityRow = businessentityRepo.insert(
                BusinessentityRowUnsaved(
                    Defaulted.UseDefault(),
                    Defaulted.UseDefault(),
                    Defaulted.UseDefault()
                ),
                c
            )

            // Create person - use named parameters + copy
            val personRow = personRepo.insert(
                PersonRowUnsaved(
                    businessentityid = businessentityRow.businessentityid,
                    persontype = "SC",
                    firstname = FirstName("firstname"),
                    lastname = Name("lastname")
                ).copy(
                    middlename = Optional.of(Name("middlename")),
                    suffix = Optional.of("suffix"),
                    additionalcontactinfo = Optional.of(TypoXml("<additionalcontactinfo/>"))
                ),
                c
            )

            // Setup employee - use named parameters + copy for specific values
            val unsaved = EmployeeRowUnsaved(
                businessentityid = personRow.businessentityid,
                nationalidnumber = "9912312312",
                loginid = "loginid",
                jobtitle = "jobtitle",
                birthdate = TypoLocalDate(LocalDate.of(1950, 1, 1)),
                maritalstatus = "M",
                gender = "F",
                hiredate = TypoLocalDate(LocalDate.now().minusYears(1))
            ).copy(
                salariedflag = Defaulted.Provided(Flag(true)),
                vacationhours = Defaulted.Provided(TypoShort(1.toShort())),
                sickleavehours = Defaulted.Provided(TypoShort(2.toShort())),
                currentflag = Defaulted.Provided(Flag(true)),
                rowguid = Defaulted.Provided(TypoUUID.randomUUID()),
                modifieddate = Defaulted.Provided(TypoLocalDateTime.now()),
                organizationnode = Defaulted.Provided(Optional.of("/"))
            )

            // Insert and round trip check
            val saved1 = employeeRepo.insert(unsaved, c)
            assertEquals(unsaved.nationalidnumber, saved1.nationalidnumber)
            assertEquals(unsaved.loginid, saved1.loginid)
            assertEquals(unsaved.jobtitle, saved1.jobtitle)

            // Check field values
            employeeRepo.update(saved1.copy(gender = "M"), c)
            val all = employeeRepo.selectAll(c)
            assertEquals(1, all.size)
            val saved3 = all[0]
            assertEquals("M", saved3.gender)

            // Select by ids
            val byIds = employeeRepo.selectByIds(arrayOf(saved1.businessentityid, BusinessentityId(22)), c)
            assertEquals(1, byIds.size)
            assertEquals(saved3, byIds[0])

            // Delete
            employeeRepo.deleteById(saved1.businessentityid, c)
            val afterDelete = employeeRepo.selectAll(c)
            assertTrue(afterDelete.isEmpty())

            // Test insert with minimal fields to verify defaults - use short ctor
            val minimalUnsaved = EmployeeRowUnsaved(
                businessentityid = personRow.businessentityid,
                nationalidnumber = "9912312313",
                loginid = "loginid2",
                jobtitle = "jobtitle2",
                birthdate = TypoLocalDate(LocalDate.of(1960, 1, 1)),
                maritalstatus = "M",
                gender = "F",
                hiredate = TypoLocalDate(LocalDate.now().minusYears(1))
            )

            val withDefaults = employeeRepo.insert(minimalUnsaved, c)

            // Verify the static default values from the database schema
            assertEquals(Flag(true), withDefaults.salariedflag)
            assertEquals(TypoShort(0.toShort()), withDefaults.vacationhours)
            assertEquals(TypoShort(0.toShort()), withDefaults.sickleavehours)
            assertEquals(Flag(true), withDefaults.currentflag)
            assertEquals(Optional.of("/"), withDefaults.organizationnode)
        }
    }
}

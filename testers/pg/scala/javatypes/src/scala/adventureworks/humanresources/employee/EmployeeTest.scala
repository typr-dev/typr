package adventureworks.humanresources.employee

import adventureworks.{DbNow, WithConnection}
import adventureworks.customtypes.Defaulted
import adventureworks.person.businessentity.{BusinessentityId, BusinessentityRepoImpl, BusinessentityRowUnsaved}
import adventureworks.person.person.{PersonRepoImpl, PersonRowUnsaved}
import adventureworks.public.{Flag, Name}
import adventureworks.userdefined.{CurrentFlag, FirstName, LastName, MiddleName, SalariedFlag}
import org.junit.Assert.*
import org.junit.Test
import dev.typr.foundations.data.Xml

import java.time.LocalDate
import java.util.{Optional, UUID}

class EmployeeTest {
  private val employeeRepo = new EmployeeRepoImpl
  private val personRepo = new PersonRepoImpl
  private val businessentityRepo = new BusinessentityRepoImpl

  @Test
  def works(): Unit = {
    WithConnection {
      val businessentityRow = businessentityRepo.insert(
        BusinessentityRowUnsaved()
      )

      val personRow = personRepo.insert(
        PersonRowUnsaved(
          businessentityid = businessentityRow.businessentityid,
          persontype = "SC",
          firstname = FirstName(Name("firstname")),
          lastname = LastName(Name("lastname"))
        ).copy(
          middlename = Optional.of(MiddleName(Name("middlename"))),
          suffix = Optional.of("suffix"),
          additionalcontactinfo = Optional.of(Xml("<additionalcontactinfo/>"))
        )
      )

      val unsaved = EmployeeRowUnsaved(
        businessentityid = personRow.businessentityid,
        nationalidnumber = "9912312312",
        loginid = "loginid",
        jobtitle = "jobtitle",
        birthdate = LocalDate.of(1950, 1, 1),
        maritalstatus = "M",
        gender = "F",
        hiredate = LocalDate.now().minusYears(1)
      ).copy(
        salariedflag = Defaulted.Provided(SalariedFlag(Flag(true))),
        vacationhours = Defaulted.Provided(java.lang.Short.valueOf(1.toShort)),
        sickleavehours = Defaulted.Provided(java.lang.Short.valueOf(2.toShort)),
        currentflag = Defaulted.Provided(CurrentFlag(Flag(true))),
        rowguid = Defaulted.Provided(UUID.randomUUID()),
        modifieddate = Defaulted.Provided(DbNow.localDateTime()),
        organizationnode = Defaulted.Provided(Optional.of("/"))
      )

      val saved1 = employeeRepo.insert(unsaved)
      assertEquals(unsaved.nationalidnumber, saved1.nationalidnumber)
      assertEquals(unsaved.loginid, saved1.loginid)
      assertEquals(unsaved.jobtitle, saved1.jobtitle)

      val _ = employeeRepo.update(saved1.copy(gender = "M"))
      val all = employeeRepo.selectAll
      assertEquals(1, all.size)
      val saved3 = all.get(0)
      assertEquals("M", saved3.gender)

      val byIds = employeeRepo.selectByIds(Array(saved1.businessentityid, BusinessentityId(22)))
      assertEquals(1, byIds.size)
      assertEquals(saved3, byIds.get(0))

      val _ = employeeRepo.deleteById(saved1.businessentityid)
      val afterDelete = employeeRepo.selectAll
      assertTrue(afterDelete.isEmpty)

      val minimalUnsaved = EmployeeRowUnsaved(
        businessentityid = personRow.businessentityid,
        nationalidnumber = "9912312313",
        loginid = "loginid2",
        jobtitle = "jobtitle2",
        birthdate = LocalDate.of(1960, 1, 1),
        maritalstatus = "M",
        gender = "F",
        hiredate = LocalDate.now().minusYears(1)
      )

      val withDefaults = employeeRepo.insert(minimalUnsaved)

      assertEquals(SalariedFlag(Flag(true)), withDefaults.salariedflag)
      assertEquals(java.lang.Short.valueOf(0.toShort), withDefaults.vacationhours)
      assertEquals(java.lang.Short.valueOf(0.toShort), withDefaults.sickleavehours)
      assertEquals(CurrentFlag(Flag(true)), withDefaults.currentflag)
      assertEquals(Optional.of("/"), withDefaults.organizationnode)
    }
  }
}

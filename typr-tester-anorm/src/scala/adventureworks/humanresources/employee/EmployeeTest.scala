package adventureworks.humanresources.employee

import adventureworks.customtypes.*
import adventureworks.person.businessentity.{BusinessentityId, BusinessentityRepoImpl, BusinessentityRow, BusinessentityRowUnsaved}
import adventureworks.person.person.{PersonRepoImpl, PersonRowUnsaved}
import adventureworks.public.{Flag, Name}
import adventureworks.userdefined.FirstName
import adventureworks.withConnection
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.funsuite.AnyFunSuite

import java.time.LocalDate
import scala.annotation.nowarn

class EmployeeTest extends AnyFunSuite with TypeCheckedTripleEquals {
  val employeeRepo = new EmployeeRepoImpl
  val personRepo = new PersonRepoImpl
  val businessentityRepo = new BusinessentityRepoImpl

  test("works") {
    withConnection { implicit c =>
      val businessentityRow: BusinessentityRow =
        businessentityRepo.insert(
          BusinessentityRowUnsaved(
          )
        )

      val personRow = personRepo.insert(
        PersonRowUnsaved(
          businessentityid = businessentityRow.businessentityid,
          persontype = "SC",
          title = None,
          firstname = FirstName("firstname"),
          middlename = Some(Name("middlename")),
          lastname = Name("lastname"),
          suffix = Some("suffix"),
          additionalcontactinfo = Some(TypoXml("<additionalcontactinfo/>")),
          demographics = None
        )
      )
      // setup
      val unsaved = EmployeeRowUnsaved(
        businessentityid = personRow.businessentityid,
        nationalidnumber = "9912312312",
        loginid = "loginid",
        jobtitle = "jobtitle",
        birthdate = TypoLocalDate(LocalDate.of(1950, 1, 1)),
        maritalstatus = "M",
        gender = "F",
        hiredate = TypoLocalDate(LocalDate.now().minusYears(1)),
        salariedflag = Defaulted.Provided(Flag(true)),
        vacationhours = Defaulted.Provided(TypoShort(1)),
        sickleavehours = Defaulted.Provided(TypoShort(2)),
        currentflag = Defaulted.Provided(Flag(true)),
        rowguid = Defaulted.Provided(TypoUUID.randomUUID),
        modifieddate = Defaulted.Provided(TypoLocalDateTime.now),
        organizationnode = Defaulted.Provided(Some("/"))
      )

      // insert and round trip check
      val saved1 = employeeRepo.insert(unsaved)
      val saved2 = unsaved.toRow(???, ???, ???, ???, ???, ???, ???)
      assert(saved1 === saved2): @nowarn

      // check field values
      val updatedOpt = employeeRepo.update(saved1.copy(gender = "M"))
      assert(updatedOpt.isDefined): @nowarn
      assert(updatedOpt.get.gender == "M"): @nowarn

      val List(saved3) = employeeRepo.selectAll: @unchecked
      val List(`saved3`) = employeeRepo.selectByIds(Array(saved1.businessentityid, BusinessentityId(22))): @unchecked
      assert(saved3.gender == "M"): @nowarn

      // delete
      employeeRepo.deleteById(saved1.businessentityid): @nowarn

      val List() = employeeRepo.selectAll: @unchecked

      {
        val unsaved = EmployeeRowUnsaved(
          businessentityid = personRow.businessentityid,
          nationalidnumber = "9912312312",
          loginid = "loginid",
          jobtitle = "jobtitle",
          birthdate = TypoLocalDate(LocalDate.of(1950, 1, 1)),
          maritalstatus = "M",
          gender = "F",
          hiredate = TypoLocalDate(LocalDate.now().minusYears(1))
        )

        // insert and round trip check
        val EmployeeRow(
          personRow.businessentityid,
          unsaved.nationalidnumber,
          unsaved.loginid,
          unsaved.jobtitle,
          unsaved.birthdate,
          unsaved.maritalstatus,
          unsaved.gender,
          unsaved.hiredate,
          // below: these are assertions for the static default values
          Flag(true),
          TypoShort(0),
          TypoShort(0),
          Flag(true),
          _,
          _,
          Some("/")
        ) = employeeRepo.insert(unsaved): @unchecked
      }
      succeed
    }
  }
}

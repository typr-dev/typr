package adventureworks

import adventureworks.customtypes.*
import adventureworks.person_row_join.PersonRowJoinSqlRepoImpl
import adventureworks.userdefined.FirstName
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.funsuite.AnyFunSuite

import scala.annotation.nowarn
import scala.util.Random

class RecordTest extends AnyFunSuite with TypeCheckedTripleEquals {
  val personRowJoinSqlRepo = new PersonRowJoinSqlRepoImpl

  test("works") {

    withConnection { implicit c =>
      val testInsert = new TestInsert(new Random(0), DomainInsert)
      val businessentityRow = testInsert.personBusinessentity()
      val personRow = testInsert.personPerson(businessentityRow.businessentityid, persontype = "EM", FirstName("a"))
      testInsert.personEmailaddress(personRow.businessentityid, Some("a@b.c")): @nowarn
      val employeeRow =
        testInsert.humanresourcesEmployee(personRow.businessentityid, gender = "M", maritalstatus = "M", birthdate = TypoLocalDate("1998-01-01"), hiredate = TypoLocalDate("1997-01-01"))
      testInsert.salesSalesperson(employeeRow.businessentityid): @nowarn
      personRowJoinSqlRepo.apply foreach println
    }
  }
}

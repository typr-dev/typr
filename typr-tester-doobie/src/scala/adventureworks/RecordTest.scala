package adventureworks

import adventureworks.customtypes.*
import adventureworks.person_row_join.PersonRowJoinSqlRepoImpl
import adventureworks.userdefined.FirstName
import doobie.free.connection.delay
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.funsuite.AnyFunSuite

import scala.util.Random

class RecordTest extends AnyFunSuite with TypeCheckedTripleEquals {
  val personRowJoinSqlRepo = new PersonRowJoinSqlRepoImpl

  test("works") {
    withConnection {
      val testInsert = new TestInsert(new Random(0), DomainInsert)
      for {
        businessentityRow <- testInsert.personBusinessentity()
        personRow <- testInsert.personPerson(businessentityRow.businessentityid, persontype = "EM", FirstName("a"))
        _ <- testInsert.personEmailaddress(personRow.businessentityid, Some("a@b.c"))
        employeeRow <- testInsert.humanresourcesEmployee(personRow.businessentityid, gender = "M", maritalstatus = "M", birthdate = TypoLocalDate("1998-01-01"), hiredate = TypoLocalDate("1997-01-01"))
        _ <- testInsert.salesSalesperson(employeeRow.businessentityid)
        results <- personRowJoinSqlRepo.apply.compile.toList
        _ <- delay(results.foreach(println))
      } yield ()
    }
  }
}

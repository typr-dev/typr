package adventureworks

import adventureworks.customtypes.*
import adventureworks.humanresources.employee.EmployeeRepoImpl
import adventureworks.person.businessentity.BusinessentityRepoImpl
import adventureworks.person.emailaddress.EmailaddressRepoImpl
import adventureworks.person.person.PersonRepoImpl
import adventureworks.sales.salesperson.SalespersonRepoImpl
import adventureworks.userdefined.FirstName
import zio.ZIO

import scala.util.Random

class DSLTest extends SnapshotTest {
  val businessentityRepoImpl = new BusinessentityRepoImpl
  val personRepoImpl = new PersonRepoImpl
  val employeeRepoImpl = new EmployeeRepoImpl()
  val salespersonRepoImpl = new SalespersonRepoImpl
  val emailaddressRepoImpl = new EmailaddressRepoImpl

  test("works") {
    withConnection {
      val testInsert = new TestInsert(new Random(0), DomainInsert)
      for {
        businessentityRow <- testInsert.personBusinessentity()
        personRow <- testInsert.personPerson(businessentityRow.businessentityid, persontype = "EM", FirstName("a"))
        _ <- testInsert.personEmailaddress(personRow.businessentityid, Some("a@b.c"))
        employeeRow <- testInsert.humanresourcesEmployee(personRow.businessentityid, gender = "M", maritalstatus = "M", birthdate = TypoLocalDate("1998-01-01"), hiredate = TypoLocalDate("1997-01-01"))
        salespersonRow <- testInsert.salesSalesperson(employeeRow.businessentityid)
        q = salespersonRepoImpl.select
          .where(_.rowguid === salespersonRow.rowguid)
          .joinFk(_.fkHumanresourcesEmployee)(employeeRepoImpl.select)
          .joinFk { case (_, e) => e.fkPersonPerson }(personRepoImpl.select)
          .joinFk { case ((_, _), p) => p.fkBusinessentity }(businessentityRepoImpl.select)
          .join(emailaddressRepoImpl.select.orderBy(_.rowguid.asc))
          .on { case ((_, b), email) => email.businessentityid === b.businessentityid }
          .joinOn(salespersonRepoImpl.select) { case ((_, e), s2) =>
            e.businessentityid.underlying === s2.businessentityid.underlying
          }
        doubled = q.join(q).on { case (((_, e1), _), ((_, e2), _)) => e1.businessentityid === e2.businessentityid }
        doubledRes <- doubled.toChunk
        _ <- ZIO.succeed(doubledRes.foreach(println))
        _ <- doubled.count.map(v => assert(v == 1))
        _ <- ZIO.succeed(compareFragment("doubled")(doubled.sql))
      } yield ()
    }
  }
}

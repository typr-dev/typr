package adventureworks

import adventureworks.customtypes.*
import adventureworks.humanresources.employee.EmployeeRepoImpl
import adventureworks.person.businessentity.BusinessentityRepoImpl
import adventureworks.person.emailaddress.EmailaddressRepoImpl
import adventureworks.person.person.PersonRepoImpl
import adventureworks.sales.salesperson.SalespersonRepoImpl
import adventureworks.userdefined.FirstName
import typr.dsl.SqlExpr._

import scala.annotation.nowarn
import scala.util.Random

class DSLTest extends SnapshotTest {
  val businessentityRepoImpl = new BusinessentityRepoImpl
  val personRepoImpl = new PersonRepoImpl
  val employeeRepoImpl = new EmployeeRepoImpl()
  val salespersonRepoImpl = new SalespersonRepoImpl
  val emailaddressRepoImpl = new EmailaddressRepoImpl

  test("works") {
    withConnection { implicit c =>
      val testInsert = new TestInsert(new Random(0), DomainInsert)
      val businessentityRow = testInsert.personBusinessentity()
      val personRow = testInsert.personPerson(businessentityRow.businessentityid, persontype = "EM", FirstName("a"))
      testInsert.personEmailaddress(personRow.businessentityid, Some("a@b.c")): @nowarn
      val employeeRow =
        testInsert.humanresourcesEmployee(personRow.businessentityid, gender = "M", maritalstatus = "M", birthdate = TypoLocalDate("1998-01-01"), hiredate = TypoLocalDate("1997-01-01"))
      val salespersonRow = testInsert.salesSalesperson(employeeRow.businessentityid)

      def q = salespersonRepoImpl.select
        .where(_.rowguid === salespersonRow.rowguid)
        .joinFk(_.fkHumanresourcesEmployee)(employeeRepoImpl.select)
        .joinFk { case (_, e) => e.fkPersonPerson }(personRepoImpl.select)
        .joinFk { case ((_, _), p) => p.fkBusinessentity }(businessentityRepoImpl.select)
        .join(emailaddressRepoImpl.select.orderBy(_.rowguid.asc))
        .on { case ((_, b), email) => email.businessentityid === b.businessentityid }
        .joinOn(salespersonRepoImpl.select) { case ((_, e), s2) =>
          e.businessentityid.underlying === s2.businessentityid.underlying
        }

      val doubled = q.join(q).on { case (((_, e1), _), ((_, e2), _)) => e1.businessentityid === e2.businessentityid }

      doubled.toList.foreach(println)
      assert(doubled.count == 1): @nowarn

      compareFragment("doubled")(doubled.sql)
    }
  }
}

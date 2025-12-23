package adventureworks.person_detail

import adventureworks.customtypes.TypoLocalDateTime
import adventureworks.person.businessentity.BusinessentityId
import adventureworks.withConnection
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.funsuite.AnyFunSuite

class PersonDetailTest extends AnyFunSuite with TypeCheckedTripleEquals {
  val personDetailSqlRepo = new PersonDetailSqlRepoImpl
  test("timestamp works") {
    withConnection {
      personDetailSqlRepo(BusinessentityId(1), TypoLocalDateTime.now).runCollect
    }
  }
}

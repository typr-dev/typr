package adventureworks.person_detail

import adventureworks.{DbNow, WithConnection}
import adventureworks.person.businessentity.BusinessentityId
import org.junit.Test

class PersonDetailTest {
  private val personDetailSqlRepo = new PersonDetailSqlRepoImpl

  @Test
  def timestampWorks(): Unit = {
    WithConnection {
      val _ = personDetailSqlRepo.apply(BusinessentityId(1), DbNow.localDateTime())
    }
  }
}

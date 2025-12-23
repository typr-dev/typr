package adventureworks.update_person_returning

import adventureworks.{DbNow, WithConnection}
import org.junit.Test

class UpdatePersonReturningSqlRepoTest {
  private val updatePersonReturningSqlRepo = new UpdatePersonReturningSqlRepoImpl

  @Test
  def timestampWorks(): Unit = {
    WithConnection {
      val _ = updatePersonReturningSqlRepo.apply(Some("1"), Some(DbNow.localDateTime()))
    }
  }
}

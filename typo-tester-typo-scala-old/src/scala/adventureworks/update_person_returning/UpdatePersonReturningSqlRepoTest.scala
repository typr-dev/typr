package adventureworks.update_person_returning

import adventureworks.{DbNow, WithConnection}
import org.junit.Test

import java.util.Optional

class UpdatePersonReturningSqlRepoTest {
  private val updatePersonReturningSqlRepo = new UpdatePersonReturningSqlRepoImpl

  @Test
  def timestampWorks(): Unit = {
    WithConnection {
      val _ = updatePersonReturningSqlRepo.apply(Optional.of("1"), Optional.of(DbNow.localDateTime()))
    }
  }
}

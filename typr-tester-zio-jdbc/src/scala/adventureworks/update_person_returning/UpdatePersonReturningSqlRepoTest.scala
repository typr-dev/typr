package adventureworks.update_person_returning

import adventureworks.customtypes.TypoLocalDateTime
import adventureworks.withConnection
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.funsuite.AnyFunSuite

class UpdatePersonReturningSqlRepoTest extends AnyFunSuite with TypeCheckedTripleEquals {
  val updatePersonReturningSqlRepo = new UpdatePersonReturningSqlRepoImpl

  test("timestamp works") {
    withConnection {
      for {
        _ <- updatePersonReturningSqlRepo(Some("1"), Some(TypoLocalDateTime.now)).runCollect
      } yield ()
    }
  }
}

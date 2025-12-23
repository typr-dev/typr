package adventureworks

import adventureworks.public.title.*
import adventureworks.public.title_domain.*
import adventureworks.public.titledperson.*
import org.scalatest.funsuite.AnyFunSuite
import typr.dsl.ToTupleOps

import scala.util.Random

class OpenEnumTest extends AnyFunSuite with JsonEquals {
  val titleRepo = new TitleRepoImpl
  val titleDomainRepo = new TitleDomainRepoImpl
  val titledPersonRepo = new TitledpersonRepoImpl
  val testInsert = new TestInsert(new Random(0), DomainInsert)

  test("works") {
    withConnection {
      for {
        john <- testInsert.publicTitledperson(TitleDomainId.dr, TitleId.dr, "John")
        found <- titledPersonRepo.select
          .joinFk(_.fkTitle)(titleRepo.select.where(_.code.in(Array(TitleId.dr))))
          .joinFk(_._1.fkTitleDomain)(titleDomainRepo.select.where(_.code.in(Array(TitleDomainId.dr))))
          .where { case ((tp, _), _) => tp.name === "John" }
          .toList
          .map(_.headOption)
      } yield {
        val expected: Option[((TitledpersonRow, TitleRow), TitleDomainRow)] =
          Option(john ~ TitleRow(TitleId.dr) ~ TitleDomainRow(TitleDomainId.dr))

        assertJsonEquals(found, expected)
      }
    }
  }
}

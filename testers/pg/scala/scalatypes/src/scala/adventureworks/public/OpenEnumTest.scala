package adventureworks.public

import adventureworks.public.title.*
import adventureworks.public.title_domain.*
import adventureworks.public.titledperson.*
import adventureworks.{DomainInsertImpl, TestInsert, WithConnection}
import org.junit.Assert.*
import org.junit.Test

import java.util.Random

class OpenEnumTest {
  private val titleRepo = TitleRepoImpl()
  private val titleDomainRepo = TitleDomainRepoImpl()
  private val titledPersonRepo = TitledpersonRepoImpl()

  @Test
  def testOpenEnumPatternMatching(): Unit = {
    WithConnection {
      val testInsert = new TestInsert(new Random(0), new DomainInsertImpl())
      val john = testInsert.publicTitledperson(TitleDomainId.dr, TitleId.dr, "John")

      // Verify pattern matching works with open enums
      val titleValue = john.title
      val titleString = titleValue match {
        case TitleId.dr               => "Known: dr"
        case TitleId.mr               => "Known: mr"
        case TitleId.ms               => "Known: ms"
        case TitleId.phd              => "Known: phd"
        case unknown: TitleId.Unknown => s"Unknown: ${unknown.value}"
      }
      assertEquals("Known: dr", titleString)

      // Test DSL query with joinFk chain
      val found = titledPersonRepo.select
        .joinFk(tp => tp.fkTitle, titleRepo.select.where(t => t.code.in(Array[Object](TitleId.dr), TitleId.pgType)))
        .joinFk(tp_t => tp_t._1.fkTitleDomain, titleDomainRepo.select.where(td => td.code.in(Array[Object](TitleDomainId.dr), TitleDomainId.pgType)))
        .where(tp_t_td => tp_t_td._1._1.name.isEqual("John"))
        .toList
        .headOption

      assertTrue(found.isDefined)
      val result = found.get
      assertEquals(john, result._1._1)
      assertEquals(TitleRow(TitleId.dr), result._1._2)
      assertEquals(TitleDomainRow(TitleDomainId.dr), result._2)
    }
  }
}

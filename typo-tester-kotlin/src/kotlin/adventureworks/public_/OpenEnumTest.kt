package adventureworks.public_

import adventureworks.DomainInsertImpl
import adventureworks.TestInsert
import adventureworks.WithConnection
import adventureworks.public.title.*
import adventureworks.public.title_domain.*
import adventureworks.public.titledperson.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.Random

/**
 * Tests for open enum types (sealed interface with Known enum + Unknown record).
 */
class OpenEnumTest {
    private val titleRepo = TitleRepoImpl()
    private val titleDomainRepo = TitleDomainRepoImpl()
    private val titledPersonRepo = TitledpersonRepoImpl()

    @Test
    fun works() {
        WithConnection.run { c ->
            val testInsert = TestInsert(Random(0), DomainInsertImpl())
            // publicTitledperson(name, titleShort, title, c)
            val john = testInsert.publicTitledperson("John", TitleDomainId.Known.dr, TitleId.Known.dr, c)

            // DSL query with joinFk chain - DSL fields use method calls with ()
            val found = titledPersonRepo.select()
                .joinFk({ tp -> tp.fkTitle() }, titleRepo.select().where { t -> t.code().`in`(TitleId.Known.dr) })
                .joinFk({ tp_t -> tp_t._1().fkTitleDomain() }, titleDomainRepo.select().where { td -> td.code().`in`(TitleDomainId.Known.dr) })
                .where { tp_t_td -> tp_t_td._1()._1().name().isEqual("John") }
                .toList(c)
                .firstOrNull()

            assertNotNull(found)
            val result = found!!
            // result is ((TitledpersonRow, TitleRow), TitleDomainRow)
            assertEquals(john, result._1()._1())
            assertEquals(TitleRow(TitleId.Known.dr), result._1()._2())
            assertEquals(TitleDomainRow(TitleDomainId.Known.dr), result._2())
        }
    }

    private fun testDsl(
        titledPersonRepo: TitledpersonRepo,
        titleRepo: TitleRepo,
        titleDomainRepo: TitleDomainRepo
    ) {
        WithConnection.run { c ->
            // Insert the titled person with dr/dr/John
            val john = titledPersonRepo.insert(
                TitledpersonRow(TitleDomainId.Known.dr, TitleId.Known.dr, "John"),
                c
            )

            // DSL query with joinFk chain
            val found = titledPersonRepo.select()
                .joinFk({ tp -> tp.fkTitle() }, titleRepo.select().where { t -> t.code().`in`(TitleId.Known.dr) })
                .joinFk({ tp_t -> tp_t._1().fkTitleDomain() }, titleDomainRepo.select().where { td -> td.code().`in`(TitleDomainId.Known.dr) })
                .where { tp_t_td -> tp_t_td._1()._1().name().isEqual("John") }
                .toList(c)
                .firstOrNull()

            assertNotNull(found)
            val result = found!!
            assertEquals(john, result._1()._1())
            assertEquals(TitleRow(TitleId.Known.dr), result._1()._2())
            assertEquals(TitleDomainRow(TitleDomainId.Known.dr), result._2())

            // Verify pattern matching works with open enums
            val titleValue = john.title
            val titleString = when (titleValue) {
                is TitleId.Known -> "Known: ${titleValue.value}"
                is TitleId.Unknown -> "Unknown: ${titleValue.value}"
            }
            assertEquals("Known: dr", titleString)
        }
    }

    @Test
    fun pg() {
        testDsl(TitledpersonRepoImpl(), TitleRepoImpl(), TitleDomainRepoImpl())
    }
}

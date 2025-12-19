package adventureworks.public

import adventureworks.WithConnection
import adventureworks.public.title.*
import adventureworks.public.title_domain.*
import adventureworks.public.titledperson.*
import org.junit.Assert.*
import org.junit.Test

class OpenEnumTest {
    private val titleRepo = TitleRepoImpl()
    private val titleDomainRepo = TitleDomainRepoImpl()
    private val titledPersonRepo = TitledpersonRepoImpl()

    @Test
    fun testOpenEnumPatternMatching() {
        WithConnection.run { c ->
            val john = titledPersonRepo.insert(
                TitledpersonRow(
                    titleShort = TitleDomainId.Known.dr,
                    title = TitleId.Known.dr,
                    name = "John"
                ),
                c
            )

            // Verify pattern matching works with open enums
            val titleValue = john.title
            val titleString = when (titleValue) {
                TitleId.Known.dr -> "Known: dr"
                TitleId.Known.mr -> "Known: mr"
                TitleId.Known.ms -> "Known: ms"
                TitleId.Known.phd -> "Known: phd"
                is TitleId.Unknown -> "Unknown: ${titleValue.value}"
            }
            assertEquals("Known: dr", titleString)

            // Test DSL query with joinFk chain
            val found = titledPersonRepo.select()
                .joinFk({ tp -> tp.fkTitle() }, titleRepo.select().where { t -> t.code().`in`(arrayOf(TitleId.Known.dr), TitleId.pgType) })
                .joinFk({ tp_t -> tp_t._1().fkTitleDomain() }, titleDomainRepo.select().where { td -> td.code().`in`(arrayOf(TitleDomainId.Known.dr), TitleDomainId.pgType) })
                .where { tp_t_td -> tp_t_td._1()._1().name().isEqual("John") }
                .toList(c)
                .firstOrNull()

            assertNotNull(found)
            assertEquals(john, found?._1()?._1())
            assertEquals(TitleRow(TitleId.Known.dr), found?._1()?._2())
            assertEquals(TitleDomainRow(TitleDomainId.Known.dr), found?._2())
        }
    }
}

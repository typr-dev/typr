package adventureworks

import adventureworks.public.only_pk_columns.OnlyPkColumnsRepoImpl
import adventureworks.public.only_pk_columns.OnlyPkColumnsRow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class UpsertTwiceTest {
    private val onlyPkColumnsRepo = OnlyPkColumnsRepoImpl()

    @Test
    fun secondUpsertShouldNotError() {
        val row = OnlyPkColumnsRow("the answer is", 42)
        WithConnection.run { c ->
            assertEquals(onlyPkColumnsRepo.upsert(row, c), onlyPkColumnsRepo.upsert(row, c))
        }
    }
}

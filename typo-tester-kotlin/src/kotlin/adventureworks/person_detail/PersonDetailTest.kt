package adventureworks.person_detail

import adventureworks.WithConnection
import adventureworks.customtypes.TypoLocalDateTime
import adventureworks.person.businessentity.BusinessentityId
import org.junit.jupiter.api.Test

class PersonDetailTest {
    private val personDetailSqlRepo = PersonDetailSqlRepoImpl()

    @Test
    fun timestampWorks() {
        WithConnection.run { c ->
            personDetailSqlRepo.apply(BusinessentityId(1), TypoLocalDateTime.now(), c)
        }
    }
}

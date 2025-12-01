package adventureworks.update_person_returning

import adventureworks.WithConnection
import adventureworks.customtypes.TypoLocalDateTime
import org.junit.jupiter.api.Test
import java.util.Optional

class UpdatePersonReturningSqlRepoTest {
    private val updatePersonReturningSqlRepo = UpdatePersonReturningSqlRepoImpl()

    @Test
    fun timestampWorks() {
        WithConnection.run { c ->
            updatePersonReturningSqlRepo.apply(
                Optional.of("1"),
                Optional.of(TypoLocalDateTime.now()),
                c
            )
        }
    }
}

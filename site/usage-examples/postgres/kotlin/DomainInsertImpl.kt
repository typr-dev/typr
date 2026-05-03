package showcase

import dev.typr.foundations.internal.RandomHelper
import java.math.BigDecimal
import java.util.Random
import showcase.showcase.EmailAddress
import showcase.showcase.Percentage
import showcase.showcase.PhoneNumber
import showcase.showcase.PositiveAmount
import kotlin.math.abs

class DomainInsertImpl : TestDomainInsert {
    override fun showcaseEmailAddress(random: Random): EmailAddress =
        EmailAddress(RandomHelper.alphanumeric(random, 8) + "@example.com")

    override fun showcasePositiveAmount(random: Random): PositiveAmount =
        PositiveAmount(BigDecimal.valueOf(abs(random.nextDouble() * 10000) + 1))

    override fun showcasePhoneNumber(random: Random): PhoneNumber =
        PhoneNumber("+1-555-${random.nextInt(1000)}-${random.nextInt(10000)}")

    override fun showcasePercentage(random: Random): Percentage =
        Percentage(BigDecimal.valueOf(random.nextDouble() * 100))
}

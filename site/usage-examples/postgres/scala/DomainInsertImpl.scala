package showcase

import scala.util.Random
import showcase.showcase.EmailAddress
import showcase.showcase.Percentage
import showcase.showcase.PhoneNumber
import showcase.showcase.PositiveAmount

object DomainInsertImpl extends TestDomainInsert:
  override def showcaseEmailAddress(random: Random): EmailAddress =
    EmailAddress(random.alphanumeric.take(8).mkString + "@example.com")

  override def showcasePositiveAmount(random: Random): PositiveAmount =
    PositiveAmount(BigDecimal(math.abs(random.nextDouble() * 10000) + 1))

  override def showcasePhoneNumber(random: Random): PhoneNumber =
    PhoneNumber(s"+1-555-${random.nextInt(1000)}-${random.nextInt(10000)}")

  override def showcasePercentage(random: Random): Percentage =
    Percentage(BigDecimal(random.nextDouble() * 100))

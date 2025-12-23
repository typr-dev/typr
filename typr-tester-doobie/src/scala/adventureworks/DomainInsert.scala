package adventureworks

import adventureworks.public.*

import scala.util.Random

object DomainInsert extends TestDomainInsert {
  override def publicAccountNumber(random: Random): AccountNumber = AccountNumber(random.nextString(10))
  override def publicFlag(random: Random): Flag = Flag(random.nextBoolean())
  override def publicMydomain(random: Random): Mydomain = Mydomain(random.nextString(10))
  override def publicName(random: Random): Name = Name(random.nextString(10))
  override def publicNameStyle(random: Random): NameStyle = NameStyle(random.nextBoolean())
  override def publicPhone(random: Random): Phone = Phone(random.nextString(10))
  override def publicShortText(random: Random): ShortText = ShortText(random.nextString(10))
  override def publicOrderNumber(random: Random): OrderNumber = OrderNumber(random.nextString(10))
}

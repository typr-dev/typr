package adventureworks

import adventureworks.public.*

import scala.util.Random

class DomainInsertImpl extends TestDomainInsert {
  private def alphanumeric(random: Random, length: Int): String =
    random.alphanumeric.take(length).mkString

  override def publicFlag(random: Random): Flag =
    Flag(random.nextBoolean())

  override def publicMydomain(random: Random): Mydomain =
    Mydomain(alphanumeric(random, 10))

  override def publicName(random: Random): Name =
    Name(alphanumeric(random, 10))

  override def publicNameStyle(random: Random): NameStyle =
    NameStyle(random.nextBoolean())

  override def publicShortText(random: Random): ShortText =
    ShortText(alphanumeric(random, 10))
}

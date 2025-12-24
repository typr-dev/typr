package adventureworks

import adventureworks.public.*
import typr.runtime.internal.RandomHelper

import java.util.Random

class DomainInsertImpl extends TestDomainInsert {
  override def publicFlag(random: Random): Flag =
    Flag(random.nextBoolean())

  override def publicMydomain(random: Random): Mydomain =
    Mydomain(RandomHelper.alphanumeric(random, 10))

  override def publicName(random: Random): Name =
    Name(RandomHelper.alphanumeric(random, 10))

  override def publicNameStyle(random: Random): NameStyle =
    NameStyle(random.nextBoolean())

  override def publicShortText(random: Random): ShortText =
    ShortText(RandomHelper.alphanumeric(random, 10))
}

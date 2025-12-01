package adventureworks

import adventureworks.public.*
import typo.runtime.internal.RandomHelper
import java.util.Random

class DomainInsertImpl : TestDomainInsert {
    override fun publicAccountNumber(random: Random): AccountNumber {
        return AccountNumber(RandomHelper.alphanumeric(random, 10))
    }

    override fun publicFlag(random: Random): Flag {
        return Flag(random.nextBoolean())
    }

    override fun publicMydomain(random: Random): Mydomain {
        return Mydomain(RandomHelper.alphanumeric(random, 10))
    }

    override fun publicName(random: Random): Name {
        return Name(RandomHelper.alphanumeric(random, 10))
    }

    override fun publicNameStyle(random: Random): NameStyle {
        return NameStyle(random.nextBoolean())
    }

    override fun publicPhone(random: Random): Phone {
        return Phone(RandomHelper.alphanumeric(random, 10))
    }

    override fun publicShortText(random: Random): ShortText {
        return ShortText(RandomHelper.alphanumeric(random, 10))
    }

    override fun publicOrderNumber(random: Random): OrderNumber {
        return OrderNumber(RandomHelper.alphanumeric(random, 10))
    }
}

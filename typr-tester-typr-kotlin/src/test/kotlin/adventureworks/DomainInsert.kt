package adventureworks

import adventureworks.public.*
import java.util.Random

object DomainInsert : TestDomainInsert {
    override fun publicAccountNumber(random: Random): AccountNumber = AccountNumber(randomString(random, 10))
    override fun publicFlag(random: Random): Flag = Flag(random.nextBoolean())
    override fun publicMydomain(random: Random): Mydomain = Mydomain(randomString(random, 10))
    override fun publicName(random: Random): Name = Name(randomString(random, 10))
    override fun publicNameStyle(random: Random): NameStyle = NameStyle(random.nextBoolean())
    override fun publicPhone(random: Random): Phone = Phone(randomString(random, 10))
    override fun publicShortText(random: Random): ShortText = ShortText(randomString(random, 10))
    override fun publicOrderNumber(random: Random): OrderNumber = OrderNumber(randomString(random, 10))

    private fun randomString(random: Random, length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length)
            .map { chars[random.nextInt(chars.length)] }
            .joinToString("")
    }
}

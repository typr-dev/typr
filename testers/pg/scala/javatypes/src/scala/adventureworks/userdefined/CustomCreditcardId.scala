package adventureworks.userdefined

import dev.typr.foundations.Bijection
import dev.typr.foundations.{PgText, PgTypes}

/** Type for the primary key of table `sales.creditcard` */
case class CustomCreditcardId(value: Int) extends AnyVal

object CustomCreditcardId {
  given bijection: Bijection[CustomCreditcardId, Int] = Bijection.of[CustomCreditcardId, Int](_.value, CustomCreditcardId.apply)
  given dbType: dev.typr.foundations.DbType[CustomCreditcardId] = PgTypes.int4.to(Bijection.of(CustomCreditcardId.apply, _.value))
  given pgText: PgText[CustomCreditcardId] = new PgText[CustomCreditcardId] {
    override def unsafeEncode(v: CustomCreditcardId, sb: java.lang.StringBuilder): Unit = PgText.textInteger.unsafeEncode(v.value, sb)
    override def unsafeArrayEncode(v: CustomCreditcardId, sb: java.lang.StringBuilder): Unit = PgText.textInteger.unsafeArrayEncode(v.value, sb)
  }
}

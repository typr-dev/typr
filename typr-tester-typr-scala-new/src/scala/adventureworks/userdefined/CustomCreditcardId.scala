package adventureworks.userdefined

import typr.dsl.Bijection
import typr.runtime.{PgText, PgTypes}

/** Type for the primary key of table `sales.creditcard` */
case class CustomCreditcardId(value: Int) extends AnyVal

object CustomCreditcardId {
  given bijection: Bijection[CustomCreditcardId, Int] = Bijection.apply[CustomCreditcardId, Int](_.value)(CustomCreditcardId.apply)
  given pgType: typr.runtime.PgType[CustomCreditcardId] = PgTypes.int4.bimap(CustomCreditcardId.apply, _.value)
  given pgTypeArray: typr.runtime.PgType[Array[CustomCreditcardId]] = PgTypes.int4Array.bimap(xs => xs.map(CustomCreditcardId.apply), xs => xs.map(_.value))
  given pgText: PgText[CustomCreditcardId] = new PgText[CustomCreditcardId] {
    override def unsafeEncode(v: CustomCreditcardId, sb: java.lang.StringBuilder): Unit = PgText.textInteger.unsafeEncode(v.value, sb)
    override def unsafeArrayEncode(v: CustomCreditcardId, sb: java.lang.StringBuilder): Unit = PgText.textInteger.unsafeArrayEncode(v.value, sb)
  }
}

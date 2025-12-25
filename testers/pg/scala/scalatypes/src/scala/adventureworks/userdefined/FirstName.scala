package adventureworks.userdefined

import dev.typr.foundations.scala.Bijection
import dev.typr.foundations.{PgText, PgTypes}

case class FirstName(value: String) extends AnyVal

object FirstName {
  given bijection: Bijection[FirstName, String] = Bijection.apply[FirstName, String](_.value)(FirstName.apply)
  given pgType: dev.typr.foundations.PgType[FirstName] = PgTypes.text.bimap(FirstName.apply, _.value)
  given pgText: PgText[FirstName] = new PgText[FirstName] {
    override def unsafeEncode(v: FirstName, sb: java.lang.StringBuilder): Unit = PgText.textString.unsafeEncode(v.value, sb)
    override def unsafeArrayEncode(v: FirstName, sb: java.lang.StringBuilder): Unit = PgText.textString.unsafeArrayEncode(v.value, sb)
  }
}

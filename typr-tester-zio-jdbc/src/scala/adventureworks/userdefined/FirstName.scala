package adventureworks.userdefined

import adventureworks.Text
import typr.dsl.Bijection
import zio.jdbc.SqlFragment.Setter
import zio.jdbc.{JdbcDecoder, JdbcEncoder}
import zio.json.*

case class FirstName(value: String) extends AnyVal
object FirstName {
  implicit lazy val bijection: Bijection[FirstName, String] = Bijection[FirstName, String](_.value)(FirstName.apply)
  implicit lazy val jsonDecoder: JsonDecoder[FirstName] = JsonDecoder.string.map(FirstName.apply)
  implicit lazy val jsonEncoder: JsonEncoder[FirstName] = JsonEncoder.string.contramap(_.value)
  implicit lazy val jdbcDecoder: JdbcDecoder[FirstName] = JdbcDecoder.stringDecoder.map(FirstName.apply)
  implicit lazy val jdbcEncoder: JdbcEncoder[FirstName] = JdbcEncoder.stringEncoder.contramap(_.value)
  implicit lazy val ordering: Ordering[FirstName] = Ordering.by(_.value)
  implicit lazy val setter: Setter[FirstName] = Setter.stringSetter.contramap(_.value)
  implicit lazy val pgText: Text[FirstName] = Text.stringInstance.contramap(_.value)
}

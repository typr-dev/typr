package adventureworks.userdefined

import doobie.postgres.Text
import doobie.util.meta.Meta
import doobie.util.{Get, Put}
import io.circe.{Decoder, Encoder}
import typr.dsl.Bijection

case class FirstName(value: String) extends AnyVal
object FirstName {
  implicit lazy val arrayGet: Get[Array[FirstName]] = adventureworks.StringArrayMeta.get.map(_.map(FirstName.apply))
  implicit lazy val arrayPut: Put[Array[FirstName]] = adventureworks.StringArrayMeta.put.contramap(_.map(_.value))
  implicit lazy val bijection: Bijection[FirstName, String] = Bijection[FirstName, String](_.value)(FirstName.apply)
  implicit lazy val decoder: Decoder[FirstName] = Decoder.decodeString.map(FirstName.apply)
  implicit lazy val encoder: Encoder[FirstName] = Encoder.encodeString.contramap(_.value)
  implicit lazy val get: Get[FirstName] = Meta.StringMeta.get.map(FirstName.apply)
  implicit lazy val ordering: Ordering[FirstName] = Ordering.by(_.value)
  implicit lazy val put: Put[FirstName] = Meta.StringMeta.put.contramap(_.value)
  implicit lazy val pgText: Text[FirstName] = Text.stringInstance.contramap(_.value)
}

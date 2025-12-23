package adventureworks.userdefined

import adventureworks.Text
import anorm.{Column, ParameterMetaData, ToStatement}
import play.api.libs.json.{Reads, Writes}
import typr.dsl.Bijection

case class FirstName(value: String) extends AnyVal
object FirstName {
  implicit lazy val arrayColumn: Column[Array[FirstName]] = Column.columnToArray(using column, implicitly)
  implicit lazy val arrayToStatement: ToStatement[Array[FirstName]] = ToStatement.arrayToParameter(using ParameterMetaData.StringParameterMetaData).contramap(_.map(_.value))
  implicit lazy val bijection: Bijection[FirstName, String] = Bijection[FirstName, String](_.value)(FirstName.apply)
  implicit lazy val column: Column[FirstName] = Column.columnToString.map(FirstName.apply)
  implicit lazy val ordering: Ordering[FirstName] = Ordering.by(_.value)
  implicit lazy val parameterMetadata: ParameterMetaData[FirstName] = new ParameterMetaData[FirstName] {
    override def sqlType: String = ParameterMetaData.StringParameterMetaData.sqlType
    override def jdbcType: Int = ParameterMetaData.StringParameterMetaData.jdbcType
  }
  implicit lazy val reads: Reads[FirstName] = Reads.StringReads.map(FirstName.apply)
  implicit lazy val pgText: Text[FirstName] = new Text[FirstName] {
    override def unsafeEncode(v: FirstName, sb: StringBuilder) = Text.stringInstance.unsafeEncode(v.value, sb)
    override def unsafeArrayEncode(v: FirstName, sb: StringBuilder) = Text.stringInstance.unsafeArrayEncode(v.value, sb)
  }
  implicit lazy val toStatement: ToStatement[FirstName] = ToStatement.stringToStatement.contramap(_.value)
  implicit lazy val writes: Writes[FirstName] = Writes.StringWrites.contramap(_.value)
}

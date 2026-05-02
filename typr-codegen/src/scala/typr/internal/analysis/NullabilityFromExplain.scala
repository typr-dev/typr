package typr.internal.analysis

import anorm.{Column, SqlParser, SqlStringInterpolation, TypeDoesNotMatch}
import org.postgresql.util.PGobject
import play.api.libs.json.{Json, Reads}
import typr.internal.quote

import java.sql.Connection

/** Postgres is not able to figure out correct nullability through outer joins.
  *
  * This analysis produces a list of columns that are nullable because the come through such a join.
  *
  * This will be in addition to the columns that are nullable because they are nullable in the database.
  */
object NullabilityFromExplain {
  private val jsonColumn: Column[PGobject] = Column[PGobject] {
    case (value: PGobject, _) => Right(value)
    case (other, _)           => Left(TypeDoesNotMatch(s"Cannot convert $other to PGobject"))
  }

  /** NOTE IN PARTICULAR that the column names in `nullableOutputs` may not match column names from they query if they are aliased.
    *
    * However, the order of the columns *will* match, so `nullableIndices` can be used to find the indices of the nullable columns.
    */
  case class NullableColumns(plan: Plan, nullableOutputs: List[String]) {
    lazy val nullableIndices: Option[NullableIndices] = plan.Output.map(output => NullableIndices(output.zipWithIndex.filter { case (name, _) => nullableOutputs.contains(name) }.map(_._2).toSet))
  }
  case class NullableIndices(values: Set[Int])

  case class HasPlans(Plan: Plan)
  case class Plan(`Node Type`: String, `Join Type`: Option[String], Output: Option[List[String]], Plans: Option[List[Plan]]) {
    def output = Output.getOrElse(Nil)
  }

  implicit val readsPlan: Reads[Plan] = Json.reads
  implicit val readsHasPlans: Reads[HasPlans] = Json.reads

  def from(sql: DecomposedSql, params: List[MetadataParameterColumn])(implicit c: Connection): NullableColumns = {
    val renderedSql: String = sqlWithExamples(sql, params)
    val jsonStr = SQL"EXPLAIN (VERBOSE, FORMAT JSON) #$renderedSql".as(SqlParser.get("QUERY PLAN")(jsonColumn).single)
    val json = Json.parse(jsonStr.getValue)
    val hasPlans = json.as[Seq[HasPlans]]
    fromPlan(hasPlans.head)
  }

  // apparently we have to provide some non-NULL arguments.
  // if we set them to NULL postgres will sometimes be clever about impossible join conditions and prune the query plan
  def sqlWithExamples(sql: DecomposedSql, params: List[MetadataParameterColumn]): String =
    sql.render { n =>
      val param = params(n)
      val example = param.parameterType match {
        case JdbcType.Array                 => s"ARRAY[]"
        case JdbcType.BigInt                => "1"
        case JdbcType.Boolean               => "true"
        case JdbcType.Char                  => quote("")
        case JdbcType.Date                  => quote("2021-01-01")
        case JdbcType.Decimal               => "1.0"
        case JdbcType.Double                => "1.0"
        case JdbcType.Float                 => "1.0"
        case JdbcType.Integer               => "1"
        case JdbcType.NChar                 => quote("")
        case JdbcType.Numeric               => "1.0"
        case JdbcType.NVarChar              => quote("")
        case JdbcType.Real                  => "1.0"
        case JdbcType.SmallInt              => "1"
        case JdbcType.Time                  => quote("00:00:00")
        case JdbcType.TimeWithTimezone      => quote("00:00:00Z")
        case JdbcType.Timestamp             => quote("2021-01-01T00:00:00")
        case JdbcType.TimestampWithTimezone => quote("2021-01-01T00:00:00Z")
        case JdbcType.TinyInt               => "1"
        case JdbcType.VarChar               => "1.0"
        case _                              => "NULL"
      }
      s"$example::${param.parameterTypeName}"
    }

  /** recursively inspect the query plan to find joins which cause nullable columns
    *
    * @param hasPlans
    * @return
    */
  def fromPlan(hasPlans: HasPlans): NullableColumns = {
    def go(plan: Plan): NullableColumns =
      plan match {
        case Plan(_, _, None, _) => NullableColumns(plan, Nil)
        case Plan(_, Some(joinType), Some(outputs), Some(List(left, right))) =>
          val leftPlan = go(left)
          val rightPlan = go(right)
          val newNullables =
            joinType match {
              case "Inner" | "Semi" => Nil
              case "Right"          => leftPlan.plan.output
              case "Left"           => rightPlan.plan.output
              case "Full" | "Anti"  => leftPlan.plan.output ++ rightPlan.plan.output
            }

          val allNullables = leftPlan.nullableOutputs ++ rightPlan.nullableOutputs ++ newNullables
          NullableColumns(plan, allNullables.intersect(outputs))
        case other =>
          val nullables = other.Plans match {
            case Some(plans) => plans.flatMap(p => go(p).nullableOutputs)
            case None        => Nil
          }
          NullableColumns(other, nullables.intersect(other.output))
      }

    go(hasPlans.Plan)
  }
}

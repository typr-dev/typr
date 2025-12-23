package typr.internal.sqlfiles

import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.funsuite.AnyFunSuite
import typr.internal.analysis.{DecomposedSql, NullabilityFromExplain}
import typr.internal.analysis.NullabilityFromExplain.NullableIndices

import java.sql.{Connection, DriverManager}
import scala.annotation.nowarn

class NullabilityFromExplainTest extends AnyFunSuite with TypeCheckedTripleEquals {
  test("works") {
    implicit val conn: Connection = DriverManager.getConnection(
      "jdbc:postgresql://localhost:6432/Adventureworks?user=postgres&password=password"
    )
    val sql = DecomposedSql.parse(
      """SELECT s.businessentityid
        |     , p.title
        |     , p.firstname
        |     , p.middlename
        |     , p.namestyle
        |     , e.jobtitle
        |     , a.addressline1 as flaff
        |     , a.city
        |     , a.postalcode
        |FROM sales.salesperson s
        |         JOIN humanresources.employee e ON e.businessentityid = s.businessentityid
        |         JOIN person.person p ON p.businessentityid = s.businessentityid
        |         JOIN person.businessentityaddress bea ON bea.businessentityid = s.businessentityid
        |         LEFT JOIN person.address a ON a.addressid = bea.addressid
        |where s.businessentityid = 1
        |  and p.modifieddate > now()""".stripMargin
    )

    val planWithNullability = NullabilityFromExplain.from(sql, Nil)
    assert(planWithNullability.nullableOutputs === List("a.addressline1", "a.city", "a.postalcode")): @nowarn
    assert(planWithNullability.nullableIndices === Some(NullableIndices(Set(6, 7, 8))))
  }
}

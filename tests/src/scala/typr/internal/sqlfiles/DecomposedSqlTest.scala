package typr.internal.sqlfiles

import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.funsuite.AnyFunSuite
import typr.internal.analysis.{DecomposedSql, ParsedName}

import scala.annotation.nowarn

class DecomposedSqlTest extends AnyFunSuite with TypeCheckedTripleEquals {

  test("testParsing") {
    val actual = DecomposedSql.parse(
      "SELECT * FROM table WHERE afield = ':not me' AND bfield = :param1 AND cfield = :param2 and dfield = :param2;"
    )
    val expected = DecomposedSql(
      List(
        DecomposedSql.SqlText("SELECT * FROM table WHERE afield = ':not me' AND bfield = "),
        DecomposedSql.NamedParam(ParsedName.of("param1")),
        DecomposedSql.SqlText(" AND cfield = "),
        DecomposedSql.NamedParam(ParsedName.of("param2")),
        DecomposedSql.SqlText(" and dfield = "),
        DecomposedSql.NamedParam(ParsedName.of("param2"))
      )
    )
    assert(actual === expected)
  }

  test("testParsing2") {
    val actual = DecomposedSql.parse(
      """/*
        |Test SQL file to test correct parsing
        |:named_parameters in multiline comments should be ignored
        |*/
        |SELECT col1, col2 -- :named_parameters in comments should be ignored
        |FROM table
        |WHERE id = :named_parameter1
        |AND name = :named_parameter2;""".stripMargin
    )
    val expected = DecomposedSql(
      List(
        DecomposedSql.SqlText("""/*
                              |Test SQL file to test correct parsing
                              |:named_parameters in multiline comments should be ignored
                              |*/
                              |SELECT col1, col2 -- :named_parameters in comments should be ignored
                              |FROM table
                              |WHERE id = """.stripMargin),
        DecomposedSql.NamedParam(ParsedName.of("named_parameter1")),
        DecomposedSql.SqlText("""
                              |AND name = """.stripMargin),
        DecomposedSql.NamedParam(ParsedName.of("named_parameter2"))
      )
    )
    assert(actual === expected)
  }

  test("testDoubleColon") {
    val actual = DecomposedSql.parse(
      "SELECT '{1,2,3,4,5}'::int[] WHERE some_field = :some_field_value"
    )
    val expected = DecomposedSql(
      List(
        DecomposedSql.SqlText("SELECT '{1,2,3,4,5}'::int[] WHERE some_field = "),
        DecomposedSql.NamedParam(ParsedName.of("some_field_value"))
      )
    )

    assert(actual === expected)
  }

  test("testUnnamed") {
    val actual = DecomposedSql.parse(
      "SELECT '{1,2,3,4,5}'::int[] WHERE f1 = :foo and f2 = ? and f3 = :foo and f4 = ?"
    )
    val expected = List(
      DecomposedSql.NamedParam(ParsedName.of("foo")) -> List(0, 2),
      DecomposedSql.NotNamedParam -> List(1),
      DecomposedSql.NotNamedParam -> List(3)
    )
    assert(actual.paramNamesWithIndices === expected)
  }

  test("optional and required params") {
    val actual = DecomposedSql.parse(
      """SELECT '{1,2,3,4,5}'::int[] WHERE f1 = :"optional?" AND f2 = :"required!""""
    )
    val expected = List(
      DecomposedSql.NamedParam(ParsedName.of("optional?")) -> List(0),
      DecomposedSql.NamedParam(ParsedName.of("required!")) -> List(1)
    )
    assert(actual.paramNamesWithIndices === expected)
  }

  test("typed array param") {
    val actual = DecomposedSql.parse(
      """SELECT 1 where WHERE foo.bar = ANY(:"flaff:org.foo.Id")""".stripMargin
    )
    val expected = List(
      DecomposedSql.NamedParam(ParsedName.of("flaff:org.foo.Id")) -> List(0)
    )
    assert(actual.paramNamesWithIndices === expected): @nowarn
    assert(actual.sqlWithNulls === "SELECT 1 where WHERE foo.bar = ANY(null)")
  }

  test("specify details in first parameter occurrence (1)") {
    val actual = DecomposedSql.parse(
      """select 1 where :"foo!" = 1 and :foo != 2""".stripMargin
    )
    val expected = List(
      DecomposedSql.NamedParam(ParsedName.of("foo!")) -> List(0, 1)
    )
    assert(actual.paramNamesWithIndices === expected)
  }

  test("specify details in first parameter occurrence (2)") {
    val actual = DecomposedSql.parse(
      """select 1 where :foo = 1 and :"foo!" != 2""".stripMargin
    )
    val expected = List(
      DecomposedSql.NamedParam(ParsedName.of("foo")) -> List(0, 1)
    )
    assert(actual.paramNamesWithIndices === expected)
  }

  test("ignorer semicolon and everything after (but keep in comments and string literals)") {
    val actual = DecomposedSql.parse(
      """|select /* ; */
         | -- ;
         |';';discard whatever after""".stripMargin
    )
    val expected = """select /* ; */
                     | -- ;
                     |';'""".stripMargin
    assert(actual.sqlWithNulls === expected)
  }
}

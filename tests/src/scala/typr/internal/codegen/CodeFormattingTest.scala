package typr
package internal
package codegen

import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.funsuite.AnyFunSuite

class CodeFormattingTest extends AnyFunSuite with TypeCheckedTripleEquals {
  val lang = LangScala.javaDsl(Dialect.Scala3, TypeSupportScala)
  test("interpolation keeps indentation of interpolation point") {
    val actual = code"""|a${List("b1", "b2").mkString("\n")}
                        |a""".stripMargin.render(lang)

    val expected = """|ab1
                      | b2
                      |a""".stripMargin

    assert(actual.asString === expected)
  }

  test("work with mkCode") {
    val foo = List("b1", "b2").map(x => code"import $x").mkCode("\n")
    val actual = code"""|a $foo
                        |a""".stripMargin.render(lang)

    val expected = """|a import b1
                      |  import b2
                      |a""".stripMargin

    assert(actual.asString === expected)
  }

  test("keep linebreaks") {
    val bs = List("b1", "b2").map(x => code"import $x").mkCode("\n\n")
    val cs = List("c1", "c2").map(x => code"import $x").mkCode("\n\n")
    val actual = code"""|$bs

                        |$cs""".stripMargin.render(lang)

    val expected = """|import b1
                      |
                      |import b2
                      |
                      |import c1
                      |
                      |import c2""".stripMargin

    assert(actual.asString === expected)
  }

  test("multiple on same line") {
    val actual = code"""${"a"}${"b"}${"___\n___"}${"a"}""".stripMargin.render(lang)

    val expected = s"""|ab___
                       |  ___a""".stripMargin

    assert(actual.asString === expected)
  }
}

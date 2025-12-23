package adventureworks

import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.Assertion
import org.scalatest.funsuite.AnyFunSuite
import zio.json.JsonEncoder

trait JsonEquals extends AnyFunSuite with TypeCheckedTripleEquals {
  // need to compare json instead of case classes because of arrays
  def assertJsonEquals[A: JsonEncoder](a1: A, a2: A): Assertion =
    assert(JsonEncoder[A].toJsonAST(a1) === JsonEncoder[A].toJsonAST(a2))
}

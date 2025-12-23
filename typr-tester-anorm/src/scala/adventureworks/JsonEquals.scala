package adventureworks

import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.Assertion
import org.scalatest.funsuite.AnyFunSuite
import play.api.libs.json.{Json, Writes}

trait JsonEquals extends AnyFunSuite with TypeCheckedTripleEquals {
  // need to compare json instead of case classes because of arrays
  def assertJsonEquals[A: Writes](a1: A, a2: A): Assertion =
    assert(Json.toJson(a1) === Json.toJson(a2))

}

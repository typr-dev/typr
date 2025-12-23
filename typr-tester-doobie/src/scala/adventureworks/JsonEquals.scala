package adventureworks

import io.circe.Encoder
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.Assertion
import org.scalatest.funsuite.AnyFunSuite

trait JsonEquals extends AnyFunSuite with TypeCheckedTripleEquals {
  // need to compare json instead of case classes because of arrays
  def assertJsonEquals[A: Encoder](a1: A, a2: A): Assertion =
    assert(Encoder[A].apply(a1) === Encoder[A].apply(a2))
}

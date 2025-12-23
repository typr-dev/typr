package typr

import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.funsuite.AnyFunSuite

class NamingTest extends AnyFunSuite with TypeCheckedTripleEquals {
  test("works") {
    assert(Naming.splitOnSymbol("asd-dsa").toList === List("asd", "dsa"))
  }
}

package typr.bridge

import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.funsuite.AnyFunSuite
import typr.bridge.model.TypePolicy
import typr.bridge.validation.TypePolicyValidator

class TypePolicyValidatorTest extends AnyFunSuite with TypeCheckedTripleEquals {

  test("Exact: matching types pass") {
    assert(TypePolicyValidator.validate("INTEGER", "INTEGER", TypePolicy.Exact) === Right(()))
  }

  test("Exact: mismatched types fail") {
    assert(TypePolicyValidator.validate("INTEGER", "BIGINT", TypePolicy.Exact).isLeft)
  }

  test("Exact: normalized types pass") {
    assert(TypePolicyValidator.validate("INT", "INTEGER", TypePolicy.Exact) === Right(()))
  }

  test("AllowWidening: INT domain, SMALLINT source passes (source narrower)") {
    assert(TypePolicyValidator.validate("INTEGER", "SMALLINT", TypePolicy.AllowWidening) === Right(()))
  }

  test("AllowWidening: INT domain, BIGINT source fails (source wider)") {
    assert(TypePolicyValidator.validate("INTEGER", "BIGINT", TypePolicy.AllowWidening).isLeft)
  }

  test("AllowWidening: same type passes") {
    assert(TypePolicyValidator.validate("INTEGER", "INTEGER", TypePolicy.AllowWidening) === Right(()))
  }

  test("AllowWidening: cross-family fails") {
    assert(TypePolicyValidator.validate("INTEGER", "VARCHAR", TypePolicy.AllowWidening).isLeft)
  }

  test("AllowNarrowing: BIGINT source, INT domain passes (source wider)") {
    assert(TypePolicyValidator.validate("INTEGER", "BIGINT", TypePolicy.AllowNarrowing) === Right(()))
  }

  test("AllowNarrowing: SMALLINT source, INT domain fails (source narrower)") {
    assert(TypePolicyValidator.validate("INTEGER", "SMALLINT", TypePolicy.AllowNarrowing).isLeft)
  }

  test("AllowNarrowing: same type passes") {
    assert(TypePolicyValidator.validate("BIGINT", "BIGINT", TypePolicy.AllowNarrowing) === Right(()))
  }

  test("AllowNarrowing: cross-family fails") {
    assert(TypePolicyValidator.validate("VARCHAR", "INTEGER", TypePolicy.AllowNarrowing).isLeft)
  }

  test("AllowPrecisionLoss: DECIMAL to FLOAT passes") {
    assert(TypePolicyValidator.validate("DECIMAL", "REAL", TypePolicy.AllowPrecisionLoss) === Right(()))
  }

  test("AllowPrecisionLoss: INT to DOUBLE passes") {
    assert(TypePolicyValidator.validate("INTEGER", "DOUBLE", TypePolicy.AllowPrecisionLoss) === Right(()))
  }

  test("AllowPrecisionLoss: VARCHAR to INT fails") {
    assert(TypePolicyValidator.validate("VARCHAR", "INTEGER", TypePolicy.AllowPrecisionLoss).isLeft)
  }

  test("AllowTruncation: VARCHAR to VARCHAR passes") {
    assert(TypePolicyValidator.validate("VARCHAR", "TEXT", TypePolicy.AllowTruncation) === Right(()))
  }

  test("AllowTruncation: VARCHAR to INT fails") {
    assert(TypePolicyValidator.validate("VARCHAR", "INTEGER", TypePolicy.AllowTruncation).isLeft)
  }

  test("AllowNullableToRequired: always passes for types") {
    assert(TypePolicyValidator.validate("INTEGER", "VARCHAR", TypePolicy.AllowNullableToRequired) === Right(()))
  }

  test("validateWithCanonical: Int domain, bigint source passes with AllowNarrowing") {
    assert(TypePolicyValidator.validateWithCanonical("Int", "bigint", TypePolicy.AllowNarrowing) === Right(()))
  }

  test("validateWithCanonical: Int domain, bigint source fails with Exact") {
    assert(TypePolicyValidator.validateWithCanonical("Int", "bigint", TypePolicy.Exact).isLeft)
  }

  test("validateWithCanonical: String domain, varchar source passes with Exact") {
    assert(TypePolicyValidator.validateWithCanonical("String", "varchar", TypePolicy.Exact) === Right(()))
  }

  test("cross-family always fails regardless of policy (except AllowNullableToRequired)") {
    val crossFamilyPolicies = List(
      TypePolicy.Exact,
      TypePolicy.AllowWidening,
      TypePolicy.AllowNarrowing,
      TypePolicy.AllowPrecisionLoss,
      TypePolicy.AllowTruncation
    )
    crossFamilyPolicies.foreach { policy =>
      assert(TypePolicyValidator.validate("VARCHAR", "BOOLEAN", policy).isLeft, s"Expected failure for $policy with VARCHAR/BOOLEAN")
    }
  }
}

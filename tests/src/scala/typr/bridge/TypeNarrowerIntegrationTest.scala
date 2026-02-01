package typr.bridge

import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.funsuite.AnyFunSuite

class TypeNarrowerIntegrationTest extends AnyFunSuite with TypeCheckedTripleEquals {

  test("mapCanonicalToNormalized: Int maps to INTEGER") {
    assert(TypeNarrower.mapCanonicalToNormalized("Int") === "INTEGER")
  }

  test("mapCanonicalToNormalized: Long maps to BIGINT") {
    assert(TypeNarrower.mapCanonicalToNormalized("Long") === "BIGINT")
  }

  test("mapCanonicalToNormalized: String maps to VARCHAR") {
    assert(TypeNarrower.mapCanonicalToNormalized("String") === "VARCHAR")
  }

  test("mapCanonicalToNormalized: Boolean maps to BOOLEAN") {
    assert(TypeNarrower.mapCanonicalToNormalized("Boolean") === "BOOLEAN")
  }

  test("mapCanonicalToNormalized: UUID maps to UUID") {
    assert(TypeNarrower.mapCanonicalToNormalized("UUID") === "UUID")
  }

  test("mapCanonicalToNormalized: LocalDate maps to DATE") {
    assert(TypeNarrower.mapCanonicalToNormalized("LocalDate") === "DATE")
  }

  test("mapCanonicalToNormalized: OffsetDateTime maps to TIMESTAMPTZ") {
    assert(TypeNarrower.mapCanonicalToNormalized("OffsetDateTime") === "TIMESTAMPTZ")
  }

  test("mapCanonicalToNormalized: Instant maps to TIMESTAMPTZ") {
    assert(TypeNarrower.mapCanonicalToNormalized("Instant") === "TIMESTAMPTZ")
  }

  test("mapCanonicalToNormalized: ByteArray maps to BYTEA") {
    assert(TypeNarrower.mapCanonicalToNormalized("ByteArray") === "BYTEA")
  }

  test("mapCanonicalToNormalized: BigDecimal maps to DECIMAL") {
    assert(TypeNarrower.mapCanonicalToNormalized("BigDecimal") === "DECIMAL")
  }

  test("mapCanonicalToNormalized: unknown type uppercases") {
    assert(TypeNarrower.mapCanonicalToNormalized("CustomType") === "CUSTOMTYPE")
  }

  test("normalizeDbType: int4 normalizes to INTEGER") {
    assert(TypeNarrower.normalizeDbType("int4") === "INTEGER")
  }

  test("normalizeDbType: serial normalizes to INTEGER") {
    assert(TypeNarrower.normalizeDbType("serial") === "INTEGER")
  }

  test("normalizeDbType: character varying normalizes to VARCHAR") {
    assert(TypeNarrower.normalizeDbType("character varying") === "VARCHAR")
  }

  test("normalizeDbType: text normalizes to VARCHAR") {
    assert(TypeNarrower.normalizeDbType("text") === "VARCHAR")
  }

  test("normalizeDbType: numeric normalizes to DECIMAL") {
    assert(TypeNarrower.normalizeDbType("numeric") === "DECIMAL")
  }

  test("areTypesCompatible: INTEGER and BIGINT are compatible") {
    assert(TypeNarrower.areTypesCompatible("INTEGER", "BIGINT") === true)
  }

  test("areTypesCompatible: VARCHAR and INTEGER are not compatible") {
    assert(TypeNarrower.areTypesCompatible("VARCHAR", "INTEGER") === false)
  }

  test("areTypesCompatible: TIMESTAMP and TIMESTAMPTZ are compatible") {
    assert(TypeNarrower.areTypesCompatible("TIMESTAMP", "TIMESTAMPTZ") === true)
  }

  test("areTypesCompatible: same types are compatible") {
    assert(TypeNarrower.areTypesCompatible("INTEGER", "INTEGER") === true)
  }

  test("typeCompatible in AlignmentComputer works with canonical domain types") {
    val domainType = DomainTypeDefinition(
      name = "Test",
      primary = None,
      fields = List(DomainField(name = "id", typeName = "Int", nullable = false, array = false, description = None)),
      alignedSources = Map.empty,
      description = None,
      generateDomainType = true,
      generateMappers = true,
      generateInterface = false,
      generateBuilder = false,
      generateCopy = true
    )
    val alignedSource = AlignedSource.empty("test", "table")
    val sourceEntity = SourceEntity(
      sourceName = "test",
      entityPath = "table",
      sourceType = SourceEntityType.Table,
      fields = List(SourceField(name = "id", typeName = "bigint", nullable = false, isPrimaryKey = true, comment = None))
    )
    val nameAligner = DefaultNameAligner.default

    val results = AlignmentComputer.computeAlignment(domainType, alignedSource, sourceEntity, nameAligner)

    assert(results.exists {
      case FieldAlignmentResult.Aligned("id", "id", true) => true
      case _                                              => false
    })
  }

  test("typeCompatible detects incompatible types") {
    val domainType = DomainTypeDefinition(
      name = "Test",
      primary = None,
      fields = List(DomainField(name = "name", typeName = "String", nullable = false, array = false, description = None)),
      alignedSources = Map.empty,
      description = None,
      generateDomainType = true,
      generateMappers = true,
      generateInterface = false,
      generateBuilder = false,
      generateCopy = true
    )
    val alignedSource = AlignedSource.empty("test", "table")
    val sourceEntity = SourceEntity(
      sourceName = "test",
      entityPath = "table",
      sourceType = SourceEntityType.Table,
      fields = List(SourceField(name = "name", typeName = "integer", nullable = false, isPrimaryKey = false, comment = None))
    )
    val nameAligner = DefaultNameAligner.default

    val results = AlignmentComputer.computeAlignment(domainType, alignedSource, sourceEntity, nameAligner)

    assert(results.exists {
      case FieldAlignmentResult.TypeMismatch("name", "name", _, _) => true
      case _                                                       => false
    })
  }
}

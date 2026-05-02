package typr.bridge

import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.funsuite.AnyFunSuite
import typr.bridge.model.*
import typr.bridge.validation.FlowValidator

class FlowValidatorTest extends AnyFunSuite with TypeCheckedTripleEquals {

  private val nameAligner = DefaultNameAligner.default

  private def mkDomainType(
      name: String = "Customer",
      fields: List[DomainField] = Nil,
      primary: Option[PrimarySource] = None
  ): DomainTypeDefinition =
    DomainTypeDefinition(
      name = name,
      primary = primary,
      fields = fields,
      alignedSources = Map.empty,
      description = None,
      generateDomainType = true,
      generateMappers = true,
      generateInterface = false,
      generateBuilder = false,
      generateCopy = true
    )

  private def mkSourceDecl(
      sourceName: String = "pg",
      entityPath: String = "customers",
      role: SourceRole = SourceRole.Primary,
      direction: FlowDirection = FlowDirection.InOut,
      mode: CompatibilityMode = CompatibilityMode.Superset,
      fieldOverrides: Map[String, FieldOverride] = Map.empty,
      defaultTypePolicy: TypePolicy = TypePolicy.Exact
  ): SourceDeclaration =
    SourceDeclaration(
      sourceName = sourceName,
      entityPath = entityPath,
      role = role,
      direction = direction,
      mode = mode,
      mappings = Map.empty,
      exclude = Set.empty,
      includeExtra = Nil,
      readonly = false,
      defaultTypePolicy = defaultTypePolicy,
      fieldOverrides = fieldOverrides
    )

  private def mkSourceEntity(
      sourceName: String = "pg",
      entityPath: String = "customers",
      fields: List[SourceField] = Nil
  ): SourceEntity =
    SourceEntity(
      sourceName = sourceName,
      entityPath = entityPath,
      sourceType = SourceEntityType.Table,
      fields = fields
    )

  test("clean config: all fields auto-forward with compatible types produces no errors") {
    val domainType = mkDomainType(
      fields = List(
        DomainField(name = "id", typeName = "Int", nullable = false, array = false, description = None),
        DomainField(name = "name", typeName = "String", nullable = false, array = false, description = None)
      ),
      primary = Some(PrimarySource("pg", "customers"))
    )
    val sourceKey = "pg:customers"
    val decl = mkSourceDecl()
    val entity = mkSourceEntity(fields =
      List(
        SourceField(name = "id", typeName = "integer", nullable = false, isPrimaryKey = true, comment = None),
        SourceField(name = "name", typeName = "varchar", nullable = false, isPrimaryKey = false, comment = None)
      )
    )

    val findings = FlowValidator.validateEntity(
      domainType,
      Map(sourceKey -> decl),
      Map(sourceKey -> entity),
      nameAligner
    )

    assert(findings.count(_.severity == Severity.Error) === 0)
  }

  test("missing primary source: NoPrimarySource error") {
    val domainType = mkDomainType(
      fields = List(
        DomainField(name = "id", typeName = "Int", nullable = false, array = false, description = None)
      )
    )
    val sourceKey = "pg:customers"
    val decl = mkSourceDecl(role = SourceRole.Aligned)
    val entity = mkSourceEntity(fields =
      List(
        SourceField(name = "id", typeName = "integer", nullable = false, isPrimaryKey = true, comment = None)
      )
    )

    val findings = FlowValidator.validateEntity(
      domainType,
      Map(sourceKey -> decl),
      Map(sourceKey -> entity),
      nameAligner
    )

    assert(findings.exists(_.code == CheckCode.NoPrimarySource))
  }

  test("source entity not found: SourceEntityNotFound error") {
    val domainType = mkDomainType(
      fields = List(
        DomainField(name = "id", typeName = "Int", nullable = false, array = false, description = None)
      ),
      primary = Some(PrimarySource("pg", "customers"))
    )
    val sourceKey = "pg:customers"
    val decl = mkSourceDecl()

    val findings = FlowValidator.validateEntity(
      domainType,
      Map(sourceKey -> decl),
      Map.empty,
      nameAligner
    )

    assert(findings.exists(_.code == CheckCode.SourceEntityNotFound))
  }

  test("unannotated field in Exact mode: UnannotatedField error") {
    val domainType = mkDomainType(
      fields = List(
        DomainField(name = "id", typeName = "Int", nullable = false, array = false, description = None)
      ),
      primary = Some(PrimarySource("pg", "customers"))
    )
    val sourceKey = "pg:customers"
    val decl = mkSourceDecl(mode = CompatibilityMode.Exact)
    val entity = mkSourceEntity(fields =
      List(
        SourceField(name = "id", typeName = "integer", nullable = false, isPrimaryKey = true, comment = None),
        SourceField(name = "extra_field", typeName = "varchar", nullable = true, isPrimaryKey = false, comment = None)
      )
    )

    val findings = FlowValidator.validateEntity(
      domainType,
      Map(sourceKey -> decl),
      Map(sourceKey -> entity),
      nameAligner
    )

    assert(findings.exists(f => f.code == CheckCode.UnannotatedField && f.fieldName.contains("extra_field")))
  }

  test("type mismatch with no policy (Exact): TypeIncompatible error") {
    val domainType = mkDomainType(
      fields = List(
        DomainField(name = "amount", typeName = "String", nullable = false, array = false, description = None)
      ),
      primary = Some(PrimarySource("pg", "orders"))
    )
    val sourceKey = "pg:orders"
    val decl = mkSourceDecl(entityPath = "orders", defaultTypePolicy = TypePolicy.Exact)
    val entity = mkSourceEntity(
      entityPath = "orders",
      fields = List(
        SourceField(name = "amount", typeName = "integer", nullable = false, isPrimaryKey = false, comment = None)
      )
    )

    val findings = FlowValidator.validateEntity(
      domainType,
      Map(sourceKey -> decl),
      Map(sourceKey -> entity),
      nameAligner
    )

    assert(findings.exists(f => f.code == CheckCode.TypeIncompatible && f.fieldName.contains("amount")))
  }

  test("type mismatch with correct policy: no error") {
    val domainType = mkDomainType(
      fields = List(
        DomainField(name = "amount", typeName = "Long", nullable = false, array = false, description = None)
      ),
      primary = Some(PrimarySource("pg", "orders"))
    )
    val sourceKey = "pg:orders"
    val decl = mkSourceDecl(entityPath = "orders", defaultTypePolicy = TypePolicy.AllowWidening)
    val entity = mkSourceEntity(
      entityPath = "orders",
      fields = List(
        SourceField(name = "amount", typeName = "integer", nullable = false, isPrimaryKey = false, comment = None)
      )
    )

    val findings = FlowValidator.validateEntity(
      domainType,
      Map(sourceKey -> decl),
      Map(sourceKey -> entity),
      nameAligner
    )

    assert(findings.count(f => f.severity == Severity.Error && f.fieldName.contains("amount")) === 0)
  }

  test("type mismatch with wrong policy: TypePolicyViolation error") {
    val domainType = mkDomainType(
      fields = List(
        DomainField(name = "amount", typeName = "Int", nullable = false, array = false, description = None)
      ),
      primary = Some(PrimarySource("pg", "orders"))
    )
    val sourceKey = "pg:orders"
    val decl = mkSourceDecl(entityPath = "orders", defaultTypePolicy = TypePolicy.AllowWidening)
    val entity = mkSourceEntity(
      entityPath = "orders",
      fields = List(
        SourceField(name = "amount", typeName = "bigint", nullable = false, isPrimaryKey = false, comment = None)
      )
    )

    val findings = FlowValidator.validateEntity(
      domainType,
      Map(sourceKey -> decl),
      Map(sourceKey -> entity),
      nameAligner
    )

    assert(findings.exists(f => f.code == CheckCode.TypePolicyViolation && f.fieldName.contains("amount")))
  }

  test("drop on out-source: warning") {
    val domainType = mkDomainType(
      fields = List(
        DomainField(name = "id", typeName = "Int", nullable = false, array = false, description = None),
        DomainField(name = "internal_note", typeName = "String", nullable = true, array = false, description = None)
      ),
      primary = Some(PrimarySource("pg", "customers"))
    )
    val sourceKey = "pg:customers"
    val decl = mkSourceDecl(direction = FlowDirection.Out)
    val entity = mkSourceEntity(fields =
      List(
        SourceField(name = "id", typeName = "integer", nullable = false, isPrimaryKey = true, comment = None)
      )
    )

    val findings = FlowValidator.validateEntity(
      domainType,
      Map(sourceKey -> decl),
      Map(sourceKey -> entity),
      nameAligner
    )

    assert(findings.exists(f => f.severity == Severity.Warning && f.fieldName.contains("internal_note")))
  }

  test("MergeFrom referencing non-existent source field: InvalidMergeFromRef error") {
    val domainType = mkDomainType(
      fields = List(
        DomainField(name = "full_name", typeName = "String", nullable = false, array = false, description = None)
      ),
      primary = Some(PrimarySource("pg", "customers"))
    )
    val sourceKey = "pg:customers"
    val overrides = Map("full_name" -> FieldOverride.Custom(CustomKind.MergeFrom(List("first_name", "nonexistent"))))
    val decl = mkSourceDecl(fieldOverrides = overrides)
    val entity = mkSourceEntity(fields =
      List(
        SourceField(name = "first_name", typeName = "varchar", nullable = false, isPrimaryKey = false, comment = None)
      )
    )

    val findings = FlowValidator.validateEntity(
      domainType,
      Map(sourceKey -> decl),
      Map(sourceKey -> entity),
      nameAligner
    )

    assert(findings.exists(f => f.code == CheckCode.InvalidMergeFromRef && f.message.contains("nonexistent")))
  }

  test("ComputedFrom referencing non-existent domain field: InvalidComputedFromRef error") {
    val domainType = mkDomainType(
      fields = List(
        DomainField(name = "display_name", typeName = "String", nullable = false, array = false, description = None)
      ),
      primary = Some(PrimarySource("pg", "customers"))
    )
    val sourceKey = "pg:customers"
    val overrides = Map("display_name" -> FieldOverride.Custom(CustomKind.ComputedFrom(List("first_name", "last_name"))))
    val decl = mkSourceDecl(fieldOverrides = overrides)
    val entity = mkSourceEntity(fields =
      List(
        SourceField(name = "id", typeName = "integer", nullable = false, isPrimaryKey = true, comment = None)
      )
    )

    val findings = FlowValidator.validateEntity(
      domainType,
      Map(sourceKey -> decl),
      Map(sourceKey -> entity),
      nameAligner
    )

    assert(findings.exists(f => f.code == CheckCode.InvalidComputedFromRef && f.message.contains("first_name")))
    assert(findings.exists(f => f.code == CheckCode.InvalidComputedFromRef && f.message.contains("last_name")))
  }

  test("Out source missing a domain field: MissingRequiredField error") {
    val domainType = mkDomainType(
      fields = List(
        DomainField(name = "id", typeName = "Int", nullable = false, array = false, description = None),
        DomainField(name = "email", typeName = "String", nullable = false, array = false, description = None)
      ),
      primary = Some(PrimarySource("pg", "customers"))
    )
    val sourceKey = "pg:customers"
    val decl = mkSourceDecl(direction = FlowDirection.Out)
    val entity = mkSourceEntity(fields =
      List(
        SourceField(name = "id", typeName = "integer", nullable = false, isPrimaryKey = true, comment = None)
      )
    )

    val findings = FlowValidator.validateEntity(
      domainType,
      Map(sourceKey -> decl),
      Map(sourceKey -> entity),
      nameAligner
    )

    assert(
      findings.exists(f =>
        f.severity == Severity.Error &&
          f.code == CheckCode.MissingRequiredField &&
          f.fieldName.contains("email") &&
          f.message.contains("Out-source")
      )
    )
  }

  test("In source missing a domain field: warning only") {
    val domainType = mkDomainType(
      fields = List(
        DomainField(name = "id", typeName = "Int", nullable = false, array = false, description = None),
        DomainField(name = "email", typeName = "String", nullable = false, array = false, description = None)
      ),
      primary = Some(PrimarySource("pg", "customers"))
    )
    val sourceKey = "pg:customers"
    val decl = mkSourceDecl(direction = FlowDirection.In)
    val entity = mkSourceEntity(fields =
      List(
        SourceField(name = "id", typeName = "integer", nullable = false, isPrimaryKey = true, comment = None)
      )
    )

    val findings = FlowValidator.validateEntity(
      domainType,
      Map(sourceKey -> decl),
      Map(sourceKey -> entity),
      nameAligner
    )

    val emailFindings = findings.filter(_.fieldName.contains("email"))
    assert(!emailFindings.exists(_.severity == Severity.Error))
    assert(emailFindings.exists(_.severity == Severity.Warning))
  }

  test("InOut source missing a domain field: MissingRequiredField error") {
    val domainType = mkDomainType(
      fields = List(
        DomainField(name = "id", typeName = "Int", nullable = false, array = false, description = None),
        DomainField(name = "email", typeName = "String", nullable = false, array = false, description = None)
      ),
      primary = Some(PrimarySource("pg", "customers"))
    )
    val sourceKey = "pg:customers"
    val decl = mkSourceDecl(direction = FlowDirection.InOut)
    val entity = mkSourceEntity(fields =
      List(
        SourceField(name = "id", typeName = "integer", nullable = false, isPrimaryKey = true, comment = None)
      )
    )

    val findings = FlowValidator.validateEntity(
      domainType,
      Map(sourceKey -> decl),
      Map(sourceKey -> entity),
      nameAligner
    )

    assert(
      findings.exists(f =>
        f.severity == Severity.Error &&
          f.code == CheckCode.MissingRequiredField &&
          f.fieldName.contains("email") &&
          f.message.contains("InOut-source")
      )
    )
  }

  test("no fields: NoFields error") {
    val domainType = mkDomainType(
      fields = Nil,
      primary = Some(PrimarySource("pg", "customers"))
    )
    val sourceKey = "pg:customers"
    val decl = mkSourceDecl()
    val entity = mkSourceEntity(fields =
      List(
        SourceField(name = "id", typeName = "integer", nullable = false, isPrimaryKey = true, comment = None)
      )
    )

    val findings = FlowValidator.validateEntity(
      domainType,
      Map(sourceKey -> decl),
      Map(sourceKey -> entity),
      nameAligner
    )

    assert(findings.exists(_.code == CheckCode.NoFields))
  }

  test("nullability mismatch: required domain, nullable source with Exact policy") {
    val domainType = mkDomainType(
      fields = List(
        DomainField(name = "email", typeName = "String", nullable = false, array = false, description = None)
      ),
      primary = Some(PrimarySource("pg", "customers"))
    )
    val sourceKey = "pg:customers"
    val decl = mkSourceDecl(defaultTypePolicy = TypePolicy.Exact)
    val entity = mkSourceEntity(fields =
      List(
        SourceField(name = "email", typeName = "varchar", nullable = true, isPrimaryKey = false, comment = None)
      )
    )

    val findings = FlowValidator.validateEntity(
      domainType,
      Map(sourceKey -> decl),
      Map(sourceKey -> entity),
      nameAligner
    )

    assert(findings.exists(_.code == CheckCode.NullabilityMismatch))
  }

  test("end-to-end: multi-source customer entity") {
    val domainType = mkDomainType(
      name = "Customer",
      fields = List(
        DomainField(name = "id", typeName = "Long", nullable = false, array = false, description = None),
        DomainField(name = "name", typeName = "String", nullable = false, array = false, description = None),
        DomainField(name = "email", typeName = "String", nullable = true, array = false, description = None)
      ),
      primary = Some(PrimarySource("pg", "customers"))
    )

    val pgKey = "pg:customers"
    val pgDecl = mkSourceDecl(sourceName = "pg", entityPath = "customers", role = SourceRole.Primary, direction = FlowDirection.InOut)
    val pgEntity = mkSourceEntity(
      sourceName = "pg",
      entityPath = "customers",
      fields = List(
        SourceField(name = "id", typeName = "bigint", nullable = false, isPrimaryKey = true, comment = None),
        SourceField(name = "name", typeName = "varchar", nullable = false, isPrimaryKey = false, comment = None),
        SourceField(name = "email", typeName = "varchar", nullable = true, isPrimaryKey = false, comment = None)
      )
    )

    val apiKey = "api:Customer"
    val apiDecl = mkSourceDecl(sourceName = "api", entityPath = "Customer", role = SourceRole.Aligned, direction = FlowDirection.Out)
    val apiEntity = mkSourceEntity(
      sourceName = "api",
      entityPath = "Customer",
      fields = List(
        SourceField(name = "id", typeName = "bigint", nullable = false, isPrimaryKey = false, comment = None),
        SourceField(name = "name", typeName = "varchar", nullable = false, isPrimaryKey = false, comment = None)
      )
    )

    val findings = FlowValidator.validateEntity(
      domainType,
      Map(pgKey -> pgDecl, apiKey -> apiDecl),
      Map(pgKey -> pgEntity, apiKey -> apiEntity),
      nameAligner
    )

    val pgErrors = findings.filter(f => f.sourceKey.contains(pgKey) && f.severity == Severity.Error)
    assert(pgErrors.isEmpty, s"Expected no pg errors but got: $pgErrors")

    assert(
      findings.exists(f =>
        f.sourceKey.contains(apiKey) &&
          f.fieldName.contains("email") &&
          (f.severity == Severity.Error || f.severity == Severity.Warning)
      )
    )
  }
}

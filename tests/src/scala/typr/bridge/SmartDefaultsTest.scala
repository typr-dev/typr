package typr.bridge

import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.funsuite.AnyFunSuite
import typr.bridge.model.*
import typr.bridge.validation.SmartDefaults

class SmartDefaultsTest extends AnyFunSuite with TypeCheckedTripleEquals {

  private val nameAligner = DefaultNameAligner.default

  private def mkDomainType(fields: List[DomainField]): DomainTypeDefinition =
    DomainTypeDefinition(
      name = "TestEntity",
      primary = None,
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
      mode: CompatibilityMode = CompatibilityMode.Exact,
      mappings: Map[String, String] = Map.empty,
      exclude: Set[String] = Set.empty,
      fieldOverrides: Map[String, FieldOverride] = Map.empty
  ): SourceDeclaration =
    SourceDeclaration(
      sourceName = "test",
      entityPath = "table",
      role = SourceRole.Primary,
      direction = FlowDirection.InOut,
      mode = mode,
      mappings = mappings,
      exclude = exclude,
      includeExtra = Nil,
      readonly = false,
      defaultTypePolicy = TypePolicy.Exact,
      fieldOverrides = fieldOverrides
    )

  private def mkSourceEntity(fields: List[SourceField]): SourceEntity =
    SourceEntity(
      sourceName = "test",
      entityPath = "table",
      sourceType = SourceEntityType.Table,
      fields = fields
    )

  test("field present in both: auto-forward") {
    val domainType = mkDomainType(
      List(
        DomainField(name = "id", typeName = "Int", nullable = false, array = false, description = None)
      )
    )
    val sourceEntity = mkSourceEntity(
      List(
        SourceField(name = "id", typeName = "integer", nullable = false, isPrimaryKey = true, comment = None)
      )
    )

    val actions = SmartDefaults.resolveFieldActions(domainType, mkSourceDecl(), sourceEntity, nameAligner)

    assert(actions.exists {
      case ResolvedFieldAction.Forward("id", "id", _, true) => true
      case _                                                => false
    })
  }

  test("field in domain but not in source: auto-drop") {
    val domainType = mkDomainType(
      List(
        DomainField(name = "email", typeName = "String", nullable = true, array = false, description = None)
      )
    )
    val sourceEntity = mkSourceEntity(
      List(
        SourceField(name = "id", typeName = "integer", nullable = false, isPrimaryKey = true, comment = None)
      )
    )

    val actions = SmartDefaults.resolveFieldActions(domainType, mkSourceDecl(), sourceEntity, nameAligner)

    assert(actions.exists {
      case ResolvedFieldAction.Drop("email", None, true) => true
      case _                                             => false
    })
  }

  test("explicit Forward override: uses override") {
    val domainType = mkDomainType(
      List(
        DomainField(name = "customer_name", typeName = "String", nullable = false, array = false, description = None)
      )
    )
    val sourceEntity = mkSourceEntity(
      List(
        SourceField(name = "cust_name", typeName = "varchar", nullable = false, isPrimaryKey = false, comment = None)
      )
    )
    val overrides = Map(
      "customer_name" -> FieldOverride.Forward(
        sourceFieldName = Some("cust_name"),
        typePolicy = Some(TypePolicy.AllowTruncation),
        directionOverride = None
      )
    )

    val actions = SmartDefaults.resolveFieldActions(
      domainType,
      mkSourceDecl(fieldOverrides = overrides),
      sourceEntity,
      nameAligner
    )

    assert(actions.exists {
      case ResolvedFieldAction.Forward("customer_name", "cust_name", TypePolicy.AllowTruncation, false) => true
      case _                                                                                            => false
    })
  }

  test("explicit Drop override: uses drop") {
    val domainType = mkDomainType(
      List(
        DomainField(name = "legacy_field", typeName = "String", nullable = true, array = false, description = None)
      )
    )
    val sourceEntity = mkSourceEntity(
      List(
        SourceField(name = "legacy_field", typeName = "varchar", nullable = true, isPrimaryKey = false, comment = None)
      )
    )
    val overrides = Map("legacy_field" -> FieldOverride.Drop(reason = Some("deprecated")))

    val actions = SmartDefaults.resolveFieldActions(
      domainType,
      mkSourceDecl(fieldOverrides = overrides),
      sourceEntity,
      nameAligner
    )

    assert(actions.exists {
      case ResolvedFieldAction.Drop("legacy_field", Some("deprecated"), false) => true
      case _                                                                   => false
    })
  }

  test("source field not in domain, Exact mode: unannotated") {
    val domainType = mkDomainType(
      List(
        DomainField(name = "id", typeName = "Int", nullable = false, array = false, description = None)
      )
    )
    val sourceEntity = mkSourceEntity(
      List(
        SourceField(name = "id", typeName = "integer", nullable = false, isPrimaryKey = true, comment = None),
        SourceField(name = "extra_col", typeName = "varchar", nullable = true, isPrimaryKey = false, comment = None)
      )
    )

    val actions = SmartDefaults.resolveFieldActions(
      domainType,
      mkSourceDecl(mode = CompatibilityMode.Exact),
      sourceEntity,
      nameAligner
    )

    assert(actions.exists {
      case ResolvedFieldAction.Unannotated("extra_col", "varchar", true) => true
      case _                                                             => false
    })
  }

  test("source field not in domain, Superset mode: ignored") {
    val domainType = mkDomainType(
      List(
        DomainField(name = "id", typeName = "Int", nullable = false, array = false, description = None)
      )
    )
    val sourceEntity = mkSourceEntity(
      List(
        SourceField(name = "id", typeName = "integer", nullable = false, isPrimaryKey = true, comment = None),
        SourceField(name = "extra_col", typeName = "varchar", nullable = true, isPrimaryKey = false, comment = None)
      )
    )

    val actions = SmartDefaults.resolveFieldActions(
      domainType,
      mkSourceDecl(mode = CompatibilityMode.Superset),
      sourceEntity,
      nameAligner
    )

    assert(!actions.exists(_.isInstanceOf[ResolvedFieldAction.Unannotated]))
  }

  test("source field in exclude: ignored even in Exact mode") {
    val domainType = mkDomainType(
      List(
        DomainField(name = "id", typeName = "Int", nullable = false, array = false, description = None)
      )
    )
    val sourceEntity = mkSourceEntity(
      List(
        SourceField(name = "id", typeName = "integer", nullable = false, isPrimaryKey = true, comment = None),
        SourceField(name = "audit_col", typeName = "timestamp", nullable = true, isPrimaryKey = false, comment = None)
      )
    )

    val actions = SmartDefaults.resolveFieldActions(
      domainType,
      mkSourceDecl(mode = CompatibilityMode.Exact, exclude = Set("audit_col")),
      sourceEntity,
      nameAligner
    )

    assert(!actions.exists {
      case ResolvedFieldAction.Unannotated("audit_col", _, _) => true
      case _                                                  => false
    })
  }

  test("MergeFrom override: resolved as Custom") {
    val domainType = mkDomainType(
      List(
        DomainField(name = "full_name", typeName = "String", nullable = false, array = false, description = None)
      )
    )
    val sourceEntity = mkSourceEntity(
      List(
        SourceField(name = "first_name", typeName = "varchar", nullable = false, isPrimaryKey = false, comment = None),
        SourceField(name = "last_name", typeName = "varchar", nullable = false, isPrimaryKey = false, comment = None)
      )
    )
    val overrides = Map("full_name" -> FieldOverride.Custom(CustomKind.MergeFrom(List("first_name", "last_name"))))

    val actions = SmartDefaults.resolveFieldActions(
      domainType,
      mkSourceDecl(fieldOverrides = overrides),
      sourceEntity,
      nameAligner
    )

    assert(actions.exists {
      case ResolvedFieldAction.Custom("full_name", CustomKind.MergeFrom(List("first_name", "last_name"))) => true
      case _                                                                                              => false
    })
  }
}

package typr
package internal
package codegen

/** Generates mapper methods for Bridge composite type projections.
  *
  * For each projection that maps to a known relation, generates:
  *   - fromXxx: converts from the source row type to the bridge type
  *   - toXxx: converts from the bridge type to the source row type (if not readonly)
  */
object FileBridgeProjectionMapper {

  case class ResolvedProjection(
      projectionKey: String,
      projection: BridgeProjection,
      table: ComputedTable,
      fieldMappings: List[FieldMapping]
  )

  case class FieldMapping(
      bridgeField: ComputedBridgeField,
      sourceColumn: ComputedColumn
  )

  def generateMapperMethods(
      computed: ComputedBridgeCompositeType,
      options: InternalOptions,
      relationLookup: db.RelationName => Option[ComputedTable]
  ): List[jvm.ClassMember] = {
    if (!computed.generateMappers) {
      return Nil
    }

    val lang = options.lang
    val resolvedProjections = resolveProjections(computed, relationLookup)

    resolvedProjections.flatMap { resolved =>
      generateMethodsForProjection(computed, resolved, lang)
    }
  }

  private def resolveProjections(
      computed: ComputedBridgeCompositeType,
      relationLookup: db.RelationName => Option[ComputedTable]
  ): List[ResolvedProjection] = {
    val resolved = computed.projections.toList.flatMap { case (key, projection) =>
      val relationName = parseEntityPath(projection.entityPath)
      relationLookup(relationName).map { table =>
        val fieldMappings = resolveFieldMappings(computed.fields, table, projection)
        ResolvedProjection(key, projection, table, fieldMappings)
      }
    }
    resolved.distinctBy(_.table.names.RowName)
  }

  private def parseEntityPath(entityPath: String): db.RelationName = {
    entityPath.split("\\.").toList match {
      case schema :: name :: Nil => db.RelationName(Some(schema), name)
      case name :: Nil           => db.RelationName(None, name)
      case _                     => db.RelationName(None, entityPath)
    }
  }

  private def resolveFieldMappings(
      bridgeFields: List[ComputedBridgeField],
      table: ComputedTable,
      projection: BridgeProjection
  ): List[FieldMapping] = {
    val columnsByDbName = table.cols.map(c => c.dbName.value.toLowerCase -> c).toMap

    bridgeFields.flatMap { bridgeField =>
      val sourceFieldName = projection.mappings.getOrElse(bridgeField.name.value, bridgeField.name.value)
      columnsByDbName.get(sourceFieldName.toLowerCase).map { sourceColumn =>
        FieldMapping(bridgeField, sourceColumn)
      }
    }
  }

  private def generateMethodsForProjection(
      computed: ComputedBridgeCompositeType,
      resolved: ResolvedProjection,
      lang: Lang
  ): List[jvm.ClassMember] = {
    val sourceRowType = resolved.table.names.RowName
    val methodSuffix = sanitizeName(resolved.projectionKey)

    val fromMethod = generateFromSourceMethod(computed, resolved, sourceRowType, methodSuffix, lang)
    val toMethod =
      if (resolved.projection.readonly) None
      else Some(generateToSourceMethod(computed, resolved, sourceRowType, methodSuffix, lang))

    fromMethod :: toMethod.toList
  }

  private def sanitizeName(key: String): String = {
    key.split(":").last.split("\\.").map(_.capitalize).mkString("")
  }

  private def generateFromSourceMethod(
      computed: ComputedBridgeCompositeType,
      resolved: ResolvedProjection,
      sourceRowType: jvm.Type.Qualified,
      methodSuffix: String,
      lang: Lang
  ): jvm.Method = {
    val sourceParam = jvm.Param(jvm.Ident("source"), sourceRowType)

    val fieldArgs = resolved.fieldMappings.map { mapping =>
      val sourceAccess = lang.propertyGetterAccess(jvm.Ident("source").code, mapping.sourceColumn.name)
      val value = convertSourceToBridge(sourceAccess, mapping, lang)
      jvm.Arg.Named(mapping.bridgeField.name, value)
    }

    val body = jvm.New(computed.tpe, fieldArgs)

    jvm.Method(
      annotations = Nil,
      comments = scaladoc(List(s"Convert from ${sourceRowType.value.name.value} to ${computed.name}")),
      tparams = Nil,
      name = jvm.Ident(s"from$methodSuffix"),
      params = List(sourceParam),
      implicitParams = Nil,
      tpe = computed.tpe,
      throws = Nil,
      body = jvm.Body.Expr(body.code),
      isOverride = false,
      isDefault = false
    )
  }

  private def generateToSourceMethod(
      computed: ComputedBridgeCompositeType,
      resolved: ResolvedProjection,
      sourceRowType: jvm.Type.Qualified,
      methodSuffix: String,
      lang: Lang
  ): jvm.Method = {
    val bridgeParam = jvm.Param(jvm.Ident("bridge"), computed.tpe)

    val fieldArgs = resolved.fieldMappings.map { mapping =>
      val bridgeAccess = lang.propertyGetterAccess(jvm.Ident("bridge").code, mapping.bridgeField.name)
      val value = convertBridgeToSource(bridgeAccess, mapping, lang)
      jvm.Arg.Named(mapping.sourceColumn.name, value)
    }

    val body = jvm.New(sourceRowType, fieldArgs)

    jvm.Method(
      annotations = Nil,
      comments = scaladoc(List(s"Convert from ${computed.name} to ${sourceRowType.value.name.value}")),
      tparams = Nil,
      name = jvm.Ident(s"to$methodSuffix"),
      params = List(bridgeParam),
      implicitParams = Nil,
      tpe = sourceRowType,
      throws = Nil,
      body = jvm.Body.Expr(body.code),
      isOverride = false,
      isDefault = false
    )
  }

  private def convertSourceToBridge(
      sourceValue: jvm.Code,
      mapping: FieldMapping,
      lang: Lang
  ): jvm.Code = {
    val sourceIsOptional = lang.Optional.unapply(mapping.sourceColumn.tpe).isDefined
    val bridgeIsOptional = mapping.bridgeField.nullable

    (sourceIsOptional, bridgeIsOptional) match {
      case (true, true)   => sourceValue
      case (true, false)  => lang.Optional.get(sourceValue)
      case (false, true)  => lang.Optional.some(sourceValue)
      case (false, false) => sourceValue
    }
  }

  private def convertBridgeToSource(
      bridgeValue: jvm.Code,
      mapping: FieldMapping,
      lang: Lang
  ): jvm.Code = {
    val sourceIsOptional = lang.Optional.unapply(mapping.sourceColumn.tpe).isDefined
    val bridgeIsOptional = mapping.bridgeField.nullable

    (bridgeIsOptional, sourceIsOptional) match {
      case (true, true)   => bridgeValue
      case (true, false)  => lang.Optional.get(bridgeValue)
      case (false, true)  => lang.Optional.some(bridgeValue)
      case (false, false) => bridgeValue
    }
  }
}

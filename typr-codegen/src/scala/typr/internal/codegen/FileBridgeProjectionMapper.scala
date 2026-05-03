package typr
package internal
package codegen

/** Generates mapper methods for Bridge composite type projections.
  *
  * For each projection that maps to a known relation or external record, generates:
  *   - fromXxx: converts from the source row type to the bridge type
  *   - toXxx: converts from the bridge type to the source row type (if not readonly)
  */
object FileBridgeProjectionMapper {

  /** Abstract representation of an external record source (e.g., Avro, Protobuf) */
  case class ExternalRecord(
      tpe: jvm.Type.Qualified,
      fields: List[ExternalField]
  )

  case class ExternalField(
      name: jvm.Ident,
      protoName: String,
      jvmType: jvm.Type,
      isNullable: Boolean
  )

  case class ResolvedProjection(
      projectionKey: String,
      projection: BridgeProjection,
      table: ComputedTable,
      fieldMappings: List[FieldMapping]
  )

  case class ResolvedExternalProjection(
      projectionKey: String,
      projection: BridgeProjection,
      externalRecord: ExternalRecord,
      fieldMappings: List[ExternalFieldMapping]
  )

  case class FieldMapping(
      bridgeField: ComputedBridgeField,
      sourceColumn: ComputedColumn
  )

  case class ExternalFieldMapping(
      bridgeField: ComputedBridgeField,
      externalField: ExternalField
  )

  def generateMapperMethods(
      computed: ComputedBridgeCompositeType,
      options: InternalOptions,
      relationLookup: db.RelationName => Option[ComputedTable],
      externalRecordLookup: String => Option[ExternalRecord] = _ => None
  ): List[jvm.ClassMember] = {
    if (!computed.generateMappers) {
      return Nil
    }

    val lang = options.lang
    val resolvedProjections = resolveProjections(computed, relationLookup)
    val resolvedExternalProjections = resolveExternalProjections(computed, externalRecordLookup)

    val dbMethods = resolvedProjections.flatMap { resolved =>
      generateMethodsForProjection(computed, resolved, lang)
    }

    val externalMethods = resolvedExternalProjections.flatMap { resolved =>
      generateMethodsForExternalProjection(computed, resolved, lang)
    }

    dbMethods ++ externalMethods
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

  private def resolveExternalProjections(
      computed: ComputedBridgeCompositeType,
      externalRecordLookup: String => Option[ExternalRecord]
  ): List[ResolvedExternalProjection] = {
    val resolved = computed.projections.toList.flatMap { case (key, projection) =>
      externalRecordLookup(projection.entityPath).map { externalRecord =>
        val fieldMappings = resolveExternalFieldMappings(computed.fields, externalRecord, projection)
        ResolvedExternalProjection(key, projection, externalRecord, fieldMappings)
      }
    }
    resolved.distinctBy(_.externalRecord.tpe)
  }

  private def resolveExternalFieldMappings(
      bridgeFields: List[ComputedBridgeField],
      externalRecord: ExternalRecord,
      projection: BridgeProjection
  ): List[ExternalFieldMapping] = {
    val externalFieldsByName = externalRecord.fields.map(f => f.protoName.toLowerCase -> f).toMap

    bridgeFields.flatMap { bridgeField =>
      val sourceFieldName = projection.mappings.getOrElse(bridgeField.name.value, bridgeField.name.value)
      externalFieldsByName.get(sourceFieldName.toLowerCase).map { externalField =>
        ExternalFieldMapping(bridgeField, externalField)
      }
    }
  }

  private def generateMethodsForExternalProjection(
      computed: ComputedBridgeCompositeType,
      resolved: ResolvedExternalProjection,
      lang: Lang
  ): List[jvm.ClassMember] = {
    val sourceRowType = resolved.externalRecord.tpe
    val methodSuffix = sanitizeName(resolved.projectionKey)

    val fromMethod = generateFromExternalSourceMethod(computed, resolved, sourceRowType, methodSuffix, lang)
    val toMethod =
      if (resolved.projection.readonly) None
      else Some(generateToExternalSourceMethod(computed, resolved, sourceRowType, methodSuffix, lang))

    fromMethod :: toMethod.toList
  }

  private def generateFromExternalSourceMethod(
      computed: ComputedBridgeCompositeType,
      resolved: ResolvedExternalProjection,
      sourceRowType: jvm.Type.Qualified,
      methodSuffix: String,
      lang: Lang
  ): jvm.Method = {
    val sourceParam = jvm.Param(jvm.Ident("source"), sourceRowType)

    val fieldArgs = resolved.fieldMappings.map { mapping =>
      val sourceAccess = lang.propertyGetterAccess(jvm.Ident("source").code, mapping.externalField.name)
      val value = convertExternalSourceToBridge(sourceAccess, mapping, lang)
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

  private def generateToExternalSourceMethod(
      computed: ComputedBridgeCompositeType,
      resolved: ResolvedExternalProjection,
      sourceRowType: jvm.Type.Qualified,
      methodSuffix: String,
      lang: Lang
  ): jvm.Method = {
    val bridgeParam = jvm.Param(jvm.Ident("bridge"), computed.tpe)

    val fieldArgs = resolved.fieldMappings.map { mapping =>
      val bridgeAccess = lang.propertyGetterAccess(jvm.Ident("bridge").code, mapping.bridgeField.name)
      val value = convertBridgeToExternalSource(bridgeAccess, mapping, lang)
      jvm.Arg.Named(mapping.externalField.name, value)
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

  private def convertExternalSourceToBridge(
      sourceValue: jvm.Code,
      mapping: ExternalFieldMapping,
      lang: Lang
  ): jvm.Code = {
    val sourceIsOptional = mapping.externalField.isNullable
    val bridgeIsOptional = mapping.bridgeField.nullable

    (sourceIsOptional, bridgeIsOptional) match {
      case (true, true)   => sourceValue
      case (true, false)  => lang.Optional.get(sourceValue)
      case (false, true)  => lang.Optional.some(sourceValue)
      case (false, false) => sourceValue
    }
  }

  private def convertBridgeToExternalSource(
      bridgeValue: jvm.Code,
      mapping: ExternalFieldMapping,
      lang: Lang
  ): jvm.Code = {
    val sourceIsOptional = mapping.externalField.isNullable
    val bridgeIsOptional = mapping.bridgeField.nullable

    (bridgeIsOptional, sourceIsOptional) match {
      case (true, true)   => bridgeValue
      case (true, false)  => lang.Optional.get(bridgeValue)
      case (false, true)  => lang.Optional.some(bridgeValue)
      case (false, false) => bridgeValue
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

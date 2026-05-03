package typr.avro

import typr.avro.codegen._
import typr.avro.parser.{AvroParseError, AvroParser, ProtocolParser, SchemaRegistryClient}
import typr.internal.codegen.FilePreciseType
import typr.openapi.codegen.{JsonLibSupport, NoJsonLibSupport}
import typr.{jvm, Lang, Naming, Scope}

/** Main entry point for Avro/Kafka code generation */
object AvroCodegen {

  case class Result(
      files: List[jvm.File],
      errors: List[String]
  )

  /** Generate code from Avro schema files */
  def generate(
      options: AvroOptions,
      lang: Lang
  ): Result = {
    loadSchemas(options.schemaSource, options.schemaEvolution) match {
      case Left(error) =>
        Result(Nil, List(error.message))
      case Right(schemaFiles) =>
        // Load protocols if enabled
        val protocols = if (options.generateProtocols) {
          loadProtocols(options.schemaSource) match {
            case Left(error) =>
              return Result(Nil, List(error.message))
            case Right(protos) =>
              protos
          }
        } else {
          Nil
        }
        generateFromSchemas(schemaFiles, protocols, options, lang)
    }
  }

  /** Load schemas from the configured source */
  private def loadSchemas(source: SchemaSource, schemaEvolution: SchemaEvolution): Either[AvroParseError, List[AvroSchemaFile]] = source match {
    case SchemaSource.Directory(path) =>
      AvroParser.parseDirectory(path)

    case SchemaSource.Registry(url) =>
      SchemaRegistryClient.fetchSchemasWithEvolution(url, schemaEvolution)

    case SchemaSource.Multi(sources) =>
      val results = sources.map(s => loadSchemas(s, schemaEvolution))
      val errors = results.collect { case Left(e) => e }
      if (errors.nonEmpty) {
        Left(AvroParseError.MultipleErrors(errors.map(_.message)))
      } else {
        Right(results.collect { case Right(schemas) => schemas }.flatten)
      }
  }

  /** Load protocols from the configured source */
  private def loadProtocols(source: SchemaSource): Either[AvroParseError, List[AvroProtocol]] = source match {
    case SchemaSource.Directory(path) =>
      ProtocolParser.parseDirectory(path)

    case SchemaSource.Registry(_) =>
      // Protocols are not typically stored in Schema Registry
      Right(Nil)

    case SchemaSource.Multi(sources) =>
      val results = sources.map(loadProtocols)
      val errors = results.collect { case Left(e) => e }
      if (errors.nonEmpty) {
        Left(AvroParseError.MultipleErrors(errors.map(_.message)))
      } else {
        Right(results.collect { case Right(protocols) => protocols }.flatten)
      }
  }

  /** Generate code from loaded schema files */
  private def generateFromSchemas(
      schemaFiles: List[AvroSchemaFile],
      protocols: List[AvroProtocol],
      options: AvroOptions,
      lang: Lang
  ): Result = {
    val naming = new Naming(options.pkg, lang)

    // Handle schema evolution if enabled
    val versionedCodegen = new VersionedRecordCodegen(naming, lang)
    val (processedSchemaFiles, versionedGroups) = if (options.schemaEvolution != SchemaEvolution.LatestOnly) {
      val (groups, nonVersioned) = versionedCodegen.groupVersionedSchemas(schemaFiles)

      // Rename versioned schemas with V{n} suffix
      val renamedVersioned = groups.flatMap { group =>
        group.versions.map { case (version, sf) =>
          versionedCodegen.renameSchemaWithVersion(sf, version)
        }
      }

      (renamedVersioned ++ nonVersioned, groups)
    } else {
      (schemaFiles, Nil)
    }

    // Separate value schemas from key schemas
    val valueSchemaFiles = processedSchemaFiles.filter(_.schemaRole == SchemaRole.Value)
    val keySchemaFiles = processedSchemaFiles.filter(_.schemaRole == SchemaRole.Key)

    // Build map from topic name to key schema record
    val keySchemasByTopic: Map[String, AvroRecord] = keySchemaFiles.flatMap { file =>
      file.primarySchema match {
        case r: AvroRecord =>
          // The source path contains the topic name for registry schemas
          val topicName = file.sourcePath.getOrElse(toTopicName(r.name))
          Some(topicName -> r)
        case _ => None
      }
    }.toMap

    val allSchemas = processedSchemaFiles.flatMap(f => f.primarySchema :: f.inlineSchemas)
    val enumNames: Set[String] = allSchemas.collect { case e: AvroEnum => e.fullName }.toSet

    // Build a map of all schemas by full name for inlining references
    val schemasByName: Map[String, AvroSchema] = allSchemas.map(s => s.fullName -> s).toMap

    // Collect complex unions from all records if union type generation is enabled
    val unionTypeCodegen = new UnionTypeCodegen(naming, lang)
    val allRecordsForUnions: List[AvroRecord] = allSchemas.collect { case r: AvroRecord => r }
    val complexUnions: Set[AvroType.Union] = if (options.generateUnionTypes) {
      allRecordsForUnions.flatMap { record =>
        record.fields.flatMap(f => AvroType.extractComplexUnions(f.fieldType))
      }.toSet
    } else {
      Set.empty
    }

    // Generate type names for complex unions
    val unionTypeNames: Map[AvroType.Union, jvm.Type.Qualified] = complexUnions.map { union =>
      val normalizedUnion = normalizeUnion(union)
      normalizedUnion -> unionTypeCodegen.generateUnionTypeName(
        union,
        options.pkg.idents.map(_.value).mkString(".") match {
          case "" => None
          case ns => Some(ns)
        }
      )
    }.toMap

    // Collect all records for wrapper type collection
    val allRecordsForWrappers: List[AvroRecord] = allSchemas.collect { case r: AvroRecord => r }

    // Create base type mapper (without wrapper types) for wrapper collection
    val baseTypeMapper = new AvroTypeMapper(
      lang = lang,
      unionTypeNames = unionTypeNames,
      naming = if (options.enablePreciseTypes) Some(naming) else None,
      enablePreciseTypes = options.enablePreciseTypes,
      wrapperTypeMap = Map.empty
    )

    // Collect wrapper types from all records
    val computedWrappers = ComputedAvroWrapper.collect(allRecordsForWrappers, baseTypeMapper, naming)

    // Build wrapper type lookup map: (namespace, wrapperName) -> QualifiedType
    val wrapperTypeMap: Map[(Option[String], String), jvm.Type.Qualified] =
      computedWrappers.map { w =>
        val namespace = w.tpe.value.parentOpt.map(_.idents.map(_.value).mkString("."))
        (namespace, w.tpe.value.name.value) -> w.tpe
      }.toMap

    // Create type mapper with union type names and precise types support
    val typeMapper = new AvroTypeMapper(
      lang = lang,
      unionTypeNames = unionTypeNames,
      naming = if (options.enablePreciseTypes) Some(naming) else None,
      enablePreciseTypes = options.enablePreciseTypes,
      wrapperTypeMap = wrapperTypeMap
    )

    val avroWireFormat = AvroWireFormatSupport(
      avroWireFormat = options.avroWireFormat,
      lang = lang,
      enumNames = enumNames,
      unionTypeNames = unionTypeNames,
      naming = if (options.enablePreciseTypes) Some(naming) else None,
      enablePreciseTypes = options.enablePreciseTypes,
      wrapperTypeMap = wrapperTypeMap
    )

    // Create AvroLib instance for wrapper type file generation
    val avroLib = new AvroLibGenericRecord(lang)

    // Extract JsonLibSupport from wire format for wrapper types
    // Only JsonEncoded wire format provides JSON library support
    val jsonLibSupport: JsonLibSupport = options.avroWireFormat match {
      case AvroWireFormat.JsonEncoded(jsonLib) => jsonLib
      case _                                   => NoJsonLibSupport
    }

    val recordCodegen = new RecordCodegen(
      naming = naming,
      typeMapper = typeMapper,
      lang = lang,
      avroWireFormat = avroWireFormat,
      jsonSchema = record => schemaToJson(record, schemasByName),
      wrapperTypeMap = wrapperTypeMap,
      jsonLibSupport = jsonLibSupport
    )

    val files = List.newBuilder[jvm.File]
    val errors = List.newBuilder[String]

    // Generate precise types if enabled
    if (options.enablePreciseTypes) {
      val preciseConstraints = collectPreciseConstraints(processedSchemaFiles)
      preciseConstraints.foreach { constraint =>
        try {
          files += generatePreciseType(constraint, naming, lang)
        } catch {
          case e: Exception =>
            errors += s"Failed to generate precise type: ${e.getMessage}"
        }
      }
    }

    // Generate wrapper types from x-typr-wrapper annotations
    if (options.generateRecords && computedWrappers.nonEmpty) {
      computedWrappers.foreach { wrapper =>
        files += FileAvroWrapper(wrapper, avroLib, jsonLibSupport, lang)
      }
    }

    // Build event groups from directory structure (auto-detection)
    val directoryGroups: List[AvroEventGroup] = {
      // Group schema files by their directory group
      val byDirectory = schemaFiles
        .filter(_.directoryGroup.isDefined)
        .groupBy(_.directoryGroup.get)

      byDirectory.toList.flatMap { case (dirName, files) =>
        // Only create a group if there are multiple records in the directory
        val recordMembers = files.flatMap { f =>
          f.primarySchema match {
            case r: AvroRecord => Some(r)
            case _             => None
          }
        }
        if (recordMembers.size >= 2) {
          // Convert directory name to group name (e.g., "order-events" -> "OrderEvents")
          val groupName = typr.Naming.titleCase(dirName)
          // Infer namespace from the first member
          val namespace = recordMembers.headOption.flatMap(_.namespace)
          Some(AvroEventGroup(groupName, namespace, doc = None, recordMembers))
        } else {
          None
        }
      }
    }

    // Build event groups from manual topicGroups configuration
    val manualGroups: List[AvroEventGroup] = options.topicGroups.toList.flatMap { case (groupName, schemaNames) =>
      val members = schemaNames.flatMap { name =>
        schemasByName.get(name).collect { case r: AvroRecord => r }
      }
      if (members.nonEmpty) {
        // Infer namespace from the first member
        val namespace = members.headOption.flatMap(_.namespace)
        Some(AvroEventGroup(groupName, namespace, doc = None, members))
      } else {
        errors += s"Event group '$groupName' has no valid record members (schemas: ${schemaNames.mkString(", ")})"
        None
      }
    }

    // Combine groups: manual groups take precedence over directory-detected groups
    // If a record is in both a manual group and directory group, manual wins
    val manualGroupRecords = manualGroups.flatMap(_.members.map(_.fullName)).toSet
    val filteredDirectoryGroups = directoryGroups
      .map { group =>
        group.copy(members = group.members.filterNot(m => manualGroupRecords.contains(m.fullName)))
      }
      .filter(_.members.size >= 2)

    val eventGroups: List[AvroEventGroup] = manualGroups ++ filteredDirectoryGroups

    // Map from record full name to its parent event group type
    val recordToGroupType: Map[String, jvm.Type.Qualified] = eventGroups.flatMap { group =>
      val groupType = naming.avroEventGroupTypeName(group.name, group.namespace)
      group.members.map(m => m.fullName -> groupType)
    }.toMap

    // Collect all records for serde generation
    val allRecords: List[AvroRecord] = allSchemas.collect { case r: AvroRecord => r }

    if (options.generateRecords) {
      // Generate event group sealed interfaces first
      eventGroups.foreach { group =>
        try {
          files += recordCodegen.generateEventGroup(group)
        } catch {
          case e: Exception =>
            errors += s"Failed to generate event group ${group.name}: ${e.getMessage}"
        }
      }

      // Generate union types for complex unions
      if (options.generateUnionTypes) {
        complexUnions.foreach { union =>
          try {
            val unionTypeName = unionTypeNames(normalizeUnion(union))
            files += unionTypeCodegen.generate(union, unionTypeName)
          } catch {
            case e: Exception =>
              errors += s"Failed to generate union type: ${e.getMessage}"
          }
        }
      }

      // Generate type aliases for latest versions when schema evolution is enabled
      if (options.schemaEvolution != SchemaEvolution.LatestOnly) {
        versionedGroups.foreach { group =>
          try {
            files += versionedCodegen.generateLatestTypeAlias(group)
          } catch {
            case e: Exception =>
              errors += s"Failed to generate type alias for ${group.baseName}: ${e.getMessage}"
          }
        }

        // Generate migration helpers when WithMigrations mode
        if (options.schemaEvolution == SchemaEvolution.WithMigrations) {
          val migrationCodegen = new MigrationCodegen(naming, lang)
          versionedGroups.foreach { group =>
            try {
              migrationCodegen.generateMigrationClass(group, typeMapper).foreach { file =>
                files += file
              }
            } catch {
              case e: Exception =>
                errors += s"Failed to generate migrations for ${group.baseName}: ${e.getMessage}"
            }
          }
        }
      }

      processedSchemaFiles.foreach { schemaFile =>
        schemaFile.primarySchema match {
          case record: AvroRecord =>
            try {
              val parentType = recordToGroupType.get(record.fullName)
              files += recordCodegen.generate(record, parentType)
            } catch {
              case e: Exception =>
                errors += s"Failed to generate ${record.name}: ${e.getMessage}"
            }

          case avroEnum: AvroEnum =>
            try {
              files += recordCodegen.generateEnum(avroEnum)
            } catch {
              case e: Exception =>
                errors += s"Failed to generate enum ${avroEnum.name}: ${e.getMessage}"
            }

          case fixed: AvroFixed =>
            errors += s"Fixed types are not yet supported: ${fixed.name}"

          case _: AvroError =>
          // Errors are protocol types, not standalone schema files
        }

        // Generate inline schemas (nested records/enums)
        schemaFile.inlineSchemas.foreach {
          case record: AvroRecord =>
            try {
              val parentType = recordToGroupType.get(record.fullName)
              files += recordCodegen.generate(record, parentType)
            } catch {
              case e: Exception =>
                errors += s"Failed to generate inline ${record.name}: ${e.getMessage}"
            }

          case avroEnum: AvroEnum =>
            try {
              files += recordCodegen.generateEnum(avroEnum)
            } catch {
              case e: Exception =>
                errors += s"Failed to generate inline enum ${avroEnum.name}: ${e.getMessage}"
            }

          case fixed: AvroFixed =>
            errors += s"Fixed types are not yet supported: ${fixed.name}"

          case _: AvroError =>
          // Errors are protocol types, not inline schemas
        }
      }
    }

    // Generate Kafka Serializers, Deserializers, and Serdes
    // For JSON wire format, serdes are not generated - users use framework-provided JSON serializers
    // (e.g., Spring's JsonSerializer, Quarkus's auto-generated serdes)
    val isJsonWireFormat = options.avroWireFormat.isInstanceOf[AvroWireFormat.JsonEncoded]
    if (options.generateSerdes && !isJsonWireFormat) {
      val serdeCodegen = new SerdeCodegen(naming, lang, options.avroWireFormat)

      allRecords.foreach { record =>
        try {
          // Serde now implements Serializer and Deserializer directly
          files += serdeCodegen.generateSerde(record)
        } catch {
          case e: Exception =>
            errors += s"Failed to generate serde for ${record.name}: ${e.getMessage}"
        }
      }

      // Generate serdes for event groups (sealed types)
      eventGroups.foreach { group =>
        try {
          files += serdeCodegen.generateEventGroupSerde(group)
        } catch {
          case e: Exception =>
            errors += s"Failed to generate serde for event group ${group.name}: ${e.getMessage}"
        }
      }
    }

    // Generate typed header classes
    // Skip for JSON wire format - headers use Kafka types
    if (options.generateHeaders && options.headerSchemas.nonEmpty && !isJsonWireFormat) {
      val headerCodegen = new HeaderCodegen(naming, lang)

      options.headerSchemas.foreach { case (name, schema) =>
        try {
          files += headerCodegen.generateHeaderClass(name, schema)
        } catch {
          case e: Exception =>
            errors += s"Failed to generate header class for '$name': ${e.getMessage}"
        }
      }
    }

    // Generate topic bindings (TypedTopic constants)
    // Skip for JSON wire format - topic bindings use Kafka types
    if (options.generateTopicBindings && !isJsonWireFormat) {
      val topicBindingsCodegen = new TopicBindingsCodegen(naming, lang, options)

      try {
        // Generate TypedTopic class first
        files += topicBindingsCodegen.generateTypedTopicClass()

        // Generate Topics class with topic constants, passing key schemas from registry
        topicBindingsCodegen.generateTopicsClass(allRecords, eventGroups, keySchemasByTopic).foreach { file =>
          files += file
        }
      } catch {
        case e: Exception =>
          errors += s"Failed to generate topic bindings: ${e.getMessage}"
      }
    }

    // Generate typed producers
    // Skip for JSON wire format - producers use Avro/Kafka serialization
    // Skip when framework integration generates EventPublisher instead (e.g., Cats with fs2-kafka)
    val hasFrameworkPublishers = options.frameworkIntegration.kafkaFramework.isDefined && options.generateKafkaEvents
    if (options.generateProducers && !isJsonWireFormat && !hasFrameworkPublishers) {
      val producerCodegen = new ProducerCodegen(naming, lang, options)

      // Generate producers for standalone records (not in event groups)
      val eventGroupRecordNames = eventGroups.flatMap(_.members.map(_.fullName)).toSet
      allRecords.filterNot(r => eventGroupRecordNames.contains(r.fullName)).foreach { record =>
        try {
          files += producerCodegen.generateProducer(record)
        } catch {
          case e: Exception =>
            errors += s"Failed to generate producer for ${record.name}: ${e.getMessage}"
        }
      }

      // Generate producers for event groups
      eventGroups.foreach { group =>
        try {
          files += producerCodegen.generateEventGroupProducer(group)
        } catch {
          case e: Exception =>
            errors += s"Failed to generate producer for event group ${group.name}: ${e.getMessage}"
        }
      }
    }

    // Generate typed consumers (poll-based wrapper for raw Kafka usage)
    // Skip for JSON wire format - consumers use Avro/Kafka deserialization
    // Skip when a framework is set - frameworks provide their own consumer patterns via Listeners
    val hasFramework = options.frameworkIntegration.kafkaFramework.isDefined
    if (options.generateConsumers && !isJsonWireFormat && !hasFramework) {
      val consumerCodegen = new ConsumerCodegen(naming, lang, options)

      // Generate consumers for standalone records (not in event groups)
      val eventGroupRecordNames = eventGroups.flatMap(_.members.map(_.fullName)).toSet
      allRecords.filterNot(r => eventGroupRecordNames.contains(r.fullName)).foreach { record =>
        try {
          consumerCodegen.generateConsumer(record).foreach(files += _)
        } catch {
          case e: Exception =>
            errors += s"Failed to generate consumer for ${record.name}: ${e.getMessage}"
        }
      }

      // Generate consumers for event groups
      eventGroups.foreach { group =>
        try {
          consumerCodegen.generateEventGroupConsumer(group).foreach(files += _)
        } catch {
          case e: Exception =>
            errors += s"Failed to generate consumer for event group ${group.name}: ${e.getMessage}"
        }
      }
    }

    // Generate schema validator utility
    // Skip for JSON wire format - validator uses Avro Schema compatibility
    if (options.generateSchemaValidator && !isJsonWireFormat) {
      val validatorCodegen = new SchemaValidatorCodegen(naming, lang, options.compatibilityMode)
      try {
        files += validatorCodegen.generate(allRecords, eventGroups)
      } catch {
        case e: Exception =>
          errors += s"Failed to generate schema validator: ${e.getMessage}"
      }
    }

    // Generate protocol service interfaces (.avpr files)
    if (options.generateProtocols && protocols.nonEmpty) {
      val protocolCodegen = new ProtocolCodegen(naming, lang, options, typeMapper)

      protocols.foreach { protocol =>
        try {
          files ++= protocolCodegen.generate(protocol)
        } catch {
          case e: Exception =>
            errors += s"Failed to generate protocol ${protocol.name}: ${e.getMessage}"
        }
      }

      // Also generate record types defined within protocols
      if (options.generateRecords) {
        protocols.foreach { protocol =>
          protocol.types.foreach {
            case record: AvroRecord =>
              try {
                files += recordCodegen.generate(record, parentType = None)
              } catch {
                case e: Exception =>
                  errors += s"Failed to generate protocol record ${record.name}: ${e.getMessage}"
              }

            case avroEnum: AvroEnum =>
              try {
                files += recordCodegen.generateEnum(avroEnum)
              } catch {
                case e: Exception =>
                  errors += s"Failed to generate protocol enum ${avroEnum.name}: ${e.getMessage}"
              }

            case _: AvroFixed =>
            // Fixed types not yet supported

            case _: AvroError =>
            // Error types are generated by ProtocolCodegen
          }
        }
      }
    }

    // Generate framework-specific Kafka event publishers/listeners (Phase 2)
    options.frameworkIntegration.kafkaFramework.foreach { framework =>
      if (options.generateKafkaEvents) {
        val eventPublisherCodegen = new EventPublisherCodegen(naming, lang, options, framework)
        val eventListenerCodegen = new EventListenerCodegen(naming, lang, options, framework)

        // Generate publishers and listeners for event groups
        eventGroups.foreach { group =>
          try {
            files += eventPublisherCodegen.generateEventGroupPublisher(group)
            files += eventListenerCodegen.generateEventGroupListener(group)
          } catch {
            case e: Exception =>
              errors += s"Failed to generate event publisher/listener for ${group.name}: ${e.getMessage}"
          }
        }

        // Generate publishers and listeners for standalone records (not in event groups)
        val eventGroupRecordNames = eventGroups.flatMap(_.members.map(_.fullName)).toSet
        allRecords.filterNot(r => eventGroupRecordNames.contains(r.fullName)).foreach { record =>
          try {
            files += eventPublisherCodegen.generateRecordPublisher(record)
            files += eventListenerCodegen.generateRecordListener(record)
          } catch {
            case e: Exception =>
              errors += s"Failed to generate event publisher/listener for ${record.name}: ${e.getMessage}"
          }
        }
      }

      // Generate framework-specific Kafka RPC client/server (Phase 3)
      // Note: Cats framework only generates the server - fs2-kafka doesn't have a built-in request-reply pattern
      // like Spring's ReplyingKafkaTemplate, so clients need to be implemented manually.
      if (options.generateKafkaRpc && protocols.nonEmpty) {
        val kafkaRpcCodegen = new KafkaRpcCodegen(naming, lang, framework, typeMapper, jsonLibSupport, options.effectType)

        protocols.foreach { protocol =>
          try {
            files ++= kafkaRpcCodegen.generate(protocol)
          } catch {
            case e: Exception =>
              errors += s"Failed to generate Kafka RPC for ${protocol.name}: ${e.getMessage}"
          }
        }
      }
    }

    Result(files.result(), errors.result())
  }

  /** Convert an AvroRecord back to its JSON schema string.
    *
    * This inlines referenced schemas (enums, records) so the schema can be parsed in isolation.
    */
  private def schemaToJson(record: AvroRecord, allSchemas: Map[String, AvroSchema]): String = {
    // Track which schemas have been inlined to avoid duplicates (use first occurrence, then reference by name)
    val inlinedSchemas = scala.collection.mutable.Set.empty[String]

    def typeToJsonWithInlining(tpe: AvroType): String = tpe match {
      case AvroType.Null    => "\"null\""
      case AvroType.Boolean => "\"boolean\""
      case AvroType.Int     => "\"int\""
      case AvroType.Long    => "\"long\""
      case AvroType.Float   => "\"float\""
      case AvroType.Double  => "\"double\""
      case AvroType.Bytes   => "\"bytes\""
      case AvroType.String  => "\"string\""

      case AvroType.UUID =>
        """{"type": "string", "logicalType": "uuid"}"""

      case AvroType.Date =>
        """{"type": "int", "logicalType": "date"}"""

      case AvroType.TimeMillis =>
        """{"type": "int", "logicalType": "time-millis"}"""

      case AvroType.TimeMicros =>
        """{"type": "long", "logicalType": "time-micros"}"""

      case AvroType.TimeNanos =>
        """{"type": "long", "logicalType": "time-nanos"}"""

      case AvroType.TimestampMillis =>
        """{"type": "long", "logicalType": "timestamp-millis"}"""

      case AvroType.TimestampMicros =>
        """{"type": "long", "logicalType": "timestamp-micros"}"""

      case AvroType.TimestampNanos =>
        """{"type": "long", "logicalType": "timestamp-nanos"}"""

      case AvroType.LocalTimestampMillis =>
        """{"type": "long", "logicalType": "local-timestamp-millis"}"""

      case AvroType.LocalTimestampMicros =>
        """{"type": "long", "logicalType": "local-timestamp-micros"}"""

      case AvroType.LocalTimestampNanos =>
        """{"type": "long", "logicalType": "local-timestamp-nanos"}"""

      case AvroType.Duration =>
        """{"type": "fixed", "size": 12, "name": "duration", "logicalType": "duration"}"""

      case d: AvroType.DecimalBytes =>
        s"""{"type": "bytes", "logicalType": "decimal", "precision": ${d.precision}, "scale": ${d.scale}}"""

      case d: AvroType.DecimalFixed =>
        s"""{"type": "fixed", "size": ${d.fixedSize}, "name": "decimal_${d.precision}_${d.scale}", "logicalType": "decimal", "precision": ${d.precision}, "scale": ${d.scale}}"""

      case AvroType.Array(items) =>
        s"""{"type": "array", "items": ${typeToJsonWithInlining(items)}}"""

      case AvroType.Map(values) =>
        s"""{"type": "map", "values": ${typeToJsonWithInlining(values)}}"""

      case AvroType.Union(members) =>
        "[" + members.map(typeToJsonWithInlining).mkString(",") + "]"

      case AvroType.Named(fullName) =>
        // Inline the schema definition if not already inlined
        if (inlinedSchemas.contains(fullName)) {
          // Already inlined, just use the name reference
          s""""$fullName""""
        } else {
          allSchemas.get(fullName) match {
            case Some(avroEnum: AvroEnum) =>
              inlinedSchemas += fullName
              inlineEnumSchema(avroEnum)
            case Some(rec: AvroRecord) =>
              inlinedSchemas += fullName
              inlineRecordSchema(rec, typeToJsonWithInlining)
            case Some(fixed: AvroFixed) =>
              inlinedSchemas += fullName
              inlineFixedSchema(fixed)
            case Some(error: AvroError) =>
              // Error types are protocol-specific, treat like records for JSON inlining
              inlinedSchemas += fullName
              inlineErrorSchema(error, typeToJsonWithInlining)
            case None =>
              // Unknown reference, output as name
              s""""$fullName""""
          }
        }

      case AvroType.Record(rec) =>
        if (inlinedSchemas.contains(rec.fullName)) {
          s""""${rec.fullName}""""
        } else {
          inlinedSchemas += rec.fullName
          inlineRecordSchema(rec, typeToJsonWithInlining)
        }

      case AvroType.EnumType(avroEnum) =>
        if (inlinedSchemas.contains(avroEnum.fullName)) {
          s""""${avroEnum.fullName}""""
        } else {
          inlinedSchemas += avroEnum.fullName
          inlineEnumSchema(avroEnum)
        }

      case AvroType.Fixed(fixed) =>
        if (inlinedSchemas.contains(fixed.fullName)) {
          s""""${fixed.fullName}""""
        } else {
          inlinedSchemas += fixed.fullName
          inlineFixedSchema(fixed)
        }
    }

    // Add the outer record to inlinedSchemas BEFORE processing fields
    // This ensures self-referential fields use name references instead of infinite inlining
    inlinedSchemas += record.fullName

    val namespace = record.namespace.map(ns => s""""namespace": "$ns",""").getOrElse("")
    val doc = record.doc.map(d => s""""doc": "${escapeJson(d)}",""").getOrElse("")

    val fieldsJson = record.fields
      .map { field =>
        val fieldDoc = field.doc.map(d => s""""doc": "${escapeJson(d)}",""").getOrElse("")
        val typeJson = typeToJsonWithInlining(field.fieldType)
        val defaultJson = field.defaultValue.map(d => s""","default": $d""").getOrElse("")
        s"""{"name": "${field.name}",$fieldDoc"type": $typeJson$defaultJson}"""
      }
      .mkString(",")

    s"""{
       |"type": "record",
       |"name": "${record.name}",
       |$namespace
       |$doc
       |"fields": [$fieldsJson]
       |}""".stripMargin.replace("\n", "")
  }

  private def inlineEnumSchema(avroEnum: AvroEnum): String = {
    val namespace = avroEnum.namespace.map(ns => s""""namespace": "$ns",""").getOrElse("")
    val symbols = avroEnum.symbols.map(s => s""""$s"""").mkString(",")
    s"""{"type": "enum", "name": "${avroEnum.name}", $namespace"symbols": [$symbols]}"""
  }

  private def inlineRecordSchema(record: AvroRecord, typeToJson: AvroType => String): String = {
    val namespace = record.namespace.map(ns => s""""namespace": "$ns",""").getOrElse("")
    val doc = record.doc.map(d => s""""doc": "${escapeJson(d)}",""").getOrElse("")

    val fieldsJson = record.fields
      .map { field =>
        val fieldDoc = field.doc.map(d => s""""doc": "${escapeJson(d)}",""").getOrElse("")
        val fieldTypeJson = typeToJson(field.fieldType)
        val defaultJson = field.defaultValue.map(d => s""","default": $d""").getOrElse("")
        s"""{"name": "${field.name}",$fieldDoc"type": $fieldTypeJson$defaultJson}"""
      }
      .mkString(",")

    s"""{"type": "record", "name": "${record.name}", $namespace$doc"fields": [$fieldsJson]}"""
  }

  private def inlineFixedSchema(fixed: AvroFixed): String = {
    val namespace = fixed.namespace.map(ns => s""""namespace": "$ns",""").getOrElse("")
    s"""{"type": "fixed", "name": "${fixed.name}", $namespace"size": ${fixed.size}}"""
  }

  private def inlineErrorSchema(error: AvroError, typeToJson: AvroType => String): String = {
    val namespace = error.namespace.map(ns => s""""namespace": "$ns",""").getOrElse("")
    val doc = error.doc.map(d => s""""doc": "${escapeJson(d)}",""").getOrElse("")

    val fieldsJson = error.fields
      .map { field =>
        val fieldDoc = field.doc.map(d => s""""doc": "${escapeJson(d)}",""").getOrElse("")
        val fieldTypeJson = typeToJson(field.fieldType)
        val defaultJson = field.defaultValue.map(d => s""","default": $d""").getOrElse("")
        s"""{"name": "${field.name}",$fieldDoc"type": $fieldTypeJson$defaultJson}"""
      }
      .mkString(",")

    s"""{"type": "error", "name": "${error.name}", $namespace$doc"fields": [$fieldsJson]}"""
  }

  /** Convert a name to topic name format (kebab-case) */
  private def toTopicName(name: String): String =
    name.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase

  private def escapeJson(s: String): String =
    s.replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")

  /** Normalize a union for use as a map key (remove null, sort members) */
  private def normalizeUnion(union: AvroType.Union): AvroType.Union = {
    val nonNull = union.members.filterNot(_ == AvroType.Null)
    AvroType.Union(nonNull.sortBy(_.toString))
  }

  /** Constraint types for precise type generation */
  private sealed trait AvroPreciseConstraint
  private case class DecimalConstraint(precision: Int, scale: Int) extends AvroPreciseConstraint
  private case class BinaryConstraint(size: Int) extends AvroPreciseConstraint

  /** Collect unique precise type constraints from schema files */
  private def collectPreciseConstraints(schemaFiles: List[AvroSchemaFile]): Set[AvroPreciseConstraint] = {
    val constraints = Set.newBuilder[AvroPreciseConstraint]

    def collectFromType(avroType: AvroType): Unit = avroType match {
      case d: AvroType.DecimalBytes =>
        constraints += DecimalConstraint(d.precision, d.scale)
      case d: AvroType.DecimalFixed =>
        constraints += DecimalConstraint(d.precision, d.scale)
      case AvroType.Fixed(fixed) =>
        constraints += BinaryConstraint(fixed.size)
      case AvroType.Array(items) =>
        collectFromType(items)
      case AvroType.Map(values) =>
        collectFromType(values)
      case AvroType.Union(members) =>
        members.foreach(collectFromType)
      case AvroType.Record(record) =>
        record.fields.foreach(f => collectFromType(f.fieldType))
      case _ =>
      // Primitives and other types don't need precise wrappers
    }

    schemaFiles.foreach { schemaFile =>
      schemaFile.primarySchema match {
        case record: AvroRecord =>
          record.fields.foreach(f => collectFromType(f.fieldType))
        case _ =>
      }
      schemaFile.inlineSchemas.foreach {
        case record: AvroRecord =>
          record.fields.foreach(f => collectFromType(f.fieldType))
        case _ =>
      }
    }

    constraints.result()
  }

  /** Generate a precise type class for a constraint */
  private def generatePreciseType(constraint: AvroPreciseConstraint, naming: Naming, lang: Lang): jvm.File = {
    constraint match {
      case DecimalConstraint(precision, scale) =>
        val tpe = jvm.Type.Qualified(naming.preciseDecimalNName(precision, scale))
        FilePreciseType.forDecimalNAvro(tpe, precision, scale, lang)
      case BinaryConstraint(size) =>
        val tpe = jvm.Type.Qualified(naming.preciseBinaryNName(size))
        FilePreciseType.forBinaryNAvro(tpe, size, lang)
    }
  }
}

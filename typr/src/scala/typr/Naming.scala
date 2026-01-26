package typr

import scala.annotation.unused

class Naming(val pkg: jvm.QIdent, lang: Lang) {
  protected def fragments(source: Source): (jvm.QIdent, String) = {
    def forRelPath(relPath: RelPath): (jvm.QIdent, String) =
      (
        pkg / relPath.segments.init.map(pkgSafe),
        relPath.segments.last.replace(".sql", "")
      )

    source match {
      case relation: Source.Relation =>
        (pkg / relation.name.schema.toList.map(pkgSafe), normalizeOracleName(relation.name.name))
      case Source.SqlFile(relPath) => forRelPath(relPath)
    }
  }

  // Normalize Oracle SCREAMING_CASE identifiers to lowercase for package/directory names
  private def normalizeOracleName(str: String): String =
    if (str.nonEmpty && str.forall(c => c.isUpper || c.isDigit || c == '_' || c == '-' || c == '.')) {
      str.toLowerCase
    } else {
      str
    }

  // not enough, but covers a common case
  def pkgSafe(str: String): jvm.Ident = jvm.Ident(lang.escapedIdent(normalizeOracleName(str)))

  def suffixFor(source: Source): String =
    source match {
      case Source.Table(_)       => ""
      case Source.View(_, false) => "View"
      case Source.View(_, true)  => "MV"
      case Source.SqlFile(_)     => "Sql"
    }

  protected def relation(source: Source, suffix: String): jvm.QIdent = {
    val (init, name) = fragments(source)
    val suffix0 = suffixFor(source)
    init / pkgSafe(name) / jvm.Ident(Naming.titleCase(name)).appended(suffix0 + suffix)
  }

  protected def tpe(name: db.RelationName, suffix: String): jvm.QIdent =
    pkg / name.schema.map(pkgSafe).toList / jvm.Ident(Naming.titleCase(name.name)).appended(suffix)

  def idName(source: Source, @unused cols: List[db.Col]): jvm.QIdent = {
    relation(source, "Id")
  }
  def repoName(source: Source): jvm.QIdent = relation(source, "Repo")
  def repoImplName(source: Source): jvm.QIdent = relation(source, "RepoImpl")
  def repoMockName(source: Source): jvm.QIdent = relation(source, "RepoMock")
  def rowName(source: Source): jvm.QIdent = relation(source, "Row")
  def fieldsName(source: Source): jvm.QIdent = relation(source, "Fields")
  def fieldOrIdValueName(source: Source): jvm.QIdent = relation(source, "FieldValue")
  def rowUnsaved(source: Source): jvm.QIdent = relation(source, "RowUnsaved")

  def className(names: List[jvm.Ident]): jvm.QIdent = pkg / names

  def enumName(name: db.RelationName): jvm.QIdent =
    tpe(name, "")

  def domainName(name: db.RelationName): jvm.QIdent =
    tpe(name, "")

  def compositeTypeName(name: db.RelationName): jvm.QIdent =
    tpe(name, "")

  def objectTypeName(name: db.RelationName): jvm.QIdent =
    tpe(name, "")

  /** Name for a DuckDB STRUCT type. The name is pre-computed (e.g., "EmployeeContactInfo"). */
  def structTypeName(name: String): jvm.QIdent =
    pkg / jvm.Ident(name)

  /** Name for a MariaDB SET type derived from its sorted values. Example: SET('read', 'write', 'admin') -> sorted: admin, read, write -> AdminReadWriteSet Limits to first 20 characters to avoid
    * extremely long names.
    */
  def setTypeName(values: scala.collection.immutable.SortedSet[String]): jvm.QIdent = {
    val name = values.toList.map(Naming.titleCase).mkString("").take(20)
    pkg / jvm.Ident(name + "Set")
  }

  private val preciseTypesPackage: jvm.QIdent = pkg / jvm.Ident("precisetypes")

  def preciseStringNName(maxLength: Int): jvm.QIdent =
    preciseTypesPackage / jvm.Ident(s"String$maxLength")

  def precisePaddedStringNName(length: Int): jvm.QIdent =
    preciseTypesPackage / jvm.Ident(s"PaddedString$length")

  def preciseNonEmptyPaddedStringNName(length: Int): jvm.QIdent =
    preciseTypesPackage / jvm.Ident(s"NonEmptyPaddedString$length")

  def preciseNonEmptyStringName: jvm.QIdent =
    preciseTypesPackage / jvm.Ident("NonEmptyString")

  def preciseNonEmptyStringNName(maxLength: Int): jvm.QIdent =
    preciseTypesPackage / jvm.Ident(s"NonEmptyString$maxLength")

  def preciseBinaryNName(maxLength: Int): jvm.QIdent =
    preciseTypesPackage / jvm.Ident(s"Binary$maxLength")

  def preciseDecimalNName(precision: Int, scale: Int): jvm.QIdent =
    if (scale == 0) preciseTypesPackage / jvm.Ident(s"Int$precision")
    else preciseTypesPackage / jvm.Ident(s"Decimal${precision}_$scale")

  def preciseLocalDateTimeNName(fsp: Int): jvm.QIdent =
    preciseTypesPackage / jvm.Ident(s"LocalDateTime$fsp")

  def preciseInstantNName(fsp: Int): jvm.QIdent =
    preciseTypesPackage / jvm.Ident(s"Instant$fsp")

  def preciseLocalTimeNName(fsp: Int): jvm.QIdent =
    preciseTypesPackage / jvm.Ident(s"LocalTime$fsp")

  def preciseOffsetDateTimeNName(fsp: Int): jvm.QIdent =
    preciseTypesPackage / jvm.Ident(s"OffsetDateTime$fsp")

  def enumValue(name: String): jvm.Ident = jvm.Ident(name)

  // field names
  def field(name: db.ColName): jvm.Ident =
    Naming.camelCaseIdent(name.value.split('_'))

  def fk(baseTable: db.RelationName, fk: db.ForeignKey, includeCols: Boolean): jvm.Ident = {
    val parts = Array[Iterable[String]](
      List("fk"),
      fk.otherTable.schema.filterNot(baseTable.schema.contains),
      List(fk.otherTable.name),
      if (includeCols) fk.cols.toList.flatMap(_.value.split("_")) else Nil
    )
    Naming.camelCaseIdent(parts.flatten)
  }

  // multiple field names together into one name
  def field(colNames: NonEmptyList[db.ColName]): jvm.Ident =
    Naming.camelCaseIdent(colNames.map(field).map(_.value).toArray)

  // ============================================================================
  // Avro/Kafka naming methods
  // ============================================================================

  /** Package for Avro record/event types */
  def avroRecordPackage: jvm.QIdent = pkg

  /** Package for Avro serializer/deserializer classes */
  def avroSerdePackage: jvm.QIdent = pkg / jvm.Ident("serde")

  /** Package for Avro producer classes */
  def avroProducerPackage: jvm.QIdent = pkg / jvm.Ident("producer")

  /** Package for Avro consumer classes */
  def avroConsumerPackage: jvm.QIdent = pkg / jvm.Ident("consumer")

  /** Package for Avro header classes */
  def avroHeaderPackage: jvm.QIdent = pkg / jvm.Ident("header")

  /** Record class name from Avro schema */
  def avroRecordName(name: String, namespace: Option[String]): jvm.QIdent = {
    val packagePath = namespace match {
      case Some(ns) => jvm.QIdent(ns)
      case None     => pkg
    }
    packagePath / jvm.Ident(name)
  }

  /** Record class type from name and namespace */
  def avroRecordTypeName(name: String, namespace: Option[String]): jvm.Type.Qualified =
    jvm.Type.Qualified(avroRecordName(name, namespace))

  /** Serializer class name */
  def avroSerializerName(schemaName: String): jvm.QIdent =
    avroSerdePackage / jvm.Ident(schemaName + "Serializer")

  /** Deserializer class name */
  def avroDeserializerName(schemaName: String): jvm.QIdent =
    avroSerdePackage / jvm.Ident(schemaName + "Deserializer")

  /** Serde (serializer + deserializer) class name */
  def avroSerdeName(schemaName: String): jvm.QIdent =
    avroSerdePackage / jvm.Ident(schemaName + "Serde")

  /** Codec class/object name */
  def avroCodecName(name: String, namespace: Option[String]): jvm.QIdent = {
    val base = avroRecordName(name, namespace)
    base.parentOpt.map(_ / base.name.appended("Codec")).getOrElse(jvm.QIdent(List(base.name.appended("Codec"))))
  }

  /** Topic binding class name */
  def avroTopicsClassName: jvm.QIdent =
    pkg / jvm.Ident("Topics")

  /** Producer class name for a topic */
  def avroProducerName(topicName: String): jvm.QIdent =
    avroProducerPackage / jvm.Ident(Naming.titleCase(topicName) + "Producer")

  /** Consumer class name for a topic */
  def avroConsumerName(topicName: String): jvm.QIdent =
    avroConsumerPackage / jvm.Ident(Naming.titleCase(topicName) + "Consumer")

  /** Handler interface name for a topic */
  def avroHandlerName(topicName: String): jvm.QIdent =
    avroConsumerPackage / jvm.Ident(Naming.titleCase(topicName) + "Handler")

  /** Header class name */
  def avroHeaderClassName(headerSchemaName: String): jvm.QIdent =
    avroHeaderPackage / jvm.Ident(Naming.titleCase(headerSchemaName) + "Headers")

  /** Field name from Avro field (convert snake_case to camelCase) */
  def avroFieldName(avroFieldName: String): jvm.Ident =
    jvm.Ident(Naming.camelCase(avroFieldName.split("_")))

  /** Enum value name */
  def avroEnumValueName(symbol: String): jvm.Ident = {
    val sanitized = symbol
      .replace("-", "_")
      .replace(".", "_")
      .replace(" ", "_")
      .filter(c => c.isLetterOrDigit || c == '_')

    val result =
      if (sanitized.isEmpty) "_"
      else if (sanitized.headOption.exists(_.isDigit)) "_" + sanitized
      else sanitized

    jvm.Ident(result)
  }

  /** Sealed interface name for multi-event topic */
  def avroSealedInterfaceName(topicName: String): jvm.QIdent =
    pkg / jvm.Ident(Naming.titleCase(topicName) + "Event")

  /** Event group type name (sealed trait/interface for sum types) */
  def avroEventGroupTypeName(groupName: String, namespace: Option[String]): jvm.Type.Qualified = {
    val packagePath = namespace match {
      case Some(ns) => jvm.QIdent(ns)
      case None     => pkg
    }
    jvm.Type.Qualified(packagePath / jvm.Ident(groupName))
  }

  /** Wrapper type name for x-typr-wrapper annotated fields */
  def avroWrapperTypeName(wrapperName: String, namespace: Option[String]): jvm.Type.Qualified = {
    val packagePath = namespace match {
      case Some(ns) => jvm.QIdent(ns)
      case None     => pkg
    }
    jvm.Type.Qualified(packagePath / jvm.Ident(wrapperName))
  }

  /** TypedTopic field name (e.g., ORDER_PLACED for order-placed topic) */
  def avroTopicConstantName(topicName: String): jvm.Ident = {
    val screaming = topicName
      .replace("-", "_")
      .replace(".", "_")
      .toUpperCase
    jvm.Ident(screaming)
  }

  /** Schema Registry subject name for a topic value */
  def avroValueSubjectName(topicName: String): String =
    s"$topicName-value"

  /** Schema Registry subject name for a topic key */
  def avroKeySubjectName(topicName: String): String =
    s"$topicName-key"

  /** Schema validator utility class name */
  def avroSchemaValidatorName: jvm.QIdent =
    pkg / jvm.Ident("SchemaValidator")

  // ===== Avro Protocol Naming =====

  /** Package for protocol services */
  def avroProtocolPackage: jvm.QIdent = pkg / jvm.Ident("protocol")

  /** Service interface name for a protocol */
  def avroServiceTypeName(protocolName: String, namespace: Option[String]): jvm.Type.Qualified = {
    val basePkg = namespace.map(ns => jvm.QIdent(ns)).getOrElse(avroProtocolPackage)
    jvm.Type.Qualified(basePkg / jvm.Ident(protocolName))
  }

  /** Handler interface name for a protocol */
  def avroHandlerTypeName(protocolName: String, namespace: Option[String]): jvm.Type.Qualified = {
    val basePkg = namespace.map(ns => jvm.QIdent(ns)).getOrElse(avroProtocolPackage)
    jvm.Type.Qualified(basePkg / jvm.Ident(protocolName + "Handler"))
  }

  /** Error type name */
  def avroErrorTypeName(errorName: String, namespace: Option[String]): jvm.Type.Qualified = {
    val basePkg = namespace.map(ns => jvm.QIdent(ns)).getOrElse(avroProtocolPackage)
    jvm.Type.Qualified(basePkg / jvm.Ident(errorName))
  }

  /** Result ADT type name for a protocol message (e.g., CreateUserResult) */
  def avroMessageResultTypeName(messageName: String, namespace: Option[String]): jvm.Type.Qualified = {
    val basePkg = namespace.map(ns => jvm.QIdent(ns)).getOrElse(avroProtocolPackage)
    val capitalizedName = messageName.capitalize
    jvm.Type.Qualified(basePkg / jvm.Ident(capitalizedName + "Result"))
  }

  /** Error union type name for a protocol message with multiple errors (e.g., CreateUserError) */
  def avroMessageErrorTypeName(messageName: String, namespace: Option[String]): jvm.Type.Qualified = {
    val basePkg = namespace.map(ns => jvm.QIdent(ns)).getOrElse(avroProtocolPackage)
    val capitalizedName = messageName.capitalize
    jvm.Type.Qualified(basePkg / jvm.Ident(capitalizedName + "Error"))
  }

  // ===== Kafka Framework Integration Naming (Phase 2 & 3) =====

  /** Event publisher class name for framework integration (e.g., OrderEventsPublisher) */
  def avroEventPublisherTypeName(groupName: String, namespace: Option[String]): jvm.Type.Qualified = {
    val basePkg = namespace.map(ns => jvm.QIdent(ns)).getOrElse(pkg)
    jvm.Type.Qualified(basePkg / jvm.Ident(Naming.titleCase(groupName) + "Publisher"))
  }

  /** Event listener class name for framework integration (e.g., OrderEventsListener) */
  def avroEventListenerTypeName(groupName: String, namespace: Option[String]): jvm.Type.Qualified = {
    val basePkg = namespace.map(ns => jvm.QIdent(ns)).getOrElse(pkg)
    jvm.Type.Qualified(basePkg / jvm.Ident(Naming.titleCase(groupName) + "Listener"))
  }

  /** Request wrapper type name for Kafka RPC (e.g., GetUserRequest) */
  def avroMessageRequestTypeName(messageName: String, namespace: Option[String]): jvm.Type.Qualified = {
    val basePkg = namespace.map(ns => jvm.QIdent(ns)).getOrElse(avroProtocolPackage)
    val capitalizedName = messageName.capitalize
    jvm.Type.Qualified(basePkg / jvm.Ident(capitalizedName + "Request"))
  }

  /** Response wrapper type name for Kafka RPC (e.g., GetUserResponse) */
  def avroMessageResponseTypeName(messageName: String, namespace: Option[String]): jvm.Type.Qualified = {
    val basePkg = namespace.map(ns => jvm.QIdent(ns)).getOrElse(avroProtocolPackage)
    val capitalizedName = messageName.capitalize
    jvm.Type.Qualified(basePkg / jvm.Ident(capitalizedName + "Response"))
  }

  /** Service client class name for Kafka RPC (e.g., UserServiceClient) */
  def avroServiceClientTypeName(protocolName: String, namespace: Option[String]): jvm.Type.Qualified = {
    val basePkg = namespace.map(ns => jvm.QIdent(ns)).getOrElse(avroProtocolPackage)
    jvm.Type.Qualified(basePkg / jvm.Ident(protocolName + "Client"))
  }

  /** Service server class name for Kafka RPC (e.g., UserServiceServer) */
  def avroServiceServerTypeName(protocolName: String, namespace: Option[String]): jvm.Type.Qualified = {
    val basePkg = namespace.map(ns => jvm.QIdent(ns)).getOrElse(avroProtocolPackage)
    jvm.Type.Qualified(basePkg / jvm.Ident(protocolName + "Server"))
  }

  /** Service request interface name for Kafka RPC (e.g., UserServiceRequest) */
  def avroServiceRequestInterfaceTypeName(protocolName: String, namespace: Option[String]): jvm.Type.Qualified = {
    val basePkg = namespace.map(ns => jvm.QIdent(ns)).getOrElse(avroProtocolPackage)
    jvm.Type.Qualified(basePkg / jvm.Ident(protocolName + "Request"))
  }

  /** Generic Result type name for a protocol (e.g., Result) */
  def avroResultTypeName(namespace: Option[String]): jvm.Type.Qualified = {
    val basePkg = namespace.map(ns => jvm.QIdent(ns)).getOrElse(avroProtocolPackage)
    jvm.Type.Qualified(basePkg / jvm.Ident("Result"))
  }
}

object Naming {
  def camelCaseIdent(strings: Array[String]): jvm.Ident =
    jvm.Ident(camelCase(strings))

  def splitOnSymbol(str: String): Array[String] = {
    // Normalize Oracle SCREAMING_CASE to lowercase before splitting
    val normalized = if (str.nonEmpty && str.forall(c => c.isUpper || c.isDigit || c == '_' || c == '-' || c == '.')) {
      str.toLowerCase
    } else {
      str
    }
    normalized.split("[\\-_.]")
  }

  def camelCase(strings: Array[String]): String =
    strings
      .flatMap(splitOnSymbol)
      .zipWithIndex
      .map {
        case (s, 0) => s.updated(0, s.head.toLower)
        case (s, _) => s.capitalize
      }
      .mkString("")

  def camelCase(string: String): String =
    camelCase(string.split('_'))

  def titleCase(name: String): String =
    titleCase(name.split('_'))

  def titleCase(strings: Array[String]): String =
    strings.flatMap(splitOnSymbol).map(_.capitalize).mkString("")
}

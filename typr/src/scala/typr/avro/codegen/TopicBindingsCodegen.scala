package typr.avro.codegen

import typr.avro._
import typr.jvm.Code.{CodeOps, TreeOps, TypeOps}
import typr.{jvm, Lang, Naming, Scope}

import scala.collection.mutable

/** Generates type-safe topic binding constants.
  *
  * Creates a Topics class with TypedTopic<K,V> constants that provide compile-time type safety for topic key/value types and their serdes.
  */
class TopicBindingsCodegen(
    naming: Naming,
    lang: Lang,
    options: AvroOptions
) {

  // Kafka types
  private val SerdeType = jvm.Type.Qualified(jvm.QIdent("org.apache.kafka.common.serialization.Serde"))
  private val SerdesType = jvm.Type.Qualified(jvm.QIdent("org.apache.kafka.common.serialization.Serdes"))

  // Java types
  private val UUID = jvm.Type.Qualified(jvm.QIdent("java.util.UUID"))

  /** Generate the Topics class and TypedTopic class Returns a list of files: Topics class and TypedTopic class
    *
    * @param records
    *   All value records
    * @param eventGroups
    *   Event groups (sealed hierarchies)
    * @param keySchemas
    *   Map from topic name to key schema record (from Schema Registry -key subjects)
    */
  def generateTopicsClass(
      records: List[AvroRecord],
      eventGroups: List[AvroEventGroup],
      keySchemas: Map[String, AvroRecord]
  ): Option[jvm.File] = {
    // Build topic bindings from schema-to-topic mapping
    val recordBindings = buildRecordBindings(records, keySchemas)
    val groupBindings = buildEventGroupBindings(eventGroups, keySchemas)

    // Deduplicate by topic name (use Map to keep last occurrence)
    val allBindings = (recordBindings ++ groupBindings)
      .groupBy(_.topicName)
      .values
      .map(_.head)
      .toList
      .sortBy(_.topicName)

    if (allBindings.isEmpty) {
      return None
    }

    val topicsType = jvm.Type.Qualified(naming.avroTopicsClassName)

    // TypedTopic as a separate top-level class in the same package
    val typedTopicType = jvm.Type.Qualified(naming.avroRecordPackage / jvm.Ident("TypedTopic"))

    // Generate topic constants as static values
    val topicConstants = allBindings.map { binding =>
      generateTopicConstant(binding, typedTopicType)
    }

    // Create the Topics class (final class with static constants)
    // In Scala/Kotlin this becomes an object, in Java a final class with static fields
    val topicsClass = jvm.Class(
      annotations = Nil,
      comments = jvm.Comments(List("Type-safe topic binding constants")),
      classType = jvm.ClassType.Class,
      name = topicsType,
      tparams = Nil,
      params = Nil,
      implicitParams = Nil,
      `extends` = None,
      implements = Nil,
      members = Nil,
      staticMembers = topicConstants
    )

    Some(
      jvm.File(
        topicsType,
        jvm.Code.Tree(topicsClass),
        secondaryTypes = List(typedTopicType),
        scope = Scope.Main
      )
    )
  }

  /** Generate the TypedTopic record class as a separate file */
  def generateTypedTopicClass(): jvm.File = {
    val typedTopicType = jvm.Type.Qualified(naming.avroRecordPackage / jvm.Ident("TypedTopic"))
    val keyTParam = jvm.Type.Abstract(jvm.Ident("K"))
    val valueTParam = jvm.Type.Abstract(jvm.Ident("V"))

    val typedTopicRecord = jvm.Adt.Record(
      annotations = Nil,
      constructorAnnotations = Nil,
      isWrapper = false,
      privateConstructor = false,
      comments = jvm.Comments(List("A typed topic with key and value serdes")),
      name = typedTopicType,
      tparams = List(keyTParam, valueTParam),
      params = List(
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("name"), lang.String, None),
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("keySerde"), SerdeType.of(keyTParam), None),
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("valueSerde"), SerdeType.of(valueTParam), None)
      ),
      implicitParams = Nil,
      `extends` = None,
      implements = Nil,
      members = Nil,
      staticMembers = Nil
    )

    jvm.File(
      typedTopicType,
      jvm.Code.Tree(typedTopicRecord),
      secondaryTypes = Nil,
      scope = Scope.Main
    )
  }

  /** Binding information for a topic */
  private case class TopicBinding(
      topicName: String,
      valueType: jvm.Type.Qualified,
      serdeType: jvm.Type.Qualified,
      keyType: KeyType,
      keySchemaRecord: Option[AvroRecord],
      isEventGroup: Boolean
  )

  /** Build topic bindings from records based on topicMapping config */
  private def buildRecordBindings(records: List[AvroRecord], keySchemas: Map[String, AvroRecord]): List[TopicBinding] = {
    records.flatMap { record =>
      // Get topic name from mapping, or derive from record name
      val topicName = options.topicMapping.getOrElse(
        record.fullName,
        toTopicName(record.name)
      )

      // Skip if this record is part of an event group (handled separately)
      if (options.topicGroups.values.flatten.toSet.contains(record.fullName)) {
        None
      } else {
        val valueType = naming.avroRecordTypeName(record.name, record.namespace)
        val serdeType = jvm.Type.Qualified(naming.avroSerdeName(record.name))

        // Check if there's a key schema from the registry; otherwise use configured key type
        val (keyType, keySchemaRecord) = keySchemas.get(topicName) match {
          case Some(keyRecord) => (KeyType.SchemaKey(keyRecord.fullName), Some(keyRecord))
          case None            => (options.topicKeys.getOrElse(topicName, options.defaultKeyType), None)
        }

        Some(TopicBinding(topicName, valueType, serdeType, keyType, keySchemaRecord, isEventGroup = false))
      }
    }
  }

  /** Build topic bindings for event groups */
  private def buildEventGroupBindings(eventGroups: List[AvroEventGroup], keySchemas: Map[String, AvroRecord]): List[TopicBinding] = {
    eventGroups.map { group =>
      // For event groups, topic name is derived from group name
      val topicName = toTopicName(group.name)
      val valueType = naming.avroEventGroupTypeName(group.name, group.namespace)
      val serdeType = jvm.Type.Qualified(naming.avroSerdeName(group.name))

      // Check if there's a key schema from the registry; otherwise use configured key type
      val (keyType, keySchemaRecord) = keySchemas.get(topicName) match {
        case Some(keyRecord) => (KeyType.SchemaKey(keyRecord.fullName), Some(keyRecord))
        case None            => (options.topicKeys.getOrElse(topicName, options.defaultKeyType), None)
      }

      TopicBinding(topicName, valueType, serdeType, keyType, keySchemaRecord, isEventGroup = true)
    }
  }

  /** Convert a name to topic name format (kebab-case) */
  private def toTopicName(name: String): String = {
    // OrderPlaced -> order-placed
    name.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase
  }

  /** Generate a topic constant value (static field) */
  private def generateTopicConstant(binding: TopicBinding, typedTopicType: jvm.Type.Qualified): jvm.Value = {
    val keyJvmType = keyTypeToJvmType(binding.keyType)
    val parameterizedType = typedTopicType.of(keyJvmType, binding.valueType)

    val topicNameLit = jvm.StrLit(binding.topicName)
    val keySerdeExpr = keySerdeExpression(binding.keyType, binding.keySchemaRecord)
    val valueSerdeExpr = jvm.New(binding.serdeType.code, Nil).code

    val value = jvm
      .New(
        parameterizedType.code,
        List(
          jvm.Arg.Pos(topicNameLit.code),
          jvm.Arg.Pos(keySerdeExpr),
          jvm.Arg.Pos(valueSerdeExpr)
        )
      )
      .code

    val fieldName = naming.avroTopicConstantName(binding.topicName)

    jvm.Value(
      annotations = Nil,
      name = fieldName,
      tpe = parameterizedType,
      body = Some(value),
      isLazy = false,
      isOverride = false
    )
  }

  /** Convert KeyType to JVM type */
  private def keyTypeToJvmType(keyType: KeyType): jvm.Type = keyType match {
    case KeyType.StringKey             => lang.String
    case KeyType.UUIDKey               => UUID
    case KeyType.LongKey               => lang.Long
    case KeyType.IntKey                => lang.Int
    case KeyType.BytesKey              => lang.ByteArrayType
    case KeyType.SchemaKey(schemaName) => jvm.Type.Qualified(jvm.QIdent(schemaName))
  }

  /** Generate serde expression for a key type */
  private def keySerdeExpression(keyType: KeyType, keySchemaRecord: Option[AvroRecord]): jvm.Code = keyType match {
    case KeyType.StringKey =>
      lang.nullaryMethodCall(SerdesType.code, jvm.Ident("String"))
    case KeyType.UUIDKey =>
      jvm.New(jvm.Type.Qualified(naming.avroSerdePackage / jvm.Ident("UUIDSerde")).code, Nil).code
    case KeyType.LongKey =>
      lang.nullaryMethodCall(SerdesType.code, jvm.Ident("Long"))
    case KeyType.IntKey =>
      lang.nullaryMethodCall(SerdesType.code, jvm.Ident("Integer"))
    case KeyType.BytesKey =>
      lang.nullaryMethodCall(SerdesType.code, jvm.Ident("ByteArray"))
    case KeyType.SchemaKey(schemaName) =>
      // For schema-based keys, use the generated serde for the key schema record
      keySchemaRecord match {
        case Some(record) =>
          val serdeType = jvm.Type.Qualified(naming.avroSerdeName(record.name))
          jvm.New(serdeType.code, Nil).code
        case None =>
          // Fallback to String serde if record not available
          lang.nullaryMethodCall(SerdesType.code, jvm.Ident("String"))
      }
  }
}

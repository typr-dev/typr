package typr.avro.codegen

import typr.avro._
import typr.internal.codegen._
import typr.{jvm, Lang, Naming, Scope}

/** Generates framework-specific event publisher classes.
  *
  * For Spring: @Service class with KafkaTemplate For Quarkus: @ApplicationScoped class with Emitter + @Channel
  */
class EventPublisherCodegen(
    naming: Naming,
    lang: Lang,
    options: AvroOptions,
    framework: KafkaFramework
) {

  /** Generate a publisher for an event group (multiple event types on one topic) */
  def generateEventGroupPublisher(group: AvroEventGroup): jvm.File = {
    val topicName = toTopicName(group.name)
    val keyType = keyTypeToJvmType(options.topicKeys.getOrElse(topicName, options.defaultKeyType))
    val valueType = naming.avroEventGroupTypeName(group.name, group.namespace)

    generatePublisherClass(
      publisherType = naming.avroEventPublisherTypeName(group.name, group.namespace),
      topicName = topicName,
      keyType = keyType,
      valueType = valueType,
      eventTypes = group.members.map(r => naming.avroRecordTypeName(r.name, r.namespace))
    )
  }

  /** Generate a publisher for a standalone record type */
  def generateRecordPublisher(record: AvroRecord): jvm.File = {
    val topicName = options.topicMapping.getOrElse(record.fullName, toTopicName(record.name))
    val keyType = keyTypeToJvmType(options.topicKeys.getOrElse(topicName, options.defaultKeyType))
    val valueType = naming.avroRecordTypeName(record.name, record.namespace)

    generatePublisherClass(
      publisherType = naming.avroEventPublisherTypeName(record.name, record.namespace),
      topicName = topicName,
      keyType = keyType,
      valueType = valueType,
      eventTypes = List(valueType)
    )
  }

  private def generatePublisherClass(
      publisherType: jvm.Type.Qualified,
      topicName: String,
      keyType: jvm.Type,
      valueType: jvm.Type.Qualified,
      eventTypes: List[jvm.Type.Qualified]
  ): jvm.File = {
    val templateVar = jvm.Ident("kafkaTemplate")
    val topicVar = jvm.Ident("topic")

    val templateFieldType = framework.publisherFieldType(keyType, valueType)
    val templateFieldAnnotations = framework.publisherFieldAnnotations(topicName)

    val templateParam = jvm.Param(
      templateFieldAnnotations,
      jvm.Comments.Empty,
      templateVar,
      templateFieldType,
      None
    )

    val topicParam = jvm.Param(
      Nil,
      jvm.Comments.Empty,
      topicVar,
      lang.String,
      Some(jvm.StrLit(topicName).code)
    )

    val publishMethods = eventTypes.map { eventType =>
      generatePublishMethod(templateVar, topicVar, keyType, valueType, eventType)
    }

    val cls = jvm.Adt.Record(
      annotations = List(framework.serviceAnnotation),
      constructorAnnotations = framework.constructorAnnotations,
      isWrapper = false,
      privateConstructor = false,
      comments = jvm.Comments(List(s"Type-safe event publisher for $topicName topic")),
      name = publisherType,
      tparams = Nil,
      params = List(templateParam, topicParam),
      implicitParams = Nil,
      `extends` = None,
      implements = Nil,
      members = publishMethods,
      staticMembers = Nil
    )

    jvm.File(publisherType, jvm.Code.Tree(cls), secondaryTypes = Nil, scope = Scope.Main)
  }

  private def generatePublishMethod(
      templateVar: jvm.Ident,
      topicVar: jvm.Ident,
      keyType: jvm.Type,
      valueType: jvm.Type.Qualified,
      eventType: jvm.Type.Qualified
  ): jvm.Method = {
    val keyParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("key"), keyType, None)
    val eventParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("event"), eventType, None)

    val publishCall = framework.publishCall(
      templateVar,
      topicVar.code,
      jvm.Ident("key").code,
      jvm.Ident("event").code
    )

    val returnType = framework.effectOf(framework.publishReturnType(keyType, valueType))

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(List(s"Publish a ${eventType.value.name.value} event")),
      tparams = Nil,
      name = jvm.Ident("publish"),
      params = List(keyParam, eventParam),
      implicitParams = Nil,
      tpe = returnType,
      throws = Nil,
      body = jvm.Body.Stmts(List(jvm.Return(publishCall).code)),
      isOverride = false,
      isDefault = false
    )
  }

  private def toTopicName(name: String): String =
    name.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase

  private def keyTypeToJvmType(keyType: KeyType): jvm.Type = keyType match {
    case KeyType.StringKey    => lang.String
    case KeyType.UUIDKey      => jvm.Type.Qualified(jvm.QIdent("java.util.UUID"))
    case KeyType.LongKey      => lang.Long
    case KeyType.IntKey       => lang.Int
    case KeyType.BytesKey     => lang.ByteArrayType
    case KeyType.SchemaKey(_) => lang.String
  }
}

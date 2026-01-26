package typr.avro.codegen

import typr.avro._
import typr.internal.codegen._
import typr.{jvm, Lang, Naming, Scope}

/** Generates framework-specific abstract event listener classes.
  *
  * Users extend these classes and implement the abstract handler methods. The framework handles deserialization and dispatching to the correct handler based on event type.
  */
class EventListenerCodegen(
    naming: Naming,
    lang: Lang,
    options: AvroOptions,
    framework: KafkaFramework
) {

  // Use language-appropriate top type (Object for Java, Any for Kotlin)
  private val TopType = lang.topType

  /** Generate a listener for an event group (multiple event types on one topic) */
  def generateEventGroupListener(group: AvroEventGroup): jvm.File = {
    val topicName = toTopicName(group.name)
    val keyType = keyTypeToJvmType(options.topicKeys.getOrElse(topicName, options.defaultKeyType))
    val valueType = naming.avroEventGroupTypeName(group.name, group.namespace)
    val listenerType = naming.avroEventListenerTypeName(group.name, group.namespace)

    generateListenerClass(
      listenerType = listenerType,
      topicName = topicName,
      keyType = keyType,
      valueType = valueType,
      eventTypes = group.members.map(r => naming.avroRecordTypeName(r.name, r.namespace))
    )
  }

  /** Generate a listener for a standalone record type */
  def generateRecordListener(record: AvroRecord): jvm.File = {
    val topicName = options.topicMapping.getOrElse(record.fullName, toTopicName(record.name))
    val keyType = keyTypeToJvmType(options.topicKeys.getOrElse(topicName, options.defaultKeyType))
    val valueType = naming.avroRecordTypeName(record.name, record.namespace)
    val listenerType = naming.avroEventListenerTypeName(record.name, record.namespace)

    generateListenerClass(
      listenerType = listenerType,
      topicName = topicName,
      keyType = keyType,
      valueType = valueType,
      eventTypes = List(valueType)
    )
  }

  private def generateListenerClass(
      listenerType: jvm.Type.Qualified,
      topicName: String,
      keyType: jvm.Type,
      valueType: jvm.Type.Qualified,
      eventTypes: List[jvm.Type.Qualified]
  ): jvm.File = {
    val recordParam = jvm.Ident("record")
    val consumerRecordType = framework.consumerRecordType(keyType, TopType)

    val receiveMethod = generateReceiveMethod(
      topicName = topicName,
      recordParam = recordParam,
      consumerRecordType = consumerRecordType,
      eventTypes = eventTypes
    )

    val handlerMethods = eventTypes.map(generateHandlerMethod)

    val onUnknownMethod = generateOnUnknownMethod(consumerRecordType)

    val cls = jvm.Class(
      annotations = Nil,
      comments = jvm.Comments(List(s"Event listener interface for $topicName topic. Implement this interface to handle events.")),
      classType = jvm.ClassType.Interface,
      name = listenerType,
      tparams = Nil,
      params = Nil,
      implicitParams = Nil,
      `extends` = None,
      implements = Nil,
      members = List(receiveMethod) ++ handlerMethods ++ List(onUnknownMethod),
      staticMembers = Nil
    )

    jvm.File(listenerType, jvm.Code.Tree(cls), secondaryTypes = Nil, scope = Scope.Main)
  }

  private def generateReceiveMethod(
      topicName: String,
      recordParam: jvm.Ident,
      consumerRecordType: jvm.Type,
      eventTypes: List[jvm.Type.Qualified]
  ): jvm.Method = {
    val param = jvm.Param(Nil, jvm.Comments.Empty, recordParam, consumerRecordType, None)

    val payloadExpr = framework.getPayload(recordParam)
    val metadataExpr = framework.getMetadata(recordParam)

    val switchCases = eventTypes.map { eventType =>
      val varName = jvm.Ident("e")
      val handlerName = jvm.Ident("on" + eventType.value.name.value)
      val handlerCall = code"$handlerName($varName, $metadataExpr)"
      jvm.TypeSwitch.Case(eventType, varName, handlerCall)
    }

    val defaultCase = code"onUnknown($recordParam)"

    val typeSwitch = jvm.TypeSwitch(
      value = payloadExpr,
      cases = switchCases,
      nullCase = Some(defaultCase),
      defaultCase = Some(defaultCase)
    )

    val returnType = framework.voidEffectType

    jvm.Method(
      annotations = List(framework.listenerAnnotation(topicName)),
      comments = jvm.Comments(List("Receive and dispatch events to handler methods")),
      tparams = Nil,
      name = jvm.Ident("receive"),
      params = List(param),
      implicitParams = Nil,
      tpe = returnType,
      throws = Nil,
      body = jvm.Body.Stmts(List(jvm.Return(typeSwitch.code).code)),
      isOverride = false,
      isDefault = true
    )
  }

  private def generateHandlerMethod(eventType: jvm.Type.Qualified): jvm.Method = {
    val eventParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("event"), eventType, None)
    val metadataParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("metadata"), framework.metadataType, None)

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(List(s"Handle ${eventType.value.name.value} event")),
      tparams = Nil,
      name = jvm.Ident("on" + eventType.value.name.value),
      params = List(eventParam, metadataParam),
      implicitParams = Nil,
      tpe = framework.voidEffectType,
      throws = Nil,
      body = jvm.Body.Abstract,
      isOverride = false,
      isDefault = false
    )
  }

  private def generateOnUnknownMethod(consumerRecordType: jvm.Type): jvm.Method = {
    val recordParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("record"), consumerRecordType, None)

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(List("Handle unknown event types. Override to customize behavior.")),
      tparams = Nil,
      name = jvm.Ident("onUnknown"),
      params = List(recordParam),
      implicitParams = Nil,
      tpe = framework.voidEffectType,
      throws = Nil,
      body = jvm.Body.Stmts(List(jvm.Return(framework.voidSuccess).code)),
      isOverride = false,
      isDefault = true
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

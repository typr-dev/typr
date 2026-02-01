package typr.avro.computed

import typr.avro.{AvroMessage, AvroProtocol, AvroType}
import typr.{jvm, Naming}

/** Computed representation of an Avro protocol message (RPC method) for code generation.
  *
  * @param proto
  *   The original protocol message definition
  * @param name
  *   The computed JVM method name
  * @param requestType
  *   The computed JVM request type (None if void/empty)
  * @param responseType
  *   The computed JVM response type (None if void/one-way)
  */
case class ComputedProtocolMessage(
    proto: AvroMessage,
    name: jvm.Ident,
    requestType: Option[jvm.Type],
    responseType: Option[jvm.Type]
) {

  /** Original message name */
  def protoName: String = proto.name

  /** Documentation */
  def doc: Option[String] = proto.doc

  /** Whether this is a one-way message (no response) */
  def isOneWay: Boolean = proto.oneWay

  /** Error types this message can throw */
  def errors: List[AvroType] = proto.errors
}

/** Computed representation of an Avro protocol for code generation.
  *
  * @param proto
  *   The original Avro protocol definition
  * @param serviceTpe
  *   The qualified JVM type for the service interface
  * @param clientTpe
  *   The qualified JVM type for the client implementation
  * @param serverTpe
  *   The qualified JVM type for the server handler
  * @param messages
  *   Computed messages (RPC methods)
  */
case class ComputedProtocol(
    proto: AvroProtocol,
    serviceTpe: jvm.Type.Qualified,
    clientTpe: jvm.Type.Qualified,
    serverTpe: jvm.Type.Qualified,
    messages: List[ComputedProtocolMessage]
) {

  /** Protocol name */
  def name: String = proto.name

  /** Protocol namespace */
  def namespace: Option[String] = proto.namespace

  /** Documentation */
  def doc: Option[String] = proto.doc
}

object ComputedProtocol {

  /** Create a computed protocol from an Avro protocol definition */
  def from(
      proto: AvroProtocol,
      naming: Naming,
      mapType: AvroType => jvm.Type
  ): ComputedProtocol = {
    val serviceTpe = naming.avroServiceTypeName(proto.name, proto.namespace)
    val clientTpe = naming.avroServiceClientTypeName(proto.name, proto.namespace)
    val serverTpe = naming.avroServiceServerTypeName(proto.name, proto.namespace)

    val messages = proto.messages.map { msg =>
      val methodName = naming.avroFieldName(msg.name)

      // Map request type - if multiple params, would be a tuple or record
      val requestType: Option[jvm.Type] = msg.request match {
        case Nil      => None
        case h :: Nil => Some(mapType(h.fieldType))
        case params   =>
          // For multiple params, use the first one's type as the request type
          // In practice, Avro RPC usually wraps multiple params in a record
          Some(mapType(params.head.fieldType))
      }

      // Map response type
      val responseType: Option[jvm.Type] = if (msg.oneWay) {
        None
      } else {
        msg.response match {
          case AvroType.Null => None
          case tpe           => Some(mapType(tpe))
        }
      }

      ComputedProtocolMessage(
        proto = msg,
        name = methodName,
        requestType = requestType,
        responseType = responseType
      )
    }

    ComputedProtocol(
      proto = proto,
      serviceTpe = serviceTpe,
      clientTpe = clientTpe,
      serverTpe = serverTpe,
      messages = messages
    )
  }
}

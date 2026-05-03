package typr.avro

import typr.jvm
import typr.effects.EffectType
import typr.openapi.codegen.JsonLibSupport

import java.nio.file.Path

/** Configuration options for Avro/Kafka code generation */
case class AvroOptions(
    /** Base package for generated code */
    pkg: jvm.QIdent,
    /** Avro wire format (Confluent Schema Registry with magic bytes, or raw binary encoding) */
    avroWireFormat: AvroWireFormat,
    /** Schema source location */
    schemaSource: SchemaSource,
    /** Whether to generate record classes (event types) */
    generateRecords: Boolean,
    /** Whether to generate Serializer/Deserializer classes */
    generateSerdes: Boolean,
    /** Whether to generate type-safe producer classes */
    generateProducers: Boolean,
    /** Whether to generate type-safe consumer classes */
    generateConsumers: Boolean,
    /** Whether to generate Topics object with typed topic constants */
    generateTopicBindings: Boolean,
    /** Whether to generate typed header classes */
    generateHeaders: Boolean,
    /** Whether to generate schema compatibility validation utility */
    generateSchemaValidator: Boolean,
    /** Whether to generate RPC service interfaces from .avpr protocol files */
    generateProtocols: Boolean,
    /** Whether to generate sealed types for complex union types (e.g., ["string", "int"] -> StringOrInt) */
    generateUnionTypes: Boolean,
    /** Effect type for async/reactive operations */
    effectType: EffectType,
    /** Topic mapping: schema name -> topic name */
    topicMapping: Map[String, String],
    /** Multi-event topic grouping: topic name -> list of schema names (generates sealed hierarchy) */
    topicGroups: Map[String, List[String]],
    /** Key types per topic */
    topicKeys: Map[String, KeyType],
    /** Default key type when not specified per-topic */
    defaultKeyType: KeyType,
    /** Header schemas by name */
    headerSchemas: Map[String, HeaderSchema],
    /** Topic to header schema mapping */
    topicHeaders: Map[String, String],
    /** Default header schema (if any) */
    defaultHeaderSchema: Option[String],
    /** Schema evolution strategy */
    schemaEvolution: SchemaEvolution,
    /** Compatibility mode (informs nullability decisions) */
    compatibilityMode: CompatibilityMode,
    /** Whether to add @Since annotations for new fields */
    markNewFields: Boolean,
    /** Whether to generate precise wrapper types for constrained Avro types (DecimalN for decimal, FixedN for fixed) */
    enablePreciseTypes: Boolean,
    /** Framework integration for Kafka (Spring, Quarkus, or None for framework-agnostic code) */
    frameworkIntegration: FrameworkIntegration,
    /** Whether to generate framework-specific event publishers/listeners (Phase 2) */
    generateKafkaEvents: Boolean,
    /** Whether to generate framework-specific RPC client/server implementations (Phase 3) */
    generateKafkaRpc: Boolean
)

object AvroOptions {
  def default(pkg: jvm.QIdent, schemaSource: SchemaSource): AvroOptions =
    AvroOptions(
      pkg = pkg,
      avroWireFormat = AvroWireFormat.ConfluentRegistry,
      schemaSource = schemaSource,
      generateRecords = true,
      generateSerdes = true,
      generateProducers = true,
      generateConsumers = true,
      generateTopicBindings = true,
      generateHeaders = true,
      generateSchemaValidator = true,
      generateProtocols = true,
      generateUnionTypes = true,
      effectType = EffectType.Blocking,
      topicMapping = Map.empty,
      topicGroups = Map.empty,
      topicKeys = Map.empty,
      defaultKeyType = KeyType.StringKey,
      headerSchemas = Map.empty,
      topicHeaders = Map.empty,
      defaultHeaderSchema = None,
      schemaEvolution = SchemaEvolution.LatestOnly,
      compatibilityMode = CompatibilityMode.Backward,
      markNewFields = false,
      enablePreciseTypes = false,
      frameworkIntegration = FrameworkIntegration.None,
      generateKafkaEvents = false,
      generateKafkaRpc = false
    )
}

/** Schema source for Avro code generation */
sealed trait SchemaSource

object SchemaSource {

  /** Load schemas from a directory containing .avsc files */
  case class Directory(path: Path) extends SchemaSource

  /** Fetch schemas from a Schema Registry */
  case class Registry(url: String) extends SchemaSource

  /** Combine multiple schema sources */
  case class Multi(sources: List[SchemaSource]) extends SchemaSource
}

/** Avro wire format for Kafka serialization.
  *
  * This determines how Avro data is encoded on the wire:
  *   - ConfluentRegistry: Uses Confluent Schema Registry format (5-byte magic prefix with schema ID)
  *   - BinaryEncoded: Raw Avro binary encoding without schema ID prefix
  *   - JsonEncoded: JSON serialization via Jackson/Circe (no Avro binary encoding)
  */
sealed trait AvroWireFormat

object AvroWireFormat {

  /** Confluent Schema Registry format (most common, default).
    *
    * Messages include a 5-byte prefix: magic byte (0x0) + 4-byte schema ID. Requires a running Schema Registry.
    */
  case object ConfluentRegistry extends AvroWireFormat

  /** Raw Avro binary encoding without schema registry.
    *
    * Messages are pure Avro binary data without schema ID prefix. Schema must be provided out-of-band (e.g., embedded in message or known a priori).
    */
  case object BinaryEncoded extends AvroWireFormat

  /** JSON wire format using the specified JSON library.
    *
    * Messages are JSON-encoded using Jackson or Circe. Generates toJson/fromJson methods instead of toGenericRecord/fromGenericRecord. Kafka serializers use ObjectMapper (Jackson) or Circe codecs.
    */
  case class JsonEncoded(jsonLib: JsonLibSupport) extends AvroWireFormat
}

/** Effect type for Kafka async/reactive operations.
  *
  * Type alias for the shared EffectType. See EffectType for available options.
  */
type KafkaEffectType = EffectType

/** Effect type companion with values for backwards compatibility */
object KafkaEffectType {

  /** Blocking/synchronous operations */
  val Blocking: EffectType = EffectType.Blocking

  /** Java CompletableFuture */
  val CompletableFuture: EffectType = EffectType.CompletableFuture

  /** Cats Effect IO */
  val CatsIO: EffectType = EffectType.CatsIO

  /** ZIO Task */
  val ZIO: EffectType = EffectType.ZIO

  /** Project Reactor Mono (for Spring integration) */
  val ReactorMono: EffectType = EffectType.ReactorMono

  /** SmallRye Mutiny Uni (for Quarkus) */
  val MutinyUni: EffectType = EffectType.MutinyUni
}

/** Key type for a topic */
sealed trait KeyType

object KeyType {

  /** String key (most common) */
  case object StringKey extends KeyType

  /** UUID key */
  case object UUIDKey extends KeyType

  /** Long key */
  case object LongKey extends KeyType

  /** Int key */
  case object IntKey extends KeyType

  /** Byte array key */
  case object BytesKey extends KeyType

  /** Composite key from Avro schema. The schemaName references a schema in the same source. */
  case class SchemaKey(schemaName: String) extends KeyType
}

/** Header field definition */
case class HeaderField(
    name: String,
    headerType: HeaderType,
    required: Boolean
)

/** Supported header types */
sealed trait HeaderType

object HeaderType {
  case object String extends HeaderType
  case object UUID extends HeaderType
  case object Instant extends HeaderType
  case object Long extends HeaderType
  case object Int extends HeaderType
  case object Boolean extends HeaderType
}

/** Header schema definition */
case class HeaderSchema(
    fields: List[HeaderField]
)

/** Schema evolution strategy */
sealed trait SchemaEvolution

object SchemaEvolution {

  /** Generate from latest schema only (default, recommended) */
  case object LatestOnly extends SchemaEvolution

  /** Generate versioned types (OrderPlacedV1, OrderPlacedV2, etc.) */
  case object AllVersions extends SchemaEvolution

  /** Generate versioned types with migration helpers */
  case object WithMigrations extends SchemaEvolution
}

/** Schema compatibility mode */
sealed trait CompatibilityMode

object CompatibilityMode {

  /** Consumers can read old data (default for Schema Registry) */
  case object Backward extends CompatibilityMode

  /** Producers can write data for old consumers */
  case object Forward extends CompatibilityMode

  /** Both backward and forward compatible */
  case object Full extends CompatibilityMode

  /** No compatibility checks (development only) */
  case object None extends CompatibilityMode
}

/** Framework integration for Kafka event publishers/listeners and RPC.
  *
  * This determines which annotations and types are used in generated code:
  *   - None: Generate framework-agnostic code (default)
  *   - Spring: Use @Service, KafkaTemplate, @KafkaListener, ReplyingKafkaTemplate
  *   - Quarkus: Use @ApplicationScoped, @Channel, @Incoming, KafkaRequestReply
  */
sealed trait FrameworkIntegration {
  import typr.avro.codegen.KafkaFramework

  /** Get the KafkaFramework implementation for this integration, if any */
  def kafkaFramework: Option[KafkaFramework]
}

object FrameworkIntegration {
  import typr.avro.codegen.{KafkaFrameworkSpring, KafkaFrameworkQuarkus, KafkaFrameworkCats}

  /** No framework annotations - generate framework-agnostic code */
  case object None extends FrameworkIntegration {
    override def kafkaFramework: Option[typr.avro.codegen.KafkaFramework] = scala.None
  }

  /** Spring framework integration.
    *
    * Events: Uses KafkaTemplate for publishing, @KafkaListener for consuming RPC: Uses ReplyingKafkaTemplate for client, @KafkaListener + @SendTo for server Effect type: CompletableFuture
    */
  case object Spring extends FrameworkIntegration {
    override def kafkaFramework: Option[typr.avro.codegen.KafkaFramework] = Some(KafkaFrameworkSpring)
  }

  /** Quarkus framework integration.
    *
    * Events: Uses Emitter + @Channel for publishing, @Incoming for consuming RPC: Uses KafkaRequestReply for client, @Incoming + @Outgoing for server Effect type: Uni (Mutiny)
    */
  case object Quarkus extends FrameworkIntegration {
    override def kafkaFramework: Option[typr.avro.codegen.KafkaFramework] = Some(KafkaFrameworkQuarkus)
  }

  /** Cats/Typelevel framework integration.
    *
    * Events: Uses KafkaProducer[IO, K, V] for publishing, stream-based consumers with handler traits RPC: Uses fs2.Stream patterns Effect type: cats.effect.IO
    */
  case object Cats extends FrameworkIntegration {
    override def kafkaFramework: Option[typr.avro.codegen.KafkaFramework] = Some(KafkaFrameworkCats)
  }
}

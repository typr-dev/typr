package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder

/** Configuration for Bridge type code generation in this output */
case class BridgeOutputConfig(
    /** [DEPRECATED: use domain_package] Legacy alias for domain_package */
    canonical_package: Option[String],
    /** Package for domain types (default: {package}.bridge) */
    domain_package: Option[String],
    /** Generate shared interfaces for domain types */
    generate_interfaces: Option[Boolean],
    /** Generate mapper functions between domain types and aligned source types */
    generate_mappers: Option[Boolean],
    /** Style of generated mappers: static methods, instance methods, or extension functions (Kotlin) */
    mapper_style: Option[String],
    /** Package for mapper classes (default: {package}.bridge.mappers) */
    mappers_package: Option[String]
)

object BridgeOutputConfig {
  implicit val decoder: Decoder[BridgeOutputConfig] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.BridgeOutputConfig]

  implicit val encoder: Encoder[BridgeOutputConfig] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.BridgeOutputConfig]
}

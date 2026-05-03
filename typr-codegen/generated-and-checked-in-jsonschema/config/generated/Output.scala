package typr.config.generated

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json

case class Output(
    bridge: Option[BridgeOutputConfig],
    /** Database library */
    db_lib: Option[String],
    /** Effect type for async/reactive operations across all boundary types */
    effect_type: Option[String],
    /** Framework integration. Applies to all boundary types: spring (Spring Boot/Kafka/gRPC), quarkus (Quarkus REST/Kafka/gRPC), jaxrs (JAX-RS for OpenAPI only), http4s (Http4s for OpenAPI only),
      * cats (Typelevel stack: fs2-kafka, fs2-grpc, http4s), none (vanilla code)
      */
    framework: Option[String],
    /** JSON library */
    json: Option[String],
    /** Target language */
    language: String,
    matchers: Option[Matchers],
    /** Base package name */
    `package`: String,
    /** Output directory path */
    path: String,
    scala: Option[Json],
    sources: Option[StringOrArray]
)

object Output {
  implicit val decoder: Decoder[Output] = io.circe.generic.semiauto.deriveDecoder[typr.config.generated.Output]

  implicit val encoder: Encoder[Output] = io.circe.generic.semiauto.deriveEncoder[typr.config.generated.Output]
}

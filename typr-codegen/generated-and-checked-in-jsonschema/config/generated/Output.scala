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
    /** Framework integration. Applies to all boundary types: spring, quarkus, jaxrs, http4s, none */
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

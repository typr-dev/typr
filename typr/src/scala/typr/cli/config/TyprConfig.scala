package typr.cli.config

import io.circe.Decoder
import io.circe.Encoder
import io.circe.HCursor
import io.circe.Json
import typr.config.generated.BridgeType
import typr.config.generated.Output

case class TyprConfig(
    version: Option[Int],
    include: Option[List[String]],
    sources: Option[Map[String, Json]],
    types: Option[Map[String, BridgeType]],
    outputs: Option[Map[String, Output]]
) {
  def boundaries: Option[Map[String, Json]] = sources
}

object TyprConfig {
  implicit val decoder: Decoder[TyprConfig] = Decoder.instance { (c: HCursor) =>
    for {
      version <- c.downField("version").as[Option[Int]]
      include <- c.downField("include").as[Option[List[String]]]
      boundariesField <- c.downField("boundaries").as[Option[Map[String, Json]]]
      sourcesField <- c.downField("sources").as[Option[Map[String, Json]]]
      sources = boundariesField.orElse(sourcesField)
      types <- c.downField("types").as[Option[Map[String, BridgeType]]]
      outputs <- c.downField("outputs").as[Option[Map[String, Output]]]
    } yield TyprConfig(version, include, sources, types, outputs)
  }

  implicit val encoder: Encoder[TyprConfig] = Encoder.instance { config =>
    val fields = List(
      config.version.map(v => "version" -> Json.fromInt(v)),
      config.include.map(i => "include" -> Encoder[List[String]].apply(i)),
      config.sources.map(b => "boundaries" -> Encoder[Map[String, Json]].apply(b)),
      config.types.map(t => "types" -> Encoder[Map[String, BridgeType]].apply(t)),
      config.outputs.map(o => "outputs" -> Encoder[Map[String, Output]].apply(o))
    ).flatten
    Json.obj(fields*)
  }
}

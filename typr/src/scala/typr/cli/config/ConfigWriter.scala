package typr.cli.config

import io.circe.Json
import io.circe.JsonObject
import io.circe.syntax.*
import io.circe.yaml.v12.syntax.*
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object ConfigWriter {
  def write(path: Path, config: TyprConfig): Either[Throwable, Unit] =
    try {
      val yaml = toYaml(config)
      Files.write(path, yaml.getBytes(StandardCharsets.UTF_8))
      Right(())
    } catch {
      case e: Throwable => Left(e)
    }

  def toYaml(config: TyprConfig): String = {
    val json = sortKeys(dropNullKeys(config.asJson))
    json.asYaml.spaces2
  }

  private def dropNullKeys(json: Json): Json = json.arrayOrObject(
    json,
    arr => Json.fromValues(arr.map(dropNullKeys)),
    obj => Json.fromJsonObject(obj.filter { case (_, v) => !v.isNull }.mapValues(dropNullKeys))
  )

  private def sortKeys(json: Json): Json = json.arrayOrObject(
    json,
    arr => Json.fromValues(arr.map(sortKeys)),
    obj => {
      val sorted = obj.toList.sortBy(_._1)
      Json.fromJsonObject(JsonObject.fromIterable(sorted.map { case (k, v) => k -> sortKeys(v) }))
    }
  )
}

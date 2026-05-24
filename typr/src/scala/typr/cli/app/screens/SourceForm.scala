package typr.cli.app.screens

import io.circe.{Json, JsonObject}

/** Plain data model for the source editor. Decouples the form's flat `Map[label, value]` representation from circe `Json`, and remembers the original JSON so fields we don't surface (selectors,
  * sql_scripts, custom keys) pass through unchanged when we write the source back.
  *
  * Field set is keyed on the source kind. The kind is inferred from the JSON shape the same way [[SourceList]] does it — explicit `type` field wins, otherwise infer from `path` / `spec` / `host`.
  * Type editing isn't supported yet; users can change values but not switch a postgres source to a duckdb one.
  */
object SourceForm {

  /** A single editable field: storage key + a human label for the form row. */
  final case class Field(key: String, label: String)

  /** Which fields are surfaced for which kind. Order is render order. */
  val fieldsByKind: Map[String, List[Field]] = Map(
    "duckdb" -> List(Field("path", "path")),
    "sqlite" -> List(Field("path", "path")),
    "openapi" -> List(Field("spec", "spec path")),
    "jsonschema" -> List(Field("spec", "spec path")),
    "oracle" -> List(
      Field("host", "host"),
      Field("port", "port"),
      Field("service", "service"),
      Field("username", "username"),
      Field("password", "password")
    ),
    "jdbc" -> List(
      Field("host", "host"),
      Field("port", "port"),
      Field("database", "database"),
      Field("username", "username"),
      Field("password", "password")
    )
  )

  /** Current state of an open editor: which kind, current values, and the JSON we'd write back. */
  final case class State(kind: String, values: Map[String, String], original: Json) {
    def fields: List[Field] = fieldsByKind.getOrElse(kind, Nil)
    def get(key: String): String = values.getOrElse(key, "")
    def withField(key: String, value: String): State = copy(values = values.updated(key, value))

    /** Re-encode to JSON, preserving any keys we didn't expose in the form. Numeric ports go back as numbers when parseable, otherwise as the raw string the user typed (so a mis-typed port is still
      * saved and visible — better than silently dropping).
      */
    def toJson: Json = {
      val obj0 = original.asObject.getOrElse(JsonObject.empty)
      val merged = fields.foldLeft(obj0) { case (obj, Field(key, _)) =>
        val v = values.getOrElse(key, "").trim
        if (v.isEmpty) obj.remove(key)
        else if (key == "port")
          v.toLongOption.fold(obj.add(key, Json.fromString(v)))(n => obj.add(key, Json.fromLong(n)))
        else obj.add(key, Json.fromString(v))
      }
      Json.fromJsonObject(merged)
    }
  }

  /** Parse a source JSON into a form state. Unknown kinds fall back to the jdbc field set (host/port/database/user/pass) — better than showing an empty form.
    */
  def parse(json: Json): State = {
    val kind = inferKind(json) match {
      case k if fieldsByKind.contains(k) => k
      case _                             => "jdbc"
    }
    val c = json.hcursor
    val values = fieldsByKind(kind).map { f =>
      val v = c
        .get[String](f.key)
        .toOption
        .orElse(c.get[Long](f.key).toOption.map(_.toString))
        .getOrElse("")
      f.key -> v
    }.toMap
    State(kind, values, json)
  }

  private def inferKind(j: Json): String = {
    val c = j.hcursor
    c.get[String]("type").toOption.getOrElse {
      if (c.get[String]("path").isRight) "duckdb"
      else if (c.get[String]("spec").isRight) "openapi"
      else if (c.get[String]("host").isRight) "jdbc"
      else "?"
    }
  }
}

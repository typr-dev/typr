package typr
package internal

import play.api.libs.json.*

// lazy load this
class DebugJson(f: () => JsValue) {
  lazy val maybeJson: Option[JsValue] = f() match {
    case JsNull   => None
    case nonEmpty => Some(nonEmpty)
  }
}
object DebugJson {
  val Empty = new DebugJson(() => JsNull)

  def apply[T: Writes](t: T): DebugJson =
    new DebugJson(() => removeNullsFromJson(Json.toJson(t)))

  def removeNullsFromJson(json: JsValue): JsValue = json match {
    case x: JsObject => JsObject(x.fields.collect { case (k, v) if v != JsNull => k -> removeNullsFromJson(v) })
    case other       => other
  }
}

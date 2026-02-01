package typr.cli.tui.components

import tui.*
import tui.widgets.*

object FormField {
  def renderHint(area: Rect, buf: Buffer, hint: String): Unit = {
    val hintStyle = Style(fg = Some(Color.DarkGray))
    buf.setString(area.x, area.y, hint, hintStyle)
  }

  def renderError(area: Rect, buf: Buffer, error: String): Unit = {
    val errorStyle = Style(fg = Some(Color.Red))
    buf.setString(area.x, area.y, error, errorStyle)
  }

  def renderLabel(area: Rect, buf: Buffer, label: String, isFocused: Boolean): Unit = {
    val labelStyle = if (isFocused) {
      Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)
    } else {
      Style(fg = Some(Color.Gray))
    }
    buf.setString(area.x, area.y, label, labelStyle)
  }

  def renderValue(area: Rect, buf: Buffer, value: String, isFocused: Boolean): Unit = {
    val valueStyle =
      if (isFocused) Style(fg = Some(Color.White), bg = Some(Color.DarkGray))
      else Style(fg = Some(Color.White))
    buf.setString(area.x, area.y, value, valueStyle)
  }
}

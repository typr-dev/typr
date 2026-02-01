package typr.cli.tui.components

import tui.*
import tui.crossterm.KeyCode
import tui.widgets.*

case class TextInput(
    label: String,
    value: String,
    placeholder: String,
    error: Option[String],
    isFocused: Boolean
) {
  def handleKey(keyCode: KeyCode): TextInput = keyCode match {
    case c: KeyCode.Char      => copy(value = value + c.c(), error = None)
    case _: KeyCode.Backspace => copy(value = if (value.nonEmpty) value.dropRight(1) else value, error = None)
    case _                    => this
  }

  def render(area: Rect, buf: Buffer): Unit = {
    val labelStyle = if (isFocused) Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD) else Style(fg = Some(Color.Cyan))
    val inputStyle =
      if (isFocused) Style(fg = Some(Color.White), bg = Some(Color.DarkGray))
      else Style(fg = Some(Color.Gray))
    val errorStyle = Style(fg = Some(Color.Red))

    buf.setString(area.x, area.y, label, labelStyle)

    val displayValue = if (value.isEmpty && !isFocused) placeholder else value
    val displayStyle = if (value.isEmpty && !isFocused) Style(fg = Some(Color.DarkGray)) else inputStyle

    val inputY = area.y + 1
    val inputWidth = math.min(area.width - 2, 40)

    for (i <- 0 until inputWidth) {
      buf.get(area.x + i, inputY).setSymbol(" ").setStyle(inputStyle)
    }

    val truncatedValue = if (displayValue.length > inputWidth - 1) {
      displayValue.takeRight(inputWidth - 1)
    } else {
      displayValue
    }
    buf.setString(area.x, inputY, truncatedValue, displayStyle)

    error.foreach { err =>
      buf.setString(area.x, area.y + 2, err, errorStyle)
    }
  }

  def cursorPosition(area: Rect): (Int, Int) = {
    val inputY = area.y + 1
    val cursorX = math.min(value.length, 38)
    (area.x + cursorX, inputY)
  }
}

object TextInput {
  def apply(label: String, placeholder: String): TextInput =
    TextInput(
      label = label,
      value = "",
      placeholder = placeholder,
      error = None,
      isFocused = false
    )
}

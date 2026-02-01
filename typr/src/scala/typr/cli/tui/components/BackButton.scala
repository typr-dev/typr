package typr.cli.tui.components

import tui.*
import tui.widgets.*

/** BackButton component matching the React BackButton design.
  *
  * Renders a 3-line bordered button:
  * {{{
  * ╭──────────────╮
  * │ ← Title      │ (Esc)
  * ╰──────────────╯
  * }}}
  */
object BackButton {

  /** Height of the BackButton component */
  val Height: Int = 3

  def render(f: Frame, area: Rect, title: String): Unit = {
    renderClickable(f, area, title, isHovered = false)
  }

  def renderClickable(f: Frame, area: Rect, title: String, isHovered: Boolean): Unit = {
    val borderColor = if (isHovered) Color.Cyan else Color.Gray
    val textColor = if (isHovered) Color.Cyan else Color.Gray
    val borderStyle = Style(fg = Some(borderColor))
    val textStyle = if (isHovered) Style(fg = Some(textColor), addModifier = Modifier.BOLD) else Style(fg = Some(textColor))

    val buttonText = s"← $title"
    val hint = "(Esc)"
    val innerWidth = buttonText.length + 2
    val totalWidth = innerWidth + 2
    val maxX = area.x + area.width

    def safeSetString(x: Short, y: Short, text: String, style: Style): Unit = {
      if (x >= 0 && x < maxX && y >= area.y && y < area.y + area.height) {
        val availableWidth = maxX - x
        if (availableWidth > 0) {
          f.buffer.setString(x, y, text.take(availableWidth.toInt), style)
        }
      }
    }

    safeSetString(area.x.toShort, area.y.toShort, "╭" + "─" * innerWidth + "╮", borderStyle)
    safeSetString(area.x.toShort, (area.y + 1).toShort, "│", borderStyle)
    safeSetString((area.x + 1).toShort, (area.y + 1).toShort, s" $buttonText ", textStyle)
    safeSetString((area.x + innerWidth + 1).toShort, (area.y + 1).toShort, "│", borderStyle)
    safeSetString(area.x.toShort, (area.y + 2).toShort, "╰" + "─" * innerWidth + "╯", borderStyle)
    safeSetString((area.x + totalWidth + 2).toShort, (area.y + 1).toShort, hint, Style(fg = Some(Color.DarkGray)))
  }

  /** Check if click is on back button.
    * @param col
    *   click column
    * @param row
    *   click row
    * @param margin
    *   margin offset
    * @return
    *   true if click is within the button area (3 rows tall)
    */
  def isClicked(col: Int, row: Int, margin: Int): Boolean = {
    row >= margin && row < margin + Height && col >= margin && col < margin + 30
  }
}

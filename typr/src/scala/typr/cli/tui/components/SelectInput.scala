package typr.cli.tui.components

import tui.*
import tui.crossterm.KeyCode
import tui.widgets.*

case class SelectInput[T](
    label: String,
    options: List[(T, String)],
    selectedIndex: Int,
    isFocused: Boolean
) {
  def handleKey(keyCode: KeyCode): SelectInput[T] = keyCode match {
    case _: KeyCode.Up   => copy(selectedIndex = math.max(0, selectedIndex - 1))
    case _: KeyCode.Down => copy(selectedIndex = math.min(options.length - 1, selectedIndex + 1))
    case c: KeyCode.Char =>
      val char = c.c().toLower
      options.zipWithIndex
        .find { case ((_, display), _) =>
          display.toLowerCase.startsWith(char.toString)
        }
        .map { case (_, idx) =>
          copy(selectedIndex = idx)
        }
        .getOrElse(this)
    case _ => this
  }

  def selectedValue: T = options(selectedIndex)._1
  def selectedDisplay: String = options(selectedIndex)._2
  def selectedLabel: String = options(selectedIndex)._2

  def prev: SelectInput[T] = copy(selectedIndex = math.max(0, selectedIndex - 1))
  def next: SelectInput[T] = copy(selectedIndex = math.min(options.length - 1, selectedIndex + 1))

  def render(area: Rect, buf: Buffer): Unit = {
    val labelStyle = if (isFocused) Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD) else Style(fg = Some(Color.Cyan))

    buf.setString(area.x, area.y, label, labelStyle)

    val listY = area.y + 1
    val maxVisible = math.min(options.length, area.height.toInt - 1)

    val startIdx = if (selectedIndex >= maxVisible) {
      selectedIndex - maxVisible + 1
    } else {
      0
    }

    options.slice(startIdx, startIdx + maxVisible).zipWithIndex.foreach { case ((_, display), idx) =>
      val actualIdx = startIdx + idx
      val isSelected = actualIdx == selectedIndex

      val style =
        if (isSelected && isFocused) Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
        else if (isSelected) Style(fg = Some(Color.White), bg = Some(Color.DarkGray))
        else Style(fg = Some(Color.Gray))

      val prefix = if (isSelected) "> " else "  "
      val text = prefix + display

      for (i <- 0 until area.width.toInt) {
        buf.get(area.x + i, listY + idx).setSymbol(" ").setStyle(style)
      }
      buf.setString(area.x, listY + idx, text.take(area.width.toInt), style)
    }
  }
}

object SelectInput {
  def apply[T](label: String, options: List[(T, String)]): SelectInput[T] =
    SelectInput(
      label = label,
      options = options,
      selectedIndex = 0,
      isFocused = false
    )

  def apply[T](label: String, options: List[(T, String)], selectedIndex: Int): SelectInput[T] =
    SelectInput(
      label = label,
      options = options,
      selectedIndex = selectedIndex,
      isFocused = false
    )
}

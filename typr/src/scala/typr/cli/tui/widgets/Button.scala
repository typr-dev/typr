package typr.cli.tui.widgets

import tui.*
import typr.cli.tui.FocusableId

/** A clickable button with hover and focus support */
case class Button(
    label: String,
    x: Int,
    y: Int,
    id: FocusableId = FocusableId.Button(""),
    style: ButtonStyle = ButtonStyle.default,
    disabled: Boolean = false
) {
  val width: Int = label.length + 4
  val height: Int = 1

  def withId(buttonId: String): Button = copy(id = FocusableId.Button(buttonId))

  def contains(col: Int, row: Int): Boolean = {
    col >= x && col < x + width && row >= y && row < y + height
  }

  def render(buf: Buffer, hoverCol: Option[Int], hoverRow: Option[Int]): Unit = {
    renderWithFocus(buf, hoverCol, hoverRow, None)
  }

  def renderWithFocus(buf: Buffer, hoverCol: Option[Int], hoverRow: Option[Int], focusedId: Option[FocusableId]): Unit = {
    val isHovered = (hoverCol, hoverRow) match {
      case (Some(c), Some(r)) => contains(c, r)
      case _                  => false
    }
    val isFocused = focusedId.contains(id)

    val currentStyle = if (disabled) {
      style.disabled
    } else if (isFocused) {
      style.focused
    } else if (isHovered) {
      style.hover
    } else {
      style.normal
    }

    val focusIndicator = if (isFocused && !disabled) ">" else " "
    val text = s"$focusIndicator[ $label ]"
    buf.setString(x - 1, y, text, currentStyle)
  }

  def renderWithFrame(f: Frame, hoverCol: Option[Int], hoverRow: Option[Int]): Unit = {
    render(f.buffer, hoverCol, hoverRow)
  }

  def renderWithFrameAndFocus(f: Frame, hoverCol: Option[Int], hoverRow: Option[Int], focusedId: Option[FocusableId]): Unit = {
    renderWithFocus(f.buffer, hoverCol, hoverRow, focusedId)
  }
}

case class ButtonStyle(
    normal: Style,
    hover: Style,
    focused: Style,
    disabled: Style
)

object ButtonStyle {
  val default: ButtonStyle = ButtonStyle(
    normal = Style(fg = Some(Color.White), bg = Some(Color.DarkGray)),
    hover = Style(fg = Some(Color.Black), bg = Some(Color.Cyan), addModifier = Modifier.BOLD),
    focused = Style(fg = Some(Color.Black), bg = Some(Color.Yellow), addModifier = Modifier.BOLD),
    disabled = Style(fg = Some(Color.DarkGray))
  )

  val primary: ButtonStyle = ButtonStyle(
    normal = Style(fg = Some(Color.White), bg = Some(Color.Blue)),
    hover = Style(fg = Some(Color.White), bg = Some(Color.LightBlue), addModifier = Modifier.BOLD),
    focused = Style(fg = Some(Color.Black), bg = Some(Color.Yellow), addModifier = Modifier.BOLD),
    disabled = Style(fg = Some(Color.DarkGray))
  )

  val danger: ButtonStyle = ButtonStyle(
    normal = Style(fg = Some(Color.White), bg = Some(Color.Red)),
    hover = Style(fg = Some(Color.White), bg = Some(Color.LightRed), addModifier = Modifier.BOLD),
    focused = Style(fg = Some(Color.Black), bg = Some(Color.Yellow), addModifier = Modifier.BOLD),
    disabled = Style(fg = Some(Color.DarkGray))
  )
}

/** A row of buttons that can be rendered and clicked */
case class ButtonRow(
    buttons: List[Button],
    spacing: Int = 2
) {
  def render(buf: Buffer, hoverCol: Option[Int], hoverRow: Option[Int]): Unit = {
    buttons.foreach(_.render(buf, hoverCol, hoverRow))
  }

  def renderWithFocus(buf: Buffer, hoverCol: Option[Int], hoverRow: Option[Int], focusedId: Option[FocusableId]): Unit = {
    buttons.foreach(_.renderWithFocus(buf, hoverCol, hoverRow, focusedId))
  }

  def findClicked(col: Int, row: Int): Option[Int] = {
    buttons.zipWithIndex
      .find { case (btn, _) =>
        !btn.disabled && btn.contains(col, row)
      }
      .map(_._2)
  }

  def findClickedButton(col: Int, row: Int): Option[Button] = {
    buttons.find(btn => !btn.disabled && btn.contains(col, row))
  }

  def focusableIds: List[FocusableId] = buttons.filterNot(_.disabled).map(_.id)
}

object ButtonRow {
  def create(labels: List[String], startX: Int, y: Int, spacing: Int = 2, style: ButtonStyle = ButtonStyle.default): ButtonRow = {
    var x = startX + 1 // +1 to account for focus indicator
    val buttons = labels.map { label =>
      val btn = Button(label, x, y, style = style).withId(label.toLowerCase.replace(" ", "-"))
      x += btn.width + spacing + 1
      btn
    }
    ButtonRow(buttons, spacing)
  }

  def createWithIds(labelsAndIds: List[(String, String)], startX: Int, y: Int, spacing: Int = 2, style: ButtonStyle = ButtonStyle.default): ButtonRow = {
    var x = startX + 1
    val buttons = labelsAndIds.map { case (label, id) =>
      val btn = Button(label, x, y, style = style).withId(id)
      x += btn.width + spacing + 1
      btn
    }
    ButtonRow(buttons, spacing)
  }
}

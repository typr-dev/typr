package tui.react

import tui.*
import tui.widgets.*

/** React-style element builders that wrap tui-scala widgets */
object Elements {

  /** A clickable text button */
  def Button(
      label: String,
      style: Style = Style.DEFAULT,
      hoverStyle: Option[Style] = None,
      hoverPosition: Option[(Int, Int)] = None
  )(onClick: => Unit): Component = Component.named(s"Button:$label") { (ctx, area) =>
    // Check if cursor is currently over this button
    val isHovered = hoverPosition.exists { case (hx, hy) =>
      hx >= area.x && hx < area.x + area.width &&
      hy >= area.y && hy < area.y + area.height
    }
    val effectiveStyle = if (isHovered) hoverStyle.getOrElse(style) else style

    ctx.buffer.setString(area.x, area.y, label.take(area.width.toInt), effectiveStyle)

    ctx.onClick(area)(onClick)
  }

  /** A text span - no events */
  def Text(content: String, style: Style = Style.DEFAULT): Component = Component { (ctx, area) =>
    ctx.buffer.setString(area.x, area.y, content.take(area.width.toInt), style)
  }

  /** Multi-line text */
  def Paragraph(content: String, style: Style = Style.DEFAULT): Component = Component { (ctx, area) =>
    val lines = content.split("\n")
    lines.zipWithIndex.foreach { case (line, idx) =>
      if (idx < area.height) {
        ctx.buffer.setString(area.x, (area.y + idx).toShort, line.take(area.width.toInt), style)
      }
    }
  }

  /** A box with optional border */
  def Box(
      title: Option[String] = None,
      borders: Borders = Borders.ALL,
      borderType: BlockWidget.BorderType = BlockWidget.BorderType.Rounded,
      style: Style = Style.DEFAULT
  )(children: Component*): Component = Component { (ctx, area) =>
    val titleSpans = title.map(t => Spans.from(Span.styled(t, style)))
    val block = BlockWidget(
      title = titleSpans,
      borders = borders,
      borderType = borderType,
      style = style
    )
    ctx.frame.renderWidget(block, area)

    // Calculate inner area
    val innerArea = if (borders != Borders.NONE) {
      Rect(
        x = (area.x + 1).toShort,
        y = (area.y + 1).toShort,
        width = math.max(0, area.width - 2).toShort,
        height = math.max(0, area.height - 2).toShort
      )
    } else {
      area
    }

    // Render children in a vertical stack
    var y = innerArea.y
    children.foreach { child =>
      if (y < innerArea.y + innerArea.height) {
        val childArea = Rect(innerArea.x, y, innerArea.width, 1)
        child.render(ctx, childArea)
        y = (y + 1).toShort
      }
    }
  }

  /** Vertical layout - stacks children */
  def Column(spacing: Int = 0)(children: Component*): Component = Component { (ctx, area) =>
    var y = area.y
    children.foreach { child =>
      if (y < area.y + area.height) {
        val childArea = Rect(area.x, y, area.width, 1)
        child.render(ctx, childArea)
        y = (y + 1 + spacing).toShort
      }
    }
  }

  /** Vertical layout with custom heights */
  def ColumnWithHeights(heights: List[Int])(children: Component*): Component = Component { (ctx, area) =>
    var y = area.y
    children.zip(heights).foreach { case (child, height) =>
      if (y < area.y + area.height) {
        val actualHeight = math.min(height, area.height - (y - area.y)).toShort
        val childArea = Rect(area.x, y, area.width, actualHeight)
        child.render(ctx, childArea)
        y = (y + height).toShort
      }
    }
  }

  /** Horizontal layout with equal distribution */
  def Row(children: Component*): Component = Component { (ctx, area) =>
    if (children.nonEmpty) {
      val childWidth = (area.width / children.size).toShort
      var x = area.x
      children.foreach { child =>
        val childArea = Rect(x, area.y, childWidth, area.height)
        child.render(ctx, childArea)
        x = (x + childWidth).toShort
      }
    }
  }

  /** Horizontal layout with specific widths */
  def RowWithWidths(widths: List[Int])(children: Component*): Component = Component { (ctx, area) =>
    var x = area.x
    children.zip(widths).foreach { case (child, width) =>
      if (x < area.x + area.width) {
        val actualWidth = math.min(width, area.width - (x - area.x)).toShort
        val childArea = Rect(x, area.y, actualWidth, area.height)
        child.render(ctx, childArea)
        x = (x + width).toShort
      }
    }
  }

  /** Clickable list item */
  def ListItem(
      label: String,
      isSelected: Boolean = false,
      icon: Option[String] = None,
      selectedStyle: Style = Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD),
      normalStyle: Style = Style(fg = Some(Color.White))
  )(onClick: => Unit): Component = Component.named(s"ListItem:$label") { (ctx, area) =>
    val (isHovered, setHovered) = ctx.useState(false)
    val style = if (isSelected || isHovered) selectedStyle else normalStyle

    val prefix = if (isSelected) "> " else "  "
    val iconStr = icon.map(_ + " ").getOrElse("")
    val text = s"$prefix$iconStr$label"

    ctx.buffer.setString(area.x, area.y, text.take(area.width.toInt), style)

    ctx.onClick(area)(onClick)
    ctx.onHover(area)(setHovered(true))
  }

  /** Spacer that takes up space but renders nothing */
  def Spacer(height: Int = 1): Component = Component { (_, _) => () }

  /** Empty component */
  val Empty: Component = Component { (_, _) => () }

  /** Conditional rendering */
  def When(condition: Boolean)(child: => Component): Component = Component { (ctx, area) =>
    if (condition) child.render(ctx, area)
  }

  /** If-else rendering */
  def IfElse(condition: Boolean)(ifTrue: => Component)(ifFalse: => Component): Component = Component { (ctx, area) =>
    if (condition) ifTrue.render(ctx, area) else ifFalse.render(ctx, area)
  }

  /** Render each item in a list */
  def ForEach[T](items: List[T])(render: (T, Int) => Component): Component = Component { (ctx, area) =>
    var y = area.y
    items.zipWithIndex.foreach { case (item, idx) =>
      if (y < area.y + area.height) {
        val childArea = Rect(area.x, y, area.width, 1)
        render(item, idx).render(ctx, childArea)
        y = (y + 1).toShort
      }
    }
  }

  /** Render each item with custom height */
  def ForEachWithHeight[T](items: List[T], itemHeight: Int)(render: (T, Int) => Component): Component =
    Component { (ctx, area) =>
      var y = area.y
      items.zipWithIndex.foreach { case (item, idx) =>
        if (y < area.y + area.height) {
          val height = math.min(itemHeight, area.height - (y - area.y)).toShort
          val childArea = Rect(area.x, y, area.width, height)
          render(item, idx).render(ctx, childArea)
          y = (y + itemHeight).toShort
        }
      }
    }

  /** Wrapper that captures area for custom rendering */
  def Custom(f: (RenderContext, Rect) => Unit): Component = Component(f)

  /** Render styled spans on a line */
  def StyledLine(spans: Spans): Component = Component { (ctx, area) =>
    ctx.buffer.setSpans(area.x, area.y, spans, area.width.toInt)
  }

  /** Clickable row with styled spans */
  def ClickableRow(spans: Spans)(onClick: => Unit): Component = Component { (ctx, area) =>
    ctx.buffer.setSpans(area.x, area.y, spans, area.width.toInt)
    ctx.onClick(area)(onClick)
  }

  /** Simple header/title text */
  def Header(text: String, style: Style = Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)): Component =
    Text(text, style)

  /** Hint text at bottom */
  def Hint(text: String): Component = Text(text, Style(fg = Some(Color.DarkGray)))

  /** Back button with visible button styling - use at top of screens */
  def BackButton(title: String)(onBack: => Unit): Component = Component.named("BackButton") { (ctx, area) =>
    val (isHovered, setHovered) = ctx.useState(false)

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
          ctx.buffer.setString(x, y, text.take(availableWidth), style)
        }
      }
    }

    safeSetString(area.x.toShort, area.y.toShort, "╭" + "─" * innerWidth + "╮", borderStyle)
    safeSetString(area.x.toShort, (area.y + 1).toShort, "│", borderStyle)
    safeSetString((area.x + 1).toShort, (area.y + 1).toShort, s" $buttonText ", textStyle)
    safeSetString((area.x + innerWidth + 1).toShort, (area.y + 1).toShort, "│", borderStyle)
    safeSetString(area.x.toShort, (area.y + 2).toShort, "╰" + "─" * innerWidth + "╯", borderStyle)
    safeSetString((area.x + totalWidth + 2).toShort, (area.y + 1).toShort, hint, Style(fg = Some(Color.DarkGray)))

    val clickArea = Rect(area.x, area.y, math.min(totalWidth, area.width.toInt).toShort, 3)
    ctx.onClick(clickArea)(onBack)
    ctx.onHover(clickArea)(setHovered(true))
  }

  /** Wrap a ParagraphWidget */
  def WrappedParagraph(widget: tui.widgets.ParagraphWidget): Component = Component { (ctx, area) =>
    ctx.frame.renderWidget(widget, area)
  }

  /** Wrap a BlockWidget */
  def WrappedBlock(widget: tui.widgets.BlockWidget): Component = Component { (ctx, area) =>
    ctx.frame.renderWidget(widget, area)
  }

  /** Wrap a TableWidget */
  def WrappedTable(widget: tui.widgets.TableWidget): Component = Component { (ctx, area) =>
    ctx.frame.renderWidget(widget, area)
  }

  // ============================================
  // Form Elements with Focus Management
  // ============================================

  /** Text input field with focus support */
  def TextInput(
      label: String,
      value: String,
      isFocused: Boolean,
      placeholder: String = ""
  )(onFocus: => Unit, onChange: String => Unit): Component = Component.named(s"TextInput:$label") { (ctx, area) =>
    val (isHovered, setHovered) = ctx.useState(false)

    val labelStyle = Style(fg = Some(Color.Cyan))
    val focusedStyle = Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
    val normalStyle = Style(fg = Some(Color.White))
    val placeholderStyle = Style(fg = Some(Color.DarkGray))
    val hoverStyle = Style(fg = Some(Color.White), bg = Some(Color.DarkGray))

    val labelWidth = label.length + 2
    ctx.buffer.setString(area.x, area.y, s"$label: ", labelStyle)

    val inputX = (area.x + labelWidth).toShort
    val inputWidth = math.max(1, area.width - labelWidth).toShort

    val displayValue = if (value.isEmpty && !isFocused) placeholder else value
    val displayStyle =
      if (isFocused) focusedStyle
      else if (value.isEmpty && !isFocused) placeholderStyle
      else if (isHovered) hoverStyle
      else normalStyle

    val cursor = if (isFocused) "█" else ""
    val displayText = (displayValue + cursor).take(inputWidth.toInt)

    // Clear input area first
    ctx.buffer.setString(inputX, area.y, " " * inputWidth.toInt, displayStyle)
    ctx.buffer.setString(inputX, area.y, displayText, displayStyle)

    val inputArea = Rect(inputX, area.y, inputWidth, 1)
    ctx.onClick(inputArea)(onFocus)
    ctx.onHover(inputArea)(setHovered(true))
  }

  /** Checkbox with focus support */
  def Checkbox(
      label: String,
      isChecked: Boolean,
      isFocused: Boolean
  )(onFocus: => Unit, onToggle: => Unit): Component = Component.named(s"Checkbox:$label") { (ctx, area) =>
    val (isHovered, setHovered) = ctx.useState(false)

    val boxStyle =
      if (isFocused) Style(fg = Some(Color.Cyan), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
      else if (isHovered) Style(fg = Some(Color.Cyan), bg = Some(Color.DarkGray))
      else Style(fg = Some(Color.Cyan))
    val labelStyle =
      if (isFocused) Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
      else if (isHovered) Style(fg = Some(Color.White), bg = Some(Color.DarkGray))
      else Style(fg = Some(Color.White))

    val checkChar = if (isChecked) "☑" else "☐"
    ctx.buffer.setString(area.x, area.y, checkChar, boxStyle)
    ctx.buffer.setString((area.x + 2).toShort, area.y, label.take(area.width.toInt - 3), labelStyle)

    val clickArea = Rect(area.x, area.y, math.min(area.width, (label.length + 3).toShort).toShort, 1)
    ctx.onClick(clickArea) { onFocus; onToggle }
    ctx.onHover(clickArea)(setHovered(true))
  }

  /** Three-state toggle (None, Some(true), Some(false)) */
  def TriStateToggle(
      label: String,
      value: Option[Boolean],
      isFocused: Boolean
  )(onFocus: => Unit, onCycle: => Unit): Component = Component.named(s"TriState:$label") { (ctx, area) =>
    val (isHovered, setHovered) = ctx.useState(false)

    val valueStr = value match {
      case None        => "(any)"
      case Some(true)  => "Yes"
      case Some(false) => "No"
    }

    val labelStyle = Style(fg = Some(Color.Cyan))
    val valueStyle =
      if (isFocused) Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
      else if (isHovered) Style(fg = Some(Color.White), bg = Some(Color.DarkGray))
      else Style(fg = Some(Color.White))

    val labelWidth = label.length + 2
    ctx.buffer.setString(area.x, area.y, s"$label: ", labelStyle)

    val valueX = (area.x + labelWidth).toShort
    ctx.buffer.setString(valueX, area.y, s"[$valueStr]", valueStyle)

    val clickArea = Rect(valueX, area.y, (valueStr.length + 2).toShort, 1)
    ctx.onClick(clickArea) { onFocus; onCycle }
    ctx.onHover(clickArea)(setHovered(true))
  }

  /** Select dropdown (cycles through options with left/right arrows) */
  def Select[T](
      label: String,
      options: List[(T, String)],
      selectedValue: T,
      isFocused: Boolean
  )(onFocus: => Unit, onChange: T => Unit): Component = Component.named(s"Select:$label") { (ctx, area) =>
    val (isHovered, setHovered) = ctx.useState(false)

    val currentLabel = options.find(_._1 == selectedValue).map(_._2).getOrElse("?")
    val currentIdx = options.indexWhere(_._1 == selectedValue)

    val labelStyle = Style(fg = Some(Color.Cyan))
    val valueStyle =
      if (isFocused) Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
      else if (isHovered) Style(fg = Some(Color.White), bg = Some(Color.DarkGray))
      else Style(fg = Some(Color.White))

    val labelWidth = label.length + 2
    ctx.buffer.setString(area.x, area.y, s"$label: ", labelStyle)

    val valueX = (area.x + labelWidth).toShort
    ctx.buffer.setString(valueX, area.y, s"◀ $currentLabel ▶", valueStyle)

    val totalWidth = currentLabel.length + 4

    val leftArrowArea = Rect(valueX, area.y, 2, 1)
    ctx.onClick(leftArrowArea) {
      onFocus
      val prevIdx = (currentIdx - 1 + options.length) % options.length
      onChange(options(prevIdx)._1)
    }

    val rightArrowArea = Rect((valueX + totalWidth - 1).toShort, area.y, 2, 1)
    ctx.onClick(rightArrowArea) {
      onFocus
      val nextIdx = (currentIdx + 1) % options.length
      onChange(options(nextIdx)._1)
    }

    val labelArea = Rect((valueX + 2).toShort, area.y, currentLabel.length.toShort, 1)
    ctx.onClick(labelArea) {
      onFocus
      val nextIdx = (currentIdx + 1) % options.length
      onChange(options(nextIdx)._1)
    }

    val fullArea = Rect(valueX, area.y, (totalWidth + 1).toShort, 1)
    ctx.onHover(fullArea)(setHovered(true))
  }

  /** Action button with focus support */
  def FormButton(
      label: String,
      isFocused: Boolean,
      isPrimary: Boolean = false
  )(onFocus: => Unit, onPress: => Unit): Component = Component.named(s"FormButton:$label") { (ctx, area) =>
    val (isHovered, setHovered) = ctx.useState(false)

    val (normalBg, focusBg) = if (isPrimary) (Color.Green, Color.Cyan) else (Color.DarkGray, Color.Blue)

    val style =
      if (isFocused) Style(fg = Some(Color.White), bg = Some(focusBg), addModifier = Modifier.BOLD)
      else if (isHovered) Style(fg = Some(Color.White), bg = Some(normalBg), addModifier = Modifier.BOLD)
      else Style(fg = Some(Color.White), bg = Some(normalBg))

    val buttonText = s" $label "
    ctx.buffer.setString(area.x, area.y, buttonText, style)

    val clickArea = Rect(area.x, area.y, buttonText.length.toShort, 1)
    ctx.onClick(clickArea) { onFocus; onPress }
    ctx.onHover(clickArea)(setHovered(true))
  }

  /** Form row with label and component */
  def FormRow(label: String, isRequired: Boolean = false)(child: Component): Component =
    Component.named(s"FormRow:$label") { (ctx, area) =>
      val labelStyle = Style(fg = Some(Color.Cyan))
      val requiredStyle = Style(fg = Some(Color.Red))

      val requiredMark = if (isRequired) "*" else " "
      ctx.buffer.setString(area.x, area.y, requiredMark, requiredStyle)

      child.render(ctx, Rect((area.x + 2).toShort, area.y, (area.width - 2).toShort, area.height))
    }

  /** Error message display */
  def ErrorMessage(message: Option[String]): Component = Component { (ctx, area) =>
    message.foreach { msg =>
      val style = Style(fg = Some(Color.Red))
      ctx.buffer.setString(area.x, area.y, s"⚠ $msg", style)
    }
  }

  /** Success message display */
  def SuccessMessage(message: String): Component = Component { (ctx, area) =>
    val style = Style(fg = Some(Color.Green))
    ctx.buffer.setString(area.x, area.y, s"✓ $message", style)
  }

  /** Form section with title */
  def FormSection(title: String)(children: Component*): Component = Component.named(s"Section:$title") { (ctx, area) =>
    val titleStyle = Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD)
    ctx.buffer.setString(area.x, area.y, title, titleStyle)

    var y = (area.y + 2).toShort
    children.foreach { child =>
      if (y < area.y + area.height) {
        child.render(ctx, Rect(area.x, y, area.width, 1))
        y = (y + 1).toShort
      }
    }
  }

  /** Form hint text */
  def FormHint(text: String): Component = Component { (ctx, area) =>
    val style = Style(fg = Some(Color.DarkGray))
    ctx.buffer.setString(area.x, area.y, text, style)
  }

  /** Keyboard hint bar at bottom of forms */
  def KeyboardHints(hints: List[(String, String)]): Component = Component { (ctx, area) =>
    val hintStyle = Style(fg = Some(Color.DarkGray))
    val keyStyle = Style(fg = Some(Color.Cyan))

    var x = area.x
    hints.foreach { case (key, action) =>
      val keyText = s"[$key]"
      val actionText = s" $action  "
      ctx.buffer.setString(x, area.y, keyText, keyStyle)
      x = (x + keyText.length).toShort
      ctx.buffer.setString(x, area.y, actionText, hintStyle)
      x = (x + actionText.length).toShort
    }
  }

  // ============================================
  // Card Component
  // ============================================

  val CardHeight: Int = 4

  /** A clickable card with border, icon, title, and subtitle.
    *
    * The card is 4 lines tall with rounded borders and supports:
    *   - Icon with custom color
    *   - Title text (bold)
    *   - Subtitle/detail text
    *   - Selection/hover states with background highlight
    *
    * @param icon
    *   Optional icon character (e.g., "◈", "◇", "+")
    * @param title
    *   Main title text
    * @param subtitle
    *   Secondary detail text
    * @param isSelected
    *   Whether the card is currently selected
    * @param borderColor
    *   Color for the border (defaults to DarkGray, Cyan when selected)
    * @param iconColor
    *   Color for the icon
    * @param maxWidth
    *   Maximum card width (defaults to 60)
    * @param onHover
    *   Optional callback when the card is hovered (for updating external state)
    */
  def Card(
      icon: Option[String] = None,
      title: String,
      subtitle: String = "",
      isSelected: Boolean = false,
      borderColor: Option[Color] = None,
      iconColor: Option[Color] = None,
      maxWidth: Int = 60,
      onHover: Option[() => Unit] = None,
      hoverPosition: Option[(Int, Int)] = None
  )(onClick: => Unit): Component = Component.named(s"Card:$title") { (ctx, area) =>
    val cardWidth = math.min(maxWidth, area.width.toInt)
    val x = area.x
    val y = area.y
    val cardArea = Rect(x, y, cardWidth.toShort, CardHeight.toShort)

    // Check if cursor is currently over this card
    val isHovered = hoverPosition.exists { case (hx, hy) =>
      hx >= cardArea.x && hx < cardArea.x + cardArea.width &&
      hy >= cardArea.y && hy < cardArea.y + cardArea.height
    }
    val highlighted = isSelected || isHovered

    val effectiveBorderColor = borderColor.getOrElse(if (highlighted) Color.Cyan else Color.DarkGray)
    val bgColor = if (highlighted) Some(Color.Cyan) else None
    val fgColor = if (highlighted) Color.Black else Color.White

    val topBorder = "\u256d" + "\u2500" * (cardWidth - 2) + "\u256e"
    val bottomBorder = "\u2570" + "\u2500" * (cardWidth - 2) + "\u256f"
    val emptyLine = "\u2502" + " " * (cardWidth - 2) + "\u2502"

    val borderStyle = Style(fg = Some(effectiveBorderColor), bg = bgColor)
    ctx.buffer.setString(x, y, topBorder.take(cardWidth), borderStyle)
    ctx.buffer.setString(x, (y + 1).toShort, emptyLine.take(cardWidth), borderStyle)
    ctx.buffer.setString(x, (y + 2).toShort, emptyLine.take(cardWidth), borderStyle)
    ctx.buffer.setString(x, (y + 3).toShort, bottomBorder.take(cardWidth), borderStyle)

    val effectiveIconColor = iconColor.getOrElse(if (highlighted) Color.Black else Color.Magenta)
    val iconStyle = Style(fg = Some(effectiveIconColor), bg = bgColor)
    val titleStyle = Style(fg = Some(fgColor), bg = bgColor, addModifier = Modifier.BOLD)
    val subtitleStyle = Style(fg = Some(if (highlighted) Color.DarkGray else Color.Gray), bg = bgColor)

    icon match {
      case Some(ic) =>
        ctx.buffer.setString((x + 2).toShort, (y + 1).toShort, s"  $ic  ", iconStyle)
        ctx.buffer.setString((x + 7).toShort, (y + 1).toShort, title.take(cardWidth - 10), titleStyle)
        ctx.buffer.setString((x + 7).toShort, (y + 2).toShort, subtitle.take(cardWidth - 10), subtitleStyle)
      case None =>
        ctx.buffer.setString((x + 2).toShort, (y + 1).toShort, title.take(cardWidth - 4), titleStyle)
        ctx.buffer.setString((x + 2).toShort, (y + 2).toShort, subtitle.take(cardWidth - 4), subtitleStyle)
    }

    ctx.onClick(cardArea)(onClick)
    ctx.onHover(cardArea) {
      onHover.foreach(_.apply())
    }
  }

  /** Simplified card for "Add" actions */
  def AddCard(
      title: String = "Add New",
      subtitle: String = "",
      isSelected: Boolean = false,
      maxWidth: Int = 60,
      onHover: Option[() => Unit] = None,
      hoverPosition: Option[(Int, Int)] = None
  )(onClick: => Unit): Component = Component.named(s"AddCard:$title") { (ctx, area) =>
    val cardWidth = math.min(maxWidth, area.width.toInt)
    val x = area.x
    val y = area.y
    val cardArea = Rect(x, y, cardWidth.toShort, CardHeight.toShort)

    // Check if cursor is currently over this card
    val isHovered = hoverPosition.exists { case (hx, hy) =>
      hx >= cardArea.x && hx < cardArea.x + cardArea.width &&
      hy >= cardArea.y && hy < cardArea.y + cardArea.height
    }
    val highlighted = isSelected || isHovered

    val borderColor = if (highlighted) Color.Cyan else Color.Green
    val bgColor = if (highlighted) Some(Color.Cyan) else None
    val fgColor = if (highlighted) Color.Black else Color.Green

    val topBorder = "\u256d" + "\u2500" * (cardWidth - 2) + "\u256e"
    val bottomBorder = "\u2570" + "\u2500" * (cardWidth - 2) + "\u256f"
    val emptyLine = "\u2502" + " " * (cardWidth - 2) + "\u2502"

    val borderStyle = Style(fg = Some(borderColor), bg = bgColor)
    ctx.buffer.setString(x, y, topBorder.take(cardWidth), borderStyle)
    ctx.buffer.setString(x, (y + 1).toShort, emptyLine.take(cardWidth), borderStyle)
    ctx.buffer.setString(x, (y + 2).toShort, emptyLine.take(cardWidth), borderStyle)
    ctx.buffer.setString(x, (y + 3).toShort, bottomBorder.take(cardWidth), borderStyle)

    val titleStyle = Style(fg = Some(fgColor), bg = bgColor, addModifier = Modifier.BOLD)
    val subtitleStyle = Style(fg = Some(if (highlighted) Color.DarkGray else Color.DarkGray), bg = bgColor)

    ctx.buffer.setString((x + 2).toShort, (y + 1).toShort, s"  +  $title", titleStyle)
    if (subtitle.nonEmpty) {
      ctx.buffer.setString((x + 7).toShort, (y + 2).toShort, subtitle.take(cardWidth - 10), subtitleStyle)
    }

    ctx.onClick(cardArea)(onClick)
    ctx.onHover(cardArea) {
      onHover.foreach(_.apply())
    }
  }

  /** Renders a vertical scrollbar
    * @param totalItems
    *   total number of items in the list
    * @param visibleItems
    *   number of items visible at once
    * @param scrollOffset
    *   current scroll position (0-based)
    * @param trackColor
    *   color of the scrollbar track
    * @param thumbColor
    *   color of the scrollbar thumb
    */
  def Scrollbar(
      totalItems: Int,
      visibleItems: Int,
      scrollOffset: Int,
      trackColor: Color = Color.DarkGray,
      thumbColor: Color = Color.Gray
  ): Component = Component.named("Scrollbar") { (ctx, area) =>
    val x = area.x
    val y = area.y
    val height = area.height.toInt

    if (totalItems > visibleItems && height >= 3) {
      // Draw track
      val trackChar = "\u2502" // │ thin vertical line
      for (i <- 0 until height) {
        ctx.buffer.setString(x, (y + i).toShort, trackChar, Style(fg = Some(trackColor)))
      }

      // Calculate thumb position and size
      val thumbHeight = math.max(1, (visibleItems.toDouble / totalItems * height).toInt)
      val maxOffset = totalItems - visibleItems
      val scrollRatio = if (maxOffset > 0) scrollOffset.toDouble / maxOffset else 0.0
      val thumbStart = (scrollRatio * (height - thumbHeight)).toInt

      // Draw thumb
      val thumbChar = "\u2588" // █ full block
      for (i <- 0 until thumbHeight) {
        val thumbY = y + thumbStart + i
        if (thumbY < y + height) {
          ctx.buffer.setString(x, thumbY.toShort, thumbChar, Style(fg = Some(thumbColor)))
        }
      }
    }
  }

  // ============================================
  // MultiSelect Component
  // ============================================

  /** State for MultiSelect popup */
  case class MultiSelectState(
      isOpen: Boolean,
      cursorIndex: Int,
      selectedItems: Set[String],
      hasAllOption: Boolean,
      allSelected: Boolean
  )

  object MultiSelectState {
    def initial(selected: Set[String], hasAllOption: Boolean, allSelected: Boolean): MultiSelectState =
      MultiSelectState(
        isOpen = false,
        cursorIndex = 0,
        selectedItems = selected,
        hasAllOption = hasAllOption,
        allSelected = allSelected
      )
  }

  /** A multiselect field that shows a summary and opens a popup on click.
    *
    * @param label
    *   Field label
    * @param options
    *   List of (value, displayLabel) options
    * @param selectedValues
    *   Set of currently selected values
    * @param allSelected
    *   Whether "all" is selected (overrides individual selections)
    * @param hasAllOption
    *   Whether to show an "All" option at the top
    * @param isFocused
    *   Whether this field is currently focused
    * @param isOpen
    *   Whether the popup is open
    * @param cursorIndex
    *   Current cursor position in popup
    * @param onOpen
    *   Called when popup should open
    * @param onClose
    *   Called when popup should close (with final selections)
    * @param onToggle
    *   Called when an item is toggled
    * @param onToggleAll
    *   Called when "All" is toggled
    * @param onCursorMove
    *   Called when cursor moves in popup
    */
  def MultiSelect(
      label: String,
      options: List[(String, String)],
      selectedValues: Set[String],
      allSelected: Boolean,
      hasAllOption: Boolean = true,
      isFocused: Boolean = false,
      isOpen: Boolean = false,
      cursorIndex: Int = 0
  )(
      onOpen: () => Unit,
      onClose: () => Unit,
      onToggle: String => Unit,
      onToggleAll: () => Unit,
      onCursorMove: Int => Unit
  ): Component = Component.named(s"MultiSelect:$label") { (ctx, area) =>
    val (isHovered, setHovered) = ctx.useState(false)

    // Build summary text
    val summaryText =
      if (allSelected) "All"
      else if (selectedValues.isEmpty) "(none selected)"
      else if (selectedValues.size <= 2) selectedValues.toList.sorted.mkString(", ")
      else s"${selectedValues.size} selected"

    val labelStyle = Style(fg = Some(Color.Cyan))
    val valueStyle =
      if (isFocused) Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
      else if (isHovered) Style(fg = Some(Color.White), bg = Some(Color.DarkGray))
      else Style(fg = Some(Color.White))
    val hintStyle = Style(fg = Some(Color.DarkGray))

    val labelWidth = label.length + 2
    ctx.buffer.setString(area.x, area.y, s"$label: ", labelStyle)

    val valueX = (area.x + labelWidth).toShort
    val displayText = s"[$summaryText]"
    ctx.buffer.setString(valueX, area.y, displayText, valueStyle)

    val hintX = (valueX + displayText.length + 1).toShort
    if (isFocused && !isOpen) {
      ctx.buffer.setString(hintX, area.y, "[Enter]", hintStyle)
    }

    // Click area for the value
    val clickArea = Rect(valueX, area.y, (displayText.length + 2).toShort, 1)
    ctx.onClick(clickArea)(onOpen())
    ctx.onHover(clickArea)(setHovered(true))
  }

  /** Popup overlay for MultiSelect. Render this separately over the main content when isOpen=true. */
  def MultiSelectPopup(
      title: String,
      options: List[(String, String)],
      selectedValues: Set[String],
      allSelected: Boolean,
      hasAllOption: Boolean,
      cursorIndex: Int,
      popupWidth: Int = 50,
      popupHeight: Int = 15
  )(
      onClose: () => Unit,
      onAccept: () => Unit,
      onToggle: String => Unit,
      onToggleAll: () => Unit,
      onCursorMove: Int => Unit
  ): Component = Component.named(s"MultiSelectPopup:$title") { (ctx, area) =>
    // Calculate popup position (centered)
    val actualWidth = math.min(popupWidth, area.width.toInt - 4)
    val actualHeight = math.min(popupHeight, area.height.toInt - 4)
    val popupX = (area.x + (area.width - actualWidth) / 2).toShort
    val popupY = (area.y + (area.height - actualHeight) / 2).toShort

    val popupArea = Rect(popupX, popupY, actualWidth.toShort, actualHeight.toShort)

    // Clear background
    for (py <- popupY.toInt until popupY.toInt + actualHeight) {
      for (px <- popupX.toInt until popupX.toInt + actualWidth) {
        ctx.buffer.get(px, py).setSymbol(" ").setStyle(Style(bg = Some(Color.Black)))
      }
    }

    // Draw border
    val borderStyle = Style(fg = Some(Color.Cyan))
    val topBorder = "\u2554" + "\u2550" * (actualWidth - 2) + "\u2557"
    val bottomBorder = "\u255a" + "\u2550" * (actualWidth - 2) + "\u255d"
    ctx.buffer.setString(popupX, popupY, topBorder, borderStyle)
    ctx.buffer.setString(popupX, (popupY + actualHeight - 1).toShort, bottomBorder, borderStyle)

    for (i <- 1 until actualHeight - 1) {
      ctx.buffer.setString(popupX, (popupY + i).toShort, "\u2551", borderStyle)
      ctx.buffer.setString((popupX + actualWidth - 1).toShort, (popupY + i).toShort, "\u2551", borderStyle)
    }

    // Title
    val titleStyle = Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)
    ctx.buffer.setString((popupX + 2).toShort, (popupY + 1).toShort, title.take(actualWidth - 4), titleStyle)

    // Options list
    val listStartY = popupY + 3
    val listHeight = actualHeight - 6
    val allItems = if (hasAllOption) ("__all__", "All sources") :: options else options
    val totalItems = allItems.size

    // Calculate visible window
    val visibleCount = math.min(listHeight, totalItems)
    val scrollOffset = math.max(0, math.min(cursorIndex - visibleCount / 2, totalItems - visibleCount))

    allItems.zipWithIndex.slice(scrollOffset, scrollOffset + visibleCount).foreach { case ((value, label), globalIdx) =>
      val localIdx = globalIdx - scrollOffset
      val y = listStartY + localIdx
      if (y < popupY + actualHeight - 3) {
        val isCursor = globalIdx == cursorIndex
        val isSelected =
          if (value == "__all__") allSelected
          else if (allSelected) true
          else selectedValues.contains(value)

        val checkbox = if (isSelected) "[x]" else "[ ]"
        val prefix = if (isCursor) "> " else "  "
        val style =
          if (isCursor) Style(fg = Some(Color.White), addModifier = Modifier.BOLD)
          else if (isSelected) Style(fg = Some(Color.Green))
          else Style(fg = Some(Color.White))

        val line = s"$prefix$checkbox $label"
        ctx.buffer.setString((popupX + 2).toShort, y.toShort, line.take(actualWidth - 4), style)

        // Click handler for this row
        val rowArea = Rect((popupX + 2).toShort, y.toShort, (actualWidth - 4).toShort, 1)
        ctx.onClick(rowArea) {
          if (value == "__all__") onToggleAll()
          else onToggle(value)
        }
        ctx.onHover(rowArea)(onCursorMove(globalIdx))
      }
    }

    // Scroll indicator
    if (totalItems > visibleCount) {
      if (scrollOffset > 0) {
        ctx.buffer.setString((popupX + actualWidth - 3).toShort, listStartY.toShort, "▲", Style(fg = Some(Color.Cyan)))
      }
      if (scrollOffset + visibleCount < totalItems) {
        ctx.buffer.setString(
          (popupX + actualWidth - 3).toShort,
          (listStartY + visibleCount - 1).toShort,
          "▼",
          Style(fg = Some(Color.Cyan))
        )
      }
    }

    // Hints
    val hintY = (popupY + actualHeight - 2).toShort
    val hint = "[Space] Toggle  [Enter] Accept  [Esc] Cancel"
    ctx.buffer.setString((popupX + 2).toShort, hintY, hint.take(actualWidth - 4), Style(fg = Some(Color.DarkGray)))

    // Keyboard handlers via scroll events (parent should handle actual key events)
    ctx.onScrollUp(popupArea) {
      if (cursorIndex > 0) onCursorMove(cursorIndex - 1)
    }
    ctx.onScrollDown(popupArea) {
      if (cursorIndex < totalItems - 1) onCursorMove(cursorIndex + 1)
    }
  }

  /** Renders a vertical scrollbar with arrows at top and bottom
    * @param totalItems
    *   total number of items in the list
    * @param visibleItems
    *   number of items visible at once
    * @param scrollOffset
    *   current scroll position (0-based)
    * @param trackColor
    *   color of the scrollbar track
    * @param thumbColor
    *   color of the scrollbar thumb
    * @param onScrollUp
    *   callback when up arrow is clicked
    * @param onScrollDown
    *   callback when down arrow is clicked
    */
  def ScrollbarWithArrows(
      totalItems: Int,
      visibleItems: Int,
      scrollOffset: Int,
      trackColor: Color = Color.DarkGray,
      thumbColor: Color = Color.Gray,
      arrowColor: Color = Color.Cyan,
      onScrollUp: Option[() => Unit] = None,
      onScrollDown: Option[() => Unit] = None
  ): Component = Component.named("ScrollbarWithArrows") { (ctx, area) =>
    val x = area.x
    val y = area.y
    val height = area.height.toInt

    if (totalItems > visibleItems && height >= 5) {
      // Draw up arrow
      val canScrollUp = scrollOffset > 0
      val upStyle = if (canScrollUp) Style(fg = Some(arrowColor)) else Style(fg = Some(trackColor))
      ctx.buffer.setString(x, y, "▲", upStyle)
      if (canScrollUp) {
        val upArea = Rect(x, y, 1, 1)
        onScrollUp.foreach(handler => ctx.onClick(upArea)(handler()))
      }

      // Draw down arrow
      val maxOffset = totalItems - visibleItems
      val canScrollDown = scrollOffset < maxOffset
      val downStyle = if (canScrollDown) Style(fg = Some(arrowColor)) else Style(fg = Some(trackColor))
      ctx.buffer.setString(x, (y + height - 1).toShort, "▼", downStyle)
      if (canScrollDown) {
        val downArea = Rect(x, (y + height - 1).toShort, 1, 1)
        onScrollDown.foreach(handler => ctx.onClick(downArea)(handler()))
      }

      // Draw track (between arrows)
      val trackHeight = height - 2
      if (trackHeight > 0) {
        val trackChar = "\u2502"
        for (i <- 0 until trackHeight) {
          ctx.buffer.setString(x, (y + 1 + i).toShort, trackChar, Style(fg = Some(trackColor)))
        }

        // Calculate and draw thumb
        val thumbHeight = math.max(1, (visibleItems.toDouble / totalItems * trackHeight).toInt)
        val scrollRatio = if (maxOffset > 0) scrollOffset.toDouble / maxOffset else 0.0
        val thumbStart = (scrollRatio * (trackHeight - thumbHeight)).toInt

        val thumbChar = "\u2588"
        for (i <- 0 until thumbHeight) {
          val thumbY = y + 1 + thumbStart + i
          if (thumbY < y + height - 1) {
            ctx.buffer.setString(x, thumbY.toShort, thumbChar, Style(fg = Some(thumbColor)))
          }
        }
      }
    }
  }

  // ============================================================================
  // Form Infrastructure (mouse-only, keyboard handled by outer code)
  // ============================================================================

  /** Text input field for forms - renders with focus/hover states, handles clicks */
  def FormTextField(
      label: String,
      value: String,
      isFocused: Boolean,
      labelWidth: Int,
      onFocus: () => Unit,
      hoverPosition: Option[(Int, Int)]
  ): Component = Component.named(s"FormTextField:$label") { (ctx, area) =>
    val valueX = (area.x + labelWidth).toShort
    val maxValueWidth = math.max(1, area.width.toInt - labelWidth)
    val valueArea = Rect(valueX, area.y, maxValueWidth.toShort, 1)

    val isHovered = hoverPosition.exists { case (hx, hy) =>
      hx >= valueArea.x && hx < valueArea.x + valueArea.width && hy == valueArea.y
    }

    val labelStyle = Style(fg = Some(Color.Cyan))
    val valueStyle =
      if (isFocused) Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
      else if (isHovered) Style(fg = Some(Color.White), bg = Some(Color.DarkGray))
      else Style(fg = Some(Color.White))

    val paddedLabel = label.padTo(labelWidth, ' ')
    ctx.buffer.setString(area.x, area.y, paddedLabel, labelStyle)

    val cursor = if (isFocused) "_" else ""
    val displayValue = value + cursor
    ctx.buffer.setString(valueX, area.y, displayValue.take(maxValueWidth).padTo(maxValueWidth, ' '), valueStyle)

    ctx.onClick(valueArea)(onFocus())
  }

  /** Select field with left/right arrows - click arrows to change, wraps around */
  def FormSelectField[T](
      label: String,
      options: List[(T, String)],
      value: T,
      isFocused: Boolean,
      labelWidth: Int,
      onFocus: () => Unit,
      onChange: T => Unit,
      hoverPosition: Option[(Int, Int)]
  ): Component = Component.named(s"FormSelectField:$label") { (ctx, area) =>
    val valueX = (area.x + labelWidth).toShort
    val availableWidth = math.max(0, area.width.toInt - labelWidth)
    val fullArea = Rect(valueX, area.y, availableWidth.toShort, 1)
    val isHovered = hoverPosition.exists { case (hx, hy) =>
      hx >= fullArea.x && hx < fullArea.x + fullArea.width && hy == fullArea.y
    }

    val currentIdx = options.indexWhere(_._1 == value)
    val fullLabel = options.find(_._1 == value).map(_._2).getOrElse("?")

    val labelStyle = Style(fg = Some(Color.Cyan))
    val arrowStyle =
      if (isFocused) Style(fg = Some(Color.Yellow), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
      else if (isHovered) Style(fg = Some(Color.Yellow), bg = Some(Color.DarkGray))
      else Style(fg = Some(Color.DarkGray))
    val valueStyle =
      if (isFocused) Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
      else if (isHovered) Style(fg = Some(Color.White), bg = Some(Color.DarkGray))
      else Style(fg = Some(Color.White))

    // Clear the entire row first to prevent any leftover characters
    val clearStyle = Style()
    for (i <- 0 until area.width.toInt) {
      ctx.buffer.setString((area.x + i).toShort, area.y, " ", clearStyle)
    }

    // Render label
    val paddedLabel = label.padTo(labelWidth, ' ').take(labelWidth)
    ctx.buffer.setString(area.x, area.y, paddedLabel.take(area.width.toInt), labelStyle)

    if (availableWidth > 6) {
      val maxLabelLen = availableWidth - 6 // Account for "< " + " >"
      val currentLabel = fullLabel.take(math.max(1, maxLabelLen))
      val displayStr = s"< $currentLabel >"

      // Render the actual content (row already cleared above)
      ctx.buffer.setString(valueX, area.y, "<", arrowStyle)
      ctx.buffer.setString((valueX + 1).toShort, area.y, " ", valueStyle)
      ctx.buffer.setString((valueX + 2).toShort, area.y, currentLabel, valueStyle)
      val rightArrowX = valueX + 2 + currentLabel.length
      ctx.buffer.setString(rightArrowX.toShort, area.y, " ", valueStyle)
      if (rightArrowX + 1 < area.x + area.width) {
        ctx.buffer.setString((rightArrowX + 1).toShort, area.y, ">", arrowStyle)
      }

      val totalWidth = math.min(displayStr.length, availableWidth)

      // Left arrow click area
      val leftArrowArea = Rect(valueX, area.y, 2, 1)
      ctx.onClick(leftArrowArea) {
        onFocus()
        if (options.nonEmpty) {
          val prevIdx = (currentIdx - 1 + options.length) % options.length
          onChange(options(prevIdx)._1)
        }
      }

      // Right arrow click area
      val rightArrowArea = Rect((rightArrowX).toShort, area.y, 2, 1)
      ctx.onClick(rightArrowArea) {
        onFocus()
        if (options.nonEmpty) {
          val nextIdx = (currentIdx + 1) % options.length
          onChange(options(nextIdx)._1)
        }
      }

      // Label click area
      val labelClickArea = Rect((valueX + 2).toShort, area.y, math.max(1, currentLabel.length).toShort, 1)
      ctx.onClick(labelClickArea) {
        onFocus()
        if (options.nonEmpty) {
          val nextIdx = (currentIdx + 1) % options.length
          onChange(options(nextIdx)._1)
        }
      }

    }
  }

  /** Multi-select field - shows summary, click to toggle popup */
  def FormMultiSelectField(
      label: String,
      options: List[(String, String)],
      selected: Set[String],
      allSelected: Boolean,
      hasAllOption: Boolean,
      isFocused: Boolean,
      labelWidth: Int,
      onFocus: () => Unit,
      onToggle: () => Unit
  ): Component = Component.named(s"FormMultiSelectField:$label") { (ctx, area) =>
    val (isHovered, setHovered) = ctx.useState(false)

    val labelStyle = Style(fg = Some(Color.Cyan))
    val valueStyle =
      if (isFocused) Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
      else if (isHovered) Style(fg = Some(Color.White), bg = Some(Color.DarkGray))
      else Style(fg = Some(Color.White))

    val paddedLabel = label.padTo(labelWidth, ' ')
    ctx.buffer.setString(area.x, area.y, paddedLabel, labelStyle)

    val valueX = (area.x + labelWidth).toShort
    val summary = if (allSelected) {
      "All"
    } else if (selected.isEmpty) {
      "None"
    } else if (selected.size == 1) {
      selected.head
    } else {
      s"${selected.size} selected"
    }
    val displayText = s"[$summary] ▼"
    val maxValueWidth = math.max(1, area.width.toInt - labelWidth)
    ctx.buffer.setString(valueX, area.y, displayText.take(maxValueWidth), valueStyle)

    val valueArea = Rect(valueX, area.y, displayText.length.toShort, 1)
    ctx.onClick(valueArea) {
      onFocus()
      onToggle()
    }
    ctx.onHover(valueArea)(setHovered(true))
  }

  /** Multi-select popup overlay - renders the dropdown with checkboxes */
  def FormMultiSelectPopup(
      options: List[(String, String)],
      selected: Set[String],
      allSelected: Boolean,
      hasAllOption: Boolean,
      cursorIndex: Int,
      popupX: Short,
      popupY: Short,
      onSelect: String => Unit,
      onToggleAll: () => Unit,
      onCursorMove: Int => Unit,
      onClose: () => Unit
  ): Component = Component.named("FormMultiSelectPopup") { (ctx, area) =>
    val popupItems = (if (hasAllOption) List(("__all__", "All")) else Nil) ++ options
    val popupHeight = math.min(popupItems.size + 2, 12)
    val popupWidth = math.max(30, popupItems.map(_._2.length).maxOption.getOrElse(10) + 8)
    val fullPopupArea = Rect(popupX, popupY, popupWidth.toShort, popupHeight.toShort)

    // Register handlers in order (reverse order for priority):
    // 1. Full area onClose (lowest priority - catches clicks outside popup)
    // 2. Popup area no-op (medium priority - catches clicks on popup border/bg)
    // 3. Item handlers (highest priority - catches clicks on items)
    ctx.onClick(area) { onClose() }
    ctx.onClick(fullPopupArea) { () } // no-op to prevent popup border clicks from closing

    val popupBg = Style(bg = Some(Color.DarkGray))
    for (py <- popupY.toInt until popupY.toInt + popupHeight) {
      ctx.buffer.setString(popupX, py.toShort, " " * popupWidth, popupBg)
    }

    val borderStyle = Style(fg = Some(Color.Cyan), bg = Some(Color.DarkGray))
    ctx.buffer.setString(popupX, popupY, "┌" + "─" * (popupWidth - 2) + "┐", borderStyle)
    ctx.buffer.setString(popupX, (popupY + popupHeight - 1).toShort, "└" + "─" * (popupWidth - 2) + "┘", borderStyle)
    for (py <- popupY.toInt + 1 until popupY.toInt + popupHeight - 1) {
      ctx.buffer.setString(popupX, py.toShort, "│", borderStyle)
      ctx.buffer.setString((popupX + popupWidth - 1).toShort, py.toShort, "│", borderStyle)
    }

    val visibleItems = popupHeight - 2
    val scrollOffset = math.max(0, cursorIndex - visibleItems + 1)
    popupItems.zipWithIndex.slice(scrollOffset, scrollOffset + visibleItems).foreach { case ((value, displayLabel), sliceIdx) =>
      val actualIdx = scrollOffset + sliceIdx
      val itemY = (popupY + 1 + sliceIdx).toShort
      val isItemSelected = if (value == "__all__") allSelected else selected.contains(value)
      val isCursorHere = actualIdx == cursorIndex

      val checkbox = if (isItemSelected) "[✓]" else "[ ]"
      val itemStyle =
        if (isCursorHere) Style(fg = Some(Color.White), bg = Some(Color.Blue))
        else Style(fg = Some(Color.White), bg = Some(Color.DarkGray))

      val itemText = s" $checkbox $displayLabel"
      ctx.buffer.setString((popupX + 1).toShort, itemY, itemText.padTo(popupWidth - 2, ' ').take(popupWidth - 2), itemStyle)

      val itemArea = Rect((popupX + 1).toShort, itemY, (popupWidth - 2).toShort, 1)
      ctx.onClick(itemArea) {
        if (value == "__all__") onToggleAll()
        else onSelect(value)
      }
      ctx.onHover(itemArea)(onCursorMove(actualIdx))
    }
    ctx.onScrollUp(fullPopupArea) {
      val newCursor = if (cursorIndex <= 0) popupItems.length - 1 else cursorIndex - 1
      onCursorMove(newCursor)
    }
    ctx.onScrollDown(fullPopupArea) {
      val newCursor = if (cursorIndex >= popupItems.length - 1) 0 else cursorIndex + 1
      onCursorMove(newCursor)
    }
  }

  /** Button field for forms */
  def FormButtonField(
      label: String,
      buttonLabel: String,
      isPrimary: Boolean,
      isFocused: Boolean,
      labelWidth: Int,
      onFocus: () => Unit,
      onPress: () => Unit,
      hoverPosition: Option[(Int, Int)]
  ): Component = Component.named(s"FormButtonField:$label") { (ctx, area) =>
    val labelStyle = Style(fg = Some(Color.Cyan))

    if (label.nonEmpty) {
      val paddedLabel = label.padTo(labelWidth, ' ')
      ctx.buffer.setString(area.x, area.y, paddedLabel, labelStyle)
    }

    val buttonX = (area.x + (if (label.nonEmpty) labelWidth else 0)).toShort
    val buttonText = s" $buttonLabel "
    val buttonArea = Rect(buttonX, area.y, buttonText.length.toShort, 1)

    val isHovered = hoverPosition.exists { case (hx, hy) =>
      hx >= buttonArea.x && hx < buttonArea.x + buttonArea.width && hy == buttonArea.y
    }

    val (normalBg, focusBg) = if (isPrimary) (Color.Green, Color.Cyan) else (Color.DarkGray, Color.Blue)
    val buttonStyle =
      if (isFocused) Style(fg = Some(Color.White), bg = Some(focusBg), addModifier = Modifier.BOLD)
      else if (isHovered) Style(fg = Some(Color.White), bg = Some(normalBg), addModifier = Modifier.BOLD)
      else Style(fg = Some(Color.White), bg = Some(normalBg))

    ctx.buffer.setString(buttonX, area.y, buttonText, buttonStyle)

    ctx.onClick(buttonArea) {
      onFocus()
      onPress()
    }
  }

  /** Unsaved changes confirmation dialog with clickable buttons */
  def UnsavedChangesDialog(
      focusedButton: Int,
      onSave: () => Unit,
      onDiscard: () => Unit,
      onCancel: () => Unit,
      onSetFocus: Int => Unit
  ): Component = Component.named("UnsavedChangesDialog") { (ctx, area) =>
    val popupWidth: Short = 50
    val popupHeight: Short = 10
    val popupX: Short = ((area.width - popupWidth) / 2).toShort
    val popupY: Short = ((area.height - popupHeight) / 2).toShort

    val popupArea = Rect(
      x = popupX,
      y = popupY,
      width = popupWidth,
      height = popupHeight
    )

    ctx.frame.renderWidget(ClearWidget, popupArea)

    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Unsaved Changes", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    ctx.frame.renderWidget(block, popupArea)

    val innerX = (popupX + 2).toShort
    var y = (popupY + 2).toShort

    ctx.buffer.setString(
      innerX,
      y,
      "You have unsaved changes!",
      Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD)
    )
    y = (y + 2).toShort

    ctx.buffer.setString(innerX, y, "What would you like to do?", Style())
    y = (y + 2).toShort

    val buttonLabels = List("Save", "Discard", "Cancel")
    val buttonActions = List(onSave, onDiscard, onCancel)
    var buttonX = innerX.toInt

    buttonLabels.zipWithIndex.foreach { case (label, idx) =>
      val isFocused = idx == focusedButton
      val (isHovered, setHovered) = ctx.useState(false)

      val buttonStyle =
        if (isFocused) Style(fg = Some(Color.Black), bg = Some(Color.Yellow), addModifier = Modifier.BOLD)
        else if (isHovered) Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
        else Style(fg = Some(Color.White), bg = Some(Color.DarkGray))

      val buttonText = s" $label "
      ctx.buffer.setString(buttonX.toShort, y, buttonText, buttonStyle)

      val buttonArea = Rect(buttonX.toShort, y, buttonText.length.toShort, 1)
      ctx.onClick(buttonArea) {
        onSetFocus(idx)
        buttonActions(idx)()
      }
      ctx.onHover(buttonArea) {
        setHovered(true)
        onSetFocus(idx)
      }

      buttonX += buttonText.length + 1
    }

    y = (y + 2).toShort
    ctx.buffer.setString(
      innerX,
      y,
      "[Tab] switch  [Enter] confirm  [Esc] cancel",
      Style(fg = Some(Color.DarkGray))
    )
  }

  /** Exit confirmation dialog with clickable buttons */
  def ExitConfirmDialog(
      focusedButton: Int,
      onYes: () => Unit,
      onNo: () => Unit,
      onSetFocus: Int => Unit
  ): Component = Component.named("ExitConfirmDialog") { (ctx, area) =>
    val popupWidth: Short = 40
    val popupHeight: Short = 7
    val popupX: Short = ((area.width - popupWidth) / 2).toShort
    val popupY: Short = ((area.height - popupHeight) / 2).toShort

    val popupArea = Rect(
      x = popupX,
      y = popupY,
      width = popupWidth,
      height = popupHeight
    )

    ctx.frame.renderWidget(ClearWidget, popupArea)

    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Confirm Exit", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    ctx.frame.renderWidget(block, popupArea)

    val innerX = (popupX + 2).toShort
    var y = (popupY + 2).toShort

    ctx.buffer.setString(
      innerX,
      y,
      "Are you sure you want to exit?",
      Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD)
    )
    y = (y + 2).toShort

    val buttonLabels = List("Yes", "No")
    val buttonActions = List(onYes, onNo)
    var buttonX = innerX.toInt

    buttonLabels.zipWithIndex.foreach { case (label, idx) =>
      val isFocused = idx == focusedButton
      val (isHovered, setHovered) = ctx.useState(false)

      val buttonStyle =
        if (isFocused) Style(fg = Some(Color.Black), bg = Some(Color.Yellow), addModifier = Modifier.BOLD)
        else if (isHovered) Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
        else Style(fg = Some(Color.White), bg = Some(Color.DarkGray))

      val buttonText = s" $label "
      ctx.buffer.setString(buttonX.toShort, y, buttonText, buttonStyle)

      val buttonArea = Rect(buttonX.toShort, y, buttonText.length.toShort, 1)
      ctx.onClick(buttonArea) {
        onSetFocus(idx)
        buttonActions(idx)()
      }
      ctx.onHover(buttonArea) {
        setHovered(true)
        onSetFocus(idx)
      }

      buttonX += buttonText.length + 1
    }
  }
}

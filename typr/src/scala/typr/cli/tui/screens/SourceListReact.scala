package typr.cli.tui.screens

import io.circe.Json
import tui.*
import tui.react.*
import tui.react.Elements.{BackButton, Hint}
import tui.widgets.*
import typr.cli.tui.ConnectionTestResult
import typr.cli.tui.GlobalCallbacks
import typr.cli.tui.GlobalState
import typr.cli.tui.Location
import typr.cli.tui.SourceName
import typr.cli.tui.SourceStatus

/** React-based SourceList with sleek card-based design */
object SourceListReact {

  val CardHeight: Int = 4

  case class Props(
      globalState: GlobalState,
      callbacks: GlobalCallbacks,
      testResults: Map[String, ConnectionTestResult]
  )

  def component(props: Props): Component = {
    val sources = props.globalState.config.sources.getOrElse(Map.empty).toList.sortBy(_._1)

    Component.named("SourceList") { (ctx, area) =>
      val (selectedIndex, setSelectedIndex) = ctx.useState(0)
      val (scrollOffset, setScrollOffset) = ctx.useState(0)

      val chunks = Layout(
        direction = Direction.Vertical,
        margin = Margin(1),
        constraints = Array(Constraint.Length(3), Constraint.Min(10))
      ).split(area)

      BackButton("Sources")(props.callbacks.goBack()).render(ctx, chunks(0))

      val onAddSource = () => props.callbacks.navigateTo(Location.SourceWizard(typr.cli.tui.SourceWizardStep.SelectType))
      val onEditSource = (name: String) => props.callbacks.navigateTo(Location.SourceEditor(name))
      val onBrowseSource = (name: String) => ()

      val contentArea = chunks(1)
      val innerX = (contentArea.x + 1).toShort
      val innerY = (contentArea.y + 1).toShort
      val innerWidth = math.max(20, contentArea.width - 2).toInt
      val innerHeight = math.max(5, contentArea.height - 3).toInt
      val maxX = innerX + innerWidth

      val block = BlockWidget(
        borders = Borders.ALL,
        borderType = BlockWidget.BorderType.Rounded,
        borderStyle = Style(fg = Some(Color.DarkGray))
      )
      ctx.frame.renderWidget(block, contentArea)

      var y = innerY.toInt

      // Add button card
      val addCardSelected = selectedIndex == 0
      renderAddCard(ctx, innerX, y.toShort, innerWidth, maxX, addCardSelected, onAddSource, () => setSelectedIndex(0))
      y += CardHeight + 1

      // Source cards
      val visibleCards = math.max(1, (innerHeight - CardHeight - 1) / (CardHeight + 1))
      val totalItems = sources.length
      val adjustedOffset = math.min(scrollOffset, math.max(0, totalItems - visibleCards))
      val visibleItems = sources.slice(adjustedOffset, adjustedOffset + visibleCards)

      visibleItems.zipWithIndex.foreach { case ((name, json), relIdx) =>
        if (y + CardHeight <= innerY + innerHeight) {
          val idx = adjustedOffset + relIdx + 1
          val isSelected = idx == selectedIndex
          val testResult = props.testResults.get(name)
          renderSourceCard(
            ctx,
            innerX,
            y.toShort,
            innerWidth,
            maxX,
            name,
            json,
            testResult,
            props.globalState,
            isSelected,
            () => onEditSource(name),
            () => setSelectedIndex(idx)
          )
          y += CardHeight + 1
        }
      }

      // Show scroll indicators
      if (adjustedOffset > 0) {
        safeSetString(ctx.buffer, (innerX + innerWidth - 8).toShort, innerY, maxX, "▲ more", Style(fg = Some(Color.DarkGray)))
      }
      if (adjustedOffset + visibleCards < totalItems) {
        safeSetString(ctx.buffer, (innerX + innerWidth - 8).toShort, (innerY + innerHeight - 1).toShort, maxX, "▼ more", Style(fg = Some(Color.DarkGray)))
      }

      // Scroll handling
      ctx.onScrollUp(contentArea) {
        val newIdx = math.max(0, selectedIndex - 1)
        setSelectedIndex(newIdx)
        if (newIdx > 0 && newIdx - 1 < adjustedOffset) {
          setScrollOffset(math.max(0, adjustedOffset - 1))
        } else if (newIdx == 0) {
          setScrollOffset(0)
        }
      }

      ctx.onScrollDown(contentArea) {
        val maxIdx = sources.length
        if (selectedIndex < maxIdx) {
          val newIdx = selectedIndex + 1
          setSelectedIndex(newIdx)
          if (newIdx > 0 && newIdx - 1 >= adjustedOffset + visibleCards) {
            setScrollOffset(adjustedOffset + 1)
          }
        }
      }

      // Hint
      val hintY = (contentArea.y + contentArea.height - 2).toShort
      val hint = if (selectedIndex == 0) {
        "Scroll to navigate • [Enter] Add • [Esc] Back"
      } else {
        "Scroll to navigate • [Enter] Edit • [b] Browse • [Esc] Back"
      }
      Hint(hint).render(ctx, Rect((contentArea.x + 2).toShort, hintY, (contentArea.width - 4).toShort, 1))
    }
  }

  private def renderAddCard(
      ctx: RenderContext,
      x: Short,
      y: Short,
      width: Int,
      maxX: Int,
      isSelected: Boolean,
      onClick: () => Unit,
      onHover: () => Unit
  ): Unit = {
    val cardWidth = math.min(width, 70)
    val borderColor = if (isSelected) Color.Cyan else Color.Green
    val bgColor = if (isSelected) Some(Color.Blue) else None

    // Draw card border
    val topBorder = "╭" + "─" * (cardWidth - 2) + "╮"
    val bottomBorder = "╰" + "─" * (cardWidth - 2) + "╯"
    val emptyLine = "│" + " " * (cardWidth - 2) + "│"

    val borderStyle = Style(fg = Some(borderColor), bg = bgColor)
    safeSetString(ctx.buffer, x, y, maxX, topBorder, borderStyle)
    safeSetString(ctx.buffer, x, (y + 1).toShort, maxX, emptyLine, borderStyle)
    safeSetString(ctx.buffer, x, (y + 2).toShort, maxX, emptyLine, borderStyle)
    safeSetString(ctx.buffer, x, (y + 3).toShort, maxX, bottomBorder, borderStyle)

    // Draw content
    val contentStyle = Style(fg = Some(Color.Green), bg = bgColor, addModifier = Modifier.BOLD)
    val descStyle = Style(fg = Some(Color.DarkGray), bg = bgColor)

    safeSetString(ctx.buffer, (x + 2).toShort, (y + 1).toShort, maxX, "  +  Add New Source", contentStyle)
    safeSetString(ctx.buffer, (x + 2).toShort, (y + 2).toShort, maxX, "     Database, API, or OpenAPI spec", descStyle)

    val cardArea = Rect(x, y, cardWidth.toShort, CardHeight.toShort)
    ctx.onClick(cardArea)(onClick())
    ctx.onHover(cardArea)(onHover())
  }

  private def renderSourceCard(
      ctx: RenderContext,
      x: Short,
      y: Short,
      width: Int,
      maxX: Int,
      name: String,
      json: Json,
      testResult: Option[ConnectionTestResult],
      state: GlobalState,
      isSelected: Boolean,
      onClick: () => Unit,
      onHover: () => Unit
  ): Unit = {
    val cardWidth = math.min(width, 70)
    val sourceType = json.hcursor.get[String]("type").getOrElse("unknown")
    val isSpecSource = sourceType == "openapi" || sourceType == "jsonschema"

    val (statusIcon, statusColor, statusText) = if (isSpecSource) {
      state.sourceStatuses.get(SourceName(name)) match {
        case Some(_: SourceStatus.ReadySpec)   => ("●", Color.Green, "Ready")
        case Some(_: SourceStatus.LoadingSpec) => ("◕", Color.Cyan, "Loading...")
        case Some(f: SourceStatus.Failed)      => ("✗", Color.Red, f.error.message.take(30))
        case _                                 => ("○", Color.Gray, "Pending")
      }
    } else {
      testResult match {
        case None                                    => ("○", Color.Gray, "Not tested")
        case Some(ConnectionTestResult.Pending)      => ("○", Color.Gray, "Pending")
        case Some(ConnectionTestResult.Testing)      => ("◕", Color.Cyan, "Testing...")
        case Some(ConnectionTestResult.Success(ms))  => ("●", Color.Green, s"Connected (${ms}ms)")
        case Some(ConnectionTestResult.Failure(err)) => ("✗", Color.Red, err.take(30))
      }
    }

    val (typeIcon, typeColor) = sourceType match {
      case "postgres"   => ("🐘", Color.Cyan)
      case "mariadb"    => ("🐬", Color.Cyan)
      case "mysql"      => ("🐬", Color.Cyan)
      case "duckdb"     => ("🦆", Color.Yellow)
      case "oracle"     => ("🔴", Color.Red)
      case "sqlserver"  => ("🟦", Color.Blue)
      case "openapi"    => ("📄", Color.Magenta)
      case "jsonschema" => ("📋", Color.Magenta)
      case _            => ("💾", Color.Gray)
    }

    val details = sourceType match {
      case "duckdb" =>
        val path = json.hcursor.get[String]("path").getOrElse(":memory:")
        path
      case "openapi" | "jsonschema" =>
        val spec = json.hcursor
          .get[String]("spec")
          .getOrElse(
            json.hcursor.downField("specs").downArray.as[String].getOrElse("")
          )
        spec
      case _ =>
        val host = json.hcursor.get[String]("host").getOrElse("")
        val port = json.hcursor.get[Long]("port").map(_.toString).getOrElse("")
        val database = json.hcursor.get[String]("database").getOrElse("")
        if (host.nonEmpty && port.nonEmpty) s"$host:$port/$database"
        else if (host.nonEmpty) s"$host/$database"
        else database
    }

    val borderColor = if (isSelected) typeColor else Color.DarkGray
    val bgColor = if (isSelected) Some(Color.Blue) else None

    // Draw card border
    val topBorder = "╭" + "─" * (cardWidth - 2) + "╮"
    val bottomBorder = "╰" + "─" * (cardWidth - 2) + "╯"
    val emptyLine = "│" + " " * (cardWidth - 2) + "│"

    val borderStyle = Style(fg = Some(borderColor), bg = bgColor)
    safeSetString(ctx.buffer, x, y, maxX, topBorder, borderStyle)
    safeSetString(ctx.buffer, x, (y + 1).toShort, maxX, emptyLine, borderStyle)
    safeSetString(ctx.buffer, x, (y + 2).toShort, maxX, emptyLine, borderStyle)
    safeSetString(ctx.buffer, x, (y + 3).toShort, maxX, bottomBorder, borderStyle)

    // Draw content - Row 1: status icon + name + type
    val statusStyle = Style(fg = Some(statusColor), bg = bgColor)
    val nameStyle = Style(fg = Some(Color.White), bg = bgColor, addModifier = Modifier.BOLD)
    val typeStyle = Style(fg = Some(typeColor), bg = bgColor)

    safeSetString(ctx.buffer, (x + 2).toShort, (y + 1).toShort, maxX, s"  $statusIcon  ", statusStyle)
    safeSetString(ctx.buffer, (x + 7).toShort, (y + 1).toShort, maxX, name.take(25), nameStyle)
    safeSetString(ctx.buffer, (x + 7 + math.min(name.length, 25) + 2).toShort, (y + 1).toShort, maxX, sourceType, typeStyle)

    // Draw content - Row 2: details + status text
    val detailStyle = Style(fg = Some(Color.Gray), bg = bgColor)
    val statusTextStyle = Style(fg = Some(statusColor), bg = bgColor)

    val detailsDisplay = details.take(cardWidth - 20)
    safeSetString(ctx.buffer, (x + 7).toShort, (y + 2).toShort, maxX, detailsDisplay, detailStyle)
    val statusX = x + cardWidth - statusText.length - 3
    if (statusX > x + 7 + detailsDisplay.length) {
      safeSetString(ctx.buffer, statusX.toShort, (y + 2).toShort, maxX, statusText, statusTextStyle)
    }

    val cardArea = Rect(x, y, cardWidth.toShort, CardHeight.toShort)
    ctx.onClick(cardArea)(onClick())
    ctx.onHover(cardArea)(onHover())
  }

  private def safeSetString(buffer: Buffer, x: Short, y: Short, maxX: Int, text: String, style: Style): Unit = {
    val availableWidth = maxX - x
    if (availableWidth > 0 && x >= 0 && y >= 0 && y < buffer.area.height) {
      buffer.setString(x, y, text.take(availableWidth), style)
    }
  }
}

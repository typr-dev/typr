package typr.cli.tui.screens

import tui.*
import tui.react.*
import tui.react.Elements.{BackButton, Hint}
import tui.widgets.*
import typr.cli.commands.Interactive
import typr.cli.tui.ApiInfo
import typr.cli.tui.AppScreen
import typr.cli.tui.BrowserPane
import typr.cli.tui.GlobalCallbacks
import typr.cli.tui.GlobalState
import typr.cli.tui.ModelInfo
import typr.cli.tui.ModelType
import typr.cli.tui.PropertyInfo
import typr.cli.tui.SpecBrowserLevel
import typr.cli.tui.SpecBrowserState
import typr.cli.tui.SpecSourceType

/** React-based SpecBrowser with sleek card-based design */
object SpecBrowserReact {

  val CardHeight: Int = 3

  case class Props(
      globalState: GlobalState,
      callbacks: GlobalCallbacks,
      browserState: SpecBrowserState
  )

  /** UI state managed locally with useState */
  case class UiState(
      selectedModelIndex: Int,
      setSelectedModelIndex: Int => Unit,
      selectedPropertyIndex: Int,
      setSelectedPropertyIndex: Int => Unit,
      selectedApiIndex: Int,
      setSelectedApiIndex: Int => Unit,
      focusedPane: BrowserPane,
      setFocusedPane: BrowserPane => Unit
  ) {
    def currentModel(models: List[ModelInfo]): Option[ModelInfo] = {
      if (models.isEmpty || selectedModelIndex >= models.length) None
      else Some(models(selectedModelIndex))
    }

    def currentProperty(properties: List[PropertyInfo]): Option[PropertyInfo] = {
      if (properties.isEmpty || selectedPropertyIndex >= properties.length) None
      else Some(properties(selectedPropertyIndex))
    }

    def currentApi(apis: List[ApiInfo]): Option[ApiInfo] = {
      if (apis.isEmpty || selectedApiIndex >= apis.length) None
      else Some(apis(selectedApiIndex))
    }
  }

  def component(props: Props): Component = {
    Component.named("SpecBrowserReact") { (ctx, area) =>
      val browserState = props.browserState

      // Initialize local UI state
      val (selectedModelIndex, setSelectedModelIndex) = ctx.useState(browserState.selectedModelIndex)
      val (selectedPropertyIndex, setSelectedPropertyIndex) = ctx.useState(browserState.selectedPropertyIndex)
      val (selectedApiIndex, setSelectedApiIndex) = ctx.useState(browserState.selectedApiIndex)
      val (focusedPane, setFocusedPane) = ctx.useState(browserState.focusedPane)

      val ui = UiState(
        selectedModelIndex = selectedModelIndex,
        setSelectedModelIndex = setSelectedModelIndex,
        selectedPropertyIndex = selectedPropertyIndex,
        setSelectedPropertyIndex = setSelectedPropertyIndex,
        selectedApiIndex = selectedApiIndex,
        setSelectedApiIndex = setSelectedApiIndex,
        focusedPane = focusedPane,
        setFocusedPane = setFocusedPane
      )

      val chunks = Layout(
        direction = Direction.Vertical,
        margin = Margin(1),
        constraints = Array(Constraint.Length(3), Constraint.Min(10))
      ).split(area)

      val typeLabel = browserState.sourceType match {
        case SpecSourceType.OpenApi    => "OpenAPI"
        case SpecSourceType.JsonSchema => "JSON Schema"
      }

      val title = browserState.level match {
        case SpecBrowserLevel.Models     => s"$typeLabel: ${browserState.sourceName.value}"
        case SpecBrowserLevel.Properties => s"$typeLabel: ${ui.currentModel(browserState.models).map(_.name).getOrElse("Model")}"
        case SpecBrowserLevel.Apis       => s"APIs: ${browserState.sourceName.value}"
        case SpecBrowserLevel.ApiDetails => ui.currentApi(browserState.apis).map(a => s"${a.method} ${a.path}").getOrElse("API Details")
      }

      BackButton(title)(props.callbacks.goBack()).render(ctx, chunks(0))

      val contentArea = chunks(1)

      browserState.level match {
        case SpecBrowserLevel.Models     => renderModelCards(ctx, contentArea, props, ui)
        case SpecBrowserLevel.Properties => renderPropertyView(ctx, contentArea, props, ui)
        case SpecBrowserLevel.Apis       => renderApiCards(ctx, contentArea, props, ui)
        case SpecBrowserLevel.ApiDetails => renderApiDetails(ctx, contentArea, props, ui)
      }
    }
  }

  private def renderModelCards(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val browserState = props.browserState

    val mainChunks = Layout(
      direction = Direction.Horizontal,
      constraints = Array(Constraint.Percentage(50), Constraint.Percentage(50))
    ).split(area)

    // Left: Model list
    val innerX = (mainChunks(0).x + 1).toShort
    val innerY = (mainChunks(0).y + 1).toShort
    val innerWidth = math.max(20, mainChunks(0).width - 2).toInt
    val innerHeight = math.max(5, mainChunks(0).height - 3).toInt
    val maxX = innerX + innerWidth

    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Models", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    ctx.frame.renderWidget(block, mainChunks(0))

    var y = innerY.toInt

    if (browserState.models.isEmpty) {
      safeSetString(ctx.buffer, innerX, y.toShort, maxX, "No models found", Style(fg = Some(Color.DarkGray)))
    } else {
      browserState.models.zipWithIndex.foreach { case (model, idx) =>
        if (y + CardHeight <= innerY + innerHeight - 2) {
          val isSelected = idx == ui.selectedModelIndex
          renderModelCard(ctx, innerX, y.toShort, innerWidth, maxX, model, isSelected, idx, ui)
          y += CardHeight + 1
        }
      }
    }

    // Scroll handlers for model list
    ctx.onScrollUp(mainChunks(0)) {
      val newIdx = math.max(0, ui.selectedModelIndex - 1)
      ui.setSelectedModelIndex(newIdx)
    }

    ctx.onScrollDown(mainChunks(0)) {
      val newIdx = math.min(browserState.models.length - 1, ui.selectedModelIndex + 1)
      ui.setSelectedModelIndex(math.max(0, newIdx))
    }

    // Right: Model preview
    renderModelPreview(ctx, mainChunks(1), props, ui)

    val hintY = (mainChunks(0).y + mainChunks(0).height - 2).toShort
    val apiHint = if (browserState.apis.nonEmpty) " • [a] APIs" else ""
    safeSetString(ctx.buffer, (mainChunks(0).x + 2).toShort, hintY, maxX, s"[Enter] View • [f] Search$apiHint • [Esc] Back", Style(fg = Some(Color.DarkGray)))
  }

  private def renderModelCard(
      ctx: RenderContext,
      x: Short,
      y: Short,
      width: Int,
      maxX: Int,
      model: ModelInfo,
      isSelected: Boolean,
      idx: Int,
      ui: UiState
  ): Unit = {
    val cardWidth = math.min(width - 2, 45)
    val borderColor = if (isSelected) modelTypeColor(model.modelType) else Color.DarkGray
    val bgColor = if (isSelected) Some(Color.Blue) else None

    val topBorder = "╭" + "─" * (cardWidth - 2) + "╮"
    val bottomBorder = "╰" + "─" * (cardWidth - 2) + "╯"
    val emptyLine = "│" + " " * (cardWidth - 2) + "│"

    val borderStyle = Style(fg = Some(borderColor), bg = bgColor)
    safeSetString(ctx.buffer, x, y, maxX, topBorder, borderStyle)
    safeSetString(ctx.buffer, x, (y + 1).toShort, maxX, emptyLine, borderStyle)
    safeSetString(ctx.buffer, x, (y + 2).toShort, maxX, bottomBorder, borderStyle)

    val icon = modelTypeIcon(model.modelType)
    val iconStyle = Style(fg = Some(modelTypeColor(model.modelType)), bg = bgColor)
    val nameStyle = Style(fg = Some(Color.White), bg = bgColor, addModifier = Modifier.BOLD)
    val countStyle = Style(fg = Some(Color.DarkGray), bg = bgColor)

    safeSetString(ctx.buffer, (x + 2).toShort, (y + 1).toShort, maxX, s" $icon ", iconStyle)
    safeSetString(ctx.buffer, (x + 5).toShort, (y + 1).toShort, maxX, model.name.take(cardWidth - 15), nameStyle)
    safeSetString(ctx.buffer, (x + cardWidth - 6).toShort, (y + 1).toShort, maxX, f"${model.propertyCount}%3d", countStyle)

    val cardArea = Rect(x, y, cardWidth.toShort, CardHeight.toShort)
    ctx.onClick(cardArea) {
      Interactive.scheduleStateUpdate { state =>
        state.currentScreen match {
          case browser: AppScreen.SpecBrowser =>
            SpecBrowser.navigateToPropertiesFromReact(state, browser, idx)
          case _ => state
        }
      }
    }
    ctx.onHover(cardArea) {
      ui.setSelectedModelIndex(idx)
    }
  }

  private def renderModelPreview(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val browserState = props.browserState

    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Preview", Style(fg = Some(Color.Cyan))))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    ctx.frame.renderWidget(block, area)

    val innerX = (area.x + 2).toShort
    val innerY = (area.y + 1).toShort
    val innerWidth = math.max(10, area.width - 4).toInt
    val maxX = innerX + innerWidth

    var y = innerY.toInt

    ui.currentModel(browserState.models) match {
      case Some(model) =>
        // Name card
        val cardWidth = math.min(innerWidth, 40)
        safeSetString(ctx.buffer, innerX, y.toShort, maxX, "╭" + "─" * (cardWidth - 2) + "╮", Style(fg = Some(modelTypeColor(model.modelType))))
        y += 1
        safeSetString(ctx.buffer, innerX, y.toShort, maxX, "│" + " " * (cardWidth - 2) + "│", Style(fg = Some(modelTypeColor(model.modelType))))
        safeSetString(ctx.buffer, (innerX + 2).toShort, y.toShort, maxX, model.name.take(cardWidth - 4), Style(fg = Some(Color.White), addModifier = Modifier.BOLD))
        y += 1
        safeSetString(ctx.buffer, innerX, y.toShort, maxX, "╰" + "─" * (cardWidth - 2) + "╯", Style(fg = Some(modelTypeColor(model.modelType))))
        y += 2

        val typeLabel = model.modelType match {
          case ModelType.Object  => "Object"
          case ModelType.Enum    => "Enum"
          case ModelType.Wrapper => "Wrapper"
          case ModelType.Alias   => "Alias"
          case ModelType.SumType => "Sum Type"
        }
        safeSetString(ctx.buffer, innerX, y.toShort, maxX, s"Type: ", Style(fg = Some(Color.Yellow)))
        safeSetString(ctx.buffer, (innerX + 6).toShort, y.toShort, maxX, typeLabel, Style(fg = Some(modelTypeColor(model.modelType))))
        y += 1

        val countLabel = model.modelType match {
          case ModelType.Enum    => "Values"
          case ModelType.SumType => "Members"
          case _                 => "Properties"
        }
        safeSetString(ctx.buffer, innerX, y.toShort, maxX, s"$countLabel: ", Style(fg = Some(Color.Yellow)))
        safeSetString(ctx.buffer, (innerX + countLabel.length + 2).toShort, y.toShort, maxX, model.propertyCount.toString, Style(fg = Some(Color.White)))
        y += 1

        model.description.foreach { desc =>
          y += 1
          safeSetString(ctx.buffer, innerX, y.toShort, maxX, "Description:", Style(fg = Some(Color.Yellow)))
          y += 1
          safeSetString(ctx.buffer, innerX, y.toShort, maxX, desc.take(innerWidth - 2), Style(fg = Some(Color.Gray)))
        }

      case None =>
        safeSetString(ctx.buffer, innerX, y.toShort, maxX, "Select a model", Style(fg = Some(Color.DarkGray)))
    }
  }

  private def renderPropertyView(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val chunks = Layout(
      direction = Direction.Horizontal,
      constraints = Array(Constraint.Percentage(50), Constraint.Percentage(50))
    ).split(area)

    // Left: Property list
    renderPropertyList(ctx, chunks(0), props, ui)

    // Right: Property details
    renderPropertyDetails(ctx, chunks(1), props, ui)
  }

  private def renderPropertyList(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val browserState = props.browserState
    val isFocused = ui.focusedPane == BrowserPane.Left

    val modelName = ui.currentModel(browserState.models).map(_.name).getOrElse("Properties")
    val borderStyle = if (isFocused) Style(fg = Some(Color.Cyan)) else Style(fg = Some(Color.DarkGray))

    val block = BlockWidget(
      title = Some(Spans.from(Span.styled(modelName, Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded,
      borderStyle = borderStyle
    )
    ctx.frame.renderWidget(block, area)

    val innerX = (area.x + 1).toShort
    val innerY = (area.y + 1).toShort
    val innerWidth = math.max(20, area.width - 2).toInt
    val innerHeight = math.max(5, area.height - 3).toInt
    val maxX = innerX + innerWidth

    var y = innerY.toInt

    if (browserState.properties.isEmpty) {
      safeSetString(ctx.buffer, innerX, y.toShort, maxX, "No properties", Style(fg = Some(Color.DarkGray)))
    } else {
      browserState.properties.zipWithIndex.foreach { case (prop, idx) =>
        if (y + 2 <= innerY + innerHeight - 1) {
          val isSelected = idx == ui.selectedPropertyIndex
          renderPropertyRow(ctx, innerX, y.toShort, innerWidth, maxX, prop, isSelected && isFocused, idx, ui)
          y += 2
        }
      }
    }

    ctx.onScrollUp(area) {
      val newIdx = math.max(0, ui.selectedPropertyIndex - 1)
      ui.setSelectedPropertyIndex(newIdx)
    }

    ctx.onScrollDown(area) {
      val newIdx = math.min(browserState.properties.length - 1, ui.selectedPropertyIndex + 1)
      ui.setSelectedPropertyIndex(math.max(0, newIdx))
    }

    val hintY = (area.y + area.height - 2).toShort
    safeSetString(ctx.buffer, (area.x + 2).toShort, hintY, maxX, "[m] Models • [Esc] Back", Style(fg = Some(Color.DarkGray)))
  }

  private def renderPropertyRow(
      ctx: RenderContext,
      x: Short,
      y: Short,
      width: Int,
      maxX: Int,
      prop: PropertyInfo,
      isSelected: Boolean,
      idx: Int,
      ui: UiState
  ): Unit = {
    val bgColor = if (isSelected) Some(Color.Blue) else None

    if (isSelected) {
      safeSetString(ctx.buffer, x, y, maxX, " " * (width - 2), Style(bg = Some(Color.Blue)))
      safeSetString(ctx.buffer, x, (y + 1).toShort, maxX, " " * (width - 2), Style(bg = Some(Color.Blue)))
    }

    val reqIcon = if (prop.required) "!" else "?"
    val typeIcon = prop.matchingType.map(_ => "⚡").getOrElse(" ")

    val iconStyle = Style(fg = Some(Color.Yellow), bg = bgColor)
    val nameStyle = if (isSelected) Style(fg = Some(Color.White), bg = bgColor, addModifier = Modifier.BOLD) else Style(fg = Some(Color.White), bg = bgColor)
    val typeStyle = Style(fg = Some(Color.DarkGray), bg = bgColor)

    safeSetString(ctx.buffer, x, y, maxX, s" $reqIcon$typeIcon ", iconStyle)
    safeSetString(ctx.buffer, (x + 4).toShort, y, maxX, prop.name.take(width - 8), nameStyle)
    safeSetString(ctx.buffer, (x + 4).toShort, (y + 1).toShort, maxX, prop.typeName.take(width - 8), typeStyle)

    val rowArea = Rect(x, y, (width - 2).toShort, 2)
    ctx.onClick(rowArea) {
      ui.setSelectedPropertyIndex(idx)
      ui.setFocusedPane(BrowserPane.Left)
    }
    ctx.onHover(rowArea) {
      ui.setSelectedPropertyIndex(idx)
      ui.setFocusedPane(BrowserPane.Left)
    }
  }

  private def renderPropertyDetails(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val browserState = props.browserState
    val isFocused = ui.focusedPane == BrowserPane.Right

    val borderStyle = if (isFocused) Style(fg = Some(Color.Cyan)) else Style(fg = Some(Color.DarkGray))

    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Details", Style(fg = Some(Color.Cyan))))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded,
      borderStyle = borderStyle
    )
    ctx.frame.renderWidget(block, area)

    val innerX = (area.x + 2).toShort
    val innerY = (area.y + 2).toShort
    val innerWidth = math.max(10, area.width - 4).toInt
    val maxX = innerX + innerWidth

    var y = innerY.toInt

    ui.currentProperty(browserState.properties) match {
      case Some(prop) =>
        // Name card
        val cardWidth = math.min(innerWidth, 40)
        safeSetString(ctx.buffer, innerX, y.toShort, maxX, "╭" + "─" * (cardWidth - 2) + "╮", Style(fg = Some(Color.Cyan)))
        y += 1
        safeSetString(ctx.buffer, innerX, y.toShort, maxX, "│" + " " * (cardWidth - 2) + "│", Style(fg = Some(Color.Cyan)))
        safeSetString(ctx.buffer, (innerX + 2).toShort, y.toShort, maxX, prop.name.take(cardWidth - 4), Style(fg = Some(Color.White), addModifier = Modifier.BOLD))
        y += 1
        safeSetString(ctx.buffer, innerX, y.toShort, maxX, "│" + " " * (cardWidth - 2) + "│", Style(fg = Some(Color.Cyan)))
        safeSetString(ctx.buffer, (innerX + 2).toShort, y.toShort, maxX, prop.typeName.take(cardWidth - 4), Style(fg = Some(Color.Yellow)))
        y += 1
        safeSetString(ctx.buffer, innerX, y.toShort, maxX, "╰" + "─" * (cardWidth - 2) + "╯", Style(fg = Some(Color.Cyan)))
        y += 2

        val reqStr = if (prop.required) "required" else "optional"
        val reqColor = if (prop.required) Color.Green else Color.Gray
        safeSetString(ctx.buffer, innerX, y.toShort, maxX, s"Status: ", Style(fg = Some(Color.Yellow)))
        safeSetString(ctx.buffer, (innerX + 8).toShort, y.toShort, maxX, reqStr, Style(fg = Some(reqColor)))
        y += 1

        prop.format.foreach { format =>
          safeSetString(ctx.buffer, innerX, y.toShort, maxX, s"Format: ", Style(fg = Some(Color.Yellow)))
          safeSetString(ctx.buffer, (innerX + 8).toShort, y.toShort, maxX, format, Style(fg = Some(Color.Cyan)))
          y += 1
        }

        prop.description.foreach { desc =>
          y += 1
          safeSetString(ctx.buffer, innerX, y.toShort, maxX, "Description:", Style(fg = Some(Color.Yellow)))
          y += 1
          safeSetString(ctx.buffer, innerX, y.toShort, maxX, desc.take(innerWidth - 2), Style(fg = Some(Color.Gray)))
          y += 1
        }

        y += 1
        prop.matchingType match {
          case Some(typeName) =>
            safeSetString(ctx.buffer, innerX, y.toShort, maxX, "⚡ Bridge Type:", Style(fg = Some(Color.Yellow)))
            y += 1
            safeSetString(ctx.buffer, innerX, y.toShort, maxX, s"  $typeName", Style(fg = Some(Color.Green)))
          case None =>
            safeSetString(ctx.buffer, innerX, y.toShort, maxX, "No Bridge type matched", Style(fg = Some(Color.DarkGray)))
            y += 1
            safeSetString(ctx.buffer, innerX, y.toShort, maxX, "Press [t] to create one", Style(fg = Some(Color.DarkGray)))
        }

      case None =>
        safeSetString(ctx.buffer, innerX, y.toShort, maxX, "Select a property", Style(fg = Some(Color.DarkGray)))
    }

    val hintY = (area.y + area.height - 2).toShort
    safeSetString(ctx.buffer, (area.x + 2).toShort, hintY, maxX, "[t] Create Type", Style(fg = Some(Color.DarkGray)))
  }

  private def renderApiCards(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val browserState = props.browserState

    val innerX = (area.x + 1).toShort
    val innerY = (area.y + 1).toShort
    val innerWidth = math.max(20, area.width - 2).toInt
    val innerHeight = math.max(5, area.height - 3).toInt
    val maxX = innerX + innerWidth

    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("API Endpoints", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    ctx.frame.renderWidget(block, area)

    var y = innerY.toInt

    if (browserState.apis.isEmpty) {
      safeSetString(ctx.buffer, innerX, y.toShort, maxX, "No APIs found", Style(fg = Some(Color.DarkGray)))
    } else {
      browserState.apis.zipWithIndex.foreach { case (api, idx) =>
        if (y + CardHeight + 1 <= innerY + innerHeight - 2) {
          val isSelected = idx == ui.selectedApiIndex
          renderApiCard(ctx, innerX, y.toShort, innerWidth, maxX, api, isSelected, idx, ui)
          y += CardHeight + 2
        }
      }
    }

    ctx.onScrollUp(area) {
      val newIdx = math.max(0, ui.selectedApiIndex - 1)
      ui.setSelectedApiIndex(newIdx)
    }

    ctx.onScrollDown(area) {
      val newIdx = math.min(browserState.apis.length - 1, ui.selectedApiIndex + 1)
      ui.setSelectedApiIndex(math.max(0, newIdx))
    }

    val hintY = (area.y + area.height - 2).toShort
    safeSetString(ctx.buffer, (area.x + 2).toShort, hintY, maxX, "[Enter] Details • [m] Models • [Esc] Back", Style(fg = Some(Color.DarkGray)))
  }

  private def renderApiCard(
      ctx: RenderContext,
      x: Short,
      y: Short,
      width: Int,
      maxX: Int,
      api: ApiInfo,
      isSelected: Boolean,
      idx: Int,
      ui: UiState
  ): Unit = {
    val cardWidth = math.min(width - 2, 70)
    val methodColor = api.method match {
      case "GET"    => Color.Green
      case "POST"   => Color.Yellow
      case "PUT"    => Color.Blue
      case "DELETE" => Color.Red
      case "PATCH"  => Color.Magenta
      case _        => Color.White
    }
    val borderColor = if (isSelected) methodColor else Color.DarkGray
    val bgColor = if (isSelected) Some(Color.Blue) else None

    val topBorder = "╭" + "─" * (cardWidth - 2) + "╮"
    val bottomBorder = "╰" + "─" * (cardWidth - 2) + "╯"
    val emptyLine = "│" + " " * (cardWidth - 2) + "│"

    val borderStyle = Style(fg = Some(borderColor), bg = bgColor)
    safeSetString(ctx.buffer, x, y, maxX, topBorder, borderStyle)
    safeSetString(ctx.buffer, x, (y + 1).toShort, maxX, emptyLine, borderStyle)
    safeSetString(ctx.buffer, x, (y + 2).toShort, maxX, emptyLine, borderStyle)
    safeSetString(ctx.buffer, x, (y + 3).toShort, maxX, bottomBorder, borderStyle)

    val methodStyle = Style(fg = Some(methodColor), bg = bgColor, addModifier = Modifier.BOLD)
    val pathStyle = Style(fg = Some(Color.White), bg = bgColor, addModifier = Modifier.BOLD)
    val summaryStyle = Style(fg = Some(Color.DarkGray), bg = bgColor)

    safeSetString(ctx.buffer, (x + 2).toShort, (y + 1).toShort, maxX, f"${api.method}%-7s", methodStyle)
    safeSetString(ctx.buffer, (x + 10).toShort, (y + 1).toShort, maxX, api.path.take(cardWidth - 14), pathStyle)
    api.summary.foreach { summary =>
      safeSetString(ctx.buffer, (x + 2).toShort, (y + 2).toShort, maxX, summary.take(cardWidth - 6), summaryStyle)
    }

    val cardArea = Rect(x, y, cardWidth.toShort, 4)
    ctx.onClick(cardArea) {
      Interactive.scheduleStateUpdate { state =>
        state.currentScreen match {
          case browser: AppScreen.SpecBrowser =>
            SpecBrowser.navigateToApiDetailsFromReact(state, browser, idx)
          case _ => state
        }
      }
    }
    ctx.onHover(cardArea) {
      ui.setSelectedApiIndex(idx)
    }
  }

  private def renderApiDetails(ctx: RenderContext, area: Rect, props: Props, ui: UiState): Unit = {
    val browserState = props.browserState

    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("API Details", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    ctx.frame.renderWidget(block, area)

    val innerX = (area.x + 2).toShort
    val innerY = (area.y + 2).toShort
    val innerWidth = math.max(10, area.width - 4).toInt
    val maxX = innerX + innerWidth

    var y = innerY.toInt

    ui.currentApi(browserState.apis) match {
      case Some(api) =>
        val methodColor = api.method match {
          case "GET"    => Color.Green
          case "POST"   => Color.Yellow
          case "PUT"    => Color.Blue
          case "DELETE" => Color.Red
          case "PATCH"  => Color.Magenta
          case _        => Color.White
        }

        // Endpoint card
        val cardWidth = math.min(innerWidth, 60)
        safeSetString(ctx.buffer, innerX, y.toShort, maxX, "╭" + "─" * (cardWidth - 2) + "╮", Style(fg = Some(methodColor)))
        y += 1
        safeSetString(ctx.buffer, innerX, y.toShort, maxX, "│" + " " * (cardWidth - 2) + "│", Style(fg = Some(methodColor)))
        safeSetString(ctx.buffer, (innerX + 2).toShort, y.toShort, maxX, api.method, Style(fg = Some(methodColor), addModifier = Modifier.BOLD))
        safeSetString(
          ctx.buffer,
          (innerX + 2 + api.method.length + 1).toShort,
          y.toShort,
          maxX,
          api.path.take(cardWidth - api.method.length - 6),
          Style(fg = Some(Color.White), addModifier = Modifier.BOLD)
        )
        y += 1
        safeSetString(ctx.buffer, innerX, y.toShort, maxX, "╰" + "─" * (cardWidth - 2) + "╯", Style(fg = Some(methodColor)))
        y += 2

        api.operationId.foreach { opId =>
          safeSetString(ctx.buffer, innerX, y.toShort, maxX, "Operation ID: ", Style(fg = Some(Color.Yellow)))
          safeSetString(ctx.buffer, (innerX + 14).toShort, y.toShort, maxX, opId, Style(fg = Some(Color.Cyan)))
          y += 1
        }

        api.summary.foreach { summary =>
          safeSetString(ctx.buffer, innerX, y.toShort, maxX, "Summary: ", Style(fg = Some(Color.Yellow)))
          safeSetString(ctx.buffer, (innerX + 9).toShort, y.toShort, maxX, summary.take(innerWidth - 11), Style(fg = Some(Color.White)))
          y += 1
        }

        safeSetString(ctx.buffer, innerX, y.toShort, maxX, "Parameters: ", Style(fg = Some(Color.Yellow)))
        safeSetString(ctx.buffer, (innerX + 12).toShort, y.toShort, maxX, api.paramCount.toString, Style(fg = Some(Color.White)))
        y += 1

        if (api.responseTypes.nonEmpty) {
          y += 1
          safeSetString(ctx.buffer, innerX, y.toShort, maxX, "Response Types:", Style(fg = Some(Color.Yellow)))
          y += 1
          api.responseTypes.foreach { rt =>
            safeSetString(ctx.buffer, innerX, y.toShort, maxX, s"  • $rt", Style(fg = Some(Color.Gray)))
            y += 1
          }
        }

      case None =>
        safeSetString(ctx.buffer, innerX, y.toShort, maxX, "Select an API", Style(fg = Some(Color.DarkGray)))
    }

    val hintY = (area.y + area.height - 2).toShort
    safeSetString(ctx.buffer, (area.x + 2).toShort, hintY, maxX, "[Esc] Back to list", Style(fg = Some(Color.DarkGray)))
  }

  private def modelTypeIcon(modelType: ModelType): String = modelType match {
    case ModelType.Object  => "◼"
    case ModelType.Enum    => "◆"
    case ModelType.Wrapper => "◇"
    case ModelType.Alias   => "≡"
    case ModelType.SumType => "⊕"
  }

  private def modelTypeColor(modelType: ModelType): Color = modelType match {
    case ModelType.Object  => Color.Cyan
    case ModelType.Enum    => Color.Yellow
    case ModelType.Wrapper => Color.Green
    case ModelType.Alias   => Color.Magenta
    case ModelType.SumType => Color.Blue
  }

  private def safeSetString(buffer: Buffer, x: Short, y: Short, maxX: Int, text: String, style: Style): Unit = {
    val availableWidth = maxX - x
    if (availableWidth > 0 && x >= 0 && y >= 0 && y < buffer.area.height) {
      buffer.setString(x, y, text.take(availableWidth), style)
    }
  }
}

package typr.cli.tui.screens

import tui.*
import tui.crossterm.KeyCode
import tui.widgets.*
import typr.cli.tui.AppScreen
import typr.cli.tui.BrowserPane
import typr.cli.tui.BrowserSearchState
import typr.cli.tui.ModelInfo
import typr.cli.tui.ModelType
import typr.cli.tui.PropertyInfo
import typr.cli.tui.SpecBrowserLevel
import typr.cli.tui.SpecBrowserState
import typr.cli.tui.SpecSourceType
import typr.cli.tui.SourceName
import typr.cli.tui.TuiState
import typr.cli.tui.TypeWizardState
import typr.cli.tui.components.BackButton
import typr.cli.tui.navigation.Navigator

object SpecBrowser {

  def handleKey(state: TuiState, browser: AppScreen.SpecBrowser, keyCode: KeyCode): TuiState = {
    val browserState = browser.state

    if (browserState.searchState.active) {
      handleSearchKey(state, browser, keyCode)
    } else {
      handleBrowseKey(state, browser, keyCode)
    }
  }

  def tick(state: TuiState, browser: AppScreen.SpecBrowser): TuiState = state

  private def handleSearchKey(state: TuiState, browser: AppScreen.SpecBrowser, keyCode: KeyCode): TuiState = {
    val browserState = browser.state
    val search = browserState.searchState

    keyCode match {
      case _: KeyCode.Esc =>
        val newSearch = search.copy(active = false, query = "", results = Nil, selectedIndex = 0)
        state.copy(currentScreen = browser.copy(state = browserState.copy(searchState = newSearch)))

      case _: KeyCode.Backspace =>
        val newQuery = search.query.dropRight(1)
        val newSearch = search.copy(query = newQuery, selectedIndex = 0)
        state.copy(currentScreen = browser.copy(state = browserState.copy(searchState = newSearch)))

      case c: KeyCode.Char =>
        val newQuery = search.query + c.c()
        val newSearch = search.copy(query = newQuery, selectedIndex = 0)
        state.copy(currentScreen = browser.copy(state = browserState.copy(searchState = newSearch)))

      case _ => state
    }
  }

  private def handleBrowseKey(state: TuiState, browser: AppScreen.SpecBrowser, keyCode: KeyCode): TuiState = {
    val browserState = browser.state

    keyCode match {
      case _: KeyCode.Esc =>
        browserState.level match {
          case SpecBrowserLevel.Models =>
            Navigator.goBack(state)
          case SpecBrowserLevel.Properties =>
            state.copy(currentScreen =
              browser.copy(state =
                browserState.copy(
                  level = SpecBrowserLevel.Models,
                  properties = Nil,
                  selectedPropertyIndex = 0
                )
              )
            )
          case SpecBrowserLevel.Apis =>
            state.copy(currentScreen = browser.copy(state = browserState.copy(level = SpecBrowserLevel.Models)))
          case SpecBrowserLevel.ApiDetails =>
            state.copy(currentScreen = browser.copy(state = browserState.copy(level = SpecBrowserLevel.Apis)))
        }

      case c: KeyCode.Char if c.c() == 'f' =>
        val newSearch = browserState.searchState.copy(active = true)
        state.copy(currentScreen = browser.copy(state = browserState.copy(searchState = newSearch)))

      case c: KeyCode.Char if c.c() == 'q' =>
        Navigator.handleEscape(state)

      case c: KeyCode.Char if c.c() == 'a' && browserState.apis.nonEmpty =>
        state.copy(currentScreen = browser.copy(state = browserState.copy(level = SpecBrowserLevel.Apis)))

      case c: KeyCode.Char if c.c() == 'm' =>
        state.copy(currentScreen = browser.copy(state = browserState.copy(level = SpecBrowserLevel.Models)))

      case _: KeyCode.Up =>
        handleUpNavigation(state, browser)

      case _: KeyCode.Down =>
        handleDownNavigation(state, browser)

      case _: KeyCode.Left =>
        if (browserState.focusedPane == BrowserPane.Right) {
          state.copy(currentScreen = browser.copy(state = browserState.copy(focusedPane = BrowserPane.Left)))
        } else {
          state
        }

      case _: KeyCode.Right =>
        if (browserState.focusedPane == BrowserPane.Left && browserState.level == SpecBrowserLevel.Properties) {
          state.copy(currentScreen = browser.copy(state = browserState.copy(focusedPane = BrowserPane.Right)))
        } else {
          state
        }

      case _: KeyCode.Enter =>
        handleEnterKey(state, browser)

      case c: KeyCode.Char if c.c() == 't' =>
        browserState.currentProperty match {
          case Some(prop) if prop.matchingType.isEmpty =>
            createTypeFromProperty(state, browser, prop)
          case _ =>
            state
        }

      case _ => state
    }
  }

  private def handleUpNavigation(state: TuiState, browser: AppScreen.SpecBrowser): TuiState = {
    val browserState = browser.state
    browserState.level match {
      case SpecBrowserLevel.Models =>
        val newIdx = math.max(0, browserState.selectedModelIndex - 1)
        state.copy(currentScreen = browser.copy(state = browserState.copy(selectedModelIndex = newIdx)))
      case SpecBrowserLevel.Properties =>
        if (browserState.focusedPane == BrowserPane.Left) {
          val newIdx = math.max(0, browserState.selectedPropertyIndex - 1)
          state.copy(currentScreen = browser.copy(state = browserState.copy(selectedPropertyIndex = newIdx)))
        } else {
          val newOffset = math.max(0, browserState.detailsScrollOffset - 1)
          state.copy(currentScreen = browser.copy(state = browserState.copy(detailsScrollOffset = newOffset)))
        }
      case SpecBrowserLevel.Apis =>
        val newIdx = math.max(0, browserState.selectedApiIndex - 1)
        state.copy(currentScreen = browser.copy(state = browserState.copy(selectedApiIndex = newIdx)))
      case SpecBrowserLevel.ApiDetails =>
        val newOffset = math.max(0, browserState.detailsScrollOffset - 1)
        state.copy(currentScreen = browser.copy(state = browserState.copy(detailsScrollOffset = newOffset)))
    }
  }

  private def handleDownNavigation(state: TuiState, browser: AppScreen.SpecBrowser): TuiState = {
    val browserState = browser.state
    browserState.level match {
      case SpecBrowserLevel.Models =>
        val newIdx = math.min(browserState.models.length - 1, browserState.selectedModelIndex + 1)
        state.copy(currentScreen = browser.copy(state = browserState.copy(selectedModelIndex = math.max(0, newIdx))))
      case SpecBrowserLevel.Properties =>
        if (browserState.focusedPane == BrowserPane.Left) {
          val newIdx = math.min(browserState.properties.length - 1, browserState.selectedPropertyIndex + 1)
          state.copy(currentScreen = browser.copy(state = browserState.copy(selectedPropertyIndex = math.max(0, newIdx))))
        } else {
          state.copy(currentScreen = browser.copy(state = browserState.copy(detailsScrollOffset = browserState.detailsScrollOffset + 1)))
        }
      case SpecBrowserLevel.Apis =>
        val newIdx = math.min(browserState.apis.length - 1, browserState.selectedApiIndex + 1)
        state.copy(currentScreen = browser.copy(state = browserState.copy(selectedApiIndex = math.max(0, newIdx))))
      case SpecBrowserLevel.ApiDetails =>
        state.copy(currentScreen = browser.copy(state = browserState.copy(detailsScrollOffset = browserState.detailsScrollOffset + 1)))
    }
  }

  private def handleEnterKey(state: TuiState, browser: AppScreen.SpecBrowser): TuiState = {
    val browserState = browser.state
    browserState.level match {
      case SpecBrowserLevel.Models =>
        browserState.currentModel match {
          case Some(model) =>
            val properties = SpecBrowserState.extractPropertiesForModel(browserState.spec, model.name)
            state.copy(currentScreen =
              browser.copy(state =
                browserState.copy(
                  level = SpecBrowserLevel.Properties,
                  properties = properties,
                  selectedPropertyIndex = 0,
                  focusedPane = BrowserPane.Left
                )
              )
            )
          case None => state
        }

      case SpecBrowserLevel.Properties =>
        if (browserState.focusedPane == BrowserPane.Left) {
          state.copy(currentScreen = browser.copy(state = browserState.copy(focusedPane = BrowserPane.Right)))
        } else {
          state
        }

      case SpecBrowserLevel.Apis =>
        state.copy(currentScreen =
          browser.copy(state =
            browserState.copy(
              level = SpecBrowserLevel.ApiDetails,
              detailsScrollOffset = 0
            )
          )
        )

      case SpecBrowserLevel.ApiDetails =>
        state
    }
  }

  /** Navigate to properties view from React component - for click on model card */
  def navigateToPropertiesFromReact(state: TuiState, browser: AppScreen.SpecBrowser, modelIndex: Int): TuiState = {
    val browserState = browser.state
    val models = browserState.models
    if (modelIndex >= 0 && modelIndex < models.length) {
      val model = models(modelIndex)
      val properties = SpecBrowserState.extractPropertiesForModel(browserState.spec, model.name)
      state.copy(currentScreen =
        browser.copy(state =
          browserState.copy(
            level = SpecBrowserLevel.Properties,
            selectedModelIndex = modelIndex,
            properties = properties,
            selectedPropertyIndex = 0,
            focusedPane = BrowserPane.Left
          )
        )
      )
    } else {
      state
    }
  }

  /** Navigate to API details from React component - for click on API card */
  def navigateToApiDetailsFromReact(state: TuiState, browser: AppScreen.SpecBrowser, apiIndex: Int): TuiState = {
    val browserState = browser.state
    val apis = browserState.apis
    if (apiIndex >= 0 && apiIndex < apis.length) {
      state.copy(currentScreen =
        browser.copy(state =
          browserState.copy(
            level = SpecBrowserLevel.ApiDetails,
            selectedApiIndex = apiIndex,
            detailsScrollOffset = 0
          )
        )
      )
    } else {
      state
    }
  }

  private def createTypeFromProperty(state: TuiState, browser: AppScreen.SpecBrowser, prop: PropertyInfo): TuiState = {
    val wizardState = TypeWizardState
      .initial(Nil)
      .copy(
        name = prop.name.capitalize,
        modelNames = s"*${prop.name.toLowerCase}*"
      )
    state.copy(currentScreen = AppScreen.TypeWizard(wizardState))
  }

  def render(f: Frame, state: TuiState, browser: AppScreen.SpecBrowser): Unit = {
    val browserState = browser.state

    if (browserState.searchState.active) {
      renderSearch(f, state, browserState)
    } else {
      browserState.level match {
        case SpecBrowserLevel.Models =>
          renderModelList(f, state, browserState)
        case SpecBrowserLevel.Properties =>
          renderPropertyView(f, state, browserState)
        case SpecBrowserLevel.Apis =>
          renderApiList(f, state, browserState)
        case SpecBrowserLevel.ApiDetails =>
          renderApiDetails(f, state, browserState)
      }
    }
  }

  private def renderSearch(f: Frame, state: TuiState, browserState: SpecBrowserState): Unit = {
    val block = BlockWidget(
      title = Some(Spans.from(Span.styled(s"Search: ${browserState.sourceName.value}", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    f.renderWidget(block, f.size)

    val innerArea = Rect(
      x = f.size.x + 2,
      y = f.size.y + 1,
      width = f.size.width - 4,
      height = f.size.height - 3
    )

    val buf = f.buffer
    var y = innerArea.y

    buf.setSpans(
      innerArea.x,
      y,
      Spans.from(
        Span.styled("Find: ", Style(fg = Some(Color.Yellow))),
        Span.styled(browserState.searchState.query + "█", Style(fg = Some(Color.White), bg = Some(Color.Blue)))
      ),
      innerArea.width.toInt
    )
    y += 2

    val query = browserState.searchState.query.toLowerCase
    if (query.nonEmpty) {
      val matchingModels = browserState.models.filter(_.name.toLowerCase.contains(query)).take(10)
      matchingModels.foreach { model =>
        if (y < innerArea.y + innerArea.height.toInt - 2) {
          val icon = modelTypeIcon(model.modelType)
          buf.setSpans(
            innerArea.x,
            y,
            Spans.from(
              Span.styled(s"$icon ", Style(fg = Some(Color.Cyan))),
              Span.styled(model.name, Style(fg = Some(Color.White)))
            ),
            innerArea.width.toInt
          )
          y += 1
        }
      }
    }

    val hintY = f.size.y + f.size.height.toInt - 2
    buf.setString(innerArea.x, hintY, "[Esc] Cancel", Style(fg = Some(Color.DarkGray)))
  }

  private def renderModelList(f: Frame, state: TuiState, browserState: SpecBrowserState): Unit = {
    val chunks = Layout(
      direction = Direction.Horizontal,
      margin = Margin(1),
      constraints = Array(Constraint.Percentage(50), Constraint.Percentage(50))
    ).split(f.size)

    renderModelListPane(f, chunks(0), browserState)
    renderModelPreview(f, chunks(1), browserState)
  }

  private def renderModelListPane(f: Frame, area: Rect, browserState: SpecBrowserState): Unit = {
    val typeLabel = browserState.sourceType match {
      case SpecSourceType.OpenApi    => "OpenAPI"
      case SpecSourceType.JsonSchema => "JSON Schema"
    }
    val block = BlockWidget(
      title = Some(Spans.from(Span.styled(s"Models ($typeLabel)", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    f.renderWidget(block, area)

    val innerArea = Rect(
      x = area.x + 2,
      y = area.y + 1,
      width = area.width - 4,
      height = area.height - 2
    )

    val buf = f.buffer
    var y = innerArea.y

    if (browserState.models.isEmpty) {
      buf.setString(innerArea.x, y, "No models found", Style(fg = Some(Color.DarkGray)))
    } else {
      browserState.models.zipWithIndex.foreach { case (model, idx) =>
        if (y < innerArea.y + innerArea.height.toInt) {
          val isSelected = idx == browserState.selectedModelIndex
          val prefix = if (isSelected) "> " else "  "
          val icon = modelTypeIcon(model.modelType)
          val typeColor = modelTypeColor(model.modelType)

          val style =
            if (isSelected) Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
            else Style(fg = Some(Color.White))

          buf.setString(innerArea.x, y, prefix, style)
          buf.setString(innerArea.x + 2, y, icon + " ", Style(fg = Some(typeColor)))
          buf.setString(innerArea.x + 4, y, model.name.take(innerArea.width.toInt - 6), style)
          y += 1
        }
      }
    }
  }

  private def renderModelPreview(f: Frame, area: Rect, browserState: SpecBrowserState): Unit = {
    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Preview", Style(fg = Some(Color.Cyan))))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    f.renderWidget(block, area)

    val innerArea = Rect(
      x = area.x + 2,
      y = area.y + 1,
      width = area.width - 4,
      height = area.height - 3
    )

    val buf = f.buffer
    var y = innerArea.y

    browserState.currentModel match {
      case Some(model) =>
        buf.setSpans(
          innerArea.x,
          y,
          Spans.from(
            Span.styled("Name: ", Style(fg = Some(Color.Yellow))),
            Span.styled(model.name, Style(fg = Some(Color.White), addModifier = Modifier.BOLD))
          ),
          innerArea.width.toInt
        )
        y += 1

        val typeLabel = model.modelType match {
          case ModelType.Object  => "Object"
          case ModelType.Enum    => "Enum"
          case ModelType.Wrapper => "Wrapper"
          case ModelType.Alias   => "Alias"
          case ModelType.SumType => "Sum Type"
        }
        buf.setSpans(
          innerArea.x,
          y,
          Spans.from(
            Span.styled("Type: ", Style(fg = Some(Color.Yellow))),
            Span.styled(typeLabel, Style(fg = Some(modelTypeColor(model.modelType))))
          ),
          innerArea.width.toInt
        )
        y += 1

        val countLabel = model.modelType match {
          case ModelType.Enum    => "Values"
          case ModelType.SumType => "Members"
          case _                 => "Properties"
        }
        buf.setSpans(
          innerArea.x,
          y,
          Spans.from(
            Span.styled(s"$countLabel: ", Style(fg = Some(Color.Yellow))),
            Span.styled(model.propertyCount.toString, Style(fg = Some(Color.White)))
          ),
          innerArea.width.toInt
        )
        y += 1

        model.description.foreach { desc =>
          y += 1
          buf.setString(innerArea.x, y, "Description:", Style(fg = Some(Color.Yellow)))
          y += 1
          buf.setString(innerArea.x + 2, y, desc.take(innerArea.width.toInt - 4), Style(fg = Some(Color.Gray)))
        }

      case None =>
        buf.setString(innerArea.x, y, "Select a model", Style(fg = Some(Color.DarkGray)))
    }

    val hintY = area.y + area.height.toInt - 2
    val apiHint = if (browserState.apis.nonEmpty) "  [a] APIs" else ""
    buf.setString(area.x + 2, hintY, s"[Enter] View  [f] Search$apiHint  [Esc] Back", Style(fg = Some(Color.DarkGray)))
  }

  private def renderPropertyView(f: Frame, state: TuiState, browserState: SpecBrowserState): Unit = {
    val chunks = Layout(
      direction = Direction.Horizontal,
      margin = Margin(1),
      constraints = Array(Constraint.Percentage(50), Constraint.Percentage(50))
    ).split(f.size)

    renderPropertyListPane(f, chunks(0), browserState)
    renderPropertyDetailsPane(f, chunks(1), browserState)
  }

  private def renderPropertyListPane(f: Frame, area: Rect, browserState: SpecBrowserState): Unit = {
    val modelName = browserState.currentModel.map(_.name).getOrElse("Properties")
    val isFocused = browserState.focusedPane == BrowserPane.Left
    val borderStyle = if (isFocused) Style(fg = Some(Color.Cyan)) else Style(fg = Some(Color.DarkGray))

    val block = BlockWidget(
      title = Some(Spans.from(Span.styled(modelName, Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded,
      borderStyle = borderStyle
    )
    f.renderWidget(block, area)

    val innerArea = Rect(
      x = area.x + 2,
      y = area.y + 1,
      width = area.width - 4,
      height = area.height - 2
    )

    val buf = f.buffer
    var y = innerArea.y

    if (browserState.properties.isEmpty) {
      buf.setString(innerArea.x, y, "No properties", Style(fg = Some(Color.DarkGray)))
    } else {
      browserState.properties.zipWithIndex.foreach { case (prop, idx) =>
        if (y < innerArea.y + innerArea.height.toInt) {
          val isSelected = idx == browserState.selectedPropertyIndex
          val reqIcon = if (prop.required) "!" else "?"
          val typeIcon = prop.matchingType.map(_ => "⚡").getOrElse(" ")
          val prefix = if (isSelected && isFocused) "> " else "  "

          val style =
            if (isSelected && isFocused)
              Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
            else if (isSelected)
              Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)
            else
              Style(fg = Some(Color.White))

          buf.setSpans(
            innerArea.x,
            y,
            Spans.from(
              Span.styled(prefix, style),
              Span.styled(s"$reqIcon$typeIcon ", Style(fg = Some(Color.Yellow))),
              Span.styled(prop.name, style),
              Span.styled(s" ${prop.typeName}", Style(fg = Some(Color.DarkGray)))
            ),
            innerArea.width.toInt
          )
          y += 1
        }
      }
    }
  }

  private def renderPropertyDetailsPane(f: Frame, area: Rect, browserState: SpecBrowserState): Unit = {
    val isFocused = browserState.focusedPane == BrowserPane.Right
    val borderStyle = if (isFocused) Style(fg = Some(Color.Cyan)) else Style(fg = Some(Color.DarkGray))

    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Details", Style(fg = Some(Color.Cyan))))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded,
      borderStyle = borderStyle
    )
    f.renderWidget(block, area)

    val innerArea = Rect(
      x = area.x + 2,
      y = area.y + 1,
      width = area.width - 4,
      height = area.height - 3
    )

    val buf = f.buffer
    var y = innerArea.y

    browserState.currentProperty match {
      case Some(prop) =>
        buf.setSpans(
          innerArea.x,
          y,
          Spans.from(
            Span.styled("Property: ", Style(fg = Some(Color.Yellow))),
            Span.styled(prop.name, Style(fg = Some(Color.White), addModifier = Modifier.BOLD))
          ),
          innerArea.width.toInt
        )
        y += 1

        buf.setSpans(
          innerArea.x,
          y,
          Spans.from(
            Span.styled("Type: ", Style(fg = Some(Color.Yellow))),
            Span.styled(prop.typeName, Style(fg = Some(Color.White)))
          ),
          innerArea.width.toInt
        )
        y += 1

        buf.setSpans(
          innerArea.x,
          y,
          Spans.from(
            Span.styled("Required: ", Style(fg = Some(Color.Yellow))),
            Span.styled(if (prop.required) "yes" else "no", Style(fg = Some(if (prop.required) Color.Green else Color.Gray)))
          ),
          innerArea.width.toInt
        )
        y += 1

        prop.format.foreach { format =>
          buf.setSpans(
            innerArea.x,
            y,
            Spans.from(
              Span.styled("Format: ", Style(fg = Some(Color.Yellow))),
              Span.styled(format, Style(fg = Some(Color.Cyan)))
            ),
            innerArea.width.toInt
          )
          y += 1
        }

        prop.description.foreach { desc =>
          y += 1
          buf.setString(innerArea.x, y, "Description:", Style(fg = Some(Color.Yellow)))
          y += 1
          buf.setString(innerArea.x + 2, y, desc.take(innerArea.width.toInt - 4), Style(fg = Some(Color.Gray)))
          y += 1
        }

        y += 1
        prop.matchingType match {
          case Some(typeName) =>
            buf.setSpans(
              innerArea.x,
              y,
              Spans.from(
                Span.styled("⚡ Bridge Type: ", Style(fg = Some(Color.Yellow))),
                Span.styled(typeName, Style(fg = Some(Color.Green)))
              ),
              innerArea.width.toInt
            )
          case None =>
            buf.setString(innerArea.x, y, "[t] Create Bridge Type", Style(fg = Some(Color.DarkGray)))
        }

      case None =>
        buf.setString(innerArea.x, y, "Select a property", Style(fg = Some(Color.DarkGray)))
    }

    val hintY = area.y + area.height.toInt - 2
    buf.setString(area.x + 2, hintY, "[Enter] Edit  [t] Create Type  [m] Models  [Esc] Back", Style(fg = Some(Color.DarkGray)))
  }

  private def renderApiList(f: Frame, state: TuiState, browserState: SpecBrowserState): Unit = {
    val block = BlockWidget(
      title = Some(Spans.from(Span.styled(s"APIs: ${browserState.sourceName.value}", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    f.renderWidget(block, f.size)

    val innerArea = Rect(
      x = f.size.x + 2,
      y = f.size.y + 1,
      width = f.size.width - 4,
      height = f.size.height - 3
    )

    val buf = f.buffer
    var y = innerArea.y

    if (browserState.apis.isEmpty) {
      buf.setString(innerArea.x, y, "No APIs found", Style(fg = Some(Color.DarkGray)))
    } else {
      browserState.apis.zipWithIndex.foreach { case (api, idx) =>
        if (y < innerArea.y + innerArea.height.toInt - 2) {
          val isSelected = idx == browserState.selectedApiIndex
          val prefix = if (isSelected) "> " else "  "

          val methodColor = api.method match {
            case "GET"    => Color.Green
            case "POST"   => Color.Yellow
            case "PUT"    => Color.Blue
            case "DELETE" => Color.Red
            case "PATCH"  => Color.Magenta
            case _        => Color.White
          }

          val style =
            if (isSelected) Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
            else Style(fg = Some(Color.White))

          buf.setSpans(
            innerArea.x,
            y,
            Spans.from(
              Span.styled(prefix, style),
              Span.styled(f"${api.method}%-7s", Style(fg = Some(methodColor), addModifier = Modifier.BOLD)),
              Span.styled(api.path, style)
            ),
            innerArea.width.toInt
          )
          y += 1

          api.summary.foreach { summary =>
            if (y < innerArea.y + innerArea.height.toInt - 2) {
              val summaryStyle = if (isSelected) Style(fg = Some(Color.Gray), bg = Some(Color.Blue)) else Style(fg = Some(Color.Gray))
              buf.setString(innerArea.x + 11, y, summary.take(innerArea.width.toInt - 13), summaryStyle)
              y += 1
            }
          }
        }
      }
    }

    val hintY = f.size.y + f.size.height.toInt - 2
    buf.setString(innerArea.x, hintY, "[Enter] Details  [m] Models  [Esc] Back", Style(fg = Some(Color.DarkGray)))
  }

  private def renderApiDetails(f: Frame, state: TuiState, browserState: SpecBrowserState): Unit = {
    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("API Details", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    f.renderWidget(block, f.size)

    val innerArea = Rect(
      x = f.size.x + 2,
      y = f.size.y + 1,
      width = f.size.width - 4,
      height = f.size.height - 3
    )

    val buf = f.buffer
    var y = innerArea.y

    browserState.currentApi match {
      case Some(api) =>
        val methodColor = api.method match {
          case "GET"    => Color.Green
          case "POST"   => Color.Yellow
          case "PUT"    => Color.Blue
          case "DELETE" => Color.Red
          case "PATCH"  => Color.Magenta
          case _        => Color.White
        }

        buf.setSpans(
          innerArea.x,
          y,
          Spans.from(
            Span.styled(api.method + " ", Style(fg = Some(methodColor), addModifier = Modifier.BOLD)),
            Span.styled(api.path, Style(fg = Some(Color.White), addModifier = Modifier.BOLD))
          ),
          innerArea.width.toInt
        )
        y += 2

        api.operationId.foreach { opId =>
          buf.setSpans(
            innerArea.x,
            y,
            Spans.from(
              Span.styled("Operation ID: ", Style(fg = Some(Color.Yellow))),
              Span.styled(opId, Style(fg = Some(Color.Cyan)))
            ),
            innerArea.width.toInt
          )
          y += 1
        }

        api.summary.foreach { summary =>
          buf.setSpans(
            innerArea.x,
            y,
            Spans.from(
              Span.styled("Summary: ", Style(fg = Some(Color.Yellow))),
              Span.styled(summary, Style(fg = Some(Color.White)))
            ),
            innerArea.width.toInt
          )
          y += 1
        }

        buf.setSpans(
          innerArea.x,
          y,
          Spans.from(
            Span.styled("Parameters: ", Style(fg = Some(Color.Yellow))),
            Span.styled(api.paramCount.toString, Style(fg = Some(Color.White)))
          ),
          innerArea.width.toInt
        )
        y += 1

        if (api.responseTypes.nonEmpty) {
          buf.setString(innerArea.x, y, "Response Types:", Style(fg = Some(Color.Yellow)))
          y += 1
          api.responseTypes.foreach { rt =>
            buf.setString(innerArea.x + 2, y, s"- $rt", Style(fg = Some(Color.Gray)))
            y += 1
          }
        }

      case None =>
        buf.setString(innerArea.x, y, "Select an API", Style(fg = Some(Color.DarkGray)))
    }

    val hintY = f.size.y + f.size.height.toInt - 2
    buf.setString(innerArea.x, hintY, "[Esc] Back to list", Style(fg = Some(Color.DarkGray)))
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
}

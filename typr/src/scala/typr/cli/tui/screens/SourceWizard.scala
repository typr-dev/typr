package typr.cli.tui.screens

import tui.*
import tui.crossterm.KeyCode
import tui.widgets.*
import typr.cli.tui.*
import typr.cli.tui.components.*
import typr.cli.tui.navigation.Navigator
import typr.cli.tui.util.SourceScanner

import java.nio.file.Paths

object SourceWizard {
  def handleKey(state: TuiState, wizard: AppScreen.SourceWizard, keyCode: KeyCode): TuiState = {
    val ws = wizard.state

    keyCode match {
      case _: KeyCode.Esc =>
        if (ws.step == SourceWizardStep.SelectType) {
          Navigator.goBack(state)
        } else {
          val newWs = ws.copy(step = ws.previousStep, error = None)
          state.copy(currentScreen = wizard.copy(state = newWs))
        }

      case _: KeyCode.Enter =>
        ws.step match {
          case SourceWizardStep.SelectType =>
            val selected = ws.typeSelect.selectedValue
            val isSpec = selected == "openapi" || selected == "jsonschema"
            if (isSpec) {
              val baseDir = Paths.get(".").toAbsolutePath.normalize()
              val discovered = SourceScanner.scan(baseDir).filter { d =>
                (selected == "openapi" && d.sourceType == SourceScanner.DiscoveredSourceType.OpenApi) ||
                (selected == "jsonschema" && d.sourceType == SourceScanner.DiscoveredSourceType.JsonSchema)
              }
              val newWs = ws.copy(
                step = SourceWizardStep.ScanSpecs,
                sourceType = Some(selected),
                discoveredSpecs = discovered,
                selectedDiscoveredIndex = 0,
                scanning = false
              )
              state.copy(currentScreen = wizard.copy(state = newWs))
            } else {
              val newWs = ws.copy(
                step = ws.nextStep,
                sourceType = Some(selected),
                port = ws.copy(sourceType = Some(selected)).defaultPort,
                path = if (selected == "duckdb") ":memory:" else ""
              )
              state.copy(currentScreen = wizard.copy(state = newWs))
            }

          case SourceWizardStep.ScanSpecs =>
            if (ws.discoveredSpecs.isEmpty) {
              val newWs = ws.copy(step = SourceWizardStep.EnterName)
              state.copy(currentScreen = wizard.copy(state = newWs))
            } else {
              val selected = ws.discoveredSpecs(ws.selectedDiscoveredIndex)
              val suggestedName = SourceScanner.suggestSourceName(selected.path)
              val newWs = ws.copy(
                step = SourceWizardStep.EnterName,
                name = suggestedName,
                path = selected.relativePath
              )
              state.copy(currentScreen = wizard.copy(state = newWs))
            }

          case SourceWizardStep.EnterName =>
            if (ws.name.trim.isEmpty) {
              state.copy(currentScreen = wizard.copy(state = ws.copy(error = Some("Name is required"))))
            } else if (state.config.sources.exists(_.contains(ws.name.trim))) {
              state.copy(currentScreen = wizard.copy(state = ws.copy(error = Some("Source name already exists"))))
            } else {
              val newWs = ws.copy(step = ws.nextStep, error = None)
              state.copy(currentScreen = wizard.copy(state = newWs))
            }

          case SourceWizardStep.EnterHost =>
            val newWs = ws.copy(step = ws.nextStep, error = None)
            state.copy(currentScreen = wizard.copy(state = newWs))

          case SourceWizardStep.EnterPort =>
            if (ws.port.nonEmpty && ws.port.toIntOption.isEmpty) {
              state.copy(currentScreen = wizard.copy(state = ws.copy(error = Some("Port must be a number"))))
            } else {
              val newWs = ws.copy(step = ws.nextStep, error = None)
              state.copy(currentScreen = wizard.copy(state = newWs))
            }

          case SourceWizardStep.EnterDatabase =>
            val newWs = ws.copy(step = ws.nextStep, error = None)
            state.copy(currentScreen = wizard.copy(state = newWs))

          case SourceWizardStep.EnterService =>
            val newWs = ws.copy(step = ws.nextStep, error = None)
            state.copy(currentScreen = wizard.copy(state = newWs))

          case SourceWizardStep.EnterUsername =>
            val newWs = ws.copy(step = ws.nextStep, error = None)
            state.copy(currentScreen = wizard.copy(state = newWs))

          case SourceWizardStep.EnterPassword =>
            val newWs = ws.copy(step = ws.nextStep, error = None)
            state.copy(currentScreen = wizard.copy(state = newWs))

          case SourceWizardStep.EnterPath =>
            if (ws.path.trim.isEmpty) {
              state.copy(currentScreen = wizard.copy(state = ws.copy(error = Some("Path is required"))))
            } else {
              val newWs = ws.copy(step = ws.nextStep, error = None)
              state.copy(currentScreen = wizard.copy(state = newWs))
            }

          case SourceWizardStep.EnterSchemaSql =>
            val newWs = ws.copy(step = ws.nextStep, error = None)
            state.copy(currentScreen = wizard.copy(state = newWs))

          case SourceWizardStep.TestConnection =>
            val newWs = ws.copy(step = ws.nextStep, error = None)
            state.copy(currentScreen = wizard.copy(state = newWs))

          case SourceWizardStep.Review =>
            val sourceJson = ws.toJson
            val newSources = state.config.sources.getOrElse(Map.empty) + (ws.name.trim -> sourceJson)
            val newConfig = state.config.copy(sources = Some(newSources))
            val updatedState = state.copy(
              config = newConfig,
              hasUnsavedChanges = true,
              currentScreen = AppScreen.SourceList(selectedIndex = 0, testResults = Map.empty),
              statusMessage = Some((s"Added source: ${ws.name}", Color.Green))
            )
            Navigator.goBack(updatedState)
        }

      case _: KeyCode.Up if ws.step == SourceWizardStep.SelectType =>
        val newSelect = ws.typeSelect.handleKey(keyCode)
        state.copy(currentScreen = wizard.copy(state = ws.copy(typeSelect = newSelect)))

      case _: KeyCode.Down if ws.step == SourceWizardStep.SelectType =>
        val newSelect = ws.typeSelect.handleKey(keyCode)
        state.copy(currentScreen = wizard.copy(state = ws.copy(typeSelect = newSelect)))

      case _: KeyCode.Up if ws.step == SourceWizardStep.ScanSpecs =>
        if (ws.discoveredSpecs.nonEmpty) {
          val newIndex = if (ws.selectedDiscoveredIndex > 0) ws.selectedDiscoveredIndex - 1 else ws.discoveredSpecs.length - 1
          state.copy(currentScreen = wizard.copy(state = ws.copy(selectedDiscoveredIndex = newIndex)))
        } else state

      case _: KeyCode.Down if ws.step == SourceWizardStep.ScanSpecs =>
        if (ws.discoveredSpecs.nonEmpty) {
          val newIndex = (ws.selectedDiscoveredIndex + 1) % ws.discoveredSpecs.length
          state.copy(currentScreen = wizard.copy(state = ws.copy(selectedDiscoveredIndex = newIndex)))
        } else state

      case c: KeyCode.Char =>
        ws.step match {
          case SourceWizardStep.SelectType =>
            val newSelect = ws.typeSelect.handleKey(keyCode)
            state.copy(currentScreen = wizard.copy(state = ws.copy(typeSelect = newSelect)))
          case SourceWizardStep.EnterName =>
            state.copy(currentScreen = wizard.copy(state = ws.copy(name = ws.name + c.c(), error = None)))
          case SourceWizardStep.EnterHost =>
            state.copy(currentScreen = wizard.copy(state = ws.copy(host = ws.host + c.c())))
          case SourceWizardStep.EnterPort =>
            state.copy(currentScreen = wizard.copy(state = ws.copy(port = ws.port + c.c(), error = None)))
          case SourceWizardStep.EnterDatabase =>
            state.copy(currentScreen = wizard.copy(state = ws.copy(database = ws.database + c.c())))
          case SourceWizardStep.EnterService =>
            state.copy(currentScreen = wizard.copy(state = ws.copy(service = ws.service + c.c())))
          case SourceWizardStep.EnterUsername =>
            state.copy(currentScreen = wizard.copy(state = ws.copy(username = ws.username + c.c())))
          case SourceWizardStep.EnterPassword =>
            state.copy(currentScreen = wizard.copy(state = ws.copy(password = ws.password + c.c())))
          case SourceWizardStep.EnterPath =>
            state.copy(currentScreen = wizard.copy(state = ws.copy(path = ws.path + c.c(), error = None)))
          case SourceWizardStep.EnterSchemaSql =>
            state.copy(currentScreen = wizard.copy(state = ws.copy(schemaSql = ws.schemaSql + c.c())))
          case _ => state
        }

      case _: KeyCode.Backspace =>
        ws.step match {
          case SourceWizardStep.EnterName =>
            state.copy(currentScreen = wizard.copy(state = ws.copy(name = ws.name.dropRight(1))))
          case SourceWizardStep.EnterHost =>
            state.copy(currentScreen = wizard.copy(state = ws.copy(host = ws.host.dropRight(1))))
          case SourceWizardStep.EnterPort =>
            state.copy(currentScreen = wizard.copy(state = ws.copy(port = ws.port.dropRight(1))))
          case SourceWizardStep.EnterDatabase =>
            state.copy(currentScreen = wizard.copy(state = ws.copy(database = ws.database.dropRight(1))))
          case SourceWizardStep.EnterService =>
            state.copy(currentScreen = wizard.copy(state = ws.copy(service = ws.service.dropRight(1))))
          case SourceWizardStep.EnterUsername =>
            state.copy(currentScreen = wizard.copy(state = ws.copy(username = ws.username.dropRight(1))))
          case SourceWizardStep.EnterPassword =>
            state.copy(currentScreen = wizard.copy(state = ws.copy(password = ws.password.dropRight(1))))
          case SourceWizardStep.EnterPath =>
            state.copy(currentScreen = wizard.copy(state = ws.copy(path = ws.path.dropRight(1))))
          case SourceWizardStep.EnterSchemaSql =>
            state.copy(currentScreen = wizard.copy(state = ws.copy(schemaSql = ws.schemaSql.dropRight(1))))
          case _ => state
        }

      case _ => state
    }
  }

  def render(f: Frame, state: TuiState, wizard: AppScreen.SourceWizard): Unit = {
    val ws = wizard.state

    val chunks = Layout(
      direction = Direction.Vertical,
      margin = Margin(1),
      constraints = Array(Constraint.Length(3), Constraint.Min(10), Constraint.Length(3))
    ).split(f.size)

    val stepNum = stepNumber(ws.step, ws.sourceType)
    val totalSteps = if (ws.sourceType.contains("duckdb")) 5 else if (ws.isSpecSource) 5 else 9
    val title = s"Add Source (Step $stepNum/$totalSteps) - ${stepTitle(ws.step, ws.sourceType)}"

    val isBackHovered = state.hoverPosition.exists { case (col, row) => BackButton.isClicked(col, row, 1) }
    BackButton.renderClickable(f, chunks(0), title, isBackHovered)

    renderStep(f, chunks(1), ws)
    renderFooter(f, chunks(2), ws)
  }

  private def renderStep(f: Frame, area: Rect, ws: SourceWizardState): Unit = {
    val innerArea = area.inner(Margin(1))

    ws.step match {
      case SourceWizardStep.SelectType =>
        val select = ws.typeSelect.copy(isFocused = true)
        select.render(innerArea, f.buffer)

      case SourceWizardStep.ScanSpecs =>
        renderScanSpecs(f, innerArea, ws)

      case SourceWizardStep.EnterName =>
        renderTextInput(f, innerArea, "Source Name:", ws.name, "e.g., my-postgres", ws.error)

      case SourceWizardStep.EnterHost =>
        renderTextInput(f, innerArea, "Host:", ws.host, "e.g., localhost", None)

      case SourceWizardStep.EnterPort =>
        renderTextInput(f, innerArea, s"Port (default: ${ws.defaultPort}):", ws.port, ws.defaultPort, ws.error)

      case SourceWizardStep.EnterDatabase =>
        renderTextInput(f, innerArea, "Database:", ws.database, "e.g., mydb", None)

      case SourceWizardStep.EnterService =>
        renderTextInput(f, innerArea, "Service Name:", ws.service, "e.g., FREEPDB1", None)

      case SourceWizardStep.EnterUsername =>
        renderTextInput(f, innerArea, "Username:", ws.username, "e.g., postgres", None)

      case SourceWizardStep.EnterPassword =>
        renderPasswordInput(f, innerArea, "Password:", ws.password)

      case SourceWizardStep.EnterPath =>
        val (label, placeholder) = if (ws.isSpecSource) {
          val specType = if (ws.sourceType.contains("openapi")) "OpenAPI" else "JSON Schema"
          (s"$specType Spec Path:", "e.g., api.yaml or schemas/user.json")
        } else {
          ("Database Path:", ":memory:")
        }
        renderTextInput(f, innerArea, label, ws.path, placeholder, ws.error)

      case SourceWizardStep.EnterSchemaSql =>
        renderTextInput(f, innerArea, "Schema SQL File (optional):", ws.schemaSql, "e.g., schema.sql", None)

      case SourceWizardStep.TestConnection =>
        renderConnectionTest(f, innerArea, ws)

      case SourceWizardStep.Review =>
        renderReview(f, innerArea, ws)
    }
  }

  private def renderTextInput(f: Frame, area: Rect, label: String, value: String, placeholder: String, error: Option[String]): Unit = {
    val labelStyle = Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)
    val inputStyle = Style(fg = Some(Color.White), bg = Some(Color.DarkGray))
    val errorStyle = Style(fg = Some(Color.Red))

    f.buffer.setString(area.x, area.y, label, labelStyle)

    val inputY = area.y + 1
    val inputWidth = math.min(area.width - 2, 40).toInt

    for (i <- 0 until inputWidth) {
      f.buffer.get(area.x + i, inputY).setSymbol(" ").setStyle(inputStyle)
    }

    val displayValue = if (value.isEmpty) placeholder else value
    val displayStyle = if (value.isEmpty) Style(fg = Some(Color.DarkGray), bg = Some(Color.DarkGray)) else inputStyle
    f.buffer.setString(area.x, inputY, displayValue.take(inputWidth), displayStyle)

    error.foreach { err =>
      f.buffer.setString(area.x, area.y + 3, err, errorStyle)
    }

    val cursorX = math.min(value.length, inputWidth - 1)
    f.setCursor(area.x + cursorX, inputY)
  }

  private def renderPasswordInput(f: Frame, area: Rect, label: String, value: String): Unit = {
    val labelStyle = Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)
    val inputStyle = Style(fg = Some(Color.White), bg = Some(Color.DarkGray))

    f.buffer.setString(area.x, area.y, label, labelStyle)

    val inputY = area.y + 1
    val inputWidth = math.min(area.width - 2, 40).toInt

    for (i <- 0 until inputWidth) {
      f.buffer.get(area.x + i, inputY).setSymbol(" ").setStyle(inputStyle)
    }

    val maskedValue = "*" * value.length
    f.buffer.setString(area.x, inputY, maskedValue.take(inputWidth), inputStyle)

    val cursorX = math.min(value.length, inputWidth - 1)
    f.setCursor(area.x + cursorX, inputY)
  }

  private def renderConnectionTest(f: Frame, area: Rect, ws: SourceWizardState): Unit = {
    val lines = List(
      Spans.from(Span.styled("Connection Test", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))),
      Spans.nostyle(""),
      Spans.from(Span.nostyle("Press Enter to skip and continue to review.")),
      Spans.nostyle(""),
      Spans.from(Span.nostyle("(Connection testing available via 't' in source list)"))
    )

    val text = Text.fromSpans(lines*)
    lines.zipWithIndex.foreach { case (spans, idx) =>
      f.buffer.setSpans(area.x, area.y + idx, spans, area.width)
    }
  }

  private def renderReview(f: Frame, area: Rect, ws: SourceWizardState): Unit = {
    val lines = ws.sourceType match {
      case Some("duckdb") =>
        List(
          Spans.from(Span.styled("Review Configuration", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))),
          Spans.nostyle(""),
          Spans.from(Span.styled("Name: ", Style(fg = Some(Color.Cyan))), Span.nostyle(ws.name)),
          Spans.from(Span.styled("Type: ", Style(fg = Some(Color.Cyan))), Span.nostyle("DuckDB")),
          Spans.from(Span.styled("Path: ", Style(fg = Some(Color.Cyan))), Span.nostyle(ws.path)),
          Spans.from(Span.styled("Schema SQL: ", Style(fg = Some(Color.Cyan))), Span.nostyle(if (ws.schemaSql.isEmpty) "(none)" else ws.schemaSql)),
          Spans.nostyle(""),
          Spans.from(Span.styled("Press Enter to save, Esc to go back", Style(fg = Some(Color.Green))))
        )
      case Some("openapi") =>
        List(
          Spans.from(Span.styled("Review Configuration", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))),
          Spans.nostyle(""),
          Spans.from(Span.styled("Name: ", Style(fg = Some(Color.Cyan))), Span.nostyle(ws.name)),
          Spans.from(Span.styled("Type: ", Style(fg = Some(Color.Cyan))), Span.nostyle("OpenAPI / Swagger")),
          Spans.from(Span.styled("Spec: ", Style(fg = Some(Color.Cyan))), Span.nostyle(ws.path)),
          Spans.nostyle(""),
          Spans.from(Span.styled("Press Enter to save, Esc to go back", Style(fg = Some(Color.Green))))
        )
      case Some("jsonschema") =>
        List(
          Spans.from(Span.styled("Review Configuration", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))),
          Spans.nostyle(""),
          Spans.from(Span.styled("Name: ", Style(fg = Some(Color.Cyan))), Span.nostyle(ws.name)),
          Spans.from(Span.styled("Type: ", Style(fg = Some(Color.Cyan))), Span.nostyle("JSON Schema")),
          Spans.from(Span.styled("Spec: ", Style(fg = Some(Color.Cyan))), Span.nostyle(ws.path)),
          Spans.nostyle(""),
          Spans.from(Span.styled("Press Enter to save, Esc to go back", Style(fg = Some(Color.Green))))
        )
      case Some(dbType) =>
        List(
          Spans.from(Span.styled("Review Configuration", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))),
          Spans.nostyle(""),
          Spans.from(Span.styled("Name: ", Style(fg = Some(Color.Cyan))), Span.nostyle(ws.name)),
          Spans.from(Span.styled("Type: ", Style(fg = Some(Color.Cyan))), Span.nostyle(dbType)),
          Spans.from(Span.styled("Host: ", Style(fg = Some(Color.Cyan))), Span.nostyle(ws.host)),
          Spans.from(Span.styled("Port: ", Style(fg = Some(Color.Cyan))), Span.nostyle(ws.port)),
          Spans.from(Span.styled("Database: ", Style(fg = Some(Color.Cyan))), Span.nostyle(ws.database)),
          Spans.from(Span.styled("Username: ", Style(fg = Some(Color.Cyan))), Span.nostyle(ws.username)),
          Spans.from(Span.styled("Password: ", Style(fg = Some(Color.Cyan))), Span.nostyle("*".repeat(ws.password.length))),
          Spans.nostyle(""),
          Spans.from(Span.styled("Press Enter to save, Esc to go back", Style(fg = Some(Color.Green))))
        )
      case _ =>
        List(Spans.nostyle("Unknown source type"))
    }

    lines.zipWithIndex.foreach { case (spans, idx) =>
      f.buffer.setSpans(area.x, area.y + idx, spans, area.width)
    }
  }

  private def renderFooter(f: Frame, area: Rect, ws: SourceWizardState): Unit = {
    val hint = ws.step match {
      case SourceWizardStep.SelectType => "[Up/Down] Select  [Enter] Next  [Esc] Cancel"
      case SourceWizardStep.ScanSpecs  => "[Up/Down] Select  [Enter] Use Selected  [Esc] Back"
      case SourceWizardStep.Review     => "[Enter] Save  [Esc] Back"
      case _                           => "[Enter] Next  [Esc] Back"
    }

    val para = ParagraphWidget(text = Text.nostyle(hint))
    f.renderWidget(para, area)
  }

  private def renderScanSpecs(f: Frame, area: Rect, ws: SourceWizardState): Unit = {
    val specType = if (ws.sourceType.contains("openapi")) "OpenAPI" else "JSON Schema"
    val buf = f.buffer

    val titleStyle = Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)
    buf.setString(area.x, area.y, s"Discovered $specType files:", titleStyle)

    if (ws.discoveredSpecs.isEmpty) {
      val msgStyle = Style(fg = Some(Color.Yellow))
      buf.setString(area.x, area.y + 2, "No spec files found in current directory.", msgStyle)
      buf.setString(area.x, area.y + 3, "Press Enter to enter path manually.", Style(fg = Some(Color.DarkGray)))
    } else {
      val visibleHeight = math.max(1, area.height.toInt - 4)
      val startIdx = math.max(0, ws.selectedDiscoveredIndex - visibleHeight / 2)
      val endIdx = math.min(ws.discoveredSpecs.length, startIdx + visibleHeight)

      ws.discoveredSpecs.slice(startIdx, endIdx).zipWithIndex.foreach { case (spec, idx) =>
        val actualIdx = startIdx + idx
        val isSelected = actualIdx == ws.selectedDiscoveredIndex
        val y = area.y + 2 + idx

        val prefix = if (isSelected) "> " else "  "
        val icon = spec.sourceType match {
          case SourceScanner.DiscoveredSourceType.OpenApi    => "◆"
          case SourceScanner.DiscoveredSourceType.JsonSchema => "◇"
          case _                                             => "○"
        }

        val lineStyle = if (isSelected) {
          Style(fg = Some(Color.White), bg = Some(Color.DarkGray), addModifier = Modifier.BOLD)
        } else {
          Style(fg = Some(Color.White))
        }

        val title = spec.title.map(t => s" ($t)").getOrElse("")
        val displayPath = spec.relativePath.take(50) + (if (spec.relativePath.length > 50) "..." else "")
        val line = s"$prefix$icon $displayPath$title"
        buf.setString(area.x, y, line.take(area.width.toInt), lineStyle)
      }

      val infoY = area.y + 2 + visibleHeight + 1
      if (infoY < area.y + area.height.toInt) {
        val info = s"${ws.discoveredSpecs.length} files found (${ws.selectedDiscoveredIndex + 1}/${ws.discoveredSpecs.length})"
        buf.setString(area.x, infoY, info, Style(fg = Some(Color.DarkGray)))
      }
    }
  }

  private def stepNumber(step: SourceWizardStep, sourceType: Option[String]): Int = {
    val isDuckDb = sourceType.contains("duckdb")
    val isSpec = sourceType.exists(t => t == "openapi" || t == "jsonschema")
    step match {
      case SourceWizardStep.SelectType     => 1
      case SourceWizardStep.ScanSpecs      => 2
      case SourceWizardStep.EnterName      => if (isSpec) 3 else 2
      case SourceWizardStep.EnterHost      => 3
      case SourceWizardStep.EnterPath      => if (isSpec) 4 else 3
      case SourceWizardStep.EnterPort      => 4
      case SourceWizardStep.EnterSchemaSql => 4
      case SourceWizardStep.EnterDatabase  => 5
      case SourceWizardStep.EnterService   => 6
      case SourceWizardStep.EnterUsername  => if (sourceType.contains("oracle")) 7 else 6
      case SourceWizardStep.EnterPassword  => if (sourceType.contains("oracle")) 8 else 7
      case SourceWizardStep.TestConnection => if (sourceType.contains("oracle")) 9 else 8
      case SourceWizardStep.Review         => if (isSpec) 5 else if (isDuckDb) 5 else 9
    }
  }

  private def stepTitle(step: SourceWizardStep, sourceType: Option[String]): String = {
    val isSpec = sourceType.exists(t => t == "openapi" || t == "jsonschema")
    step match {
      case SourceWizardStep.SelectType     => "Source Type"
      case SourceWizardStep.ScanSpecs      => "Select Spec File"
      case SourceWizardStep.EnterName      => "Source Name"
      case SourceWizardStep.EnterHost      => "Host"
      case SourceWizardStep.EnterPort      => "Port"
      case SourceWizardStep.EnterDatabase  => "Database"
      case SourceWizardStep.EnterService   => "Service Name"
      case SourceWizardStep.EnterUsername  => "Username"
      case SourceWizardStep.EnterPassword  => "Password"
      case SourceWizardStep.EnterPath      => if (isSpec) "Spec Path" else "Database Path"
      case SourceWizardStep.EnterSchemaSql => "Schema SQL"
      case SourceWizardStep.TestConnection => "Test Connection"
      case SourceWizardStep.Review         => "Review"
    }
  }
}

package typr.cli.tui.screens

import tui.*
import tui.react.*
import tui.react.Elements.*
import tui.widgets.*
import typr.cli.tui.AppScreen
import typr.cli.tui.GlobalCallbacks
import typr.cli.tui.GlobalState
import typr.cli.tui.SourceWizardState
import typr.cli.tui.SourceWizardStep
import typr.cli.tui.util.SourceScanner

import java.nio.file.Paths

/** React-based SourceWizard with form components */
object SourceWizardReact {

  case class Props(
      globalState: GlobalState,
      callbacks: GlobalCallbacks,
      wizardState: SourceWizardState
  )

  def component(props: Props): Component = {
    Component.named("SourceWizardReact") { (ctx, area) =>
      val ws = props.wizardState
      val hoverPos = props.globalState.hoverPosition
      val callbacks = props.callbacks

      val chunks = Layout(
        direction = Direction.Vertical,
        margin = Margin(1),
        constraints = Array(
          Constraint.Length(3),
          Constraint.Min(10),
          Constraint.Length(3)
        )
      ).split(area)

      // Header with title (no back button - navigation is at bottom)
      val stepNum = stepNumber(ws.step, ws.sourceType)
      val totalSteps = if (ws.sourceType.contains("duckdb")) 5 else if (ws.isSpecSource) 5 else 9
      val title = s"Add Source (Step $stepNum/$totalSteps) - ${stepTitle(ws.step, ws.sourceType)}"

      // Simple title header without back button
      val headerBlock = BlockWidget(
        title = Some(Spans.from(Span.styled(title, Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
        borders = Borders.ALL,
        borderType = BlockWidget.BorderType.Rounded
      )
      ctx.frame.renderWidget(headerBlock, chunks(0))

      // Main content area
      val contentArea = chunks(1)
      val block = BlockWidget(
        title = Some(Spans.from(Span.styled("Configuration", Style(fg = Some(Color.Cyan))))),
        borders = Borders.ALL,
        borderType = BlockWidget.BorderType.Rounded
      )
      ctx.frame.renderWidget(block, contentArea)

      val innerArea = Rect(
        x = (contentArea.x + 2).toShort,
        y = (contentArea.y + 1).toShort,
        width = (contentArea.width - 4).toShort,
        height = (contentArea.height - 2).toShort
      )

      // Render step content
      ws.step match {
        case SourceWizardStep.SelectType =>
          renderSelectType(ctx, innerArea, ws, props.callbacks, hoverPos)

        case SourceWizardStep.ScanSpecs =>
          renderScanSpecs(ctx, innerArea, ws, props.callbacks, hoverPos)

        case SourceWizardStep.EnterName =>
          renderTextInputStep(ctx, innerArea, "Source Name", ws.name, "e.g., my-postgres", ws.error, hoverPos)

        case SourceWizardStep.EnterHost =>
          renderTextInputStep(ctx, innerArea, "Host", ws.host, "e.g., localhost", None, hoverPos)

        case SourceWizardStep.EnterPort =>
          renderTextInputStep(ctx, innerArea, s"Port (default: ${ws.defaultPort})", ws.port, ws.defaultPort, ws.error, hoverPos)

        case SourceWizardStep.EnterDatabase =>
          renderTextInputStep(ctx, innerArea, "Database", ws.database, "e.g., mydb", None, hoverPos)

        case SourceWizardStep.EnterService =>
          renderTextInputStep(ctx, innerArea, "Service Name", ws.service, "e.g., FREEPDB1", None, hoverPos)

        case SourceWizardStep.EnterUsername =>
          renderTextInputStep(ctx, innerArea, "Username", ws.username, "e.g., postgres", None, hoverPos)

        case SourceWizardStep.EnterPassword =>
          renderPasswordInputStep(ctx, innerArea, "Password", ws.password, hoverPos)

        case SourceWizardStep.EnterPath =>
          val (label, placeholder) = if (ws.isSpecSource) {
            val specType = if (ws.sourceType.contains("openapi")) "OpenAPI" else "JSON Schema"
            (s"$specType Spec Path", "e.g., api.yaml or schemas/user.json")
          } else {
            ("Database Path", ":memory:")
          }
          renderTextInputStep(ctx, innerArea, label, ws.path, placeholder, ws.error, hoverPos)

        case SourceWizardStep.EnterSchemaSql =>
          renderTextInputStep(ctx, innerArea, "Schema SQL (optional)", ws.schemaSql, "e.g., schema.sql", None, hoverPos)

        case SourceWizardStep.TestConnection =>
          renderConnectionTest(ctx, innerArea, ws)

        case SourceWizardStep.Review =>
          renderReview(ctx, innerArea, ws)
      }

      // Bottom navigation bar with Back/Continue buttons
      renderNavigationBar(ctx, chunks(2), ws, callbacks, hoverPos)
    }
  }

  private def renderNavigationBar(
      ctx: RenderContext,
      area: Rect,
      ws: SourceWizardState,
      callbacks: GlobalCallbacks,
      hoverPos: Option[(Int, Int)]
  ): Unit = {
    val y = (area.y + 1).toShort
    val isFirstStep = ws.step == SourceWizardStep.SelectType

    // Back button (Cancel on first step, Back on others)
    val backLabel = if (isFirstStep) "Cancel" else "Back"
    val backWidth = (backLabel.length + 4).toShort

    FormButtonField(
      label = "",
      buttonLabel = backLabel,
      isPrimary = false,
      isFocused = false,
      labelWidth = 0,
      onFocus = () => (),
      onPress = () => {
        if (isFirstStep) {
          callbacks.goBack()
        } else {
          callbacks.updateSourceWizard(s => s.copy(step = s.previousStep, error = None))
        }
      },
      hoverPosition = hoverPos
    ).render(ctx, Rect(area.x, y, backWidth, 1))

    // Continue/Save button (not shown on SelectType - clicking items advances)
    val showContinue = ws.step != SourceWizardStep.SelectType
    if (showContinue) {
      val isReview = ws.step == SourceWizardStep.Review
      val continueLabel = if (isReview) "Save Source" else "Continue"
      val continueWidth = (continueLabel.length + 4).toShort
      val continueX = (area.x + backWidth + 2).toShort

      FormButtonField(
        label = "",
        buttonLabel = continueLabel,
        isPrimary = true,
        isFocused = true,
        labelWidth = 0,
        onFocus = () => (),
        onPress = () => {
          if (isReview) {
            callbacks.saveSourceWizard()
          } else if (ws.step == SourceWizardStep.ScanSpecs && ws.discoveredSpecs.nonEmpty) {
            // For ScanSpecs, use selected spec to populate name and path
            val selected = ws.discoveredSpecs(ws.selectedDiscoveredIndex)
            val suggestedName = SourceScanner.suggestSourceName(selected.path)
            callbacks.updateSourceWizard(
              _.copy(
                step = SourceWizardStep.EnterName,
                name = suggestedName,
                path = selected.relativePath,
                error = None
              )
            )
          } else {
            callbacks.updateSourceWizard(s => s.copy(step = s.nextStep, error = None))
          }
        },
        hoverPosition = hoverPos
      ).render(ctx, Rect(continueX, y, continueWidth, 1))
    }
  }

  private def renderSelectType(
      ctx: RenderContext,
      area: Rect,
      ws: SourceWizardState,
      callbacks: GlobalCallbacks,
      hoverPos: Option[(Int, Int)]
  ): Unit = {
    var y = area.y.toInt

    ctx.buffer.setString(
      area.x,
      y.toShort,
      "Select database type:",
      Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)
    )
    y += 2

    // Database type options as a clickable list
    val typeOptions = List(
      ("postgres", "PostgreSQL", "Full support including domains, enums, arrays, JSON, UUID"),
      ("mariadb", "MariaDB/MySQL", "Including unsigned types and MySQL-specific features"),
      ("oracle", "Oracle", "Including OBJECT and MULTISET types"),
      ("sqlserver", "SQL Server", "T-SQL specific features"),
      ("db2", "IBM DB2", "Enterprise database"),
      ("duckdb", "DuckDB", "Embedded analytical database"),
      ("openapi", "OpenAPI/Swagger", "REST API specification files"),
      ("jsonschema", "JSON Schema", "JSON Schema definition files")
    )

    typeOptions.foreach { case (value, label, description) =>
      val isSelected = ws.typeSelect.selectedValue == value
      val rowY = y.toShort

      val isHovered = hoverPos.exists { case (hx, hy) =>
        hy == rowY && hx >= area.x && hx < area.x + area.width
      }

      val icon = if (isSelected) "●" else "○"
      val prefix = if (isSelected) "> " else "  "

      val style =
        if (isSelected) Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
        else if (isHovered) Style(fg = Some(Color.White), bg = Some(Color.DarkGray))
        else Style(fg = Some(Color.White))

      val descStyle =
        if (isSelected) Style(fg = Some(Color.Cyan), bg = Some(Color.Blue))
        else if (isHovered) Style(fg = Some(Color.Gray), bg = Some(Color.DarkGray))
        else Style(fg = Some(Color.DarkGray))

      // Clear the row first
      ctx.buffer.setString(area.x, rowY, " " * area.width.toInt, style)

      // Render the option
      ctx.buffer.setString(area.x, rowY, s"$prefix$icon $label", style)
      ctx.buffer.setString((area.x + 20).toShort, rowY, s" - $description".take(area.width.toInt - 22), descStyle)

      // Click handler - select and advance to next step
      val rowArea = Rect(area.x, rowY, area.width, 1)
      ctx.onClick(rowArea) {
        callbacks.updateSourceWizard { s =>
          val newSelect = s.typeSelect.copy(selectedIndex = s.typeSelect.options.indexWhere(_._1 == value))
          val isSpec = value == "openapi" || value == "jsonschema"
          if (isSpec) {
            val baseDir = Paths.get(".").toAbsolutePath.normalize()
            val discovered = SourceScanner.scan(baseDir).filter { d =>
              (value == "openapi" && d.sourceType == SourceScanner.DiscoveredSourceType.OpenApi) ||
              (value == "jsonschema" && d.sourceType == SourceScanner.DiscoveredSourceType.JsonSchema)
            }
            s.copy(
              typeSelect = newSelect,
              step = SourceWizardStep.ScanSpecs,
              sourceType = Some(value),
              discoveredSpecs = discovered,
              selectedDiscoveredIndex = 0,
              scanning = false
            )
          } else {
            s.copy(
              typeSelect = newSelect,
              step = s.copy(sourceType = Some(value)).nextStep,
              sourceType = Some(value),
              port = s.copy(sourceType = Some(value)).defaultPort,
              path = if (value == "duckdb") ":memory:" else ""
            )
          }
        }
      }

      y += 1
    }
  }

  private def renderScanSpecs(
      ctx: RenderContext,
      area: Rect,
      ws: SourceWizardState,
      callbacks: GlobalCallbacks,
      hoverPos: Option[(Int, Int)]
  ): Unit = {
    val specType = if (ws.sourceType.contains("openapi")) "OpenAPI" else "JSON Schema"

    ctx.buffer.setString(
      area.x,
      area.y,
      s"Discovered $specType files:",
      Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)
    )

    if (ws.discoveredSpecs.isEmpty) {
      ctx.buffer.setString(
        area.x,
        (area.y + 2).toShort,
        "No spec files found in current directory.",
        Style(fg = Some(Color.Yellow))
      )
      ctx.buffer.setString(
        area.x,
        (area.y + 3).toShort,
        "Click Continue to enter path manually.",
        Style(fg = Some(Color.DarkGray))
      )
    } else {
      val visibleHeight = math.max(1, area.height.toInt - 4)
      val startIdx = math.max(0, ws.selectedDiscoveredIndex - visibleHeight / 2)
      val endIdx = math.min(ws.discoveredSpecs.length, startIdx + visibleHeight)

      ws.discoveredSpecs.slice(startIdx, endIdx).zipWithIndex.foreach { case (spec, idx) =>
        val actualIdx = startIdx + idx
        val isSelected = actualIdx == ws.selectedDiscoveredIndex
        val rowY = (area.y + 2 + idx).toShort

        val isHovered = hoverPos.exists { case (hx, hy) =>
          hy == rowY && hx >= area.x && hx < area.x + area.width
        }

        val prefix = if (isSelected) "> " else "  "
        val icon = spec.sourceType match {
          case SourceScanner.DiscoveredSourceType.OpenApi    => "◆"
          case SourceScanner.DiscoveredSourceType.JsonSchema => "◇"
          case _                                             => "○"
        }

        val lineStyle =
          if (isSelected) Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
          else if (isHovered) Style(fg = Some(Color.White), bg = Some(Color.DarkGray))
          else Style(fg = Some(Color.White))

        val title = spec.title.map(t => s" ($t)").getOrElse("")
        val displayPath = spec.relativePath.take(50) + (if (spec.relativePath.length > 50) "..." else "")
        val line = s"$prefix$icon $displayPath$title"

        // Clear and render
        ctx.buffer.setString(area.x, rowY, " " * area.width.toInt, lineStyle)
        ctx.buffer.setString(area.x, rowY, line.take(area.width.toInt), lineStyle)

        // Click handler - select and advance
        val rowArea = Rect(area.x, rowY, area.width, 1)
        ctx.onClick(rowArea) {
          val selected = ws.discoveredSpecs(actualIdx)
          val suggestedName = SourceScanner.suggestSourceName(selected.path)
          callbacks.updateSourceWizard(
            _.copy(
              selectedDiscoveredIndex = actualIdx,
              step = SourceWizardStep.EnterName,
              name = suggestedName,
              path = selected.relativePath
            )
          )
        }
      }

      val infoY = (area.y + 2 + visibleHeight).toShort
      if (infoY < area.y + area.height) {
        val info =
          s"${ws.discoveredSpecs.length} files found (${ws.selectedDiscoveredIndex + 1}/${ws.discoveredSpecs.length})"
        ctx.buffer.setString(area.x, infoY, info, Style(fg = Some(Color.DarkGray)))
      }
    }
  }

  private def renderTextInputStep(
      ctx: RenderContext,
      area: Rect,
      label: String,
      value: String,
      placeholder: String,
      error: Option[String],
      hoverPos: Option[(Int, Int)]
  ): Unit = {
    var y = area.y.toInt

    ctx.buffer.setString(
      area.x,
      y.toShort,
      s"$label:",
      Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)
    )
    y += 2

    FormTextField(
      label = "",
      value = value,
      isFocused = true,
      labelWidth = 0,
      onFocus = () => (),
      hoverPosition = hoverPos
    ).render(ctx, Rect(area.x, y.toShort, math.min(area.width, 50).toShort, 1))
    y += 1

    // Show placeholder hint if value is empty
    if (value.isEmpty) {
      ctx.buffer.setString(
        area.x,
        y.toShort,
        s"Hint: $placeholder",
        Style(fg = Some(Color.DarkGray))
      )
    }
    y += 1

    // Show error if present
    error.foreach { err =>
      ctx.buffer.setString(area.x, y.toShort, err, Style(fg = Some(Color.Red)))
    }
  }

  private def renderPasswordInputStep(
      ctx: RenderContext,
      area: Rect,
      label: String,
      value: String,
      hoverPos: Option[(Int, Int)]
  ): Unit = {
    var y = area.y.toInt

    ctx.buffer.setString(
      area.x,
      y.toShort,
      s"$label:",
      Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)
    )
    y += 2

    // Show masked password
    FormTextField(
      label = "",
      value = "*" * value.length,
      isFocused = true,
      labelWidth = 0,
      onFocus = () => (),
      hoverPosition = hoverPos
    ).render(ctx, Rect(area.x, y.toShort, math.min(area.width, 50).toShort, 1))
  }

  private def renderConnectionTest(
      ctx: RenderContext,
      area: Rect,
      ws: SourceWizardState
  ): Unit = {
    var y = area.y.toInt

    ctx.buffer.setString(
      area.x,
      y.toShort,
      "Connection Test",
      Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD)
    )
    y += 2

    ctx.buffer.setString(
      area.x,
      y.toShort,
      "Click Continue to skip and proceed to review.",
      Style(fg = Some(Color.White))
    )
    y += 2

    ctx.buffer.setString(
      area.x,
      y.toShort,
      "(Connection testing available via 't' in source list)",
      Style(fg = Some(Color.DarkGray))
    )
  }

  private def renderReview(
      ctx: RenderContext,
      area: Rect,
      ws: SourceWizardState
  ): Unit = {
    var y = area.y.toInt

    ctx.buffer.setString(
      area.x,
      y.toShort,
      "Review Configuration",
      Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD)
    )
    y += 2

    val labelWidth = 14
    val labelStyle = Style(fg = Some(Color.Cyan))
    val valueStyle = Style(fg = Some(Color.White))

    def renderField(label: String, value: String): Unit = {
      ctx.buffer.setString(area.x, y.toShort, s"$label: ".padTo(labelWidth, ' '), labelStyle)
      ctx.buffer.setString((area.x + labelWidth).toShort, y.toShort, value, valueStyle)
      y += 1
    }

    renderField("Name", ws.name)
    renderField("Type", ws.sourceType.getOrElse("unknown"))

    ws.sourceType match {
      case Some("duckdb") =>
        renderField("Path", ws.path)
        if (ws.schemaSql.nonEmpty) renderField("Schema SQL", ws.schemaSql)

      case Some("openapi") | Some("jsonschema") =>
        renderField("Spec", ws.path)

      case Some(_) =>
        renderField("Host", ws.host)
        renderField("Port", ws.port)
        renderField("Database", ws.database)
        renderField("Username", ws.username)
        renderField("Password", "*" * ws.password.length)

      case None => ()
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

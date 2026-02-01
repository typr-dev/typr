package typr.cli.tui.screens

import tui.*
import tui.crossterm.KeyCode
import tui.widgets.*
import typr.cli.tui.*
import typr.cli.tui.components.*
import typr.cli.tui.navigation.Navigator

object OutputWizard {
  def handleKey(state: TuiState, wizard: AppScreen.OutputWizard, keyCode: KeyCode): TuiState = {
    val ws = wizard.state

    keyCode match {
      case _: KeyCode.Esc =>
        if (ws.step == OutputWizardStep.EnterName) {
          Navigator.goBack(state)
        } else {
          val newWs = ws.copy(step = ws.previousStep, error = None)
          state.copy(currentScreen = wizard.copy(state = newWs))
        }

      case _: KeyCode.Enter =>
        ws.step match {
          case OutputWizardStep.EnterName =>
            if (ws.name.trim.isEmpty) {
              state.copy(currentScreen = wizard.copy(state = ws.copy(error = Some("Name is required"))))
            } else if (state.config.outputs.exists(_.contains(ws.name.trim))) {
              state.copy(currentScreen = wizard.copy(state = ws.copy(error = Some("Output name already exists"))))
            } else {
              val newWs = ws.copy(step = ws.nextStep, error = None)
              state.copy(currentScreen = wizard.copy(state = newWs))
            }

          case OutputWizardStep.SelectSources =>
            val newWs = ws.copy(step = ws.nextStep, error = None)
            state.copy(currentScreen = wizard.copy(state = newWs))

          case OutputWizardStep.EnterPath =>
            if (ws.path.trim.isEmpty) {
              state.copy(currentScreen = wizard.copy(state = ws.copy(error = Some("Path is required"))))
            } else {
              val newWs = ws.copy(step = ws.nextStep, error = None)
              state.copy(currentScreen = wizard.copy(state = newWs))
            }

          case OutputWizardStep.EnterPackage =>
            if (ws.pkg.trim.isEmpty) {
              state.copy(currentScreen = wizard.copy(state = ws.copy(error = Some("Package is required"))))
            } else {
              val newWs = ws.copy(step = ws.nextStep, error = None)
              state.copy(currentScreen = wizard.copy(state = newWs))
            }

          case OutputWizardStep.SelectLanguage =>
            val selected = ws.languageSelect.selectedValue
            val newWs = ws.copy(step = ws.nextStep, language = Some(selected), error = None)
            state.copy(currentScreen = wizard.copy(state = newWs))

          case OutputWizardStep.SelectDbLib =>
            val selected = ws.dbLibSelect.selectedValue
            val newWs = ws.copy(step = ws.nextStep, dbLib = Some(selected), error = None)
            state.copy(currentScreen = wizard.copy(state = newWs))

          case OutputWizardStep.SelectJsonLib =>
            val selected = ws.jsonLibSelect.selectedValue
            val newWs = ws.copy(step = ws.nextStep, jsonLib = Some(selected), error = None)
            state.copy(currentScreen = wizard.copy(state = newWs))

          case OutputWizardStep.ScalaDialect =>
            val selected = ws.dialectSelect.selectedValue
            val newWs = ws.copy(step = ws.nextStep, scalaDialect = Some(selected), error = None)
            state.copy(currentScreen = wizard.copy(state = newWs))

          case OutputWizardStep.ScalaDsl =>
            val selected = ws.dslSelect.selectedValue
            val newWs = ws.copy(step = ws.nextStep, scalaDsl = Some(selected), error = None)
            state.copy(currentScreen = wizard.copy(state = newWs))

          case OutputWizardStep.Review =>
            val output = ws.toOutput
            val newOutputs = state.config.outputs.getOrElse(Map.empty) + (ws.name.trim -> output)
            val newConfig = state.config.copy(outputs = Some(newOutputs))
            val updatedState = state.copy(
              config = newConfig,
              hasUnsavedChanges = true,
              currentScreen = AppScreen.OutputList(selectedIndex = 0),
              statusMessage = Some((s"Added output: ${ws.name}", Color.Green))
            )
            Navigator.goBack(updatedState)
        }

      case _: KeyCode.Up =>
        ws.step match {
          case OutputWizardStep.SelectLanguage =>
            val newSelect = ws.languageSelect.handleKey(keyCode)
            state.copy(currentScreen = wizard.copy(state = ws.copy(languageSelect = newSelect)))
          case OutputWizardStep.SelectDbLib =>
            val newSelect = ws.dbLibSelect.handleKey(keyCode)
            state.copy(currentScreen = wizard.copy(state = ws.copy(dbLibSelect = newSelect)))
          case OutputWizardStep.SelectJsonLib =>
            val newSelect = ws.jsonLibSelect.handleKey(keyCode)
            state.copy(currentScreen = wizard.copy(state = ws.copy(jsonLibSelect = newSelect)))
          case OutputWizardStep.ScalaDialect =>
            val newSelect = ws.dialectSelect.handleKey(keyCode)
            state.copy(currentScreen = wizard.copy(state = ws.copy(dialectSelect = newSelect)))
          case OutputWizardStep.ScalaDsl =>
            val newSelect = ws.dslSelect.handleKey(keyCode)
            state.copy(currentScreen = wizard.copy(state = ws.copy(dslSelect = newSelect)))
          case _ => state
        }

      case _: KeyCode.Down =>
        ws.step match {
          case OutputWizardStep.SelectLanguage =>
            val newSelect = ws.languageSelect.handleKey(keyCode)
            state.copy(currentScreen = wizard.copy(state = ws.copy(languageSelect = newSelect)))
          case OutputWizardStep.SelectDbLib =>
            val newSelect = ws.dbLibSelect.handleKey(keyCode)
            state.copy(currentScreen = wizard.copy(state = ws.copy(dbLibSelect = newSelect)))
          case OutputWizardStep.SelectJsonLib =>
            val newSelect = ws.jsonLibSelect.handleKey(keyCode)
            state.copy(currentScreen = wizard.copy(state = ws.copy(jsonLibSelect = newSelect)))
          case OutputWizardStep.ScalaDialect =>
            val newSelect = ws.dialectSelect.handleKey(keyCode)
            state.copy(currentScreen = wizard.copy(state = ws.copy(dialectSelect = newSelect)))
          case OutputWizardStep.ScalaDsl =>
            val newSelect = ws.dslSelect.handleKey(keyCode)
            state.copy(currentScreen = wizard.copy(state = ws.copy(dslSelect = newSelect)))
          case _ => state
        }

      case c: KeyCode.Char =>
        ws.step match {
          case OutputWizardStep.EnterName =>
            state.copy(currentScreen = wizard.copy(state = ws.copy(name = ws.name + c.c(), error = None)))
          case OutputWizardStep.SelectSources =>
            state.copy(currentScreen = wizard.copy(state = ws.copy(sources = ws.sources + c.c())))
          case OutputWizardStep.EnterPath =>
            state.copy(currentScreen = wizard.copy(state = ws.copy(path = ws.path + c.c(), error = None)))
          case OutputWizardStep.EnterPackage =>
            state.copy(currentScreen = wizard.copy(state = ws.copy(pkg = ws.pkg + c.c(), error = None)))
          case _ => state
        }

      case _: KeyCode.Backspace =>
        ws.step match {
          case OutputWizardStep.EnterName =>
            state.copy(currentScreen = wizard.copy(state = ws.copy(name = ws.name.dropRight(1))))
          case OutputWizardStep.SelectSources =>
            state.copy(currentScreen = wizard.copy(state = ws.copy(sources = ws.sources.dropRight(1))))
          case OutputWizardStep.EnterPath =>
            state.copy(currentScreen = wizard.copy(state = ws.copy(path = ws.path.dropRight(1))))
          case OutputWizardStep.EnterPackage =>
            state.copy(currentScreen = wizard.copy(state = ws.copy(pkg = ws.pkg.dropRight(1))))
          case _ => state
        }

      case _ => state
    }
  }

  def render(f: Frame, state: TuiState, wizard: AppScreen.OutputWizard): Unit = {
    val ws = wizard.state

    val chunks = Layout(
      direction = Direction.Vertical,
      margin = Margin(1),
      constraints = Array(Constraint.Length(3), Constraint.Min(10), Constraint.Length(3))
    ).split(f.size)

    renderHeader(f, chunks(0), ws)
    renderStep(f, chunks(1), ws)
    renderFooter(f, chunks(2), ws)
  }

  private def renderHeader(f: Frame, area: Rect, ws: OutputWizardState): Unit = {
    val stepNum = stepNumber(ws.step, ws.language)
    val totalSteps = if (ws.language.contains("scala")) 9 else 7
    val title = s"Add Output (Step $stepNum/$totalSteps) - ${stepTitle(ws.step)}"

    val para = ParagraphWidget(
      block = Some(
        BlockWidget(
          title = Some(Spans.from(Span.styled("Output Wizard", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
          borders = Borders.ALL,
          borderType = BlockWidget.BorderType.Rounded
        )
      ),
      text = Text.from(Spans.from(Span.styled(title, Style(fg = Some(Color.Yellow)))))
    )
    f.renderWidget(para, area)
  }

  private def renderStep(f: Frame, area: Rect, ws: OutputWizardState): Unit = {
    val innerArea = area.inner(Margin(1))

    ws.step match {
      case OutputWizardStep.EnterName =>
        renderTextInput(f, innerArea, "Output Name:", ws.name, "e.g., my-java-output", ws.error)

      case OutputWizardStep.SelectSources =>
        renderTextInput(f, innerArea, "Source Patterns (* for all):", ws.sources, "*", None)

      case OutputWizardStep.EnterPath =>
        renderTextInput(f, innerArea, "Output Path:", ws.path, "generated", ws.error)

      case OutputWizardStep.EnterPackage =>
        renderTextInput(f, innerArea, "Package Name:", ws.pkg, "com.example", ws.error)

      case OutputWizardStep.SelectLanguage =>
        val select = ws.languageSelect.copy(isFocused = true)
        select.render(innerArea, f.buffer)

      case OutputWizardStep.SelectDbLib =>
        val select = ws.dbLibSelect.copy(isFocused = true)
        select.render(innerArea, f.buffer)

      case OutputWizardStep.SelectJsonLib =>
        val select = ws.jsonLibSelect.copy(isFocused = true)
        select.render(innerArea, f.buffer)

      case OutputWizardStep.ScalaDialect =>
        val select = ws.dialectSelect.copy(isFocused = true)
        select.render(innerArea, f.buffer)

      case OutputWizardStep.ScalaDsl =>
        val select = ws.dslSelect.copy(isFocused = true)
        select.render(innerArea, f.buffer)

      case OutputWizardStep.Review =>
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

  private def renderReview(f: Frame, area: Rect, ws: OutputWizardState): Unit = {
    val scalaInfo = if (ws.language.contains("scala")) {
      List(
        Spans.from(Span.styled("Scala Dialect: ", Style(fg = Some(Color.Cyan))), Span.nostyle(ws.scalaDialect.getOrElse("scala3"))),
        Spans.from(Span.styled("DSL Style: ", Style(fg = Some(Color.Cyan))), Span.nostyle(ws.scalaDsl.getOrElse("scala")))
      )
    } else {
      Nil
    }

    val lines = List(
      Spans.from(Span.styled("Review Configuration", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))),
      Spans.nostyle(""),
      Spans.from(Span.styled("Name: ", Style(fg = Some(Color.Cyan))), Span.nostyle(ws.name)),
      Spans.from(Span.styled("Sources: ", Style(fg = Some(Color.Cyan))), Span.nostyle(ws.sources)),
      Spans.from(Span.styled("Path: ", Style(fg = Some(Color.Cyan))), Span.nostyle(ws.path)),
      Spans.from(Span.styled("Package: ", Style(fg = Some(Color.Cyan))), Span.nostyle(ws.pkg)),
      Spans.from(Span.styled("Language: ", Style(fg = Some(Color.Cyan))), Span.nostyle(ws.language.getOrElse("java"))),
      Spans.from(Span.styled("DB Library: ", Style(fg = Some(Color.Cyan))), Span.nostyle(ws.dbLib.getOrElse("foundations"))),
      Spans.from(Span.styled("JSON Library: ", Style(fg = Some(Color.Cyan))), Span.nostyle(ws.jsonLib.getOrElse("jackson")))
    ) ++ scalaInfo ++ List(
      Spans.nostyle(""),
      Spans.from(Span.styled("Press Enter to save, Esc to go back", Style(fg = Some(Color.Green))))
    )

    lines.zipWithIndex.foreach { case (spans, idx) =>
      f.buffer.setSpans(area.x, area.y + idx, spans, area.width)
    }
  }

  private def renderFooter(f: Frame, area: Rect, ws: OutputWizardState): Unit = {
    val hint = ws.step match {
      case OutputWizardStep.SelectLanguage | OutputWizardStep.SelectDbLib | OutputWizardStep.SelectJsonLib | OutputWizardStep.ScalaDialect | OutputWizardStep.ScalaDsl =>
        "[Up/Down] Select  [Enter] Next  [Esc] Back"
      case OutputWizardStep.Review => "[Enter] Save  [Esc] Back"
      case _                       => "[Enter] Next  [Esc] Back"
    }

    val para = ParagraphWidget(text = Text.nostyle(hint))
    f.renderWidget(para, area)
  }

  private def stepNumber(step: OutputWizardStep, language: Option[String]): Int = step match {
    case OutputWizardStep.EnterName      => 1
    case OutputWizardStep.SelectSources  => 2
    case OutputWizardStep.EnterPath      => 3
    case OutputWizardStep.EnterPackage   => 4
    case OutputWizardStep.SelectLanguage => 5
    case OutputWizardStep.SelectDbLib    => 6
    case OutputWizardStep.SelectJsonLib  => 7
    case OutputWizardStep.ScalaDialect   => 8
    case OutputWizardStep.ScalaDsl       => 9
    case OutputWizardStep.Review         => if (language.contains("scala")) 10 else 8
  }

  private def stepTitle(step: OutputWizardStep): String = step match {
    case OutputWizardStep.EnterName      => "Output Name"
    case OutputWizardStep.SelectSources  => "Source Patterns"
    case OutputWizardStep.EnterPath      => "Output Path"
    case OutputWizardStep.EnterPackage   => "Package Name"
    case OutputWizardStep.SelectLanguage => "Language"
    case OutputWizardStep.SelectDbLib    => "Database Library"
    case OutputWizardStep.SelectJsonLib  => "JSON Library"
    case OutputWizardStep.ScalaDialect   => "Scala Dialect"
    case OutputWizardStep.ScalaDsl       => "DSL Style"
    case OutputWizardStep.Review         => "Review"
  }
}

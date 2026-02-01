package typr.cli.tui.screens

import io.circe.Json
import tui.*
import tui.crossterm.KeyCode
import tui.widgets.*
import typr.cli.config.ConfigParser
import typr.cli.tui.AppScreen
import typr.cli.tui.ConnectionTestResult
import typr.cli.tui.FocusableId
import typr.cli.tui.Location
import typr.cli.tui.SourceName
import typr.cli.tui.SourceStatus
import typr.cli.tui.SourceViewState
import typr.cli.tui.SourceWizardState
import typr.cli.tui.TuiState
import typr.cli.tui.components.BackButton
import typr.cli.tui.navigation.Navigator
import typr.cli.tui.util.ConnectionTester

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*

object SourceList {
  private val testers = new ConcurrentHashMap[String, ConnectionTester]()

  def focusableElements(itemCount: Int): List[FocusableId] =
    (0 until itemCount).map(i => FocusableId.ListItem(i)).toList

  def handleKey(state: TuiState, list: AppScreen.SourceList, keyCode: KeyCode): TuiState = {
    val sources = state.config.sources.getOrElse(Map.empty).toList.sortBy(_._1)
    val itemCount = sources.length + 1

    keyCode match {
      case _: KeyCode.Up | _: KeyCode.BackTab =>
        val newIdx = math.max(0, list.selectedIndex - 1)
        val newState = state.copy(currentScreen = list.copy(selectedIndex = newIdx))
        Navigator.setFocus(newState, FocusableId.ListItem(newIdx))

      case _: KeyCode.Down | _: KeyCode.Tab =>
        val newIdx = math.min(itemCount - 1, list.selectedIndex + 1)
        val newState = state.copy(currentScreen = list.copy(selectedIndex = newIdx))
        Navigator.setFocus(newState, FocusableId.ListItem(newIdx))

      case _: KeyCode.Enter =>
        if (list.selectedIndex == 0) {
          Navigator.navigateTo(
            state,
            Location.SourceWizard(typr.cli.tui.SourceWizardStep.SelectType),
            AppScreen.SourceWizard(SourceWizardState.initial)
          )
        } else {
          val (sourceName, sourceJson) = sources(list.selectedIndex - 1)
          val viewState = SourceViewState.fromSource(sourceName, sourceJson)
          Navigator.navigateTo(state, Location.SourceView(sourceName), AppScreen.SourceView(viewState))
        }

      case _: KeyCode.Esc =>
        testers.clear()
        Navigator.goBack(state)

      case c: KeyCode.Char if c.c() == 'd' && list.selectedIndex > 0 =>
        val sourceName = sources(list.selectedIndex - 1)._1
        val newSources = state.config.sources.map(_ - sourceName)
        val newConfig = state.config.copy(sources = newSources)
        testers.remove(sourceName)
        val newResults = list.testResults - sourceName
        state.copy(
          config = newConfig,
          hasUnsavedChanges = true,
          currentScreen = list.copy(testResults = newResults),
          statusMessage = Some((s"Deleted source: $sourceName", Color.Yellow))
        )

      case c: KeyCode.Char if c.c() == 'r' && list.selectedIndex > 0 =>
        val sourceName = sources(list.selectedIndex - 1)._1
        val sourceJson = sources(list.selectedIndex - 1)._2
        testers.remove(sourceName)
        val tester = new ConnectionTester()
        testers.put(sourceName, tester)
        tester.testSource(sourceJson)
        val newResults = list.testResults + (sourceName -> ConnectionTestResult.Testing)
        state.copy(currentScreen = list.copy(testResults = newResults))

      case c: KeyCode.Char if c.c() == 'q' =>
        testers.clear()
        Navigator.handleEscape(state)

      case _ => state
    }
  }

  def tick(state: TuiState, list: AppScreen.SourceList): TuiState = {
    val sources = state.config.sources.getOrElse(Map.empty)
    var newResults = list.testResults
    var changed = false

    sources.foreach { case (name, json) =>
      val sourceType = json.hcursor.get[String]("type").getOrElse("")
      // Skip connection testing for spec sources (they're file-based)
      val isSpecSource = sourceType == "openapi" || sourceType == "jsonschema"
      if (!isSpecSource && !list.testResults.contains(name) && !testers.containsKey(name)) {
        val tester = new ConnectionTester()
        testers.put(name, tester)
        tester.testSource(json)
        newResults = newResults + (name -> ConnectionTestResult.Testing)
        changed = true
      }
    }

    testers.asScala.foreach { case (name, tester) =>
      val result = tester.getResult
      if (list.testResults.get(name) != Some(result)) {
        newResults = newResults + (name -> result)
        changed = true
        result match {
          case ConnectionTestResult.Success(_) | ConnectionTestResult.Failure(_) =>
            testers.remove(name)
          case _ =>
        }
      }
    }

    if (changed) {
      state.copy(currentScreen = list.copy(testResults = newResults))
    } else {
      state
    }
  }

  def render(f: Frame, state: TuiState, list: AppScreen.SourceList): Unit = {
    val sources = state.config.sources.getOrElse(Map.empty).toList.sortBy(_._1)

    val chunks = Layout(
      direction = Direction.Vertical,
      margin = Margin(1),
      constraints = Array(Constraint.Length(3), Constraint.Min(5))
    ).split(f.size)

    val isBackHovered = state.hoverPosition.exists { case (col, row) =>
      BackButton.isClicked(col, row, 1)
    }
    BackButton.renderClickable(f, chunks(0), "Sources", isBackHovered)

    val block = BlockWidget(
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    f.renderWidget(block, chunks(1))

    val innerArea = Rect(
      x = chunks(1).x + 2,
      y = chunks(1).y + 1,
      width = chunks(1).width - 4,
      height = chunks(1).height - 2
    )

    val buf = f.buffer
    var y = innerArea.y

    val addSelected = list.selectedIndex == 0
    val addStyle =
      if (addSelected) Style(fg = Some(Color.Green), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
      else Style(fg = Some(Color.Green), addModifier = Modifier.BOLD)
    val addPrefix = if (addSelected) "> " else "  "
    buf.setString(innerArea.x, y, s"$addPrefix+ Add new source", addStyle)
    y += 1

    sources.zipWithIndex.foreach { case ((name, json), idx) =>
      if (y < innerArea.y + innerArea.height.toInt - 2) {
        val isSelected = list.selectedIndex == idx + 1
        val sourceType = json.hcursor.get[String]("type").getOrElse("unknown")
        val testResult = list.testResults.get(name)

        val isSpecSource = sourceType == "openapi" || sourceType == "jsonschema"

        val (statusIcon, statusColor) = if (isSpecSource) {
          // Check if spec is loaded from sourceStatuses
          state.sourceStatuses.get(SourceName(name)) match {
            case Some(_: SourceStatus.ReadySpec)   => ("◆", Color.Green)
            case Some(_: SourceStatus.LoadingSpec) => ("◇", Color.Cyan)
            case Some(_: SourceStatus.Failed)      => ("✗", Color.Red)
            case _                                 => ("◇", Color.Gray)
          }
        } else {
          testResult match {
            case None                                  => ("○", Color.Gray)
            case Some(ConnectionTestResult.Pending)    => ("○", Color.Gray)
            case Some(ConnectionTestResult.Testing)    => ("◕", Color.Cyan)
            case Some(ConnectionTestResult.Success(_)) => ("●", Color.Green)
            case Some(ConnectionTestResult.Failure(_)) => ("✗", Color.Red)
          }
        }

        val details = sourceType match {
          case "duckdb" =>
            val path = json.hcursor.get[String]("path").getOrElse(":memory:")
            s"($path)"
          case "openapi" | "jsonschema" =>
            val spec = json.hcursor
              .get[String]("spec")
              .getOrElse(
                json.hcursor.downField("specs").downArray.as[String].getOrElse("")
              )
            if (spec.nonEmpty) s"($spec)" else ""
          case _ =>
            val host = json.hcursor.get[String]("host").getOrElse("")
            val port = json.hcursor.get[Long]("port").map(_.toString).getOrElse("")
            val database = json.hcursor.get[String]("database").getOrElse("")
            if (host.nonEmpty && port.nonEmpty) s"($host:$port/$database)"
            else if (host.nonEmpty) s"($host/$database)"
            else ""
        }

        val prefix = if (isSelected) "> " else "  "
        val nameStyle =
          if (isSelected) Style(fg = Some(Color.Cyan), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
          else Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)
        val typeStyle =
          if (isSelected) Style(fg = Some(Color.Yellow), bg = Some(Color.Blue))
          else Style(fg = Some(Color.Yellow))
        val detailStyle =
          if (isSelected) Style(fg = Some(Color.Gray), bg = Some(Color.Blue))
          else Style(fg = Some(Color.Gray))

        val line = Spans.from(
          Span.styled(prefix, nameStyle),
          Span.styled(s"$statusIcon ", Style(fg = Some(statusColor))),
          Span.styled(name, nameStyle),
          Span.nostyle(" "),
          Span.styled(sourceType, typeStyle),
          Span.nostyle(" "),
          Span.styled(details, detailStyle)
        )
        buf.setSpans(innerArea.x, y, line, innerArea.width.toInt)
        y += 1

        testResult match {
          case Some(ConnectionTestResult.Failure(err)) =>
            val errLines = wrapText(err, innerArea.width.toInt - 6)
            errLines.take(3).foreach { errLine =>
              if (y < innerArea.y + innerArea.height.toInt - 2) {
                val errStyle =
                  if (isSelected) Style(fg = Some(Color.Red), bg = Some(Color.Blue))
                  else Style(fg = Some(Color.Red))
                buf.setString(innerArea.x + 4, y, errLine, errStyle)
                y += 1
              }
            }
          case _ =>
        }
      }
    }

    val hintY = f.size.y + f.size.height.toInt - 2
    val hint = if (list.selectedIndex == 0) {
      "[Tab/Up/Down] Navigate  [Enter] Add  [Esc] Back"
    } else {
      "[Tab] Nav  [Enter] Edit  [r] Retest  [d] Delete  [Esc] Back"
    }
    buf.setString(innerArea.x, hintY, hint, Style(fg = Some(Color.DarkGray)))
  }

  private def wrapText(text: String, maxWidth: Int): List[String] = {
    if (text.length <= maxWidth) {
      List(text)
    } else {
      val words = text.split(" ")
      val lines = scala.collection.mutable.ListBuffer[String]()
      var currentLine = ""

      words.foreach { word =>
        if (currentLine.isEmpty) {
          currentLine = word
        } else if (currentLine.length + 1 + word.length <= maxWidth) {
          currentLine = currentLine + " " + word
        } else {
          lines += currentLine
          currentLine = word
        }
      }
      if (currentLine.nonEmpty) {
        lines += currentLine
      }
      lines.toList
    }
  }
}

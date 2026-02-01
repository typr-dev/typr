package typr.cli.commands

import cats.effect.ExitCode
import cats.effect.IO
import tui.*
import tui.crossterm.KeyCode
import tui.crossterm.KeyEvent
import tui.crossterm.MouseEvent
import tui.crossterm.MouseEventKind
import tui.widgets.*
import typr.*
import typr.cli.config.*
import typr.cli.tui.*
import typr.cli.tui.components.BackButton
import typr.cli.tui.navigation.Navigator
import typr.cli.tui.screens.*
import typr.cli.tui.util.SourceLoader
import typr.internal.external.{ExternalTools, ExternalToolsConfig}
import tui.react.Integration as ReactIntegration
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.{Duration as JDuration, Instant}
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Interactive {
  private val sourceLoader: AtomicReference[Option[SourceLoader]] = new AtomicReference(None)
  private val lastTerminalSize: AtomicReference[(Int, Int)] = new AtomicReference((80, 24))
  private var sourceTester: Option[typr.cli.tui.util.ConnectionTester] = None

  /** State reference for React component callbacks to update state */
  private val pendingStateUpdate: AtomicReference[Option[TuiState => TuiState]] = new AtomicReference(None)

  /** Schedule a state update from a React component callback */
  def scheduleStateUpdate(f: TuiState => TuiState): Unit = {
    pendingStateUpdate.set(Some(f))
  }

  /** Apply any pending state updates */
  private def applyPendingUpdates(state: TuiState): TuiState = {
    pendingStateUpdate.getAndSet(None) match {
      case Some(f) => f(state)
      case None    => state
    }
  }

  /** Convert TuiState to GlobalState for React components */
  private def toGlobalState(state: TuiState): GlobalState = {
    GlobalState(
      config = state.config,
      configPath = state.configPath,
      sourceStatuses = state.sourceStatuses,
      navigation = state.navigation,
      currentLocation = state.navigation.current,
      hasUnsavedChanges = state.hasUnsavedChanges,
      shouldExit = state.shouldExit,
      statusMessage = state.statusMessage,
      terminalSize = state.terminalSize,
      hoverPosition = state.hoverPosition
    )
  }

  /** Create GlobalCallbacks that schedule state updates */
  private def createCallbacks(): GlobalCallbacks = new GlobalCallbacks {
    def navigateTo(location: Location): Unit = {
      scheduleStateUpdate { state =>
        val screen = locationToScreen(location, state)
        Navigator.navigateTo(state, location, screen)
      }
    }

    def goBack(): Unit = {
      scheduleStateUpdate(state => Navigator.goBack(state))
    }

    def requestExit(): Unit = {
      scheduleStateUpdate(_.copy(shouldExit = true))
    }

    def updateConfig(f: typr.cli.config.TyprConfig => typr.cli.config.TyprConfig): Unit = {
      scheduleStateUpdate(state => state.copy(config = f(state.config), hasUnsavedChanges = true))
    }

    def setStatusMessage(msg: String, color: tui.Color): Unit = {
      scheduleStateUpdate(_.copy(statusMessage = Some((msg, color))))
    }

    def clearStatusMessage(): Unit = {
      scheduleStateUpdate(_.copy(statusMessage = None))
    }

    def updateOutputEditor(f: typr.cli.tui.OutputEditorState => typr.cli.tui.OutputEditorState): Unit = {
      scheduleStateUpdate { state =>
        state.currentScreen match {
          case editor: typr.cli.tui.AppScreen.OutputEditor =>
            state.copy(currentScreen = editor.copy(state = f(editor.state)))
          case _ => state
        }
      }
    }

    def updateSourceEditor(f: typr.cli.tui.SourceEditorState => typr.cli.tui.SourceEditorState): Unit = {
      scheduleStateUpdate { state =>
        state.currentScreen match {
          case editor: typr.cli.tui.AppScreen.SourceEditor =>
            state.copy(currentScreen = editor.copy(state = f(editor.state)))
          case _ => state
        }
      }
    }

    def saveSourceEditor(): Unit = {
      scheduleStateUpdate { state =>
        import typr.cli.tui.navigation.Navigator
        state.currentScreen match {
          case editor: typr.cli.tui.AppScreen.SourceEditor =>
            val es = editor.state
            val sourceName = editor.sourceName
            val newJson = es.toJson
            val newSources = state.config.sources.getOrElse(Map.empty) + (sourceName -> newJson)
            val newConfig = state.config.copy(sources = Some(newSources))
            Navigator
              .goBack(state)
              .copy(
                config = newConfig,
                hasUnsavedChanges = true,
                statusMessage = Some((s"Updated source: $sourceName", tui.Color.Green))
              )
          case _ => state
        }
      }
    }

    def testSourceConnection(): Unit = {
      scheduleStateUpdate { state =>
        state.currentScreen match {
          case editor: typr.cli.tui.AppScreen.SourceEditor =>
            val es = editor.state
            val newTester = new typr.cli.tui.util.ConnectionTester()
            sourceTester = Some(newTester)
            newTester.testSource(es.toJson)
            val newState = es.copy(connectionResult = typr.cli.tui.ConnectionTestResult.Testing)
            state.copy(currentScreen = editor.copy(state = newState))
          case _ => state
        }
      }
    }

    def updateSourceWizard(f: typr.cli.tui.SourceWizardState => typr.cli.tui.SourceWizardState): Unit = {
      scheduleStateUpdate { state =>
        state.currentScreen match {
          case wizard: typr.cli.tui.AppScreen.SourceWizard =>
            state.copy(currentScreen = wizard.copy(state = f(wizard.state)))
          case _ => state
        }
      }
    }

    def saveSourceWizard(): Unit = {
      scheduleStateUpdate { state =>
        import typr.cli.tui.navigation.Navigator
        state.currentScreen match {
          case wizard: typr.cli.tui.AppScreen.SourceWizard =>
            val ws = wizard.state
            val sourceJson = ws.toJson
            val newSources = state.config.sources.getOrElse(Map.empty) + (ws.name.trim -> sourceJson)
            val newConfig = state.config.copy(sources = Some(newSources))
            Navigator
              .goBack(state)
              .copy(
                config = newConfig,
                hasUnsavedChanges = true,
                statusMessage = Some((s"Added source: ${ws.name}", tui.Color.Green))
              )
          case _ => state
        }
      }
    }

    def dialogSave(): Unit = {
      scheduleStateUpdate { state =>
        import typr.cli.tui.navigation.Navigator
        import typr.cli.config.ConfigWriter
        ConfigWriter.write(state.configPath, state.config) match {
          case Right(_) =>
            Navigator
              .dismissDialog(state)
              .copy(
                hasUnsavedChanges = false,
                shouldExit = true,
                statusMessage = Some(("Configuration saved!", tui.Color.Green))
              )
          case Left(e) =>
            Navigator
              .dismissDialog(state)
              .copy(
                statusMessage = Some((s"Save failed: ${e.getMessage}", tui.Color.Red))
              )
        }
      }
    }

    def dialogDiscard(): Unit = {
      scheduleStateUpdate { state =>
        import typr.cli.tui.navigation.Navigator
        Navigator.dismissDialog(state).copy(shouldExit = true)
      }
    }

    def dialogCancel(): Unit = {
      scheduleStateUpdate { state =>
        import typr.cli.tui.navigation.Navigator
        Navigator.dismissDialog(state)
      }
    }

    def dialogYes(): Unit = {
      scheduleStateUpdate { state =>
        import typr.cli.tui.navigation.Navigator
        Navigator.dismissDialog(state).copy(shouldExit = true)
      }
    }

    def dialogNo(): Unit = {
      scheduleStateUpdate { state =>
        import typr.cli.tui.navigation.Navigator
        Navigator.dismissDialog(state)
      }
    }

    def dialogSetFocus(index: Int): Unit = {
      scheduleStateUpdate { state =>
        state.navigation.pendingConfirmation match {
          case Some(_: ConfirmationDialog.UnsavedChanges) =>
            state.copy(navigation = state.navigation.showConfirmation(ConfirmationDialog.UnsavedChanges(index)))
          case Some(_: ConfirmationDialog.ConfirmExit) =>
            state.copy(navigation = state.navigation.showConfirmation(ConfirmationDialog.ConfirmExit(index)))
          case None =>
            state
        }
      }
    }
  }

  /** Map Location to a new AppScreen */
  private def locationToScreen(location: Location, state: TuiState): AppScreen = location match {
    case Location.MainMenu =>
      AppScreen.MainMenu(selectedIndex = 0)
    case Location.SourceList =>
      AppScreen.SourceList(selectedIndex = 0, testResults = Map.empty)
    case Location.OutputList =>
      AppScreen.OutputList(selectedIndex = 0)
    case Location.TypeList(filter) =>
      AppScreen.TypeList(selectedIndex = TypeList.AddButtonIndex, typeKindFilter = filter.orElse(Some(BridgeTypeKind.Scalar)))
    case Location.SchemaBrowserPicker =>
      AppScreen.BrowserSourcePicker(BrowserSourcePickerState(selectedIndex = 0))
    case Location.SlidingDemo =>
      AppScreen.SlidingDemo(SlidingDemoState.initial)
    case Location.Generating =>
      AppScreen.Generating(GeneratingState.initial)
    case Location.SourceWizard(step) =>
      AppScreen.SourceWizard(SourceWizardState.initial.copy(step = step))
    case Location.OutputWizard(step) =>
      AppScreen.OutputWizard(OutputWizardState.initial.copy(step = step))
    case Location.TypeWizard(step) =>
      val suggestions = TypeList.computeSuggestions(state)
      AppScreen.TypeWizard(TypeWizardState.initial(suggestions).copy(step = step))
    case Location.CompositeWizard(step) =>
      val compositeSuggestions = TypeList.computeCompositeSuggestions(state)
      AppScreen.CompositeWizard(CompositeWizardState.withSuggestions(compositeSuggestions).copy(step = step))
    case Location.DomainTypeBuilder =>
      val suggestions = computeDomainTypeSuggestions(state)
      AppScreen.DomainTypeBuilder(DomainTypeBuilderState.withSuggestions(suggestions))
    case Location.DomainTypeEditor(name) =>
      val typeDef = state.config.types.flatMap(_.get(name))
      typeDef match {
        case Some(dt: typr.config.generated.DomainType) =>
          val editorState = DomainTypeBuilderState.fromConfigDomainType(name, dt)
          val validatedState = validateAlignedSources(editorState, state.sourceStatuses)
          AppScreen.DomainTypeBuilder(validatedState)
        case _ =>
          AppScreen.DomainTypeBuilder(DomainTypeBuilderState.initial.copy(name = name))
      }
    case Location.SourceEditor(name) =>
      val sourceJson = state.config.sources.flatMap(_.get(name))
      val editorState = sourceJson.map(SourceEditorState.fromJson).getOrElse(SourceEditorState.fromJson(io.circe.Json.obj()))
      AppScreen.SourceEditor(name, editorState)
    case Location.OutputEditor(name) =>
      val output = state.config.outputs.flatMap(_.get(name))
      val emptyOutput = typr.config.generated.Output(
        bridge = None,
        db_lib = None,
        effect_type = None,
        framework = None,
        json = None,
        language = "scala",
        matchers = None,
        `package` = "",
        path = "",
        scala = None,
        sources = None
      )
      val editorState = output.map(OutputEditorState.fromOutput).getOrElse(OutputEditorState.fromOutput(emptyOutput))
      AppScreen.OutputEditor(name, editorState)
    case Location.TypeEditor(name) =>
      val typeDef = state.config.types.flatMap(_.get(name))
      typeDef match {
        case Some(st: typr.config.generated.FieldType) =>
          val editorState = TypeEditorState.fromFieldType(name, st)
          AppScreen.TypeEditor(name, editorState)
        case _ =>
          val emptyFieldType = typr.config.generated.FieldType(
            api = None,
            db = None,
            model = None,
            underlying = None,
            validation = None
          )
          val editorState = TypeEditorState.fromFieldType(name, emptyFieldType)
          AppScreen.TypeEditor(name, editorState)
      }
    case Location.CompositeEditor(name) =>
      val bridge = typr.bridge.CompositeTypeDefinition.empty(name)
      val editorState = CompositeEditorState.fromCompositeType(bridge)
      AppScreen.CompositeEditor(name, editorState)
    case Location.SchemaBrowser(sourceName, level, schema, table) =>
      state.sourceStatuses.get(sourceName) match {
        case Some(SourceStatus.Ready(metaDb, _, _, _, _, _)) =>
          val browserState = SchemaBrowserState.fromMetaDb(sourceName, metaDb)
          AppScreen.SchemaBrowser(browserState)
        case _ =>
          val emptyMetaDb = MetaDb(
            dbType = DbType.PostgreSQL,
            relations = Map.empty,
            enums = List.empty[typr.db.StringEnum],
            domains = List.empty[typr.db.Domain]
          )
          AppScreen.SchemaBrowser(SchemaBrowserState.fromMetaDb(sourceName, emptyMetaDb))
      }
    case Location.SpecBrowser(sourceName) =>
      state.sourceStatuses.get(sourceName) match {
        case Some(SourceStatus.ReadySpec(spec, specPath, sourceType, _)) =>
          AppScreen.SpecBrowser(SpecBrowserState.fromSpec(sourceName, spec, sourceType))
        case _ =>
          val emptySpec = typr.openapi.ParsedSpec(
            info = typr.openapi.SpecInfo(title = "", version = "", description = None),
            models = Nil,
            sumTypes = Nil,
            apis = Nil,
            webhooks = Nil,
            securitySchemes = Map.empty,
            warnings = Nil
          )
          AppScreen.SpecBrowser(SpecBrowserState.fromSpec(sourceName, emptySpec, SpecSourceType.OpenApi))
      }
    case Location.ConnectionTest(name) =>
      AppScreen.ConnectionTest(name, ConnectionTestResult.Pending)
    case Location.SourceView(name) =>
      val sourceJson = state.config.sources.flatMap(_.get(name)).getOrElse(io.circe.Json.obj())
      val viewState = SourceViewState.fromSource(name, sourceJson)
      AppScreen.SourceView(viewState)
  }

  def run(configPath: Path): IO[ExitCode] = IO.blocking {
    val absolutePath = configPath.toAbsolutePath

    if (!Files.exists(absolutePath)) {
      System.err.println(s"Error: Config file not found: $absolutePath")
      ExitCode.Error
    } else {
      val yamlContent = Files.readString(absolutePath)
      val substituted = EnvSubstitution.substitute(yamlContent).getOrElse(yamlContent)
      ConfigParser.parse(substituted) match {
        case Right(config) =>
          runTui(config, absolutePath)
          ExitCode.Success
        case Left(err) =>
          System.err.println(s"Error: Failed to parse config: $err")
          ExitCode.Error
      }
    }
  }

  def init(): IO[ExitCode] = {
    val defaultPath = Paths.get("typr.yaml")
    run(defaultPath)
  }

  private def runTui(config: TyprConfig, configPath: Path): Unit = {
    if (System.console() == null) {
      println("Error: No TTY available for interactive mode")
      return
    }

    withTerminal { (jni, terminal) =>
      var state = TuiState.initial(config, configPath)
      val tickRate = JDuration.ofMillis(16)
      var lastTick = Instant.now()

      val loader = new SourceLoader()
      sourceLoader.set(Some(loader))
      val buildDir = Path.of(System.getProperty("user.dir"))
      loader.startLoading(config, buildDir)

      state = state.copy(
        sourceStatuses = config.sources.getOrElse(Map.empty).keys.map(n => SourceName(n) -> SourceStatus.Pending).toMap
      )

      while (!state.shouldExit) {
        terminal.draw { f =>
          lastTerminalSize.set((f.size.width, f.size.height))
          ReactIntegration.clearRegistry()
          renderScreen(f, state)
        }

        val now = Instant.now()
        val elapsed = JDuration.between(lastTick, now)

        if (elapsed.compareTo(tickRate) >= 0) {
          lastTick = now
          state = handleTick(state)
        }

        val remaining = tickRate.minus(JDuration.between(lastTick, Instant.now()))
        val timeoutDur = new tui.crossterm.Duration(
          Math.max(0, remaining.toSeconds),
          Math.max(0, remaining.getNano)
        )

        if (jni.poll(timeoutDur)) {
          jni.read() match {
            case key: tui.crossterm.Event.Key =>
              state = handleKey(state, key.keyEvent)
            case mouse: tui.crossterm.Event.Mouse =>
              state = handleMouse(state, mouse.mouseEvent)
            case _ => ()
          }
        }
      }

      sourceLoader.get().foreach(_.shutdown())
      sourceLoader.set(None)
    }
  }

  // CONTROL modifier bit in crossterm KeyModifiers
  private val CONTROL_MODIFIER = 0x02

  private def handleKey(state: TuiState, keyEvent: KeyEvent): TuiState = {
    val clearedState = state.copy(statusMessage = None)
    val keyCode = keyEvent.code
    val hasCtrl = (keyEvent.modifiers.bits & CONTROL_MODIFIER) != 0

    // Global Ctrl+Q to quit
    keyCode match {
      case c: KeyCode.Char if c.c() == 'q' && hasCtrl =>
        if (clearedState.hasUnsavedChanges) {
          clearedState.copy(navigation = clearedState.navigation.showConfirmation(ConfirmationDialog.UnsavedChanges(0)))
        } else {
          clearedState.copy(shouldExit = true)
        }
      case _ =>
        // Handle confirmation dialog if present
        clearedState.navigation.pendingConfirmation match {
          case Some(dialog) =>
            handleConfirmationDialog(clearedState, dialog, keyCode)
          case None =>
            handleScreenKey(clearedState, keyCode)
        }
    }
  }

  private def handleConfirmationDialog(state: TuiState, dialog: ConfirmationDialog, keyCode: KeyCode): TuiState = {
    import typr.cli.tui.navigation.Navigator
    import typr.cli.config.ConfigWriter

    dialog match {
      case unsaved: ConfirmationDialog.UnsavedChanges =>
        keyCode match {
          case _: KeyCode.Tab =>
            val newFocus = (unsaved.focusedButton + 1) % 3
            state.copy(navigation = state.navigation.showConfirmation(ConfirmationDialog.UnsavedChanges(newFocus)))
          case _: KeyCode.BackTab =>
            val newFocus = if (unsaved.focusedButton == 0) 2 else unsaved.focusedButton - 1
            state.copy(navigation = state.navigation.showConfirmation(ConfirmationDialog.UnsavedChanges(newFocus)))
          case _: KeyCode.Enter =>
            unsaved.focusedButton match {
              case 0 => // Save
                ConfigWriter.write(state.configPath, state.config) match {
                  case Right(_) =>
                    Navigator
                      .dismissDialog(state)
                      .copy(
                        hasUnsavedChanges = false,
                        shouldExit = true,
                        statusMessage = Some(("Configuration saved!", tui.Color.Green))
                      )
                  case Left(e) =>
                    Navigator
                      .dismissDialog(state)
                      .copy(
                        statusMessage = Some((s"Save failed: ${e.getMessage}", tui.Color.Red))
                      )
                }
              case 1 => // Discard
                Navigator.dismissDialog(state).copy(shouldExit = true)
              case 2 => // Cancel
                Navigator.dismissDialog(state)
              case _ => state
            }
          case _: KeyCode.Esc =>
            Navigator.dismissDialog(state)
          case c: KeyCode.Char if c.c() == 's' =>
            ConfigWriter.write(state.configPath, state.config) match {
              case Right(_) =>
                Navigator
                  .dismissDialog(state)
                  .copy(
                    hasUnsavedChanges = false,
                    shouldExit = true,
                    statusMessage = Some(("Configuration saved!", tui.Color.Green))
                  )
              case Left(e) =>
                Navigator
                  .dismissDialog(state)
                  .copy(
                    statusMessage = Some((s"Save failed: ${e.getMessage}", tui.Color.Red))
                  )
            }
          case c: KeyCode.Char if c.c() == 'd' =>
            Navigator.dismissDialog(state).copy(shouldExit = true)
          case c: KeyCode.Char if c.c() == 'c' =>
            Navigator.dismissDialog(state)
          case _ => state
        }

      case confirm: ConfirmationDialog.ConfirmExit =>
        keyCode match {
          case _: KeyCode.Tab =>
            val newFocus = (confirm.focusedButton + 1) % 2
            state.copy(navigation = state.navigation.showConfirmation(ConfirmationDialog.ConfirmExit(newFocus)))
          case _: KeyCode.BackTab =>
            val newFocus = if (confirm.focusedButton == 0) 1 else 0
            state.copy(navigation = state.navigation.showConfirmation(ConfirmationDialog.ConfirmExit(newFocus)))
          case _: KeyCode.Enter =>
            confirm.focusedButton match {
              case 0 => // Yes
                Navigator.dismissDialog(state).copy(shouldExit = true)
              case 1 => // No
                Navigator.dismissDialog(state)
              case _ => state
            }
          case _: KeyCode.Esc =>
            Navigator.dismissDialog(state)
          case c: KeyCode.Char if c.c() == 'y' =>
            Navigator.dismissDialog(state).copy(shouldExit = true)
          case c: KeyCode.Char if c.c() == 'n' =>
            Navigator.dismissDialog(state)
          case _ => state
        }
    }
  }

  private def handleScreenKey(state: TuiState, keyCode: KeyCode): TuiState = {
    state.currentScreen match {
      case splash: AppScreen.Splash =>
        SplashScreen.handleKey(state, splash, keyCode)

      case menu: AppScreen.MainMenu =>
        MainMenu.handleKey(state, menu, keyCode)

      case list: AppScreen.SourceList =>
        SourceList.handleKey(state, list, keyCode)

      case wizard: AppScreen.SourceWizard =>
        SourceWizard.handleKey(state, wizard, keyCode)

      case list: AppScreen.OutputList =>
        OutputList.handleKey(state, list, keyCode)

      case wizard: AppScreen.OutputWizard =>
        OutputWizard.handleKey(state, wizard, keyCode)

      case test: AppScreen.ConnectionTest =>
        ConnectionTest.handleKey(state, test, keyCode)

      case gen: AppScreen.Generating =>
        GeneratingScreen.handleKey(state, gen, keyCode)

      case editor: AppScreen.SourceEditor =>
        SourceEditor.handleKey(state, editor, keyCode)

      case editor: AppScreen.OutputEditor =>
        OutputEditor.handleKey(state, editor, keyCode)

      case list: AppScreen.TypeList =>
        TypeList.handleKey(state, list, keyCode)

      case wizard: AppScreen.TypeWizard =>
        TypeWizard.handleKey(state, wizard, keyCode)

      case wizard: AppScreen.CompositeWizard =>
        CompositeWizard.handleKey(state, wizard, keyCode)

      case builder: AppScreen.DomainTypeBuilder =>
        DomainTypeBuilder.handleKey(state, builder, keyCode)

      case editor: AppScreen.TypeEditor =>
        TypeEditor.handleKey(state, editor, keyCode)

      case editor: AppScreen.CompositeEditor =>
        CompositeEditor.handleKey(state, editor, keyCode)

      case browser: AppScreen.SchemaBrowser =>
        SchemaBrowser.handleKey(state, browser, keyCode)

      case browser: AppScreen.SpecBrowser =>
        SpecBrowser.handleKey(state, browser, keyCode)

      case view: AppScreen.SourceView =>
        SourceView.handleKey(state, view, keyCode)

      case picker: AppScreen.BrowserSourcePicker =>
        BrowserSourcePicker.handleKey(state, picker, keyCode)

      case demo: AppScreen.SlidingDemo =>
        SlidingDemo.handleKey(state, demo, keyCode)
    }
  }

  private def handleMouse(state: TuiState, event: MouseEvent): TuiState = {
    val (termWidth, termHeight) = lastTerminalSize.get()
    val stateWithHover = state.copy(
      hoverPosition = Some((event.column, event.row)),
      terminalSize = Some((termWidth, termHeight))
    )

    // Try tui-react event handling first
    event.kind match {
      case _: MouseEventKind.Down =>
        if (ReactIntegration.handleClick(event.column, event.row)) {
          return applyPendingUpdates(stateWithHover)
        }
      case _: MouseEventKind.ScrollUp =>
        if (ReactIntegration.handleScrollUp(event.column, event.row)) {
          return applyPendingUpdates(stateWithHover)
        }
      case _: MouseEventKind.ScrollDown =>
        if (ReactIntegration.handleScrollDown(event.column, event.row)) {
          return applyPendingUpdates(stateWithHover)
        }
      case _: MouseEventKind.Moved =>
        if (ReactIntegration.handleHover(event.column, event.row)) {
          return applyPendingUpdates(stateWithHover)
        }
      case _ => ()
    }

    stateWithHover.currentScreen match {
      case demo: AppScreen.SlidingDemo =>
        SlidingDemo.handleMouse(stateWithHover, demo, event)

      case menu: AppScreen.MainMenu =>
        MainMenu.handleMouse(stateWithHover, menu, event.column, event.row, termWidth, termHeight, event.kind)

      case picker: AppScreen.BrowserSourcePicker =>
        event.kind match {
          case _: MouseEventKind.Down =>
            BrowserSourcePicker.handleMouse(stateWithHover, picker, event.column, event.row)
          case _ => stateWithHover
        }

      case typeList: AppScreen.TypeList =>
        event.kind match {
          case _: MouseEventKind.Down =>
            if (BackButton.isClicked(event.column, event.row, 1)) {
              Navigator.goBack(stateWithHover)
            } else {
              TypeList.handleMouse(stateWithHover, typeList, event.column, event.row)
            }
          case _ => stateWithHover
        }

      case _: AppScreen.SourceList =>
        event.kind match {
          case _: MouseEventKind.Down if BackButton.isClicked(event.column, event.row, 1) =>
            Navigator.goBack(stateWithHover)
          case _ => stateWithHover
        }

      case _: AppScreen.OutputList =>
        event.kind match {
          case _: MouseEventKind.Down if BackButton.isClicked(event.column, event.row, 1) =>
            Navigator.goBack(stateWithHover)
          case _ => stateWithHover
        }

      case _: AppScreen.SourceView =>
        event.kind match {
          case _: MouseEventKind.Down if BackButton.isClicked(event.column, event.row, 1) =>
            Navigator.goBack(stateWithHover)
          case _ => stateWithHover
        }

      case _: AppScreen.ConnectionTest =>
        event.kind match {
          case _: MouseEventKind.Down if BackButton.isClicked(event.column, event.row, 1) =>
            Navigator.goBack(stateWithHover)
          case _ => stateWithHover
        }

      case _: AppScreen.SourceEditor =>
        event.kind match {
          case _: MouseEventKind.Down if BackButton.isClicked(event.column, event.row, 1) =>
            Navigator.goBack(stateWithHover)
          case _ => stateWithHover
        }

      case _: AppScreen.OutputEditor =>
        event.kind match {
          case _: MouseEventKind.Down if BackButton.isClicked(event.column, event.row, 1) =>
            Navigator.goBack(stateWithHover)
          case _ => stateWithHover
        }

      case _: AppScreen.TypeEditor =>
        event.kind match {
          case _: MouseEventKind.Down if BackButton.isClicked(event.column, event.row, 1) =>
            Navigator.goBack(stateWithHover)
          case _ => stateWithHover
        }

      case _: AppScreen.CompositeEditor =>
        event.kind match {
          case _: MouseEventKind.Down if BackButton.isClicked(event.column, event.row, 1) =>
            Navigator.goBack(stateWithHover)
          case _ => stateWithHover
        }

      case _: AppScreen.DomainTypeBuilder =>
        event.kind match {
          case _: MouseEventKind.Down if BackButton.isClicked(event.column, event.row, 1) =>
            Navigator.goBack(stateWithHover)
          case _ => stateWithHover
        }

      case _: AppScreen.SourceWizard =>
        event.kind match {
          case _: MouseEventKind.Down if BackButton.isClicked(event.column, event.row, 1) =>
            Navigator.goBack(stateWithHover)
          case _ => stateWithHover
        }

      case _ => stateWithHover
    }
  }

  private val generationFuture: AtomicReference[Option[Future[Unit]]] = new AtomicReference(None)
  private val generationTracker: AtomicReference[Option[Generate.ProgressTracker]] = new AtomicReference(None)
  private val generationLogger: AtomicReference[Option[TuiLogger]] = new AtomicReference(None)
  private val generationPhase: AtomicReference[GeneratingPhase] = new AtomicReference(GeneratingPhase.Starting)
  private val sourceFetches: AtomicReference[List[SourceFetchProgress]] = new AtomicReference(Nil)
  private val fetchedSources: ConcurrentHashMap[String, Generate.FetchedSource] = new ConcurrentHashMap()
  private val generationResult: AtomicReference[Option[Either[String, (Int, Int, Int, Int)]]] = new AtomicReference(None)

  private def handleTick(state: TuiState): TuiState = {
    import typr.cli.tui.navigation.Navigator

    // Tick navigation animation
    val stateWithNav = Navigator.tick(state)

    val stateWithSourceStatus = sourceLoader.get() match {
      case Some(loader) =>
        val buildDir = Path.of(System.getProperty("user.dir"))
        val configSources = stateWithNav.config.sources.getOrElse(Map.empty)
        val loaderStatuses = loader.getStatuses

        // Check for new sources that need loading
        configSources.foreach { case (name, json) =>
          val sourceName = SourceName(name)
          if (!loaderStatuses.contains(sourceName)) {
            loader.loadNewSource(sourceName, json, buildDir)
          } else {
            // Check if source config has changed
            loader.getLoadedConfig(sourceName) match {
              case Some(loadedJson) if loadedJson != json =>
                loader.reloadSource(sourceName, json, buildDir)
              case _ =>
            }
          }
        }

        // Check for deleted sources
        val configSourceNames = configSources.keys.map(SourceName(_)).toSet
        loaderStatuses.keys.foreach { sourceName =>
          if (!configSourceNames.contains(sourceName)) {
            loader.removeSource(sourceName)
          }
        }

        val newStatuses = loader.getStatuses
        if (newStatuses != stateWithNav.sourceStatuses) {
          stateWithNav.copy(sourceStatuses = newStatuses)
        } else {
          stateWithNav
        }
      case None =>
        stateWithNav
    }

    stateWithSourceStatus.currentScreen match {
      case list: AppScreen.SourceList =>
        SourceList.tick(stateWithSourceStatus, list)

      case editor: AppScreen.SourceEditor =>
        tickSourceEditor(stateWithSourceStatus, editor)

      case test: AppScreen.ConnectionTest =>
        ConnectionTest.tick(stateWithSourceStatus, test)

      case gen: AppScreen.Generating =>
        gen.state.phase match {
          case GeneratingPhase.Starting =>
            startGeneration(stateWithSourceStatus)

          case GeneratingPhase.FetchingSources | GeneratingPhase.GeneratingCode =>
            val currentPhase = generationPhase.get()
            val currentSources = sourceFetches.get()
            val currentLogger = generationLogger.get()
            val currentTracker = generationTracker.get()

            generationResult.get() match {
              case Some(Right((successful, failed, skipped, filesWritten))) =>
                generationFuture.set(None)
                generationResult.set(None)
                val newGenState = gen.state.copy(
                  phase = GeneratingPhase.Completed,
                  sourceFetches = currentSources,
                  tracker = currentTracker,
                  logger = currentLogger,
                  endTime = Some(System.currentTimeMillis()),
                  successful = successful,
                  failed = failed,
                  skipped = skipped,
                  filesWritten = filesWritten
                )
                stateWithSourceStatus.copy(currentScreen = AppScreen.Generating(newGenState))

              case Some(Left(error)) =>
                generationFuture.set(None)
                generationResult.set(None)
                val newGenState = gen.state.copy(
                  phase = GeneratingPhase.Failed(error),
                  sourceFetches = currentSources,
                  tracker = currentTracker,
                  logger = currentLogger,
                  endTime = Some(System.currentTimeMillis())
                )
                stateWithSourceStatus.copy(currentScreen = AppScreen.Generating(newGenState))

              case None =>
                val newGenState = gen.state.copy(
                  phase = currentPhase,
                  sourceFetches = currentSources,
                  tracker = currentTracker,
                  logger = currentLogger
                )
                stateWithSourceStatus.copy(currentScreen = AppScreen.Generating(newGenState))
            }

          case GeneratingPhase.Completed | GeneratingPhase.Failed(_) =>
            stateWithSourceStatus
        }

      case editor: AppScreen.OutputEditor =>
        OutputEditor.tick(stateWithSourceStatus, editor)

      case editor: AppScreen.TypeEditor =>
        TypeEditor.tick(stateWithSourceStatus, editor)

      case view: AppScreen.SourceView =>
        SourceView.tick(stateWithSourceStatus, view)

      case picker: AppScreen.BrowserSourcePicker =>
        BrowserSourcePicker.tick(stateWithSourceStatus, picker)

      case browser: AppScreen.SchemaBrowser =>
        SchemaBrowser.tick(stateWithSourceStatus, browser)

      case browser: AppScreen.SpecBrowser =>
        SpecBrowser.tick(stateWithSourceStatus, browser)

      case demo: AppScreen.SlidingDemo =>
        SlidingDemo.tick(stateWithSourceStatus, demo)

      case splash: AppScreen.Splash =>
        SplashScreen.tick(stateWithSourceStatus, splash)

      case _ =>
        stateWithSourceStatus
    }
  }

  private def tickSourceEditor(state: TuiState, editor: AppScreen.SourceEditor): TuiState = {
    sourceTester match {
      case Some(t) =>
        val result = t.getResult
        if (result != editor.state.connectionResult) {
          val newState = editor.state.copy(connectionResult = result)
          result match {
            case ConnectionTestResult.Success(_) | ConnectionTestResult.Failure(_) =>
              sourceTester = None
            case _ =>
          }
          state.copy(currentScreen = editor.copy(state = newState))
        } else {
          state
        }
      case None => state
    }
  }

  private def startGeneration(state: TuiState): TuiState = {
    val config = state.config

    val parsedSources = config.sources
      .getOrElse(Map.empty)
      .toList
      .flatMap { case (name, json) =>
        ConfigParser.parseSource(json).toOption.map(name -> _)
      }
      .toMap

    val parsedOutputs = config.outputs
      .getOrElse(Map.empty)
      .toList
      .flatMap { case (name, output) =>
        ConfigToOptions.convertOutput(name, output, config.types).toOption.map(name -> _)
      }
      .toMap

    if (parsedOutputs.isEmpty) {
      val newGenState = GeneratingState.initial.copy(
        phase = GeneratingPhase.Failed("No outputs configured")
      )
      return state.copy(currentScreen = AppScreen.Generating(newGenState))
    }

    val uniqueSourceNames = parsedOutputs.values.flatMap { outputConfig =>
      parsedSources.keys.filter(name => ConfigToOptions.matchesSource(name, outputConfig.sourcePatterns))
    }.toSet

    if (uniqueSourceNames.isEmpty) {
      val newGenState = GeneratingState.initial.copy(
        phase = GeneratingPhase.Failed("No sources match configured outputs")
      )
      return state.copy(currentScreen = AppScreen.Generating(newGenState))
    }

    val logger = TuiLogger()
    generationLogger.set(Some(logger))

    val outputNames = parsedOutputs.keys.toList.sorted
    val tracker = new Generate.ProgressTracker(outputNames)
    generationTracker.set(Some(tracker))

    val initialSourceFetches = uniqueSourceNames.toList.sorted.map { name =>
      val sourceType = parsedSources
        .get(name)
        .map {
          case ParsedSource.Database(db)  => db.`type`.getOrElse("database")
          case ParsedSource.DuckDb(_)     => "duckdb"
          case ParsedSource.OpenApi(_)    => "openapi"
          case ParsedSource.JsonSchema(_) => "jsonschema"
        }
        .getOrElse("unknown")
      SourceFetchProgress(name, sourceType, SourceFetchStatus.Pending, "", None, None)
    }
    sourceFetches.set(initialSourceFetches)
    generationPhase.set(GeneratingPhase.FetchingSources)
    fetchedSources.clear()

    val buildDir = Path.of(System.getProperty("user.dir"))

    val future = Future {
      val externalTools = ExternalTools.init(logger, ExternalToolsConfig.default)

      try {
        val fetchFutures = uniqueSourceNames.toList.sorted.map { sourceName =>
          Future {
            updateSourceStatus(sourceName, SourceFetchStatus.Fetching, "Starting...")

            parsedSources.get(sourceName).foreach {
              case ParsedSource.Database(dbConfig) =>
                Generate.fetchDatabaseSource(
                  sourceName,
                  dbConfig,
                  buildDir,
                  logger,
                  externalTools,
                  step => updateSourceStatus(sourceName, SourceFetchStatus.Fetching, step)
                ) match {
                  case Right(fetched) =>
                    fetchedSources.put(sourceName, fetched)
                    val warnings = logger.getWarningCount(sourceName)
                    updateSourceStatus(
                      sourceName,
                      SourceFetchStatus.Done(
                        fetched.stats.tables,
                        fetched.stats.views,
                        fetched.stats.enums,
                        warnings
                      ),
                      "Done"
                    )
                  case Left(error) =>
                    updateSourceStatus(sourceName, SourceFetchStatus.Failed(error), error)
                }

              case ParsedSource.DuckDb(duckConfig) =>
                Generate.fetchDuckDbSource(
                  sourceName,
                  duckConfig,
                  buildDir,
                  logger,
                  externalTools,
                  step => updateSourceStatus(sourceName, SourceFetchStatus.Fetching, step)
                ) match {
                  case Right(fetched) =>
                    fetchedSources.put(sourceName, fetched)
                    val warnings = logger.getWarningCount(sourceName)
                    updateSourceStatus(
                      sourceName,
                      SourceFetchStatus.Done(
                        fetched.stats.tables,
                        fetched.stats.views,
                        fetched.stats.enums,
                        warnings
                      ),
                      "Done"
                    )
                  case Left(error) =>
                    updateSourceStatus(sourceName, SourceFetchStatus.Failed(error), error)
                }

              case ParsedSource.OpenApi(_) | ParsedSource.JsonSchema(_) =>
                updateSourceStatus(sourceName, SourceFetchStatus.Done(0, 0, 0, 0), "Skipped")
            }
          }
        }

        Await.result(Future.sequence(fetchFutures), Duration.Inf)

        generationPhase.set(GeneratingPhase.GeneratingCode)
        logger.setContext(None, "generate")

        val totalSuccessful = new AtomicInteger(0)
        val totalFailed = new AtomicInteger(0)
        val totalSkipped = new AtomicInteger(0)

        val codegenFutures = parsedOutputs.toList.map { case (outputName, outputConfig) =>
          Future {
            val matchedSourceNames = parsedSources.keys.filter(name => ConfigToOptions.matchesSource(name, outputConfig.sourcePatterns)).toList

            if (matchedSourceNames.isEmpty) {
              tracker.update(outputName, Generate.OutputStatus.Skipped)
              totalSkipped.incrementAndGet()
            } else {
              matchedSourceNames.foreach { sourceName =>
                Option(fetchedSources.get(sourceName)).foreach { fetched =>
                  logger.setContext(Some(outputName), "generate")
                  tracker.update(outputName, Generate.OutputStatus.Processing(sourceName, "Starting..."))

                  Generate.generateWithCachedSource(
                    outputName,
                    outputConfig,
                    fetched,
                    buildDir,
                    logger,
                    externalTools,
                    tracker,
                    step => tracker.update(outputName, Generate.OutputStatus.Processing(sourceName, step))
                  ) match {
                    case Right(_) =>
                      totalSuccessful.incrementAndGet()
                    case Left(_) =>
                      totalFailed.incrementAndGet()
                  }
                }
              }
            }
          }
        }

        Await.result(Future.sequence(codegenFutures), Duration.Inf)

        import scala.jdk.CollectionConverters.*
        fetchedSources.values().asScala.foreach { fetched =>
          try {
            fetched.dataSource.ds.asInstanceOf[com.zaxxer.hikari.HikariDataSource].close()
          } catch {
            case _: Exception =>
          }
        }

        val (written, _, _) = tracker.getTotalFiles
        generationResult.set(Some(Right((totalSuccessful.get(), totalFailed.get(), totalSkipped.get(), written))))
      } catch {
        case e: Exception =>
          generationResult.set(Some(Left(Option(e.getMessage).getOrElse(e.getClass.getSimpleName))))
      }
    }

    generationFuture.set(Some(future))

    val newGenState = GeneratingState(
      phase = GeneratingPhase.FetchingSources,
      sourceFetches = initialSourceFetches,
      tracker = Some(tracker),
      logger = Some(logger),
      startTime = System.currentTimeMillis(),
      endTime = None,
      successful = 0,
      failed = 0,
      skipped = 0,
      filesWritten = 0
    )
    state.copy(currentScreen = AppScreen.Generating(newGenState))
  }

  private def updateSourceStatus(name: String, status: SourceFetchStatus, step: String): Unit = {
    val current = sourceFetches.get()
    val now = System.currentTimeMillis()
    val updated = current.map { src =>
      if (src.name == name) {
        status match {
          case SourceFetchStatus.Fetching =>
            val start = src.startTime.getOrElse(now)
            src.copy(status = status, currentStep = step, startTime = Some(start))
          case _: SourceFetchStatus.Done | _: SourceFetchStatus.Failed =>
            src.copy(status = status, currentStep = step, endTime = Some(now))
          case _ =>
            src.copy(status = status, currentStep = step)
        }
      } else src
    }
    sourceFetches.set(updated)
  }

  private def renderScreen(f: Frame, state: TuiState): Unit = {
    val showStatusPanel = StatusPanel.shouldShow(state.sourceStatuses)

    val (mainArea, statusArea) = if (showStatusPanel) {
      val chunks = Layout(
        direction = Direction.Vertical,
        constraints = Array(
          Constraint.Min(0),
          Constraint.Length(5)
        )
      ).split(f.size)
      (chunks(0), Some(chunks(1)))
    } else {
      (f.size, None)
    }

    // Check if we should render with sliding animation
    val animation = state.navigation.animation
    if (animation.isAnimating && state.previousScreen.isDefined) {
      renderWithSliding(f, mainArea, state, animation)
    } else {
      val mainFrame = Frame(
        buffer = f.buffer,
        size = mainArea,
        cursorPosition = f.cursorPosition
      )
      renderSingleScreen(mainFrame, state, state.currentScreen)
    }

    statusArea.foreach { area =>
      StatusPanel.render(f, area, state.sourceStatuses)
    }

    state.statusMessage.foreach { case (msg, color) =>
      val msgArea = Rect(
        x = 1,
        y = mainArea.height - 2,
        width = mainArea.width - 2,
        height = 1
      )
      f.buffer.setString(msgArea.x, msgArea.y, msg, Style(fg = Some(color)))
    }

    // Render confirmation dialog overlay if present
    state.navigation.pendingConfirmation.foreach { dialog =>
      renderConfirmationDialog(f, f.size, dialog)
    }
  }

  private def renderWithSliding(f: Frame, area: Rect, state: TuiState, animation: NavSlideAnimation): Unit = {
    val stripWidth = 8
    val minRenderWidth = 30 // Minimum width to render a full screen
    val progress = animation.offset
    val isForward = animation.direction == SlideDirection.Forward

    val previousScreen = state.previousScreen.get
    val currentScreen = state.currentScreen

    if (isForward) {
      // Forward: previous compresses to strip on left, current slides in from right
      val prevWidth = math.max(stripWidth, ((1.0 - progress) * (area.width - stripWidth)).toInt + stripWidth)
      val currWidth = (area.width - prevWidth).toInt

      // Render compressed previous screen (strip or full)
      if (prevWidth > 0) {
        val prevArea = Rect(
          x = area.x,
          y = area.y,
          width = prevWidth.toShort,
          height = area.height
        )
        val prevFrame = Frame(buffer = f.buffer, size = prevArea, cursorPosition = f.cursorPosition)
        if (prevWidth < minRenderWidth) {
          renderStrip(prevFrame, prevArea, previousScreen)
        } else {
          renderSingleScreen(prevFrame, state, previousScreen)
        }
      }

      // Render current screen sliding in
      if (currWidth >= minRenderWidth) {
        val currArea = Rect(
          x = (area.x + prevWidth).toShort,
          y = area.y,
          width = currWidth.toShort,
          height = area.height
        )
        val currFrame = Frame(buffer = f.buffer, size = currArea, cursorPosition = f.cursorPosition)
        renderSingleScreen(currFrame, state, currentScreen)
      } else if (currWidth > 0) {
        // Just render a placeholder strip for narrow current screen
        val currArea = Rect(
          x = (area.x + prevWidth).toShort,
          y = area.y,
          width = currWidth.toShort,
          height = area.height
        )
        val currFrame = Frame(buffer = f.buffer, size = currArea, cursorPosition = f.cursorPosition)
        renderStrip(currFrame, currArea, currentScreen)
      }
    } else {
      // Backward: current compresses to strip on right, previous expands from left
      val currWidth = math.max(stripWidth, ((1.0 - progress) * (area.width - stripWidth)).toInt + stripWidth)
      val prevWidth = (area.width - currWidth).toInt

      // Render previous screen expanding (this is the screen we're going TO)
      if (prevWidth >= minRenderWidth) {
        val prevArea = Rect(
          x = area.x,
          y = area.y,
          width = prevWidth.toShort,
          height = area.height
        )
        val prevFrame = Frame(buffer = f.buffer, size = prevArea, cursorPosition = f.cursorPosition)
        renderSingleScreen(prevFrame, state, currentScreen)
      } else if (prevWidth > 0) {
        val prevArea = Rect(
          x = area.x,
          y = area.y,
          width = prevWidth.toShort,
          height = area.height
        )
        val prevFrame = Frame(buffer = f.buffer, size = prevArea, cursorPosition = f.cursorPosition)
        renderStrip(prevFrame, prevArea, currentScreen)
      }

      // Render current screen compressing to strip (this is the screen we're leaving)
      if (currWidth > 0) {
        val currArea = Rect(
          x = (area.x + prevWidth).toShort,
          y = area.y,
          width = currWidth.toShort,
          height = area.height
        )
        val currFrame = Frame(buffer = f.buffer, size = currArea, cursorPosition = f.cursorPosition)
        if (currWidth < minRenderWidth) {
          renderStrip(currFrame, currArea, previousScreen)
        } else {
          renderSingleScreen(currFrame, state, previousScreen)
        }
      }
    }
  }

  private def renderStrip(f: Frame, area: Rect, screen: AppScreen): Unit = {
    val title = screenTitle(screen)
    val block = BlockWidget(
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    f.renderWidget(block, area)

    // Render vertical title
    val innerY = area.y + 1
    val maxChars = math.min(title.length, area.height.toInt - 2)
    for (i <- 0 until maxChars) {
      f.buffer.setString(
        area.x + area.width / 2,
        innerY + i,
        title(i).toString,
        Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)
      )
    }
  }

  private def screenTitle(screen: AppScreen): String = screen match {
    case _: AppScreen.Splash              => ""
    case _: AppScreen.MainMenu            => "Menu"
    case _: AppScreen.SourceList          => "Sources"
    case _: AppScreen.OutputList          => "Outputs"
    case _: AppScreen.TypeList            => "Bridge Types"
    case _: AppScreen.SourceWizard        => "Add Source"
    case _: AppScreen.OutputWizard        => "Add Output"
    case _: AppScreen.TypeWizard          => "Add Type"
    case _: AppScreen.CompositeWizard     => "Add Composite"
    case _: AppScreen.DomainTypeBuilder   => "Add Domain Type"
    case _: AppScreen.SourceEditor        => "Edit Source"
    case _: AppScreen.OutputEditor        => "Edit Output"
    case _: AppScreen.TypeEditor          => "Edit Type"
    case _: AppScreen.SchemaBrowser       => "Browser"
    case _: AppScreen.SpecBrowser         => "Spec"
    case _: AppScreen.BrowserSourcePicker => "Sources"
    case _: AppScreen.ConnectionTest      => "Test"
    case _: AppScreen.Generating          => "Generate"
    case _: AppScreen.SourceView          => "View"
    case _: AppScreen.SlidingDemo         => "Demo"
  }

  /** Shared callbacks instance for React components */
  private lazy val reactCallbacks: GlobalCallbacks = createCallbacks()

  private def renderSingleScreen(f: Frame, state: TuiState, screen: AppScreen): Unit = {
    val globalState = toGlobalState(state)

    screen match {
      case _: AppScreen.MainMenu =>
        val props = MainMenuReact.Props(globalState, reactCallbacks)
        ReactIntegration.renderIn(f, f.size, MainMenuReact.component(props))

      case list: AppScreen.SourceList =>
        val props = SourceListReact.Props(globalState, reactCallbacks, list.testResults)
        ReactIntegration.renderIn(f, f.size, SourceListReact.component(props))

      case wizard: AppScreen.SourceWizard =>
        val props = SourceWizardReact.Props(globalState, reactCallbacks, wizard.state)
        ReactIntegration.renderIn(f, f.size, SourceWizardReact.component(props))

      case _: AppScreen.OutputList =>
        val props = OutputListReact.Props(globalState, reactCallbacks)
        ReactIntegration.renderIn(f, f.size, OutputListReact.component(props))

      case wizard: AppScreen.OutputWizard =>
        OutputWizard.render(f, state, wizard)

      case test: AppScreen.ConnectionTest =>
        ConnectionTest.render(f, state, test)

      case gen: AppScreen.Generating =>
        GeneratingScreen.render(f, state, gen)

      case editor: AppScreen.SourceEditor =>
        val props = SourceEditorReact.Props(globalState, reactCallbacks, editor.sourceName, editor.state)
        ReactIntegration.renderIn(f, f.size, SourceEditorReact.component(props))

      case editor: AppScreen.OutputEditor =>
        val props = OutputEditorReact.Props(globalState, reactCallbacks, editor.outputName, editor.state)
        ReactIntegration.renderIn(f, f.size, OutputEditorReact.component(props))

      case list: AppScreen.TypeList =>
        val props = TypeListReact.Props(globalState, reactCallbacks, list.typeKindFilter)
        ReactIntegration.renderIn(f, f.size, TypeListReact.component(props))

      case wizard: AppScreen.TypeWizard =>
        val props = TypeWizardReact.Props(globalState, reactCallbacks, wizard.state)
        ReactIntegration.renderIn(f, f.size, TypeWizardReact.component(props))

      case wizard: AppScreen.CompositeWizard =>
        val props = CompositeWizardReact.Props(globalState, reactCallbacks, wizard.state)
        ReactIntegration.renderIn(f, f.size, CompositeWizardReact.component(props))

      case builder: AppScreen.DomainTypeBuilder =>
        val props = DomainTypeBuilderReact.Props(globalState, reactCallbacks, builder.state)
        ReactIntegration.renderIn(f, f.size, DomainTypeBuilderReact.component(props))

      case editor: AppScreen.TypeEditor =>
        val props = TypeEditorReact.Props(globalState, reactCallbacks, editor.typeName, editor.state)
        ReactIntegration.renderIn(f, f.size, TypeEditorReact.component(props))

      case editor: AppScreen.CompositeEditor =>
        val props = CompositeEditorReact.Props(globalState, reactCallbacks, editor.typeName, editor.state)
        ReactIntegration.renderIn(f, f.size, CompositeEditorReact.component(props))

      case browser: AppScreen.SchemaBrowser =>
        if (browser.state.searchState.active || browser.state.slideAnimation.animating) {
          SchemaBrowser.render(f, state, browser)
        } else {
          val props = SchemaBrowserReact.Props(globalState, reactCallbacks, browser.state)
          ReactIntegration.renderIn(f, f.size, SchemaBrowserReact.component(props))
        }

      case browser: AppScreen.SpecBrowser =>
        if (browser.state.searchState.active) {
          SpecBrowser.render(f, state, browser)
        } else {
          val props = SpecBrowserReact.Props(globalState, reactCallbacks, browser.state)
          ReactIntegration.renderIn(f, f.size, SpecBrowserReact.component(props))
        }

      case view: AppScreen.SourceView =>
        val props = SourceViewReact.Props(globalState, reactCallbacks, view.state.sourceName, view.state.editorState)
        ReactIntegration.renderIn(f, f.size, SourceViewReact.component(props))

      case _: AppScreen.BrowserSourcePicker =>
        val props = BrowserSourcePickerReact.Props(globalState, reactCallbacks)
        ReactIntegration.renderIn(f, f.size, BrowserSourcePickerReact.component(props))

      case demo: AppScreen.SlidingDemo =>
        SlidingDemo.render(f, state, demo)

      case splash: AppScreen.Splash =>
        val props = SplashScreenReact.Props(globalState, reactCallbacks, splash.state)
        ReactIntegration.renderIn(f, f.size, SplashScreenReact.component(props))
    }
  }

  private def renderConfirmationDialog(f: Frame, area: Rect, dialog: ConfirmationDialog): Unit = {
    import tui.react.Elements.{UnsavedChangesDialog, ExitConfirmDialog}

    dialog match {
      case unsaved: ConfirmationDialog.UnsavedChanges =>
        val component = UnsavedChangesDialog(
          focusedButton = unsaved.focusedButton,
          onSave = () => reactCallbacks.dialogSave(),
          onDiscard = () => reactCallbacks.dialogDiscard(),
          onCancel = () => reactCallbacks.dialogCancel(),
          onSetFocus = idx => reactCallbacks.dialogSetFocus(idx)
        )
        ReactIntegration.renderIn(f, area, component)
      case confirm: ConfirmationDialog.ConfirmExit =>
        val component = ExitConfirmDialog(
          focusedButton = confirm.focusedButton,
          onYes = () => reactCallbacks.dialogYes(),
          onNo = () => reactCallbacks.dialogNo(),
          onSetFocus = idx => reactCallbacks.dialogSetFocus(idx)
        )
        ReactIntegration.renderIn(f, area, component)
    }
  }

  private def computeDomainTypeSuggestions(state: TuiState): List[typr.bridge.CompositeTypeSuggester.CompositeSuggestion] = {
    import typr.bridge.CompositeTypeSuggester

    val existingTypes = state.config.types.getOrElse(Map.empty).keySet

    val metaDbs: Map[String, typr.MetaDb] = state.sourceStatuses.collect { case (name, ready: SourceStatus.Ready) =>
      name.value -> ready.metaDb
    }

    if (metaDbs.isEmpty) {
      Nil
    } else {
      CompositeTypeSuggester.suggest(metaDbs, existingTypes, limit = Int.MaxValue)
    }
  }

  /** Validate aligned sources by computing field mappings from loaded source data */
  private def validateAlignedSources(
      builderState: DomainTypeBuilderState,
      sourceStatuses: Map[SourceName, SourceStatus]
  ): DomainTypeBuilderState = {
    val primaryFields = builderState.primaryFields.filter(_.included)

    val validatedAlignedSources = builderState.alignedSources.map { alignedSource =>
      sourceStatuses.get(SourceName(alignedSource.sourceName)) match {
        case Some(SourceStatus.Ready(metaDb, _, _, _, _, _)) =>
          val alignedFields = getFieldsFromEntity(metaDb, alignedSource.entityPath)
          val fieldMappings = computeFieldMappings(primaryFields, alignedFields)
          alignedSource.copy(fieldMappings = fieldMappings)
        case _ =>
          alignedSource
      }
    }

    builderState.copy(alignedSources = validatedAlignedSources)
  }

  /** Get fields from an entity in a MetaDb */
  private def getFieldsFromEntity(metaDb: MetaDb, entityPath: String): List[(String, String, Boolean)] = {
    val (schema, name) = entityPath.split("\\.").toList match {
      case s :: n :: Nil => (Some(s), n)
      case n :: Nil      => (None, n)
      case _             => (None, entityPath)
    }
    val relName = typr.db.RelationName(schema, name)

    metaDb.relations.get(relName).map(_.forceGet).toList.flatMap {
      case table: typr.db.Table =>
        table.cols.toList.map { col =>
          (col.name.value, col.tpe.toString.split("\\.").last, col.nullability != typr.Nullability.NoNulls)
        }
      case view: typr.db.View =>
        view.cols.toList.map { case (col, _) =>
          (col.name.value, col.tpe.toString.split("\\.").last, col.nullability != typr.Nullability.NoNulls)
        }
    }
  }

  /** Compute field mappings between primary fields and aligned source fields */
  private def computeFieldMappings(
      primaryFields: List[PrimaryFieldState],
      alignedFields: List[(String, String, Boolean)]
  ): List[FieldMapping] = {
    val alignedFieldMap = alignedFields.map { case (name, tpe, nullable) => name.toLowerCase -> (name, tpe, nullable) }.toMap

    val mappingsFromPrimary = primaryFields.map { pf =>
      alignedFieldMap.get(pf.canonicalName.toLowerCase) match {
        case Some((sourceName, sourceType, _)) =>
          FieldMapping(
            canonicalField = pf.canonicalName,
            sourceField = Some(sourceName),
            sourceType = Some(sourceType),
            status = MappingStatus.Matched,
            comment = if (sourceName == pf.canonicalName) "exact match" else s"matched: $sourceName"
          )
        case None =>
          FieldMapping(
            canonicalField = pf.canonicalName,
            sourceField = None,
            sourceType = None,
            status = MappingStatus.Missing,
            comment = "not found in source"
          )
      }
    }

    val matchedSourceFields = mappingsFromPrimary.flatMap(_.sourceField).map(_.toLowerCase).toSet
    val extraFields = alignedFields.filterNot { case (name, _, _) => matchedSourceFields.contains(name.toLowerCase) }
    val extraMappings = extraFields.map { case (name, tpe, _) =>
      FieldMapping(
        canonicalField = "",
        sourceField = Some(name),
        sourceType = Some(tpe),
        status = MappingStatus.Extra,
        comment = "extra field in source"
      )
    }

    mappingsFromPrimary ++ extraMappings
  }

}

package typr.cli.tui

import typr.cli.config.TyprConfig
import java.nio.file.Path

/** Minimal global state for the TUI application.
  *
  * This contains only data that truly needs to be shared across all components:
  *   - Configuration (immutable)
  *   - Source loading status (fetched from databases)
  *   - Navigation state (history and transitions)
  *   - App lifecycle (unsaved changes, exit flag)
  *
  * Screen-specific state (selectedIndex, scroll positions, form fields) should use component-local useState instead.
  */
case class GlobalState(
    config: TyprConfig,
    configPath: Path,
    sourceStatuses: Map[SourceName, SourceStatus],
    navigation: NavigationState,
    currentLocation: Location,
    hasUnsavedChanges: Boolean,
    shouldExit: Boolean,
    statusMessage: Option[(String, tui.Color)],
    terminalSize: Option[(Int, Int)],
    hoverPosition: Option[(Int, Int)]
) {
  def tick: GlobalState = {
    if (navigation.animation.isAnimating)
      copy(navigation = navigation.tick)
    else this
  }

  def updateSourceStatus(name: SourceName, status: SourceStatus): GlobalState =
    copy(sourceStatuses = sourceStatuses + (name -> status))

  def markDirty: GlobalState = copy(hasUnsavedChanges = true)
  def markClean: GlobalState = copy(hasUnsavedChanges = false)
  def requestExit: GlobalState = copy(shouldExit = true)

  def withStatusMessage(msg: String, color: tui.Color): GlobalState =
    copy(statusMessage = Some((msg, color)))

  def clearStatusMessage: GlobalState = copy(statusMessage = None)

  def canGoBack: Boolean = navigation.history.nonEmpty
}

object GlobalState {
  def initial(config: TyprConfig, configPath: Path): GlobalState = GlobalState(
    config = config,
    configPath = configPath,
    sourceStatuses = Map.empty,
    navigation = NavigationState.initial,
    currentLocation = Location.MainMenu,
    hasUnsavedChanges = false,
    shouldExit = false,
    statusMessage = None,
    terminalSize = None,
    hoverPosition = None
  )
}

/** Callbacks for components to interact with global state.
  *
  * Components receive this trait to trigger navigation and state updates without having direct access to the mutable state.
  */
trait GlobalCallbacks {
  def navigateTo(location: Location): Unit
  def goBack(): Unit
  def requestExit(): Unit
  def updateConfig(f: TyprConfig => TyprConfig): Unit
  def setStatusMessage(msg: String, color: tui.Color): Unit
  def clearStatusMessage(): Unit
  def updateOutputEditor(f: OutputEditorState => OutputEditorState): Unit
  def updateSourceEditor(f: SourceEditorState => SourceEditorState): Unit
  def saveSourceEditor(): Unit
  def testSourceConnection(): Unit
  def updateSourceWizard(f: SourceWizardState => SourceWizardState): Unit
  def saveSourceWizard(): Unit
  def dialogSave(): Unit
  def dialogDiscard(): Unit
  def dialogCancel(): Unit
  def dialogYes(): Unit
  def dialogNo(): Unit
  def dialogSetFocus(index: Int): Unit
}

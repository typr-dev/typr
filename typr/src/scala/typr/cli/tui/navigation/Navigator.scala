package typr.cli.tui.navigation

import typr.cli.tui.*

/** Centralized navigation logic for the TUI */
object Navigator {

  /** Navigate to a new location with explicit screen transition. This is the preferred method - handles both screen and location in one call.
    */
  def navigateTo(state: TuiState, location: Location, newScreen: AppScreen): TuiState = {
    if (state.navigation.animation.isAnimating) state
    else {
      state.copy(
        currentScreen = newScreen,
        previousScreen = Some(state.currentScreen),
        navigation = state.navigation.goTo(location, state.currentScreen)
      )
    }
  }

  /** Go back to previous location in history */
  def goBack(state: TuiState): TuiState = {
    state.navigation.goBack match {
      case Some((newNav, restoredScreen)) =>
        // Save current screen as previous for backward animation, restore previous screen
        state.copy(
          currentScreen = restoredScreen,
          previousScreen = Some(state.currentScreen),
          navigation = newNav
        )
      case None =>
        // At root (MainMenu), handle exit
        if (state.hasUnsavedChanges) {
          showUnsavedChangesDialog(state)
        } else {
          state.copy(shouldExit = true)
        }
    }
  }

  /** Navigate to a new location (legacy - prefer navigateTo). Note: This doesn't properly save the screen for back navigation.
    */
  def goTo(state: TuiState, location: Location): TuiState = {
    if (state.navigation.animation.isAnimating) state
    else {
      state.copy(
        navigation = state.navigation.goTo(location, state.currentScreen)
      )
    }
  }

  /** Replace current location without adding to history (for wizard steps) */
  def replaceTop(state: TuiState, location: Location): TuiState = {
    state.copy(navigation = state.navigation.replaceTop(location))
  }

  /** Handle Escape key - go back or show exit dialog */
  def handleEscape(state: TuiState): TuiState = {
    state.navigation.current match {
      case Location.MainMenu =>
        if (state.hasUnsavedChanges) {
          showUnsavedChangesDialog(state)
        } else {
          state.copy(shouldExit = true)
        }
      case _ =>
        goBack(state)
    }
  }

  /** Show unsaved changes confirmation dialog */
  def showUnsavedChangesDialog(state: TuiState): TuiState = {
    state.copy(
      navigation = state.navigation.showConfirmation(ConfirmationDialog.UnsavedChanges(focusedButton = 0))
    )
  }

  /** Dismiss any pending confirmation dialog */
  def dismissDialog(state: TuiState): TuiState = {
    state.copy(navigation = state.navigation.dismissConfirmation)
  }

  /** Handle Tab key - cycle focus forward */
  def handleTab(state: TuiState): TuiState = {
    state.copy(navigation = state.navigation.copy(focus = state.navigation.focus.tabNext))
  }

  /** Handle Shift+Tab - cycle focus backward */
  def handleBackTab(state: TuiState): TuiState = {
    state.copy(navigation = state.navigation.copy(focus = state.navigation.focus.tabPrev))
  }

  /** Update focus order for current screen */
  def setFocusOrder(state: TuiState, order: List[FocusableId]): TuiState = {
    val newFocus = FocusState.withOrder(order)
    state.copy(navigation = state.navigation.copy(focus = newFocus))
  }

  /** Set focus to specific element */
  def setFocus(state: TuiState, id: FocusableId): TuiState = {
    state.copy(navigation = state.navigation.copy(focus = state.navigation.focus.setFocus(id)))
  }

  /** Tick the animation */
  def tick(state: TuiState): TuiState = {
    if (state.navigation.animation.isAnimating) {
      val newNav = state.navigation.tick
      // Clear previousScreen when animation completes
      if (!newNav.animation.isAnimating) {
        state.copy(navigation = newNav, previousScreen = None)
      } else {
        state.copy(navigation = newNav)
      }
    } else {
      state
    }
  }

  /** Update hover position */
  def updateHover(state: TuiState, col: Int, row: Int): TuiState = {
    state.copy(navigation = state.navigation.updateHover(col, row))
  }

  /** Check if we can go back */
  def canGoBack(state: TuiState): Boolean = state.navigation.canGoBack

  /** Check if animation is in progress */
  def isAnimating(state: TuiState): Boolean = state.navigation.animation.isAnimating

  /** Get current focused element */
  def focusedElement(state: TuiState): Option[FocusableId] = state.navigation.focus.currentId

  /** Check if specific element is focused */
  def isFocused(state: TuiState, id: FocusableId): Boolean = state.navigation.focus.isFocused(id)
}

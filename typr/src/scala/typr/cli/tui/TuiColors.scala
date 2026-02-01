package typr.cli.tui

import tui.Color

/** Brand colors from typr.dev for consistent TUI styling.
  *
  * These colors work well on both light and dark terminal backgrounds.
  */
object TuiColors {

  // Primary purple palette
  val primary = Color.Rgb(139, 92, 246) // #8b5cf6 - main brand purple
  val primaryDark = Color.Rgb(124, 58, 237) // #7c3aed
  val primaryDarker = Color.Rgb(109, 40, 217) // #6d28d9
  val primaryDarkest = Color.Rgb(91, 33, 182) // #5b21b6
  val primaryLight = Color.Rgb(167, 139, 250) // #a78bfa
  val primaryLighter = Color.Rgb(196, 181, 253) // #c4b5fd
  val primaryLightest = Color.Rgb(221, 214, 254) // #ddd6fe

  // Accent emerald green
  val accent = Color.Rgb(16, 185, 129) // #10b981
  val accentLight = Color.Rgb(52, 211, 153) // #34d399
  val accentDark = Color.Rgb(5, 150, 105) // #059669

  // Grays for text and borders
  val gray900 = Color.Rgb(26, 26, 26) // #1a1a1a - darkest
  val gray800 = Color.Rgb(45, 45, 45) // #2d2d2d
  val gray700 = Color.Rgb(64, 64, 64) // #404040
  val gray600 = Color.Rgb(82, 82, 82) // #525252
  val gray500 = Color.Rgb(115, 115, 115) // #737373
  val gray400 = Color.Rgb(153, 153, 153) // #999999
  val gray300 = Color.Rgb(191, 191, 191) // #bfbfbf
  val gray200 = Color.Rgb(230, 230, 230) // #e6e6e6
  val gray100 = Color.Rgb(245, 245, 245) // #f5f5f5 - lightest

  // Semantic colors
  val success = accent
  val error = Color.Rgb(239, 68, 68) // #ef4444 red
  val warning = Color.Rgb(245, 158, 11) // #f59e0b amber
  val info = primaryLight

  // UI element colors
  val border = Color.Rgb(64, 50, 100) // muted purple for borders
  val borderHighlight = primary
  val selection = primaryDark
  val selectionText = Color.Rgb(255, 255, 255) // white text on selection

  // Text colors
  val textPrimary = gray200 // light text for dark bg
  val textSecondary = gray400
  val textMuted = gray500
  val textHighlight = primaryLight
}

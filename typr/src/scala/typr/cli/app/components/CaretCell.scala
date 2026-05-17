package typr.cli.app.components

import jatatui.core.style.{Color, Style}
import jatatui.react.Components.*
import jatatui.react.{Element, MouseEvent}

/** Clickable caret prefix for an expandable tree row.
  *
  * Sits at the start of each row that can expand/collapse — 6 cells wide (" ▸ " or " ▾ ") to match the indentation the tree browsers use. Clicking the cell toggles the row's expansion immediately (no
  * double-click), without changing the SelectableList's selection cursor — `stopPropagation` keeps the click from bubbling to the row's outer click handler.
  *
  * Usage:
  * {{{
  *   row(
  *     length(CaretCell.WidthCols, CaretCell.of(isOpen, selected, () => toggleExpand(key))),
  *     fill(1, widget(restOfRowWidget))
  *   )
  * }}}
  */
object CaretCell {

  /** Width in cells, including leading indent + caret + trailing space. */
  val WidthCols: Int = 6

  def of(isOpen: Boolean, selected: Boolean, onToggle: () => Unit): Element =
    component { c =>
      c.onClick { (e: MouseEvent) =>
        onToggle()
        e.stopPropagation()
      }
      val sym = if (isOpen) "▾" else "▸"
      val st = Style.empty.withFg(if (selected) Color.CYAN else Color.DARK_GRAY)
      text(s"    $sym ", st)
    }
}

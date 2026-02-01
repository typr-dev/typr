package typr.cli.tui.navigation

import tui.*
import typr.cli.tui.*

/** Renders panes with sliding animation transitions.
  *
  * Each pane is defined by a render function that takes state and produces content. The renderer handles the animation, showing previous pane as a strip while the new pane slides in.
  */
object SlidingRenderer {

  private val stripWidth = 8

  /** Render with sliding animation between previous and current pane.
    *
    * @param frame
    *   The TUI frame to render to
    * @param animation
    *   Current animation state
    * @param previousRenderer
    *   Optional render function for previous pane (if animating backward)
    * @param currentRenderer
    *   Render function for current pane
    */
  def render(
      frame: Frame,
      animation: NavSlideAnimation,
      previousRenderer: Option[(Frame, Rect, Boolean) => Unit],
      currentRenderer: (Frame, Rect, Boolean) => Unit
  ): Unit = {
    val area = frame.size
    val totalWidth = area.width.toInt

    if (!animation.isAnimating) {
      currentRenderer(frame, area, false)
      return
    }

    val progress = animation.offset

    animation.direction match {
      case SlideDirection.Forward =>
        renderForwardTransition(frame, area, totalWidth, progress, previousRenderer, currentRenderer)
      case SlideDirection.Backward =>
        renderBackwardTransition(frame, area, totalWidth, progress, previousRenderer, currentRenderer)
    }
  }

  private def renderForwardTransition(
      frame: Frame,
      area: Rect,
      totalWidth: Int,
      progress: Double,
      previousRenderer: Option[(Frame, Rect, Boolean) => Unit],
      currentRenderer: (Frame, Rect, Boolean) => Unit
  ): Unit = {
    // Previous pane shrinks to strip on left, current slides in from right
    val prevWidth = math.max(stripWidth, ((1.0 - progress) * totalWidth).toInt)
    val currX = prevWidth
    val currWidth = totalWidth - currX

    if (prevWidth > 0) {
      val prevArea = Rect(area.x, area.y, prevWidth.toShort, area.height)
      val faded = progress > 0.3
      previousRenderer.foreach(_(frame, prevArea, faded))
    }

    if (currWidth > stripWidth) {
      val currArea = Rect((area.x + currX).toShort, area.y, currWidth.toShort, area.height)
      currentRenderer(frame, currArea, false)
    }
  }

  private def renderBackwardTransition(
      frame: Frame,
      area: Rect,
      totalWidth: Int,
      progress: Double,
      previousRenderer: Option[(Frame, Rect, Boolean) => Unit],
      currentRenderer: (Frame, Rect, Boolean) => Unit
  ): Unit = {
    // Current pane expands from strip on left, previous shrinks to right
    val currWidth = math.max(stripWidth, (progress * totalWidth).toInt)
    val prevX = currWidth
    val prevWidth = totalWidth - prevX

    if (currWidth > stripWidth) {
      val currArea = Rect(area.x, area.y, currWidth.toShort, area.height)
      currentRenderer(frame, currArea, false)
    }

    if (prevWidth > 0) {
      val prevArea = Rect((area.x + prevX).toShort, area.y, prevWidth.toShort, area.height)
      val faded = progress > 0.3
      previousRenderer.foreach(_(frame, prevArea, faded))
    }
  }

  /** Render a stack of panes with history visible as strips.
    *
    * Each pane in history is shown as a narrow strip on the left, with the current pane taking the remaining space.
    *
    * @param frame
    *   The TUI frame to render to
    * @param area
    *   The area to render in
    * @param historyRenderers
    *   Renderers for history panes (oldest first)
    * @param currentRenderer
    *   Renderer for current pane
    * @param animation
    *   Current animation state
    */
  def renderWithHistory(
      frame: Frame,
      area: Rect,
      historyRenderers: List[(Frame, Rect, Boolean) => Unit],
      currentRenderer: (Frame, Rect, Boolean) => Unit,
      animation: NavSlideAnimation
  ): Unit = {
    val totalWidth = area.width.toInt
    val visibleHistory = historyRenderers.take(4) // Max 4 visible strips
    val historyWidth = visibleHistory.length * stripWidth

    // Render history strips (faded)
    visibleHistory.zipWithIndex.foreach { case (renderer, idx) =>
      val stripX = idx * stripWidth
      if (stripX + stripWidth <= totalWidth) {
        val stripArea = Rect(
          (area.x + stripX).toShort,
          area.y,
          stripWidth.toShort,
          area.height
        )
        renderer(frame, stripArea, true) // Always faded
      }
    }

    // Render current pane in remaining space
    val currX = historyWidth
    val currWidth = totalWidth - currX

    if (currWidth > 0) {
      val currArea = Rect(
        (area.x + currX).toShort,
        area.y,
        currWidth.toShort,
        area.height
      )
      currentRenderer(frame, currArea, false)
    }
  }
}

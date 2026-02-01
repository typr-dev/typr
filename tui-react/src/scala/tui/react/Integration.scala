package tui.react

import tui.*

/** Integration helpers for migrating existing TUI code to tui-react.
  *
  * This provides a bridge between the traditional render pattern and tui-react components.
  */
object Integration {

  /** Shared state and event registry for the app */
  private val stateStore = new StateStore()
  private var currentRegistry: Option[EventRegistry] = None

  /** Get or create a ReactApp instance */
  def app: ReactApp = new ReactApp(stateStore)

  /** Render a component and store the event registry */
  def render(frame: Frame, component: Component): Unit = {
    val registry = new EventRegistry()
    val ctx = new RenderContext(frame, registry, stateStore)
    component.render(ctx, frame.size)
    currentRegistry = Some(registry)
  }

  /** Render a component in a specific area */
  def renderIn(frame: Frame, area: Rect, component: Component): Unit = {
    val registry = currentRegistry.getOrElse {
      val r = new EventRegistry()
      currentRegistry = Some(r)
      r
    }
    val ctx = new RenderContext(frame, registry, stateStore)
    component.render(ctx, area)
  }

  /** Handle a click event - returns true if handled */
  def handleClick(x: Int, y: Int): Boolean =
    currentRegistry.exists(_.dispatchClick(x, y))

  /** Handle a scroll up event - returns true if handled */
  def handleScrollUp(x: Int, y: Int): Boolean =
    currentRegistry.exists(_.dispatchScrollUp(x, y))

  /** Handle a scroll down event - returns true if handled */
  def handleScrollDown(x: Int, y: Int): Boolean =
    currentRegistry.exists(_.dispatchScrollDown(x, y))

  /** Handle a hover event - returns true if handled */
  def handleHover(x: Int, y: Int): Boolean =
    currentRegistry.exists(_.dispatchHover(x, y))

  /** Check if position is hovered (without triggering handler) */
  def isHovered(x: Int, y: Int): Option[Rect] =
    currentRegistry.flatMap(_.findHovered(x, y))

  /** Clear the event registry (call at start of each frame) */
  def clearRegistry(): Unit = {
    currentRegistry.foreach(_.clear())
  }

  /** Reset all state (useful for screen changes) */
  def resetState(): Unit = {
    stateStore.clear()
    currentRegistry = None
  }
}

/** Trait for screens that want to use tui-react for rendering */
trait ReactScreen {

  /** Return the component tree for this screen */
  def component(state: Any): Component

  /** Render using tui-react */
  def renderReact(frame: Frame, state: Any): Unit = {
    Integration.clearRegistry()
    Integration.render(frame, component(state))
  }
}

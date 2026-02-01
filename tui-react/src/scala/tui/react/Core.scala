package tui.react

import tui.*
import tui.widgets.*

import scala.collection.mutable

/** Event types that can be handled */
sealed trait EventKind
object EventKind {
  case object Click extends EventKind
  case object Hover extends EventKind
  case object ScrollUp extends EventKind
  case object ScrollDown extends EventKind
}

/** A registered event handler with its bounding area */
case class EventHandler(
    area: Rect,
    kind: EventKind,
    handler: () => Unit
)

/** Registry that accumulates event handlers during render */
class EventRegistry {
  private val handlers = mutable.ArrayBuffer[EventHandler]()

  def register(area: Rect, kind: EventKind, handler: () => Unit): Unit =
    handlers += EventHandler(area, kind, handler)

  def clear(): Unit = handlers.clear()

  /** Dispatch a click event - returns true if handled */
  def dispatchClick(x: Int, y: Int): Boolean =
    // Search in reverse order (last rendered = on top)
    handlers.reverseIterator
      .find { h =>
        h.kind == EventKind.Click &&
        x >= h.area.x && x < h.area.x + h.area.width &&
        y >= h.area.y && y < h.area.y + h.area.height
      }
      .map { h =>
        h.handler()
        true
      }
      .getOrElse(false)

  /** Dispatch hover event - returns true if handled */
  def dispatchHover(x: Int, y: Int): Boolean =
    handlers.reverseIterator
      .find { h =>
        h.kind == EventKind.Hover &&
        x >= h.area.x && x < h.area.x + h.area.width &&
        y >= h.area.y && y < h.area.y + h.area.height
      }
      .map { h =>
        h.handler()
        true
      }
      .getOrElse(false)

  /** Find hovered area (without triggering handler) */
  def findHovered(x: Int, y: Int): Option[Rect] =
    handlers.reverseIterator
      .find { h =>
        h.kind == EventKind.Hover &&
        x >= h.area.x && x < h.area.x + h.area.width &&
        y >= h.area.y && y < h.area.y + h.area.height
      }
      .map(_.area)

  /** Dispatch scroll up event - returns true if handled */
  def dispatchScrollUp(x: Int, y: Int): Boolean =
    handlers.reverseIterator
      .find { h =>
        h.kind == EventKind.ScrollUp &&
        x >= h.area.x && x < h.area.x + h.area.width &&
        y >= h.area.y && y < h.area.y + h.area.height
      }
      .map { h =>
        h.handler()
        true
      }
      .getOrElse(false)

  /** Dispatch scroll down event - returns true if handled */
  def dispatchScrollDown(x: Int, y: Int): Boolean =
    handlers.reverseIterator
      .find { h =>
        h.kind == EventKind.ScrollDown &&
        x >= h.area.x && x < h.area.x + h.area.width &&
        y >= h.area.y && y < h.area.y + h.area.height
      }
      .map { h =>
        h.handler()
        true
      }
      .getOrElse(false)

  def getHandlers: List[EventHandler] = handlers.toList
}

/** State store for useState hooks */
class StateStore {
  private val state = mutable.Map[String, Any]()
  private var dirty = false

  def get[T](key: String): Option[T] = state.get(key).map(_.asInstanceOf[T])

  def set[T](key: String, value: T): Unit = {
    val changed = state.get(key) != Some(value)
    state(key) = value
    if (changed) dirty = true
  }

  def isDirty: Boolean = dirty
  def clearDirty(): Unit = dirty = false
  def clear(): Unit = {
    state.clear()
    dirty = false
  }
}

/** Context passed during rendering - provides hooks and event registration */
class RenderContext(
    val frame: Frame,
    val registry: EventRegistry,
    val stateStore: StateStore
) {
  private val componentStack = mutable.Stack[String]()
  private var hookIndex = 0

  /** Push a component onto the stack (for hook identity) */
  def pushComponent(id: String): Unit = {
    componentStack.push(id)
    hookIndex = 0
  }

  /** Pop a component from the stack */
  def popComponent(): Unit = {
    val _ = componentStack.pop()
  }

  /** Get current component path (for state keys) */
  private def currentPath: String = componentStack.mkString("/")

  /** useState hook - returns current value and setter */
  def useState[T](initial: => T): (T, T => Unit) = {
    val key = s"$currentPath:$hookIndex"
    hookIndex += 1

    val value = stateStore.get[T](key).getOrElse {
      val v = initial
      stateStore.set(key, v)
      v
    }

    val setter = (newValue: T) => stateStore.set(key, newValue)

    (value, setter)
  }

  /** Register a click handler for an area */
  def onClick(area: Rect)(handler: => Unit): Unit =
    registry.register(area, EventKind.Click, () => handler)

  /** Register a hover zone for an area */
  def onHover(area: Rect)(handler: => Unit): Unit =
    registry.register(area, EventKind.Hover, () => handler)

  /** Register a scroll up handler for an area */
  def onScrollUp(area: Rect)(handler: => Unit): Unit =
    registry.register(area, EventKind.ScrollUp, () => handler)

  /** Register a scroll down handler for an area */
  def onScrollDown(area: Rect)(handler: => Unit): Unit =
    registry.register(area, EventKind.ScrollDown, () => handler)

  /** Convenience: render a ParagraphWidget and register click handler */
  def renderClickableParagraph(widget: tui.widgets.ParagraphWidget, area: Rect)(handler: => Unit): Unit = {
    frame.renderWidget(widget, area)
    onClick(area)(handler)
  }

  /** Convenience: render a BlockWidget and register click handler */
  def renderClickableBlock(widget: tui.widgets.BlockWidget, area: Rect)(handler: => Unit): Unit = {
    frame.renderWidget(widget, area)
    onClick(area)(handler)
  }

  /** Direct buffer access */
  def buffer: Buffer = frame.buffer
}

/** Base trait for React-style components */
trait Component {
  def render(ctx: RenderContext, area: Rect): Unit
}

/** Functional component - just a function */
object Component {

  /** Anonymous component (no hooks allowed) */
  def apply(f: (RenderContext, Rect) => Unit): Component = new Component {
    def render(ctx: RenderContext, area: Rect): Unit = f(ctx, area)
  }

  /** Named component with automatic push/pop - required for useState */
  def named(name: String)(f: (RenderContext, Rect) => Unit): Component = new Component {
    def render(ctx: RenderContext, area: Rect): Unit = {
      ctx.pushComponent(name)
      f(ctx, area)
      ctx.popComponent()
    }
  }

  /** Named component that only receives context (area passed separately) */
  def withCtx(name: String)(f: RenderContext => Component): Component = new Component {
    def render(ctx: RenderContext, area: Rect): Unit = {
      ctx.pushComponent(name)
      f(ctx).render(ctx, area)
      ctx.popComponent()
    }
  }
}

/** App runner that manages the render loop and event dispatch */
class ReactApp(stateStore: StateStore) {
  private val registry = new EventRegistry()

  /** Render a component tree, returning the event registry */
  def render(frame: Frame, root: Component): EventRegistry = {
    registry.clear()
    val ctx = new RenderContext(frame, registry, stateStore)
    root.render(ctx, frame.size)
    registry
  }

  /** Handle a click event - returns true if handled */
  def handleClick(x: Int, y: Int): Boolean =
    registry.dispatchClick(x, y)

  /** Handle a scroll up event - returns true if handled */
  def handleScrollUp(x: Int, y: Int): Boolean =
    registry.dispatchScrollUp(x, y)

  /** Handle a scroll down event - returns true if handled */
  def handleScrollDown(x: Int, y: Int): Boolean =
    registry.dispatchScrollDown(x, y)

  /** Check if a position is hovered */
  def findHovered(x: Int, y: Int): Option[Rect] =
    registry.findHovered(x, y)

  /** Check if state changed and needs re-render */
  def needsRerender: Boolean = stateStore.isDirty

  /** Clear the dirty flag after re-render */
  def clearDirty(): Unit = stateStore.clearDirty()
}

/** Extension methods for Component builders */
extension(comp: Component.type) {

  /** Create a component that receives a typed props object */
  def withProps[P](name: String)(f: (RenderContext, Rect, P) => Unit): P => Component =
    (props: P) =>
      new Component {
        def render(ctx: RenderContext, area: Rect): Unit = {
          ctx.pushComponent(name)
          f(ctx, area, props)
          ctx.popComponent()
        }
      }

  /** Create a stateful component with typed props */
  def stateful[P, S](name: String)(
      initial: P => S
  )(f: (RenderContext, Rect, P, S, S => Unit) => Unit): P => Component =
    (props: P) =>
      new Component {
        def render(ctx: RenderContext, area: Rect): Unit = {
          ctx.pushComponent(name)
          val (state, setState) = ctx.useState(initial(props))
          f(ctx, area, props, state, setState)
          ctx.popComponent()
        }
      }
}

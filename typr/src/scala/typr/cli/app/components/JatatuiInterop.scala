package typr.cli.app.components

import jatatui.components.link.Link as JLink
import jatatui.react.Element

import java.util.function.Function as JFunction

/** Scala-friendly wrappers around the Java-typed jatatui-components APIs whose signatures don't auto-convert cleanly from Scala lambdas (boxed-Boolean Function generics) or from bound `() => Unit`
  * values that don't trigger SAM conversion to `Runnable`.
  */
object Run {

  /** Lift a Scala `() => Unit` to a Java `Runnable`. Useful when passing a bound function value (not a fresh lambda) to a jatatui API that expects `Runnable`.
    */
  def apply(f: () => Unit): Runnable = new Runnable { def run(): Unit = f() }

  /** Implicit conversion so a bound `() => Unit` slots into a `Runnable` parameter without needing `Run(...)` at every call site. Fresh lambdas SAM-convert on their own; this only kicks in for
    * previously-bound `val`s.
    */
  given Conversion[() => Unit, Runnable] = f => new Runnable { def run(): Unit = f() }
}

/** Scala-friendly façade over [[jatatui.components.link.Link]]. Same shape, but takes Scala `Boolean => Element` content lambdas instead of `java.util.function.Function<Boolean, Element>`.
  */
object Link {

  /** Tab-focusable link. Activation = Enter when focused OR mouse click anywhere in the body's area. `content(focused)` receives the live focus state.
    */
  def focusable(autoFocus: Boolean, onActivate: Runnable, content: Boolean => Element): Element =
    JLink.focusable(
      autoFocus,
      onActivate,
      new JFunction[java.lang.Boolean, Element] {
        def apply(focused: java.lang.Boolean): Element = content(focused.booleanValue)
      }
    )

  /** Tab-focusable link with an explicit focus id (for imperative `ctx.focus(id)` / stable Tab order across reorders).
    */
  def focusable(
      focusId: String,
      autoFocus: Boolean,
      onActivate: Runnable,
      content: Boolean => Element
  ): Element =
    JLink.focusable(
      focusId,
      autoFocus,
      onActivate,
      new JFunction[java.lang.Boolean, Element] {
        def apply(focused: java.lang.Boolean): Element = content(focused.booleanValue)
      }
    )
}

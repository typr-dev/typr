package tui.react

import tui.*

/** Example showing how to use tui-react
  *
  * Usage:
  * {{{
  * import tui.react.*
  * import tui.react.Elements.*
  *
  * // Create app state
  * val stateStore = new StateStore()
  * val app = new ReactApp(stateStore)
  *
  * // Define your component
  * def myScreen(onSelect: String => Unit): Component = {
  *   Box(title = Some("My App"), borders = Borders.ALL)(
  *     Text("Welcome!", Style(fg = Some(Color.Cyan))),
  *     Spacer(),
  *     ListItem("Option 1", isSelected = true)(onSelect("option1")),
  *     ListItem("Option 2")(onSelect("option2")),
  *     ListItem("Option 3")(onSelect("option3"))
  *   )
  * }
  *
  * // In your render loop:
  * terminal.draw { frame =>
  *   app.render(frame, myScreen(selected => println(s"Selected: $selected")))
  * }
  *
  * // In your event loop:
  * case MouseEvent(x, y, MouseEventKind.Down) =>
  *   if (app.handleClick(x, y)) {
  *     // Click was handled by a component
  *   }
  * }}}
  */
object Example {

  /** Example: A simple counter with useState */
  def counter: Component = Component.named("counter") { (ctx, area) =>
    val (count, setCount) = ctx.useState(0)

    Elements
      .Column()(
        Elements.Text(s"Count: $count", Style(fg = Some(Color.Cyan))),
        Elements.Button(
          "[+] Increment",
          style = Style(fg = Some(Color.Green)),
          hoverStyle = Some(Style(fg = Some(Color.White), bg = Some(Color.Green)))
        )(setCount(count + 1)),
        Elements.Button(
          "[-] Decrement",
          style = Style(fg = Some(Color.Red)),
          hoverStyle = Some(Style(fg = Some(Color.White), bg = Some(Color.Red)))
        )(setCount(count - 1))
      )
      .render(ctx, area)
  }

  /** Example: A selectable list */
  def selectableList(items: List[String], onSelect: String => Unit): Component =
    Component.named("selectableList") { (ctx, area) =>
      val (selectedIndex, setSelected) = ctx.useState(0)

      Elements
        .Box(title = Some("Select an item"), borders = Borders.ALL)(
          items.zipWithIndex.map { case (item, idx) =>
            Elements.ListItem(
              item,
              isSelected = idx == selectedIndex
            ) {
              setSelected(idx)
              onSelect(item)
            }
          }*
        )
        .render(ctx, area)
    }

  /** Example: Combining multiple components */
  def dashboard(userName: String): Component = Component { (ctx, area) =>
    Elements
      .ColumnWithHeights(List(3, 5, 10))(
        // Header
        Elements.Box(title = Some("Dashboard"), borders = Borders.ALL)(
          Elements.Text(s"Welcome, $userName!", Style(fg = Some(Color.Yellow)))
        ),
        // Counter widget
        counter,
        // Menu
        selectableList(
          List("Settings", "Profile", "Help", "Exit"),
          item => println(s"Selected: $item")
        )
      )
      .render(ctx, area)
  }
}

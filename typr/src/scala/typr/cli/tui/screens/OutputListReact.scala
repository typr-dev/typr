package typr.cli.tui.screens

import tui.*
import tui.react.*
import tui.react.Elements.{AddCard, BackButton, Card, CardHeight, Hint, ScrollbarWithArrows}
import tui.widgets.*
import typr.cli.tui.GlobalCallbacks
import typr.cli.tui.GlobalState
import typr.cli.tui.Location
import typr.cli.tui.OutputWizardStep

/** React-based rendering for OutputList screen with card-based design. */
object OutputListReact {

  val AddButtonIndex: Int = -1

  case class Props(
      globalState: GlobalState,
      callbacks: GlobalCallbacks
  )

  def component(props: Props): Component = {
    val outputs = props.globalState.config.outputs.getOrElse(Map.empty).toList.sortBy(_._1)
    val hoverPos = props.globalState.hoverPosition

    Component.named("OutputList") { (ctx, area) =>
      val (scrollOffset, setScrollOffset) = ctx.useState(0)

      val onAddOutput = () => props.callbacks.navigateTo(Location.OutputWizard(OutputWizardStep.EnterName))
      val onEditOutput = (name: String) => props.callbacks.navigateTo(Location.OutputEditor(name))

      val chunks = Layout(
        direction = Direction.Vertical,
        margin = Margin(1),
        constraints = Array(Constraint.Length(3), Constraint.Min(10))
      ).split(area)

      BackButton("Outputs")(props.callbacks.goBack()).render(ctx, chunks(0))

      val contentArea = chunks(1)
      val innerX = (contentArea.x + 1).toShort
      val innerY = (contentArea.y + 1).toShort
      val innerWidth = math.max(20, contentArea.width - 2).toInt
      val innerHeight = math.max(5, contentArea.height - 3).toInt
      val maxX = innerX + innerWidth

      val block = BlockWidget(
        borders = Borders.ALL,
        borderType = BlockWidget.BorderType.Rounded,
        borderStyle = Style(fg = Some(Color.DarkGray))
      )
      ctx.frame.renderWidget(block, contentArea)

      var y = innerY.toInt

      // Calculate visible cards
      val visibleCards = math.max(1, (innerHeight - CardHeight - 1) / (CardHeight + 1))
      val totalItems = outputs.length
      val adjustedOffset = math.min(scrollOffset, math.max(0, totalItems - visibleCards))
      val visibleItems = outputs.slice(adjustedOffset, adjustedOffset + visibleCards)
      val hasScrollbar = totalItems > visibleCards
      val cardWidth = if (hasScrollbar) innerWidth - 2 else innerWidth

      // Add button card
      AddCard(
        title = "Add New Output",
        subtitle = "Configure code generation target",
        maxWidth = math.min(cardWidth, 60),
        hoverPosition = hoverPos
      )(onAddOutput()).render(ctx, Rect(innerX, y.toShort, cardWidth.toShort, CardHeight.toShort))
      y += CardHeight + 1

      // Output cards
      visibleItems.zipWithIndex.foreach { case ((name, output), _) =>
        if (y + CardHeight <= innerY + innerHeight) {
          val lang = output.language
          val path = output.path
          val sources = output.sources match {
            case Some(typr.config.generated.StringOrArrayString(s))  => s
            case Some(typr.config.generated.StringOrArrayArray(arr)) => arr.take(2).mkString(", ") + (if (arr.length > 2) "..." else "")
            case None                                                => "all sources"
          }

          val icon = lang.toLowerCase match {
            case "scala"  => "S"
            case "java"   => "J"
            case "kotlin" => "K"
            case _        => "◈"
          }
          val iconColor = lang.toLowerCase match {
            case "scala"  => Color.Red
            case "java"   => Color.Yellow
            case "kotlin" => Color.Magenta
            case _        => Color.Cyan
          }

          Card(
            icon = Some(icon),
            title = name,
            subtitle = s"$lang → $path • $sources",
            iconColor = Some(iconColor),
            maxWidth = math.min(cardWidth, 60),
            hoverPosition = hoverPos
          )(onEditOutput(name)).render(ctx, Rect(innerX, y.toShort, cardWidth.toShort, CardHeight.toShort))
          y += CardHeight + 1
        }
      }

      // Scrollbar
      if (hasScrollbar) {
        val scrollbarX = (contentArea.x + contentArea.width - 2).toShort
        val scrollbarY = (contentArea.y + 1).toShort
        val scrollbarHeight = contentArea.height - 3
        ScrollbarWithArrows(
          totalItems = totalItems,
          visibleItems = visibleCards,
          scrollOffset = adjustedOffset,
          trackColor = Color.DarkGray,
          thumbColor = Color.Gray,
          arrowColor = Color.Cyan,
          onScrollUp = Some(() => setScrollOffset(math.max(0, adjustedOffset - 1))),
          onScrollDown = Some(() => {
            val maxOffset = math.max(0, totalItems - visibleCards)
            setScrollOffset(math.min(maxOffset, adjustedOffset + 1))
          })
        ).render(ctx, Rect(scrollbarX, scrollbarY, 1, scrollbarHeight.toShort))
      }

      // Scroll handling
      ctx.onScrollUp(contentArea) {
        setScrollOffset(math.max(0, adjustedOffset - 1))
      }

      ctx.onScrollDown(contentArea) {
        val maxOffset = math.max(0, totalItems - visibleCards)
        setScrollOffset(math.min(maxOffset, adjustedOffset + 1))
      }

      // Hint
      val hintY = (contentArea.y + contentArea.height - 2).toShort
      Hint("Click to select • [Esc] Back").render(ctx, Rect((contentArea.x + 2).toShort, hintY, (contentArea.width - 4).toShort, 1))
    }
  }
}

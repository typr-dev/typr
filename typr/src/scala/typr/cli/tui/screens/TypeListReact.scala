package typr.cli.tui.screens

import tui.*
import tui.react.*
import tui.react.Elements.*
import tui.widgets.*
import typr.cli.commands.Interactive
import typr.cli.tui.AppScreen
import typr.cli.tui.BridgeTypeKind
import typr.cli.tui.GlobalCallbacks
import typr.cli.tui.GlobalState
import typr.cli.tui.Location
import typr.cli.tui.TypeWizardStep
import typr.config.generated.BridgeType
import typr.config.generated.DomainType
import typr.config.generated.FieldType

/** React-based TypeList with sleek card-based design */
object TypeListReact {

  val AddButtonIndex: Int = TypeList.AddButtonIndex

  case class Props(
      globalState: GlobalState,
      callbacks: GlobalCallbacks,
      typeKindFilter: Option[BridgeTypeKind]
  )

  def component(props: Props): Component = {
    val filtered = filteredTypes(props.globalState, props.typeKindFilter)
    val hoverPos = props.globalState.hoverPosition

    val typeLabel = props.typeKindFilter match {
      case Some(BridgeTypeKind.Scalar)    => "Field Types"
      case Some(BridgeTypeKind.Composite) => "Domain Types"
      case None                           => "Bridge Types"
    }

    Component.named("TypeList") { (ctx, area) =>
      val (scrollOffset, setScrollOffset) = ctx.useState(0)

      val onAddType = () =>
        props.typeKindFilter match {
          case Some(BridgeTypeKind.Composite) =>
            props.callbacks.navigateTo(Location.DomainTypeBuilder)
          case _ =>
            props.callbacks.navigateTo(
              Location.TypeWizard(TypeWizardStep.SelectFromSuggestions)
            )
        }

      // Edit should go to the appropriate editor based on type kind
      val onEditType = (name: String, bridgeType: BridgeType) =>
        bridgeType match {
          case _: DomainType => props.callbacks.navigateTo(Location.DomainTypeEditor(name))
          case _: FieldType  => props.callbacks.navigateTo(Location.TypeEditor(name))
        }

      val chunks = Layout(
        direction = Direction.Vertical,
        margin = Margin(1),
        constraints = Array(Constraint.Length(3), Constraint.Min(10))
      ).split(area)

      BackButton(typeLabel)(props.callbacks.goBack()).render(ctx, chunks(0))

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

      // Type cards
      val visibleCards = math.max(1, (innerHeight - CardHeight - 1) / (CardHeight + 1))
      val totalItems = filtered.length
      val adjustedOffset = math.min(scrollOffset, math.max(0, totalItems - visibleCards))
      val visibleItems = filtered.slice(adjustedOffset, adjustedOffset + visibleCards)
      val hasScrollbar = totalItems > visibleCards
      val cardWidth = if (hasScrollbar) innerWidth - 2 else innerWidth

      // Add button card using reusable AddCard component
      AddCard(
        title = "Add New Type",
        subtitle = "Create a new Bridge type",
        maxWidth = math.min(cardWidth, 60),
        hoverPosition = hoverPos
      )(onAddType()).render(ctx, Rect(innerX, y.toShort, cardWidth.toShort, CardHeight.toShort))
      y += CardHeight + 1

      visibleItems.zipWithIndex.foreach { case ((name, bridgeType), _) =>
        if (y + CardHeight <= innerY + innerHeight) {
          val (icon, kindColor, summary) = bridgeType match {
            case st: FieldType =>
              val patterns = st.db.flatMap(_.column).map(patternToString).getOrElse("no patterns")
              val pk = if (st.db.flatMap(_.primary_key).getOrElse(false)) " PK" else ""
              ("\u25c7", Color.Cyan, s"Field \u2022 $patterns$pk")
            case ct: DomainType =>
              val fieldCount = ct.fields.size
              val projCount = ct.alignedSources.map(_.size).getOrElse(0)
              ("\u25c8", Color.Magenta, s"Domain \u2022 $fieldCount fields, $projCount aligned sources")
          }

          Card(
            icon = Some(icon),
            title = name,
            subtitle = summary,
            iconColor = Some(kindColor),
            maxWidth = math.min(cardWidth, 60),
            hoverPosition = hoverPos
          )(onEditType(name, bridgeType)).render(ctx, Rect(innerX, y.toShort, cardWidth.toShort, CardHeight.toShort))
          y += CardHeight + 1
        }
      }

      // Show scrollbar if needed
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

  private def filteredTypes(state: GlobalState, filter: Option[BridgeTypeKind]): List[(String, BridgeType)] = {
    val types = state.config.types.getOrElse(Map.empty)
    val sorted = types.toList.sortBy(_._1)
    filter match {
      case Some(BridgeTypeKind.Scalar)    => sorted.filter(_._2.isInstanceOf[FieldType])
      case Some(BridgeTypeKind.Composite) => sorted.filter(_._2.isInstanceOf[DomainType])
      case None                           => sorted
    }
  }

  private def patternToString(pattern: typr.config.generated.StringOrArray): String = {
    import typr.config.generated.*
    pattern match {
      case StringOrArrayString(s)  => s
      case StringOrArrayArray(arr) => arr.take(2).mkString(", ") + (if (arr.length > 2) "..." else "")
    }
  }

  private def safeSetString(buffer: Buffer, x: Short, y: Short, maxX: Int, text: String, style: Style): Unit = {
    val availableWidth = maxX - x
    val bufferArea = buffer.area
    if (availableWidth > 0 && x >= 0 && y >= bufferArea.y && y < bufferArea.y + bufferArea.height) {
      buffer.setString(x, y, text.take(availableWidth), style)
    }
  }
}

package typr.cli.tui.screens

import tui.*
import tui.crossterm.KeyCode
import tui.widgets.*
import typr.cli.tui.AppScreen
import typr.cli.tui.BridgeTypeKind
import typr.cli.tui.FocusableId
import typr.cli.tui.Location
import typr.cli.tui.SourceStatus
import typr.cli.tui.TuiState
import typr.cli.tui.TypeEditorState
import typr.cli.tui.TypeWizardState
import typr.cli.tui.components.BackButton
import typr.cli.tui.navigation.Navigator
import typr.config.generated.BridgeType
import typr.config.generated.FieldType
import typr.config.generated.DomainType
import typr.bridge.ColumnGrouper
import typr.bridge.ColumnMember
import typr.bridge.TypeSuggester

object TypeList {

  val AddButtonIndex = -1

  def focusableElements(itemCount: Int): List[FocusableId] =
    FocusableId.ListItem(AddButtonIndex) :: (0 until itemCount).map(i => FocusableId.ListItem(i)).toList

  private def filteredTypes(state: TuiState, filter: Option[BridgeTypeKind]): List[(String, BridgeType)] = {
    val types = state.config.types.getOrElse(Map.empty)
    val sorted = types.toList.sortBy(_._1)
    filter match {
      case Some(BridgeTypeKind.Scalar)    => sorted.filter(_._2.isInstanceOf[FieldType])
      case Some(BridgeTypeKind.Composite) => sorted.filter(_._2.isInstanceOf[DomainType])
      case None                           => sorted
    }
  }

  def handleKey(state: TuiState, list: AppScreen.TypeList, keyCode: KeyCode): TuiState = {
    val filtered = filteredTypes(state, list.typeKindFilter)
    val typeNames = filtered.map(_._1)
    val maxListIndex = math.max(-1, typeNames.length - 1)

    keyCode match {
      case _: KeyCode.Up | _: KeyCode.BackTab =>
        val newIdx = math.max(AddButtonIndex, list.selectedIndex - 1)
        val newState = state.copy(currentScreen = list.copy(selectedIndex = newIdx))
        Navigator.setFocus(newState, FocusableId.ListItem(newIdx))

      case _: KeyCode.Down | _: KeyCode.Tab =>
        val newIdx = math.min(maxListIndex, list.selectedIndex + 1)
        val newState = state.copy(currentScreen = list.copy(selectedIndex = newIdx))
        Navigator.setFocus(newState, FocusableId.ListItem(newIdx))

      case _: KeyCode.Enter =>
        if (list.selectedIndex == AddButtonIndex) {
          navigateToAddWizard(state, list.typeKindFilter)
        } else if (typeNames.nonEmpty && list.selectedIndex >= 0 && list.selectedIndex < typeNames.length) {
          val typeName = typeNames(list.selectedIndex)
          filtered.find(_._1 == typeName).map(_._2) match {
            case Some(st: FieldType) =>
              val editorState = TypeEditorState.fromFieldType(typeName, st)
              Navigator.navigateTo(state, Location.TypeEditor(typeName), AppScreen.TypeEditor(typeName, editorState))
            case Some(_: DomainType) =>
              state
            case None =>
              state
          }
        } else {
          state
        }

      case c: KeyCode.Char if c.c() == 'a' || c.c() == 'n' =>
        navigateToAddWizard(state, list.typeKindFilter)

      case c: KeyCode.Char if c.c() == 'd' =>
        if (typeNames.nonEmpty && list.selectedIndex < typeNames.length) {
          val typeName = typeNames(list.selectedIndex)
          val types = state.config.types.getOrElse(Map.empty)
          val newTypes = types - typeName
          val newConfig = state.config.copy(types = if (newTypes.isEmpty) None else Some(newTypes))
          val newFiltered = filteredTypes(state.copy(config = newConfig), list.typeKindFilter)
          val newIdx = math.min(list.selectedIndex, math.max(0, newFiltered.size - 1))
          state.copy(
            config = newConfig,
            currentScreen = list.copy(selectedIndex = newIdx),
            hasUnsavedChanges = true,
            statusMessage = Some((s"Deleted type '$typeName'", Color.Yellow))
          )
        } else {
          state
        }

      case _: KeyCode.Esc =>
        Navigator.goBack(state)

      case c: KeyCode.Char if c.c() == 'q' =>
        Navigator.handleEscape(state)

      case _ => state
    }
  }

  private def navigateToAddWizard(state: TuiState, typeKindFilter: Option[BridgeTypeKind]): TuiState = {
    typeKindFilter match {
      case Some(BridgeTypeKind.Scalar) | None =>
        val suggestions = computeSuggestions(state)
        Navigator.navigateTo(
          state,
          Location.TypeWizard(typr.cli.tui.TypeWizardStep.SelectFromSuggestions),
          AppScreen.TypeWizard(TypeWizardState.initial(suggestions))
        )
      case Some(BridgeTypeKind.Composite) =>
        val compositeSuggestions = computeCompositeSuggestions(state)
        val wizardState = typr.cli.tui.CompositeWizardState.withSuggestions(compositeSuggestions)
        val startStep =
          if (compositeSuggestions.nonEmpty) typr.cli.tui.CompositeWizardStep.SelectSuggestion
          else typr.cli.tui.CompositeWizardStep.EnterName
        Navigator.navigateTo(
          state,
          Location.CompositeWizard(startStep),
          AppScreen.CompositeWizard(wizardState)
        )
    }
  }

  def handleMouse(state: TuiState, list: AppScreen.TypeList, col: Int, row: Int): TuiState = {
    val filtered = filteredTypes(state, list.typeKindFilter)
    val addButtonArea = (row >= 1 && row <= 4)

    if (addButtonArea && col >= 2 && col <= 24) {
      val newState = state.copy(currentScreen = list.copy(selectedIndex = AddButtonIndex))
      Navigator.setFocus(newState, FocusableId.ListItem(AddButtonIndex))
    } else if (row >= 6 && filtered.nonEmpty) {
      val listStartRow = 7
      val clickedIdx = row - listStartRow
      if (clickedIdx >= 0 && clickedIdx < filtered.length) {
        val newState = state.copy(currentScreen = list.copy(selectedIndex = clickedIdx))
        Navigator.setFocus(newState, FocusableId.ListItem(clickedIdx))
      } else {
        state
      }
    } else {
      state
    }
  }

  def render(f: Frame, state: TuiState, list: AppScreen.TypeList): Unit = {
    val filtered = filteredTypes(state, list.typeKindFilter)
    val hasItems = filtered.nonEmpty

    val typeLabel = list.typeKindFilter match {
      case Some(BridgeTypeKind.Scalar)    => "Field Types"
      case Some(BridgeTypeKind.Composite) => "Domain Types"
      case None                           => "Bridge Types"
    }

    val mainChunks = Layout(
      direction = Direction.Vertical,
      margin = Margin(1),
      constraints = Array(
        Constraint.Length(3),
        Constraint.Length(5),
        Constraint.Min(10)
      )
    ).split(f.size)

    val isBackHovered = state.hoverPosition.exists { case (col, row) =>
      BackButton.isClicked(col, row, 1)
    }
    BackButton.renderClickable(f, mainChunks(0), typeLabel, isBackHovered)

    val isAddButtonSelected = list.selectedIndex == AddButtonIndex
    renderAddButton(f, mainChunks(1), list.typeKindFilter, isAddButtonSelected)

    if (hasItems) {
      val contentChunks = Layout(
        direction = Direction.Horizontal,
        constraints = Array(Constraint.Percentage(50), Constraint.Percentage(50))
      ).split(mainChunks(2))

      renderTypeList(f, contentChunks(0), state, list)
      renderTypeDetails(f, contentChunks(1), state, list)
    } else {
      renderEmptyState(f, mainChunks(2), list.typeKindFilter)
    }
  }

  private def renderAddButton(f: Frame, area: Rect, filter: Option[BridgeTypeKind], isSelected: Boolean): Unit = {
    val (title, icon, description, baseColor) = filter match {
      case Some(BridgeTypeKind.Scalar) =>
        ("Field Types", "🔤", "Field-level type safety (e.g. Email, CustomerId)", Color.Cyan)
      case Some(BridgeTypeKind.Composite) =>
        ("Domain Types", "🧩", "Define once, use everywhere (e.g. Customer, Order)", Color.Magenta)
      case None =>
        ("Bridge Types", "📦", "All bridge types", Color.Yellow)
    }

    val borderColor = if (isSelected) Color.Cyan else baseColor
    val buttonBg = if (isSelected) Color.Blue else baseColor
    val buttonStyle = Style(fg = Some(Color.White), bg = Some(buttonBg), addModifier = Modifier.BOLD)
    val borderStyle = if (isSelected) {
      Style(fg = Some(borderColor), addModifier = Modifier.BOLD)
    } else {
      Style(fg = Some(borderColor))
    }

    val lines = List(
      Spans.from(
        Span.styled(s"  $icon  $title", Style(fg = Some(baseColor), addModifier = Modifier.BOLD)),
        Span.styled(s"  —  $description", Style(fg = Some(Color.DarkGray)))
      ),
      Spans.nostyle(""),
      Spans.from(
        Span.styled("  ╭─────────────────────╮", borderStyle),
        Span.nostyle("     "),
        Span.styled("[n/a] Add New   [d] Delete   [Esc] Back", Style(fg = Some(Color.DarkGray)))
      ),
      Spans.from(
        Span.styled("  │", borderStyle),
        Span.styled("   + Add New Type   ", buttonStyle),
        Span.styled("│", borderStyle)
      ),
      Spans.from(Span.styled("  ╰─────────────────────╯", borderStyle))
    )

    val text = Text.fromSpans(lines*)
    val para = ParagraphWidget(text = text)
    f.renderWidget(para, area)
  }

  private def renderEmptyState(f: Frame, area: Rect, filter: Option[BridgeTypeKind]): Unit = {
    val (kindName, instructions) = filter match {
      case Some(BridgeTypeKind.Scalar) =>
        (
          "Field Types",
          List(
            "No field types defined yet.",
            "",
            "Field types add type safety at the column level.",
            "Examples: Email, CustomerId, PhoneNumber",
            "",
            "Press [n] or select the Add button above to create one."
          )
        )
      case Some(BridgeTypeKind.Composite) =>
        (
          "Domain Types",
          List(
            "No domain types defined yet.",
            "",
            "Domain types define once, use everywhere.",
            "Examples: Customer, Order, Product",
            "",
            "Press [n] or select the Add button above to create one."
          )
        )
      case None =>
        (
          "Bridge Types",
          List(
            "No bridge types defined yet.",
            "",
            "Press [n] to add a new type."
          )
        )
    }

    val lines = List(
      Spans.from(Span.styled(kindName, Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))),
      Spans.nostyle("")
    ) ++ instructions.map(line => Spans.from(Span.styled(line, Style(fg = Some(Color.DarkGray)))))

    val text = Text.fromSpans(lines*)

    val block = BlockWidget(
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded,
      borderStyle = Style(fg = Some(Color.DarkGray))
    )

    val para = ParagraphWidget(block = Some(block), text = text)
    f.renderWidget(para, area)
  }

  private def renderTypeList(f: Frame, area: Rect, state: TuiState, list: AppScreen.TypeList): Unit = {
    val filtered = filteredTypes(state, list.typeKindFilter)
    val typeNames = filtered.map(_._1)

    val (listTitle, emptyMessage) = list.typeKindFilter match {
      case Some(BridgeTypeKind.Scalar)    => ("Field Types", "No field types defined")
      case Some(BridgeTypeKind.Composite) => ("Domain Types", "No domain types defined")
      case None                           => ("All Bridge Types", "No types defined")
    }

    val items = if (typeNames.isEmpty) {
      Array(
        ListWidget.Item(
          Text.from(Spans.from(Span.styled(emptyMessage, Style(fg = Some(Color.DarkGray)))))
        ),
        ListWidget.Item(
          Text.from(Spans.from(Span.styled("Press [n] to add a new type", Style(fg = Some(Color.DarkGray)))))
        )
      )
    } else {
      filtered.zipWithIndex.map { case ((name, typeDef), idx) =>
        val isSelected = idx == list.selectedIndex
        val style =
          if (isSelected) Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
          else Style(fg = Some(Color.Gray))

        val summary = getTypeSummary(typeDef)

        ListWidget.Item(
          Text.from(
            Spans.from(
              Span.styled(name, style),
              Span.nostyle("  "),
              Span.styled(summary, Style(fg = Some(Color.DarkGray)))
            )
          )
        )
      }.toArray
    }

    val listWidget = ListWidget(
      block = Some(
        BlockWidget(
          title = Some(Spans.from(Span.styled(listTitle, Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
          borders = Borders.ALL,
          borderType = BlockWidget.BorderType.Rounded
        )
      ),
      items = items,
      highlightStyle = Style(bg = Some(Color.Blue)),
      highlightSymbol = Some("> ")
    )

    val selectedListItem = if (typeNames.nonEmpty && list.selectedIndex >= 0 && list.selectedIndex < typeNames.length) {
      Some(list.selectedIndex)
    } else {
      None
    }
    val listState = ListWidget.State(offset = 0, selected = selectedListItem)
    f.renderStatefulWidget(listWidget, area)(listState)
  }

  private def renderTypeDetails(f: Frame, area: Rect, state: TuiState, list: AppScreen.TypeList): Unit = {
    val filtered = filteredTypes(state, list.typeKindFilter)

    val lines: List[Spans] = if (filtered.isEmpty || list.selectedIndex < 0 || list.selectedIndex >= filtered.length) {
      val (kindName, kindDesc) = list.typeKindFilter match {
        case Some(BridgeTypeKind.Scalar) =>
          (
            "Field Types",
            List(
              "Field-level type safety",
              "Pattern-matched across sources",
              "e.g. Email, CustomerId, PhoneNumber"
            )
          )
        case Some(BridgeTypeKind.Composite) =>
          (
            "Domain Types",
            List(
              "Define once, use everywhere",
              "Aligned to tables across sources",
              "e.g. Customer, Order, Product"
            )
          )
        case None =>
          (
            "Bridge Types",
            List(
              "Define unified types that work",
              "across all your data sources."
            )
          )
      }

      List(
        Spans.from(Span.styled(kindName, Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))),
        Spans.nostyle("")
      ) ++ kindDesc.map(d => Spans.from(Span.nostyle(d)))
    } else {
      val (typeName, bridgeType) = filtered(list.selectedIndex)

      val typeSpecificLines = bridgeType match {
        case st: FieldType =>
          renderFieldTypeDetails(st)
        case ct: DomainType =>
          renderDomainTypeDetails(ct)
      }

      List(
        Spans.from(Span.styled(s"Type: $typeName", Style(fg = Some(Color.Yellow), addModifier = Modifier.BOLD))),
        Spans.nostyle("")
      ) ++ typeSpecificLines ++ renderAlignment(typeName, bridgeType, state)
    }

    val text = Text.fromSpans(lines*)

    val para = ParagraphWidget(
      block = Some(
        BlockWidget(
          title = Some(Spans.from(Span.styled("Details", Style(fg = Some(Color.Cyan))))),
          borders = Borders.ALL,
          borderType = BlockWidget.BorderType.Rounded
        )
      ),
      text = text
    )
    f.renderWidget(para, area)
  }

  private def getTypeSummary(bridgeType: BridgeType): String = bridgeType match {
    case st: FieldType =>
      val parts = List(
        st.db.flatMap(_.column).map(c => patternToString(c)),
        st.db.flatMap(_.primary_key).filter(identity).map(_ => "PK")
      ).flatten
      if (parts.isEmpty) "(field, no patterns)"
      else parts.mkString(", ")
    case ct: DomainType =>
      val fieldCount = ct.fields.size
      val projCount = ct.projections.map(_.size).getOrElse(0)
      val projLabel = if (projCount == 1) "aligned source" else "aligned sources"
      val fieldLabel = if (fieldCount == 1) "field" else "fields"
      s"\u25A0 domain: $fieldCount $fieldLabel, $projCount $projLabel"
  }

  def renderFieldTypeDetails(st: FieldType): List[Spans] = {
    val dbLines = List(
      Spans.from(Span.styled("Database Match:", Style(fg = Some(Color.Cyan))))
    ) ++ (st.db match {
      case Some(db) =>
        List(
          db.column.map(c => Spans.from(Span.nostyle(s"  column: ${patternToString(c)}"))),
          db.schema.map(s => Spans.from(Span.nostyle(s"  schema: ${patternToString(s)}"))),
          db.table.map(t => Spans.from(Span.nostyle(s"  table: ${patternToString(t)}"))),
          db.db_type.map(t => Spans.from(Span.nostyle(s"  db_type: ${patternToString(t)}"))),
          db.primary_key.map(pk => Spans.from(Span.nostyle(s"  primary_key: $pk")))
        ).flatten match {
          case Nil   => List(Spans.from(Span.styled("  (no patterns)", Style(fg = Some(Color.DarkGray)))))
          case parts => parts
        }
      case None => List(Spans.from(Span.styled("  (not configured)", Style(fg = Some(Color.DarkGray)))))
    })

    val modelLines = List(
      Spans.nostyle(""),
      Spans.from(Span.styled("Model Match (OpenAPI):", Style(fg = Some(Color.Cyan))))
    ) ++ (st.model match {
      case Some(m) =>
        List(
          m.name.map(n => Spans.from(Span.nostyle(s"  name: ${patternToString(n)}"))),
          m.schema.map(s => Spans.from(Span.nostyle(s"  schema: ${patternToString(s)}"))),
          m.format.map(f => Spans.from(Span.nostyle(s"  format: ${patternToString(f)}")))
        ).flatten match {
          case Nil   => List(Spans.from(Span.styled("  (no patterns)", Style(fg = Some(Color.DarkGray)))))
          case parts => parts
        }
      case None => List(Spans.from(Span.styled("  (not configured)", Style(fg = Some(Color.DarkGray)))))
    })

    dbLines ++ modelLines
  }

  def renderDomainTypeDetails(ct: DomainType): List[Spans] = {
    val typeKindLine = List(
      Spans.from(
        Span.styled("Kind: ", Style(fg = Some(Color.Gray))),
        Span.styled("Domain (canonical type)", Style(fg = Some(Color.Magenta)))
      )
    )

    val fieldLines = List(
      Spans.nostyle(""),
      Spans.from(Span.styled(s"Fields (${ct.fields.size}):", Style(fg = Some(Color.Cyan))))
    ) ++ ct.fields.toList.take(6).map { case (name, fieldType) =>
      val typeName = fieldType match {
        case typr.config.generated.FieldSpecString(s) => s
        case typr.config.generated.FieldSpecObject(array, _, _, nullable, tpe) =>
          val nullSuffix = if (nullable.contains(true)) "?" else ""
          val arraySuffix = if (array.contains(true)) "[]" else ""
          s"$tpe$nullSuffix$arraySuffix"
      }
      Spans.from(
        Span.styled(s"  $name", Style(fg = Some(Color.White))),
        Span.styled(": ", Style(fg = Some(Color.DarkGray))),
        Span.styled(typeName, Style(fg = Some(Color.Yellow)))
      )
    } ++ (if (ct.fields.size > 6) List(Spans.from(Span.styled(s"  ... and ${ct.fields.size - 6} more", Style(fg = Some(Color.DarkGray))))) else Nil)

    val projCount = ct.projections.map(_.size).getOrElse(0)
    val projLines = List(
      Spans.nostyle(""),
      Spans.from(Span.styled(s"Aligned Sources ($projCount):", Style(fg = Some(Color.Cyan))))
    ) ++ ct.projections
      .map(_.toList.take(4).map { case (key, proj) =>
        val mode = proj.mode.getOrElse("superset")
        Spans.from(
          Span.styled(s"  $key", Style(fg = Some(Color.White))),
          Span.styled(s" ($mode)", Style(fg = Some(Color.DarkGray)))
        )
      })
      .getOrElse(List(Spans.from(Span.styled("  (none configured)", Style(fg = Some(Color.DarkGray))))))

    typeKindLine ++ fieldLines ++ projLines
  }

  private def patternToString(pattern: typr.config.generated.StringOrArray): String = {
    import typr.config.generated.*
    pattern match {
      case StringOrArrayString(s)  => s
      case StringOrArrayArray(arr) => arr.mkString(", ")
    }
  }

  def renderAlignment(typeName: String, bridgeType: BridgeType, state: TuiState): List[Spans] = bridgeType match {
    case ct: DomainType =>
      val projCount = ct.projections.map(_.size).getOrElse(0)
      List(
        Spans.nostyle(""),
        Spans.from(Span.styled("Aligned Sources:", Style(fg = Some(Color.Cyan)))),
        Spans.from(Span.styled(s"  $projCount sources configured", Style(fg = Some(Color.DarkGray))))
      )
    case st: FieldType =>
      renderFieldAlignment(typeName, st, state)
  }

  private def renderFieldAlignment(typeName: String, fieldType: FieldType, state: TuiState): List[Spans] = {
    val metaDbs: Map[String, typr.MetaDb] = state.sourceStatuses.collect { case (name, ready: SourceStatus.Ready) =>
      name.value -> ready.metaDb
    }

    if (metaDbs.isEmpty) {
      List(
        Spans.nostyle(""),
        Spans.from(Span.styled("Matches:", Style(fg = Some(Color.Cyan)))),
        Spans.from(Span.styled("  Loading sources...", Style(fg = Some(Color.DarkGray))))
      )
    } else {
      val columnPatterns = fieldType.db
        .flatMap(_.column)
        .map {
          case typr.config.generated.StringOrArrayString(s)  => List(s)
          case typr.config.generated.StringOrArrayArray(arr) => arr
        }
        .getOrElse(Nil)

      if (columnPatterns.isEmpty) {
        List(
          Spans.nostyle(""),
          Spans.from(Span.styled("Matches:", Style(fg = Some(Color.Cyan)))),
          Spans.from(Span.styled("  No column patterns", Style(fg = Some(Color.DarkGray))))
        )
      } else {
        val matchingColumns = findMatchingColumns(metaDbs, columnPatterns)

        if (matchingColumns.isEmpty) {
          List(
            Spans.nostyle(""),
            Spans.from(Span.styled("Matches:", Style(fg = Some(Color.Cyan)))),
            Spans.from(Span.styled("  No matches found", Style(fg = Some(Color.DarkGray))))
          )
        } else {
          val (compatible, incompatible) = TypeSuggester.partitionByTypeCompatibility(matchingColumns)

          val compatLines = if (compatible.nonEmpty) {
            List(
              Spans.from(
                Span.styled("  Compatible: ", Style(fg = Some(Color.Green))),
                Span.styled(s"${compatible.size}", Style(fg = Some(Color.White)))
              )
            ) ++ compatible.take(3).map { col =>
              Spans.from(Span.styled(s"    ${col.source}.${col.table}.${col.column}", Style(fg = Some(Color.DarkGray))))
            }
          } else Nil

          val incompatLines = if (incompatible.nonEmpty) {
            List(
              Spans.from(
                Span.styled("  Incompatible: ", Style(fg = Some(Color.Red))),
                Span.styled(s"${incompatible.size}", Style(fg = Some(Color.White)))
              )
            )
          } else Nil

          List(
            Spans.nostyle(""),
            Spans.from(Span.styled("Matches:", Style(fg = Some(Color.Cyan))))
          ) ++ compatLines ++ incompatLines
        }
      }
    }
  }

  private def findMatchingColumns(
      metaDbs: Map[String, typr.MetaDb],
      patterns: List[String]
  ): List[ColumnMember] = {
    val allColumns = metaDbs.flatMap { case (sourceName, metaDb) =>
      ColumnGrouper.extractColumns(sourceName, metaDb)
    }.toList

    val (inclusions, exclusions) = patterns.partition(!_.startsWith("!"))
    val exclusionPatterns = exclusions.map(_.stripPrefix("!"))

    allColumns.filter { col =>
      val matchesInclusion = inclusions.isEmpty || inclusions.exists(p => globMatches(p, col.column))
      val matchesExclusion = exclusionPatterns.exists(p => globMatches(p, col.column))
      matchesInclusion && !matchesExclusion
    }
  }

  private def globMatches(pattern: String, value: String): Boolean = {
    val regex = globToRegex(pattern)
    regex.matches(value.toLowerCase)
  }

  private def globToRegex(glob: String): scala.util.matching.Regex = {
    val escaped = glob.toLowerCase
      .replace(".", "\\.")
      .replace("**", "\u0000")
      .replace("*", ".*")
      .replace("\u0000", ".*")
      .replace("?", ".")
    s"^$escaped$$".r
  }

  def computeSuggestions(state: TuiState): List[typr.bridge.TypeSuggestion] = {
    import typr.cli.tui.SourceStatus

    val existingTypes = state.config.types.getOrElse(Map.empty)
    val typeDefinitions = typr.TypeDefinitions(
      existingTypes.map { case (name, _) =>
        typr.TypeEntry(name)
      }.toList
    )

    val metaDbs: Map[String, typr.MetaDb] = state.sourceStatuses.collect { case (name, ready: SourceStatus.Ready) =>
      name.value -> ready.metaDb
    }

    if (metaDbs.isEmpty) {
      Nil
    } else {
      val groups = ColumnGrouper.groupFromSources(metaDbs)
      TypeSuggester.suggest(groups, typeDefinitions, minFrequency = 1)
    }
  }

  def computeCompositeSuggestions(state: TuiState): List[typr.bridge.CompositeTypeSuggester.CompositeSuggestion] = {
    import typr.cli.tui.SourceStatus
    import typr.bridge.CompositeTypeSuggester

    val existingTypes = state.config.types.getOrElse(Map.empty).keySet

    val metaDbs: Map[String, typr.MetaDb] = state.sourceStatuses.collect { case (name, ready: SourceStatus.Ready) =>
      name.value -> ready.metaDb
    }

    if (metaDbs.isEmpty) {
      Nil
    } else {
      CompositeTypeSuggester.suggest(metaDbs, existingTypes, limit = Int.MaxValue)
    }
  }
}

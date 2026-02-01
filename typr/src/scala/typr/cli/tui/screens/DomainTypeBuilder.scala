package typr.cli.tui.screens

import tui.crossterm.KeyCode
import typr.cli.tui.AppScreen
import typr.cli.tui.BuilderSection
import typr.cli.tui.BuilderStep
import typr.cli.tui.DomainTypeBuilderState
import typr.cli.tui.PrimaryFieldState
import typr.cli.tui.AlignedSourceBuilderState
import typr.cli.tui.TuiState
import typr.cli.tui.navigation.Navigator
import typr.Nullability

/** Keyboard handler for the DomainTypeBuilder screen */
object DomainTypeBuilder {

  def handleKey(state: TuiState, builder: AppScreen.DomainTypeBuilder, keyCode: KeyCode): TuiState = {
    val bs = builder.state

    // Handle based on current step
    bs.step match {
      case BuilderStep.SelectSuggestion =>
        handleSuggestionStep(state, builder, keyCode)
      case BuilderStep.Builder =>
        handleBuilderStep(state, builder, keyCode)
    }
  }

  private def handleSuggestionStep(state: TuiState, builder: AppScreen.DomainTypeBuilder, keyCode: KeyCode): TuiState = {
    val bs = builder.state
    val suggestions = bs.suggestions

    keyCode match {
      case _: KeyCode.Esc =>
        Navigator.goBack(state)

      case _: KeyCode.Up =>
        val newIdx = math.max(0, bs.selectedSuggestionIndex - 1)
        val newOffset = if (newIdx < bs.suggestionScrollOffset) newIdx else bs.suggestionScrollOffset
        state.copy(currentScreen =
          builder.copy(state =
            bs.copy(
              selectedSuggestionIndex = newIdx,
              suggestionScrollOffset = newOffset
            )
          )
        )

      case _: KeyCode.Down =>
        val maxIdx = suggestions.size - 1
        val newIdx = math.min(maxIdx, bs.selectedSuggestionIndex + 1)
        val visibleCount = 10 // approximate visible items
        val newOffset = if (newIdx >= bs.suggestionScrollOffset + visibleCount) {
          bs.suggestionScrollOffset + 1
        } else {
          bs.suggestionScrollOffset
        }
        state.copy(currentScreen =
          builder.copy(state =
            bs.copy(
              selectedSuggestionIndex = newIdx,
              suggestionScrollOffset = newOffset
            )
          )
        )

      case _: KeyCode.Enter =>
        // Select the suggestion and transition to builder
        suggestions.lift(bs.selectedSuggestionIndex) match {
          case Some(suggestion) =>
            val newState = DomainTypeBuilderState.fromSuggestion(suggestion)
            state.copy(currentScreen = builder.copy(state = newState))
          case None =>
            // No suggestion selected, go to empty builder
            state.copy(currentScreen = builder.copy(state = bs.copy(step = BuilderStep.Builder)))
        }

      case _: KeyCode.Tab =>
        // Skip suggestions and go directly to builder
        state.copy(currentScreen = builder.copy(state = bs.copy(step = BuilderStep.Builder)))

      case c: KeyCode.Char if c.c() == 's' =>
        // Skip suggestions
        state.copy(currentScreen = builder.copy(state = bs.copy(step = BuilderStep.Builder)))

      case _ => state
    }
  }

  private def handleBuilderStep(state: TuiState, builder: AppScreen.DomainTypeBuilder, keyCode: KeyCode): TuiState = {
    val bs = builder.state

    // Handle escape for pickers first
    keyCode match {
      case _: KeyCode.Esc if bs.sourcePickerOpen =>
        return state.copy(currentScreen =
          builder.copy(state =
            bs.copy(
              sourcePickerOpen = false,
              addingAlignedSource = false
            )
          )
        )
      case _: KeyCode.Esc if bs.primaryEntityPickerOpen =>
        return state.copy(currentScreen = builder.copy(state = bs.copy(primaryEntityPickerOpen = false)))
      case _: KeyCode.Esc if bs.entityPickerOpen =>
        return state.copy(currentScreen =
          builder.copy(state =
            bs.copy(
              entityPickerOpen = false,
              addingAlignedSource = false
            )
          )
        )
      case _: KeyCode.Esc =>
        return Navigator.goBack(state)
      case _ =>
    }

    // Handle section-specific keys
    bs.focusedSection match {
      case BuilderSection.Name =>
        handleNameSection(state, builder, keyCode)
      case BuilderSection.PrimarySource =>
        handlePrimarySourceSection(state, builder, keyCode)
      case BuilderSection.PrimaryFields =>
        handlePrimaryFieldsSection(state, builder, keyCode)
      case BuilderSection.AlignedSources =>
        handleAlignedSourcesSection(state, builder, keyCode)
    }
  }

  private def handleNameSection(state: TuiState, builder: AppScreen.DomainTypeBuilder, keyCode: KeyCode): TuiState = {
    val bs = builder.state
    keyCode match {
      case c: KeyCode.Char =>
        val newName = bs.name + c.c()
        state.copy(currentScreen = builder.copy(state = bs.copy(name = newName)))

      case _: KeyCode.Backspace if bs.name.nonEmpty =>
        val newName = bs.name.dropRight(1)
        state.copy(currentScreen = builder.copy(state = bs.copy(name = newName)))

      case _: KeyCode.Tab =>
        state.copy(currentScreen = builder.copy(state = bs.copy(focusedSection = BuilderSection.PrimarySource)))

      case _: KeyCode.BackTab =>
        state.copy(currentScreen = builder.copy(state = bs.copy(focusedSection = BuilderSection.AlignedSources)))

      case _: KeyCode.Down =>
        state.copy(currentScreen = builder.copy(state = bs.copy(focusedSection = BuilderSection.PrimarySource)))

      case _: KeyCode.Enter if bs.canSave =>
        saveDomainType(state, bs)

      case _ => state
    }
  }

  private def handlePrimarySourceSection(state: TuiState, builder: AppScreen.DomainTypeBuilder, keyCode: KeyCode): TuiState = {
    val bs = builder.state

    if (bs.sourcePickerOpen) {
      handleSourcePicker(state, builder, keyCode, isPrimary = true)
    } else if (bs.primaryEntityPickerOpen) {
      handleEntityPicker(state, builder, keyCode, isPrimary = true)
    } else {
      keyCode match {
        case _: KeyCode.Tab =>
          state.copy(currentScreen = builder.copy(state = bs.copy(focusedSection = BuilderSection.PrimaryFields)))

        case _: KeyCode.BackTab =>
          state.copy(currentScreen = builder.copy(state = bs.copy(focusedSection = BuilderSection.Name)))

        case _: KeyCode.Up =>
          state.copy(currentScreen = builder.copy(state = bs.copy(focusedSection = BuilderSection.Name)))

        case _: KeyCode.Down =>
          state.copy(currentScreen = builder.copy(state = bs.copy(focusedSection = BuilderSection.PrimaryFields)))

        case _: KeyCode.Enter =>
          if (bs.primarySourceName.isEmpty) {
            state.copy(currentScreen = builder.copy(state = bs.copy(sourcePickerOpen = true)))
          } else {
            state.copy(currentScreen = builder.copy(state = bs.copy(primaryEntityPickerOpen = true)))
          }

        case c: KeyCode.Char if c.c() == 's' =>
          state.copy(currentScreen = builder.copy(state = bs.copy(sourcePickerOpen = true)))

        case c: KeyCode.Char if c.c() == 'e' && bs.primarySourceName.isDefined =>
          state.copy(currentScreen = builder.copy(state = bs.copy(primaryEntityPickerOpen = true)))

        case _ => state
      }
    }
  }

  private def handlePrimaryFieldsSection(state: TuiState, builder: AppScreen.DomainTypeBuilder, keyCode: KeyCode): TuiState = {
    val bs = builder.state

    keyCode match {
      case _: KeyCode.Tab =>
        state.copy(currentScreen = builder.copy(state = bs.copy(focusedSection = BuilderSection.AlignedSources)))

      case _: KeyCode.BackTab =>
        state.copy(currentScreen = builder.copy(state = bs.copy(focusedSection = BuilderSection.PrimarySource)))

      case _: KeyCode.Up =>
        if (bs.selectedFieldIndex > 0) {
          state.copy(currentScreen = builder.copy(state = bs.copy(selectedFieldIndex = bs.selectedFieldIndex - 1)))
        } else {
          state.copy(currentScreen = builder.copy(state = bs.copy(focusedSection = BuilderSection.PrimarySource)))
        }

      case _: KeyCode.Down =>
        val maxIdx = bs.primaryFields.size - 1
        if (bs.selectedFieldIndex < maxIdx) {
          state.copy(currentScreen = builder.copy(state = bs.copy(selectedFieldIndex = bs.selectedFieldIndex + 1)))
        } else {
          state
        }

      case _: KeyCode.Enter =>
        // Toggle field inclusion
        bs.primaryFields.lift(bs.selectedFieldIndex) match {
          case Some(field) =>
            val newFields = bs.primaryFields.updated(bs.selectedFieldIndex, field.copy(included = !field.included))
            state.copy(currentScreen = builder.copy(state = bs.copy(primaryFields = newFields)))
          case None => state
        }

      case c: KeyCode.Char if c.c() == ' ' =>
        // Toggle field inclusion with space
        bs.primaryFields.lift(bs.selectedFieldIndex) match {
          case Some(field) =>
            val newFields = bs.primaryFields.updated(bs.selectedFieldIndex, field.copy(included = !field.included))
            state.copy(currentScreen = builder.copy(state = bs.copy(primaryFields = newFields)))
          case None => state
        }

      case c: KeyCode.Char if c.c() == 'a' =>
        // Select all fields
        val newFields = bs.primaryFields.map(_.copy(included = true))
        state.copy(currentScreen = builder.copy(state = bs.copy(primaryFields = newFields)))

      case c: KeyCode.Char if c.c() == 'n' =>
        // Deselect all fields
        val newFields = bs.primaryFields.map(_.copy(included = false))
        state.copy(currentScreen = builder.copy(state = bs.copy(primaryFields = newFields)))

      case _ => state
    }
  }

  private def handleAlignedSourcesSection(state: TuiState, builder: AppScreen.DomainTypeBuilder, keyCode: KeyCode): TuiState = {
    val bs = builder.state

    if (bs.addingAlignedSource && bs.sourcePickerOpen) {
      handleSourcePicker(state, builder, keyCode, isPrimary = false)
    } else if (bs.addingAlignedSource && bs.entityPickerOpen) {
      handleEntityPicker(state, builder, keyCode, isPrimary = false)
    } else {
      keyCode match {
        case _: KeyCode.Tab =>
          state.copy(currentScreen = builder.copy(state = bs.copy(focusedSection = BuilderSection.Name)))

        case _: KeyCode.BackTab =>
          state.copy(currentScreen = builder.copy(state = bs.copy(focusedSection = BuilderSection.PrimaryFields)))

        case _: KeyCode.Up =>
          if (bs.selectedAlignedSourceIndex > 0) {
            state.copy(currentScreen = builder.copy(state = bs.copy(selectedAlignedSourceIndex = bs.selectedAlignedSourceIndex - 1)))
          } else {
            state.copy(currentScreen = builder.copy(state = bs.copy(focusedSection = BuilderSection.PrimaryFields)))
          }

        case _: KeyCode.Down =>
          val maxIdx = bs.alignedSources.size - 1
          if (bs.selectedAlignedSourceIndex < maxIdx) {
            state.copy(currentScreen = builder.copy(state = bs.copy(selectedAlignedSourceIndex = bs.selectedAlignedSourceIndex + 1)))
          } else {
            state
          }

        case c: KeyCode.Char if c.c() == 'a' =>
          // Add new aligned source
          state.copy(currentScreen =
            builder.copy(state =
              bs.copy(
                addingAlignedSource = true,
                sourcePickerOpen = true,
                sourcePickerIndex = 0
              )
            )
          )

        case c: KeyCode.Char if c.c() == 'd' =>
          // Delete selected aligned source
          if (bs.alignedSources.nonEmpty && bs.selectedAlignedSourceIndex < bs.alignedSources.size) {
            val newSources = bs.alignedSources.zipWithIndex.filterNot(_._2 == bs.selectedAlignedSourceIndex).map(_._1)
            val newIdx = math.min(bs.selectedAlignedSourceIndex, math.max(0, newSources.size - 1))
            state.copy(currentScreen =
              builder.copy(state =
                bs.copy(
                  alignedSources = newSources,
                  selectedAlignedSourceIndex = newIdx
                )
              )
            )
          } else {
            state
          }

        case c: KeyCode.Char if c.c() == 'm' =>
          // Cycle mode of selected aligned source
          bs.alignedSources.lift(bs.selectedAlignedSourceIndex) match {
            case Some(as) =>
              val newMode = as.mode match {
                case typr.bridge.CompatibilityMode.Superset => typr.bridge.CompatibilityMode.Exact
                case typr.bridge.CompatibilityMode.Exact    => typr.bridge.CompatibilityMode.Subset
                case typr.bridge.CompatibilityMode.Subset   => typr.bridge.CompatibilityMode.Superset
              }
              val newSources = bs.alignedSources.updated(bs.selectedAlignedSourceIndex, as.copy(mode = newMode))
              state.copy(currentScreen = builder.copy(state = bs.copy(alignedSources = newSources)))
            case None => state
          }

        case _: KeyCode.Enter if bs.canSave =>
          saveDomainType(state, bs)

        case _ => state
      }
    }
  }

  private def handleSourcePicker(state: TuiState, builder: AppScreen.DomainTypeBuilder, keyCode: KeyCode, isPrimary: Boolean): TuiState = {
    val bs = builder.state
    val sources = getAvailableSources(state)

    keyCode match {
      case _: KeyCode.Up =>
        val newIdx = math.max(0, bs.sourcePickerIndex - 1)
        state.copy(currentScreen = builder.copy(state = bs.copy(sourcePickerIndex = newIdx)))

      case _: KeyCode.Down =>
        val maxIdx = math.max(0, sources.size - 1)
        val newIdx = math.min(maxIdx, bs.sourcePickerIndex + 1)
        state.copy(currentScreen = builder.copy(state = bs.copy(sourcePickerIndex = newIdx)))

      case _: KeyCode.Enter =>
        sources.lift(bs.sourcePickerIndex) match {
          case Some(source) =>
            if (isPrimary) {
              state.copy(currentScreen =
                builder.copy(state =
                  bs.copy(
                    primarySourceName = Some(source.value),
                    primaryEntityPath = None,
                    primaryFields = Nil,
                    sourcePickerOpen = false,
                    primaryEntityPickerOpen = true,
                    primaryEntityPickerIndex = 0
                  )
                )
              )
            } else {
              state.copy(currentScreen =
                builder.copy(state =
                  bs.copy(
                    sourcePickerOpen = false,
                    entityPickerOpen = true,
                    entityPickerIndex = 0
                  )
                )
              )
            }
          case None => state
        }

      case _ => state
    }
  }

  private def handleEntityPicker(state: TuiState, builder: AppScreen.DomainTypeBuilder, keyCode: KeyCode, isPrimary: Boolean): TuiState = {
    val bs = builder.state
    val sourceName = if (isPrimary) {
      bs.primarySourceName
    } else {
      getAvailableSources(state).lift(bs.sourcePickerIndex).map(_.value)
    }
    val entities = sourceName.map(s => getEntitiesFromSource(state, typr.cli.tui.SourceName(s))).getOrElse(Nil)
    val pickerIndex = if (isPrimary) bs.primaryEntityPickerIndex else bs.entityPickerIndex

    keyCode match {
      case _: KeyCode.Up =>
        val newIdx = math.max(0, pickerIndex - 1)
        if (isPrimary) {
          state.copy(currentScreen = builder.copy(state = bs.copy(primaryEntityPickerIndex = newIdx)))
        } else {
          state.copy(currentScreen = builder.copy(state = bs.copy(entityPickerIndex = newIdx)))
        }

      case _: KeyCode.Down =>
        val maxIdx = math.max(0, entities.size - 1)
        val newIdx = math.min(maxIdx, pickerIndex + 1)
        if (isPrimary) {
          state.copy(currentScreen = builder.copy(state = bs.copy(primaryEntityPickerIndex = newIdx)))
        } else {
          state.copy(currentScreen = builder.copy(state = bs.copy(entityPickerIndex = newIdx)))
        }

      case _: KeyCode.Enter =>
        entities.lift(pickerIndex) match {
          case Some(entity) =>
            if (isPrimary) {
              val newFields = loadFieldsFromSource(state, typr.cli.tui.SourceName(sourceName.get), Some(entity))
              val newName = if (bs.name.isEmpty) entity.split("\\.").last else bs.name
              state.copy(currentScreen =
                builder.copy(state =
                  bs.copy(
                    primaryEntityPath = Some(entity),
                    primaryFields = newFields,
                    primaryEntityPickerOpen = false,
                    name = newName
                  )
                )
              )
            } else {
              val newAlignedSource = AlignedSourceBuilderState.empty(sourceName.get, entity)
              state.copy(currentScreen =
                builder.copy(state =
                  bs.copy(
                    alignedSources = bs.alignedSources :+ newAlignedSource,
                    addingAlignedSource = false,
                    entityPickerOpen = false
                  )
                )
              )
            }
          case None => state
        }

      case _ => state
    }
  }

  private def getAvailableSources(state: TuiState): List[typr.cli.tui.SourceName] = {
    import typr.cli.tui.SourceStatus
    state.sourceStatuses
      .collect { case (name, _: SourceStatus.Ready) => name }
      .toList
      .sortBy(_.value)
  }

  private def getEntitiesFromSource(state: TuiState, sourceName: typr.cli.tui.SourceName): List[String] = {
    import typr.cli.tui.SourceStatus
    state.sourceStatuses.get(sourceName) match {
      case Some(SourceStatus.Ready(metaDb, _, _, _, _, _)) =>
        metaDb.relations.keys.toList.map { relName =>
          relName.schema.map(s => s"$s.${relName.name}").getOrElse(relName.name)
        }.sorted
      case _ => Nil
    }
  }

  private def loadFieldsFromSource(state: TuiState, sourceName: typr.cli.tui.SourceName, entityPath: Option[String]): List[PrimaryFieldState] = {
    import typr.cli.tui.SourceStatus
    (state.sourceStatuses.get(sourceName), entityPath) match {
      case (Some(SourceStatus.Ready(metaDb, _, _, _, _, _)), Some(path)) =>
        val (schema, name) = path.split("\\.").toList match {
          case s :: n :: Nil => (Some(s), n)
          case n :: Nil      => (None, n)
          case _             => (None, path)
        }
        val relName = typr.db.RelationName(schema, name)
        metaDb.relations.get(relName).map(_.forceGet).toList.flatMap {
          case table: typr.db.Table =>
            table.cols.toList.map { col =>
              val colName = col.name.value
              val dbTypeStr = dbTypeToString(col.tpe)
              val isNullable = col.nullability != Nullability.NoNulls
              PrimaryFieldState(
                name = colName,
                dbType = dbTypeStr,
                nullable = isNullable,
                included = true,
                canonicalName = colName,
                canonicalType = mapDbTypeToJvmType(dbTypeStr),
                comment = ""
              )
            }
          case view: typr.db.View =>
            view.cols.toList.map { case (col, _) =>
              val colName = col.name.value
              val dbTypeStr = dbTypeToString(col.tpe)
              val isNullable = col.nullability != Nullability.NoNulls
              PrimaryFieldState(
                name = colName,
                dbType = dbTypeStr,
                nullable = isNullable,
                included = true,
                canonicalName = colName,
                canonicalType = mapDbTypeToJvmType(dbTypeStr),
                comment = ""
              )
            }
        }
      case _ => Nil
    }
  }

  private def dbTypeToString(tpe: typr.db.Type): String = {
    tpe.toString.split("\\.").last.replaceAll("\\(.*\\)", "")
  }

  private def mapDbTypeToJvmType(dbType: String): String = {
    val normalized = dbType.toUpperCase.replaceAll("\\(.*\\)", "").trim
    normalized match {
      case "INT4" | "INTEGER" | "INT" | "SERIAL"                    => "Int"
      case "INT8" | "BIGINT" | "BIGSERIAL"                          => "Long"
      case "INT2" | "SMALLINT" | "TINYINT"                          => "Short"
      case "FLOAT4" | "REAL" | "FLOAT"                              => "Float"
      case "FLOAT8" | "DOUBLE PRECISION" | "DOUBLE"                 => "Double"
      case "BOOL" | "BOOLEAN"                                       => "Boolean"
      case "NUMERIC" | "DECIMAL" | "DEC"                            => "BigDecimal"
      case "DATE"                                                   => "LocalDate"
      case "TIME" | "TIME WITHOUT TIME ZONE"                        => "LocalTime"
      case "TIMESTAMP" | "TIMESTAMP WITHOUT TIME ZONE" | "DATETIME" => "LocalDateTime"
      case "TIMESTAMPTZ" | "TIMESTAMP WITH TIME ZONE"               => "OffsetDateTime"
      case "UUID"                                                   => "UUID"
      case "BYTEA" | "BLOB" | "BINARY"                              => "ByteArray"
      case "JSON" | "JSONB"                                         => "Json"
      case _                                                        => "String"
    }
  }

  private def saveDomainType(state: TuiState, bs: DomainTypeBuilderState): TuiState = {
    val domainType = bs.toDomainTypeDefinition

    val domainTypeConfig = typr.config.generated.DomainType(
      fields = domainType.fields.map { f =>
        f.name -> typr.config.generated.FieldSpecObject(
          `type` = f.typeName,
          nullable = Some(f.nullable),
          array = Some(f.array),
          description = f.description,
          default = None
        )
      }.toMap,
      alignedSources =
        if (domainType.alignedSources.isEmpty) None
        else
          Some(
            domainType.alignedSources.map { case (key, as) =>
              key -> typr.config.generated.AlignedSource(
                entity = Some(as.entityPath),
                mode = Some(typr.bridge.CompatibilityMode.label(as.mode)),
                mappings = if (as.mappings.isEmpty) None else Some(as.mappings),
                exclude = if (as.exclude.isEmpty) None else Some(as.exclude.toList),
                include_extra = if (as.includeExtra.isEmpty) None else Some(as.includeExtra),
                readonly = if (as.readonly) Some(true) else None
              )
            }
          ),
      projections = None,
      primary = domainType.primary.map(_.key),
      description = domainType.description,
      generate = None
    )

    val newTypes = state.config.types.getOrElse(Map.empty) + (bs.name -> domainTypeConfig)
    val newConfig = state.config.copy(types = Some(newTypes))
    Navigator.goBack(state.copy(config = newConfig, hasUnsavedChanges = true))
  }
}

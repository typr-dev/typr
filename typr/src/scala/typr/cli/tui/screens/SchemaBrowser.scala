package typr.cli.tui.screens

import tui.*
import tui.crossterm.KeyCode
import tui.widgets.*
import typr.cli.tui.AppScreen
import typr.cli.tui.BrowserPane
import typr.cli.tui.BrowserSlideAnimation
import typr.cli.tui.BrowserSourcePickerState
import typr.cli.tui.BrowserSearchState
import typr.cli.tui.ColumnInfo
import typr.cli.tui.ForeignKeyRef
import typr.cli.tui.SchemaBrowserLevel
import typr.cli.tui.SchemaBrowserState
import typr.cli.tui.SearchResult
import typr.cli.tui.SourceName
import typr.cli.tui.SourceStatus
import typr.cli.tui.TuiState
import typr.cli.tui.TypeWizardState
import typr.cli.tui.components.BackButton
import typr.cli.tui.navigation.Navigator
import typr.db.RelationName

object SchemaBrowser {

  def handleKey(state: TuiState, browser: AppScreen.SchemaBrowser, keyCode: KeyCode): TuiState = {
    val browserState = browser.state

    if (browserState.searchState.active) {
      handleSearchKey(state, browser, keyCode)
    } else {
      handleBrowseKey(state, browser, keyCode)
    }
  }

  def tick(state: TuiState, browser: AppScreen.SchemaBrowser): TuiState = {
    val browserState = browser.state
    if (browserState.slideAnimation.animating) {
      val newAnimation = browserState.slideAnimation.tick
      val newBrowserState = browserState.copy(slideAnimation = newAnimation)
      state.copy(currentScreen = browser.copy(state = newBrowserState))
    } else {
      state
    }
  }

  private def handleSearchKey(state: TuiState, browser: AppScreen.SchemaBrowser, keyCode: KeyCode): TuiState = {
    val browserState = browser.state
    val search = browserState.searchState

    keyCode match {
      case _: KeyCode.Esc =>
        val newSearch = search.copy(active = false, query = "", results = Nil, selectedIndex = 0)
        state.copy(currentScreen = browser.copy(state = browserState.copy(searchState = newSearch)))

      case _: KeyCode.Enter =>
        if (search.results.isEmpty) {
          state
        } else {
          val result = search.results(search.selectedIndex)
          navigateToSearchResult(state, browser, result)
        }

      case _: KeyCode.Up =>
        val newIdx = math.max(0, search.selectedIndex - 1)
        val newSearch = search.copy(selectedIndex = newIdx)
        state.copy(currentScreen = browser.copy(state = browserState.copy(searchState = newSearch)))

      case _: KeyCode.Down =>
        val newIdx = math.min(search.results.length - 1, search.selectedIndex + 1)
        val newSearch = search.copy(selectedIndex = math.max(0, newIdx))
        state.copy(currentScreen = browser.copy(state = browserState.copy(searchState = newSearch)))

      case _: KeyCode.Backspace =>
        val newQuery = search.query.dropRight(1)
        val newResults = performSearch(state, browserState.sourceName, newQuery)
        val newSearch = search.copy(query = newQuery, results = newResults, selectedIndex = 0)
        state.copy(currentScreen = browser.copy(state = browserState.copy(searchState = newSearch)))

      case c: KeyCode.Char =>
        val newQuery = search.query + c.c()
        val newResults = performSearch(state, browserState.sourceName, newQuery)
        val newSearch = search.copy(query = newQuery, results = newResults, selectedIndex = 0)
        state.copy(currentScreen = browser.copy(state = browserState.copy(searchState = newSearch)))

      case _ => state
    }
  }

  private def handleBrowseKey(state: TuiState, browser: AppScreen.SchemaBrowser, keyCode: KeyCode): TuiState = {
    val browserState = browser.state

    keyCode match {
      case _: KeyCode.Esc =>
        browserState.level match {
          case SchemaBrowserLevel.Schemas =>
            Navigator.goBack(state)
          case SchemaBrowserLevel.Tables =>
            // Go back to schemas only if there are multiple schemas, otherwise exit browser
            if (browserState.schemas.length > 1) {
              state.copy(currentScreen =
                browser.copy(state =
                  browserState.copy(
                    level = SchemaBrowserLevel.Schemas,
                    tables = Nil,
                    columns = Nil,
                    selectedTableIndex = 0,
                    selectedColumnIndex = 0,
                    slideAnimation = BrowserSlideAnimation.backward(SchemaBrowserLevel.Tables, SchemaBrowserLevel.Schemas)
                  )
                )
              )
            } else {
              Navigator.goBack(state)
            }
          case SchemaBrowserLevel.Columns =>
            state.copy(currentScreen =
              browser.copy(state =
                browserState.copy(
                  level = SchemaBrowserLevel.Tables,
                  columns = Nil,
                  selectedColumnIndex = 0,
                  focusedPane = BrowserPane.Left,
                  slideAnimation = BrowserSlideAnimation.backward(SchemaBrowserLevel.Columns, SchemaBrowserLevel.Tables)
                )
              )
            )
        }

      case c: KeyCode.Char if c.c() == 'f' =>
        val newSearch = browserState.searchState.copy(active = true)
        state.copy(currentScreen = browser.copy(state = browserState.copy(searchState = newSearch)))

      case c: KeyCode.Char if c.c() == 'q' =>
        Navigator.handleEscape(state)

      case _: KeyCode.Up =>
        handleUpNavigation(state, browser)

      case _: KeyCode.Down =>
        handleDownNavigation(state, browser)

      case _: KeyCode.Left =>
        if (browserState.focusedPane == BrowserPane.Right) {
          state.copy(currentScreen = browser.copy(state = browserState.copy(focusedPane = BrowserPane.Left)))
        } else {
          state
        }

      case _: KeyCode.Right =>
        if (browserState.focusedPane == BrowserPane.Left && browserState.level == SchemaBrowserLevel.Columns) {
          state.copy(currentScreen = browser.copy(state = browserState.copy(focusedPane = BrowserPane.Right)))
        } else {
          state
        }

      case _: KeyCode.Enter =>
        handleEnterKey(state, browser)

      case c: KeyCode.Char if c.c() == 'g' =>
        browserState.currentColumn.flatMap(_.foreignKey) match {
          case Some(fk) =>
            navigateToForeignKey(state, browser, fk)
          case None =>
            state
        }

      case c: KeyCode.Char if c.c() == 't' =>
        browserState.currentColumn match {
          case Some(col) if col.matchingType.isEmpty =>
            createTypeFromColumn(state, browser, col)
          case Some(col) =>
            state
          case None =>
            state
        }

      case _ => state
    }
  }

  private def handleUpNavigation(state: TuiState, browser: AppScreen.SchemaBrowser): TuiState = {
    val browserState = browser.state
    browserState.level match {
      case SchemaBrowserLevel.Schemas =>
        val newIdx = math.max(0, browserState.selectedSchemaIndex - 1)
        state.copy(currentScreen = browser.copy(state = browserState.copy(selectedSchemaIndex = newIdx)))
      case SchemaBrowserLevel.Tables =>
        if (browserState.focusedPane == BrowserPane.Left) {
          val newIdx = math.max(0, browserState.selectedTableIndex - 1)
          val newState = browserState.copy(selectedTableIndex = newIdx)
          state.copy(currentScreen = browser.copy(state = updateColumnsForTable(newState, state)))
        } else {
          state
        }
      case SchemaBrowserLevel.Columns =>
        if (browserState.focusedPane == BrowserPane.Left) {
          val newIdx = math.max(0, browserState.selectedColumnIndex - 1)
          state.copy(currentScreen = browser.copy(state = browserState.copy(selectedColumnIndex = newIdx)))
        } else {
          val newOffset = math.max(0, browserState.detailsScrollOffset - 1)
          state.copy(currentScreen = browser.copy(state = browserState.copy(detailsScrollOffset = newOffset)))
        }
    }
  }

  private def handleDownNavigation(state: TuiState, browser: AppScreen.SchemaBrowser): TuiState = {
    val browserState = browser.state
    browserState.level match {
      case SchemaBrowserLevel.Schemas =>
        val newIdx = math.min(browserState.schemas.length - 1, browserState.selectedSchemaIndex + 1)
        state.copy(currentScreen = browser.copy(state = browserState.copy(selectedSchemaIndex = math.max(0, newIdx))))
      case SchemaBrowserLevel.Tables =>
        if (browserState.focusedPane == BrowserPane.Left) {
          val newIdx = math.min(browserState.tables.length - 1, browserState.selectedTableIndex + 1)
          val newState = browserState.copy(selectedTableIndex = math.max(0, newIdx))
          state.copy(currentScreen = browser.copy(state = updateColumnsForTable(newState, state)))
        } else {
          state
        }
      case SchemaBrowserLevel.Columns =>
        if (browserState.focusedPane == BrowserPane.Left) {
          val newIdx = math.min(browserState.columns.length - 1, browserState.selectedColumnIndex + 1)
          state.copy(currentScreen = browser.copy(state = browserState.copy(selectedColumnIndex = math.max(0, newIdx))))
        } else {
          val newOffset = browserState.detailsScrollOffset + 1
          state.copy(currentScreen = browser.copy(state = browserState.copy(detailsScrollOffset = newOffset)))
        }
    }
  }

  private def handleEnterKey(state: TuiState, browser: AppScreen.SchemaBrowser): TuiState = {
    val browserState = browser.state
    browserState.level match {
      case SchemaBrowserLevel.Schemas =>
        val schema = browserState.currentSchema
        val tables = getTablesForSchema(state, browserState.sourceName, schema)
        val newState = browserState.copy(
          level = SchemaBrowserLevel.Tables,
          tables = tables,
          selectedTableIndex = 0,
          columns = Nil,
          selectedColumnIndex = 0,
          slideAnimation = BrowserSlideAnimation.forward(SchemaBrowserLevel.Schemas, SchemaBrowserLevel.Tables)
        )
        state.copy(currentScreen = browser.copy(state = updateColumnsForTable(newState, state)))

      case SchemaBrowserLevel.Tables =>
        browserState.currentTable match {
          case Some((schema, table)) =>
            val columns = getColumnsForTable(state, browserState.sourceName, schema, table)
            state.copy(currentScreen =
              browser.copy(state =
                browserState.copy(
                  level = SchemaBrowserLevel.Columns,
                  columns = columns,
                  selectedColumnIndex = 0,
                  focusedPane = BrowserPane.Left,
                  slideAnimation = BrowserSlideAnimation.forward(SchemaBrowserLevel.Tables, SchemaBrowserLevel.Columns)
                )
              )
            )
          case None => state
        }

      case SchemaBrowserLevel.Columns =>
        if (browserState.focusedPane == BrowserPane.Left) {
          state.copy(currentScreen = browser.copy(state = browserState.copy(focusedPane = BrowserPane.Right)))
        } else {
          state
        }
    }
  }

  /** Navigate to columns view from React component - for click on table card */
  def navigateToColumnsFromReact(state: TuiState, browser: AppScreen.SchemaBrowser, tableIndex: Int): TuiState = {
    val browserState = browser.state
    val tables = browserState.tables
    if (tableIndex >= 0 && tableIndex < tables.length) {
      val (schema, table) = tables(tableIndex)
      val columns = getColumnsForTable(state, browserState.sourceName, schema, table)
      state.copy(currentScreen =
        browser.copy(state =
          browserState.copy(
            level = SchemaBrowserLevel.Columns,
            selectedTableIndex = tableIndex,
            columns = columns,
            selectedColumnIndex = 0,
            focusedPane = BrowserPane.Left,
            slideAnimation = BrowserSlideAnimation.forward(SchemaBrowserLevel.Tables, SchemaBrowserLevel.Columns)
          )
        )
      )
    } else {
      state
    }
  }

  /** Navigate to tables view from React component - for click on schema card */
  def navigateToTablesFromReact(state: TuiState, browser: AppScreen.SchemaBrowser, schemaIndex: Int): TuiState = {
    val browserState = browser.state
    val schemas = browserState.schemas
    if (schemaIndex >= 0 && schemaIndex < schemas.length) {
      val schema = Some(schemas(schemaIndex)).filter(_.nonEmpty)
      val tables = getTablesForSchema(state, browserState.sourceName, schema)
      val newState = browserState.copy(
        level = SchemaBrowserLevel.Tables,
        selectedSchemaIndex = schemaIndex,
        tables = tables,
        selectedTableIndex = 0,
        columns = Nil,
        selectedColumnIndex = 0,
        slideAnimation = BrowserSlideAnimation.forward(SchemaBrowserLevel.Schemas, SchemaBrowserLevel.Tables)
      )
      state.copy(currentScreen = browser.copy(state = updateColumnsForTable(newState, state)))
    } else {
      state
    }
  }

  private def updateColumnsForTable(browserState: SchemaBrowserState, tuiState: TuiState): SchemaBrowserState = {
    browserState.currentTable match {
      case Some((schema, table)) =>
        val columns = getColumnsForTable(tuiState, browserState.sourceName, schema, table)
        browserState.copy(columns = columns, selectedColumnIndex = 0)
      case None =>
        browserState.copy(columns = Nil, selectedColumnIndex = 0)
    }
  }

  private def getTablesForSchema(state: TuiState, sourceName: SourceName, schema: Option[String]): List[(Option[String], String)] = {
    state.sourceStatuses.get(sourceName) match {
      case Some(ready: SourceStatus.Ready) =>
        val metaDb = ready.metaDb
        metaDb.relations.keys.toList
          .filter(r => schema.isEmpty || r.schema == schema)
          .sortBy(r => (r.schema.getOrElse(""), r.name))
          .map(r => (r.schema, r.name))
      case _ => Nil
    }
  }

  private def getColumnsForTable(state: TuiState, sourceName: SourceName, schema: Option[String], table: String): List[ColumnInfo] = {
    state.sourceStatuses.get(sourceName) match {
      case Some(ready: SourceStatus.Ready) =>
        val metaDb = ready.metaDb
        val relName = RelationName(schema, table)
        metaDb.relations.get(relName) match {
          case Some(lazyRel) =>
            val cols: List[typr.db.Col] = lazyRel.forceGet match {
              case t: typr.db.Table => t.cols.toList
              case v: typr.db.View  => v.cols.toList.map(_._1)
            }

            val fks = lazyRel.forceGet match {
              case t: typr.db.Table => t.foreignKeys
              case _: typr.db.View  => Nil
            }

            val pks: Set[typr.db.ColName] = lazyRel.forceGet match {
              case t: typr.db.Table => t.primaryKey.map(_.colNames.toList.toSet).getOrElse(Set.empty)
              case _: typr.db.View  => Set.empty
            }

            cols.map { col =>
              val colName = col.name
              val fk = fks.find(_.cols.toList.contains(colName)).map { fkDef =>
                val idx = fkDef.cols.toList.indexOf(colName)
                if (idx >= 0 && idx < fkDef.otherCols.length) {
                  ForeignKeyRef(fkDef.otherTable.schema, fkDef.otherTable.name, fkDef.otherCols.toList(idx).value)
                } else {
                  ForeignKeyRef(fkDef.otherTable.schema, fkDef.otherTable.name, "")
                }
              }

              val matchingType = findMatchingType(state, colName.value, col.tpe)

              ColumnInfo(
                name = colName.value,
                dbType = col.tpe,
                nullable = col.nullability == typr.Nullability.Nullable,
                isPrimaryKey = pks.contains(colName),
                foreignKey = fk,
                comment = col.comment,
                defaultValue = col.columnDefault,
                matchingType = matchingType
              )
            }
          case None => Nil
        }
      case _ => Nil
    }
  }

  private def findMatchingType(state: TuiState, colName: String, dbType: typr.db.Type): Option[String] = {
    import typr.config.generated.{BridgeType, FieldType, DomainType}
    val types = state.config.types.getOrElse(Map.empty)
    types
      .find { case (typeName, bridgeType) =>
        bridgeType match {
          case field: FieldType =>
            field.db.flatMap(_.column).exists { pattern =>
              val patterns = pattern match {
                case typr.config.generated.StringOrArrayString(s)  => List(s)
                case typr.config.generated.StringOrArrayArray(arr) => arr
              }
              patterns.exists(p => globMatches(p, colName))
            }
          case _: DomainType => false
        }
      }
      .map(_._1)
  }

  private def globMatches(pattern: String, value: String): Boolean = {
    val escaped = pattern.toLowerCase
      .replace(".", "\\.")
      .replace("**", "\u0000")
      .replace("*", ".*")
      .replace("\u0000", ".*")
      .replace("?", ".")
    s"^$escaped$$".r.matches(value.toLowerCase)
  }

  def dbTypeName(tpe: typr.db.Type): String = {
    tpe.getClass.getSimpleName.stripSuffix("$")
  }

  def handleBrowserNavigation(browser: SchemaBrowserState, metaDb: typr.MetaDb, keyCode: KeyCode): SchemaBrowserState = {
    keyCode match {
      case _: KeyCode.Up =>
        browser.level match {
          case SchemaBrowserLevel.Schemas =>
            val newIdx = math.max(0, browser.selectedSchemaIndex - 1)
            browser.copy(selectedSchemaIndex = newIdx)
          case SchemaBrowserLevel.Tables =>
            val newIdx = math.max(0, browser.selectedTableIndex - 1)
            val newBrowser = browser.copy(selectedTableIndex = newIdx)
            updateColumnsForTableDirect(newBrowser, metaDb)
          case SchemaBrowserLevel.Columns =>
            if (browser.focusedPane == BrowserPane.Left) {
              val newIdx = math.max(0, browser.selectedColumnIndex - 1)
              browser.copy(selectedColumnIndex = newIdx)
            } else {
              val newOffset = math.max(0, browser.detailsScrollOffset - 1)
              browser.copy(detailsScrollOffset = newOffset)
            }
        }

      case _: KeyCode.Down =>
        browser.level match {
          case SchemaBrowserLevel.Schemas =>
            val newIdx = math.min(browser.schemas.length - 1, browser.selectedSchemaIndex + 1)
            browser.copy(selectedSchemaIndex = math.max(0, newIdx))
          case SchemaBrowserLevel.Tables =>
            val newIdx = math.min(browser.tables.length - 1, browser.selectedTableIndex + 1)
            val newBrowser = browser.copy(selectedTableIndex = math.max(0, newIdx))
            updateColumnsForTableDirect(newBrowser, metaDb)
          case SchemaBrowserLevel.Columns =>
            if (browser.focusedPane == BrowserPane.Left) {
              val newIdx = math.min(browser.columns.length - 1, browser.selectedColumnIndex + 1)
              browser.copy(selectedColumnIndex = math.max(0, newIdx))
            } else {
              browser.copy(detailsScrollOffset = browser.detailsScrollOffset + 1)
            }
        }

      case _: KeyCode.Enter =>
        browser.level match {
          case SchemaBrowserLevel.Schemas =>
            val schema = browser.currentSchema
            val tables = getTablesForSchemaDirect(metaDb, schema)
            val newBrowser = browser.copy(
              level = SchemaBrowserLevel.Tables,
              tables = tables,
              selectedTableIndex = 0,
              columns = Nil,
              selectedColumnIndex = 0
            )
            updateColumnsForTableDirect(newBrowser, metaDb)

          case SchemaBrowserLevel.Tables =>
            browser.currentTable match {
              case Some((schema, table)) =>
                val columns = getColumnsForTableDirect(metaDb, schema, table, Map.empty)
                browser.copy(
                  level = SchemaBrowserLevel.Columns,
                  columns = columns,
                  selectedColumnIndex = 0,
                  focusedPane = BrowserPane.Left
                )
              case None => browser
            }

          case SchemaBrowserLevel.Columns =>
            if (browser.focusedPane == BrowserPane.Left) {
              browser.copy(focusedPane = BrowserPane.Right)
            } else {
              browser
            }
        }

      case _: KeyCode.Esc =>
        browser.level match {
          case SchemaBrowserLevel.Schemas => browser
          case SchemaBrowserLevel.Tables =>
            if (browser.schemas.length > 1) {
              browser.copy(
                level = SchemaBrowserLevel.Schemas,
                tables = Nil,
                columns = Nil,
                selectedTableIndex = 0,
                selectedColumnIndex = 0
              )
            } else browser
          case SchemaBrowserLevel.Columns =>
            browser.copy(
              level = SchemaBrowserLevel.Tables,
              columns = Nil,
              selectedColumnIndex = 0,
              focusedPane = BrowserPane.Left
            )
        }

      case _ => browser
    }
  }

  private def getTablesForSchemaDirect(metaDb: typr.MetaDb, schema: Option[String]): List[(Option[String], String)] = {
    metaDb.relations.keys.toList
      .filter(r => schema.isEmpty || r.schema == schema)
      .sortBy(r => (r.schema.getOrElse(""), r.name))
      .map(r => (r.schema, r.name))
  }

  private def updateColumnsForTableDirect(browser: SchemaBrowserState, metaDb: typr.MetaDb): SchemaBrowserState = {
    browser.currentTable match {
      case Some((schema, table)) =>
        val columns = getColumnsForTableDirect(metaDb, schema, table, Map.empty)
        browser.copy(columns = columns, selectedColumnIndex = 0)
      case None =>
        browser.copy(columns = Nil, selectedColumnIndex = 0)
    }
  }

  private def getColumnsForTableDirect(metaDb: typr.MetaDb, schema: Option[String], table: String, types: Map[String, typr.config.generated.BridgeType]): List[ColumnInfo] = {
    val relName = RelationName(schema, table)
    metaDb.relations.get(relName) match {
      case Some(lazyRel) =>
        val cols: List[typr.db.Col] = lazyRel.forceGet match {
          case t: typr.db.Table => t.cols.toList
          case v: typr.db.View  => v.cols.toList.map(_._1)
        }

        val fks = lazyRel.forceGet match {
          case t: typr.db.Table => t.foreignKeys
          case _: typr.db.View  => Nil
        }

        val pks: Set[typr.db.ColName] = lazyRel.forceGet match {
          case t: typr.db.Table => t.primaryKey.map(_.colNames.toList.toSet).getOrElse(Set.empty)
          case _: typr.db.View  => Set.empty
        }

        cols.map { col =>
          val colName = col.name
          val fk = fks.find(_.cols.toList.contains(colName)).map { fkDef =>
            val idx = fkDef.cols.toList.indexOf(colName)
            if (idx >= 0 && idx < fkDef.otherCols.length) {
              ForeignKeyRef(fkDef.otherTable.schema, fkDef.otherTable.name, fkDef.otherCols.toList(idx).value)
            } else {
              ForeignKeyRef(fkDef.otherTable.schema, fkDef.otherTable.name, "")
            }
          }

          val matchingType = findMatchingTypeDirect(types, colName.value, col.tpe)

          ColumnInfo(
            name = colName.value,
            dbType = col.tpe,
            nullable = col.nullability == typr.Nullability.Nullable,
            isPrimaryKey = pks.contains(colName),
            foreignKey = fk,
            comment = col.comment,
            defaultValue = col.columnDefault,
            matchingType = matchingType
          )
        }
      case None => Nil
    }
  }

  private def findMatchingTypeDirect(types: Map[String, typr.config.generated.BridgeType], colName: String, dbType: typr.db.Type): Option[String] = {
    import typr.config.generated.{FieldType, DomainType}
    types
      .find { case (typeName, bridgeType) =>
        bridgeType match {
          case field: FieldType =>
            field.db.flatMap(_.column).exists { pattern =>
              val patterns = pattern match {
                case typr.config.generated.StringOrArrayString(s)  => List(s)
                case typr.config.generated.StringOrArrayArray(arr) => arr
              }
              patterns.exists(p => globMatches(p, colName))
            }
          case _: DomainType => false
        }
      }
      .map(_._1)
  }

  private def performSearch(state: TuiState, sourceName: SourceName, query: String): List[SearchResult] = {
    if (query.isEmpty) return Nil

    state.sourceStatuses.get(sourceName) match {
      case Some(ready: SourceStatus.Ready) =>
        val metaDb = ready.metaDb
        val lowerQuery = query.toLowerCase
        val results = scala.collection.mutable.ListBuffer[SearchResult]()

        metaDb.relations.keys.flatMap(_.schema).toSet.toList.sorted.filter(_.toLowerCase.contains(lowerQuery)).foreach { schema =>
          results += SearchResult.SchemaResult(schema)
        }

        metaDb.relations.foreach { case (relName, lazyRel) =>
          if (relName.name.toLowerCase.contains(lowerQuery)) {
            results += SearchResult.TableResult(relName.schema, relName.name)
          }

          val cols: List[typr.db.Col] = lazyRel.forceGet match {
            case t: typr.db.Table => t.cols.toList
            case v: typr.db.View  => v.cols.toList.map(_._1)
          }

          cols.foreach { col =>
            if (col.name.value.toLowerCase.contains(lowerQuery)) {
              results += SearchResult.ColumnResult(relName.schema, relName.name, col.name.value, dbTypeName(col.tpe))
            }
          }
        }

        results.toList.take(20)
      case _ => Nil
    }
  }

  private def navigateToSearchResult(state: TuiState, browser: AppScreen.SchemaBrowser, result: SearchResult): TuiState = {
    val browserState = browser.state

    result match {
      case SearchResult.SchemaResult(schema) =>
        val schemaIdx = browserState.schemas.indexOf(schema)
        val tables = getTablesForSchema(state, browserState.sourceName, Some(schema))
        val newState = browserState.copy(
          level = SchemaBrowserLevel.Tables,
          selectedSchemaIndex = if (schemaIdx >= 0) schemaIdx else 0,
          tables = tables,
          selectedTableIndex = 0,
          columns = Nil,
          searchState = BrowserSearchState.initial
        )
        state.copy(currentScreen = browser.copy(state = newState))

      case SearchResult.TableResult(schema, table) =>
        val tables = getTablesForSchema(state, browserState.sourceName, schema)
        val tableIdx = tables.indexWhere { case (s, t) => s == schema && t == table }
        val columns = getColumnsForTable(state, browserState.sourceName, schema, table)
        val newState = browserState.copy(
          level = SchemaBrowserLevel.Columns,
          tables = tables,
          selectedTableIndex = if (tableIdx >= 0) tableIdx else 0,
          columns = columns,
          selectedColumnIndex = 0,
          searchState = BrowserSearchState.initial
        )
        state.copy(currentScreen = browser.copy(state = newState))

      case SearchResult.ColumnResult(schema, table, column, _) =>
        val tables = getTablesForSchema(state, browserState.sourceName, schema)
        val tableIdx = tables.indexWhere { case (s, t) => s == schema && t == table }
        val columns = getColumnsForTable(state, browserState.sourceName, schema, table)
        val colIdx = columns.indexWhere(_.name == column)
        val newState = browserState.copy(
          level = SchemaBrowserLevel.Columns,
          tables = tables,
          selectedTableIndex = if (tableIdx >= 0) tableIdx else 0,
          columns = columns,
          selectedColumnIndex = if (colIdx >= 0) colIdx else 0,
          focusedPane = BrowserPane.Left,
          searchState = BrowserSearchState.initial
        )
        state.copy(currentScreen = browser.copy(state = newState))
    }
  }

  private def navigateToForeignKey(state: TuiState, browser: AppScreen.SchemaBrowser, fk: ForeignKeyRef): TuiState = {
    val browserState = browser.state
    val tables = getTablesForSchema(state, browserState.sourceName, fk.targetSchema)
    val tableIdx = tables.indexWhere { case (s, t) => s == fk.targetSchema && t == fk.targetTable }
    val columns = getColumnsForTable(state, browserState.sourceName, fk.targetSchema, fk.targetTable)
    val colIdx = columns.indexWhere(_.name == fk.targetColumn)

    val newState = browserState.copy(
      level = SchemaBrowserLevel.Columns,
      tables = tables,
      selectedTableIndex = if (tableIdx >= 0) tableIdx else 0,
      columns = columns,
      selectedColumnIndex = if (colIdx >= 0) colIdx else 0,
      focusedPane = BrowserPane.Left
    )
    state.copy(currentScreen = browser.copy(state = newState))
  }

  private def createTypeFromColumn(state: TuiState, browser: AppScreen.SchemaBrowser, col: ColumnInfo): TuiState = {
    val suggestions = computeSuggestions(state, col.name)
    val wizardState = TypeWizardState
      .initial(suggestions)
      .copy(
        name = col.name.capitalize,
        dbColumns = s"*${col.name.toLowerCase}*"
      )
    state.copy(currentScreen = AppScreen.TypeWizard(wizardState))
  }

  private def computeSuggestions(state: TuiState, columnName: String): List[typr.bridge.TypeSuggestion] = {
    import typr.bridge.{ColumnGrouper, TypeSuggester}

    val existingTypes = state.config.types.getOrElse(Map.empty)
    val typeDefinitions = typr.TypeDefinitions(
      existingTypes.map { case (name, _) => typr.TypeEntry(name) }.toList
    )

    val metaDbs: Map[String, typr.MetaDb] = state.sourceStatuses.collect { case (name, ready: SourceStatus.Ready) =>
      name.value -> ready.metaDb
    }

    if (metaDbs.isEmpty) Nil
    else {
      val groups = ColumnGrouper.groupFromSources(metaDbs)
      val allSuggestions = TypeSuggester.suggest(groups, typeDefinitions, minFrequency = 1)

      val lowerColName = columnName.toLowerCase
      val (matching, notMatching) = allSuggestions.partition { sugg =>
        sugg.matchingColumns.exists(_.column.toLowerCase.contains(lowerColName)) ||
        sugg.name.toLowerCase.contains(lowerColName)
      }

      (matching.sortBy(-_.frequency) ++ notMatching.sortBy(-_.frequency)).take(10)
    }
  }

  def render(f: Frame, state: TuiState, browser: AppScreen.SchemaBrowser): Unit = {
    val browserState = browser.state

    if (browserState.searchState.active) {
      renderSearch(f, state, browserState)
    } else if (browserState.slideAnimation.animating) {
      renderSlidingPanes(f, state, browserState)
    } else {
      browserState.level match {
        case SchemaBrowserLevel.Schemas =>
          renderSchemaList(f, state, browserState)
        case SchemaBrowserLevel.Tables =>
          renderTableList(f, state, browserState)
        case SchemaBrowserLevel.Columns =>
          renderColumnView(f, state, browserState)
      }
    }
  }

  private def renderSlidingPanes(f: Frame, state: TuiState, browserState: SchemaBrowserState): Unit = {
    val area = f.size
    val buf = f.buffer
    val anim = browserState.slideAnimation
    val progress = anim.progress
    val totalWidth = area.width.toInt

    val visibleStrip = 6

    if (anim.isForward) {
      val leftPaneWidth = math.max(visibleStrip, ((1.0 - progress) * totalWidth).toInt)
      val rightPaneX = leftPaneWidth

      val leftArea = Rect(x = area.x, y = area.y, width = leftPaneWidth.toShort, height = area.height)
      val rightArea = Rect(x = (area.x + rightPaneX).toShort, y = area.y, width = (totalWidth - rightPaneX).toShort, height = area.height)

      if (leftArea.width > 0) {
        renderLevelPane(f, state, browserState, anim.fromLevel, leftArea, faded = progress > 0.3)
      }
      if (rightArea.width > visibleStrip) {
        renderLevelPane(f, state, browserState, anim.toLevel, rightArea, faded = false)
      }

      if (leftArea.width > 0 && rightArea.width > 0) {
        for (row <- area.y until (area.y + area.height.toInt).toShort) {
          buf.setString(rightPaneX - 1, row, "│", Style(fg = Some(Color.DarkGray)))
        }
      }
    } else {
      val rightPaneStart = (progress * (totalWidth - visibleStrip)).toInt
      val leftPaneWidth = totalWidth - visibleStrip

      val leftArea = Rect(x = area.x, y = area.y, width = math.max(1, leftPaneWidth - rightPaneStart).toShort, height = area.height)
      val rightArea = Rect(
        x = (area.x + leftPaneWidth - rightPaneStart).toShort,
        y = area.y,
        width = math.min(totalWidth, totalWidth - leftPaneWidth + rightPaneStart).toShort,
        height = area.height
      )

      if (leftArea.width > visibleStrip) {
        renderLevelPane(f, state, browserState, anim.toLevel, leftArea, faded = false)
      }
      if (rightArea.width > 0 && rightArea.x < area.x + totalWidth) {
        renderLevelPane(f, state, browserState, anim.fromLevel, rightArea, faded = progress > 0.3)
      }

      val borderX = leftArea.x + leftArea.width.toInt
      if (borderX > area.x && borderX < area.x + totalWidth) {
        for (row <- area.y until (area.y + area.height.toInt).toShort) {
          buf.setString(borderX, row, "│", Style(fg = Some(Color.DarkGray)))
        }
      }
    }
  }

  private def renderLevelPane(f: Frame, state: TuiState, browserState: SchemaBrowserState, level: SchemaBrowserLevel, area: Rect, faded: Boolean): Unit = {
    level match {
      case SchemaBrowserLevel.Schemas =>
        renderSchemaListInArea(f, state, browserState, area, faded)
      case SchemaBrowserLevel.Tables =>
        renderTableListInArea(f, state, browserState, area, faded)
      case SchemaBrowserLevel.Columns =>
        renderColumnViewInArea(f, state, browserState, area, faded)
    }
  }

  private def renderSchemaListInArea(f: Frame, state: TuiState, browserState: SchemaBrowserState, area: Rect, faded: Boolean): Unit = {
    val borderStyle = if (faded) Style(fg = Some(Color.DarkGray)) else Style(fg = Some(Color.Cyan))
    val block = BlockWidget(
      title = Some(Spans.from(Span.styled(s"Schemas", if (faded) Style(fg = Some(Color.DarkGray)) else Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded,
      borderStyle = borderStyle
    )
    f.renderWidget(block, area)

    val innerArea = Rect(x = (area.x + 2).toShort, y = (area.y + 1).toShort, width = (area.width - 4).toShort, height = (area.height - 2).toShort)
    val buf = f.buffer
    var y = innerArea.y

    browserState.schemas.zipWithIndex.foreach { case (schema, idx) =>
      if (y < innerArea.y + innerArea.height.toInt) {
        val isSelected = idx == browserState.selectedSchemaIndex
        val prefix = if (isSelected && !faded) "> " else "  "
        val style = if (faded) {
          Style(fg = Some(Color.DarkGray))
        } else if (isSelected) {
          Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
        } else {
          Style(fg = Some(Color.Cyan))
        }
        buf.setString(innerArea.x, y, s"$prefix□ $schema".take(innerArea.width.toInt), style)
        y = (y + 1).toShort
      }
    }
  }

  private def renderTableListInArea(f: Frame, state: TuiState, browserState: SchemaBrowserState, area: Rect, faded: Boolean): Unit = {
    val schemaTitle = browserState.currentSchema.map(s => s": $s").getOrElse("")
    val borderStyle = if (faded) Style(fg = Some(Color.DarkGray)) else Style(fg = Some(Color.Cyan))
    val block = BlockWidget(
      title = Some(Spans.from(Span.styled(s"Tables$schemaTitle", if (faded) Style(fg = Some(Color.DarkGray)) else Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded,
      borderStyle = borderStyle
    )
    f.renderWidget(block, area)

    val innerArea = Rect(x = (area.x + 2).toShort, y = (area.y + 1).toShort, width = (area.width - 4).toShort, height = (area.height - 2).toShort)
    val buf = f.buffer
    var y = innerArea.y

    browserState.tables.zipWithIndex.foreach { case ((schema, table), idx) =>
      if (y < innerArea.y + innerArea.height.toInt) {
        val isSelected = idx == browserState.selectedTableIndex
        val prefix = if (isSelected && !faded) "> " else "  "
        val style = if (faded) {
          Style(fg = Some(Color.DarkGray))
        } else if (isSelected) {
          Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
        } else {
          Style(fg = Some(Color.Cyan))
        }
        val displayName = schema
          .filter(_ != browserState.currentSchema.getOrElse(""))
          .map(s => s"$s.$table")
          .getOrElse(table)
        buf.setString(innerArea.x, y, s"$prefix▣ $displayName".take(innerArea.width.toInt), style)
        y = (y + 1).toShort
      }
    }
  }

  private def renderColumnViewInArea(f: Frame, state: TuiState, browserState: SchemaBrowserState, area: Rect, faded: Boolean): Unit = {
    val tableName = browserState.currentTable
      .map { case (s, t) => s.map(sc => s"$sc.$t").getOrElse(t) }
      .getOrElse("Columns")
    val borderStyle = if (faded) Style(fg = Some(Color.DarkGray)) else Style(fg = Some(Color.Cyan))
    val block = BlockWidget(
      title = Some(Spans.from(Span.styled(tableName, if (faded) Style(fg = Some(Color.DarkGray)) else Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded,
      borderStyle = borderStyle
    )
    f.renderWidget(block, area)

    val innerArea = Rect(x = (area.x + 2).toShort, y = (area.y + 1).toShort, width = (area.width - 4).toShort, height = (area.height - 2).toShort)
    val buf = f.buffer
    var y = innerArea.y

    browserState.columns.zipWithIndex.foreach { case (col, idx) =>
      if (y < innerArea.y + innerArea.height.toInt) {
        val isSelected = idx == browserState.selectedColumnIndex
        val pkIcon = if (col.isPrimaryKey) "◆" else " "
        val fkIcon = if (col.foreignKey.isDefined) "→" else " "
        val prefix = if (isSelected && !faded) "> " else "  "
        val style = if (faded) {
          Style(fg = Some(Color.DarkGray))
        } else if (isSelected) {
          Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
        } else {
          Style(fg = Some(Color.White))
        }
        buf.setString(innerArea.x, y, s"$prefix$pkIcon$fkIcon ${col.name}".take(innerArea.width.toInt), style)
        y = (y + 1).toShort
      }
    }
  }

  private def renderSearch(f: Frame, state: TuiState, browserState: SchemaBrowserState): Unit = {
    val block = BlockWidget(
      title = Some(Spans.from(Span.styled(s"Search: ${browserState.sourceName.value}", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    f.renderWidget(block, f.size)

    val innerArea = Rect(
      x = f.size.x + 2,
      y = f.size.y + 1,
      width = f.size.width - 4,
      height = f.size.height - 3
    )

    val buf = f.buffer
    var y = innerArea.y

    buf.setSpans(
      innerArea.x,
      y,
      Spans.from(
        Span.styled("Find: ", Style(fg = Some(Color.Yellow))),
        Span.styled(browserState.searchState.query + "█", Style(fg = Some(Color.White), bg = Some(Color.Blue)))
      ),
      innerArea.width.toInt
    )
    y += 2

    if (browserState.searchState.results.isEmpty && browserState.searchState.query.nonEmpty) {
      buf.setString(innerArea.x, y, "No results found", Style(fg = Some(Color.DarkGray)))
    } else {
      browserState.searchState.results.zipWithIndex.foreach { case (result, idx) =>
        if (y < innerArea.y + innerArea.height.toInt - 2) {
          val isSelected = idx == browserState.searchState.selectedIndex
          val prefix = if (isSelected) "> " else "  "
          val style = if (isSelected) Style(bg = Some(Color.Blue)) else Style()

          val (icon, text) = result match {
            case SearchResult.SchemaResult(schema) =>
              ("□", s"Schema: $schema")
            case SearchResult.TableResult(schema, table) =>
              ("▣", s"Table: ${schema.map(_ + ".").getOrElse("")}$table")
            case SearchResult.ColumnResult(schema, table, column, dbType) =>
              ("◦", s"Column: ${schema.map(_ + ".").getOrElse("")}$table.$column ($dbType)")
          }

          buf.setSpans(
            innerArea.x,
            y,
            Spans.from(
              Span.styled(prefix, style),
              Span.styled(icon + " ", Style(fg = Some(Color.Cyan))),
              Span.styled(text, style)
            ),
            innerArea.width.toInt
          )
          y += 1
        }
      }
    }

    val hintY = f.size.y + f.size.height.toInt - 2
    buf.setString(innerArea.x, hintY, "[Enter] Go  [Esc] Cancel", Style(fg = Some(Color.DarkGray)))
  }

  private def renderSchemaList(f: Frame, state: TuiState, browserState: SchemaBrowserState): Unit = {
    val block = BlockWidget(
      title = Some(Spans.from(Span.styled(s"Schemas: ${browserState.sourceName.value}", Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    f.renderWidget(block, f.size)

    val innerArea = Rect(
      x = f.size.x + 2,
      y = f.size.y + 1,
      width = f.size.width - 4,
      height = f.size.height - 3
    )

    val buf = f.buffer
    var y = innerArea.y

    if (browserState.schemas.isEmpty) {
      buf.setString(innerArea.x, y, "No schemas found", Style(fg = Some(Color.DarkGray)))
    } else {
      browserState.schemas.zipWithIndex.foreach { case (schema, idx) =>
        if (y < innerArea.y + innerArea.height.toInt - 2) {
          val isSelected = idx == browserState.selectedSchemaIndex
          val prefix = if (isSelected) "> " else "  "
          val style =
            if (isSelected) Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
            else Style(fg = Some(Color.Cyan))

          buf.setString(innerArea.x, y, s"$prefix□ $schema", style)
          y += 1
        }
      }
    }

    val hintY = f.size.y + f.size.height.toInt - 2
    buf.setString(innerArea.x, hintY, "[Enter] Browse  [f] Search  [Esc] Back", Style(fg = Some(Color.DarkGray)))
  }

  private def renderTableList(f: Frame, state: TuiState, browserState: SchemaBrowserState): Unit = {
    val chunks = Layout(
      direction = Direction.Horizontal,
      margin = Margin(1),
      constraints = Array(Constraint.Percentage(50), Constraint.Percentage(50))
    ).split(f.size)

    // Left pane: show schemas as context (faded)
    renderSchemaListPane(f, chunks(0), browserState, faded = true)
    // Right pane: show tables (active)
    renderTableListPane(f, chunks(1), browserState)
  }

  private def renderSchemaListPane(f: Frame, area: Rect, browserState: SchemaBrowserState, faded: Boolean): Unit = {
    val borderStyle = if (faded) Style(fg = Some(Color.DarkGray)) else Style(fg = Some(Color.Cyan))
    val titleStyle = if (faded) Style(fg = Some(Color.DarkGray)) else Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)
    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Schemas", titleStyle))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded,
      borderStyle = borderStyle
    )
    f.renderWidget(block, area)

    val innerArea = Rect(
      x = area.x + 2,
      y = area.y + 1,
      width = area.width - 4,
      height = area.height - 2
    )

    val buf = f.buffer
    var y = innerArea.y

    browserState.schemas.zipWithIndex.foreach { case (schema, idx) =>
      if (y < innerArea.y + innerArea.height.toInt) {
        val isSelected = idx == browserState.selectedSchemaIndex
        val prefix = if (isSelected && !faded) "> " else "  "
        val style = if (faded) {
          if (isSelected) Style(fg = Some(Color.Gray), addModifier = Modifier.BOLD)
          else Style(fg = Some(Color.DarkGray))
        } else if (isSelected) {
          Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
        } else {
          Style(fg = Some(Color.Cyan))
        }
        buf.setString(innerArea.x, y, s"$prefix□ $schema".take(innerArea.width.toInt), style)
        y += 1
      }
    }
  }

  private def renderTableListPane(f: Frame, area: Rect, browserState: SchemaBrowserState, faded: Boolean = false): Unit = {
    val schemaTitle = browserState.currentSchema.map(s => s": $s").getOrElse("")
    val borderStyle = if (faded) Style(fg = Some(Color.DarkGray)) else Style(fg = Some(Color.Cyan))
    val titleStyle = if (faded) Style(fg = Some(Color.DarkGray)) else Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)
    val block = BlockWidget(
      title = Some(Spans.from(Span.styled(s"Tables$schemaTitle", titleStyle))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded,
      borderStyle = borderStyle
    )
    f.renderWidget(block, area)

    val innerArea = Rect(
      x = area.x + 2,
      y = area.y + 1,
      width = area.width - 4,
      height = area.height - 2
    )

    val buf = f.buffer
    var y = innerArea.y

    if (browserState.tables.isEmpty) {
      buf.setString(innerArea.x, y, "No tables found", Style(fg = Some(Color.DarkGray)))
    } else {
      browserState.tables.zipWithIndex.foreach { case ((schema, table), idx) =>
        if (y < innerArea.y + innerArea.height.toInt) {
          val isSelected = idx == browserState.selectedTableIndex
          val prefix = if (isSelected && !faded) "> " else "  "
          val style = if (faded) {
            if (isSelected) Style(fg = Some(Color.Gray), addModifier = Modifier.BOLD)
            else Style(fg = Some(Color.DarkGray))
          } else if (isSelected) {
            Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
          } else {
            Style(fg = Some(Color.Cyan))
          }

          val displayName = schema
            .filter(_ != browserState.currentSchema.getOrElse(""))
            .map(s => s"$s.$table")
            .getOrElse(table)
          buf.setString(innerArea.x, y, s"$prefix▣ $displayName".take(innerArea.width.toInt), style)
          y += 1
        }
      }
    }

    // Show hint at bottom if not faded
    if (!faded) {
      val hintY = area.y + area.height.toInt - 2
      buf.setString(area.x + 2, hintY, "[Enter] Columns  [f] Search  [Esc] Back", Style(fg = Some(Color.DarkGray)))
    }
  }

  private def renderTableColumnsPreview(f: Frame, area: Rect, state: TuiState, browserState: SchemaBrowserState): Unit = {
    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Columns", Style(fg = Some(Color.Cyan))))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    f.renderWidget(block, area)

    val innerArea = Rect(
      x = area.x + 2,
      y = area.y + 1,
      width = area.width - 4,
      height = area.height - 2
    )

    val buf = f.buffer
    var y = innerArea.y

    if (browserState.columns.isEmpty) {
      buf.setString(innerArea.x, y, "Select a table", Style(fg = Some(Color.DarkGray)))
    } else {
      browserState.columns.take(innerArea.height.toInt).foreach { col =>
        if (y < innerArea.y + innerArea.height.toInt) {
          val pkIcon = if (col.isPrimaryKey) "◆" else " "
          val fkIcon = if (col.foreignKey.isDefined) "→" else " "
          val nullIcon = if (col.nullable) "?" else "!"
          val typeIcon = col.matchingType.map(_ => "⚡").getOrElse(" ")

          val icons = s"$pkIcon$fkIcon$nullIcon$typeIcon"
          val colStyle = Style(fg = Some(Color.White))
          val typeStyle = Style(fg = Some(Color.DarkGray))

          buf.setSpans(
            innerArea.x,
            y,
            Spans.from(
              Span.styled(icons + " ", Style(fg = Some(Color.Yellow))),
              Span.styled(col.name, colStyle),
              Span.styled(s" ${dbTypeName(col.dbType)}", typeStyle)
            ),
            innerArea.width.toInt
          )
          y += 1
        }
      }
    }
  }

  private def renderColumnView(f: Frame, state: TuiState, browserState: SchemaBrowserState): Unit = {
    val chunks = Layout(
      direction = Direction.Horizontal,
      margin = Margin(1),
      constraints = Array(Constraint.Percentage(50), Constraint.Percentage(50))
    ).split(f.size)

    // Left pane: show tables as context (faded)
    renderTableListPane(f, chunks(0), browserState, faded = true)
    // Right pane: show columns (active) with details in the hint area
    renderColumnListPane(f, chunks(1), browserState, state)
  }

  private def renderColumnListPane(f: Frame, area: Rect, browserState: SchemaBrowserState, state: TuiState): Unit = {
    val tableName = browserState.currentTable
      .map { case (s, t) =>
        s.map(sc => s"$sc.$t").getOrElse(t)
      }
      .getOrElse("Columns")

    val block = BlockWidget(
      title = Some(Spans.from(Span.styled(tableName, Style(fg = Some(Color.Cyan), addModifier = Modifier.BOLD)))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded
    )
    f.renderWidget(block, area)

    // Split the area: columns list on top, details on bottom
    val listHeight = math.max(1, (area.height.toInt - 2) * 2 / 3)
    val detailsHeight = area.height.toInt - 2 - listHeight - 1

    val listArea = Rect(
      x = area.x + 2,
      y = area.y + 1,
      width = area.width - 4,
      height = listHeight.toShort
    )

    val dividerY = area.y + 1 + listHeight
    val detailsArea = Rect(
      x = area.x + 2,
      y = (dividerY + 1).toShort,
      width = area.width - 4,
      height = detailsHeight.toShort
    )

    val buf = f.buffer
    var y = listArea.y

    // Render columns list
    browserState.columns.zipWithIndex.foreach { case (col, idx) =>
      if (y < listArea.y + listArea.height.toInt) {
        val isSelected = idx == browserState.selectedColumnIndex
        val pkIcon = if (col.isPrimaryKey) "◆" else " "
        val fkIcon = if (col.foreignKey.isDefined) "→" else " "
        val nullIcon = if (col.nullable) "?" else "!"
        val typeIcon = col.matchingType.map(_ => "⚡").getOrElse(" ")

        val prefix = if (isSelected) "> " else "  "
        val style = if (isSelected) {
          Style(fg = Some(Color.White), bg = Some(Color.Blue), addModifier = Modifier.BOLD)
        } else {
          Style(fg = Some(Color.White))
        }

        val icons = s"$pkIcon$fkIcon$nullIcon$typeIcon"

        buf.setSpans(
          listArea.x,
          y,
          Spans.from(
            Span.styled(prefix, style),
            Span.styled(icons + " ", Style(fg = Some(Color.Yellow))),
            Span.styled(col.name, style),
            Span.styled(s" ${dbTypeName(col.dbType)}", Style(fg = Some(Color.DarkGray)))
          ),
          listArea.width.toInt
        )
        y += 1
      }
    }

    // Draw horizontal divider
    for (x <- area.x + 1 until area.x + area.width.toInt - 1) {
      buf.setString(x, dividerY, "─", Style(fg = Some(Color.DarkGray)))
    }

    // Render selected column details
    var dy = detailsArea.y
    browserState.currentColumn match {
      case Some(col) =>
        // Type info
        buf.setSpans(
          detailsArea.x,
          dy,
          Spans.from(
            Span.styled("Type: ", Style(fg = Some(Color.Yellow))),
            Span.styled(dbTypeName(col.dbType), Style(fg = Some(Color.White))),
            Span.styled(if (col.nullable) " (nullable)" else " (not null)", Style(fg = Some(Color.DarkGray)))
          ),
          detailsArea.width.toInt
        )
        dy += 1

        // Primary key / Foreign key
        if (col.isPrimaryKey) {
          buf.setSpans(detailsArea.x, dy, Spans.from(Span.styled("◆ Primary Key", Style(fg = Some(Color.Green)))), detailsArea.width.toInt)
          dy += 1
        }

        col.foreignKey.foreach { fk =>
          buf.setSpans(
            detailsArea.x,
            dy,
            Spans.from(
              Span.styled("→ FK: ", Style(fg = Some(Color.Yellow))),
              Span.styled(s"${fk.targetSchema.map(_ + ".").getOrElse("")}${fk.targetTable}.${fk.targetColumn}", Style(fg = Some(Color.Cyan)))
            ),
            detailsArea.width.toInt
          )
          dy += 1
        }

        // Matching type
        col.matchingType match {
          case Some(typeName) =>
            buf.setSpans(
              detailsArea.x,
              dy,
              Spans.from(
                Span.styled("⚡ Bridge: ", Style(fg = Some(Color.Yellow))),
                Span.styled(typeName, Style(fg = Some(Color.Green)))
              ),
              detailsArea.width.toInt
            )
          case None =>
            buf.setString(detailsArea.x, dy, "[t] Create Bridge Type", Style(fg = Some(Color.DarkGray)))
        }

      case None =>
        buf.setString(detailsArea.x, dy, "Select a column", Style(fg = Some(Color.DarkGray)))
    }

    // Hint at bottom
    val hintY = area.y + area.height.toInt - 2
    val hints = browserState.currentColumn.flatMap(_.foreignKey) match {
      case Some(_) => "[g] Go to FK  [t] Type  [f] Search  [Esc] Back"
      case None    => "[t] Create Type  [f] Search  [Esc] Back"
    }
    buf.setString(area.x + 2, hintY, hints, Style(fg = Some(Color.DarkGray)))
  }

  private def renderColumnDetailsPane(f: Frame, area: Rect, state: TuiState, browserState: SchemaBrowserState): Unit = {
    val isFocused = browserState.focusedPane == BrowserPane.Right
    val borderStyle = if (isFocused) Style(fg = Some(Color.Cyan)) else Style(fg = Some(Color.DarkGray))

    val block = BlockWidget(
      title = Some(Spans.from(Span.styled("Details", Style(fg = Some(Color.Cyan))))),
      borders = Borders.ALL,
      borderType = BlockWidget.BorderType.Rounded,
      borderStyle = borderStyle
    )
    f.renderWidget(block, area)

    val innerArea = Rect(
      x = area.x + 2,
      y = area.y + 1,
      width = area.width - 4,
      height = area.height - 3
    )

    val buf = f.buffer
    var y = innerArea.y

    browserState.currentColumn match {
      case Some(col) =>
        buf.setSpans(
          innerArea.x,
          y,
          Spans.from(
            Span.styled("Column: ", Style(fg = Some(Color.Yellow))),
            Span.styled(col.name, Style(fg = Some(Color.White), addModifier = Modifier.BOLD))
          ),
          innerArea.width.toInt
        )
        y += 1

        buf.setSpans(
          innerArea.x,
          y,
          Spans.from(
            Span.styled("Type: ", Style(fg = Some(Color.Yellow))),
            Span.styled(dbTypeName(col.dbType), Style(fg = Some(Color.White)))
          ),
          innerArea.width.toInt
        )
        y += 1

        buf.setSpans(
          innerArea.x,
          y,
          Spans.from(
            Span.styled("Nullable: ", Style(fg = Some(Color.Yellow))),
            Span.styled(if (col.nullable) "yes" else "no", Style(fg = Some(if (col.nullable) Color.Gray else Color.Green)))
          ),
          innerArea.width.toInt
        )
        y += 1

        if (col.isPrimaryKey) {
          buf.setSpans(
            innerArea.x,
            y,
            Spans.from(
              Span.styled("◆ ", Style(fg = Some(Color.Yellow))),
              Span.styled("Primary Key", Style(fg = Some(Color.Green)))
            ),
            innerArea.width.toInt
          )
          y += 1
        }

        col.foreignKey.foreach { fk =>
          y += 1
          buf.setSpans(
            innerArea.x,
            y,
            Spans.from(
              Span.styled("→ FK: ", Style(fg = Some(Color.Yellow))),
              Span.styled(s"${fk.targetSchema.map(_ + ".").getOrElse("")}${fk.targetTable}.${fk.targetColumn}", Style(fg = Some(Color.Cyan)))
            ),
            innerArea.width.toInt
          )
          y += 1
          buf.setString(innerArea.x + 2, y, "[g] Go to FK target", Style(fg = Some(Color.DarkGray)))
          y += 1
        }

        col.comment.foreach { comment =>
          y += 1
          buf.setString(innerArea.x, y, "Comment:", Style(fg = Some(Color.Yellow)))
          y += 1
          buf.setString(innerArea.x + 2, y, comment.take(innerArea.width.toInt - 4), Style(fg = Some(Color.Gray)))
          y += 1
        }

        col.defaultValue.foreach { default =>
          buf.setSpans(
            innerArea.x,
            y,
            Spans.from(
              Span.styled("Default: ", Style(fg = Some(Color.Yellow))),
              Span.styled(default, Style(fg = Some(Color.Gray)))
            ),
            innerArea.width.toInt
          )
          y += 1
        }

        y += 1
        col.matchingType match {
          case Some(typeName) =>
            buf.setSpans(
              innerArea.x,
              y,
              Spans.from(
                Span.styled("⚡ Bridge Type: ", Style(fg = Some(Color.Yellow))),
                Span.styled(typeName, Style(fg = Some(Color.Green)))
              ),
              innerArea.width.toInt
            )
          case None =>
            buf.setString(innerArea.x, y, "[t] Create Bridge Type", Style(fg = Some(Color.DarkGray)))
        }

      case None =>
        buf.setString(innerArea.x, y, "Select a column", Style(fg = Some(Color.DarkGray)))
    }

    val hintY = area.y + area.height.toInt - 2
    val hints = browserState.currentColumn.flatMap(_.foreignKey) match {
      case Some(_) => "[Enter] Edit  [g] Go to FK  [t] Create Type  [f] Search  [Esc] Back"
      case None    => "[Enter] Edit  [t] Create Type  [f] Search  [Esc] Back"
    }
    buf.setString(area.x + 2, hintY, hints, Style(fg = Some(Color.DarkGray)))
  }
}

package typr.cli.tui

import io.circe.Json
import typr.cli.config.TyprConfig
import typr.cli.tui.components.*
import typr.config.generated.DatabaseSource
import typr.config.generated.DuckdbSource
import typr.config.generated.Output
import java.nio.file.Path

sealed trait AppScreen
object AppScreen {
  case class Splash(state: SplashState) extends AppScreen
  case class MainMenu(selectedIndex: Int) extends AppScreen
  case class SourceList(selectedIndex: Int, testResults: Map[String, ConnectionTestResult]) extends AppScreen
  case class OutputList(selectedIndex: Int) extends AppScreen
  case class TypeList(selectedIndex: Int, typeKindFilter: Option[BridgeTypeKind]) extends AppScreen
  case class SourceWizard(state: SourceWizardState) extends AppScreen
  case class OutputWizard(state: OutputWizardState) extends AppScreen
  case class TypeWizard(state: TypeWizardState) extends AppScreen
  case class CompositeWizard(state: CompositeWizardState) extends AppScreen
  case class DomainTypeBuilder(state: DomainTypeBuilderState) extends AppScreen
  case class SourceEditor(sourceName: String, state: SourceEditorState) extends AppScreen
  case class OutputEditor(outputName: String, state: OutputEditorState) extends AppScreen
  case class TypeEditor(typeName: String, state: TypeEditorState) extends AppScreen
  case class CompositeEditor(typeName: String, state: CompositeEditorState) extends AppScreen
  case class ConnectionTest(sourceName: String, result: ConnectionTestResult) extends AppScreen
  case class Generating(state: GeneratingState) extends AppScreen
  case class SchemaBrowser(state: SchemaBrowserState) extends AppScreen
  case class SpecBrowser(state: SpecBrowserState) extends AppScreen
  case class SourceView(state: SourceViewState) extends AppScreen
  case class BrowserSourcePicker(state: BrowserSourcePickerState) extends AppScreen
  case class SlidingDemo(state: SlidingDemoState) extends AppScreen
}

case class SplashState(
    startTime: Long,
    frame: Int
) {
  def elapsed: Long = System.currentTimeMillis() - startTime
  def progress: Double = math.min(1.0, elapsed / 1500.0)
  def isDone: Boolean = elapsed >= 1500
  def tick: SplashState = copy(frame = frame + 1)
}

object SplashState {
  def initial: SplashState = SplashState(
    startTime = System.currentTimeMillis(),
    frame = 0
  )
}

// ═══════════════════════════════════════════════════════════════════════════════
// Navigation System - Location-based navigation with history stack
// ═══════════════════════════════════════════════════════════════════════════════

/** Unified navigation destination - represents WHERE the user is */
sealed trait Location
object Location {
  case object MainMenu extends Location
  case object SourceList extends Location
  case object OutputList extends Location
  case class TypeList(filter: Option[BridgeTypeKind]) extends Location
  case object SchemaBrowserPicker extends Location
  case object SlidingDemo extends Location
  case object Generating extends Location

  case class SourceWizard(step: SourceWizardStep) extends Location
  case class OutputWizard(step: OutputWizardStep) extends Location
  case class TypeWizard(step: TypeWizardStep) extends Location
  case class CompositeWizard(step: CompositeWizardStep) extends Location
  case object DomainTypeBuilder extends Location

  case class SourceEditor(sourceName: String) extends Location
  case class OutputEditor(outputName: String) extends Location
  case class TypeEditor(typeName: String) extends Location
  case class CompositeEditor(typeName: String) extends Location
  case class DomainTypeEditor(typeName: String) extends Location

  case class SchemaBrowser(
      sourceName: SourceName,
      level: SchemaBrowserLevel,
      schema: Option[String],
      table: Option[String]
  ) extends Location

  case class SpecBrowser(sourceName: SourceName) extends Location

  case class ConnectionTest(sourceName: String) extends Location
  case class SourceView(sourceName: String) extends Location
}

/** Identifies a focusable element on the current screen */
sealed trait FocusableId
object FocusableId {
  case class Field(name: String) extends FocusableId
  case class Button(name: String) extends FocusableId
  case class ListItem(index: Int) extends FocusableId
}

/** Current focus state - what element is focused and how to cycle */
case class FocusState(
    currentId: Option[FocusableId],
    focusOrder: List[FocusableId]
) {
  def tabNext: FocusState = currentId match {
    case None => copy(currentId = focusOrder.headOption)
    case Some(current) =>
      val idx = focusOrder.indexOf(current)
      if (idx < 0) copy(currentId = focusOrder.headOption)
      else {
        val nextIdx = (idx + 1) % focusOrder.length
        copy(currentId = focusOrder.lift(nextIdx))
      }
  }

  def tabPrev: FocusState = currentId match {
    case None => copy(currentId = focusOrder.lastOption)
    case Some(current) =>
      val idx = focusOrder.indexOf(current)
      if (idx < 0) copy(currentId = focusOrder.lastOption)
      else {
        val prevIdx = if (idx <= 0) focusOrder.length - 1 else idx - 1
        copy(currentId = focusOrder.lift(prevIdx))
      }
  }

  def isFocused(id: FocusableId): Boolean = currentId.contains(id)

  def setFocus(id: FocusableId): FocusState = copy(currentId = Some(id))

  def clearFocus: FocusState = copy(currentId = None)
}

object FocusState {
  def initial: FocusState = FocusState(None, Nil)

  def withOrder(order: List[FocusableId]): FocusState =
    FocusState(order.headOption, order)
}

/** Animation phase for sliding transitions */
sealed trait SlidePhase
object SlidePhase {
  case object Idle extends SlidePhase
  case object Animating extends SlidePhase
}

/** Direction of slide animation */
sealed trait SlideDirection
object SlideDirection {
  case object Forward extends SlideDirection
  case object Backward extends SlideDirection
}

/** Animation state for smooth navigation transitions */
case class NavSlideAnimation(
    phase: SlidePhase,
    offset: Double,
    direction: SlideDirection
) {
  def tick: NavSlideAnimation = phase match {
    case SlidePhase.Idle => this
    case SlidePhase.Animating =>
      val step = 0.12
      val newOffset = math.min(1.0, offset + step)
      if (newOffset >= 1.0) copy(phase = SlidePhase.Idle, offset = 1.0)
      else copy(offset = newOffset)
  }

  def isAnimating: Boolean = phase == SlidePhase.Animating
}

object NavSlideAnimation {
  def none: NavSlideAnimation = NavSlideAnimation(SlidePhase.Idle, 1.0, SlideDirection.Forward)
  def forward: NavSlideAnimation = NavSlideAnimation(SlidePhase.Animating, 0.0, SlideDirection.Forward)
  def backward: NavSlideAnimation = NavSlideAnimation(SlidePhase.Animating, 0.0, SlideDirection.Backward)
}

/** Confirmation dialog types */
sealed trait ConfirmationDialog
object ConfirmationDialog {
  case class UnsavedChanges(focusedButton: Int) extends ConfirmationDialog
  case class ConfirmExit(focusedButton: Int) extends ConfirmationDialog
}

/** Complete navigation state including history stack */
case class NavigationState(
    current: Location,
    previous: Option[Location],
    history: List[(Location, AppScreen)],
    animation: NavSlideAnimation,
    focus: FocusState,
    pendingConfirmation: Option[ConfirmationDialog],
    hoverCol: Option[Int],
    hoverRow: Option[Int]
) {
  def goTo(location: Location, currentScreen: AppScreen): NavigationState = {
    if (animation.isAnimating) this
    else
      copy(
        current = location,
        previous = Some(current),
        history = (current, currentScreen) :: history.take(49),
        animation = NavSlideAnimation.forward,
        focus = FocusState.initial
      )
  }

  def goBack: Option[(NavigationState, AppScreen)] = {
    if (animation.isAnimating) None
    else
      history match {
        case (prevLocation, prevScreen) :: rest =>
          Some(
            (
              copy(
                current = prevLocation,
                previous = Some(current),
                history = rest,
                animation = NavSlideAnimation.backward,
                focus = FocusState.initial
              ),
              prevScreen
            )
          )
        case Nil => None
      }
  }

  def replaceTop(location: Location): NavigationState = {
    if (animation.isAnimating) this
    else
      copy(
        current = location,
        animation = NavSlideAnimation.forward,
        focus = FocusState.initial
      )
  }

  def tick: NavigationState = {
    if (animation.isAnimating) copy(animation = animation.tick)
    else this
  }

  def updateHover(col: Int, row: Int): NavigationState =
    copy(hoverCol = Some(col), hoverRow = Some(row))

  def showConfirmation(dialog: ConfirmationDialog): NavigationState =
    copy(pendingConfirmation = Some(dialog))

  def dismissConfirmation: NavigationState =
    copy(pendingConfirmation = None)

  def canGoBack: Boolean = history.nonEmpty
}

object NavigationState {
  def initial: NavigationState = NavigationState(
    current = Location.MainMenu,
    previous = None,
    history = Nil,
    animation = NavSlideAnimation.none,
    focus = FocusState.initial,
    pendingConfirmation = None,
    hoverCol = None,
    hoverRow = None
  )
}

/** State for the sliding pane demo */
case class SlidingDemoState(
    panes: List[String],
    offset: Double,
    targetPaneIndex: Int,
    animating: Boolean,
    goingForward: Boolean,
    hoverCol: Option[Int],
    hoverRow: Option[Int],
    focusedButtonIndex: Int
) {
  def currentPaneIndex: Int = math.round(offset).toInt

  /** Number of buttons on the current pane */
  def buttonCount: Int = {
    val idx = currentPaneIndex
    var count = 0
    if (idx > 0) count += 1 // "< Prev" button
    if (idx < panes.length - 1) count += 1 // "Next >" button
    count
  }

  def goToPane(index: Int): SlidingDemoState = {
    if (index >= 0 && index < panes.length && !animating && index != targetPaneIndex) {
      // Reset focus to 0 when changing panes
      copy(targetPaneIndex = index, animating = true, goingForward = index > offset.toInt, focusedButtonIndex = 0)
    } else this
  }

  def focusNext: SlidingDemoState = {
    val count = buttonCount
    if (count == 0) this
    else copy(focusedButtonIndex = (focusedButtonIndex + 1) % count)
  }

  def focusPrev: SlidingDemoState = {
    val count = buttonCount
    if (count == 0) this
    else copy(focusedButtonIndex = (focusedButtonIndex - 1 + count) % count)
  }

  def tick: SlidingDemoState = {
    if (!animating) this
    else {
      val target = targetPaneIndex.toDouble
      val diff = target - offset
      val step = 0.12

      if (math.abs(diff) <= step) {
        copy(offset = target, animating = false, focusedButtonIndex = 0)
      } else {
        copy(offset = offset + math.signum(diff) * step)
      }
    }
  }

  def updateHover(col: Int, row: Int): SlidingDemoState = {
    copy(hoverCol = Some(col), hoverRow = Some(row))
  }
}

object SlidingDemoState {
  def initial: SlidingDemoState = SlidingDemoState(
    panes = List("Pane A", "Pane B", "Pane C", "Pane D"),
    offset = 0.0,
    targetPaneIndex = 0,
    animating = false,
    goingForward = true,
    hoverCol = None,
    hoverRow = None,
    focusedButtonIndex = 0
  )
}

// ═══════════════════════════════════════════════════════════════════════════════
// Sliding Pane Animation
// ═══════════════════════════════════════════════════════════════════════════════

/** Animation state for smooth pane transitions */
case class SlideAnimation(
    currentPane: Int,
    targetPane: Int,
    offset: Double,
    animating: Boolean
) {
  def slideLeft(paneCount: Int): SlideAnimation = {
    if (currentPane > 0 && !animating) {
      copy(targetPane = currentPane - 1, animating = true)
    } else this
  }

  def slideRight(paneCount: Int): SlideAnimation = {
    if (currentPane < paneCount - 1 && !animating) {
      copy(targetPane = currentPane + 1, animating = true)
    } else this
  }

  def tick: SlideAnimation = {
    if (!animating) this
    else {
      val target = targetPane.toDouble
      val speed = 0.35
      val newOffset = if (offset < target) {
        math.min(target, offset + speed)
      } else if (offset > target) {
        math.max(target, offset - speed)
      } else offset

      if (math.abs(newOffset - target) < 0.02) {
        copy(currentPane = targetPane, offset = target, animating = false)
      } else {
        copy(offset = newOffset)
      }
    }
  }
}

object SlideAnimation {
  def initial: SlideAnimation = SlideAnimation(
    currentPane = 0,
    targetPane = 0,
    offset = 0.0,
    animating = false
  )
}

/** State for source configuration editor */
case class SourceViewState(
    sourceName: String,
    sourceJson: Json,
    editorState: SourceEditorState
)

object SourceViewState {
  def fromSource(sourceName: String, sourceJson: Json): SourceViewState = {
    val editorState = SourceEditorState.fromJson(sourceJson)
    SourceViewState(
      sourceName = sourceName,
      sourceJson = sourceJson,
      editorState = editorState
    )
  }
}

/** State for schema browser source picker */
case class BrowserSourcePickerState(
    selectedIndex: Int
)

sealed trait GeneratingPhase
object GeneratingPhase {
  case object Starting extends GeneratingPhase
  case object FetchingSources extends GeneratingPhase
  case object GeneratingCode extends GeneratingPhase
  case object Completed extends GeneratingPhase
  case class Failed(error: String) extends GeneratingPhase
}

sealed trait SourceFetchStatus
object SourceFetchStatus {
  case object Pending extends SourceFetchStatus
  case object Fetching extends SourceFetchStatus
  case class Done(tables: Int, views: Int, enums: Int, warnings: Int) extends SourceFetchStatus
  case class Failed(error: String) extends SourceFetchStatus
}

case class SourceFetchProgress(
    name: String,
    sourceType: String,
    status: SourceFetchStatus,
    currentStep: String,
    startTime: Option[Long],
    endTime: Option[Long]
) {
  def durationMs: Option[Long] = for {
    start <- startTime
    end <- endTime.orElse(Some(System.currentTimeMillis()))
  } yield end - start

  def durationStr: String = durationMs match {
    case Some(ms) if ms < 1000 => s"${ms}ms"
    case Some(ms)              => f"${ms / 1000.0}%.1fs"
    case None                  => ""
  }
}

case class GeneratingState(
    phase: GeneratingPhase,
    sourceFetches: List[SourceFetchProgress],
    tracker: Option[typr.cli.commands.Generate.ProgressTracker],
    logger: Option[TuiLogger],
    startTime: Long,
    endTime: Option[Long],
    successful: Int,
    failed: Int,
    skipped: Int,
    filesWritten: Int
) {
  def elapsedSeconds: Double = {
    val end = endTime.getOrElse(System.currentTimeMillis())
    (end - startTime) / 1000.0
  }
}

object GeneratingState {
  def initial: GeneratingState = GeneratingState(
    phase = GeneratingPhase.Starting,
    sourceFetches = Nil,
    tracker = None,
    logger = None,
    startTime = System.currentTimeMillis(),
    endTime = None,
    successful = 0,
    failed = 0,
    skipped = 0,
    filesWritten = 0
  )
}

sealed trait ConnectionTestResult
object ConnectionTestResult {
  case object Pending extends ConnectionTestResult
  case object Testing extends ConnectionTestResult
  case class Success(message: String) extends ConnectionTestResult
  case class Failure(error: String) extends ConnectionTestResult
}

sealed trait SourceWizardStep
object SourceWizardStep {
  case object SelectType extends SourceWizardStep
  case object ScanSpecs extends SourceWizardStep
  case object EnterName extends SourceWizardStep
  case object EnterHost extends SourceWizardStep
  case object EnterPort extends SourceWizardStep
  case object EnterDatabase extends SourceWizardStep
  case object EnterService extends SourceWizardStep
  case object EnterUsername extends SourceWizardStep
  case object EnterPassword extends SourceWizardStep
  case object EnterPath extends SourceWizardStep
  case object EnterSchemaSql extends SourceWizardStep
  case object TestConnection extends SourceWizardStep
  case object Review extends SourceWizardStep
}

case class SourceWizardState(
    step: SourceWizardStep,
    sourceType: Option[String],
    name: String,
    host: String,
    port: String,
    database: String,
    service: String,
    username: String,
    password: String,
    path: String,
    schemaSql: String,
    typeSelect: SelectInput[String],
    connectionResult: ConnectionTestResult,
    error: Option[String],
    discoveredSpecs: List[typr.cli.tui.util.SourceScanner.DiscoveredSource],
    selectedDiscoveredIndex: Int,
    scanning: Boolean
) {
  def defaultPort: String = sourceType match {
    case Some("postgresql")              => "5432"
    case Some("mariadb") | Some("mysql") => "3306"
    case Some("sqlserver")               => "1433"
    case Some("oracle")                  => "1521"
    case Some("db2")                     => "50000"
    case _                               => ""
  }

  def isSpecSource: Boolean = sourceType.exists(t => t == "openapi" || t == "jsonschema")

  def nextStep: SourceWizardStep = step match {
    case SourceWizardStep.SelectType =>
      if (isSpecSource) SourceWizardStep.ScanSpecs
      else SourceWizardStep.EnterName
    case SourceWizardStep.ScanSpecs =>
      SourceWizardStep.EnterName
    case SourceWizardStep.EnterName =>
      if (sourceType.contains("duckdb") || isSpecSource) SourceWizardStep.EnterPath
      else SourceWizardStep.EnterHost
    case SourceWizardStep.EnterPath =>
      if (isSpecSource) SourceWizardStep.Review
      else SourceWizardStep.EnterSchemaSql
    case SourceWizardStep.EnterSchemaSql =>
      SourceWizardStep.Review
    case SourceWizardStep.EnterHost =>
      SourceWizardStep.EnterPort
    case SourceWizardStep.EnterPort =>
      SourceWizardStep.EnterDatabase
    case SourceWizardStep.EnterDatabase =>
      if (sourceType.contains("oracle")) SourceWizardStep.EnterService
      else SourceWizardStep.EnterUsername
    case SourceWizardStep.EnterService =>
      SourceWizardStep.EnterUsername
    case SourceWizardStep.EnterUsername =>
      SourceWizardStep.EnterPassword
    case SourceWizardStep.EnterPassword =>
      SourceWizardStep.TestConnection
    case SourceWizardStep.TestConnection =>
      SourceWizardStep.Review
    case SourceWizardStep.Review =>
      SourceWizardStep.Review
  }

  def previousStep: SourceWizardStep = step match {
    case SourceWizardStep.SelectType =>
      SourceWizardStep.SelectType
    case SourceWizardStep.ScanSpecs =>
      SourceWizardStep.SelectType
    case SourceWizardStep.EnterName =>
      if (isSpecSource) SourceWizardStep.ScanSpecs
      else SourceWizardStep.SelectType
    case SourceWizardStep.EnterHost =>
      SourceWizardStep.EnterName
    case SourceWizardStep.EnterPath =>
      SourceWizardStep.EnterName
    case SourceWizardStep.EnterSchemaSql =>
      SourceWizardStep.EnterPath
    case SourceWizardStep.EnterPort =>
      SourceWizardStep.EnterHost
    case SourceWizardStep.EnterDatabase =>
      SourceWizardStep.EnterPort
    case SourceWizardStep.EnterService =>
      SourceWizardStep.EnterDatabase
    case SourceWizardStep.EnterUsername =>
      if (sourceType.contains("oracle")) SourceWizardStep.EnterService
      else SourceWizardStep.EnterDatabase
    case SourceWizardStep.EnterPassword =>
      SourceWizardStep.EnterUsername
    case SourceWizardStep.TestConnection =>
      SourceWizardStep.EnterPassword
    case SourceWizardStep.Review =>
      if (isSpecSource) SourceWizardStep.EnterPath
      else if (sourceType.contains("duckdb")) SourceWizardStep.EnterSchemaSql
      else SourceWizardStep.TestConnection
  }

  def toDatabaseSource: DatabaseSource = DatabaseSource(
    `type` = sourceType,
    host = if (host.nonEmpty) Some(host) else None,
    port = if (port.nonEmpty) Some(port.toLong) else None,
    database = if (database.nonEmpty) Some(database) else None,
    username = if (username.nonEmpty) Some(username) else None,
    password = if (password.nonEmpty) Some(password) else None,
    service = if (service.nonEmpty) Some(service) else None,
    sid = None,
    url = None,
    schemas = None,
    ssl = None,
    connection_timeout = None,
    encrypt = None,
    trust_server_certificate = None,
    schema_mode = None,
    sql_scripts = None,
    schema_sql = None,
    selectors = None,
    types = None,
    type_override = None
  )

  def toDuckdbSource: DuckdbSource = DuckdbSource(
    `type` = Some("duckdb"),
    path = path,
    schema_sql = if (schemaSql.nonEmpty) Some(schemaSql) else None,
    sql_scripts = None,
    selectors = None,
    types = None
  )

  def toOpenApiSource: typr.config.generated.OpenapiSource = typr.config.generated.OpenapiSource(
    spec = if (path.nonEmpty) Some(path) else None,
    specs = None,
    `type` = Some("openapi"),
    types = None
  )

  def toJsonSchemaSource: typr.config.generated.JsonschemaSource = typr.config.generated.JsonschemaSource(
    spec = if (path.nonEmpty) Some(path) else None,
    specs = None,
    `type` = Some("jsonschema"),
    types = None
  )

  def toJson: Json = sourceType match {
    case Some("duckdb") =>
      import io.circe.syntax.*
      toDuckdbSource.asJson
    case Some("openapi") =>
      import io.circe.syntax.*
      toOpenApiSource.asJson
    case Some("jsonschema") =>
      import io.circe.syntax.*
      toJsonSchemaSource.asJson
    case _ =>
      import io.circe.syntax.*
      toDatabaseSource.asJson
  }
}

object SourceWizardState {
  val dbTypes: List[(String, String)] = List(
    ("postgresql", "PostgreSQL"),
    ("mariadb", "MariaDB / MySQL"),
    ("sqlserver", "SQL Server"),
    ("oracle", "Oracle"),
    ("duckdb", "DuckDB (embedded)"),
    ("db2", "IBM DB2"),
    ("openapi", "OpenAPI / Swagger"),
    ("jsonschema", "JSON Schema")
  )

  def initial: SourceWizardState = SourceWizardState(
    step = SourceWizardStep.SelectType,
    sourceType = None,
    name = "",
    host = "localhost",
    port = "",
    database = "",
    service = "",
    username = "",
    password = "",
    path = "",
    schemaSql = "",
    typeSelect = SelectInput("Database Type:", dbTypes),
    connectionResult = ConnectionTestResult.Pending,
    error = None,
    discoveredSpecs = Nil,
    selectedDiscoveredIndex = 0,
    scanning = false
  )
}

sealed trait OutputWizardStep
object OutputWizardStep {
  case object EnterName extends OutputWizardStep
  case object SelectSources extends OutputWizardStep
  case object EnterPath extends OutputWizardStep
  case object EnterPackage extends OutputWizardStep
  case object SelectLanguage extends OutputWizardStep
  case object SelectDbLib extends OutputWizardStep
  case object SelectJsonLib extends OutputWizardStep
  case object ScalaDialect extends OutputWizardStep
  case object ScalaDsl extends OutputWizardStep
  case object Review extends OutputWizardStep
}

case class OutputWizardState(
    step: OutputWizardStep,
    name: String,
    sources: String,
    path: String,
    pkg: String,
    language: Option[String],
    dbLib: Option[String],
    jsonLib: Option[String],
    scalaDialect: Option[String],
    scalaDsl: Option[String],
    languageSelect: SelectInput[String],
    dbLibSelect: SelectInput[String],
    jsonLibSelect: SelectInput[String],
    dialectSelect: SelectInput[String],
    dslSelect: SelectInput[String],
    error: Option[String]
) {
  def nextStep: OutputWizardStep = step match {
    case OutputWizardStep.EnterName =>
      OutputWizardStep.SelectSources
    case OutputWizardStep.SelectSources =>
      OutputWizardStep.EnterPath
    case OutputWizardStep.EnterPath =>
      OutputWizardStep.EnterPackage
    case OutputWizardStep.EnterPackage =>
      OutputWizardStep.SelectLanguage
    case OutputWizardStep.SelectLanguage =>
      OutputWizardStep.SelectDbLib
    case OutputWizardStep.SelectDbLib =>
      OutputWizardStep.SelectJsonLib
    case OutputWizardStep.SelectJsonLib =>
      if (language.contains("scala")) OutputWizardStep.ScalaDialect
      else OutputWizardStep.Review
    case OutputWizardStep.ScalaDialect =>
      OutputWizardStep.ScalaDsl
    case OutputWizardStep.ScalaDsl =>
      OutputWizardStep.Review
    case OutputWizardStep.Review =>
      OutputWizardStep.Review
  }

  def previousStep: OutputWizardStep = step match {
    case OutputWizardStep.EnterName =>
      OutputWizardStep.EnterName
    case OutputWizardStep.SelectSources =>
      OutputWizardStep.EnterName
    case OutputWizardStep.EnterPath =>
      OutputWizardStep.SelectSources
    case OutputWizardStep.EnterPackage =>
      OutputWizardStep.EnterPath
    case OutputWizardStep.SelectLanguage =>
      OutputWizardStep.EnterPackage
    case OutputWizardStep.SelectDbLib =>
      OutputWizardStep.SelectLanguage
    case OutputWizardStep.SelectJsonLib =>
      OutputWizardStep.SelectDbLib
    case OutputWizardStep.ScalaDialect =>
      OutputWizardStep.SelectJsonLib
    case OutputWizardStep.ScalaDsl =>
      OutputWizardStep.ScalaDialect
    case OutputWizardStep.Review =>
      if (language.contains("scala")) OutputWizardStep.ScalaDsl
      else OutputWizardStep.SelectJsonLib
  }

  def toOutput: Output = {
    import io.circe.Json
    Output(
      bridge = None,
      db_lib = dbLib,
      effect_type = None,
      framework = None,
      json = jsonLib,
      language = language.getOrElse("java"),
      matchers = None,
      `package` = pkg,
      path = path,
      scala = (scalaDialect, scalaDsl) match {
        case (Some(dialect), Some(dsl)) =>
          Some(
            Json.obj(
              "dialect" -> Json.fromString(dialect),
              "dsl" -> Json.fromString(dsl)
            )
          )
        case (Some(dialect), None) =>
          Some(Json.obj("dialect" -> Json.fromString(dialect)))
        case _ => None
      },
      sources = if (sources == "*" || sources.isEmpty) None else Some(typr.config.generated.StringOrArrayString(sources))
    )
  }
}

object OutputWizardState {
  val languages: List[(String, String)] = List(
    ("java", "Java"),
    ("kotlin", "Kotlin"),
    ("scala", "Scala")
  )

  val dbLibs: List[(String, String)] = List(
    ("foundations", "Foundations (Modern)"),
    ("anorm", "Anorm (Legacy, Scala only)"),
    ("doobie", "Doobie (Legacy, Scala only)"),
    ("zio-jdbc", "ZIO-JDBC (Legacy, Scala only)")
  )

  val jsonLibs: List[(String, String)] = List(
    ("jackson", "Jackson"),
    ("circe", "Circe (Scala only)"),
    ("play-json", "Play JSON (Scala only)"),
    ("zio-json", "ZIO JSON (Scala only)")
  )

  val dialects: List[(String, String)] = List(
    ("scala3", "Scala 3"),
    ("scala2", "Scala 2.13")
  )

  val dsls: List[(String, String)] = List(
    ("scala", "Scala DSL"),
    ("java", "Java DSL"),
    ("legacy", "Legacy (Anorm/Doobie style)")
  )

  def initial: OutputWizardState = OutputWizardState(
    step = OutputWizardStep.EnterName,
    name = "",
    sources = "*",
    path = "generated",
    pkg = "generated",
    language = None,
    dbLib = None,
    jsonLib = None,
    scalaDialect = None,
    scalaDsl = None,
    languageSelect = SelectInput("Language:", languages),
    dbLibSelect = SelectInput("Database Library:", dbLibs),
    jsonLibSelect = SelectInput("JSON Library:", jsonLibs),
    dialectSelect = SelectInput("Scala Dialect:", dialects),
    dslSelect = SelectInput("DSL Style:", dsls),
    error = None
  )
}

sealed trait SourceEditorField
object SourceEditorField {
  case object Type extends SourceEditorField
  case object Host extends SourceEditorField
  case object Port extends SourceEditorField
  case object Database extends SourceEditorField
  case object Service extends SourceEditorField
  case object Username extends SourceEditorField
  case object Password extends SourceEditorField
  case object Path extends SourceEditorField
  case object SchemaSql extends SourceEditorField
  case object SpecPath extends SourceEditorField
  case object TestConnection extends SourceEditorField
  case object Save extends SourceEditorField
}

case class SourceEditorState(
    sourceType: Option[String],
    host: String,
    port: String,
    database: String,
    service: String,
    username: String,
    password: String,
    path: String,
    schemaSql: String,
    specPath: String,
    selectedField: SourceEditorField,
    typeSelect: SelectInput[String],
    modified: Boolean,
    connectionResult: ConnectionTestResult
) {
  def isDuckDb: Boolean = sourceType.contains("duckdb")
  def isSpecSource: Boolean = sourceType.contains("openapi") || sourceType.contains("jsonschema")

  def toJson: Json = {
    import io.circe.syntax.*
    if (isSpecSource) {
      Json.obj(
        "type" -> sourceType.map(Json.fromString).getOrElse(Json.Null),
        "spec" -> Json.fromString(specPath)
      )
    } else if (isDuckDb) {
      Json
        .obj(
          "type" -> Json.fromString("duckdb"),
          "path" -> Json.fromString(path)
        )
        .deepMerge(
          if (schemaSql.nonEmpty) Json.obj("schema_sql" -> Json.fromString(schemaSql)) else Json.obj()
        )
    } else {
      val base = Json.obj(
        "type" -> sourceType.map(Json.fromString).getOrElse(Json.Null)
      )
      val withHost = if (host.nonEmpty) base.deepMerge(Json.obj("host" -> Json.fromString(host))) else base
      val withPort = if (port.nonEmpty) withHost.deepMerge(Json.obj("port" -> Json.fromInt(port.toIntOption.getOrElse(0)))) else withHost
      val withDb = if (database.nonEmpty) withPort.deepMerge(Json.obj("database" -> Json.fromString(database))) else withPort
      val withService = if (service.nonEmpty) withDb.deepMerge(Json.obj("service" -> Json.fromString(service))) else withDb
      val withUser = if (username.nonEmpty) withService.deepMerge(Json.obj("username" -> Json.fromString(username))) else withService
      val withPass = if (password.nonEmpty) withUser.deepMerge(Json.obj("password" -> Json.fromString(password))) else withUser
      withPass
    }
  }

  def fields: List[SourceEditorField] = {
    val baseFields = if (isSpecSource) {
      List(SourceEditorField.Type, SourceEditorField.SpecPath)
    } else if (isDuckDb) {
      List(SourceEditorField.Type, SourceEditorField.Path, SourceEditorField.SchemaSql)
    } else if (sourceType.contains("oracle")) {
      List(
        SourceEditorField.Type,
        SourceEditorField.Host,
        SourceEditorField.Port,
        SourceEditorField.Service,
        SourceEditorField.Username,
        SourceEditorField.Password
      )
    } else {
      List(
        SourceEditorField.Type,
        SourceEditorField.Host,
        SourceEditorField.Port,
        SourceEditorField.Database,
        SourceEditorField.Username,
        SourceEditorField.Password
      )
    }
    baseFields ++ List(SourceEditorField.TestConnection, SourceEditorField.Save)
  }

  def nextField: SourceEditorField = {
    val fs = fields
    val idx = fs.indexOf(selectedField)
    if (idx >= 0 && idx < fs.length - 1) fs(idx + 1) else fs.head
  }

  def prevField: SourceEditorField = {
    val fs = fields
    val idx = fs.indexOf(selectedField)
    if (idx > 0) fs(idx - 1) else fs.last
  }
}

object SourceEditorState {
  def fromJson(json: Json): SourceEditorState = {
    val cursor = json.hcursor
    val sourceType = cursor.get[String]("type").toOption
    val host = cursor.get[String]("host").getOrElse("")
    val port = cursor.get[Long]("port").map(_.toString).getOrElse("")
    val database = cursor.get[String]("database").getOrElse("")
    val service = cursor.get[String]("service").getOrElse("")
    val username = cursor.get[String]("username").getOrElse("")
    val password = cursor.get[String]("password").getOrElse("")
    val path = cursor.get[String]("path").getOrElse(":memory:")
    val schemaSql = cursor.get[String]("schema_sql").getOrElse("")
    val specPath = cursor
      .get[String]("spec")
      .orElse(
        cursor.downField("specs").downArray.as[String]
      )
      .getOrElse("")

    val typeIdx = SourceWizardState.dbTypes.indexWhere(_._1 == sourceType.getOrElse(""))
    val typeSelect = SelectInput("Database Type:", SourceWizardState.dbTypes, selectedIndex = if (typeIdx >= 0) typeIdx else 0)

    val isSpecSource = sourceType.contains("openapi") || sourceType.contains("jsonschema")
    val defaultField = if (isSpecSource) SourceEditorField.SpecPath else SourceEditorField.Host

    SourceEditorState(
      sourceType = sourceType,
      host = host,
      port = port,
      database = database,
      service = service,
      username = username,
      password = password,
      path = path,
      schemaSql = schemaSql,
      specPath = specPath,
      selectedField = defaultField,
      typeSelect = typeSelect,
      modified = false,
      connectionResult = ConnectionTestResult.Pending
    )
  }
}

sealed trait OutputEditorField
object OutputEditorField {
  case object Sources extends OutputEditorField
  case object Path extends OutputEditorField
  case object Package extends OutputEditorField
  case object Language extends OutputEditorField
  case object DbLib extends OutputEditorField
  case object JsonLib extends OutputEditorField
  case object ScalaDialect extends OutputEditorField
  case object ScalaDsl extends OutputEditorField
  case object PrimaryKeyTypes extends OutputEditorField
  case object MockRepos extends OutputEditorField
  case object TestInserts extends OutputEditorField
  case object FieldValues extends OutputEditorField
  case object Readonly extends OutputEditorField
}

sealed trait RelationType
object RelationType {
  case object Table extends RelationType
  case object View extends RelationType
}

case class RelationInfo(
    schema: Option[String],
    name: String,
    relationType: RelationType
) {
  def fullName: String = schema.map(s => s"$s.$name").getOrElse(name)
}

sealed trait MetaDbStatus
object MetaDbStatus {
  case object Pending extends MetaDbStatus
  case object Fetching extends MetaDbStatus
  case class Loaded(relations: List[RelationInfo], enums: Int, domains: Int) extends MetaDbStatus
  case class Failed(error: String) extends MetaDbStatus
}

/** Mode for relation matchers - determines which tables/views are included */
sealed trait RelationMatcherMode
object RelationMatcherMode {

  /** Match all relations (tables/views) - generates feature for everything */
  case object All extends RelationMatcherMode

  /** Match no relations - feature disabled */
  case object None extends RelationMatcherMode

  /** Custom include/exclude patterns using glob syntax */
  case object Custom extends RelationMatcherMode

  val modes: List[RelationMatcherMode] = List(All, None, Custom)

  def label(mode: RelationMatcherMode): String = mode match {
    case All    => "All"
    case None   => "None"
    case Custom => "Custom"
  }
}

/** Matcher for database relations (tables/views).
  *
  * Maps to FeatureMatcher in config - used by Output.matchers to control which relations get features like mock repos, pk types, test inserts, etc.
  *
  * Pattern syntax:
  *   - `*` matches any characters except `.`
  *   - `**` matches any characters including `.`
  *   - `?` matches single character
  *   - Patterns match against "schema.table" or just "table"
  *
  * Examples:
  *   - `public.*` - all tables in public schema
  *   - `*_view` - all tables ending in _view
  *   - `user*` - tables starting with user
  */
/** State for picker popup UI */
case class MatcherPickerState(
    /** 0 = schema list, 1 = relation list */
    activePanel: Int,
    /** Scroll offset for schema list */
    schemaOffset: Int,
    /** Selected index in schema list */
    schemaIndex: Int,
    /** Scroll offset for relation list */
    relationOffset: Int,
    /** Selected index in relation list */
    relationIndex: Int
) {
  def nextSchema(maxItems: Int): MatcherPickerState = {
    if (schemaIndex < maxItems - 1) copy(schemaIndex = schemaIndex + 1)
    else this
  }

  def prevSchema: MatcherPickerState = {
    if (schemaIndex > 0) copy(schemaIndex = schemaIndex - 1)
    else this
  }

  def nextRelation(maxItems: Int): MatcherPickerState = {
    if (relationIndex < maxItems - 1) copy(relationIndex = relationIndex + 1)
    else this
  }

  def prevRelation: MatcherPickerState = {
    if (relationIndex > 0) copy(relationIndex = relationIndex - 1)
    else this
  }

  def togglePanel: MatcherPickerState = copy(activePanel = if (activePanel == 0) 1 else 0)
  def inSchemaPanel: Boolean = activePanel == 0
  def inRelationPanel: Boolean = activePanel == 1
}

object MatcherPickerState {
  def initial: MatcherPickerState = MatcherPickerState(
    activePanel = 0,
    schemaOffset = 0,
    schemaIndex = 0,
    relationOffset = 0,
    relationIndex = 0
  )
}

case class RelationMatcher(
    mode: RelationMatcherMode,
    /** Selected schemas (empty means "all" when in Custom mode) */
    selectedSchemas: Set[String],
    /** Individual relations to include */
    selectedRelations: Set[String],
    /** Individual relations to exclude */
    excludedRelations: Set[String],
    /** Picker UI state */
    pickerState: MatcherPickerState
) {

  /** Convert to FeatureMatcher for config serialization */
  def toFeatureMatcher: Option[typr.config.generated.FeatureMatcher] = {
    import typr.config.generated.*
    mode match {
      case RelationMatcherMode.All  => Some(FeatureMatcherString("*"))
      case RelationMatcherMode.None => None
      case RelationMatcherMode.Custom =>
        val include = toIncludePatterns
        val exclude = excludedRelations.toList.sorted
        if (include.isEmpty && exclude.isEmpty) {
          None
        } else if (exclude.isEmpty && include.size == 1) {
          Some(FeatureMatcherString(include.head))
        } else if (exclude.isEmpty) {
          Some(FeatureMatcherArray(include))
        } else if (include.isEmpty || include == List("*")) {
          Some(FeatureMatcherObject(exclude = Some(exclude), include = None))
        } else {
          Some(
            FeatureMatcherObject(
              exclude = Some(exclude),
              include = Some(io.circe.Json.arr(include.map(io.circe.Json.fromString)*))
            )
          )
        }
    }
  }

  /** Convert selected schemas and relations to include patterns */
  def toIncludePatterns: List[String] = {
    val schemaPatterns = selectedSchemas.toList.sorted.map(_ + ".*")
    val relationPatterns = selectedRelations.toList.sorted
    if (schemaPatterns.isEmpty && relationPatterns.isEmpty) List("*")
    else schemaPatterns ++ relationPatterns
  }

  def summaryString: String = mode match {
    case RelationMatcherMode.All  => "all relations"
    case RelationMatcherMode.None => "disabled"
    case RelationMatcherMode.Custom =>
      val schemaCount = selectedSchemas.size
      val relCount = selectedRelations.size
      val exclCount = excludedRelations.size
      if (schemaCount == 0 && relCount == 0) "all"
      else {
        val parts = List(
          if (schemaCount > 0) Some(s"$schemaCount schemas") else None,
          if (relCount > 0) Some(s"$relCount tables") else None,
          if (exclCount > 0) Some(s"-$exclCount excluded") else None
        ).flatten
        parts.mkString(", ")
      }
  }

  def nextMode: RelationMatcher = {
    val idx = RelationMatcherMode.modes.indexOf(mode)
    val nextIdx = (idx + 1) % RelationMatcherMode.modes.size
    copy(mode = RelationMatcherMode.modes(nextIdx))
  }

  def prevMode: RelationMatcher = {
    val idx = RelationMatcherMode.modes.indexOf(mode)
    val prevIdx = if (idx == 0) RelationMatcherMode.modes.size - 1 else idx - 1
    copy(mode = RelationMatcherMode.modes(prevIdx))
  }

  /** Toggle a schema selection */
  def toggleSchema(schema: String): RelationMatcher = {
    if (selectedSchemas.contains(schema)) {
      copy(selectedSchemas = selectedSchemas - schema)
    } else {
      copy(selectedSchemas = selectedSchemas + schema)
    }
  }

  /** Toggle a relation selection */
  def toggleRelation(fullName: String): RelationMatcher = {
    if (selectedRelations.contains(fullName)) {
      copy(selectedRelations = selectedRelations - fullName)
    } else if (excludedRelations.contains(fullName)) {
      copy(excludedRelations = excludedRelations - fullName)
    } else {
      copy(selectedRelations = selectedRelations + fullName)
    }
  }

  /** Toggle exclusion of a relation */
  def toggleExclude(fullName: String): RelationMatcher = {
    if (excludedRelations.contains(fullName)) {
      copy(excludedRelations = excludedRelations - fullName)
    } else {
      copy(
        excludedRelations = excludedRelations + fullName,
        selectedRelations = selectedRelations - fullName
      )
    }
  }

  /** Check if a schema is selected */
  def isSchemaSelected(schema: String): Boolean = selectedSchemas.contains(schema)

  /** Check if a relation is included (either directly or via schema) */
  def isRelationIncluded(schema: Option[String], fullName: String): Boolean = {
    if (selectedRelations.contains(fullName)) true
    else schema.exists(selectedSchemas.contains)
  }

  /** Check if a relation is excluded */
  def isRelationExcluded(fullName: String): Boolean = excludedRelations.contains(fullName)
}

object RelationMatcher {
  def none: RelationMatcher = RelationMatcher(
    mode = RelationMatcherMode.None,
    selectedSchemas = Set.empty,
    selectedRelations = Set.empty,
    excludedRelations = Set.empty,
    pickerState = MatcherPickerState.initial
  )

  def all: RelationMatcher = RelationMatcher(
    mode = RelationMatcherMode.All,
    selectedSchemas = Set.empty,
    selectedRelations = Set.empty,
    excludedRelations = Set.empty,
    pickerState = MatcherPickerState.initial
  )

  def fromFeatureMatcher(fm: Option[typr.config.generated.FeatureMatcher]): RelationMatcher = {
    import typr.config.generated.*
    fm match {
      case None                            => none
      case Some(FeatureMatcherString("*")) => all
      case Some(FeatureMatcherString(pattern)) =>
        val (schemas, relations, excluded) = parsePattern(pattern)
        RelationMatcher(RelationMatcherMode.Custom, schemas, relations, excluded, MatcherPickerState.initial)
      case Some(FeatureMatcherArray(patterns)) =>
        val (excludePatterns, includePatterns) = patterns.partition(_.startsWith("!"))
        val (schemas, relations) = parsePatterns(includePatterns)
        val excluded = excludePatterns.map(_.drop(1)).toSet
        RelationMatcher(RelationMatcherMode.Custom, schemas, relations, excluded, MatcherPickerState.initial)
      case Some(FeatureMatcherObject(excludeOpt, includeOpt)) =>
        val includePatterns = includeOpt
          .flatMap(json => json.asString.map(List(_)).orElse(json.asArray.map(_.flatMap(_.asString).toList)))
          .getOrElse(Nil)
        val excludePatterns = excludeOpt.getOrElse(Nil)
        if (includePatterns.isEmpty && excludePatterns.isEmpty) none
        else {
          val (schemas, relations) = parsePatterns(includePatterns)
          RelationMatcher(RelationMatcherMode.Custom, schemas, relations, excludePatterns.toSet, MatcherPickerState.initial)
        }
    }
  }

  /** Parse a single pattern into schema/relation selections */
  private def parsePattern(pattern: String): (Set[String], Set[String], Set[String]) = {
    if (pattern.startsWith("!")) {
      (Set.empty, Set.empty, Set(pattern.drop(1)))
    } else if (pattern.endsWith(".*")) {
      (Set(pattern.dropRight(2)), Set.empty, Set.empty)
    } else if (pattern == "*") {
      (Set.empty, Set.empty, Set.empty)
    } else {
      (Set.empty, Set(pattern), Set.empty)
    }
  }

  /** Parse multiple patterns into schema/relation selections */
  private def parsePatterns(patterns: List[String]): (Set[String], Set[String]) = {
    val schemas = patterns.filter(_.endsWith(".*")).map(_.dropRight(2)).toSet
    val relations = patterns.filterNot(p => p.endsWith(".*") || p == "*").toSet
    (schemas, relations)
  }
}

case class OutputEditorState(
    selectedSources: Set[String],
    allSourcesMode: Boolean,
    sourcesPopupOpen: Boolean,
    sourcesPopupCursorIndex: Int,
    sourcesPopupOriginal: Option[(Set[String], Boolean)],
    path: String,
    pkg: String,
    language: Option[String],
    dbLib: Option[String],
    jsonLib: Option[String],
    scalaDialect: Option[String],
    scalaDsl: Option[String],
    primaryKeyTypes: RelationMatcher,
    mockRepos: RelationMatcher,
    testInserts: RelationMatcher,
    fieldValues: RelationMatcher,
    readonly: RelationMatcher,
    selectedField: OutputEditorField,
    languageSelect: SelectInput[String],
    dbLibSelect: SelectInput[String],
    jsonLibSelect: SelectInput[String],
    dialectSelect: SelectInput[String],
    dslSelect: SelectInput[String],
    modified: Boolean,
    metaDbStatus: Map[String, MetaDbStatus],
    matcherPreviewField: Option[OutputEditorField],
    matcherPopupField: Option[OutputEditorField],
    matcherPopupOriginal: Option[RelationMatcher]
) {
  def isScala: Boolean = language.contains("scala")

  def fields: List[OutputEditorField] = {
    val baseFields = List(
      OutputEditorField.Sources,
      OutputEditorField.Path,
      OutputEditorField.Package,
      OutputEditorField.Language,
      OutputEditorField.DbLib,
      OutputEditorField.JsonLib
    )
    val scalaFields =
      if (isScala) List(OutputEditorField.ScalaDialect, OutputEditorField.ScalaDsl)
      else Nil
    val matcherFields = List(
      OutputEditorField.PrimaryKeyTypes,
      OutputEditorField.MockRepos,
      OutputEditorField.TestInserts,
      OutputEditorField.FieldValues,
      OutputEditorField.Readonly
    )
    baseFields ++ scalaFields ++ matcherFields
  }

  def nextField: OutputEditorField = {
    val fs = fields
    val idx = fs.indexOf(selectedField)
    if (idx >= 0 && idx < fs.length - 1) fs(idx + 1) else fs.head
  }

  def prevField: OutputEditorField = {
    val fs = fields
    val idx = fs.indexOf(selectedField)
    if (idx > 0) fs(idx - 1) else fs.last
  }

  def toOutput: Output = {
    import io.circe.Json
    import typr.config.generated.*

    val scalaJson = (scalaDialect, scalaDsl) match {
      case (Some(dialect), Some(dsl)) =>
        Some(Json.obj("dialect" -> Json.fromString(dialect), "dsl" -> Json.fromString(dsl)))
      case (Some(dialect), None) =>
        Some(Json.obj("dialect" -> Json.fromString(dialect)))
      case _ => None
    }

    val pkTypes = primaryKeyTypes.toFeatureMatcher
    val mocks = mockRepos.toFeatureMatcher
    val tests = testInserts.toFeatureMatcher
    val fvals = fieldValues.toFeatureMatcher
    val ro = readonly.toFeatureMatcher

    val matchers =
      if (pkTypes.isEmpty && mocks.isEmpty && tests.isEmpty && fvals.isEmpty && ro.isEmpty) {
        None
      } else {
        Some(
          Matchers(
            primary_key_types = pkTypes,
            mock_repos = mocks,
            test_inserts = tests,
            field_values = fvals,
            readonly = ro
          )
        )
      }

    val sourcesConfig: Option[StringOrArray] =
      if (allSourcesMode || selectedSources.isEmpty) None
      else if (selectedSources.size == 1) Some(StringOrArrayString(selectedSources.head))
      else Some(StringOrArrayArray(selectedSources.toList.sorted))

    Output(
      bridge = None,
      db_lib = dbLib,
      effect_type = None,
      framework = None,
      json = jsonLib,
      language = language.getOrElse("java"),
      matchers = matchers,
      `package` = pkg,
      path = path,
      scala = scalaJson,
      sources = sourcesConfig
    )
  }
}

object OutputEditorState {
  def fromOutput(output: Output): OutputEditorState = {
    import typr.config.generated.*

    val (selectedSources, allSourcesMode) = output.sources match {
      case Some(StringOrArrayString(s))  => (Set(s), false)
      case Some(StringOrArrayArray(arr)) => (arr.toSet, false)
      case None                          => (Set.empty[String], true)
    }

    val scalaDialect = output.scala.flatMap(_.hcursor.get[String]("dialect").toOption)
    val scalaDsl = output.scala.flatMap(_.hcursor.get[String]("dsl").toOption)

    val primaryKeyTypes = RelationMatcher.fromFeatureMatcher(output.matchers.flatMap(_.primary_key_types))
    val mockRepos = RelationMatcher.fromFeatureMatcher(output.matchers.flatMap(_.mock_repos))
    val testInserts = RelationMatcher.fromFeatureMatcher(output.matchers.flatMap(_.test_inserts))
    val fieldValues = RelationMatcher.fromFeatureMatcher(output.matchers.flatMap(_.field_values))
    val readonly = RelationMatcher.fromFeatureMatcher(output.matchers.flatMap(_.readonly))

    val langIdx = OutputWizardState.languages.indexWhere(_._1 == output.language)
    val dbLibIdx = output.db_lib.flatMap(db => Some(OutputWizardState.dbLibs.indexWhere(_._1 == db))).getOrElse(-1)
    val jsonLibIdx = output.json.flatMap(j => Some(OutputWizardState.jsonLibs.indexWhere(_._1 == j))).getOrElse(-1)
    val dialectIdx = scalaDialect.flatMap(d => Some(OutputWizardState.dialects.indexWhere(_._1 == d))).getOrElse(0)
    val dslIdx = scalaDsl.flatMap(d => Some(OutputWizardState.dsls.indexWhere(_._1 == d))).getOrElse(0)

    OutputEditorState(
      selectedSources = selectedSources,
      allSourcesMode = allSourcesMode,
      sourcesPopupOpen = false,
      sourcesPopupCursorIndex = 0,
      sourcesPopupOriginal = None,
      path = output.path,
      pkg = output.`package`,
      language = Some(output.language),
      dbLib = output.db_lib,
      jsonLib = output.json,
      scalaDialect = scalaDialect,
      scalaDsl = scalaDsl,
      primaryKeyTypes = primaryKeyTypes,
      mockRepos = mockRepos,
      testInserts = testInserts,
      fieldValues = fieldValues,
      readonly = readonly,
      selectedField = OutputEditorField.Sources,
      languageSelect = SelectInput("Language:", OutputWizardState.languages, if (langIdx >= 0) langIdx else 0),
      dbLibSelect = SelectInput("DB Library:", OutputWizardState.dbLibs, if (dbLibIdx >= 0) dbLibIdx else 0),
      jsonLibSelect = SelectInput("JSON Library:", OutputWizardState.jsonLibs, if (jsonLibIdx >= 0) jsonLibIdx else 0),
      dialectSelect = SelectInput("Scala Dialect:", OutputWizardState.dialects, if (dialectIdx >= 0) dialectIdx else 0),
      dslSelect = SelectInput("Scala DSL:", OutputWizardState.dsls, if (dslIdx >= 0) dslIdx else 0),
      modified = false,
      metaDbStatus = Map.empty,
      matcherPreviewField = None,
      matcherPopupField = None,
      matcherPopupOriginal = None
    )
  }

  def matchPattern(matcher: RelationMatcher, relations: List[RelationInfo]): List[RelationInfo] = {
    import typr.cli.util.PatternMatcher
    val selector = PatternMatcher.fromFeatureMatcher(matcher.toFeatureMatcher)
    relations.filter { rel =>
      val relName = typr.db.RelationName(rel.schema, rel.name)
      selector.include(relName)
    }
  }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Type (Bridge) State
// ═══════════════════════════════════════════════════════════════════════════════

/** Status of type preview across sources */
sealed trait TypePreviewStatus
object TypePreviewStatus {
  case object Pending extends TypePreviewStatus
  case object Fetching extends TypePreviewStatus
  case class Loaded(matchCount: Int, sampleMatches: List[String]) extends TypePreviewStatus
  case class Failed(error: String) extends TypePreviewStatus
}

/** Wizard steps for creating a new Bridge type */
sealed trait TypeWizardStep
object TypeWizardStep {
  case object SelectFromSuggestions extends TypeWizardStep
  case object EnterName extends TypeWizardStep
  case object ConfigureDbMatch extends TypeWizardStep
  case object ConfigureModelMatch extends TypeWizardStep
  case object Preview extends TypeWizardStep
  case object Review extends TypeWizardStep
}

/** Form fields in the type wizard for focus management */
sealed trait TypeWizardField
object TypeWizardField {
  case object Name extends TypeWizardField
  case object DbColumns extends TypeWizardField
  case object PrimaryKey extends TypeWizardField
  case object ModelNames extends TypeWizardField
  case object CreateButton extends TypeWizardField
  case object CancelButton extends TypeWizardField

  val formOrder: List[TypeWizardField] = List(
    Name,
    DbColumns,
    PrimaryKey,
    ModelNames,
    CreateButton,
    CancelButton
  )

  def next(current: TypeWizardField): TypeWizardField = {
    val idx = formOrder.indexOf(current)
    if (idx < 0) formOrder.head else formOrder((idx + 1) % formOrder.length)
  }

  def prev(current: TypeWizardField): TypeWizardField = {
    val idx = formOrder.indexOf(current)
    if (idx < 0) formOrder.last else formOrder(if (idx == 0) formOrder.length - 1 else idx - 1)
  }
}

/** State for type wizard */
case class TypeWizardState(
    step: TypeWizardStep,
    focusedField: TypeWizardField,
    name: String,
    dbColumns: String,
    dbSchemas: String,
    dbTables: String,
    dbTypes: String,
    primaryKey: Option[Boolean],
    modelNames: String,
    modelSchemas: String,
    modelFormats: String,
    error: Option[String],
    previewStatus: Map[String, TypePreviewStatus],
    suggestions: List[typr.bridge.TypeSuggestion],
    selectedSuggestionIndex: Int,
    suggestionScrollOffset: Int
) {
  def nextStep: TypeWizardStep = step match {
    case TypeWizardStep.SelectFromSuggestions => TypeWizardStep.EnterName
    case TypeWizardStep.EnterName             => TypeWizardStep.ConfigureDbMatch
    case TypeWizardStep.ConfigureDbMatch      => TypeWizardStep.ConfigureModelMatch
    case TypeWizardStep.ConfigureModelMatch   => TypeWizardStep.Preview
    case TypeWizardStep.Preview               => TypeWizardStep.Review
    case TypeWizardStep.Review                => TypeWizardStep.Review
  }

  def previousStep: TypeWizardStep = step match {
    case TypeWizardStep.SelectFromSuggestions => TypeWizardStep.SelectFromSuggestions
    case TypeWizardStep.EnterName             => if (suggestions.nonEmpty) TypeWizardStep.SelectFromSuggestions else TypeWizardStep.EnterName
    case TypeWizardStep.ConfigureDbMatch      => TypeWizardStep.EnterName
    case TypeWizardStep.ConfigureModelMatch   => TypeWizardStep.ConfigureDbMatch
    case TypeWizardStep.Preview               => TypeWizardStep.ConfigureModelMatch
    case TypeWizardStep.Review                => TypeWizardStep.Preview
  }

  def toFieldType: typr.config.generated.FieldType = {
    import typr.config.generated.*

    val dbMatch = if (dbColumns.isEmpty && dbSchemas.isEmpty && dbTables.isEmpty && dbTypes.isEmpty && primaryKey.isEmpty) {
      None
    } else {
      Some(
        DbMatch(
          annotation = None,
          column = parsePatternList(dbColumns),
          comment = None,
          db_type = parsePatternList(dbTypes),
          domain = None,
          nullable = None,
          primary_key = primaryKey,
          references = None,
          schema = parsePatternList(dbSchemas),
          source = None,
          table = parsePatternList(dbTables)
        )
      )
    }

    val modelMatch = if (modelNames.isEmpty && modelSchemas.isEmpty && modelFormats.isEmpty) {
      None
    } else {
      Some(
        ModelMatch(
          extension = None,
          format = parsePatternList(modelFormats),
          json_path = None,
          name = parsePatternList(modelNames),
          required = None,
          schema = parsePatternList(modelSchemas),
          schema_type = None,
          source = None
        )
      )
    }

    FieldType(
      api = None,
      db = dbMatch,
      model = modelMatch,
      underlying = None,
      validation = None
    )
  }

  private def parsePatternList(s: String): Option[typr.config.generated.StringOrArray] = {
    if (s.isEmpty) None
    else {
      val patterns = s.split(",").map(_.trim).filter(_.nonEmpty).toList
      if (patterns.isEmpty) None
      else if (patterns.size == 1) Some(typr.config.generated.StringOrArrayString(patterns.head))
      else Some(typr.config.generated.StringOrArrayArray(patterns))
    }
  }
}

object TypeWizardState {
  def initial(suggestions: List[typr.bridge.TypeSuggestion]): TypeWizardState = TypeWizardState(
    step = if (suggestions.nonEmpty) TypeWizardStep.SelectFromSuggestions else TypeWizardStep.EnterName,
    focusedField = TypeWizardField.Name,
    name = "",
    dbColumns = "",
    dbSchemas = "",
    dbTables = "",
    dbTypes = "",
    primaryKey = None,
    modelNames = "",
    modelSchemas = "",
    modelFormats = "",
    error = None,
    previewStatus = Map.empty,
    suggestions = suggestions,
    selectedSuggestionIndex = 0,
    suggestionScrollOffset = 0
  )

  def fromSuggestion(suggestion: typr.bridge.TypeSuggestion, allSuggestions: List[typr.bridge.TypeSuggestion]): TypeWizardState = TypeWizardState(
    step = TypeWizardStep.EnterName,
    focusedField = TypeWizardField.Name,
    name = suggestion.name,
    dbColumns = suggestion.dbColumnPatterns.mkString(", "),
    dbSchemas = "",
    dbTables = "",
    dbTypes = suggestion.dbTypePatterns.mkString(", "),
    primaryKey = None,
    modelNames = suggestion.modelNamePatterns.mkString(", "),
    modelSchemas = "",
    modelFormats = "",
    error = None,
    previewStatus = Map.empty,
    suggestions = allSuggestions,
    selectedSuggestionIndex = 0,
    suggestionScrollOffset = 0
  )
}

/** Fields in the type editor */
sealed trait TypeEditorField
object TypeEditorField {
  case object Name extends TypeEditorField
  case object DbColumns extends TypeEditorField
  case object DbSchemas extends TypeEditorField
  case object DbTables extends TypeEditorField
  case object DbTypes extends TypeEditorField
  case object PrimaryKey extends TypeEditorField
  case object ModelNames extends TypeEditorField
  case object ModelSchemas extends TypeEditorField
  case object ModelFormats extends TypeEditorField
}

/** State for type editor */
case class TypeEditorState(
    name: String,
    dbColumns: String,
    dbSchemas: String,
    dbTables: String,
    dbTypes: String,
    primaryKey: Option[Boolean],
    modelNames: String,
    modelSchemas: String,
    modelFormats: String,
    selectedField: TypeEditorField,
    modified: Boolean,
    previewStatus: Map[String, TypePreviewStatus]
) {
  def fields: List[TypeEditorField] = List(
    TypeEditorField.Name,
    TypeEditorField.DbColumns,
    TypeEditorField.DbSchemas,
    TypeEditorField.DbTables,
    TypeEditorField.DbTypes,
    TypeEditorField.PrimaryKey,
    TypeEditorField.ModelNames,
    TypeEditorField.ModelSchemas,
    TypeEditorField.ModelFormats
  )

  def nextField: TypeEditorField = {
    val fs = fields
    val idx = fs.indexOf(selectedField)
    if (idx >= 0 && idx < fs.length - 1) fs(idx + 1) else fs.head
  }

  def prevField: TypeEditorField = {
    val fs = fields
    val idx = fs.indexOf(selectedField)
    if (idx > 0) fs(idx - 1) else fs.last
  }

  def toFieldType: typr.config.generated.FieldType = {
    import typr.config.generated.*

    val dbMatch = if (dbColumns.isEmpty && dbSchemas.isEmpty && dbTables.isEmpty && dbTypes.isEmpty && primaryKey.isEmpty) {
      None
    } else {
      Some(
        DbMatch(
          annotation = None,
          column = parsePatternList(dbColumns),
          comment = None,
          db_type = parsePatternList(dbTypes),
          domain = None,
          nullable = None,
          primary_key = primaryKey,
          references = None,
          schema = parsePatternList(dbSchemas),
          source = None,
          table = parsePatternList(dbTables)
        )
      )
    }

    val modelMatch = if (modelNames.isEmpty && modelSchemas.isEmpty && modelFormats.isEmpty) {
      None
    } else {
      Some(
        ModelMatch(
          extension = None,
          format = parsePatternList(modelFormats),
          json_path = None,
          name = parsePatternList(modelNames),
          required = None,
          schema = parsePatternList(modelSchemas),
          schema_type = None,
          source = None
        )
      )
    }

    FieldType(
      api = None,
      db = dbMatch,
      model = modelMatch,
      underlying = None,
      validation = None
    )
  }

  private def parsePatternList(s: String): Option[typr.config.generated.StringOrArray] = {
    if (s.isEmpty) None
    else {
      val patterns = s.split(",").map(_.trim).filter(_.nonEmpty).toList
      if (patterns.isEmpty) None
      else if (patterns.size == 1) Some(typr.config.generated.StringOrArrayString(patterns.head))
      else Some(typr.config.generated.StringOrArrayArray(patterns))
    }
  }
}

object TypeEditorState {
  def fromFieldType(name: String, fieldType: typr.config.generated.FieldType): TypeEditorState = {
    import typr.config.generated.*

    def patternsToString(opt: Option[StringOrArray]): String = opt match {
      case None                          => ""
      case Some(StringOrArrayString(s))  => s
      case Some(StringOrArrayArray(arr)) => arr.mkString(", ")
    }

    val db = fieldType.db.getOrElse(
      DbMatch(
        annotation = None,
        column = None,
        comment = None,
        db_type = None,
        domain = None,
        nullable = None,
        primary_key = None,
        references = None,
        schema = None,
        source = None,
        table = None
      )
    )
    val model = fieldType.model.getOrElse(
      ModelMatch(
        extension = None,
        format = None,
        json_path = None,
        name = None,
        required = None,
        schema = None,
        schema_type = None,
        source = None
      )
    )

    TypeEditorState(
      name = name,
      dbColumns = patternsToString(db.column),
      dbSchemas = patternsToString(db.schema),
      dbTables = patternsToString(db.table),
      dbTypes = patternsToString(db.db_type),
      primaryKey = db.primary_key,
      modelNames = patternsToString(model.name),
      modelSchemas = patternsToString(model.schema),
      modelFormats = patternsToString(model.format),
      selectedField = TypeEditorField.DbColumns,
      modified = false,
      previewStatus = Map.empty
    )
  }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Source Loading State (Boot-time MetaDb resolution)
// ═══════════════════════════════════════════════════════════════════════════════

/** Type-safe wrapper for source names */
case class SourceName(value: String) extends AnyVal

/** Simple representation of a loaded SQL script for TUI purposes */
case class LoadedSqlScript(path: java.nio.file.Path, content: String)

/** Each source progresses through these states independently */
sealed trait SourceStatus
object SourceStatus {
  case object Pending extends SourceStatus

  case class LoadingMetaDb(startedAt: Long) extends SourceStatus

  case class LoadingSpec(startedAt: Long) extends SourceStatus

  case class RunningSqlGlot(
      metaDb: typr.MetaDb,
      dataSource: typr.TypoDataSource,
      sourceConfig: typr.cli.config.ConfigToOptions.SourceConfig,
      metaDbLoadedAt: Long,
      sqlGlotStartedAt: Long
  ) extends SourceStatus

  case class Ready(
      metaDb: typr.MetaDb,
      dataSource: typr.TypoDataSource,
      sourceConfig: typr.cli.config.ConfigToOptions.SourceConfig,
      sqlScripts: List[LoadedSqlScript],
      metaDbLoadedAt: Long,
      sqlGlotFinishedAt: Long
  ) extends SourceStatus

  /** Ready state for OpenAPI spec sources */
  case class ReadySpec(
      spec: typr.openapi.ParsedSpec,
      specPath: String,
      sourceType: SpecSourceType,
      loadedAt: Long
  ) extends SourceStatus

  case class Failed(
      error: SourceError,
      failedAt: Long,
      retryCount: Int
  ) extends SourceStatus
}

/** Type of spec source */
sealed trait SpecSourceType
object SpecSourceType {
  case object OpenApi extends SpecSourceType
  case object JsonSchema extends SpecSourceType
}

/** Typed error representation for source loading */
sealed trait SourceError {
  def message: String
}
object SourceError {
  case class ConfigParseFailed(details: String) extends SourceError {
    def message: String = s"Config parse failed: $details"
  }
  case class ConnectionFailed(host: String, port: Int, cause: String) extends SourceError {
    def message: String = s"Connection to $host:$port failed: $cause"
  }
  case class MetaDbFetchFailed(cause: String) extends SourceError {
    def message: String = s"MetaDb fetch failed: $cause"
  }
  case class SqlGlotFailed(file: String, cause: String) extends SourceError {
    def message: String = s"SQLGlot failed on $file: $cause"
  }
  case class SpecParseFailed(specPath: String, errors: List[String]) extends SourceError {
    def message: String = s"Failed to parse spec $specPath: ${errors.mkString(", ")}"
  }
  case class SpecNotFound(specPath: String) extends SourceError {
    def message: String = s"Spec file not found: $specPath"
  }
  case class UnsupportedSourceType(sourceType: String) extends SourceError {
    def message: String = s"Unsupported source type: $sourceType"
  }
}

/** Statistics derived from MetaDb */
case class SourceStats(
    tableCount: Int,
    viewCount: Int,
    enumCount: Int,
    columnCount: Int
)

/** Statistics derived from ParsedSpec */
case class SpecStats(
    modelCount: Int,
    enumCount: Int,
    sumTypeCount: Int,
    apiCount: Int,
    propertyCount: Int
)

/** Extension methods for source status map */
extension(statuses: Map[SourceName, SourceStatus]) {
  def readySources: Map[SourceName, SourceStatus.Ready] =
    statuses.collect { case (n, s: SourceStatus.Ready) => n -> s }

  def readySpecSources: Map[SourceName, SourceStatus.ReadySpec] =
    statuses.collect { case (n, s: SourceStatus.ReadySpec) => n -> s }

  def allReadySources: Map[SourceName, SourceStatus] =
    statuses.collect {
      case (n, s: SourceStatus.Ready)     => n -> s
      case (n, s: SourceStatus.ReadySpec) => n -> s
    }

  def allReady: Boolean =
    statuses.nonEmpty && statuses.values.forall {
      case _: SourceStatus.Ready     => true
      case _: SourceStatus.ReadySpec => true
      case _                         => false
    }

  def anyFailed: Boolean =
    statuses.values.exists(_.isInstanceOf[SourceStatus.Failed])

  def failedSources: Map[SourceName, SourceStatus.Failed] =
    statuses.collect { case (n, s: SourceStatus.Failed) => n -> s }

  def metaDbs: Map[SourceName, typr.MetaDb] =
    statuses.collect {
      case (n, s: SourceStatus.RunningSqlGlot) => n -> s.metaDb
      case (n, s: SourceStatus.Ready)          => n -> s.metaDb
    }

  def parsedSpecs: Map[SourceName, typr.openapi.ParsedSpec] =
    statuses.collect { case (n, s: SourceStatus.ReadySpec) => n -> s.spec }

  def anyLoading: Boolean =
    statuses.values.exists {
      case _: SourceStatus.LoadingMetaDb  => true
      case _: SourceStatus.LoadingSpec    => true
      case _: SourceStatus.RunningSqlGlot => true
      case _                              => false
    }
}

/** Extension for MetaDb to derive stats */
extension(metaDb: typr.MetaDb) {
  def stats: SourceStats = SourceStats(
    tableCount = metaDb.relations.count(_._2.forceGet.isInstanceOf[typr.db.Table]),
    viewCount = metaDb.relations.count(_._2.forceGet.isInstanceOf[typr.db.View]),
    enumCount = metaDb.enums.size,
    columnCount = metaDb.relations.values.map { lazyRel =>
      lazyRel.forceGet match {
        case t: typr.db.Table => t.cols.length
        case v: typr.db.View  => v.cols.length
      }
    }.sum
  )
}

/** Extension for ParsedSpec to derive stats */
extension(spec: typr.openapi.ParsedSpec) {
  def stats: SpecStats = SpecStats(
    modelCount = spec.models.count(_.isInstanceOf[typr.openapi.ModelClass.ObjectType]),
    enumCount = spec.models.count(_.isInstanceOf[typr.openapi.ModelClass.EnumType]),
    sumTypeCount = spec.sumTypes.size,
    apiCount = spec.apis.flatMap(_.methods).size,
    propertyCount = spec.models.collect { case o: typr.openapi.ModelClass.ObjectType => o.properties.size }.sum
  )
}

// ═══════════════════════════════════════════════════════════════════════════════
// Schema Browser State
// ═══════════════════════════════════════════════════════════════════════════════

/** Navigation level in the schema browser */
sealed trait SchemaBrowserLevel
object SchemaBrowserLevel {
  case object Schemas extends SchemaBrowserLevel
  case object Tables extends SchemaBrowserLevel
  case object Columns extends SchemaBrowserLevel
}

/** Which pane has focus in two-pane layout */
sealed trait BrowserPane
object BrowserPane {
  case object Left extends BrowserPane
  case object Right extends BrowserPane
}

/** Search state for the browser */
case class BrowserSearchState(
    active: Boolean,
    query: String,
    results: List[SearchResult],
    selectedIndex: Int
)

object BrowserSearchState {
  def initial: BrowserSearchState = BrowserSearchState(
    active = false,
    query = "",
    results = Nil,
    selectedIndex = 0
  )
}

/** A search result in the browser */
sealed trait SearchResult
object SearchResult {
  case class SchemaResult(schema: String) extends SearchResult
  case class TableResult(schema: Option[String], table: String) extends SearchResult
  case class ColumnResult(schema: Option[String], table: String, column: String, dbType: String) extends SearchResult
}

/** Information about a column for display */
case class ColumnInfo(
    name: String,
    dbType: typr.db.Type,
    nullable: Boolean,
    isPrimaryKey: Boolean,
    foreignKey: Option[ForeignKeyRef],
    comment: Option[String],
    defaultValue: Option[String],
    matchingType: Option[String]
)

/** Reference to a foreign key target */
case class ForeignKeyRef(
    targetSchema: Option[String],
    targetTable: String,
    targetColumn: String
)

/** Animation state for browser pane sliding */
case class BrowserSlideAnimation(
    fromLevel: SchemaBrowserLevel,
    toLevel: SchemaBrowserLevel,
    progress: Double,
    animating: Boolean
) {
  def tick: BrowserSlideAnimation = {
    if (!animating) this
    else {
      val speed = 0.25
      val newProgress = math.min(1.0, progress + speed)
      if (newProgress >= 1.0) {
        copy(progress = 1.0, animating = false)
      } else {
        copy(progress = newProgress)
      }
    }
  }

  def isForward: Boolean = levelToInt(toLevel) > levelToInt(fromLevel)

  private def levelToInt(level: SchemaBrowserLevel): Int = level match {
    case SchemaBrowserLevel.Schemas => 0
    case SchemaBrowserLevel.Tables  => 1
    case SchemaBrowserLevel.Columns => 2
  }
}

object BrowserSlideAnimation {
  def none(level: SchemaBrowserLevel): BrowserSlideAnimation = BrowserSlideAnimation(
    fromLevel = level,
    toLevel = level,
    progress = 1.0,
    animating = false
  )

  def forward(from: SchemaBrowserLevel, to: SchemaBrowserLevel): BrowserSlideAnimation = BrowserSlideAnimation(
    fromLevel = from,
    toLevel = to,
    progress = 0.0,
    animating = true
  )

  def backward(from: SchemaBrowserLevel, to: SchemaBrowserLevel): BrowserSlideAnimation = BrowserSlideAnimation(
    fromLevel = from,
    toLevel = to,
    progress = 0.0,
    animating = true
  )
}

/** State for the schema browser screen */
case class SchemaBrowserState(
    sourceName: SourceName,
    level: SchemaBrowserLevel,
    focusedPane: BrowserPane,
    selectedSchemaIndex: Int,
    selectedTableIndex: Int,
    selectedColumnIndex: Int,
    schemas: List[String],
    tables: List[(Option[String], String)],
    columns: List[ColumnInfo],
    searchState: BrowserSearchState,
    scrollOffset: Int,
    detailsScrollOffset: Int,
    slideAnimation: BrowserSlideAnimation
) {
  def currentSchema: Option[String] = {
    if (schemas.isEmpty || selectedSchemaIndex >= schemas.length) None
    else Some(schemas(selectedSchemaIndex))
  }

  def currentTable: Option[(Option[String], String)] = {
    if (tables.isEmpty || selectedTableIndex >= tables.length) None
    else Some(tables(selectedTableIndex))
  }

  def currentColumn: Option[ColumnInfo] = {
    if (columns.isEmpty || selectedColumnIndex >= columns.length) None
    else Some(columns(selectedColumnIndex))
  }
}

object SchemaBrowserState {
  def initial(sourceName: SourceName, metaDb: typr.MetaDb): SchemaBrowserState = {
    val schemas = metaDb.relations.keys.flatMap(_.schema).toList.distinct.sorted
    val initialLevel = SchemaBrowserLevel.Schemas
    SchemaBrowserState(
      sourceName = sourceName,
      level = initialLevel,
      focusedPane = BrowserPane.Left,
      selectedSchemaIndex = 0,
      selectedTableIndex = 0,
      selectedColumnIndex = 0,
      schemas = schemas,
      tables = Nil,
      columns = Nil,
      searchState = BrowserSearchState.initial,
      scrollOffset = 0,
      detailsScrollOffset = 0,
      slideAnimation = BrowserSlideAnimation.none(initialLevel)
    )
  }

  def fromMetaDb(sourceName: SourceName, metaDb: typr.MetaDb): SchemaBrowserState = {
    val schemas = metaDb.relations.keys.flatMap(_.schema).toList.distinct.sorted
    val allTables = metaDb.relations.keys.toList.sortBy(r => (r.schema.getOrElse(""), r.name))

    // Skip schema selection if there's 0 or 1 schemas
    val skipSchemas = schemas.length <= 1
    val initialLevel = if (skipSchemas) SchemaBrowserLevel.Tables else SchemaBrowserLevel.Schemas

    // If skipping schemas, filter tables to the single schema (or no schema)
    val tables = if (skipSchemas) {
      val singleSchema = schemas.headOption
      allTables.filter(_.schema == singleSchema).map(r => (r.schema, r.name))
    } else {
      Nil
    }

    SchemaBrowserState(
      sourceName = sourceName,
      level = initialLevel,
      focusedPane = BrowserPane.Left,
      selectedSchemaIndex = 0,
      selectedTableIndex = 0,
      selectedColumnIndex = 0,
      schemas = schemas,
      tables = tables,
      columns = Nil,
      searchState = BrowserSearchState.initial,
      scrollOffset = 0,
      detailsScrollOffset = 0,
      slideAnimation = BrowserSlideAnimation.none(initialLevel)
    )
  }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Spec Browser State (for OpenAPI / JSON Schema sources)
// ═══════════════════════════════════════════════════════════════════════════════

/** Navigation level in the spec browser */
sealed trait SpecBrowserLevel
object SpecBrowserLevel {
  case object Models extends SpecBrowserLevel
  case object Properties extends SpecBrowserLevel
  case object Apis extends SpecBrowserLevel
  case object ApiDetails extends SpecBrowserLevel
}

/** Information about a model for display */
case class ModelInfo(
    name: String,
    modelType: ModelType,
    propertyCount: Int,
    description: Option[String]
)

/** Type of model */
sealed trait ModelType
object ModelType {
  case object Object extends ModelType
  case object Enum extends ModelType
  case object Wrapper extends ModelType
  case object Alias extends ModelType
  case object SumType extends ModelType
}

/** Information about a property for display */
case class PropertyInfo(
    name: String,
    typeName: String,
    required: Boolean,
    description: Option[String],
    format: Option[String],
    matchingType: Option[String]
)

/** Information about an API endpoint for display */
case class ApiInfo(
    path: String,
    method: String,
    operationId: Option[String],
    summary: Option[String],
    paramCount: Int,
    responseTypes: List[String]
)

/** State for the spec browser screen */
case class SpecBrowserState(
    sourceName: SourceName,
    sourceType: SpecSourceType,
    level: SpecBrowserLevel,
    focusedPane: BrowserPane,
    selectedModelIndex: Int,
    selectedPropertyIndex: Int,
    selectedApiIndex: Int,
    models: List[ModelInfo],
    properties: List[PropertyInfo],
    apis: List[ApiInfo],
    searchState: BrowserSearchState,
    scrollOffset: Int,
    detailsScrollOffset: Int,
    spec: typr.openapi.ParsedSpec
) {
  def currentModel: Option[ModelInfo] = {
    if (models.isEmpty || selectedModelIndex >= models.length) None
    else Some(models(selectedModelIndex))
  }

  def currentProperty: Option[PropertyInfo] = {
    if (properties.isEmpty || selectedPropertyIndex >= properties.length) None
    else Some(properties(selectedPropertyIndex))
  }

  def currentApi: Option[ApiInfo] = {
    if (apis.isEmpty || selectedApiIndex >= apis.length) None
    else Some(apis(selectedApiIndex))
  }
}

object SpecBrowserState {
  def fromSpec(sourceName: SourceName, spec: typr.openapi.ParsedSpec, sourceType: SpecSourceType): SpecBrowserState = {
    val models = extractModels(spec)
    val apis = extractApis(spec)
    val initialLevel = SpecBrowserLevel.Models

    SpecBrowserState(
      sourceName = sourceName,
      sourceType = sourceType,
      level = initialLevel,
      focusedPane = BrowserPane.Left,
      selectedModelIndex = 0,
      selectedPropertyIndex = 0,
      selectedApiIndex = 0,
      models = models,
      properties = Nil,
      apis = apis,
      searchState = BrowserSearchState.initial,
      scrollOffset = 0,
      detailsScrollOffset = 0,
      spec = spec
    )
  }

  private def extractModels(spec: typr.openapi.ParsedSpec): List[ModelInfo] = {
    val objectModels = spec.models.collect { case o: typr.openapi.ModelClass.ObjectType =>
      ModelInfo(
        name = o.name,
        modelType = ModelType.Object,
        propertyCount = o.properties.size,
        description = o.description
      )
    }

    val enumModels = spec.models.collect { case e: typr.openapi.ModelClass.EnumType =>
      ModelInfo(
        name = e.name,
        modelType = ModelType.Enum,
        propertyCount = e.values.size,
        description = None
      )
    }

    val wrapperModels = spec.models.collect { case w: typr.openapi.ModelClass.WrapperType =>
      ModelInfo(
        name = w.name,
        modelType = ModelType.Wrapper,
        propertyCount = 1,
        description = None
      )
    }

    val aliasModels = spec.models.collect { case a: typr.openapi.ModelClass.AliasType =>
      ModelInfo(
        name = a.name,
        modelType = ModelType.Alias,
        propertyCount = 0,
        description = None
      )
    }

    val sumTypes = spec.sumTypes.map { st =>
      ModelInfo(
        name = st.name,
        modelType = ModelType.SumType,
        propertyCount = st.subtypeNames.size,
        description = st.description
      )
    }

    (objectModels ++ enumModels ++ wrapperModels ++ aliasModels ++ sumTypes).sortBy(_.name)
  }

  private def extractApis(spec: typr.openapi.ParsedSpec): List[ApiInfo] = {
    spec.apis
      .flatMap { api =>
        api.methods.map { method =>
          ApiInfo(
            path = method.path,
            method = method.httpMethod.toString,
            operationId = Some(method.name),
            summary = method.description,
            paramCount = method.parameters.size,
            responseTypes = method.responses.flatMap(_.typeInfo.map(_.toString)).distinct
          )
        }
      }
      .sortBy(a => (a.path, a.method))
  }

  def extractPropertiesForModel(spec: typr.openapi.ParsedSpec, modelName: String): List[PropertyInfo] = {
    spec.models
      .collectFirst {
        case o: typr.openapi.ModelClass.ObjectType if o.name == modelName =>
          o.properties
            .map { prop =>
              PropertyInfo(
                name = prop.name,
                typeName = prop.typeInfo.toString,
                required = prop.required,
                description = prop.description,
                format = None,
                matchingType = None
              )
            }
            .sortBy(_.name)
        case e: typr.openapi.ModelClass.EnumType if e.name == modelName =>
          e.values.zipWithIndex.map { case (v, _) =>
            PropertyInfo(
              name = v,
              typeName = "enum value",
              required = true,
              description = None,
              format = None,
              matchingType = None
            )
          }
      }
      .getOrElse(Nil)
  }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Composite Type Wizard State
// ═══════════════════════════════════════════════════════════════════════════════

/** Wizard steps for creating a new composite Bridge type */
sealed trait CompositeWizardStep
object CompositeWizardStep {
  case object SelectSuggestion extends CompositeWizardStep
  case object EnterName extends CompositeWizardStep
  case object DefineFields extends CompositeWizardStep
  case object AddProjections extends CompositeWizardStep
  case object AlignFields extends CompositeWizardStep
  case object Review extends CompositeWizardStep
}

/** State for editing a single field in the composite type */
case class FieldEditState(
    name: String,
    typeName: String,
    nullable: Boolean,
    array: Boolean,
    description: String,
    editing: Boolean
) {
  def toCompositeField: typr.bridge.CompositeField = typr.bridge.CompositeField(
    name = name,
    typeName = typeName,
    nullable = nullable,
    array = array,
    description = if (description.isEmpty) None else Some(description)
  )

  def compactType: String = {
    val nullSuffix = if (nullable) "?" else ""
    val arraySuffix = if (array) "[]" else ""
    s"$typeName$nullSuffix$arraySuffix"
  }
}

object FieldEditState {
  def empty: FieldEditState = FieldEditState(
    name = "",
    typeName = "String",
    nullable = false,
    array = false,
    description = "",
    editing = true
  )

  def fromCompositeField(f: typr.bridge.CompositeField): FieldEditState = FieldEditState(
    name = f.name,
    typeName = f.typeName,
    nullable = f.nullable,
    array = f.array,
    description = f.description.getOrElse(""),
    editing = false
  )
}

/** State for editing an aligned source in the domain type wizard */
case class AlignedSourceEditState(
    sourceName: String,
    entityPath: String,
    mode: typr.bridge.CompatibilityMode,
    mappings: Map[String, String],
    exclude: Set[String],
    includeExtra: List[String],
    readonly: Boolean,
    editing: Boolean,
    entityPickerOpen: Boolean,
    entityPickerIndex: Int
) {
  def toAlignedSource: typr.bridge.AlignedSource = typr.bridge.AlignedSource(
    sourceName = sourceName,
    entityPath = entityPath,
    mode = mode,
    mappings = mappings,
    exclude = exclude,
    includeExtra = includeExtra,
    readonly = readonly
  )

  // Backwards compatibility
  def toProjection: typr.bridge.AlignedSource = toAlignedSource

  def key: String = s"$sourceName:$entityPath"
}

object AlignedSourceEditState {
  def empty(sourceName: String): AlignedSourceEditState = AlignedSourceEditState(
    sourceName = sourceName,
    entityPath = "",
    mode = typr.bridge.CompatibilityMode.Superset,
    mappings = Map.empty,
    exclude = Set.empty,
    includeExtra = Nil,
    readonly = false,
    editing = true,
    entityPickerOpen = false,
    entityPickerIndex = 0
  )

  def fromAlignedSource(a: typr.bridge.AlignedSource): AlignedSourceEditState = AlignedSourceEditState(
    sourceName = a.sourceName,
    entityPath = a.entityPath,
    mode = a.mode,
    mappings = a.mappings,
    exclude = a.exclude,
    includeExtra = a.includeExtra,
    readonly = a.readonly,
    editing = false,
    entityPickerOpen = false,
    entityPickerIndex = 0
  )

  // Backwards compatibility
  def fromProjection(p: typr.bridge.AlignedSource): AlignedSourceEditState = fromAlignedSource(p)
}

// Backwards compatibility alias
type ProjectionEditState = AlignedSourceEditState
val ProjectionEditState: AlignedSourceEditState.type = AlignedSourceEditState

/** Alignment result for display in TUI */
case class AlignmentDisplayRow(
    canonicalField: String,
    canonicalType: String,
    sourceField: Option[String],
    sourceType: Option[String],
    status: AlignmentCellStatus,
    autoMapped: Boolean
)

sealed trait AlignmentCellStatus
object AlignmentCellStatus {
  case object Aligned extends AlignmentCellStatus
  case object Missing extends AlignmentCellStatus
  case object Extra extends AlignmentCellStatus
  case object TypeMismatch extends AlignmentCellStatus
  case object NullabilityMismatch extends AlignmentCellStatus
}

/** State for the field alignment step */
case class FieldAlignmentState(
    projectionKey: String,
    rows: List[AlignmentDisplayRow],
    selectedRowIndex: Int,
    editingMapping: Option[(String, String)],
    validationResult: typr.bridge.AlignmentStatus
) {
  def selectedRow: Option[AlignmentDisplayRow] =
    if (rows.isEmpty || selectedRowIndex >= rows.size) None
    else Some(rows(selectedRowIndex))
}

object FieldAlignmentState {
  def initial(projectionKey: String): FieldAlignmentState = FieldAlignmentState(
    projectionKey = projectionKey,
    rows = Nil,
    selectedRowIndex = 0,
    editingMapping = None,
    validationResult = typr.bridge.AlignmentStatus.NotValidated
  )
}

/** State for the domain type wizard */
case class CompositeWizardState(
    step: CompositeWizardStep,
    name: String,
    description: String,
    primarySource: Option[typr.bridge.PrimarySource],
    fields: List[FieldEditState],
    alignedSources: List[AlignedSourceEditState],
    alignments: Map[String, FieldAlignmentState],
    selectedFieldIndex: Int,
    selectedAlignedSourceIndex: Int,
    selectedAlignmentSourceIndex: Int,
    addingField: Boolean,
    addingAlignedSource: Boolean,
    sourcePickerOpen: Boolean,
    sourcePickerIndex: Int,
    error: Option[String],
    generateDomainType: Boolean,
    generateMappers: Boolean,
    generateInterface: Boolean,
    suggestions: List[typr.bridge.CompositeTypeSuggester.CompositeSuggestion],
    selectedSuggestionIndex: Int,
    suggestionScrollOffset: Int
) {
  def currentField: Option[FieldEditState] =
    if (fields.isEmpty || selectedFieldIndex >= fields.size) None
    else Some(fields(selectedFieldIndex))

  def currentAlignedSource: Option[AlignedSourceEditState] =
    if (alignedSources.isEmpty || selectedAlignedSourceIndex >= alignedSources.size) None
    else Some(alignedSources(selectedAlignedSourceIndex))

  def currentAlignmentSource: Option[AlignedSourceEditState] =
    if (alignedSources.isEmpty || selectedAlignmentSourceIndex >= alignedSources.size) None
    else Some(alignedSources(selectedAlignmentSourceIndex))

  def toDomainTypeDefinition: typr.bridge.DomainTypeDefinition = typr.bridge.DomainTypeDefinition(
    name = name,
    primary = primarySource,
    fields = fields.map(_.toCompositeField),
    alignedSources = alignedSources.map(a => a.key -> a.toAlignedSource).toMap,
    description = if (description.isEmpty) None else Some(description),
    generateDomainType = generateDomainType,
    generateMappers = generateMappers,
    generateInterface = generateInterface,
    generateBuilder = false,
    generateCopy = true
  )

  // Backwards compatibility
  def projections: List[AlignedSourceEditState] = alignedSources
  def selectedProjectionIndex: Int = selectedAlignedSourceIndex
  def selectedAlignmentProjectionIndex: Int = selectedAlignmentSourceIndex
  def addingProjection: Boolean = addingAlignedSource
  def generateCanonical: Boolean = generateDomainType
  def currentProjection: Option[AlignedSourceEditState] = currentAlignedSource
  def currentAlignmentProjection: Option[AlignedSourceEditState] = currentAlignmentSource
  def toCompositeTypeDefinition: typr.bridge.DomainTypeDefinition = toDomainTypeDefinition

  def nextStep: CompositeWizardStep = step match {
    case CompositeWizardStep.SelectSuggestion => CompositeWizardStep.EnterName
    case CompositeWizardStep.EnterName        => CompositeWizardStep.DefineFields
    case CompositeWizardStep.DefineFields     => CompositeWizardStep.AddProjections
    case CompositeWizardStep.AddProjections   => CompositeWizardStep.AlignFields
    case CompositeWizardStep.AlignFields      => CompositeWizardStep.Review
    case CompositeWizardStep.Review           => CompositeWizardStep.Review
  }

  def previousStep: CompositeWizardStep = step match {
    case CompositeWizardStep.SelectSuggestion => CompositeWizardStep.SelectSuggestion
    case CompositeWizardStep.EnterName        => CompositeWizardStep.SelectSuggestion
    case CompositeWizardStep.DefineFields     => CompositeWizardStep.EnterName
    case CompositeWizardStep.AddProjections   => CompositeWizardStep.DefineFields
    case CompositeWizardStep.AlignFields      => CompositeWizardStep.AddProjections
    case CompositeWizardStep.Review           => CompositeWizardStep.AlignFields
  }

  def canProceed: Boolean = step match {
    case CompositeWizardStep.SelectSuggestion => true
    case CompositeWizardStep.EnterName        => name.nonEmpty
    case CompositeWizardStep.DefineFields     => fields.nonEmpty && fields.forall(f => f.name.nonEmpty && f.typeName.nonEmpty)
    case CompositeWizardStep.AddProjections   => true
    case CompositeWizardStep.AlignFields =>
      alignments.values.forall { alignment =>
        alignment.validationResult match {
          case typr.bridge.AlignmentStatus.Incompatible(_) => false
          case _                                           => true
        }
      }
    case CompositeWizardStep.Review => true
  }

  def currentSuggestion: Option[typr.bridge.CompositeTypeSuggester.CompositeSuggestion] =
    if (suggestions.isEmpty || selectedSuggestionIndex >= suggestions.size) None
    else Some(suggestions(selectedSuggestionIndex))
}

object CompositeWizardState {
  def initial: CompositeWizardState = CompositeWizardState(
    step = CompositeWizardStep.EnterName,
    name = "",
    description = "",
    primarySource = None,
    fields = Nil,
    alignedSources = Nil,
    alignments = Map.empty,
    selectedFieldIndex = 0,
    selectedAlignedSourceIndex = 0,
    selectedAlignmentSourceIndex = 0,
    addingField = false,
    addingAlignedSource = false,
    sourcePickerOpen = false,
    sourcePickerIndex = 0,
    error = None,
    generateDomainType = true,
    generateMappers = true,
    generateInterface = false,
    suggestions = Nil,
    selectedSuggestionIndex = 0,
    suggestionScrollOffset = 0
  )

  def withSuggestions(suggestions: List[typr.bridge.CompositeTypeSuggester.CompositeSuggestion]): CompositeWizardState = CompositeWizardState(
    step = if (suggestions.nonEmpty) CompositeWizardStep.SelectSuggestion else CompositeWizardStep.EnterName,
    name = "",
    description = "",
    primarySource = None,
    fields = Nil,
    alignedSources = Nil,
    alignments = Map.empty,
    selectedFieldIndex = 0,
    selectedAlignedSourceIndex = 0,
    selectedAlignmentSourceIndex = 0,
    addingField = false,
    addingAlignedSource = false,
    sourcePickerOpen = false,
    sourcePickerIndex = 0,
    error = None,
    generateDomainType = true,
    generateMappers = true,
    generateInterface = false,
    suggestions = suggestions,
    selectedSuggestionIndex = 0,
    suggestionScrollOffset = 0
  )

  def fromDomainType(dt: typr.bridge.DomainTypeDefinition): CompositeWizardState = CompositeWizardState(
    step = CompositeWizardStep.EnterName,
    name = dt.name,
    description = dt.description.getOrElse(""),
    primarySource = dt.primary,
    fields = dt.fields.map(FieldEditState.fromCompositeField),
    alignedSources = dt.alignedSources.values.map(AlignedSourceEditState.fromAlignedSource).toList,
    alignments = Map.empty,
    selectedFieldIndex = 0,
    selectedAlignedSourceIndex = 0,
    selectedAlignmentSourceIndex = 0,
    addingField = false,
    addingAlignedSource = false,
    sourcePickerOpen = false,
    sourcePickerIndex = 0,
    error = None,
    generateDomainType = dt.generateDomainType,
    generateMappers = dt.generateMappers,
    generateInterface = dt.generateInterface,
    suggestions = Nil,
    selectedSuggestionIndex = 0,
    suggestionScrollOffset = 0
  )

  // Backwards compatibility alias
  def fromCompositeType(ct: typr.bridge.DomainTypeDefinition): CompositeWizardState = fromDomainType(ct)
}

/** State for editing an existing domain type */
case class CompositeEditorState(
    name: String,
    description: String,
    primarySource: Option[typr.bridge.PrimarySource],
    fields: List[FieldEditState],
    alignedSources: List[AlignedSourceEditState],
    alignments: Map[String, FieldAlignmentState],
    selectedTab: CompositeEditorTab,
    selectedFieldIndex: Int,
    selectedAlignedSourceIndex: Int,
    addingField: Boolean,
    addingAlignedSource: Boolean,
    modified: Boolean
) {
  def toDomainTypeDefinition: typr.bridge.DomainTypeDefinition = typr.bridge.DomainTypeDefinition(
    name = name,
    primary = primarySource,
    fields = fields.map(_.toCompositeField),
    alignedSources = alignedSources.map(a => a.key -> a.toAlignedSource).toMap,
    description = if (description.isEmpty) None else Some(description),
    generateDomainType = true,
    generateMappers = true,
    generateInterface = false,
    generateBuilder = false,
    generateCopy = true
  )

  // Backwards compatibility
  def projections: List[AlignedSourceEditState] = alignedSources
  def selectedProjectionIndex: Int = selectedAlignedSourceIndex
  def addingProjection: Boolean = addingAlignedSource
  def toCompositeTypeDefinition: typr.bridge.DomainTypeDefinition = toDomainTypeDefinition
}

sealed trait CompositeEditorTab
object CompositeEditorTab {
  case object Fields extends CompositeEditorTab
  case object AlignedSources extends CompositeEditorTab
  case object Alignment extends CompositeEditorTab
  case object Options extends CompositeEditorTab

  // Backwards compatibility
  val Projections: AlignedSources.type = AlignedSources
}

object CompositeEditorState {
  def fromDomainType(dt: typr.bridge.DomainTypeDefinition): CompositeEditorState = CompositeEditorState(
    name = dt.name,
    description = dt.description.getOrElse(""),
    primarySource = dt.primary,
    fields = dt.fields.map(FieldEditState.fromCompositeField),
    alignedSources = dt.alignedSources.values.map(AlignedSourceEditState.fromAlignedSource).toList,
    alignments = Map.empty,
    selectedTab = CompositeEditorTab.Fields,
    selectedFieldIndex = 0,
    selectedAlignedSourceIndex = 0,
    addingField = false,
    addingAlignedSource = false,
    modified = false
  )

  // Backwards compatibility alias
  def fromCompositeType(ct: typr.bridge.DomainTypeDefinition): CompositeEditorState = fromDomainType(ct)
}

// ═══════════════════════════════════════════════════════════════════════════════
// Domain Type Builder State (single-screen builder replacing multi-step wizard)
// ═══════════════════════════════════════════════════════════════════════════════

/** Status of a field mapping in the alignment matrix */
sealed trait MappingStatus
object MappingStatus {
  case object Matched extends MappingStatus
  case object Missing extends MappingStatus
  case object Extra extends MappingStatus
  case class TypeWidened(from: String, to: String) extends MappingStatus
  case class TypeMismatch(expected: String, actual: String) extends MappingStatus
}

/** Type warning when fields from multiple sources have conflicting types */
case class TypeWarning(
    fieldName: String,
    message: String,
    sources: List[String]
)

/** Field mapping for an aligned source */
case class FieldMapping(
    canonicalField: String,
    sourceField: Option[String],
    sourceType: Option[String],
    status: MappingStatus,
    comment: String
)

/** State for a single aligned source in the builder */
case class AlignedSourceBuilderState(
    sourceName: String,
    entityPath: String,
    mode: typr.bridge.CompatibilityMode,
    fieldMappings: List[FieldMapping],
    expanded: Boolean,
    selectedMappingIndex: Int
) {
  def key: String = s"$sourceName:$entityPath"
}

object AlignedSourceBuilderState {
  def empty(sourceName: String, entityPath: String): AlignedSourceBuilderState = AlignedSourceBuilderState(
    sourceName = sourceName,
    entityPath = entityPath,
    mode = typr.bridge.CompatibilityMode.Superset,
    fieldMappings = Nil,
    expanded = true,
    selectedMappingIndex = 0
  )
}

/** State for a primary field with checkbox for inclusion */
case class PrimaryFieldState(
    name: String,
    dbType: String,
    nullable: Boolean,
    included: Boolean,
    canonicalName: String,
    canonicalType: String,
    comment: String
)

/** Which step of the builder we're on */
sealed trait BuilderStep
object BuilderStep {
  case object SelectSuggestion extends BuilderStep
  case object Builder extends BuilderStep
}

/** Which section of the builder is focused */
sealed trait BuilderSection
object BuilderSection {
  case object Name extends BuilderSection
  case object PrimarySource extends BuilderSection
  case object PrimaryFields extends BuilderSection
  case object AlignedSources extends BuilderSection
}

/** State for the single-screen domain type builder */
case class DomainTypeBuilderState(
    step: BuilderStep,
    suggestions: List[typr.bridge.CompositeTypeSuggester.CompositeSuggestion],
    selectedSuggestionIndex: Int,
    suggestionScrollOffset: Int,
    name: String,
    primarySourceName: Option[String],
    primaryEntityPath: Option[String],
    primaryFields: List[PrimaryFieldState],
    alignedSources: List[AlignedSourceBuilderState],
    focusedSection: BuilderSection,
    selectedFieldIndex: Int,
    selectedAlignedSourceIndex: Int,
    typeWarnings: List[TypeWarning],
    error: Option[String],
    addingAlignedSource: Boolean,
    sourcePickerOpen: Boolean,
    sourcePickerIndex: Int,
    entityPickerOpen: Boolean,
    entityPickerIndex: Int,
    primaryEntityPickerOpen: Boolean,
    primaryEntityPickerIndex: Int
) {
  def selectedPrimaryField: Option[PrimaryFieldState] =
    primaryFields.lift(selectedFieldIndex)

  def selectedAlignedSource: Option[AlignedSourceBuilderState] =
    alignedSources.lift(selectedAlignedSourceIndex)

  def includedFields: List[PrimaryFieldState] =
    primaryFields.filter(_.included)

  def canSave: Boolean =
    name.nonEmpty && primarySourceName.isDefined && primaryEntityPath.isDefined && includedFields.nonEmpty

  def toDomainTypeDefinition: typr.bridge.DomainTypeDefinition = {
    val fields = includedFields.map { pf =>
      typr.bridge.DomainField(
        name = pf.canonicalName,
        typeName = pf.canonicalType,
        nullable = pf.nullable,
        array = false,
        description = if (pf.comment.nonEmpty) Some(pf.comment) else None
      )
    }

    val alignedSourceMap = alignedSources.map { as =>
      as.key -> typr.bridge.AlignedSource(
        sourceName = as.sourceName,
        entityPath = as.entityPath,
        mode = as.mode,
        mappings = as.fieldMappings.collect {
          case fm if fm.sourceField.isDefined && fm.sourceField.get != fm.canonicalField =>
            fm.canonicalField -> fm.sourceField.get
        }.toMap,
        exclude = Set.empty,
        includeExtra = Nil,
        readonly = false
      )
    }.toMap

    typr.bridge.DomainTypeDefinition(
      name = name,
      primary = for {
        sn <- primarySourceName
        ep <- primaryEntityPath
      } yield typr.bridge.PrimarySource(sn, ep),
      fields = fields,
      alignedSources = alignedSourceMap,
      description = None,
      generateDomainType = true,
      generateMappers = true,
      generateInterface = false,
      generateBuilder = false,
      generateCopy = true
    )
  }
}

object DomainTypeBuilderState {
  def withSuggestions(suggestions: List[typr.bridge.CompositeTypeSuggester.CompositeSuggestion]): DomainTypeBuilderState = DomainTypeBuilderState(
    step = if (suggestions.nonEmpty) BuilderStep.SelectSuggestion else BuilderStep.Builder,
    suggestions = suggestions,
    selectedSuggestionIndex = 0,
    suggestionScrollOffset = 0,
    name = "",
    primarySourceName = None,
    primaryEntityPath = None,
    primaryFields = Nil,
    alignedSources = Nil,
    focusedSection = BuilderSection.Name,
    selectedFieldIndex = 0,
    selectedAlignedSourceIndex = 0,
    typeWarnings = Nil,
    error = None,
    addingAlignedSource = false,
    sourcePickerOpen = false,
    sourcePickerIndex = 0,
    entityPickerOpen = false,
    entityPickerIndex = 0,
    primaryEntityPickerOpen = false,
    primaryEntityPickerIndex = 0
  )

  def initial: DomainTypeBuilderState = withSuggestions(Nil)

  def fromDomainType(dt: typr.bridge.DomainTypeDefinition): DomainTypeBuilderState = {
    val primaryFields = dt.fields.map { f =>
      PrimaryFieldState(
        name = f.name,
        dbType = f.typeName,
        nullable = f.nullable,
        included = true,
        canonicalName = f.name,
        canonicalType = f.typeName,
        comment = f.description.getOrElse("")
      )
    }

    val alignedSources = dt.alignedSources.values.toList.map { as =>
      AlignedSourceBuilderState(
        sourceName = as.sourceName,
        entityPath = as.entityPath,
        mode = as.mode,
        fieldMappings = Nil,
        expanded = false,
        selectedMappingIndex = 0
      )
    }

    DomainTypeBuilderState(
      step = BuilderStep.Builder,
      suggestions = Nil,
      selectedSuggestionIndex = 0,
      suggestionScrollOffset = 0,
      name = dt.name,
      primarySourceName = dt.primary.map(_.sourceName),
      primaryEntityPath = dt.primary.map(_.entityPath),
      primaryFields = primaryFields,
      alignedSources = alignedSources,
      focusedSection = BuilderSection.Name,
      selectedFieldIndex = 0,
      selectedAlignedSourceIndex = 0,
      typeWarnings = Nil,
      error = None,
      addingAlignedSource = false,
      sourcePickerOpen = false,
      sourcePickerIndex = 0,
      entityPickerOpen = false,
      entityPickerIndex = 0,
      primaryEntityPickerOpen = false,
      primaryEntityPickerIndex = 0
    )
  }

  /** Create a builder state from a selected suggestion */
  def fromSuggestion(suggestion: typr.bridge.CompositeTypeSuggester.CompositeSuggestion): DomainTypeBuilderState = {
    val primaryFields = suggestion.fields.map { f =>
      PrimaryFieldState(
        name = f.name,
        dbType = f.typeName,
        nullable = f.nullable,
        included = true,
        canonicalName = f.name,
        canonicalType = f.typeName,
        comment = ""
      )
    }

    val primaryProjection = suggestion.projections.headOption
    val otherProjections = suggestion.projections.drop(1)

    val alignedSources = otherProjections.map { proj =>
      val fieldMappings = proj.allFields.map { sf =>
        val canonicalField = suggestion.fields.find(_.name == sf.name)
        FieldMapping(
          canonicalField = canonicalField.map(_.name).getOrElse(sf.name),
          sourceField = Some(sf.name),
          sourceType = Some(sf.typeName),
          status = if (canonicalField.isDefined) MappingStatus.Matched else MappingStatus.Extra,
          comment = if (canonicalField.isDefined) "exact match" else "extra field"
        )
      }
      AlignedSourceBuilderState(
        sourceName = proj.sourceName,
        entityPath = proj.entityPath,
        mode = typr.bridge.CompatibilityMode.Superset,
        fieldMappings = fieldMappings,
        expanded = false,
        selectedMappingIndex = 0
      )
    }

    DomainTypeBuilderState(
      step = BuilderStep.Builder,
      suggestions = Nil,
      selectedSuggestionIndex = 0,
      suggestionScrollOffset = 0,
      name = suggestion.name,
      primarySourceName = primaryProjection.map(_.sourceName),
      primaryEntityPath = primaryProjection.map(_.entityPath),
      primaryFields = primaryFields,
      alignedSources = alignedSources,
      focusedSection = BuilderSection.PrimaryFields,
      selectedFieldIndex = 0,
      selectedAlignedSourceIndex = 0,
      typeWarnings = Nil,
      error = None,
      addingAlignedSource = false,
      sourcePickerOpen = false,
      sourcePickerIndex = 0,
      entityPickerOpen = false,
      entityPickerIndex = 0,
      primaryEntityPickerOpen = false,
      primaryEntityPickerIndex = 0
    )
  }

  /** Create a builder state from a config DomainType for editing */
  def fromConfigDomainType(name: String, dt: typr.config.generated.DomainType): DomainTypeBuilderState = {
    val (primarySourceName, primaryEntityPath) = dt.primary match {
      case Some(primary) =>
        val colonIdx = primary.indexOf(':')
        if (colonIdx > 0) (Some(primary.substring(0, colonIdx)), Some(primary.substring(colonIdx + 1)))
        else (None, None)
      case None => (None, None)
    }

    val primaryFields = dt.fields
      .map { case (fieldName, spec) =>
        val (typeName, nullable, array) = spec match {
          case typr.config.generated.FieldSpecString(s) => (s, false, false)
          case typr.config.generated.FieldSpecObject(arr, _, _, nul, t) =>
            (t, nul.getOrElse(false), arr.getOrElse(false))
        }
        PrimaryFieldState(
          name = fieldName,
          dbType = typeName,
          nullable = nullable,
          included = true,
          canonicalName = fieldName,
          canonicalType = typeName,
          comment = ""
        )
      }
      .toList
      .sortBy(_.name)

    val alignedSourcesConfig = dt.alignedSources.orElse(dt.projections).getOrElse(Map.empty)
    val alignedSources = alignedSourcesConfig.map { case (key, as) =>
      val colonIdx = key.indexOf(':')
      val (sourceName, entityPath) = if (colonIdx > 0) {
        (key.substring(0, colonIdx), as.entity.getOrElse(key.substring(colonIdx + 1)))
      } else {
        (key, as.entity.getOrElse(""))
      }
      val mode = as.mode match {
        case Some("exact")  => typr.bridge.CompatibilityMode.Exact
        case Some("subset") => typr.bridge.CompatibilityMode.Subset
        case _              => typr.bridge.CompatibilityMode.Superset
      }
      AlignedSourceBuilderState(
        sourceName = sourceName,
        entityPath = entityPath,
        mode = mode,
        fieldMappings = Nil,
        expanded = false,
        selectedMappingIndex = 0
      )
    }.toList

    DomainTypeBuilderState(
      step = BuilderStep.Builder,
      suggestions = Nil,
      selectedSuggestionIndex = 0,
      suggestionScrollOffset = 0,
      name = name,
      primarySourceName = primarySourceName,
      primaryEntityPath = primaryEntityPath,
      primaryFields = primaryFields,
      alignedSources = alignedSources,
      focusedSection = BuilderSection.Name,
      selectedFieldIndex = 0,
      selectedAlignedSourceIndex = 0,
      typeWarnings = Nil,
      error = None,
      addingAlignedSource = false,
      sourcePickerOpen = false,
      sourcePickerIndex = 0,
      entityPickerOpen = false,
      entityPickerIndex = 0,
      primaryEntityPickerOpen = false,
      primaryEntityPickerIndex = 0
    )
  }
}

/** Kind of Bridge type for display purposes */
sealed trait BridgeTypeKind
object BridgeTypeKind {
  case object Scalar extends BridgeTypeKind
  case object Composite extends BridgeTypeKind
}

/** Summary information for displaying Bridge types in the list */
case class BridgeTypeSummary(
    name: String,
    kind: BridgeTypeKind,
    dbMatchCount: Int,
    modelMatchCount: Int,
    projectionCount: Int,
    alignmentStatus: typr.bridge.AlignmentStatus
)

// ═══════════════════════════════════════════════════════════════════════════════
// Main TUI State
// ═══════════════════════════════════════════════════════════════════════════════

case class TuiState(
    config: TyprConfig,
    configPath: Path,
    currentScreen: AppScreen,
    previousScreen: Option[AppScreen],
    navigation: NavigationState,
    sourceStatuses: Map[SourceName, SourceStatus],
    hasUnsavedChanges: Boolean,
    statusMessage: Option[(String, tui.Color)],
    shouldExit: Boolean,
    hoverPosition: Option[(Int, Int)],
    terminalSize: Option[(Int, Int)]
)

object TuiState {
  def initial(config: TyprConfig, configPath: Path): TuiState = TuiState(
    config = config,
    configPath = configPath,
    currentScreen = AppScreen.Splash(SplashState.initial),
    previousScreen = None,
    navigation = NavigationState.initial,
    sourceStatuses = Map.empty,
    hasUnsavedChanges = false,
    statusMessage = None,
    shouldExit = false,
    hoverPosition = None,
    terminalSize = None
  )
}

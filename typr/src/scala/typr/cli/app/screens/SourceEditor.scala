package typr.cli.app.screens
import typr.cli.app.components.Run.given

import jatatui.components.button.Button
import jatatui.components.chrome.ScreenFrame
import typr.cli.app.components.Link
import jatatui.components.router.{RouterApi, Screen as RouterScreen}
import jatatui.components.textinput.{TextInputComponent, TextInputProps}
import jatatui.core.style.{Color, Modifier, Style}
import jatatui.react.Element
import jatatui.react.Components.*
import tui.crossterm.KeyCode
import typr.cli.app.{AppApi, ConnectionTest}
import typr.config.generated.{StringOrArrayArray, StringOrArrayString}

/** Form-based editor for a single source. Each relevant attribute (host, port, …) is a [[TextInputComponent]] row; Tab cycles through them, Save persists via [[AppApi.updateConfig]] which writes
  * typr.yaml back to disk.
  *
  * Field set comes from [[SourceForm.fieldsByKind]] — keyed on the source's inferred kind, with unknown fields passed through unchanged on save (so e.g. `selectors:` or `sql_scripts:` stay intact).
  *
  * Type switching is intentionally not exposed yet — changing a postgres source to a duckdb one is rare and risky; an explicit "convert" flow belongs in the wizard.
  */
object SourceEditor {

  private val titleStyle: Style = Style.empty.withFg(Color.MAGENTA).withAddModifier(Modifier.BOLD)
  private val hintStyle: Style = Style.empty.withFg(Color.DARK_GRAY)
  private val errStyle: Style = Style.empty.withFg(Color.RED).withAddModifier(Modifier.BOLD)
  private val okStyle: Style = Style.empty.withFg(Color.GREEN).withAddModifier(Modifier.BOLD)

  /** What the screen shows under the form: nothing, "saved", or an error. Local to the editor (cleared on every edit).
    */
  sealed trait SaveResult
  object SaveResult {
    case object None extends SaveResult
    case object Saved extends SaveResult
    final case class Failed(error: String) extends SaveResult
  }

  /** Connection-test progress + result, distinct from save state so they can be displayed simultaneously (e.g. "✓ saved · ✓ connected to PostgreSQL 16.2").
    */
  sealed trait TestStatus
  object TestStatus {
    case object Idle extends TestStatus
    case object Running extends TestStatus
    final case class Success(message: String) extends TestStatus
    final case class Failure(error: String) extends TestStatus
  }

  def element(name: String): Element =
    component { ctx =>
      val router = RouterApi.useRouter(ctx)
      val app = AppApi.use(ctx)
      val back = () => router.pop()

      ctx.onGlobalKey(new KeyCode.Esc, () => back())

      val initial = app.config.sources
        .getOrElse(Map.empty)
        .get(name)
        .map(SourceForm.parse)

      initial match {
        case None    => notFound(name, back)
        case Some(s) => editor(name, s, app, back)
      }
    }

  private def notFound(name: String, back: () => Unit): Element = {
    val body = column(
      fill(1, empty()),
      length(1, text(s"  source '$name' not found", errStyle)),
      length(1, text("  press esc to return", hintStyle)),
      fill(1, empty())
    )
    ScreenFrame.of("sources", back, body)
  }

  private def editor(name: String, initial: SourceForm.State, app: AppApi, back: () => Unit): Element =
    component { ctx =>
      val form = ctx.useState(() => initial)
      val saved = ctx.useState(() => SaveResult.None: SaveResult)
      val testStatus = ctx.useState(() => TestStatus.Idle: TestStatus)
      val rerender = ctx.requestRerender()

      def save(): Unit = {
        val newJson = form.get.toJson
        val newSources = app.config.sources.getOrElse(Map.empty) + (name -> newJson)
        app.updateConfig(c => c.copy(sources = Some(newSources))) match {
          case Right(_) => saved.set(SaveResult.Saved)
          case Left(e)  => saved.set(SaveResult.Failed(Option(e.getMessage).getOrElse(e.toString)))
        }
      }

      def runTest(): Unit = {
        testStatus.set(TestStatus.Running)
        val json = form.get.toJson
        val t = new Thread(
          () => {
            val result = ConnectionTest.run(json) match {
              case Right(msg) => TestStatus.Success(msg)
              case Left(err)  => TestStatus.Failure(err)
            }
            testStatus.set(result)
            rerender.run()
          },
          s"conn-test-$name"
        )
        t.setDaemon(true)
        t.start()
      }

      val fields = form.get.fields

      val rows: List[Element] = fields.zipWithIndex.map { case (field, idx) =>
        length(
          3,
          fieldRow(
            form.get.get(field.key),
            field.key,
            field.label,
            idx == 0,
            v => {
              form.update(_.withField(field.key, v))
              saved.set(SaveResult.None) // dirty again
              testStatus.set(TestStatus.Idle) // any old test result no longer applies
            }
          )
        )
      }

      val showTest = ConnectionTest.isTestable(form.get.toJson) && testStatus.get != TestStatus.Running
      val testButtonLabel = testStatus.get match {
        case TestStatus.Idle | _: TestStatus.Success | _: TestStatus.Failure => "test connection"
        case TestStatus.Running                                              => "testing…"
      }

      val actionRow = length(
        3,
        jatatui.react.Components.row(
          length(14, Button.of("save", "save", true, () => save())),
          length(2, empty()),
          length(22, Button.of(testButtonLabel, "test", false, () => if (showTest) runTest())),
          fill(1, empty())
        )
      )

      val statusLine = saved.get match {
        case SaveResult.None        => text("", hintStyle)
        case SaveResult.Saved       => text(s"  ✓ saved to ${app.configPath.getFileName}", okStyle)
        case SaveResult.Failed(err) => text(s"  ✗ save failed: ${err.take(120)}", errStyle)
      }

      val testLine = testStatus.get match {
        case TestStatus.Idle         => text("", hintStyle)
        case TestStatus.Running      => text("  ◕ testing connection…", Style.empty.withFg(Color.CYAN))
        case TestStatus.Success(msg) => text(s"  ✓ ${msg.take(160)}", okStyle)
        case TestStatus.Failure(err) => text(s"  ✗ ${err.take(160)}", errStyle)
      }

      val title = column(
        length(1, empty()),
        length(1, text(s"  source · $name  ·  ${form.get.kind}", titleStyle))
      )

      val consumers = outputsReferencing(app, name)
      val router = RouterApi.useRouter(ctx)
      val consumersRows =
        if (consumers.isEmpty) Nil
        else
          List(
            length(1, empty()),
            length(1, text(s"  used by ${consumers.size} output${if (consumers.size == 1) "" else "s"}:", hintStyle))
          ) ::: consumers.map(out =>
            length(
              1,
              consumerLink(out, () => router.push(RouterScreen.of(s"output · $out", OutputEditor.element(out))))
            )
          )

      val body = column(
        (rows
          ::: List(
            length(1, empty()),
            actionRow,
            length(1, statusLine),
            length(1, testLine)
          )
          ::: consumersRows
          ::: List(
            fill(1, empty()),
            length(1, text("  tab navigate · enter (on save) commit · esc back", hintStyle))
          ))*
      )

      ScreenFrame.withTitle("sources", back, title, body)
    }

  /** Outputs whose `sources:` field references this source (or omits sources, which means "all sources"). Sorted alphabetically. Used to render clickable consumer links.
    */
  private def outputsReferencing(app: AppApi, sourceName: String): List[String] =
    app.config.outputs
      .getOrElse(Map.empty)
      .toList
      .filter { case (_, out) =>
        out.sources match {
          case Some(StringOrArrayString(s))  => s == sourceName
          case Some(StringOrArrayArray(arr)) => arr.contains(sourceName)
          case None                          => true
        }
      }
      .map(_._1)
      .sorted

  private val consumerStyle: Style = Style.empty.withFg(Color.YELLOW)

  private def consumerLink(outputName: String, onActivate: Runnable): Element =
    Link.focusable(
      false,
      onActivate,
      { (focused: Boolean) =>
        val style =
          if (focused) Style.empty.withBg(Color.YELLOW).withFg(Color.BLACK).withAddModifier(Modifier.BOLD)
          else consumerStyle
        text(s"    → $outputName", style)
      }
    )

  /** Bordered text-input row with the label baked into the top border. Default jatatui styles are `Style.empty()` for the value, which renders in terminal-default colour and is easy to miss. We
    * override with explicit white-on-default for the value, yellow+bold when focused, and a reversed cursor so the caret is unmistakable.
    */
  private val valueStyle: Style = Style.empty.withFg(Color.WHITE)
  private val focusedValueStyle: Style = Style.empty.withFg(Color.YELLOW).withAddModifier(Modifier.BOLD)
  private val cursorStyle: Style = Style.empty.withAddModifier(Modifier.REVERSED)

  private def fieldRow(
      value: String,
      key: String,
      label: String,
      autoFocus: Boolean,
      onChange: String => Unit
  ): Element =
    TextInputComponent.of(
      TextInputProps
        .of(value, v => onChange(v))
        .withTitle(label)
        .withFocusId(s"src:$key")
        .withAutoFocus(autoFocus)
        .withStyle(valueStyle)
        .withFocusedStyle(focusedValueStyle)
        .withCursorStyle(cursorStyle)
    )
}

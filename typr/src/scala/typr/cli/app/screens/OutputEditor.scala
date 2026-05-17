package typr.cli.app.screens
import typr.cli.app.components.Run.given

import jatatui.components.button.Button
import jatatui.components.chrome.ScreenFrame
import jatatui.components.router.RouterApi
import jatatui.components.textinput.{TextInputComponent, TextInputProps}
import jatatui.core.style.{Color, Modifier, Style}
import jatatui.react.Element
import jatatui.react.Components.*
import tui.crossterm.KeyCode
import typr.cli.app.AppApi
import typr.config.generated.{Output, StringOrArray, StringOrArrayArray, StringOrArrayString}

/** Form-based editor for a single output. One [[TextInputComponent]] per attribute. Save writes the whole output map back through [[AppApi.updateConfig]].
  *
  * Common attributes only for the first cut: language, path, package, sources, db_lib, json, framework, effect_type. The `matchers`, `bridge`, and scala-specific `Json` blob are deeper nested and
  * worth their own dedicated editors later.
  */
object OutputEditor {

  private val titleStyle: Style = Style.empty.withFg(Color.MAGENTA).withAddModifier(Modifier.BOLD)
  private val hintStyle: Style = Style.empty.withFg(Color.DARK_GRAY)
  private val errStyle: Style = Style.empty.withFg(Color.RED).withAddModifier(Modifier.BOLD)
  private val okStyle: Style = Style.empty.withFg(Color.GREEN).withAddModifier(Modifier.BOLD)
  private val valueStyle: Style = Style.empty.withFg(Color.WHITE)
  private val focusedValueStyle: Style = Style.empty.withFg(Color.YELLOW).withAddModifier(Modifier.BOLD)
  private val cursorStyle: Style = Style.empty.withAddModifier(Modifier.REVERSED)

  sealed trait SaveResult
  object SaveResult {
    case object None extends SaveResult
    case object Saved extends SaveResult
    final case class Failed(error: String) extends SaveResult
  }

  /** Flat mutable state for the form. `package` is keyword-clashed so the field is named `pkg` here; we map back on write. Optional fields stay as `Option[String]` so we can round-trip "not set"
    * cleanly.
    */
  final case class FormState(
      language: String,
      path: String,
      pkg: String,
      sources: String, // comma-separated; we split on save
      dbLib: String,
      json: String,
      framework: String,
      effectType: String,
      original: Output
  )

  object FormState {
    def from(out: Output): FormState = FormState(
      language = out.language,
      path = out.path,
      pkg = out.`package`,
      sources = out.sources match {
        case Some(StringOrArrayString(s))  => s
        case Some(StringOrArrayArray(arr)) => arr.mkString(", ")
        case None                          => ""
      },
      dbLib = out.db_lib.getOrElse(""),
      json = out.json.getOrElse(""),
      framework = out.framework.getOrElse(""),
      effectType = out.effect_type.getOrElse(""),
      original = out
    )

    /** Round-trip back to an Output, preserving fields we don't expose (matchers, scala, bridge). */
    def toOutput(f: FormState): Output = {
      val sourcesField: Option[StringOrArray] = {
        val parts = f.sources.split(",").iterator.map(_.trim).filter(_.nonEmpty).toList
        parts match {
          case Nil      => None
          case s :: Nil => Some(StringOrArrayString(s))
          case xs       => Some(StringOrArrayArray(xs))
        }
      }
      f.original.copy(
        language = f.language,
        path = f.path,
        `package` = f.pkg,
        sources = sourcesField,
        db_lib = Option(f.dbLib).filter(_.nonEmpty),
        json = Option(f.json).filter(_.nonEmpty),
        framework = Option(f.framework).filter(_.nonEmpty),
        effect_type = Option(f.effectType).filter(_.nonEmpty)
      )
    }
  }

  def element(name: String): Element =
    component { ctx =>
      val router = RouterApi.useRouter(ctx)
      val app = AppApi.use(ctx)
      val back = () => router.pop()

      ctx.onGlobalKey(new KeyCode.Esc, () => back())

      val initial = app.config.outputs.getOrElse(Map.empty).get(name).map(FormState.from)
      initial match {
        case None    => notFound(name, back)
        case Some(s) => editor(name, s, app, back)
      }
    }

  private def notFound(name: String, back: () => Unit): Element = {
    val body = column(
      fill(1, empty()),
      length(1, text(s"  output '$name' not found", errStyle)),
      length(1, text("  press esc to return", hintStyle)),
      fill(1, empty())
    )
    ScreenFrame.of("outputs", back, body)
  }

  private def editor(name: String, initial: FormState, app: AppApi, back: () => Unit): Element =
    component { ctx =>
      val form = ctx.useState(() => initial)
      val saved = ctx.useState(() => SaveResult.None: SaveResult)

      def save(): Unit = {
        val newOutput = FormState.toOutput(form.get)
        val newOutputs = app.config.outputs.getOrElse(Map.empty) + (name -> newOutput)
        app.updateConfig(c => c.copy(outputs = Some(newOutputs))) match {
          case Right(_) => saved.set(SaveResult.Saved)
          case Left(e)  => saved.set(SaveResult.Failed(Option(e.getMessage).getOrElse(e.toString)))
        }
      }

      val fields: List[Element] = List(
        length(3, fieldRow("language", "out:language", form.get.language, autoFocus = true, v => { form.update(_.copy(language = v)); saved.set(SaveResult.None) })),
        length(3, fieldRow("path", "out:path", form.get.path, autoFocus = false, v => { form.update(_.copy(path = v)); saved.set(SaveResult.None) })),
        length(3, fieldRow("package", "out:pkg", form.get.pkg, autoFocus = false, v => { form.update(_.copy(pkg = v)); saved.set(SaveResult.None) })),
        length(3, fieldRow("sources", "out:sources", form.get.sources, autoFocus = false, v => { form.update(_.copy(sources = v)); saved.set(SaveResult.None) })),
        length(3, fieldRow("db_lib", "out:dbLib", form.get.dbLib, autoFocus = false, v => { form.update(_.copy(dbLib = v)); saved.set(SaveResult.None) })),
        length(3, fieldRow("json", "out:json", form.get.json, autoFocus = false, v => { form.update(_.copy(json = v)); saved.set(SaveResult.None) })),
        length(3, fieldRow("framework", "out:framework", form.get.framework, autoFocus = false, v => { form.update(_.copy(framework = v)); saved.set(SaveResult.None) })),
        length(3, fieldRow("effect_type", "out:effect", form.get.effectType, autoFocus = false, v => { form.update(_.copy(effectType = v)); saved.set(SaveResult.None) }))
      )

      val saveButton = length(3, Button.of("save", "save", true, () => save()))

      val statusLine = saved.get match {
        case SaveResult.None        => text("", hintStyle)
        case SaveResult.Saved       => text(s"  ✓ saved to ${app.configPath.getFileName}", okStyle)
        case SaveResult.Failed(err) => text(s"  ✗ save failed: ${err.take(120)}", errStyle)
      }

      val title = column(
        length(1, empty()),
        length(1, text(s"  output · $name  ·  ${form.get.language}", titleStyle))
      )

      val body = column(
        (fields
          ::: List(
            length(1, empty()),
            saveButton,
            length(1, statusLine),
            fill(1, empty()),
            length(1, text("  tab navigate · enter (on save) commit · esc back", hintStyle))
          ))*
      )

      ScreenFrame.withTitle("outputs", back, title, body)
    }

  private def fieldRow(
      label: String,
      focusId: String,
      value: String,
      autoFocus: Boolean,
      onChange: String => Unit
  ): Element =
    TextInputComponent.of(
      TextInputProps
        .of(value, v => onChange(v))
        .withTitle(label)
        .withFocusId(focusId)
        .withAutoFocus(autoFocus)
        .withStyle(valueStyle)
        .withFocusedStyle(focusedValueStyle)
        .withCursorStyle(cursorStyle)
    )
}

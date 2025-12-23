package typr

object Banner {
  val DslEnabled =
    """|I see you have enabled usage of the SQL dsl - Great! (you can change it in the `Options` object you give to `typo`)
       |
       |Remember that you need to add the corresponding runtime jar to your project to use it.
       |something like this in sbt:
       |```
       |libraryDependencies += "dev.typr" %% "typr-dsl-anorm" % "<insert version>"
       |libraryDependencies += "dev.typr" %% "typr-dsl-doobie" % "<insert version>"
       |```
       |""".stripMargin

  val DslDisabled =
    """|I see you have NOT enabled usage of the SQL DSL! 
       |
       |If you get tired of writing SQL files for trivial things, this may be what you're looking for.
       |
       |You can enable it in the `Options` object you give to `typo`.
       |Note that if you do, you'll also need to add a runtime jar (typo will tell you details.)
       |""".stripMargin

  def text(dslIsEnabled: Boolean) = {
    s"""|Welcome to typo - Hopefully it'll make your work with Scala and Postgres a dream! :)
        |
        |If that's not the case, feel free to open issues and discussions so we can improve it!
        |
        |${if (dslIsEnabled) DslEnabled else DslDisabled}
        |
        |(this banner can be silenced by setting `silentBanner` in `Options`)
        |""".stripMargin
  }

  private var printed = false

  def maybePrint(options: Options): Unit =
    if (!options.silentBanner && !printed) {
      options.logger.info(text(dslIsEnabled = options.enableDsl))
      printed = true
    }
}

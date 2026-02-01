package typr.cli.config

import java.util.regex.Matcher

object EnvSubstitution {
  private val pattern = """\$\{([^}]+)\}""".r

  def substitute(yaml: String): Either[String, String] = {
    var errors = List.empty[String]
    val result = pattern.replaceAllIn(
      yaml,
      m => {
        val varName = m.group(1)
        sys.env.get(varName) match {
          case Some(value) => Matcher.quoteReplacement(value)
          case None =>
            errors = s"Environment variable not set: $varName" :: errors
            m.matched
        }
      }
    )
    if (errors.nonEmpty) Left(errors.reverse.mkString(", "))
    else Right(result)
  }
}

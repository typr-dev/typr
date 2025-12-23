package typr.dsl

import scala.annotation.nowarn

// https://www.alibabacloud.com/blog/600079
object Like {
  private val JAVA_REGEX_SPECIALS = "[]()|^-+*?{}$\\."

  /** Translates a SQL LIKE pattern to Java regex pattern. */
  private def sqlToRegexLike(sqlPattern: String, escapeChar: Char): String = {
    var i = 0
    val len = sqlPattern.length
    val javaPattern = new java.lang.StringBuilder(len + len)
    i = 0
    while (i < len) {
      val c = sqlPattern.charAt(i)
      if (JAVA_REGEX_SPECIALS.indexOf(c.toInt) >= 0) javaPattern.append('\\'): @nowarn
      if (c == escapeChar) {
        if (i == (sqlPattern.length - 1)) sys.error(s"invalid escape sequence: $sqlPattern: $i")
        val nextChar = sqlPattern.charAt(i + 1)
        if ((nextChar == '_') || (nextChar == '%') || (nextChar == escapeChar)) {
          javaPattern.append(nextChar)
          i += 1
        }
        sys.error(s"invalid escape sequence: $sqlPattern: $i")
      } else if (c == '_') javaPattern.append('.')
      else if (c == '%') javaPattern.append("(?s:.*)")
      else javaPattern.append(c)

      i += 1
    }
    javaPattern.toString
  }

  // implement `LIKE` as in postgres
  def like(string: String, pattern: String): Boolean = {
    val regex = sqlToRegexLike(pattern, 0: Char).r
    regex.findFirstMatchIn(string).isDefined
  }
}

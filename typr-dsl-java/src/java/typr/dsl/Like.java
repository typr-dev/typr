package typr.dsl;

import java.util.regex.Pattern;

/**
 * Implementation of SQL LIKE pattern matching. Converts SQL LIKE patterns to Java regex patterns.
 */
public class Like {
  private static final String JAVA_REGEX_SPECIALS = "[]()|^-+*?{}$\\.";

  /** Translates a SQL LIKE pattern to Java regex pattern. */
  private static String sqlToRegexLike(String sqlPattern, char escapeChar) {
    int len = sqlPattern.length();
    StringBuilder javaPattern = new StringBuilder(len + len);

    for (int i = 0; i < len; i++) {
      char c = sqlPattern.charAt(i);

      if (JAVA_REGEX_SPECIALS.indexOf(c) >= 0) {
        javaPattern.append('\\');
      }

      if (c == escapeChar) {
        if (i == sqlPattern.length() - 1) {
          throw new IllegalArgumentException(
              "Invalid escape sequence: " + sqlPattern + " at position " + i);
        }

        char nextChar = sqlPattern.charAt(i + 1);
        if (nextChar == '_' || nextChar == '%' || nextChar == escapeChar) {
          javaPattern.append(nextChar);
          i++;
          continue;
        }
        throw new IllegalArgumentException(
            "Invalid escape sequence: " + sqlPattern + " at position " + i);
      } else if (c == '_') {
        javaPattern.append('.');
      } else if (c == '%') {
        javaPattern.append("(?s:.*)");
      } else {
        javaPattern.append(c);
      }
    }

    return javaPattern.toString();
  }

  /**
   * Implement LIKE as in PostgreSQL.
   *
   * @param string The string to match against
   * @param pattern The SQL LIKE pattern
   * @return true if the string matches the pattern
   */
  public static boolean like(String string, String pattern) {
    String regex = sqlToRegexLike(pattern, (char) 0);
    Pattern p = Pattern.compile(regex);
    return p.matcher(string).matches();
  }

  /**
   * Implement LIKE with custom escape character.
   *
   * @param string The string to match against
   * @param pattern The SQL LIKE pattern
   * @param escapeChar The escape character to use
   * @return true if the string matches the pattern
   */
  public static boolean like(String string, String pattern, char escapeChar) {
    String regex = sqlToRegexLike(pattern, escapeChar);
    Pattern p = Pattern.compile(regex);
    return p.matcher(string).matches();
  }
}

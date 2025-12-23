package typr.runtime.internal;

import java.util.stream.Collectors;

public class stripMargin {
  public static String apply(String value) {
    return value.lines().map(stripMargin::fromLine).collect(Collectors.joining("\n"));
  }

  static String fromLine(String line) {
    int i = 0;
    while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
      i++;
    }
    if (i < line.length() && line.charAt(i) == '|') {
      return line.substring(i + 1);
    }
    return line.substring(i);
  }
}

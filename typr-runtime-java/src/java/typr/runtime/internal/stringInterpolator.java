package typr.runtime.internal;

public interface stringInterpolator {
  /**
   * String interpolation function that concatenates all string arguments together. Similar to
   * Scala's s"..." string interpolator.
   *
   * <p>Example: str("((", "1.0", ",", "2.0", "))") produces "((1.0,2.0))"
   */
  static String str(String... parts) {
    StringBuilder sb = new StringBuilder();
    for (String part : parts) {
      sb.append(part);
    }
    return sb.toString();
  }
}

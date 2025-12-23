package typr.data;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents Oracle's INTERVAL DAY TO SECOND type.
 *
 * <p>Can parse both Oracle's native format (+03 14:30:45.123456) and ISO-8601 duration format
 * (P3DT14H30M45.123456S). This handles the fact that Oracle returns ISO-8601 format in JSON but
 * native format in columns.
 */
public record OracleIntervalDS(int days, int hours, int minutes, int seconds, int nanos) {

  // Oracle format: +03 14:30:45.123456, -01 00:00:00.000000, +00 00:00:00.000000
  private static final Pattern ORACLE_FORMAT =
      Pattern.compile("([+-]?)(\\d+)\\s+(\\d+):(\\d+):(\\d+)(?:\\.(\\d+))?");

  // ISO-8601 format: P3DT14H30M45.123456S, -P1D, P0D
  private static final Pattern ISO_FORMAT =
      Pattern.compile("(-?)P(\\d+)?D?(?:T(\\d+)?H?(\\d+)?M?(\\d+)?(?:\\.(\\d+))?S?)?");

  public OracleIntervalDS {
    // All fields must have the same sign
    boolean hasNegative = days < 0 || hours < 0 || minutes < 0 || seconds < 0 || nanos < 0;
    boolean hasPositive = days > 0 || hours > 0 || minutes > 0 || seconds > 0 || nanos > 0;
    if (hasNegative && hasPositive) {
      throw new IllegalArgumentException("All fields must have the same sign");
    }
    // Validate ranges (absolute values)
    if (Math.abs(hours) > 23) {
      throw new IllegalArgumentException("Hours must be between 0 and 23, got: " + Math.abs(hours));
    }
    if (Math.abs(minutes) > 59) {
      throw new IllegalArgumentException(
          "Minutes must be between 0 and 59, got: " + Math.abs(minutes));
    }
    if (Math.abs(seconds) > 59) {
      throw new IllegalArgumentException(
          "Seconds must be between 0 and 59, got: " + Math.abs(seconds));
    }
    if (Math.abs(nanos) > 999_999_999) {
      throw new IllegalArgumentException(
          "Nanos must be between 0 and 999999999, got: " + Math.abs(nanos));
    }
  }

  /**
   * Parse from either Oracle format (+03 14:30:45.123456) or ISO-8601 format (P3DT14H30M45.123456S)
   */
  public static OracleIntervalDS parse(String s) {
    if (s == null || s.isEmpty()) {
      throw new IllegalArgumentException("Cannot parse null or empty interval");
    }

    // Try Oracle format first
    Matcher oracleMatcher = ORACLE_FORMAT.matcher(s);
    if (oracleMatcher.matches()) {
      String sign = oracleMatcher.group(1);
      int days = Integer.parseInt(oracleMatcher.group(2));
      int hours = Integer.parseInt(oracleMatcher.group(3));
      int minutes = Integer.parseInt(oracleMatcher.group(4));
      int seconds = Integer.parseInt(oracleMatcher.group(5));
      String fracStr = oracleMatcher.group(6);
      int nanos = fracStr != null ? parseFractionalSeconds(fracStr) : 0;

      boolean negative = "-".equals(sign);
      return new OracleIntervalDS(
          negative ? -days : days,
          negative ? -hours : hours,
          negative ? -minutes : minutes,
          negative ? -seconds : seconds,
          negative ? -nanos : nanos);
    }

    // Try ISO-8601 format
    Matcher isoMatcher = ISO_FORMAT.matcher(s);
    if (isoMatcher.matches()) {
      String sign = isoMatcher.group(1);
      String daysStr = isoMatcher.group(2);
      String hoursStr = isoMatcher.group(3);
      String minutesStr = isoMatcher.group(4);
      String secondsStr = isoMatcher.group(5);
      String fracStr = isoMatcher.group(6);

      int days = daysStr != null ? Integer.parseInt(daysStr) : 0;
      int hours = hoursStr != null ? Integer.parseInt(hoursStr) : 0;
      int minutes = minutesStr != null ? Integer.parseInt(minutesStr) : 0;
      int seconds = secondsStr != null ? Integer.parseInt(secondsStr) : 0;
      int nanos = fracStr != null ? parseFractionalSeconds(fracStr) : 0;

      boolean negative = "-".equals(sign);
      return new OracleIntervalDS(
          negative ? -days : days,
          negative ? -hours : hours,
          negative ? -minutes : minutes,
          negative ? -seconds : seconds,
          negative ? -nanos : nanos);
    }

    throw new IllegalArgumentException(
        "Cannot parse interval: "
            + s
            + " (expected format: +03 14:30:45.123456 or P3DT14H30M45.123456S)");
  }

  private static int parseFractionalSeconds(String fracStr) {
    // Pad or truncate to 9 digits (nanoseconds)
    if (fracStr.length() < 9) {
      fracStr = String.format("%-9s", fracStr).replace(' ', '0');
    } else if (fracStr.length() > 9) {
      fracStr = fracStr.substring(0, 9);
    }
    return Integer.parseInt(fracStr);
  }

  /** Convert to Oracle's native format: +03 14:30:45.123456 */
  public String toOracleFormat() {
    boolean negative = days < 0 || hours < 0 || minutes < 0 || seconds < 0 || nanos < 0;
    String sign = negative ? "-" : "+";
    String fracStr = String.format("%09d", Math.abs(nanos)).substring(0, 6); // Oracle uses 6 digits

    return String.format(
        "%s%02d %02d:%02d:%02d.%s",
        sign, Math.abs(days), Math.abs(hours), Math.abs(minutes), Math.abs(seconds), fracStr);
  }

  /** Convert to ISO-8601 duration format: P3DT14H30M45.123456S */
  public String toIso8601() {
    if (days == 0 && hours == 0 && minutes == 0 && seconds == 0 && nanos == 0) {
      return "P0D";
    }

    boolean negative = days < 0 || hours < 0 || minutes < 0 || seconds < 0 || nanos < 0;
    StringBuilder sb = new StringBuilder();
    if (negative) {
      sb.append("-");
    }
    sb.append("P");

    if (days != 0) {
      sb.append(Math.abs(days)).append("D");
    }

    if (hours != 0 || minutes != 0 || seconds != 0 || nanos != 0) {
      sb.append("T");
      if (hours != 0) {
        sb.append(Math.abs(hours)).append("H");
      }
      if (minutes != 0) {
        sb.append(Math.abs(minutes)).append("M");
      }
      if (seconds != 0 || nanos != 0) {
        sb.append(Math.abs(seconds));
        if (nanos != 0) {
          String fracStr = String.format("%09d", Math.abs(nanos)).replaceAll("0+$", "");
          sb.append(".").append(fracStr);
        }
        sb.append("S");
      }
    }

    return sb.toString();
  }

  /** Convert to java.time.Duration */
  public Duration toDuration() {
    long totalSeconds =
        ((long) days * 24 * 60 * 60) + ((long) hours * 60 * 60) + ((long) minutes * 60) + seconds;
    return Duration.ofSeconds(totalSeconds, nanos);
  }

  /** Create from java.time.Duration */
  public static OracleIntervalDS fromDuration(Duration duration) {
    long totalSeconds = duration.getSeconds();
    int days = (int) (totalSeconds / (24 * 60 * 60));
    totalSeconds -= (long) days * 24 * 60 * 60;
    int hours = (int) (totalSeconds / (60 * 60));
    totalSeconds -= (long) hours * 60 * 60;
    int minutes = (int) (totalSeconds / 60);
    int seconds = (int) (totalSeconds % 60);
    int nanos = duration.getNano();

    return new OracleIntervalDS(days, hours, minutes, seconds, nanos);
  }

  @Override
  public String toString() {
    return toOracleFormat();
  }
}

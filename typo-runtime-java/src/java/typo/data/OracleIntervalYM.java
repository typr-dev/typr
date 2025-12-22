package typo.data;

import java.time.Period;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents Oracle's INTERVAL YEAR TO MONTH type.
 *
 * <p>Can parse both Oracle's native format (+02-05, -01-06) and ISO-8601 duration format (P2Y5M,
 * -P1Y6M). This handles the fact that Oracle returns ISO-8601 format in JSON but native format in
 * columns.
 */
public record OracleIntervalYM(int years, int months) {

  // Oracle format: +02-05, -01-06, +00-00
  private static final Pattern ORACLE_FORMAT = Pattern.compile("([+-]?)(\\d+)-(\\d+)");

  // ISO-8601 format: P2Y5M, -P1Y6M, P0Y
  private static final Pattern ISO_FORMAT = Pattern.compile("(-?)P(\\d+)Y(\\d+)?M?");

  public OracleIntervalYM {
    // Both years and months must have the same sign
    if ((years < 0 && months > 0) || (years > 0 && months < 0)) {
      throw new IllegalArgumentException(
          "Years and months must have the same sign, got years=" + years + ", months=" + months);
    }
    // Months must be in range 0-11 (absolute value)
    if (Math.abs(months) > 11) {
      throw new IllegalArgumentException(
          "Months must be between 0 and 11, got: " + Math.abs(months));
    }
  }

  /** Parse from either Oracle format (+02-05) or ISO-8601 format (P2Y5M) */
  public static OracleIntervalYM parse(String s) {
    if (s == null || s.isEmpty()) {
      throw new IllegalArgumentException("Cannot parse null or empty interval");
    }

    // Try Oracle format first
    Matcher oracleMatcher = ORACLE_FORMAT.matcher(s);
    if (oracleMatcher.matches()) {
      String sign = oracleMatcher.group(1);
      int years = Integer.parseInt(oracleMatcher.group(2));
      int months = Integer.parseInt(oracleMatcher.group(3));

      if ("-".equals(sign)) {
        years = -years;
        months = -months;
      }

      return new OracleIntervalYM(years, months);
    }

    // Try ISO-8601 format
    Matcher isoMatcher = ISO_FORMAT.matcher(s);
    if (isoMatcher.matches()) {
      String sign = isoMatcher.group(1);
      int years = Integer.parseInt(isoMatcher.group(2));
      String monthsStr = isoMatcher.group(3);
      int months = monthsStr != null ? Integer.parseInt(monthsStr) : 0;

      if ("-".equals(sign)) {
        years = -years;
        months = -months;
      }

      return new OracleIntervalYM(years, months);
    }

    throw new IllegalArgumentException(
        "Cannot parse interval: " + s + " (expected format: +02-05 or P2Y5M)");
  }

  /** Convert to Oracle's native format: +02-05 */
  public String toOracleFormat() {
    if (years < 0 || months < 0) {
      return String.format("-%02d-%02d", Math.abs(years), Math.abs(months));
    } else {
      return String.format("+%02d-%02d", years, months);
    }
  }

  /** Convert to ISO-8601 duration format: P2Y5M */
  public String toIso8601() {
    if (years == 0 && months == 0) {
      return "P0Y";
    }

    StringBuilder sb = new StringBuilder();
    if (years < 0 || months < 0) {
      sb.append("-");
    }
    sb.append("P");
    sb.append(Math.abs(years)).append("Y");
    if (months != 0) {
      sb.append(Math.abs(months)).append("M");
    }
    return sb.toString();
  }

  /** Convert to java.time.Period */
  public Period toPeriod() {
    return Period.of(years, months, 0);
  }

  /** Create from java.time.Period (ignoring days) */
  public static OracleIntervalYM fromPeriod(Period period) {
    return new OracleIntervalYM(period.getYears(), period.getMonths());
  }

  @Override
  public String toString() {
    return toOracleFormat();
  }
}

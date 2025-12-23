package typr.data;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.function.BiFunction;
import typr.runtime.SqlFunction;

/**
 * Parser for PostgreSQL range string format.
 *
 * <p>PostgreSQL ranges are represented as strings like:
 *
 * <ul>
 *   <li>{@code [1,10]} - closed on both ends
 *   <li>{@code (1,10)} - open on both ends
 *   <li>{@code [1,10)} - closed-open (canonical for integers)
 *   <li>{@code (1,10]} - open-closed
 *   <li>{@code [,10)} or {@code (,10)} - lower unbounded (infinite)
 *   <li>{@code [1,)} or {@code [1,]} - upper unbounded (infinite)
 *   <li>{@code empty} - empty range
 * </ul>
 */
public final class RangeParser {

  private RangeParser() {}

  // Date/time formatters for PostgreSQL range format (space-separated, not ISO 'T')
  private static final DateTimeFormatter TIMESTAMP_FORMATTER =
      new DateTimeFormatterBuilder()
          .appendPattern("yyyy-MM-dd HH:mm:ss")
          .appendFraction(ChronoField.MICRO_OF_SECOND, 0, 6, true)
          .toFormatter();

  private static final DateTimeFormatter TIMESTAMPTZ_FORMATTER =
      new DateTimeFormatterBuilder()
          .appendPattern("yyyy-MM-dd HH:mm:ss")
          .appendFraction(ChronoField.MICRO_OF_SECOND, 0, 6, true)
          .appendOffset("+HH:mm", "+00:00")
          .toFormatter();

  private static final DateTimeFormatter TIMESTAMPTZ_SHORT_OFFSET_FORMATTER =
      new DateTimeFormatterBuilder()
          .appendPattern("yyyy-MM-dd HH:mm:ss")
          .appendFraction(ChronoField.MICRO_OF_SECOND, 0, 6, true)
          .appendOffset("+HH", "+00")
          .toFormatter();

  // Pre-built value parsers for common range types
  public static final SqlFunction<String, Integer> INT4_PARSER = Integer::parseInt;
  public static final SqlFunction<String, Long> INT8_PARSER = Long::parseLong;
  public static final SqlFunction<String, BigDecimal> NUMERIC_PARSER = BigDecimal::new;
  public static final SqlFunction<String, LocalDate> DATE_PARSER = LocalDate::parse;
  public static final SqlFunction<String, LocalDateTime> TIMESTAMP_PARSER =
      RangeParser::parseLocalDateTime;
  public static final SqlFunction<String, Instant> TIMESTAMPTZ_PARSER = RangeParser::parseInstant;

  /**
   * Parse a PostgreSQL range string into a Range object.
   *
   * @param input the range string from PostgreSQL
   * @param valueParser parser for the element type
   * @param rangeFactory factory to create the Range (use Range.INT4, Range.DATE, etc.)
   */
  public static <T extends Comparable<? super T>> Range<T> parse(
      String input,
      SqlFunction<String, T> valueParser,
      BiFunction<RangeBound<T>, RangeBound<T>, Range<T>> rangeFactory)
      throws java.sql.SQLException {
    if (input == null || input.isEmpty()) {
      throw new java.sql.SQLException("Cannot parse null or empty range string");
    }

    String trimmed = input.trim();

    // Handle empty range
    if (trimmed.equals("empty")) {
      return Range.empty();
    }

    if (trimmed.length() < 3) {
      throw new java.sql.SQLException("Invalid range format: " + input);
    }

    char leftBracket = trimmed.charAt(0);
    char rightBracket = trimmed.charAt(trimmed.length() - 1);

    boolean leftClosed = leftBracket == '[';
    boolean rightClosed = rightBracket == ']';

    if ((leftBracket != '[' && leftBracket != '(')
        || (rightBracket != ']' && rightBracket != ')')) {
      throw new java.sql.SQLException("Invalid range brackets: " + input);
    }

    // Extract the content between brackets
    String content = trimmed.substring(1, trimmed.length() - 1);

    // Find the comma separator - need to handle quoted values
    int commaIndex = findComma(content);
    if (commaIndex == -1) {
      throw new java.sql.SQLException("Invalid range format, no comma found: " + input);
    }

    String leftStr = content.substring(0, commaIndex).trim();
    String rightStr = content.substring(commaIndex + 1).trim();

    RangeBound<T> leftBound;
    RangeBound<T> rightBound;

    // Parse left bound
    if (leftStr.isEmpty()) {
      leftBound = RangeBound.infinite();
    } else {
      T leftValue = valueParser.apply(unquote(leftStr));
      leftBound =
          leftClosed ? new RangeBound.Closed<>(leftValue) : new RangeBound.Open<>(leftValue);
    }

    // Parse right bound
    if (rightStr.isEmpty()) {
      rightBound = RangeBound.infinite();
    } else {
      T rightValue = valueParser.apply(unquote(rightStr));
      rightBound =
          rightClosed ? new RangeBound.Closed<>(rightValue) : new RangeBound.Open<>(rightValue);
    }

    return rangeFactory.apply(leftBound, rightBound);
  }

  /** Format a range to PostgreSQL string format. */
  public static <T extends Comparable<? super T>> String format(Range<T> range) {
    return switch (range) {
      case Range.Empty<T> e -> "empty";
      case Range.NonEmpty<T> r -> formatNonEmpty(r);
    };
  }

  /**
   * Parse a timestamp string from PostgreSQL range format. Handles both ISO format (with 'T') and
   * PostgreSQL format (with space).
   */
  public static LocalDateTime parseLocalDateTime(String value) throws java.sql.SQLException {
    try {
      if (value.contains("T")) {
        return LocalDateTime.parse(value);
      } else {
        return LocalDateTime.parse(value, TIMESTAMP_FORMATTER);
      }
    } catch (java.time.format.DateTimeParseException e) {
      throw new java.sql.SQLException("Failed to parse timestamp: " + value, e);
    }
  }

  /** Parse a timestamptz string from PostgreSQL range format. Handles various offset formats. */
  public static Instant parseInstant(String value) throws java.sql.SQLException {
    try {
      if (value.endsWith("Z")) {
        return Instant.parse(value);
      }
      // Try ISO format first
      try {
        return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
      } catch (java.time.format.DateTimeParseException e) {
        // Try space-separated format with full offset
        try {
          return OffsetDateTime.parse(value, TIMESTAMPTZ_FORMATTER).toInstant();
        } catch (java.time.format.DateTimeParseException e2) {
          // Try short offset format (+00)
          return OffsetDateTime.parse(value, TIMESTAMPTZ_SHORT_OFFSET_FORMATTER).toInstant();
        }
      }
    } catch (java.time.format.DateTimeParseException e) {
      throw new java.sql.SQLException("Failed to parse timestamptz: " + value, e);
    }
  }

  private static <T extends Comparable<? super T>> String formatNonEmpty(Range.NonEmpty<T> range) {
    StringBuilder sb = new StringBuilder();

    // Left bracket
    switch (range.from()) {
      case RangeBound.Infinite<T> i -> sb.append('(');
      case RangeBound.Finite.Closed<T> c -> sb.append('[');
      case RangeBound.Finite.Open<T> o -> sb.append('(');
    }

    // Left value
    switch (range.from()) {
      case RangeBound.Infinite<T> i -> {}
      case RangeBound.Finite<T> f -> sb.append(quote(f.value().toString()));
    }

    sb.append(',');

    // Right value
    switch (range.to()) {
      case RangeBound.Infinite<T> i -> {}
      case RangeBound.Finite<T> f -> sb.append(quote(f.value().toString()));
    }

    // Right bracket
    switch (range.to()) {
      case RangeBound.Infinite<T> i -> sb.append(')');
      case RangeBound.Finite.Closed<T> c -> sb.append(']');
      case RangeBound.Finite.Open<T> o -> sb.append(')');
    }

    return sb.toString();
  }

  /**
   * Find the comma separator, handling quoted values. PostgreSQL quotes values containing special
   * characters like commas.
   */
  private static int findComma(String content) {
    boolean inQuote = false;
    for (int i = 0; i < content.length(); i++) {
      char c = content.charAt(i);
      if (c == '"') {
        inQuote = !inQuote;
      } else if (c == ',' && !inQuote) {
        return i;
      }
    }
    return -1;
  }

  /** Remove quotes from a value if present and unescape. */
  private static String unquote(String value) {
    if (value.startsWith("\"") && value.endsWith("\"")) {
      String inner = value.substring(1, value.length() - 1);
      // Unescape doubled quotes
      return inner.replace("\"\"", "\"");
    }
    return value;
  }

  /** Quote a value if it contains special characters. */
  private static String quote(String value) {
    if (value.contains(",")
        || value.contains("\"")
        || value.contains("(")
        || value.contains(")")
        || value.contains("[")
        || value.contains("]")
        || value.contains(" ")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }
}

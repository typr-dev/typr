package dev.typr.foundations;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser for PostgreSQL composite type (record) text format.
 *
 * <p>PostgreSQL represents composite values in text format as: (val1, val2, ...)
 *
 * <p>Key parsing rules:
 *
 * <ul>
 *   <li>Values are separated by commas within parentheses
 *   <li>NULL is represented by empty (no characters between commas)
 *   <li>Empty string is represented by "" (quoted empty)
 *   <li>Values containing special chars (comma, quotes, parens, backslash) must be quoted
 *   <li>Within quoted values, quotes are escaped by doubling: " becomes ""
 *   <li>Within quoted values, backslash is escaped by doubling: \ becomes \\
 *   <li>Nested records are quoted, with inner quotes doubled at each level
 * </ul>
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>(hello,world,123) - simple values
 *   <li>("hello, world",test) - value with comma needs quotes
 *   <li>("say ""hello""",test) - value with quotes (doubled)
 *   <li>(,,"") - NULL, NULL, empty string
 *   <li>("(nested,record)",outer) - nested record (quoted)
 *   <li>("(""deeply"",nested)",outer) - deeply nested with quote doubling
 * </ul>
 */
public final class PgRecordParser {
  private PgRecordParser() {}

  /**
   * Parse a PostgreSQL composite type text representation into a list of field values.
   *
   * @param input the composite value string, e.g., "(val1, val2, ...)"
   * @return list of parsed field values, where null represents SQL NULL
   * @throws IllegalArgumentException if the input is malformed
   */
  public static List<String> parse(String input) {
    if (input == null) {
      throw new IllegalArgumentException("Input cannot be null");
    }

    String trimmed = input.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("Input cannot be empty");
    }

    if (!trimmed.startsWith("(") || !trimmed.endsWith(")")) {
      throw new IllegalArgumentException(
          "Composite value must be enclosed in parentheses: " + input);
    }

    // Remove outer parentheses
    String content = trimmed.substring(1, trimmed.length() - 1);

    return parseFields(content);
  }

  /**
   * Parse the content inside parentheses into individual field values.
   *
   * @param content the content between parentheses
   * @return list of field values
   */
  private static List<String> parseFields(String content) {
    List<String> fields = new ArrayList<>();

    if (content.isEmpty()) {
      // Empty record () has no fields
      return fields;
    }

    int pos = 0;
    int len = content.length();

    while (pos <= len) {
      // Parse one field
      FieldParseResult result = parseField(content, pos);
      fields.add(result.value);
      pos = result.nextPos;

      if (pos < len) {
        // Expect comma separator
        if (content.charAt(pos) != ',') {
          throw new IllegalArgumentException(
              "Expected comma at position " + pos + " in: " + content);
        }
        pos++; // Skip comma
      } else if (pos == len) {
        // We've consumed exactly all content
        break;
      }
    }

    return fields;
  }

  private record FieldParseResult(String value, int nextPos) {}

  /**
   * Parse a single field value starting at the given position.
   *
   * @param content the full content string
   * @param start the starting position
   * @return the parsed value and the position after it
   */
  private static FieldParseResult parseField(String content, int start) {
    int len = content.length();

    if (start >= len) {
      // Empty field at end means NULL
      return new FieldParseResult(null, start);
    }

    char firstChar = content.charAt(start);

    if (firstChar == ',') {
      // Empty field (NULL)
      return new FieldParseResult(null, start);
    }

    if (firstChar == '"') {
      // Quoted field
      return parseQuotedField(content, start);
    }

    // Unquoted field - read until comma or end
    return parseUnquotedField(content, start);
  }

  /**
   * Parse a quoted field value. Handles escape sequences and nested quotes.
   *
   * @param content the full content string
   * @param start the position of the opening quote
   * @return the parsed value and position after closing quote
   */
  private static FieldParseResult parseQuotedField(String content, int start) {
    int len = content.length();
    StringBuilder sb = new StringBuilder();

    // Skip opening quote
    int pos = start + 1;

    while (pos < len) {
      char c = content.charAt(pos);

      if (c == '"') {
        // Check for escaped quote (doubled)
        if (pos + 1 < len && content.charAt(pos + 1) == '"') {
          // Escaped quote - emit single quote and skip both
          sb.append('"');
          pos += 2;
        } else {
          // End of quoted field
          return new FieldParseResult(sb.toString(), pos + 1);
        }
      } else if (c == '\\') {
        // Backslash escaping
        if (pos + 1 < len) {
          char next = content.charAt(pos + 1);
          if (next == '\\') {
            // Escaped backslash
            sb.append('\\');
            pos += 2;
          } else {
            // Single backslash followed by something else - just emit as-is
            // PostgreSQL doesn't actually require backslash escaping in most cases
            sb.append(c);
            pos++;
          }
        } else {
          // Backslash at end - emit as-is
          sb.append(c);
          pos++;
        }
      } else {
        sb.append(c);
        pos++;
      }
    }

    throw new IllegalArgumentException("Unterminated quoted field starting at position " + start);
  }

  /**
   * Parse an unquoted field value.
   *
   * @param content the full content string
   * @param start the starting position
   * @return the parsed value and position after it
   */
  private static FieldParseResult parseUnquotedField(String content, int start) {
    int len = content.length();
    int pos = start;

    while (pos < len) {
      char c = content.charAt(pos);
      if (c == ',') {
        break;
      }
      pos++;
    }

    String value = content.substring(start, pos);

    // Unquoted empty string is NULL
    if (value.isEmpty()) {
      return new FieldParseResult(null, pos);
    }

    return new FieldParseResult(value, pos);
  }

  /**
   * Encode a list of field values into PostgreSQL composite type text format.
   *
   * @param values the field values (null represents SQL NULL)
   * @return the encoded string, e.g., "(val1, val2, ...)"
   */
  public static String encode(List<String> values) {
    StringBuilder sb = new StringBuilder();
    sb.append('(');

    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      encodeField(sb, values.get(i));
    }

    sb.append(')');
    return sb.toString();
  }

  /**
   * Encode a single field value.
   *
   * @param sb the string builder to append to
   * @param value the field value (null for SQL NULL)
   */
  private static void encodeField(StringBuilder sb, String value) {
    if (value == null) {
      // NULL - empty field
      return;
    }

    if (value.isEmpty()) {
      // Empty string needs quotes to distinguish from NULL
      sb.append("\"\"");
      return;
    }

    // Check if quoting is needed
    boolean needsQuotes = false;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == ',' || c == '"' || c == '(' || c == ')' || c == '\\' || c == '\n' || c == '\r') {
        needsQuotes = true;
        break;
      }
    }

    if (!needsQuotes) {
      sb.append(value);
      return;
    }

    // Quoted encoding
    sb.append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '"') {
        sb.append("\"\""); // Escape quote by doubling
      } else if (c == '\\') {
        sb.append("\\\\"); // Escape backslash by doubling
      } else {
        sb.append(c);
      }
    }
    sb.append('"');
  }

  /**
   * Parse a nested record value. This is useful when a field value is itself a composite.
   *
   * <p>When a composite is nested inside another composite, it appears as a quoted string where the
   * inner composite's special characters are already escaped. This method can be called on the
   * unescaped field value to parse it recursively.
   *
   * @param value the field value containing a nested record
   * @return list of parsed field values from the nested record
   */
  public static List<String> parseNested(String value) {
    return parse(value);
  }

  /**
   * Parse a PostgreSQL array text representation into a list of element values.
   *
   * <p>Array format: {elem1,elem2,...} or {"quoted elem","another"}
   *
   * @param input the array value string, e.g., "{val1, val2, ...}"
   * @return list of parsed element values, where null represents SQL NULL
   * @throws IllegalArgumentException if the input is malformed
   */
  public static List<String> parseArray(String input) {
    return parseArray(input, ',');
  }

  /**
   * Parse a PostgreSQL array text representation with a custom delimiter.
   *
   * <p>PostgreSQL uses semicolon (;) as delimiter for geometric type arrays since their elements
   * contain commas.
   *
   * @param input the array value string
   * @param delimiter the element delimiter character (typically ',' or ';')
   * @return list of parsed element values, where null represents SQL NULL
   */
  public static List<String> parseArray(String input, char delimiter) {
    if (input == null) {
      throw new IllegalArgumentException("Array input cannot be null");
    }

    String trimmed = input.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("Array input cannot be empty");
    }

    if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
      throw new IllegalArgumentException("Array value must be enclosed in braces: " + input);
    }

    // Remove outer braces
    String content = trimmed.substring(1, trimmed.length() - 1);

    return parseArrayElements(content, delimiter);
  }

  /**
   * Parse the content inside braces into individual array elements.
   *
   * @param content the content between braces
   * @param delimiter the element delimiter character
   * @return list of element values
   */
  private static List<String> parseArrayElements(String content, char delimiter) {
    List<String> elements = new ArrayList<>();

    if (content.isEmpty()) {
      // Empty array {} has no elements
      return elements;
    }

    int pos = 0;
    int len = content.length();

    while (pos <= len) {
      // Parse one element
      FieldParseResult result = parseArrayElement(content, pos, delimiter);
      elements.add(result.value);
      pos = result.nextPos;

      if (pos < len) {
        // Expect delimiter separator
        if (content.charAt(pos) != delimiter) {
          throw new IllegalArgumentException(
              "Expected delimiter '"
                  + delimiter
                  + "' at position "
                  + pos
                  + " in array: "
                  + content);
        }
        pos++; // Skip delimiter
      } else if (pos == len) {
        // We've consumed exactly all content
        break;
      }
    }

    return elements;
  }

  /**
   * Parse a single array element starting at the given position.
   *
   * @param content the full content string
   * @param start the starting position
   * @param delimiter the element delimiter character
   * @return the parsed value and the position after it
   */
  private static FieldParseResult parseArrayElement(String content, int start, char delimiter) {
    int len = content.length();

    if (start >= len) {
      // Empty element at end
      return new FieldParseResult(null, start);
    }

    char firstChar = content.charAt(start);

    if (firstChar == delimiter) {
      // Empty element (NULL)
      return new FieldParseResult(null, start);
    }

    if (firstChar == '"') {
      // Quoted element
      return parseQuotedArrayElement(content, start);
    }

    // Unquoted element - read until delimiter or end
    return parseUnquotedArrayElement(content, start, delimiter);
  }

  /**
   * Parse a quoted array element. Array quoting uses backslash escaping for quotes and backslashes.
   *
   * @param content the full content string
   * @param start the position of the opening quote
   * @return the parsed value and position after closing quote
   */
  private static FieldParseResult parseQuotedArrayElement(String content, int start) {
    int len = content.length();
    StringBuilder sb = new StringBuilder();

    // Skip opening quote
    int pos = start + 1;

    while (pos < len) {
      char c = content.charAt(pos);

      if (c == '"') {
        // End of quoted element
        return new FieldParseResult(sb.toString(), pos + 1);
      } else if (c == '\\') {
        // Backslash escaping in arrays
        if (pos + 1 < len) {
          char next = content.charAt(pos + 1);
          // In array context, backslash escapes the next character
          sb.append(next);
          pos += 2;
        } else {
          // Backslash at end - emit as-is
          sb.append(c);
          pos++;
        }
      } else {
        sb.append(c);
        pos++;
      }
    }

    throw new IllegalArgumentException(
        "Unterminated quoted array element starting at position " + start);
  }

  /**
   * Parse an unquoted array element.
   *
   * @param content the full content string
   * @param start the starting position
   * @param delimiter the element delimiter character
   * @return the parsed value and position after it
   */
  private static FieldParseResult parseUnquotedArrayElement(
      String content, int start, char delimiter) {
    int len = content.length();
    int pos = start;

    while (pos < len) {
      char c = content.charAt(pos);
      if (c == delimiter) {
        break;
      }
      pos++;
    }

    String value = content.substring(start, pos);

    // Check for NULL literal
    if (value.equalsIgnoreCase("NULL")) {
      return new FieldParseResult(null, pos);
    }

    // Unquoted empty string shouldn't happen in arrays
    if (value.isEmpty()) {
      return new FieldParseResult(null, pos);
    }

    return new FieldParseResult(value, pos);
  }

  /**
   * Encode a list of element values into PostgreSQL array text format.
   *
   * @param values the element values (null represents SQL NULL)
   * @param elementEncoder function to encode each element to its text representation
   * @return the encoded string, e.g., "{val1, val2, ...}"
   */
  public static <T> String encodeArray(
      List<T> values, java.util.function.Function<T, String> elementEncoder) {
    return encodeArray(values, elementEncoder, ',');
  }

  /**
   * Encode a list of element values into PostgreSQL array text format with a custom delimiter.
   *
   * <p>PostgreSQL uses semicolon (;) as delimiter for geometric type arrays since their elements
   * contain commas.
   *
   * @param values the element values (null represents SQL NULL)
   * @param elementEncoder function to encode each element to its text representation
   * @param delimiter the element delimiter character (typically ',' or ';')
   * @return the encoded string
   */
  public static <T> String encodeArray(
      List<T> values, java.util.function.Function<T, String> elementEncoder, char delimiter) {
    StringBuilder sb = new StringBuilder();
    sb.append('{');

    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        sb.append(delimiter);
      }
      T value = values.get(i);
      if (value == null) {
        sb.append("NULL");
      } else {
        String encoded = elementEncoder.apply(value);
        encodeArrayElement(sb, encoded, delimiter);
      }
    }

    sb.append('}');
    return sb.toString();
  }

  /**
   * Encode a single array element.
   *
   * @param sb the string builder to append to
   * @param value the element value (already encoded to string)
   * @param delimiter the element delimiter character
   */
  private static void encodeArrayElement(StringBuilder sb, String value, char delimiter) {
    if (value == null) {
      sb.append("NULL");
      return;
    }

    // Check if quoting is needed
    boolean needsQuotes = value.isEmpty();
    if (!needsQuotes) {
      for (int i = 0; i < value.length(); i++) {
        char c = value.charAt(i);
        if (c == delimiter
            || c == '"'
            || c == '{'
            || c == '}'
            || c == '\\'
            || c == '\n'
            || c == '\r'
            || c == '('
            || c == ')'
            || Character.isWhitespace(c)) {
          needsQuotes = true;
          break;
        }
      }
    }

    if (!needsQuotes) {
      sb.append(value);
      return;
    }

    // Quoted encoding with backslash escaping
    sb.append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '"' || c == '\\') {
        sb.append('\\'); // Escape with backslash
      }
      sb.append(c);
    }
    sb.append('"');
  }
}

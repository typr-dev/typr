package typr.data;

import java.util.*;

/**
 * Simple JSON parser for parsing JSON strings into JsonValue ADT. Handles standard JSON format as
 * produced by PostgreSQL and MariaDB.
 */
final class JsonParser {
  private final String json;
  private int pos;

  private JsonParser(String json) {
    this.json = json;
    this.pos = 0;
  }

  static JsonValue parse(String json) {
    if (json == null || json.isEmpty()) {
      throw new IllegalArgumentException("Empty JSON string");
    }
    JsonParser parser = new JsonParser(json);
    JsonValue result = parser.parseValue();
    parser.skipWhitespace();
    if (parser.pos < parser.json.length()) {
      throw new IllegalArgumentException(
          "Unexpected content after JSON value at position " + parser.pos);
    }
    return result;
  }

  private JsonValue parseValue() {
    skipWhitespace();
    if (pos >= json.length()) {
      throw new IllegalArgumentException("Unexpected end of JSON");
    }
    char c = json.charAt(pos);
    return switch (c) {
      case 'n' -> parseNull();
      case 't', 'f' -> parseBool();
      case '"' -> parseString();
      case '[' -> parseArray();
      case '{' -> parseObject();
      case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> parseNumber();
      default ->
          throw new IllegalArgumentException("Unexpected character '" + c + "' at position " + pos);
    };
  }

  private JsonValue.JNull parseNull() {
    expect("null");
    return JsonValue.JNull.INSTANCE;
  }

  private JsonValue.JBool parseBool() {
    if (json.charAt(pos) == 't') {
      expect("true");
      return JsonValue.JBool.TRUE;
    } else {
      expect("false");
      return JsonValue.JBool.FALSE;
    }
  }

  private JsonValue.JString parseString() {
    expect("\"");
    StringBuilder sb = new StringBuilder();
    while (pos < json.length()) {
      char c = json.charAt(pos);
      if (c == '"') {
        pos++;
        return new JsonValue.JString(sb.toString());
      } else if (c == '\\') {
        pos++;
        if (pos >= json.length()) {
          throw new IllegalArgumentException("Unexpected end of string escape");
        }
        char escaped = json.charAt(pos);
        switch (escaped) {
          case '"' -> sb.append('"');
          case '\\' -> sb.append('\\');
          case '/' -> sb.append('/');
          case 'b' -> sb.append('\b');
          case 'f' -> sb.append('\f');
          case 'n' -> sb.append('\n');
          case 'r' -> sb.append('\r');
          case 't' -> sb.append('\t');
          case 'u' -> {
            if (pos + 4 >= json.length()) {
              throw new IllegalArgumentException("Incomplete unicode escape");
            }
            String hex = json.substring(pos + 1, pos + 5);
            sb.append((char) Integer.parseInt(hex, 16));
            pos += 4;
          }
          default -> throw new IllegalArgumentException("Invalid escape character: " + escaped);
        }
        pos++;
      } else {
        sb.append(c);
        pos++;
      }
    }
    throw new IllegalArgumentException("Unterminated string");
  }

  private JsonValue.JNumber parseNumber() {
    int start = pos;
    if (json.charAt(pos) == '-') {
      pos++;
    }
    // Integer part
    if (pos < json.length() && json.charAt(pos) == '0') {
      pos++;
    } else {
      while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
        pos++;
      }
    }
    // Fractional part
    if (pos < json.length() && json.charAt(pos) == '.') {
      pos++;
      while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
        pos++;
      }
    }
    // Exponent
    if (pos < json.length() && (json.charAt(pos) == 'e' || json.charAt(pos) == 'E')) {
      pos++;
      if (pos < json.length() && (json.charAt(pos) == '+' || json.charAt(pos) == '-')) {
        pos++;
      }
      while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
        pos++;
      }
    }
    String numStr = json.substring(start, pos);
    return new JsonValue.JNumber(numStr);
  }

  private JsonValue.JArray parseArray() {
    expect("[");
    skipWhitespace();
    if (pos < json.length() && json.charAt(pos) == ']') {
      pos++;
      return new JsonValue.JArray(List.of());
    }
    List<JsonValue> values = new ArrayList<>();
    while (true) {
      values.add(parseValue());
      skipWhitespace();
      if (pos >= json.length()) {
        throw new IllegalArgumentException("Unterminated array");
      }
      char c = json.charAt(pos);
      if (c == ']') {
        pos++;
        return new JsonValue.JArray(values);
      } else if (c == ',') {
        pos++;
        skipWhitespace();
      } else {
        throw new IllegalArgumentException("Expected ',' or ']' in array at position " + pos);
      }
    }
  }

  private JsonValue.JObject parseObject() {
    expect("{");
    skipWhitespace();
    if (pos < json.length() && json.charAt(pos) == '}') {
      pos++;
      return new JsonValue.JObject(Map.of());
    }
    Map<String, JsonValue> fields = new LinkedHashMap<>();
    while (true) {
      skipWhitespace();
      if (pos >= json.length() || json.charAt(pos) != '"') {
        throw new IllegalArgumentException("Expected string key in object at position " + pos);
      }
      String key = parseString().value();
      skipWhitespace();
      if (pos >= json.length() || json.charAt(pos) != ':') {
        throw new IllegalArgumentException("Expected ':' after object key at position " + pos);
      }
      pos++;
      JsonValue value = parseValue();
      fields.put(key, value);
      skipWhitespace();
      if (pos >= json.length()) {
        throw new IllegalArgumentException("Unterminated object");
      }
      char c = json.charAt(pos);
      if (c == '}') {
        pos++;
        return new JsonValue.JObject(fields);
      } else if (c == ',') {
        pos++;
      } else {
        throw new IllegalArgumentException("Expected ',' or '}' in object at position " + pos);
      }
    }
  }

  private void skipWhitespace() {
    while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
      pos++;
    }
  }

  private void expect(String s) {
    if (!json.regionMatches(pos, s, 0, s.length())) {
      throw new IllegalArgumentException("Expected '" + s + "' at position " + pos);
    }
    pos += s.length();
  }
}

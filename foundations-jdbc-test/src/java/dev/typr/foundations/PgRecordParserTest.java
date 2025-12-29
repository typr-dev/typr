package dev.typr.foundations;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * Tests for PostgreSQL composite type (record) text format parser.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>Simple values (unquoted)
 *   <li>Quoted values with special characters
 *   <li>NULL vs empty string handling
 *   <li>Nested composite types
 *   <li>Deeply nested types (multiple levels of quote escaping)
 *   <li>Edge cases: newlines, backslashes, commas in values
 *   <li>Roundtrip encoding/decoding
 * </ul>
 */
public class PgRecordParserTest {

  @Test
  public void testSimpleValues() {
    // Simple unquoted values
    assertParse("(hello,world,123)", List.of("hello", "world", "123"));
    assertParse("(a,b,c)", List.of("a", "b", "c"));
    assertParse("(1,2,3)", List.of("1", "2", "3"));
  }

  @Test
  public void testSingleValue() {
    assertParse("(hello)", List.of("hello"));
    assertParse("(123)", List.of("123"));
  }

  @Test
  public void testEmptyRecord() {
    assertParse("()", List.of());
  }

  @Test
  public void testNullValues() {
    // NULL is represented by empty (no characters)
    assertParse("(,)", Arrays.asList(null, null));
    assertParse("(a,,c)", Arrays.asList("a", null, "c"));
    assertParse("(,,)", Arrays.asList(null, null, null));
    assertParse("(a,)", Arrays.asList("a", null));
    assertParse("(,b)", Arrays.asList(null, "b"));
  }

  @Test
  public void testEmptyStringVsNull() {
    // Empty string is "" (quoted empty), NULL is nothing
    assertParse("(\"\")", List.of(""));
    assertParse("(\"\",)", Arrays.asList("", null));
    assertParse("(,\"\")", Arrays.asList(null, ""));
    assertParse("(\"\",\"\",\"\")", List.of("", "", ""));
  }

  @Test
  public void testQuotedValuesWithCommas() {
    // Values with commas need quotes
    assertParse("(\"hello, world\",test)", List.of("hello, world", "test"));
    assertParse("(a,\"b,c,d\",e)", List.of("a", "b,c,d", "e"));
  }

  @Test
  public void testQuotedValuesWithQuotes() {
    // Quotes within quoted values are doubled: " becomes ""
    assertParse("(\"say \"\"hello\"\"\")", List.of("say \"hello\""));
    assertParse("(\"\"\"quoted\"\"\",plain)", List.of("\"quoted\"", "plain"));
    assertParse("(a,\"b\"\"c\",d)", List.of("a", "b\"c", "d"));
  }

  @Test
  public void testQuotedValuesWithParentheses() {
    // Parentheses within quoted values
    assertParse("(\"(nested)\")", List.of("(nested)"));
    assertParse("(\"(a,b)\",c)", List.of("(a,b)", "c"));
    assertParse("(\"((deep))\")", List.of("((deep))"));
  }

  @Test
  public void testQuotedValuesWithBackslashes() {
    // Backslashes - PostgreSQL doubles them in quotes
    assertParse("(\"path\\\\to\\\\file\")", List.of("path\\to\\file"));
    assertParse("(\"a\\\\b\",c)", List.of("a\\b", "c"));
  }

  @Test
  public void testQuotedValuesWithNewlines() {
    // Newlines within quoted values
    assertParse("(\"line1\nline2\")", List.of("line1\nline2"));
    assertParse("(\"a\nb\nc\",d)", List.of("a\nb\nc", "d"));
  }

  @Test
  public void testNestedComposites() {
    // Nested composite appears as quoted string
    // Inner: (a,b) -> quoted: "(a,b)"
    assertParse("(\"(a,b)\",outer)", List.of("(a,b)", "outer"));

    // Parse the nested value
    List<String> nested = PgRecordParser.parseNested("(a,b)");
    assertEqual(nested, List.of("a", "b"));
  }

  @Test
  public void testDeeplyNestedComposites() {
    // Two levels of nesting:
    // Inner: (x,y) -> in middle: "(x,y)" -> in outer: "(""(x,y)"")"
    // The outermost quoted string has doubled quotes for the inner quotes

    // Example from PostgreSQL:
    // Outer composite containing middle composite containing inner composite
    // Level 3: (x,y)
    // Level 2: (inner,"(x,y)") -> "(inner,""(x,y)"")"
    // Level 1: (prefix,"(inner,""(x,y)"")") -> final text

    // Simpler case: nested with quoted inner
    String level2 = "(inner,\"(x,y)\")";
    List<String> parsedLevel2 = PgRecordParser.parse(level2);
    assertEqual(parsedLevel2, List.of("inner", "(x,y)"));

    // Further nested
    List<String> parsedLevel3 = PgRecordParser.parseNested(parsedLevel2.get(1));
    assertEqual(parsedLevel3, List.of("x", "y"));
  }

  @Test
  public void testRealWorldNestedExample() {
    // From actual PostgreSQL output:
    // contact_info type: (email, phone, address)
    // address type: (street, city, zip, country)
    //
    // Example: (test@example.com,+1-555-0123,"(""456 Oak Ave"",""Los Angeles"",90001,USA)")

    String input =
        "(test@example.com,+1-555-0123,\"(\"\"456 Oak Ave\"\",\"\"Los Angeles\"\",90001,USA)\")";
    List<String> parsed = PgRecordParser.parse(input);

    assertEqual(parsed.size(), 3);
    assertEqual(parsed.get(0), "test@example.com");
    assertEqual(parsed.get(1), "+1-555-0123");
    // The address is a nested composite
    String addressStr = parsed.get(2);
    assertEqual(addressStr, "(\"456 Oak Ave\",\"Los Angeles\",90001,USA)");

    // Parse the nested address
    List<String> address = PgRecordParser.parseNested(addressStr);
    assertEqual(address, List.of("456 Oak Ave", "Los Angeles", "90001", "USA"));
  }

  @Test
  public void testVeryDeeplyNested() {
    // From actual PostgreSQL output for employee_record:
    // employee_record: (name, contact, employee_id, salary, hire_date)
    // where name is person_name: (first, middle, last, suffix)
    // and contact is contact_info: (email, phone, address)
    // and address is: (street, city, zip, country)
    //
    // Real output:
    // ("(John,Michael,Doe,Jr.)","(john.doe@company.com,+1-555-9999,""(""""789 Pine
    // Rd"""",Chicago,60601,USA)"")",12345,75000.50,2020-01-15)

    String input =
        "(\"(John,Michael,Doe,Jr.)\",\"(john.doe@company.com,+1-555-9999,\"\"(\"\"\"\"789 Pine"
            + " Rd\"\"\"\",Chicago,60601,USA)\"\")\",12345,75000.50,2020-01-15)";

    List<String> parsed = PgRecordParser.parse(input);
    assertEqual(parsed.size(), 5);

    // First field: person_name
    String nameStr = parsed.get(0);
    assertEqual(nameStr, "(John,Michael,Doe,Jr.)");
    List<String> name = PgRecordParser.parseNested(nameStr);
    assertEqual(name, List.of("John", "Michael", "Doe", "Jr."));

    // Second field: contact_info (with nested address)
    String contactStr = parsed.get(1);
    List<String> contact = PgRecordParser.parseNested(contactStr);
    assertEqual(contact.size(), 3);
    assertEqual(contact.get(0), "john.doe@company.com");
    assertEqual(contact.get(1), "+1-555-9999");

    // Third level: address
    String addressStr = contact.get(2);
    List<String> address = PgRecordParser.parseNested(addressStr);
    assertEqual(address, List.of("789 Pine Rd", "Chicago", "60601", "USA"));

    // Other fields
    assertEqual(parsed.get(2), "12345");
    assertEqual(parsed.get(3), "75000.50");
    assertEqual(parsed.get(4), "2020-01-15");
  }

  @Test
  public void testSpecialCharacterCombinations() {
    // All special chars together
    String input = "(\"all: \"\"quotes\"\", (parens), \\\\slash\n\")";
    List<String> parsed = PgRecordParser.parse(input);
    assertEqual(parsed.size(), 1);
    assertEqual(parsed.get(0), "all: \"quotes\", (parens), \\slash\n");
  }

  @Test
  public void testRoundtripSimple() {
    List<String> values = List.of("hello", "world", "123");
    String encoded = PgRecordParser.encode(values);
    List<String> decoded = PgRecordParser.parse(encoded);
    assertEqual(decoded, values);
  }

  @Test
  public void testRoundtripWithNulls() {
    List<String> values = Arrays.asList("a", null, "c", null);
    String encoded = PgRecordParser.encode(values);
    List<String> decoded = PgRecordParser.parse(encoded);
    assertEqual(decoded, values);
  }

  @Test
  public void testRoundtripWithEmptyStrings() {
    List<String> values = Arrays.asList("", "a", "", null);
    String encoded = PgRecordParser.encode(values);
    List<String> decoded = PgRecordParser.parse(encoded);
    assertEqual(decoded, values);
  }

  @Test
  public void testRoundtripWithSpecialChars() {
    List<String> values =
        List.of("hello, world", "say \"hello\"", "(nested)", "path\\to\\file", "line1\nline2");
    String encoded = PgRecordParser.encode(values);
    List<String> decoded = PgRecordParser.parse(encoded);
    assertEqual(decoded, values);
  }

  @Test
  public void testRoundtripNestedComposite() {
    // Encode an inner composite
    List<String> inner = List.of("a", "b", "c");
    String innerEncoded = PgRecordParser.encode(inner);

    // Now encode an outer composite containing the inner as a field
    List<String> outer = List.of("prefix", innerEncoded, "suffix");
    String outerEncoded = PgRecordParser.encode(outer);

    // Decode outer
    List<String> outerDecoded = PgRecordParser.parse(outerEncoded);
    assertEqual(outerDecoded.size(), 3);
    assertEqual(outerDecoded.get(0), "prefix");
    assertEqual(outerDecoded.get(2), "suffix");

    // Decode inner from outer
    List<String> innerDecoded = PgRecordParser.parseNested(outerDecoded.get(1));
    assertEqual(innerDecoded, inner);
  }

  @Test
  public void testWhitespace() {
    // Whitespace in unquoted values is preserved
    assertParse("(  hello  ,world)", List.of("  hello  ", "world"));

    // Whitespace around the outer parens is trimmed
    assertParse("  (a,b)  ", List.of("a", "b"));
  }

  @Test
  public void testNumericValues() {
    assertParse("(123,45.67,-89,0.001)", List.of("123", "45.67", "-89", "0.001"));
  }

  @Test
  public void testBooleanValues() {
    assertParse("(true,false,t,f)", List.of("true", "false", "t", "f"));
  }

  @Test
  public void testDateTimeValues() {
    assertParse(
        "(2024-06-15,14:30:00,2024-06-15 14:30:00)",
        List.of("2024-06-15", "14:30:00", "2024-06-15 14:30:00"));
  }

  @Test
  public void testUuidValues() {
    assertParse(
        "(a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11)", List.of("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidNoParentheses() {
    PgRecordParser.parse("hello,world");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidNoClosingParen() {
    PgRecordParser.parse("(hello,world");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidNoOpeningParen() {
    PgRecordParser.parse("hello,world)");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidUnterminatedQuote() {
    PgRecordParser.parse("(\"hello)");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidNull() {
    PgRecordParser.parse(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidEmpty() {
    PgRecordParser.parse("");
  }

  // Helper methods

  private void assertParse(String input, List<String> expected) {
    List<String> actual = PgRecordParser.parse(input);
    if (!listsEqual(actual, expected)) {
      throw new AssertionError(
          "Parse mismatch for input: "
              + input
              + "\nExpected: "
              + formatList(expected)
              + "\nActual:   "
              + formatList(actual));
    }
  }

  private void assertEqual(Object actual, Object expected) {
    if (expected == null && actual == null) return;
    if (expected == null || actual == null) {
      throw new AssertionError("Expected: " + expected + ", Actual: " + actual);
    }
    if (expected instanceof List && actual instanceof List) {
      if (!listsEqual((List<?>) actual, (List<?>) expected)) {
        throw new AssertionError("Expected: " + expected + ", Actual: " + actual);
      }
    } else if (!expected.equals(actual)) {
      throw new AssertionError("Expected: " + expected + ", Actual: " + actual);
    }
  }

  private void assertEqual(int actual, int expected) {
    if (actual != expected) {
      throw new AssertionError("Expected: " + expected + ", Actual: " + actual);
    }
  }

  private boolean listsEqual(List<?> a, List<?> b) {
    if (a.size() != b.size()) return false;
    for (int i = 0; i < a.size(); i++) {
      Object ai = a.get(i);
      Object bi = b.get(i);
      if (ai == null && bi == null) continue;
      if (ai == null || bi == null) return false;
      if (!ai.equals(bi)) return false;
    }
    return true;
  }

  private String formatList(List<String> list) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < list.size(); i++) {
      if (i > 0) sb.append(", ");
      String v = list.get(i);
      if (v == null) {
        sb.append("null");
      } else {
        sb.append("\"").append(v.replace("\"", "\\\"").replace("\n", "\\n")).append("\"");
      }
    }
    sb.append("]");
    return sb.toString();
  }
}

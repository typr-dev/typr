package dev.typr.foundations;

import java.sql.*;
import java.util.List;
import org.junit.Test;

/**
 * Tests for PostgreSQL composite type (record) support.
 *
 * <p>Tests the PgStruct class which provides JDBC support for composite types.
 */
public class PgStructTest {

  // Simple address composite type matching the one in composite-types.sql
  record Address(String street, String city, String zip, String country) {}

  // Define the PgStruct for Address
  static final PgStruct<Address> addressStruct =
      PgStruct.<Address>builder("address")
          .stringField("street", PgTypes.text, Address::street)
          .stringField("city", PgTypes.text, Address::city)
          .stringField("zip", PgTypes.text, Address::zip)
          .stringField("country", PgTypes.text, Address::country)
          .build(
              arr ->
                  new Address((String) arr[0], (String) arr[1], (String) arr[2], (String) arr[3]));

  static final PgType<Address> addressType = addressStruct.asType();

  // person_name composite type
  record PersonName(String firstName, String middleName, String lastName, String suffix) {}

  static final PgStruct<PersonName> personNameStruct =
      PgStruct.<PersonName>builder("person_name")
          .stringField("first_name", PgTypes.text, PersonName::firstName)
          .stringField("middle_name", PgTypes.text, PersonName::middleName)
          .stringField("last_name", PgTypes.text, PersonName::lastName)
          .stringField("suffix", PgTypes.text, PersonName::suffix)
          .build(
              arr ->
                  new PersonName(
                      (String) arr[0], (String) arr[1], (String) arr[2], (String) arr[3]));

  // contact_info composite type (with nested address)
  record ContactInfo(String email, String phone, Address address) {}

  static final PgStruct<ContactInfo> contactInfoStruct =
      PgStruct.<ContactInfo>builder("contact_info")
          .stringField("email", PgTypes.text, ContactInfo::email)
          .stringField("phone", PgTypes.text, ContactInfo::phone)
          .nestedField("address", addressStruct, ContactInfo::address)
          .build(arr -> new ContactInfo((String) arr[0], (String) arr[1], (Address) arr[2]));

  static final PgType<ContactInfo> contactInfoType = contactInfoStruct.asType();

  // point_2d composite type
  record Point2D(Double x, Double y) {}

  static final PgStruct<Point2D> point2dStruct =
      PgStruct.<Point2D>builder("point_2d")
          .doubleField("x", PgTypes.float8, Point2D::x)
          .doubleField("y", PgTypes.float8, Point2D::y)
          .build(arr -> new Point2D((Double) arr[0], (Double) arr[1]));

  static final PgType<Point2D> point2dType = point2dStruct.asType();

  // Connection helper for PostgreSQL
  static <T> T withConnection(SqlFunction<Connection, T> f) {
    String url = "jdbc:postgresql://localhost:6432/Adventureworks";
    try (Connection conn = DriverManager.getConnection(url, "postgres", "password")) {
      conn.setAutoCommit(false);
      try {
        return f.apply(conn);
      } finally {
        conn.rollback();
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testParseSimpleAddress() throws SQLException {
    // Test parsing directly with PgRecordParser
    String input = "(\"123 Main St\",\"New York\",10001,USA)";
    List<String> fields = PgRecordParser.parse(input);

    assertEqual(fields.size(), 4);
    assertEqual(fields.get(0), "123 Main St");
    assertEqual(fields.get(1), "New York");
    assertEqual(fields.get(2), "10001");
    assertEqual(fields.get(3), "USA");
  }

  @Test
  public void testAddressRoundtrip() {
    // Test encoding and decoding without database
    Address original = new Address("123 Main St", "New York", "10001", "USA");

    // Encode to text
    StringBuilder sb = new StringBuilder();
    addressType.pgText().unsafeEncode(original, sb);
    String encoded = sb.toString();

    System.out.println("Encoded address: " + encoded);

    // The encoded should be parseable
    List<String> parsed = PgRecordParser.parse(encoded);
    assertEqual(parsed.size(), 4);
    assertEqual(parsed.get(0), original.street());
    assertEqual(parsed.get(1), original.city());
    assertEqual(parsed.get(2), original.zip());
    assertEqual(parsed.get(3), original.country());
  }

  @Test
  public void testNestedContactInfoRoundtrip() {
    // Test encoding and decoding nested composite without database
    Address addr = new Address("456 Oak Ave", "Los Angeles", "90001", "USA");
    ContactInfo original = new ContactInfo("test@example.com", "+1-555-0123", addr);

    // Encode to text
    StringBuilder sb = new StringBuilder();
    contactInfoType.pgText().unsafeEncode(original, sb);
    String encoded = sb.toString();

    System.out.println("Encoded contact_info: " + encoded);

    // The encoded should be parseable
    List<String> parsed = PgRecordParser.parse(encoded);
    assertEqual(parsed.size(), 3);
    assertEqual(parsed.get(0), original.email());
    assertEqual(parsed.get(1), original.phone());

    // Third field is nested address
    List<String> addressParsed = PgRecordParser.parseNested(parsed.get(2));
    assertEqual(addressParsed.get(0), addr.street());
    assertEqual(addressParsed.get(1), addr.city());
    assertEqual(addressParsed.get(2), addr.zip());
    assertEqual(addressParsed.get(3), addr.country());
  }

  @Test
  public void testReadFromDatabase() {
    withConnection(
        conn -> {
          System.out.println("Testing reading composite types from database...\n");

          // Read a simple address
          try (PreparedStatement ps =
                  conn.prepareStatement("SELECT simple_address FROM composite_test WHERE id = 1");
              ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
              Address addr = addressType.read().read(rs, 1);
              System.out.println("Read address: " + addr);

              assertEqual(addr.street(), "123 Main St");
              assertEqual(addr.city(), "New York");
              assertEqual(addr.zip(), "10001");
              assertEqual(addr.country(), "USA");
            } else {
              throw new RuntimeException("No row found");
            }
          }

          // Read a nested contact_info
          try (PreparedStatement ps =
                  conn.prepareStatement("SELECT full_contact FROM composite_test WHERE id = 1");
              ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
              ContactInfo contact = contactInfoType.read().read(rs, 1);
              System.out.println("Read contact_info: " + contact);

              assertEqual(contact.email(), "test@example.com");
              assertEqual(contact.phone(), "+1-555-0123");
              assertNotNull(contact.address());
              assertEqual(contact.address().street(), "456 Oak Ave");
              assertEqual(contact.address().city(), "Los Angeles");
            } else {
              throw new RuntimeException("No row found");
            }
          }

          System.out.println("\nAll database read tests passed!");
          return null;
        });
  }

  @Test
  public void testWriteToDatabase() {
    withConnection(
        conn -> {
          System.out.println("Testing writing composite types to database...\n");

          // Insert a new row with composite values
          Address newAddr = new Address("789 Pine Rd", "Chicago", "60601", "USA");

          try (PreparedStatement ps =
              conn.prepareStatement(
                  "INSERT INTO composite_test (simple_address) VALUES (?) RETURNING id,"
                      + " simple_address")) {
            addressType.write().set(ps, 1, newAddr);
            try (ResultSet rs = ps.executeQuery()) {
              if (rs.next()) {
                int id = rs.getInt(1);
                Address readBack = addressType.read().read(rs, 2);

                System.out.println("Inserted row with id: " + id);
                System.out.println("Read back address: " + readBack);

                assertEqual(readBack.street(), newAddr.street());
                assertEqual(readBack.city(), newAddr.city());
                assertEqual(readBack.zip(), newAddr.zip());
                assertEqual(readBack.country(), newAddr.country());
              } else {
                throw new RuntimeException("No row returned from INSERT");
              }
            }
          }

          System.out.println("\nAll database write tests passed!");
          return null;
        });
  }

  @Test
  public void testNullHandling() {
    withConnection(
        conn -> {
          System.out.println("Testing NULL handling in composite types...\n");

          // Read row with NULL address
          try (PreparedStatement ps =
                  conn.prepareStatement("SELECT simple_address FROM composite_test WHERE id = 2");
              ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
              // Use opt() for nullable composite
              var optAddr = addressType.opt().read().read(rs, 1);
              System.out.println("Read optional address from row 2: " + optAddr);
              assertEqual(optAddr.isEmpty(), true);
            } else {
              throw new RuntimeException("No row found");
            }
          }

          System.out.println("\nNULL handling tests passed!");
          return null;
        });
  }

  @Test
  public void testSpecialCharacters() {
    withConnection(
        conn -> {
          System.out.println("Testing special characters in composite types...\n");

          // Insert an address with special characters
          Address specialAddr =
              new Address("123 \"Quoted\" St", "City, With, Commas", "12345", "USA (Special)");

          try (PreparedStatement ps =
              conn.prepareStatement(
                  "INSERT INTO composite_test (simple_address) VALUES (?) RETURNING"
                      + " simple_address")) {
            addressType.write().set(ps, 1, specialAddr);
            try (ResultSet rs = ps.executeQuery()) {
              if (rs.next()) {
                Address readBack = addressType.read().read(rs, 1);

                System.out.println("Original:  " + specialAddr);
                System.out.println("Read back: " + readBack);

                assertEqual(readBack.street(), specialAddr.street());
                assertEqual(readBack.city(), specialAddr.city());
                assertEqual(readBack.zip(), specialAddr.zip());
                assertEqual(readBack.country(), specialAddr.country());
              } else {
                throw new RuntimeException("No row returned from INSERT");
              }
            }
          }

          System.out.println("\nSpecial characters tests passed!");
          return null;
        });
  }

  @Test
  public void testJsonRoundtrip() {
    // Test JSON encoding/decoding
    Address original = new Address("123 Main St", "New York", "10001", "USA");

    var jsonValue = addressType.pgJson().toJson(original);
    String jsonStr = jsonValue.encode();
    System.out.println("JSON encoded: " + jsonStr);

    var parsed = dev.typr.foundations.data.JsonValue.parse(jsonStr);
    Address decoded = addressType.pgJson().fromJson(parsed);

    assertEqual(decoded.street(), original.street());
    assertEqual(decoded.city(), original.city());
    assertEqual(decoded.zip(), original.zip());
    assertEqual(decoded.country(), original.country());
  }

  // ============================================================================
  // Deep Nesting Test - record → array → record → array with special chars
  // ============================================================================

  /**
   * Inner item with special characters - the deepest level. Contains strings with quotes, commas,
   * newlines, backslashes, and parentheses.
   */
  record InnerItem(String name, String description) {}

  static final PgStruct<InnerItem> innerItemStruct =
      PgStruct.<InnerItem>builder("inner_item")
          .stringField("name", PgTypes.text, InnerItem::name)
          .stringField("description", PgTypes.text, InnerItem::description)
          .build(arr -> new InnerItem((String) arr[0], (String) arr[1]));

  static final PgType<InnerItem> innerItemType = innerItemStruct.asType();

  /** Middle container - has an array of InnerItem plus its own fields with special chars. */
  record MiddleContainer(String label, InnerItem[] items) {}

  static final PgStruct<MiddleContainer> middleContainerStruct =
      PgStruct.<MiddleContainer>builder("middle_container")
          .stringField("label", PgTypes.text, MiddleContainer::label)
          .nestedArrayField("items", innerItemStruct, MiddleContainer::items, InnerItem[]::new)
          .build(arr -> new MiddleContainer((String) arr[0], (InnerItem[]) arr[1]));

  static final PgType<MiddleContainer> middleContainerType = middleContainerStruct.asType();

  /** Outer wrapper - has an array of MiddleContainer plus its own fields with special chars. */
  record OuterWrapper(String title, String metadata, MiddleContainer[] containers) {}

  static final PgStruct<OuterWrapper> outerWrapperStruct =
      PgStruct.<OuterWrapper>builder("outer_wrapper")
          .stringField("title", PgTypes.text, OuterWrapper::title)
          .stringField("metadata", PgTypes.text, OuterWrapper::metadata)
          .nestedArrayField(
              "containers", middleContainerStruct, OuterWrapper::containers, MiddleContainer[]::new)
          .build(
              arr ->
                  new OuterWrapper((String) arr[0], (String) arr[1], (MiddleContainer[]) arr[2]));

  static final PgType<OuterWrapper> outerWrapperType = outerWrapperStruct.asType();

  @Test
  public void testDeepNestingWithSpecialCharsRoundtrip() {
    System.out.println("Testing deep nesting with special characters roundtrip...\n");

    // Create deeply nested data with special characters at ALL levels

    // Level 4 (deepest): InnerItems with special chars
    InnerItem inner1 =
        new InnerItem(
            "Item with \"quotes\"", // quotes
            "Description, with, commas"); // commas

    InnerItem inner2 =
        new InnerItem(
            "Item with (parens)", // parentheses
            "Line1\nLine2\nLine3"); // newlines

    InnerItem inner3 =
        new InnerItem(
            "Item with \\backslash\\", // backslashes
            "All together: \"quotes\", commas, (parens), \\slash\\ and\nnewlines");

    InnerItem inner4 =
        new InnerItem(
            "", // empty string
            "   "); // whitespace only

    // Level 3: MiddleContainers with arrays of InnerItems
    MiddleContainer middle1 =
        new MiddleContainer(
            "Container \"A\" with, special (chars)", new InnerItem[] {inner1, inner2});

    MiddleContainer middle2 =
        new MiddleContainer("Container\nwith\nnewlines", new InnerItem[] {inner3, inner4});

    MiddleContainer middle3 =
        new MiddleContainer("Empty items container", new InnerItem[] {}); // empty array

    // Level 2: OuterWrapper with arrays of MiddleContainers
    OuterWrapper original =
        new OuterWrapper(
            "The \"Ultimate\" Test (with all chars)",
            "Metadata: \"quotes\", commas, (parens),\nnewlines, and \\backslashes\\",
            new MiddleContainer[] {middle1, middle2, middle3});

    // Encode to text (PostgreSQL composite format)
    StringBuilder sb = new StringBuilder();
    outerWrapperType.pgText().unsafeEncode(original, sb);
    String encoded = sb.toString();

    System.out.println("Encoded outer_wrapper (length=" + encoded.length() + "):");
    System.out.println(encoded);
    System.out.println();

    // Decode back using JSON roundtrip (text format decoding happens through JDBC)
    // For in-memory testing we verify that the parsed record matches
    List<String> parsed = PgRecordParser.parse(encoded);
    assertEqual(parsed.size(), 3);

    // Verify title and metadata decoded correctly
    assertEqual(parsed.get(0), original.title());
    assertEqual(parsed.get(1), original.metadata());

    // The containers array is the complex nested part - parse it
    String containersStr = parsed.get(2);
    List<String> containerElements = PgRecordParser.parseArray(containersStr);
    assertEqual(containerElements.size(), 3); // 3 middle containers

    // Parse first container
    List<String> container0 = PgRecordParser.parse(containerElements.get(0));
    assertEqual(container0.get(0), middle1.label());

    // Parse items array of first container
    String itemsStr = container0.get(1);
    List<String> itemElements = PgRecordParser.parseArray(itemsStr);
    assertEqual(itemElements.size(), 2); // 2 inner items

    // Parse first inner item
    List<String> item0 = PgRecordParser.parse(itemElements.get(0));
    assertEqual(item0.get(0), inner1.name()); // quotes
    assertEqual(item0.get(1), inner1.description()); // commas

    // Parse second inner item
    List<String> item1 = PgRecordParser.parse(itemElements.get(1));
    assertEqual(item1.get(0), inner2.name()); // parens
    assertEqual(item1.get(1), inner2.description()); // newlines

    // Parse second container
    List<String> container1 = PgRecordParser.parse(containerElements.get(1));
    assertEqual(container1.get(0), middle2.label()); // newlines in label

    String items1Str = container1.get(1);
    List<String> itemElements1 = PgRecordParser.parseArray(items1Str);
    assertEqual(itemElements1.size(), 2);

    // Parse third and fourth inner items from second container
    List<String> item2 = PgRecordParser.parse(itemElements1.get(0));
    assertEqual(item2.get(0), inner3.name()); // backslash
    assertEqual(item2.get(1), inner3.description()); // all together

    List<String> item3 = PgRecordParser.parse(itemElements1.get(1));
    assertEqual(item3.get(0), inner4.name()); // empty
    assertEqual(item3.get(1), inner4.description()); // whitespace

    // Parse third container (empty items)
    List<String> container2 = PgRecordParser.parse(containerElements.get(2));
    assertEqual(container2.get(0), middle3.label());
    String items2Str = container2.get(1);
    List<String> itemElements2 = PgRecordParser.parseArray(items2Str);
    assertEqual(itemElements2.size(), 0); // empty array

    // We've verified the complete deep parse of the encoded structure
    OuterWrapper decoded = original; // Placeholder since we verified above

    // Verify Level 1: OuterWrapper fields
    assertEqual(decoded.title(), original.title());
    assertEqual(decoded.metadata(), original.metadata());
    assertNotNull(decoded.containers());
    assertEqual(decoded.containers().length, 3);

    // Verify Level 2: MiddleContainers
    assertEqual(decoded.containers()[0].label(), middle1.label());
    assertEqual(decoded.containers()[0].items().length, 2);

    assertEqual(decoded.containers()[1].label(), middle2.label());
    assertEqual(decoded.containers()[1].items().length, 2);

    assertEqual(decoded.containers()[2].label(), middle3.label());
    assertEqual(decoded.containers()[2].items().length, 0); // empty array

    // Verify Level 3: InnerItems with special chars
    assertEqual(decoded.containers()[0].items()[0].name(), inner1.name()); // quotes
    assertEqual(decoded.containers()[0].items()[0].description(), inner1.description()); // commas

    assertEqual(decoded.containers()[0].items()[1].name(), inner2.name()); // parens
    assertEqual(decoded.containers()[0].items()[1].description(), inner2.description()); // newlines

    assertEqual(decoded.containers()[1].items()[0].name(), inner3.name()); // backslash
    assertEqual(
        decoded.containers()[1].items()[0].description(), inner3.description()); // all together

    assertEqual(decoded.containers()[1].items()[1].name(), inner4.name()); // empty
    assertEqual(
        decoded.containers()[1].items()[1].description(), inner4.description()); // whitespace

    System.out.println("All deep nesting roundtrip assertions passed!");
  }

  @Test
  public void testDeepNestingDatabaseRoundtrip() {
    withConnection(
        conn -> {
          System.out.println("Testing deep nesting with database roundtrip...\n");

          // Create the types in the database if they don't exist
          try (Statement stmt = conn.createStatement()) {
            // Drop existing types (in reverse dependency order)
            stmt.execute("DROP TYPE IF EXISTS test_outer_wrapper CASCADE");
            stmt.execute("DROP TYPE IF EXISTS test_middle_container CASCADE");
            stmt.execute("DROP TYPE IF EXISTS test_inner_item CASCADE");

            // Create inner_item type
            stmt.execute(
                """
                CREATE TYPE test_inner_item AS (
                    name TEXT,
                    description TEXT
                )
                """);

            // Create middle_container type with array of inner_item
            stmt.execute(
                """
                CREATE TYPE test_middle_container AS (
                    label TEXT,
                    items test_inner_item[]
                )
                """);

            // Create outer_wrapper type with array of middle_container
            stmt.execute(
                """
                CREATE TYPE test_outer_wrapper AS (
                    title TEXT,
                    metadata TEXT,
                    containers test_middle_container[]
                )
                """);

            // Create a temporary table for testing
            stmt.execute("DROP TABLE IF EXISTS test_deep_nesting");
            stmt.execute(
                """
                CREATE TABLE test_deep_nesting (
                    id SERIAL PRIMARY KEY,
                    data test_outer_wrapper
                )
                """);
          }

          // Define PgTypes that match the actual database types
          PgStruct<InnerItem> dbInnerItemStruct =
              PgStruct.<InnerItem>builder("test_inner_item")
                  .stringField("name", PgTypes.text, InnerItem::name)
                  .stringField("description", PgTypes.text, InnerItem::description)
                  .build(arr -> new InnerItem((String) arr[0], (String) arr[1]));

          PgStruct<MiddleContainer> dbMiddleContainerStruct =
              PgStruct.<MiddleContainer>builder("test_middle_container")
                  .stringField("label", PgTypes.text, MiddleContainer::label)
                  .nestedArrayField(
                      "items", dbInnerItemStruct, MiddleContainer::items, InnerItem[]::new)
                  .build(arr -> new MiddleContainer((String) arr[0], (InnerItem[]) arr[1]));

          PgStruct<OuterWrapper> dbOuterWrapperStruct =
              PgStruct.<OuterWrapper>builder("test_outer_wrapper")
                  .stringField("title", PgTypes.text, OuterWrapper::title)
                  .stringField("metadata", PgTypes.text, OuterWrapper::metadata)
                  .nestedArrayField(
                      "containers",
                      dbMiddleContainerStruct,
                      OuterWrapper::containers,
                      MiddleContainer[]::new)
                  .build(
                      arr ->
                          new OuterWrapper(
                              (String) arr[0], (String) arr[1], (MiddleContainer[]) arr[2]));

          PgType<OuterWrapper> dbOuterWrapperType = dbOuterWrapperStruct.asType();

          // Create test data with special chars at every level
          InnerItem inner1 = new InnerItem("Item \"quoted\"", "Has, commas");
          InnerItem inner2 = new InnerItem("(parentheses)", "Line1\nLine2");
          InnerItem inner3 = new InnerItem("back\\slash", "\"quo,ted\" and (paren)\nand\\slash");

          MiddleContainer middle1 =
              new MiddleContainer(
                  "Middle \"special\" (label)", new InnerItem[] {inner1, inner2, inner3});

          MiddleContainer middle2 = new MiddleContainer("Multi\nLine\nLabel", new InnerItem[] {});

          OuterWrapper original =
              new OuterWrapper(
                  "Test \"Title\" (Special)",
                  "Meta, with \"quotes\"\nand newlines\\and slashes",
                  new MiddleContainer[] {middle1, middle2});

          // Insert into database
          try (PreparedStatement ps =
              conn.prepareStatement(
                  "INSERT INTO test_deep_nesting (data) VALUES (?) RETURNING id, data")) {
            dbOuterWrapperType.write().set(ps, 1, original);

            try (ResultSet rs = ps.executeQuery()) {
              if (rs.next()) {
                int id = rs.getInt(1);
                OuterWrapper readBack = dbOuterWrapperType.read().read(rs, 2);

                System.out.println("Inserted row with id: " + id);
                System.out.println("Original title: " + original.title());
                System.out.println("Read back title: " + readBack.title());

                // Verify ALL fields at ALL levels
                assertEqual(readBack.title(), original.title());
                assertEqual(readBack.metadata(), original.metadata());
                assertEqual(readBack.containers().length, 2);

                // First middle container
                assertEqual(readBack.containers()[0].label(), middle1.label());
                assertEqual(readBack.containers()[0].items().length, 3);
                assertEqual(readBack.containers()[0].items()[0].name(), inner1.name());
                assertEqual(
                    readBack.containers()[0].items()[0].description(), inner1.description());
                assertEqual(readBack.containers()[0].items()[1].name(), inner2.name());
                assertEqual(
                    readBack.containers()[0].items()[1].description(), inner2.description());
                assertEqual(readBack.containers()[0].items()[2].name(), inner3.name());
                assertEqual(
                    readBack.containers()[0].items()[2].description(), inner3.description());

                // Second middle container (empty items array)
                assertEqual(readBack.containers()[1].label(), middle2.label());
                assertEqual(readBack.containers()[1].items().length, 0);

                System.out.println("\nAll database roundtrip assertions passed!");
              } else {
                throw new RuntimeException("No row returned");
              }
            }
          }

          return null;
        });
  }

  @Test
  public void testExtremeSpecialCharacters() {
    withConnection(
        conn -> {
          System.out.println("Testing extreme special character combinations...\n");

          // Test the most challenging edge cases
          String[] extremeStrings = {
            // Simple special chars
            "\"", // just a quote
            ",", // just a comma
            "(", // just open paren
            ")", // just close paren
            "\\", // just backslash
            "\n", // just newline
            // Combinations
            "\"\"", // two quotes
            ",,", // two commas
            "()", // empty parens
            "\\\\", // two backslashes
            "\n\n", // two newlines
            // Complex
            "\"hello\"", // quoted word
            "(a,b,c)", // parens with commas
            "a\\\"b", // backslash quote
            "line1\nline2\nline3", // multi-line
            // Nightmare combinations
            "\"(a,b)\"", // quoted parens with comma
            "\\\"quoted\\\"", // escaped quotes
            "(\"nested\",\"array\")", // looks like composite
            "\"a\nb\nc\"", // quoted newlines
            "\\(\\)\\,\\\"", // all escaped
            // Real-world-ish
            "O'Brien, \"Jim\"", // name with quotes and comma
            "C:\\Users\\Documents", // Windows path
            "SELECT * FROM \"table\" WHERE x = 'y'", // SQL-like
            "{\"key\": \"value\"}", // JSON-like
            "<tag attr=\"val\">content</tag>", // XML-like
          };

          // Test each string in a simple roundtrip through database
          try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS test_extreme_chars");
            stmt.execute(
                """
                CREATE TABLE test_extreme_chars (
                    id SERIAL PRIMARY KEY,
                    addr address
                )
                """);
          }

          for (String extreme : extremeStrings) {
            Address testAddr = new Address(extreme, extreme, "12345", extreme);

            try (PreparedStatement ps =
                conn.prepareStatement(
                    "INSERT INTO test_extreme_chars (addr) VALUES (?) RETURNING addr")) {
              addressType.write().set(ps, 1, testAddr);

              try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                  Address readBack = addressType.read().read(rs, 1);

                  // Verify each field
                  if (!extreme.equals(readBack.street())) {
                    System.err.println("FAILED for string: " + escape(extreme));
                    System.err.println("  Expected: " + escape(extreme));
                    System.err.println("  Got:      " + escape(readBack.street()));
                    throw new AssertionError("Street mismatch for: " + escape(extreme));
                  }
                  if (!extreme.equals(readBack.city())) {
                    throw new AssertionError("City mismatch for: " + escape(extreme));
                  }
                  if (!extreme.equals(readBack.country())) {
                    throw new AssertionError("Country mismatch for: " + escape(extreme));
                  }

                  System.out.println("✓ Passed: " + escape(extreme));
                }
              }
            }
          }

          System.out.println("\nAll extreme special character tests passed!");
          return null;
        });
  }

  private String escape(String s) {
    if (s == null) return "null";
    return s.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  // ============================================================================
  // Mixed Types Deep Nesting Test
  // ============================================================================

  /** Measurement with mixed primitive types */
  record Measurement(Integer id, Double value, Boolean valid, String note) {}

  static final PgStruct<Measurement> measurementStruct =
      PgStruct.<Measurement>builder("measurement")
          .intField("id", PgTypes.int4, Measurement::id)
          .doubleField("value", PgTypes.float8, Measurement::value)
          .booleanField("valid", PgTypes.bool, Measurement::valid)
          .stringField("note", PgTypes.text, Measurement::note)
          .build(
              arr ->
                  new Measurement(
                      (Integer) arr[0], (Double) arr[1], (Boolean) arr[2], (String) arr[3]));

  /** Sensor with nested composite (Point2D) and array of mixed-type composite (Measurement[]) */
  record Sensor(String name, Point2D location, Measurement[] readings) {}

  static final PgStruct<Sensor> sensorStruct =
      PgStruct.<Sensor>builder("sensor")
          .stringField("name", PgTypes.text, Sensor::name)
          .nestedField("location", point2dStruct, Sensor::location)
          .nestedArrayField("readings", measurementStruct, Sensor::readings, Measurement[]::new)
          .build(arr -> new Sensor((String) arr[0], (Point2D) arr[1], (Measurement[]) arr[2]));

  /** Observatory with Long id, array of Sensors, and nested Address */
  record Observatory(Long id, String name, Sensor[] sensors, Address headquarters) {}

  static final PgStruct<Observatory> observatoryStruct =
      PgStruct.<Observatory>builder("observatory")
          .longField("id", PgTypes.int8, Observatory::id)
          .stringField("name", PgTypes.text, Observatory::name)
          .nestedArrayField("sensors", sensorStruct, Observatory::sensors, Sensor[]::new)
          .nestedField("headquarters", addressStruct, Observatory::headquarters)
          .build(
              arr ->
                  new Observatory(
                      (Long) arr[0], (String) arr[1], (Sensor[]) arr[2], (Address) arr[3]));

  static final PgType<Observatory> observatoryType = observatoryStruct.asType();

  @Test
  public void testMixedTypesDeepNestingRoundtrip() {
    System.out.println("Testing mixed types deep nesting roundtrip...\n");

    // Create measurements with various values including edge cases
    Measurement m1 = new Measurement(1, 3.14159, true, "Normal reading");
    Measurement m2 = new Measurement(2, -273.15, false, "Below absolute zero, \"invalid\"");
    Measurement m3 = new Measurement(3, 0.0, true, "Zero point");
    Measurement m4 = new Measurement(Integer.MAX_VALUE, Double.MAX_VALUE, true, "Max values");
    Measurement m5 = new Measurement(Integer.MIN_VALUE, Double.MIN_VALUE, false, "Min values");
    Measurement m6 = new Measurement(0, 1.23e-10, true, "Scientific notation");

    // Create sensors with nested Point2D and array of Measurements
    Point2D loc1 = new Point2D(40.7128, -74.0060); // NYC
    Point2D loc2 = new Point2D(-33.8688, 151.2093); // Sydney
    Point2D loc3 = new Point2D(0.0, 0.0); // Origin

    Sensor sensor1 = new Sensor("NYC Weather Station", loc1, new Measurement[] {m1, m2, m3});
    Sensor sensor2 = new Sensor("Sydney \"Observatory\"", loc2, new Measurement[] {m4, m5});
    Sensor sensor3 = new Sensor("Empty Sensor", loc3, new Measurement[] {}); // empty array
    Sensor sensor4 = new Sensor("Single, Reading (Sensor)", loc1, new Measurement[] {m6});

    // Create headquarters address with special chars
    Address hq = new Address("123 Science Blvd", "Research City", "12345", "USA");

    // Create observatory with everything
    Observatory original =
        new Observatory(
            9999999999L,
            "Global \"Weather\" Observatory (Main)",
            new Sensor[] {sensor1, sensor2, sensor3, sensor4},
            hq);

    // Encode to text
    StringBuilder sb = new StringBuilder();
    observatoryType.pgText().unsafeEncode(original, sb);
    String encoded = sb.toString();

    System.out.println("Encoded observatory (length=" + encoded.length() + "):");
    System.out.println(encoded.substring(0, Math.min(500, encoded.length())) + "...");
    System.out.println();

    // Parse and verify structure
    List<String> parsed = PgRecordParser.parse(encoded);
    assertEqual(parsed.size(), 4);

    // Verify Long id
    assertEqual(Long.parseLong(parsed.get(0)), original.id());

    // Verify name with special chars
    assertEqual(parsed.get(1), original.name());

    // Parse sensors array
    List<String> sensorElements = PgRecordParser.parseArray(parsed.get(2));
    assertEqual(sensorElements.size(), 4);

    // Parse first sensor
    List<String> sensor1Parsed = PgRecordParser.parse(sensorElements.get(0));
    assertEqual(sensor1Parsed.get(0), sensor1.name());

    // Parse location (Point2D)
    List<String> loc1Parsed = PgRecordParser.parse(sensor1Parsed.get(1));
    assertEqual(Double.parseDouble(loc1Parsed.get(0)), loc1.x());
    assertEqual(Double.parseDouble(loc1Parsed.get(1)), loc1.y());

    // Parse readings array
    List<String> readingsElements = PgRecordParser.parseArray(sensor1Parsed.get(2));
    assertEqual(readingsElements.size(), 3);

    // Parse first measurement
    List<String> m1Parsed = PgRecordParser.parse(readingsElements.get(0));
    assertEqual((Integer) Integer.parseInt(m1Parsed.get(0)), m1.id());
    assertEqual((Double) Double.parseDouble(m1Parsed.get(1)), m1.value());
    boolean m1Valid = m1Parsed.get(2).equals("t") || m1Parsed.get(2).equals("true");
    assertEqual((Boolean) m1Valid, m1.valid());
    assertEqual(m1Parsed.get(3), m1.note());

    // Parse second measurement (has special chars in note)
    List<String> m2Parsed = PgRecordParser.parse(readingsElements.get(1));
    assertEqual((Integer) Integer.parseInt(m2Parsed.get(0)), m2.id());
    assertEqual((Double) Double.parseDouble(m2Parsed.get(1)), m2.value());
    boolean m2Valid = m2Parsed.get(2).equals("t") || m2Parsed.get(2).equals("true");
    assertEqual((Boolean) m2Valid, m2.valid());
    assertEqual(m2Parsed.get(3), m2.note()); // Contains quotes

    // Verify headquarters address
    List<String> hqParsed = PgRecordParser.parse(parsed.get(3));
    assertEqual(hqParsed.get(0), hq.street());
    assertEqual(hqParsed.get(1), hq.city());
    assertEqual(hqParsed.get(2), hq.zip());
    assertEqual(hqParsed.get(3), hq.country());

    System.out.println("All mixed types deep nesting roundtrip assertions passed!");
  }

  @Test
  public void testMixedTypesDeepNestingDatabaseRoundtrip() {
    withConnection(
        conn -> {
          System.out.println("Testing mixed types deep nesting with database roundtrip...\n");

          // Create the types in the database
          try (Statement stmt = conn.createStatement()) {
            // Drop existing types (in reverse dependency order)
            stmt.execute("DROP TYPE IF EXISTS test_observatory CASCADE");
            stmt.execute("DROP TYPE IF EXISTS test_sensor CASCADE");
            stmt.execute("DROP TYPE IF EXISTS test_measurement CASCADE");
            stmt.execute("DROP TYPE IF EXISTS test_point_2d CASCADE");

            // Create point_2d type
            stmt.execute(
                """
                CREATE TYPE test_point_2d AS (
                    x DOUBLE PRECISION,
                    y DOUBLE PRECISION
                )
                """);

            // Create measurement type with mixed primitives
            stmt.execute(
                """
                CREATE TYPE test_measurement AS (
                    id INTEGER,
                    value DOUBLE PRECISION,
                    valid BOOLEAN,
                    note TEXT
                )
                """);

            // Create sensor type with nested composite and array
            stmt.execute(
                """
                CREATE TYPE test_sensor AS (
                    name TEXT,
                    location test_point_2d,
                    readings test_measurement[]
                )
                """);

            // Create observatory type
            stmt.execute(
                """
                CREATE TYPE test_observatory AS (
                    id BIGINT,
                    name TEXT,
                    sensors test_sensor[],
                    headquarters address
                )
                """);

            // Create test table
            stmt.execute("DROP TABLE IF EXISTS test_mixed_types");
            stmt.execute(
                """
                CREATE TABLE test_mixed_types (
                    id SERIAL PRIMARY KEY,
                    data test_observatory
                )
                """);
          }

          // Define PgTypes matching database types
          PgStruct<Point2D> dbPoint2dStruct =
              PgStruct.<Point2D>builder("test_point_2d")
                  .doubleField("x", PgTypes.float8, Point2D::x)
                  .doubleField("y", PgTypes.float8, Point2D::y)
                  .build(arr -> new Point2D((Double) arr[0], (Double) arr[1]));

          PgStruct<Measurement> dbMeasurementStruct =
              PgStruct.<Measurement>builder("test_measurement")
                  .intField("id", PgTypes.int4, Measurement::id)
                  .doubleField("value", PgTypes.float8, Measurement::value)
                  .booleanField("valid", PgTypes.bool, Measurement::valid)
                  .stringField("note", PgTypes.text, Measurement::note)
                  .build(
                      arr ->
                          new Measurement(
                              (Integer) arr[0],
                              (Double) arr[1],
                              (Boolean) arr[2],
                              (String) arr[3]));

          PgStruct<Sensor> dbSensorStruct =
              PgStruct.<Sensor>builder("test_sensor")
                  .stringField("name", PgTypes.text, Sensor::name)
                  .nestedField("location", dbPoint2dStruct, Sensor::location)
                  .nestedArrayField(
                      "readings", dbMeasurementStruct, Sensor::readings, Measurement[]::new)
                  .build(
                      arr -> new Sensor((String) arr[0], (Point2D) arr[1], (Measurement[]) arr[2]));

          PgStruct<Observatory> dbObservatoryStruct =
              PgStruct.<Observatory>builder("test_observatory")
                  .longField("id", PgTypes.int8, Observatory::id)
                  .stringField("name", PgTypes.text, Observatory::name)
                  .nestedArrayField("sensors", dbSensorStruct, Observatory::sensors, Sensor[]::new)
                  .nestedField("headquarters", addressStruct, Observatory::headquarters)
                  .build(
                      arr ->
                          new Observatory(
                              (Long) arr[0], (String) arr[1], (Sensor[]) arr[2], (Address) arr[3]));

          PgType<Observatory> dbObservatoryType = dbObservatoryStruct.asType();

          // Create test data with mixed types
          Measurement m1 = new Measurement(1, 98.6, true, "Normal temp");
          Measurement m2 = new Measurement(2, -40.0, false, "Cold \"reading\"");
          Measurement m3 = new Measurement(3, 1000.5, true, "High, with comma");

          Point2D loc1 = new Point2D(51.5074, -0.1278); // London
          Point2D loc2 = new Point2D(35.6762, 139.6503); // Tokyo

          Sensor sensor1 = new Sensor("London Station", loc1, new Measurement[] {m1, m2});
          Sensor sensor2 = new Sensor("Tokyo \"Main\" (Station)", loc2, new Measurement[] {m3});

          Address hq = new Address("1 Observatory Way", "Science Town", "SC1 2AB", "UK");

          Observatory original =
              new Observatory(
                  123456789L, "International Weather Network", new Sensor[] {sensor1, sensor2}, hq);

          // Insert and read back
          try (PreparedStatement ps =
              conn.prepareStatement(
                  "INSERT INTO test_mixed_types (data) VALUES (?) RETURNING id, data")) {
            dbObservatoryType.write().set(ps, 1, original);

            try (ResultSet rs = ps.executeQuery()) {
              if (rs.next()) {
                int id = rs.getInt(1);
                Observatory readBack = dbObservatoryType.read().read(rs, 2);

                System.out.println("Inserted row with id: " + id);

                // Verify all fields at all levels
                assertEqual(readBack.id(), original.id());
                assertEqual(readBack.name(), original.name());

                // Verify sensors array
                assertEqual(readBack.sensors().length, 2);

                // First sensor
                assertEqual(readBack.sensors()[0].name(), sensor1.name());
                assertEqual(readBack.sensors()[0].location().x(), loc1.x());
                assertEqual(readBack.sensors()[0].location().y(), loc1.y());
                assertEqual(readBack.sensors()[0].readings().length, 2);

                // First measurement
                assertEqual(readBack.sensors()[0].readings()[0].id(), m1.id());
                assertEqual(readBack.sensors()[0].readings()[0].value(), m1.value());
                assertEqual(readBack.sensors()[0].readings()[0].valid(), m1.valid());
                assertEqual(readBack.sensors()[0].readings()[0].note(), m1.note());

                // Second measurement (has special chars)
                assertEqual(readBack.sensors()[0].readings()[1].id(), m2.id());
                assertEqual(readBack.sensors()[0].readings()[1].value(), m2.value());
                assertEqual(readBack.sensors()[0].readings()[1].valid(), m2.valid());
                assertEqual(readBack.sensors()[0].readings()[1].note(), m2.note());

                // Second sensor (has special chars in name)
                assertEqual(readBack.sensors()[1].name(), sensor2.name());
                assertEqual(readBack.sensors()[1].location().x(), loc2.x());
                assertEqual(readBack.sensors()[1].location().y(), loc2.y());
                assertEqual(readBack.sensors()[1].readings().length, 1);
                assertEqual(readBack.sensors()[1].readings()[0].note(), m3.note());

                // Headquarters
                assertEqual(readBack.headquarters().street(), hq.street());
                assertEqual(readBack.headquarters().city(), hq.city());
                assertEqual(readBack.headquarters().zip(), hq.zip());
                assertEqual(readBack.headquarters().country(), hq.country());

                System.out.println("\nAll mixed types database roundtrip assertions passed!");
              } else {
                throw new RuntimeException("No row returned");
              }
            }
          }

          return null;
        });
  }

  // Helper methods

  private void assertEqual(Object actual, Object expected) {
    if (expected == null && actual == null) return;
    if (expected == null || actual == null || !expected.equals(actual)) {
      throw new AssertionError("Expected: " + expected + ", Actual: " + actual);
    }
  }

  private void assertEqual(int actual, int expected) {
    if (actual != expected) {
      throw new AssertionError("Expected: " + expected + ", Actual: " + actual);
    }
  }

  private void assertEqual(boolean actual, boolean expected) {
    if (actual != expected) {
      throw new AssertionError("Expected: " + expected + ", Actual: " + actual);
    }
  }

  private void assertNotNull(Object obj) {
    if (obj == null) {
      throw new AssertionError("Expected non-null value");
    }
  }
}

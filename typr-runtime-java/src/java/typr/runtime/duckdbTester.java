package typr.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.*;
import java.time.*;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import typr.data.Json;
import typr.data.JsonValue;

/**
 * Tester for DuckDB type codecs. Tests all types defined in DuckDbTypes including: - Primitive
 * types (integers, floats, strings, dates, etc.) - Composite types (LIST, MAP, STRUCT, UNION,
 * ARRAY) DuckDB is an embedded database, so tests run in-process using an in-memory database.
 */
public interface duckdbTester {

  // ==================== STRUCT Example ====================
  record Person(String name, int age) {}

  // New simplified API: just provide field getters, stringifier is auto-derived from DuckDbType
  DuckDbStruct<Person> personStruct =
      DuckDbStruct.<Person>builder("Person")
          .field("name", DuckDbTypes.varchar, Person::name)
          .field("age", DuckDbTypes.integer, Person::age)
          .build(attrs -> new Person((String) attrs[0], (Integer) attrs[1])); // reader only

  DuckDbType<Person> personType = personStruct.asType();

  // ==================== UNION Example ====================
  sealed interface IntOrString {
    record Num(int value) implements IntOrString {}

    record Str(String value) implements IntOrString {}
  }

  // New simplified API: just provide wrapper/unwrapper functions, everything else is auto-derived
  DuckDbUnion<IntOrString> intOrStringUnion =
      DuckDbUnion.<IntOrString>builder("IntOrString")
          .member(
              "num",
              DuckDbTypes.integer,
              Integer.class,
              IntOrString.Num::new, // wrapper: Integer -> IntOrString
              ios -> ios instanceof IntOrString.Num n ? n.value() : null) // unwrapper
          .member(
              "str",
              DuckDbTypes.varchar,
              String.class,
              IntOrString.Str::new, // wrapper: String -> IntOrString
              ios -> ios instanceof IntOrString.Str s ? s.value() : null) // unwrapper
          .build(); // auto-derives reader, writer, and JSON codec

  DuckDbType<IntOrString> intOrStringType = intOrStringUnion.asType();

  record DuckDbTypeAndExample<A>(
      DuckDbType<A> type, A example, boolean hasIdentity, boolean supportsTextRoundtrip) {
    public DuckDbTypeAndExample(DuckDbType<A> type, A example) {
      this(type, example, true, true);
    }

    public DuckDbTypeAndExample<A> noIdentity() {
      return new DuckDbTypeAndExample<>(type, example, false, supportsTextRoundtrip);
    }

    public DuckDbTypeAndExample<A> noTextRoundtrip() {
      return new DuckDbTypeAndExample<>(type, example, hasIdentity, false);
    }
  }

  // Sample enum for ENUM type testing
  enum Color {
    RED,
    GREEN,
    BLUE
  }

  // HUGEINT range: -170141183460469231731687303715884105728 to
  // 170141183460469231731687303715884105727
  BigInteger HUGEINT_MAX = new BigInteger("170141183460469231731687303715884105727");
  BigInteger HUGEINT_MIN = new BigInteger("-170141183460469231731687303715884105728");
  // UHUGEINT range: 0 to 340282366920938463463374607431768211455
  BigInteger UHUGEINT_MAX = new BigInteger("340282366920938463463374607431768211455");
  // UBIGINT range: 0 to 18446744073709551615
  BigInteger UBIGINT_MAX = new BigInteger("18446744073709551615");

  List<DuckDbTypeAndExample<?>> All =
      List.of(
          // ==================== Integer Types (Signed) ====================
          new DuckDbTypeAndExample<>(DuckDbTypes.tinyint, (byte) 42),
          new DuckDbTypeAndExample<>(DuckDbTypes.tinyint, Byte.MIN_VALUE),
          new DuckDbTypeAndExample<>(DuckDbTypes.tinyint, Byte.MAX_VALUE),
          new DuckDbTypeAndExample<>(DuckDbTypes.tinyint, (byte) 0),
          new DuckDbTypeAndExample<>(DuckDbTypes.smallint, (short) 4242),
          new DuckDbTypeAndExample<>(DuckDbTypes.smallint, Short.MIN_VALUE),
          new DuckDbTypeAndExample<>(DuckDbTypes.smallint, Short.MAX_VALUE),
          new DuckDbTypeAndExample<>(DuckDbTypes.smallint, (short) 0),
          new DuckDbTypeAndExample<>(DuckDbTypes.integer, 42424242),
          new DuckDbTypeAndExample<>(DuckDbTypes.integer, Integer.MIN_VALUE),
          new DuckDbTypeAndExample<>(DuckDbTypes.integer, Integer.MAX_VALUE),
          new DuckDbTypeAndExample<>(DuckDbTypes.integer, 0),
          new DuckDbTypeAndExample<>(DuckDbTypes.bigint, 4242424242424242L),
          new DuckDbTypeAndExample<>(DuckDbTypes.bigint, Long.MIN_VALUE),
          new DuckDbTypeAndExample<>(DuckDbTypes.bigint, Long.MAX_VALUE),
          new DuckDbTypeAndExample<>(DuckDbTypes.bigint, 0L),
          // HUGEINT - 128-bit signed integer
          new DuckDbTypeAndExample<>(DuckDbTypes.hugeint, BigInteger.valueOf(12345678901234567L)),
          new DuckDbTypeAndExample<>(DuckDbTypes.hugeint, HUGEINT_MAX),
          new DuckDbTypeAndExample<>(DuckDbTypes.hugeint, HUGEINT_MIN),
          new DuckDbTypeAndExample<>(DuckDbTypes.hugeint, BigInteger.ZERO),

          // ==================== Integer Types (Unsigned) ====================
          new DuckDbTypeAndExample<>(DuckDbTypes.utinyint, (short) 255),
          new DuckDbTypeAndExample<>(DuckDbTypes.utinyint, (short) 0),
          new DuckDbTypeAndExample<>(DuckDbTypes.usmallint, 65535),
          new DuckDbTypeAndExample<>(DuckDbTypes.usmallint, 0),
          new DuckDbTypeAndExample<>(DuckDbTypes.uinteger, 4294967295L),
          new DuckDbTypeAndExample<>(DuckDbTypes.uinteger, 0L),
          // UBIGINT - 64-bit unsigned integer
          new DuckDbTypeAndExample<>(DuckDbTypes.ubigint, UBIGINT_MAX),
          new DuckDbTypeAndExample<>(DuckDbTypes.ubigint, BigInteger.ZERO),
          // UHUGEINT - 128-bit unsigned integer
          new DuckDbTypeAndExample<>(DuckDbTypes.uhugeint, UHUGEINT_MAX),
          new DuckDbTypeAndExample<>(DuckDbTypes.uhugeint, BigInteger.ZERO),

          // ==================== Floating-Point Types ====================
          new DuckDbTypeAndExample<>(DuckDbTypes.float_, 3.14159f).noIdentity(),
          new DuckDbTypeAndExample<>(DuckDbTypes.float_, 0.0f).noIdentity(),
          new DuckDbTypeAndExample<>(DuckDbTypes.float_, Float.MAX_VALUE).noIdentity(),
          new DuckDbTypeAndExample<>(DuckDbTypes.float_, Float.MIN_VALUE).noIdentity(),
          new DuckDbTypeAndExample<>(DuckDbTypes.double_, 3.141592653589793),
          new DuckDbTypeAndExample<>(DuckDbTypes.double_, 0.0),
          new DuckDbTypeAndExample<>(DuckDbTypes.double_, -3.141592653589793),
          new DuckDbTypeAndExample<>(DuckDbTypes.double_, Double.MAX_VALUE),

          // ==================== Fixed-Point Types ====================
          new DuckDbTypeAndExample<>(DuckDbTypes.decimal, new BigDecimal("12345")),
          new DuckDbTypeAndExample<>(DuckDbTypes.decimal, BigDecimal.ZERO),
          new DuckDbTypeAndExample<>(DuckDbTypes.decimal, new BigDecimal("-9999999999")),
          new DuckDbTypeAndExample<>(DuckDbTypes.decimal(10, 2), new BigDecimal("12345678.90")),
          new DuckDbTypeAndExample<>(DuckDbTypes.decimal(10, 2), new BigDecimal("0.00")),
          new DuckDbTypeAndExample<>(DuckDbTypes.decimal(10, 2), new BigDecimal("-99999999.99")),
          new DuckDbTypeAndExample<>(DuckDbTypes.decimal(10, 5), new BigDecimal("12345.67890")),

          // ==================== Boolean Type ====================
          new DuckDbTypeAndExample<>(DuckDbTypes.boolean_, true),
          new DuckDbTypeAndExample<>(DuckDbTypes.boolean_, false),

          // ==================== String Types ====================
          new DuckDbTypeAndExample<>(DuckDbTypes.varchar, "Hello, DuckDB!"),
          new DuckDbTypeAndExample<>(DuckDbTypes.varchar, ""),
          new DuckDbTypeAndExample<>(
              DuckDbTypes.varchar, "Unicode: \u00e9\u00e8\u00ea \u4e2d\u6587"),
          new DuckDbTypeAndExample<>(DuckDbTypes.varchar, "Line1\nLine2\tTabbed"),
          new DuckDbTypeAndExample<>(DuckDbTypes.varchar, "Quote\"Test'Single\\Back"),
          new DuckDbTypeAndExample<>(
              DuckDbTypes.varchar, "Emoji: \uD83D\uDE00\uD83C\uDF89\uD83D\uDE80"),
          new DuckDbTypeAndExample<>(DuckDbTypes.varchar(100), "Fixed length varchar"),
          new DuckDbTypeAndExample<>(DuckDbTypes.text, "Text type content"),
          new DuckDbTypeAndExample<>(DuckDbTypes.char_(10), "hello"),

          // ==================== Binary Types ====================
          // BLOB: Binary data cannot roundtrip through textual JSON COPY format
          // JSON treats base64 strings as VARCHAR literals, storing text instead of binary.
          // Use Parquet/Arrow formats for proper binary data streaming.
          new DuckDbTypeAndExample<>(DuckDbTypes.blob, new byte[] {0x01, 0x02, 0x03, 0x04, 0x05})
              .noTextRoundtrip(),
          new DuckDbTypeAndExample<>(DuckDbTypes.blob, new byte[] {}).noTextRoundtrip(),
          new DuckDbTypeAndExample<>(
                  DuckDbTypes.blob, new byte[] {(byte) 0xFF, 0x00, 0x7F, (byte) 0x80})
              .noTextRoundtrip(),
          new DuckDbTypeAndExample<>(DuckDbTypes.blob, new byte[] {0x00}).noTextRoundtrip(),

          // ==================== Date/Time Types ====================
          new DuckDbTypeAndExample<>(DuckDbTypes.date, LocalDate.of(2024, 6, 15)),
          new DuckDbTypeAndExample<>(DuckDbTypes.date, LocalDate.of(1970, 1, 1)),
          new DuckDbTypeAndExample<>(DuckDbTypes.date, LocalDate.of(2099, 12, 31)),
          new DuckDbTypeAndExample<>(DuckDbTypes.time, LocalTime.of(14, 30, 45)),
          new DuckDbTypeAndExample<>(DuckDbTypes.time, LocalTime.of(0, 0, 0)),
          new DuckDbTypeAndExample<>(DuckDbTypes.time, LocalTime.of(23, 59, 59)),
          new DuckDbTypeAndExample<>(
              DuckDbTypes.timestamp, LocalDateTime.of(2024, 6, 15, 14, 30, 45)),
          new DuckDbTypeAndExample<>(DuckDbTypes.timestamp, LocalDateTime.of(1970, 1, 1, 0, 0, 0)),
          new DuckDbTypeAndExample<>(
              DuckDbTypes.timestamp, LocalDateTime.of(2024, 6, 15, 14, 30, 45, 123456000)),
          // Timestamp with timezone
          new DuckDbTypeAndExample<>(
              DuckDbTypes.timestamptz,
              OffsetDateTime.of(2024, 6, 15, 14, 30, 45, 0, ZoneOffset.UTC)),

          // ==================== Interval Type ====================
          new DuckDbTypeAndExample<>(DuckDbTypes.interval, Duration.ofHours(2).plusMinutes(30)),
          new DuckDbTypeAndExample<>(DuckDbTypes.interval, Duration.ofDays(5)),

          // ==================== UUID Type ====================
          new DuckDbTypeAndExample<>(
              DuckDbTypes.uuid, UUID.fromString("550e8400-e29b-41d4-a716-446655440000")),
          new DuckDbTypeAndExample<>(
              DuckDbTypes.uuid, UUID.fromString("00000000-0000-0000-0000-000000000000")),

          // ==================== JSON Type ====================
          new DuckDbTypeAndExample<>(
                  DuckDbTypes.json, new Json("{\"name\": \"DuckDB\", \"version\": 1.0}"))
              .noIdentity(),
          new DuckDbTypeAndExample<>(DuckDbTypes.json, new Json("[1, 2, 3, \"four\"]"))
              .noIdentity(),
          new DuckDbTypeAndExample<>(DuckDbTypes.json, new Json("{}")).noIdentity(),

          // ==================== ENUM Type ====================
          new DuckDbTypeAndExample<>(DuckDbTypes.ofEnum("color_enum", Color::valueOf), Color.GREEN),
          new DuckDbTypeAndExample<>(DuckDbTypes.ofEnum("color_enum", Color::valueOf), Color.RED),

          // ==================== LIST Types ====================
          // LIST types don't support direct equality in WHERE clauses, so we mark noIdentity()
          // Native JNI types (best performance)
          new DuckDbTypeAndExample<>(DuckDbTypes.listBoolean, List.of(true, false, true))
              .noIdentity(),
          new DuckDbTypeAndExample<>(
                  DuckDbTypes.listTinyint, List.of((byte) 1, (byte) 2, (byte) -1))
              .noIdentity(),
          new DuckDbTypeAndExample<>(DuckDbTypes.listSmallint, List.of((short) 100, (short) -200))
              .noIdentity(),
          new DuckDbTypeAndExample<>(DuckDbTypes.listInteger, List.of(1, 2, 3, 4, 5)).noIdentity(),
          new DuckDbTypeAndExample<>(DuckDbTypes.listInteger, List.of()).noIdentity(),
          new DuckDbTypeAndExample<>(DuckDbTypes.listInteger, List.of(-100, 0, 100)).noIdentity(),
          new DuckDbTypeAndExample<>(DuckDbTypes.listBigint, List.of(1L, 2L, 9999999999L))
              .noIdentity(),
          new DuckDbTypeAndExample<>(DuckDbTypes.listFloat, List.of(1.5f, 2.5f, 3.14f))
              .noIdentity(),
          new DuckDbTypeAndExample<>(DuckDbTypes.listDouble, List.of(1.5, 2.5, 3.14159))
              .noIdentity(),
          new DuckDbTypeAndExample<>(DuckDbTypes.listVarchar, List.of("hello", "world"))
              .noIdentity(),
          new DuckDbTypeAndExample<>(DuckDbTypes.listVarchar, List.of("quote'test", "back\\slash"))
              .noIdentity(),

          // ==================== MAP Types ====================
          // MAP types don't support direct equality in WHERE clauses, so we mark noIdentity()
          new DuckDbTypeAndExample<>(
                  DuckDbTypes.mapVarcharInteger(), java.util.Map.of("a", 1, "b", 2))
              .noIdentity(),
          new DuckDbTypeAndExample<>(
                  DuckDbTypes.mapVarcharVarchar(),
                  java.util.Map.of("key1", "value1", "key2", "value2"))
              .noIdentity(),
          new DuckDbTypeAndExample<>(
                  DuckDbTypes.mapIntegerVarchar(), java.util.Map.of(1, "one", 2, "two"))
              .noIdentity(),
          // MAP with UUID keys (natively supported)
          new DuckDbTypeAndExample<>(
                  DuckDbTypes.mapUuidVarchar(),
                  java.util.Map.of(
                      UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), "value1",
                      UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), "value2"))
              .noIdentity(),
          // MAP with TIME values (requires String conversion)
          new DuckDbTypeAndExample<>(
                  DuckDbTypes.mapVarcharTime(),
                  java.util.Map.of(
                      "morning", LocalTime.of(8, 15, 0),
                      "afternoon", LocalTime.of(14, 30, 45)))
              .noIdentity(),
          // MAP with UUID keys and TIME values (mixed conversion)
          new DuckDbTypeAndExample<>(
                  DuckDbTypes.mapUuidTime(),
                  java.util.Map.of(
                      UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                          LocalTime.of(14, 30, 45),
                      UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                          LocalTime.of(8, 15, 0)))
              .noIdentity(),

          // ==================== LIST Types with complex element types ====================
          // String-converted types (~33% overhead at 100k rows, but required for correctness)
          // LIST<UUID> - UUID requires String conversion to avoid byte-ordering bug
          new DuckDbTypeAndExample<>(
                  DuckDbTypes.listUuid,
                  List.of(
                      UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                      UUID.fromString("123e4567-e89b-12d3-a456-426614174000")))
              .noIdentity(),
          // LIST<TIME> - LocalTime requires String conversion (JNI doesn't recognize
          // java.time.LocalTime)
          new DuckDbTypeAndExample<>(
                  DuckDbTypes.listTime, List.of(LocalTime.of(14, 30, 45), LocalTime.of(8, 15, 0)))
              .noIdentity(),
          // LIST<DATE> - LocalDate requires String conversion
          new DuckDbTypeAndExample<>(
                  DuckDbTypes.listDate,
                  List.of(LocalDate.of(2024, 6, 15), LocalDate.of(1970, 1, 1)))
              .noIdentity(),
          // LIST<TIMESTAMP> - LocalDateTime requires String conversion
          new DuckDbTypeAndExample<>(
                  DuckDbTypes.listTimestamp,
                  List.of(
                      LocalDateTime.of(2024, 6, 15, 14, 30, 45),
                      LocalDateTime.of(1970, 1, 1, 0, 0, 0)))
              .noIdentity(),
          // LIST<DECIMAL> - BigDecimal requires String conversion
          new DuckDbTypeAndExample<>(
                  DuckDbTypes.listDecimal,
                  List.of(
                      new java.math.BigDecimal("123.456"), new java.math.BigDecimal("-99999.99")))
              .noIdentity(),
          // LIST<HUGEINT> - BigInteger requires String conversion
          new DuckDbTypeAndExample<>(
                  DuckDbTypes.listHugeint,
                  List.of(
                      new java.math.BigInteger("170141183460469231731687303715884105727"),
                      java.math.BigInteger.ZERO))
              .noIdentity(),

          // ==================== STRUCT Types ====================
          new DuckDbTypeAndExample<>(personType, new Person("Alice", 30)).noIdentity(),
          new DuckDbTypeAndExample<>(personType, new Person("Bob", 25)).noIdentity(),

          // ==================== UNION Types ====================
          new DuckDbTypeAndExample<>(intOrStringType, new IntOrString.Num(42))
              .noIdentity()
              .noTextRoundtrip(),
          new DuckDbTypeAndExample<>(intOrStringType, new IntOrString.Str("hello"))
              .noIdentity()
              .noTextRoundtrip(),

          // ==================== ARRAY Types (Fixed-Size) ====================
          new DuckDbTypeAndExample<>(
                  DuckDbTypes.float_.arrayNative(3, Float.class, Float[]::new),
                  List.of(1.0f, 2.0f, 3.0f))
              .noIdentity(),
          new DuckDbTypeAndExample<>(
                  DuckDbTypes.integer.arrayNative(5, Integer.class, Integer[]::new),
                  List.of(1, 2, 3, 4, 5))
              .noIdentity(),

          // ==================== LIST Variable Length Cases ====================
          // Prove that LIST (unlike ARRAY) accepts variable lengths
          new DuckDbTypeAndExample<>(DuckDbTypes.listInteger, List.of()).noIdentity(),
          new DuckDbTypeAndExample<>(DuckDbTypes.listInteger, List.of(1, 2)).noIdentity(),
          new DuckDbTypeAndExample<>(DuckDbTypes.listInteger, List.of(1, 2, 3, 4, 5)).noIdentity());

  // Connection helper for DuckDB - uses in-memory database
  static <T> T withConnection(SqlFunction<Connection, T> f) {
    try (var conn = java.sql.DriverManager.getConnection("jdbc:duckdb:")) {
      conn.setAutoCommit(false);
      // Create the enum type for testing
      conn.createStatement().execute("CREATE TYPE color_enum AS ENUM ('RED', 'GREEN', 'BLUE')");
      try {
        return f.apply(conn);
      } finally {
        conn.rollback();
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  // Main entry point
  static void main(String[] args) {
    System.out.println("Testing DuckDB type codecs...\n");

    // Test JSON roundtrip first (no database connection needed)
    System.out.println("=== JSON Roundtrip Tests ===");
    for (DuckDbTypeAndExample<?> t : All) {
      testJsonRoundtrip(t);
    }
    System.out.println();

    // Test Text encoding (no database connection needed)
    System.out.println("=== Text Encoding Tests ===");
    for (DuckDbTypeAndExample<?> t : All) {
      testTextEncoding(t);
    }
    System.out.println();

    withConnection(
        conn -> {
          int passed = 0;
          int failed = 0;

          // Test native type roundtrip via JDBC
          System.out.println("=== Native Type Roundtrip Tests (JDBC) ===");
          for (DuckDbTypeAndExample<?> t : All) {
            String typeName = t.type.typename().sqlType();
            try {
              System.out.println(
                  "Testing " + typeName + " with example '" + format(t.example) + "'");
              testCase(conn, t);
              System.out.println("  PASSED\n");
              passed++;
            } catch (Exception e) {
              System.out.println("  FAILED: " + e.getMessage() + "\n");
              e.printStackTrace();
              failed++;
            }
          }

          // Test text roundtrip via JSON COPY
          System.out.println("\n=== Text Roundtrip Tests (JSON COPY) ===");
          for (DuckDbTypeAndExample<?> t : All) {
            String typeName = t.type.typename().sqlType();
            try {
              testTextRoundtrip(conn, t);
              System.out.println("  PASSED\n");
              passed++;
            } catch (UnsupportedOperationException e) {
              System.out.println("Text roundtrip " + typeName + ": NOT SUPPORTED\n");
              // Don't count as failure
            } catch (Exception e) {
              System.out.println("  FAILED: " + e.getMessage() + "\n");
              e.printStackTrace();
              failed++;
            }
          }

          // ==================== Edge Case Tests ====================
          System.out.println("\n=== Negative Test: ARRAY Size Validation ===\n");

          // Test ARRAY size validation - DuckDB should reject wrong-sized arrays
          // Expected error: "Conversion Error: Cannot cast array of size 4 to array of size 5"
          System.out.println("ARRAY[5] with 4 values (expected: size mismatch error)");
          try (Connection negConn = java.sql.DriverManager.getConnection("jdbc:duckdb:");
              Statement stmt = negConn.createStatement()) {
            stmt.execute("CREATE TABLE array_size_test (vec INTEGER[5])");
            stmt.execute(
                "INSERT INTO array_size_test VALUES (array_value(1, 2, 3, 4)::INTEGER[5])");
            System.out.println("  FAILED - should have thrown size mismatch error\n");
            failed++;
          } catch (SQLException e) {
            String errMsg = e.getMessage();
            if (errMsg.contains("Conversion Error") || errMsg.contains("Cannot cast array")) {
              System.out.println(
                  "  PASSED - got expected error: "
                      + errMsg.substring(0, Math.min(100, errMsg.length()))
                      + "...\n");
              passed++;
            } else {
              System.out.println("  FAILED - got unexpected error: " + errMsg + "\n");
              failed++;
            }
          }

          int totalTests =
              All.size() * 2 + 1; // Each item: JSON + native roundtrip, plus 1 negative test
          System.out.println("\n=====================================");
          System.out.println(
              "Results: "
                  + passed
                  + " passed, "
                  + failed
                  + " failed out of "
                  + totalTests
                  + " tests");
          System.out.println("=====================================");

          if (failed > 0) {
            throw new RuntimeException(failed + " tests failed");
          }

          return null;
        });
  }

  static <A> void testJsonRoundtrip(DuckDbTypeAndExample<A> t) {
    try {
      DuckDbJson<A> jsonCodec = t.type.duckDbJson();
      A original = t.example;

      // Test toJson -> encode -> parse -> fromJson roundtrip (in-memory)
      JsonValue jsonValue = jsonCodec.toJson(original);
      String encoded = jsonValue.encode();
      JsonValue parsed = JsonValue.parse(encoded);
      A decoded = jsonCodec.fromJson(parsed);

      System.out.println(
          "JSON roundtrip "
              + t.type.typename().sqlType()
              + ": "
              + format(original)
              + " -> "
              + encoded
              + " -> "
              + format(decoded));

      if (t.hasIdentity && !areEqual(decoded, original)) {
        throw new RuntimeException(
            "JSON roundtrip failed for "
                + t.type.typename().sqlType()
                + ": expected '"
                + format(original)
                + "' but got '"
                + format(decoded)
                + "'");
      }
    } catch (Exception e) {
      throw new RuntimeException(
          "JSON roundtrip test failed for " + t.type.typename().sqlType(), e);
    }
  }

  static <A> void testTextEncoding(DuckDbTypeAndExample<A> t) {
    try {
      DbText<A> textCodec = t.type.text();
      A original = t.example;

      // Test text encoding (format to CSV string)
      StringBuilder sb = new StringBuilder();
      textCodec.unsafeEncode(original, sb);
      String encoded = sb.toString();

      System.out.println(
          "Text encoding "
              + t.type.typename().sqlType()
              + ": "
              + format(original)
              + " -> "
              + encoded);

    } catch (UnsupportedOperationException e) {
      // Some types don't support text encoding yet
      System.out.println("Text encoding " + t.type.typename().sqlType() + ": NOT SUPPORTED");
    } catch (Exception e) {
      throw new RuntimeException("Text encoding test failed for " + t.type.typename().sqlType(), e);
    }
  }

  static <A> void testTextRoundtrip(Connection conn, DuckDbTypeAndExample<A> t) throws Exception {
    if (!t.supportsTextRoundtrip) {
      System.out.println(
          "Text roundtrip "
              + t.type.typename().sqlType()
              + ": SKIPPED (not supported for this type)");
      return;
    }

    String sqlType = t.type.typename().sqlType();
    DuckDbJson<A> jsonCodec = t.type.duckDbJson();
    A original = t.example;

    // Encode to JSON (simpler than CSV for complex types!)
    String jsonValue = jsonCodec.toJson(original).encode();

    // Use unique table name to avoid conflicts if previous test failed
    String tableName = "text_test_" + System.nanoTime();

    // Create temp table
    conn.createStatement().execute("CREATE TEMPORARY TABLE " + tableName + " (v " + sqlType + ")");

    try {
      // Write JSON to temp file (one value per line - newline-delimited JSON)
      // DuckDB expects each line to be a JSON object matching the table structure
      java.io.File tempFile = java.io.File.createTempFile("duckdb_test_", ".json");
      tempFile.deleteOnExit();
      String jsonLine = "{\"v\":" + jsonValue + "}\n";
      java.nio.file.Files.writeString(tempFile.toPath(), jsonLine);

      // Import JSON using COPY
      // DuckDB can parse JSON directly!
      String copyCommand =
          "COPY " + tableName + " FROM '" + tempFile.getAbsolutePath() + "' (FORMAT JSON)";
      try {
        conn.createStatement().execute(copyCommand);
      } catch (SQLException e) {
        throw new RuntimeException(
            "COPY command failed: " + copyCommand + "\nJSON line: " + jsonLine, e);
      }

      // Read back the value
      var rs = conn.createStatement().executeQuery("SELECT v FROM " + tableName);
      if (!rs.next()) {
        throw new RuntimeException("No rows returned from JSON import");
      }
      A decoded = t.type.read().read(rs, 1);
      rs.close();

      // Verify roundtrip
      if (t.hasIdentity && !areEqual(decoded, original)) {
        throw new RuntimeException(
            "Text roundtrip failed: expected '"
                + format(original)
                + "' but got '"
                + format(decoded)
                + "'");
      }

      System.out.println(
          "Text roundtrip "
              + sqlType
              + ": "
              + format(original)
              + " -> "
              + jsonValue
              + " -> "
              + format(decoded));

    } finally {
      try {
        conn.createStatement().execute("DROP TABLE IF EXISTS " + tableName);
      } catch (SQLException e) {
        // Ignore cleanup errors - transaction might be aborted
      }
    }
  }

  static <A> void testCase(Connection conn, DuckDbTypeAndExample<A> t) throws SQLException {
    String sqlType = t.type.typename().sqlType();

    // Create temp table
    conn.createStatement().execute("CREATE TEMPORARY TABLE test_table (v " + sqlType + ")");

    try {
      // Insert using PreparedStatement
      var insert = conn.prepareStatement("INSERT INTO test_table (v) VALUES (?)");
      A expected = t.example;
      t.type.write().set(insert, 1, expected);
      insert.execute();
      insert.close();

      // Select and verify
      final PreparedStatement select;
      if (t.hasIdentity) {
        select = conn.prepareStatement("SELECT v, NULL FROM test_table WHERE v = ?");
        t.type.write().set(select, 1, expected);
      } else {
        select = conn.prepareStatement("SELECT v, NULL FROM test_table");
      }

      select.execute();
      var rs = select.getResultSet();

      if (!rs.next()) {
        throw new RuntimeException("No rows returned");
      }

      // Read the value
      A actual = t.type.read().read(rs, 1);
      // Read the null value using opt()
      Optional<A> actualNull = t.type.opt().read().read(rs, 2);

      select.close();

      assertEquals(actual, expected, "value mismatch");
      assertEquals(actualNull, Optional.empty(), "null value mismatch");

    } finally {
      // Drop temp table
      conn.createStatement().execute("DROP TABLE IF EXISTS test_table");
    }
  }

  static <A> void assertEquals(A actual, A expected, String message) {
    if (!areEqual(actual, expected)) {
      throw new RuntimeException(
          message + ": actual='" + format(actual) + "' expected='" + format(expected) + "'");
    }
  }

  static <A> boolean areEqual(A actual, A expected) {
    if (expected == null && actual == null) return true;
    if (expected == null || actual == null) return false;

    if (expected instanceof byte[]) {
      return Arrays.equals((byte[]) actual, (byte[]) expected);
    }
    if (expected instanceof Object[]) {
      return Arrays.deepEquals((Object[]) actual, (Object[]) expected);
    }
    // BigDecimal: compare by value, not scale
    if (expected instanceof BigDecimal && actual instanceof BigDecimal) {
      return ((BigDecimal) actual).compareTo((BigDecimal) expected) == 0;
    }
    // List: compare element by element
    if (expected instanceof List<?> && actual instanceof List<?>) {
      List<?> expectedList = (List<?>) expected;
      List<?> actualList = (List<?>) actual;
      if (expectedList.size() != actualList.size()) return false;
      for (int i = 0; i < expectedList.size(); i++) {
        if (!areEqual(actualList.get(i), expectedList.get(i))) return false;
      }
      return true;
    }
    // Map: compare entries
    if (expected instanceof java.util.Map<?, ?> && actual instanceof java.util.Map<?, ?>) {
      java.util.Map<?, ?> expectedMap = (java.util.Map<?, ?>) expected;
      java.util.Map<?, ?> actualMap = (java.util.Map<?, ?>) actual;
      if (expectedMap.size() != actualMap.size()) return false;
      for (var entry : expectedMap.entrySet()) {
        Object actualValue = actualMap.get(entry.getKey());
        if (!areEqual(actualValue, entry.getValue())) return false;
      }
      return true;
    }

    return actual.equals(expected);
  }

  static <A> String format(A a) {
    if (a == null) return "null";
    if (a instanceof byte[]) {
      return bytesToHex((byte[]) a);
    }
    if (a instanceof Object[]) {
      return Arrays.deepToString((Object[]) a);
    }
    return a.toString();
  }

  static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (int i = 0; i < bytes.length; i++) {
      if (i > 0) sb.append(", ");
      sb.append(String.format("0x%02X", bytes[i]));
    }
    sb.append("]");
    return sb.toString();
  }
}

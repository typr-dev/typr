package dev.typr.foundations;

import dev.typr.foundations.connect.oracle.OracleConfig;
import dev.typr.foundations.data.Json;
import dev.typr.foundations.data.JsonValue;
import dev.typr.foundations.data.OracleIntervalDS;
import dev.typr.foundations.data.OracleIntervalYM;
import dev.typr.foundations.hikari.HikariDataSourceFactory;
import dev.typr.foundations.hikari.PoolConfig;
import dev.typr.foundations.hikari.PooledDataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

/** Tests for Oracle type codecs. Tests all types defined in OracleTypes. */
public class OracleTypeTest {

  private static final AtomicInteger tableCounter = new AtomicInteger(0);

  // Connection pool with limited size to avoid exhausting Oracle Free's connection limit
  private static final PooledDataSource POOL;

  static {
    java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("GMT+03:00"));
    var config =
        OracleConfig.builder("localhost", 1521, "FREEPDB1", "typr", "typr_password")
            .serviceName("FREEPDB1")
            .build();
    var poolConfig = PoolConfig.builder().maximumPoolSize(5).build();
    POOL = HikariDataSourceFactory.create(config, poolConfig);
  }

  private static String uniqueTableName(String prefix) {
    return prefix + "_" + tableCounter.incrementAndGet();
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Test Data Types for Object-Relational Types
  // ═══════════════════════════════════════════════════════════════════════════

  /** Address - corresponds to Oracle type address_t */
  record Coordinates(BigDecimal latitude, BigDecimal longitude) {}

  record Address(String street, String city, Coordinates location) {}

  // Helper to build COORDINATES_T type
  static OracleType<Coordinates> coordinatesType() {
    return OracleObject.<Coordinates>builder("COORDINATES_T")
        .addAttribute("LATITUDE", OracleTypes.number(9, 6), Coordinates::latitude)
        .addAttribute("LONGITUDE", OracleTypes.number(9, 6), Coordinates::longitude)
        .build(attrs -> new Coordinates((BigDecimal) attrs[0], (BigDecimal) attrs[1]))
        .asType();
  }

  // Helper to build ADDRESS_T type (with nested COORDINATES_T)
  static OracleType<Address> addressType() {
    return OracleObject.<Address>builder("ADDRESS_T")
        .addAttribute("STREET", OracleTypes.varchar2(100), Address::street)
        .addAttribute("CITY", OracleTypes.varchar2(50), Address::city)
        .addAttribute("LOCATION", coordinatesType(), Address::location)
        .build(attrs -> new Address((String) attrs[0], (String) attrs[1], (Coordinates) attrs[2]))
        .asType();
  }

  // Example coordinates
  static Coordinates coords(String lat, String lon) {
    return new Coordinates(new BigDecimal(lat), new BigDecimal(lon));
  }

  /** OrderItem - corresponds to Oracle type order_item_t */
  record OrderItem(Long productId, Integer quantity) {}

  /** AllTypesStruct - comprehensive struct containing all Oracle types (NOT NULL fields) */
  record AllTypesStruct(
      String varcharField,
      String nvarcharField,
      String charField,
      String ncharField,
      BigDecimal numberField,
      Integer numberIntField,
      Long numberLongField,
      Float binaryFloatField,
      Double binaryDoubleField,
      LocalDateTime dateField,
      LocalDateTime timestampField,
      OffsetDateTime timestampTzField,
      OffsetDateTime timestampLtzField,
      OracleIntervalYM intervalYmField,
      OracleIntervalDS intervalDsField,
      Address nestedObjectField,
      List<String> varrayField) {}

  /** AllTypesStructOptional - comprehensive struct containing all Oracle types (all nullable) */
  record AllTypesStructOptional(
      Optional<String> varcharField,
      Optional<String> nvarcharField,
      Optional<String> charField,
      Optional<String> ncharField,
      Optional<BigDecimal> numberField,
      Optional<Integer> numberIntField,
      Optional<Long> numberLongField,
      Optional<Float> binaryFloatField,
      Optional<Double> binaryDoubleField,
      Optional<LocalDateTime> dateField,
      Optional<LocalDateTime> timestampField,
      Optional<OffsetDateTime> timestampTzField,
      Optional<OffsetDateTime> timestampLtzField,
      Optional<OracleIntervalYM> intervalYmField,
      Optional<OracleIntervalDS> intervalDsField,
      Optional<Address> nestedObjectField,
      Optional<List<String>> varrayField) {}

  /**
   * AllTypesStructNoLobs - Oracle types without LOBs (for VARRAY compatibility) Oracle restriction:
   * VARRAYs cannot contain structs with embedded LOB types or nested tables
   */
  record AllTypesStructNoLobs(
      String varcharField,
      String nvarcharField,
      String charField,
      String ncharField,
      BigDecimal numberField,
      Integer numberIntField,
      Long numberLongField,
      Float binaryFloatField,
      Double binaryDoubleField,
      LocalDateTime dateField,
      LocalDateTime timestampField,
      OffsetDateTime timestampTzField,
      OffsetDateTime timestampLtzField,
      OracleIntervalYM intervalYmField,
      OracleIntervalDS intervalDsField,
      Address nestedObjectField,
      List<String> varrayField) {}

  /** AllTypesStructNoLobsOptional - Optional variant without LOBs (for VARRAY compatibility) */
  record AllTypesStructNoLobsOptional(
      Optional<String> varcharField,
      Optional<String> nvarcharField,
      Optional<String> charField,
      Optional<String> ncharField,
      Optional<BigDecimal> numberField,
      Optional<Integer> numberIntField,
      Optional<Long> numberLongField,
      Optional<Float> binaryFloatField,
      Optional<Double> binaryDoubleField,
      Optional<LocalDateTime> dateField,
      Optional<LocalDateTime> timestampField,
      Optional<OffsetDateTime> timestampTzField,
      Optional<OffsetDateTime> timestampLtzField,
      Optional<OracleIntervalYM> intervalYmField,
      Optional<OracleIntervalDS> intervalDsField,
      Optional<Address> nestedObjectField,
      Optional<List<String>> varrayField) {}

  // ═══════════════════════════════════════════════════════════════════════════

  record OracleTypeAndExample<A>(
      OracleType<A> type,
      A example,
      A expectedRoundtrip, // Nullable - the expected value after roundtrip (may be null for SQL
      // NULL)
      boolean useExpectedRoundtrip, // If true, use expectedRoundtrip; if false, use example
      boolean hasIdentity,
      boolean streamingWorks,
      boolean jsonRoundtripWorks,
      List<String>
          setupSql // Optional SQL statements to run before test (for type definitions, etc.)
      ) {
    public OracleTypeAndExample(OracleType<A> type, A example) {
      this(type, example, null, false, true, true, true, List.of());
    }

    public OracleTypeAndExample(OracleType<A> type, A example, A expectedRoundtrip) {
      this(type, example, expectedRoundtrip, true, true, true, true, List.of());
    }

    public OracleTypeAndExample(OracleType<A> type, A example, List<String> setupSql) {
      this(type, example, null, false, true, true, true, setupSql);
    }

    public OracleTypeAndExample<A> noStreaming() {
      return new OracleTypeAndExample<>(
          type,
          example,
          expectedRoundtrip,
          useExpectedRoundtrip,
          hasIdentity,
          false,
          jsonRoundtripWorks,
          setupSql);
    }

    public OracleTypeAndExample<A> noIdentity() {
      return new OracleTypeAndExample<>(
          type,
          example,
          expectedRoundtrip,
          useExpectedRoundtrip,
          false,
          streamingWorks,
          jsonRoundtripWorks,
          setupSql);
    }

    public OracleTypeAndExample<A> noJsonRoundtrip() {
      return new OracleTypeAndExample<>(
          type,
          example,
          expectedRoundtrip,
          useExpectedRoundtrip,
          hasIdentity,
          streamingWorks,
          false,
          setupSql);
    }

    public A expected() {
      return useExpectedRoundtrip ? expectedRoundtrip : example;
    }
  }

  List<OracleTypeAndExample<?>> All =
      List.<OracleTypeAndExample<?>>of(
          // ═══════════════════════════════════════════════════════════════════════════
          // Numeric Types
          // ═══════════════════════════════════════════════════════════════════════════

          // NUMBER - universal numeric type
          new OracleTypeAndExample<>(OracleTypes.number, new BigDecimal("12345.6789")),
          new OracleTypeAndExample<>(OracleTypes.number, BigDecimal.ZERO), // Edge case: zero
          new OracleTypeAndExample<>(
              OracleTypes.number, new BigDecimal("-9999999999.999999")), // Edge case: negative
          new OracleTypeAndExample<>(
              OracleTypes.number, new BigDecimal("0.00000001")), // Edge case: small value

          // NUMBER as Integer
          new OracleTypeAndExample<>(OracleTypes.numberInt, 42),
          new OracleTypeAndExample<>(OracleTypes.numberInt, Integer.MIN_VALUE),
          new OracleTypeAndExample<>(OracleTypes.numberInt, Integer.MAX_VALUE),
          new OracleTypeAndExample<>(OracleTypes.numberInt, 0),

          // NUMBER as Long
          new OracleTypeAndExample<>(OracleTypes.numberLong, 424242424242L),
          new OracleTypeAndExample<>(OracleTypes.numberLong, Long.MIN_VALUE),
          new OracleTypeAndExample<>(OracleTypes.numberLong, Long.MAX_VALUE),
          new OracleTypeAndExample<>(OracleTypes.numberLong, 0L),

          // NUMBER with precision and scale
          new OracleTypeAndExample<>(OracleTypes.number(10, 2), new BigDecimal("12345678.90")),
          new OracleTypeAndExample<>(OracleTypes.number(10, 2), new BigDecimal("-99999999.99")),
          new OracleTypeAndExample<>(
              OracleTypes.number(38, 10),
              new BigDecimal("1234567890123456789012345678.1234567890")),

          // BINARY_FLOAT - 32-bit IEEE 754
          new OracleTypeAndExample<>(OracleTypes.binaryFloat, 3.14159f),
          new OracleTypeAndExample<>(OracleTypes.binaryFloat, 0.0f),
          new OracleTypeAndExample<>(OracleTypes.binaryFloat, Float.MIN_VALUE),
          new OracleTypeAndExample<>(OracleTypes.binaryFloat, Float.MAX_VALUE),
          new OracleTypeAndExample<>(OracleTypes.binaryFloat, -1.5E10f),

          // BINARY_DOUBLE - 64-bit IEEE 754
          // Oracle supports range: 1.0E-130 to 1.0E126 (excluding zero)
          new OracleTypeAndExample<>(OracleTypes.binaryDouble, 3.141592653589793),
          new OracleTypeAndExample<>(OracleTypes.binaryDouble, 0.0),
          new OracleTypeAndExample<>(
              OracleTypes.binaryDouble, 1.0E-129), // Near Oracle's min positive value
          new OracleTypeAndExample<>(OracleTypes.binaryDouble, -2.5E100),

          // FLOAT (ANSI type mapped to NUMBER)
          new OracleTypeAndExample<>(OracleTypes.float_, 42.42),
          new OracleTypeAndExample<>(OracleTypes.float_(63), 123.456), // REAL equivalent

          // ═══════════════════════════════════════════════════════════════════════════
          // Character Types
          // ═══════════════════════════════════════════════════════════════════════════

          // VARCHAR2
          new OracleTypeAndExample<>(OracleTypes.varchar2(100), "Hello, Oracle!"),
          new OracleTypeAndExample<>(OracleTypes.varchar2(100), "", (String) null)
              .noJsonRoundtrip(), // Oracle quirk: empty string → NULL
          new OracleTypeAndExample<>(
              OracleTypes.varchar2(100), "Unicode: \u00e9\u00e8\u00ea \u4e2d\u6587"),
          new OracleTypeAndExample<>(OracleTypes.varchar2(100), "Line1\nLine2\tTabbed"),
          new OracleTypeAndExample<>(OracleTypes.varchar2(100), "Quote\"Test'Single"),
          new OracleTypeAndExample<>(OracleTypes.varchar2(100), "Special chars: ,.;{}[]-//#"),

          // VARCHAR2 with NonEmptyString (for NOT NULL columns)
          new OracleTypeAndExample<>(
              OracleTypes.varchar2NonEmpty(100), NonEmptyString.force("NonEmpty VARCHAR2")),
          new OracleTypeAndExample<>(
              OracleTypes.varchar2NonEmpty(100), NonEmptyString.force("Test \u4e2d\u6587")),

          // CHAR (fixed-length, blank-padded)
          new OracleTypeAndExample<>(
              OracleTypes.char_(10), "hello     "), // Note: CHAR pads with spaces
          new OracleTypeAndExample<>(OracleTypes.char_(5), "abc  "), // May be trimmed on comparison

          // CHAR with PaddedString (for NOT NULL columns)
          new OracleTypeAndExample<>(OracleTypes.charPadded(10), PaddedString.force("hello", 10)),
          new OracleTypeAndExample<>(
              OracleTypes.charPadded(20), PaddedString.force("padded test", 20)),

          // NVARCHAR2 (National character set)
          new OracleTypeAndExample<>(
              OracleTypes.nvarchar2(100), "Unicode text: \u0391\u0392\u0393"),
          new OracleTypeAndExample<>(OracleTypes.nvarchar2(100), "Emoji: \uD83D\uDE00\uD83C\uDF89"),

          // NVARCHAR2 with NonEmptyString (for NOT NULL columns)
          new OracleTypeAndExample<>(
              OracleTypes.nvarchar2NonEmpty(100), NonEmptyString.force("NonEmpty NVARCHAR2")),

          // NCHAR
          new OracleTypeAndExample<>(OracleTypes.nchar(10), "test      "),

          // NCHAR with PaddedString (for NOT NULL columns)
          new OracleTypeAndExample<>(
              OracleTypes.ncharPadded(15), PaddedString.force("nchar test", 15)),

          // CLOB - Character Large Object (cannot be used as comparison key)
          new OracleTypeAndExample<>(
                  OracleTypes.clob, "This is a CLOB text that could be very large.")
              .noStreaming()
              .noIdentity(),
          new OracleTypeAndExample<>(OracleTypes.clob, "Short CLOB").noStreaming().noIdentity(),

          // CLOB with NonEmptyString (for NOT NULL columns - cannot be used as comparison key)
          new OracleTypeAndExample<>(
                  OracleTypes.clobNonEmpty, NonEmptyString.force("NonEmpty CLOB text"))
              .noStreaming()
              .noIdentity(),

          // NCLOB - National CLOB (cannot be used as comparison key)
          new OracleTypeAndExample<>(OracleTypes.nclob, "National CLOB with \u4e2d\u6587")
              .noStreaming()
              .noIdentity(),

          // NCLOB with NonEmptyString (for NOT NULL columns - cannot be used as comparison key)
          new OracleTypeAndExample<>(
                  OracleTypes.nclobNonEmpty, NonEmptyString.force("NonEmpty NCLOB \u4e2d\u6587"))
              .noStreaming()
              .noIdentity(),

          // ═══════════════════════════════════════════════════════════════════════════
          // Binary Types
          // ═══════════════════════════════════════════════════════════════════════════

          // RAW
          new OracleTypeAndExample<>(
              OracleTypes.raw(100), new byte[] {0x01, 0x02, 0x03, (byte) 0xFF}),
          new OracleTypeAndExample<>(OracleTypes.raw(100), new byte[] {}, (byte[]) null)
              .noJsonRoundtrip(), // Oracle quirk: empty byte array → NULL
          new OracleTypeAndExample<>(
              OracleTypes.raw(100), new byte[] {0x00, 0x00, 0x00}), // Edge case: zeros
          new OracleTypeAndExample<>(
              OracleTypes.raw(100),
              new byte[] {(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF}),

          // BLOB - Binary Large Object (cannot be used as comparison key)
          new OracleTypeAndExample<>(
                  OracleTypes.blob, new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE})
              .noStreaming()
              .noIdentity(),
          new OracleTypeAndExample<>(OracleTypes.blob, new byte[] {})
              .noStreaming()
              .noIdentity(), // Edge case: empty
          new OracleTypeAndExample<>(
                  OracleTypes.blob, new byte[] {0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77})
              .noStreaming()
              .noIdentity(),

          // ═══════════════════════════════════════════════════════════════════════════
          // Date/Time Types
          // ═══════════════════════════════════════════════════════════════════════════

          // DATE (includes time in Oracle!)
          new OracleTypeAndExample<>(OracleTypes.date, LocalDateTime.of(2024, 6, 15, 14, 30, 45)),
          new OracleTypeAndExample<>(
              OracleTypes.date, LocalDateTime.of(1970, 1, 1, 0, 0, 0)), // Edge case: epoch
          new OracleTypeAndExample<>(
              OracleTypes.date,
              LocalDateTime.of(2099, 12, 31, 23, 59, 59)), // Edge case: far future
          new OracleTypeAndExample<>(
              OracleTypes.date, LocalDateTime.of(1, 1, 1, 0, 0, 0)), // Edge case: very old

          // TIMESTAMP
          new OracleTypeAndExample<>(
              OracleTypes.timestamp, LocalDateTime.of(2024, 6, 15, 14, 30, 45, 123456000)),
          new OracleTypeAndExample<>(
              OracleTypes.timestamp(6), LocalDateTime.of(2024, 6, 15, 14, 30, 45, 123456000)),
          new OracleTypeAndExample<>(
              OracleTypes.timestamp(9), LocalDateTime.of(2024, 6, 15, 14, 30, 45, 123456789)),
          new OracleTypeAndExample<>(
              OracleTypes.timestamp, LocalDateTime.of(1970, 1, 1, 0, 0, 0, 0)), // Edge case: epoch

          // TIMESTAMP WITH TIME ZONE
          new OracleTypeAndExample<>(
              OracleTypes.timestampWithTimeZone,
              OffsetDateTime.of(2024, 6, 15, 14, 30, 45, 0, ZoneOffset.ofHours(2))),
          new OracleTypeAndExample<>(
              OracleTypes.timestampWithTimeZone,
              OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)),
          new OracleTypeAndExample<>(
              OracleTypes.timestampWithTimeZone,
              OffsetDateTime.of(
                  2024, 6, 15, 14, 30, 45, 123456000, ZoneOffset.ofHoursMinutes(-5, -30))),

          // TIMESTAMP WITH LOCAL TIME ZONE
          new OracleTypeAndExample<>(
              OracleTypes.timestampWithLocalTimeZone,
              OffsetDateTime.of(2024, 6, 15, 14, 30, 45, 0, ZoneOffset.ofHours(3))),

          // INTERVAL YEAR TO MONTH - Now using OracleIntervalYM class (parses both Oracle and
          // ISO-8601 formats)
          new OracleTypeAndExample<>(
              OracleTypes.intervalYearToMonth, new OracleIntervalYM(2, 5)), // 2 years, 5 months
          new OracleTypeAndExample<>(
              OracleTypes.intervalYearToMonth,
              new OracleIntervalYM(-1, -6)), // negative: -1 year -6 months
          new OracleTypeAndExample<>(
              OracleTypes.intervalYearToMonth, new OracleIntervalYM(0, 0)), // zero

          // INTERVAL DAY TO SECOND - Now using OracleIntervalDS class (parses both Oracle and
          // ISO-8601 formats)
          new OracleTypeAndExample<>(
              OracleTypes.intervalDayToSecond,
              new OracleIntervalDS(3, 14, 30, 45, 123456000)), // 3 days 14:30:45.123456
          new OracleTypeAndExample<>(
              OracleTypes.intervalDayToSecond,
              new OracleIntervalDS(-1, 0, 0, 0, 0)), // negative: -1 day
          new OracleTypeAndExample<>(
              OracleTypes.intervalDayToSecond, new OracleIntervalDS(0, 0, 0, 0, 0)), // zero

          // ═══════════════════════════════════════════════════════════════════════════
          // ROWID Types
          // ═══════════════════════════════════════════════════════════════════════════

          // Note: ROWID values are generated by Oracle and cannot be inserted directly
          // We test with mock values that follow the format but may not represent real rows

          // ═══════════════════════════════════════════════════════════════════════════
          // JSON Type (Oracle 21c+)
          // Note: JSON type doesn't support equality comparisons in WHERE clauses
          // ═══════════════════════════════════════════════════════════════════════════

          new OracleTypeAndExample<>(
                  OracleTypes.json, new Json("{\"name\": \"Oracle\", \"version\": 23}"))
              .noIdentity(),
          new OracleTypeAndExample<>(OracleTypes.json, new Json("[1, 2, 3, \"four\"]"))
              .noIdentity(),
          new OracleTypeAndExample<>(OracleTypes.json, new Json("{}"))
              .noIdentity(), // Edge case: empty object
          new OracleTypeAndExample<>(OracleTypes.json, new Json("[]"))
              .noIdentity(), // Edge case: empty array
          new OracleTypeAndExample<>(OracleTypes.json, new Json("null"))
              .noIdentity(), // Edge case: null
          new OracleTypeAndExample<>(OracleTypes.json, new Json("\"string\""))
              .noIdentity(), // Edge case: string
          new OracleTypeAndExample<>(OracleTypes.json, new Json("42"))
              .noIdentity(), // Edge case: number
          new OracleTypeAndExample<>(OracleTypes.json, new Json("true"))
              .noIdentity(), // Edge case: boolean

          // ═══════════════════════════════════════════════════════════════════════════
          // Boolean Type (Oracle 23c+ native, or NUMBER(1) convention)
          // ═══════════════════════════════════════════════════════════════════════════

          // Native BOOLEAN (23c+) - comment out if using older Oracle
          // new OracleTypeAndExample<>(OracleTypes.boolean_, true),
          // new OracleTypeAndExample<>(OracleTypes.boolean_, false),

          // NUMBER(1) as Boolean (traditional approach)
          new OracleTypeAndExample<>(OracleTypes.numberAsBoolean, true),
          new OracleTypeAndExample<>(OracleTypes.numberAsBoolean, false),

          // ═══════════════════════════════════════════════════════════════════════════
          // Object-Relational Types (User-Defined Types)
          // ═══════════════════════════════════════════════════════════════════════════

          // OBJECT TYPE example - address_t (with nested coordinates_t)
          new OracleTypeAndExample<>(
              addressType(),
              new Address("123 Main St", "San Francisco", coords("37.774929", "-122.419418")),
              List.of(
                  """
                  CREATE OR REPLACE TYPE COORDINATES_T AS OBJECT (
                    LATITUDE NUMBER(9,6),
                    LONGITUDE NUMBER(9,6)
                  )
                  """,
                  """
                  CREATE OR REPLACE TYPE ADDRESS_T AS OBJECT (
                    STREET VARCHAR2(100),
                    CITY VARCHAR2(50),
                    LOCATION COORDINATES_T
                  )
                  """)),

          // VARRAY example - phone_list (max 5 elements) - cannot be used as comparison key
          new OracleTypeAndExample<>(
              OracleVArray.of("PHONE_LIST", 5, OracleTypes.varchar2(20)),
              List.of("555-1234", "555-5678", "555-9999"),
              null,
              false,
              false,
              true,
              true,
              List.of("CREATE OR REPLACE TYPE PHONE_LIST AS VARRAY(5) OF VARCHAR2(20)")),

          // VARRAY edge case - single element
          new OracleTypeAndExample<>(
              OracleVArray.of("PHONE_LIST", 5, OracleTypes.varchar2(20)),
              List.of("555-0000"),
              null,
              false,
              false,
              true,
              true,
              List.of("CREATE OR REPLACE TYPE PHONE_LIST AS VARRAY(5) OF VARCHAR2(20)")),

          // VARRAY edge case - max size (5 elements)
          new OracleTypeAndExample<>(
              OracleVArray.of("PHONE_LIST", 5, OracleTypes.varchar2(20)),
              List.of("555-1111", "555-2222", "555-3333", "555-4444", "555-5555"),
              null,
              false,
              false,
              true,
              true,
              List.of("CREATE OR REPLACE TYPE PHONE_LIST AS VARRAY(5) OF VARCHAR2(20)")),

          // NESTED TABLE example - order_items_t with nested OBJECT type
          new OracleTypeAndExample<>(
                  OracleNestedTable.of(
                      "ORDER_ITEMS_T",
                      OracleObject.<OrderItem>builder("ORDER_ITEM_T")
                          .addAttribute("PRODUCT_ID", OracleTypes.numberLong, OrderItem::productId)
                          .addAttribute("QUANTITY", OracleTypes.numberInt, OrderItem::quantity)
                          .build(attrs -> new OrderItem((Long) attrs[0], (Integer) attrs[1]))
                          .asType()),
                  List.of(new OrderItem(101L, 2), new OrderItem(202L, 5), new OrderItem(303L, 1)),
                  List.of(
                      """
                      CREATE OR REPLACE TYPE ORDER_ITEM_T AS OBJECT (
                        PRODUCT_ID NUMBER,
                        QUANTITY NUMBER
                      )
                      """,
                      "CREATE OR REPLACE TYPE ORDER_ITEMS_T AS TABLE OF ORDER_ITEM_T"))
              .noIdentity(), // Collections can't be used as comparison keys

          // NESTED TABLE edge case - single item
          new OracleTypeAndExample<>(
                  OracleNestedTable.of(
                      "ORDER_ITEMS_T",
                      OracleObject.<OrderItem>builder("ORDER_ITEM_T")
                          .addAttribute("PRODUCT_ID", OracleTypes.numberLong, OrderItem::productId)
                          .addAttribute("QUANTITY", OracleTypes.numberInt, OrderItem::quantity)
                          .build(attrs -> new OrderItem((Long) attrs[0], (Integer) attrs[1]))
                          .asType()),
                  List.of(new OrderItem(999L, 42)),
                  List.of(
                      """
                      CREATE OR REPLACE TYPE ORDER_ITEM_T AS OBJECT (
                        PRODUCT_ID NUMBER,
                        QUANTITY NUMBER
                      )
                      """,
                      "CREATE OR REPLACE TYPE ORDER_ITEMS_T AS TABLE OF ORDER_ITEM_T"))
              .noIdentity(), // Collections can't be used as comparison keys

          // NESTED TABLE edge case - empty list
          new OracleTypeAndExample<>(
                  OracleNestedTable.of(
                      "ORDER_ITEMS_T",
                      OracleObject.<OrderItem>builder("ORDER_ITEM_T")
                          .addAttribute("PRODUCT_ID", OracleTypes.numberLong, OrderItem::productId)
                          .addAttribute("QUANTITY", OracleTypes.numberInt, OrderItem::quantity)
                          .build(attrs -> new OrderItem((Long) attrs[0], (Integer) attrs[1]))
                          .asType()),
                  List.of(),
                  List.of(
                      """
                      CREATE OR REPLACE TYPE ORDER_ITEM_T AS OBJECT (
                        PRODUCT_ID NUMBER,
                        QUANTITY NUMBER
                      )
                      """,
                      "CREATE OR REPLACE TYPE ORDER_ITEMS_T AS TABLE OF ORDER_ITEM_T"))
              .noIdentity(),

          // ═══════════════════════════════════════════════════════════════════════════
          // Comprehensive STRUCT Tests - All Oracle Types
          // ═══════════════════════════════════════════════════════════════════════════

          // TEST_ALLTYPES - struct with all Oracle types (NOT NULL fields)
          new OracleTypeAndExample<AllTypesStruct>(
                  OracleObject.<AllTypesStruct>builder("TEST_ALLTYPES")
                      .addAttribute("VARCHAR_FIELD", OracleTypes.varchar2(100), s -> s.varcharField)
                      .addAttribute(
                          "NVARCHAR_FIELD", OracleTypes.nvarchar2(100), s -> s.nvarcharField)
                      .addAttribute("CHAR_FIELD", OracleTypes.char_(10), s -> s.charField)
                      .addAttribute("NCHAR_FIELD", OracleTypes.nchar(10), s -> s.ncharField)
                      .addAttribute("NUMBER_FIELD", OracleTypes.number, s -> s.numberField)
                      .addAttribute(
                          "NUMBER_INT_FIELD", OracleTypes.numberInt, s -> s.numberIntField)
                      .addAttribute(
                          "NUMBER_LONG_FIELD", OracleTypes.numberLong, s -> s.numberLongField)
                      .addAttribute(
                          "BINARY_FLOAT_FIELD", OracleTypes.binaryFloat, s -> s.binaryFloatField)
                      .addAttribute(
                          "BINARY_DOUBLE_FIELD", OracleTypes.binaryDouble, s -> s.binaryDoubleField)
                      .addAttribute("DATE_FIELD", OracleTypes.date, s -> s.dateField)
                      .addAttribute("TIMESTAMP_FIELD", OracleTypes.timestamp, s -> s.timestampField)
                      .addAttribute(
                          "TIMESTAMP_TZ_FIELD",
                          OracleTypes.timestampWithTimeZone,
                          s -> s.timestampTzField)
                      .addAttribute(
                          "TIMESTAMP_LTZ_FIELD",
                          OracleTypes.timestampWithLocalTimeZone,
                          s -> s.timestampLtzField)
                      .addAttribute(
                          "INTERVAL_YM_FIELD",
                          OracleTypes.intervalYearToMonth,
                          s -> s.intervalYmField)
                      .addAttribute(
                          "INTERVAL_DS_FIELD",
                          OracleTypes.intervalDayToSecond,
                          s -> s.intervalDsField)
                      .addAttribute("NESTED_OBJECT_FIELD", addressType(), s -> s.nestedObjectField)
                      .addAttribute(
                          "VARRAY_FIELD",
                          OracleVArray.of("PHONE_LIST", 5, OracleTypes.varchar2(20)),
                          s -> s.varrayField)
                      .build(
                          attrs ->
                              new AllTypesStruct(
                                  (String) attrs[0], // varcharField
                                  (String) attrs[1], // nvarcharField
                                  (String) attrs[2], // charField
                                  (String) attrs[3], // ncharField
                                  (BigDecimal) attrs[4], // numberField
                                  (Integer) attrs[5], // numberIntField
                                  (Long) attrs[6], // numberLongField
                                  (Float) attrs[7], // binaryFloatField
                                  (Double) attrs[8], // binaryDoubleField
                                  (LocalDateTime) attrs[9], // dateField
                                  (LocalDateTime) attrs[10], // timestampField
                                  (OffsetDateTime) attrs[11], // timestampTzField
                                  (OffsetDateTime) attrs[12], // timestampLtzField
                                  (OracleIntervalYM) attrs[13], // intervalYmField
                                  (OracleIntervalDS) attrs[14], // intervalDsField
                                  (Address) attrs[15], // nestedObjectField
                                  (List<String>) attrs[16] // varrayField
                                  ))
                      .asType(),
                  new AllTypesStruct(
                      "test varchar",
                      "test nvarchar",
                      "char10    ",
                      "nchar10   ",
                      new BigDecimal("123.45"),
                      42,
                      12345678L,
                      3.14f,
                      2.718,
                      LocalDateTime.of(2024, 6, 15, 14, 30, 45),
                      LocalDateTime.of(2024, 6, 15, 14, 30, 45, 123456000),
                      OffsetDateTime.of(2024, 6, 15, 14, 30, 45, 0, ZoneOffset.ofHours(2)),
                      OffsetDateTime.of(2024, 6, 15, 14, 30, 45, 0, ZoneOffset.ofHours(3)),
                      new OracleIntervalYM(2, 5),
                      new OracleIntervalDS(3, 14, 30, 45, 123456000),
                      new Address("123 Main St", "San Francisco", coords("37.7749", "-122.4194")),
                      List.of("555-1234", "555-5678")),
                  List.of(
                      "BEGIN EXECUTE IMMEDIATE 'DROP TYPE TEST_ALLTYPES FORCE'; EXCEPTION WHEN"
                          + " OTHERS THEN NULL; END;",
                      """
                      CREATE OR REPLACE TYPE TEST_ALLTYPES AS OBJECT (
                        VARCHAR_FIELD VARCHAR2(100),
                        NVARCHAR_FIELD NVARCHAR2(100),
                        CHAR_FIELD CHAR(10),
                        NCHAR_FIELD NCHAR(10),
                        NUMBER_FIELD NUMBER,
                        NUMBER_INT_FIELD NUMBER(10),
                        NUMBER_LONG_FIELD NUMBER(19),
                        BINARY_FLOAT_FIELD BINARY_FLOAT,
                        BINARY_DOUBLE_FIELD BINARY_DOUBLE,
                        DATE_FIELD DATE,
                        TIMESTAMP_FIELD TIMESTAMP,
                        TIMESTAMP_TZ_FIELD TIMESTAMP WITH TIME ZONE,
                        TIMESTAMP_LTZ_FIELD TIMESTAMP WITH LOCAL TIME ZONE,
                        INTERVAL_YM_FIELD INTERVAL YEAR TO MONTH,
                        INTERVAL_DS_FIELD INTERVAL DAY TO SECOND,
                        NESTED_OBJECT_FIELD ADDRESS_T,
                        VARRAY_FIELD PHONE_LIST
                      )
                      """))
              .noIdentity()
              .noJsonRoundtrip(), // Complex struct - skip identity and JSON tests for now

          // TEST_ALLTYPES_OPT - comprehensive struct with all nullable fields
          new OracleTypeAndExample<AllTypesStructOptional>(
                  OracleObject.<AllTypesStructOptional>builder("TEST_ALLTYPES_OPT")
                      .addAttribute(
                          "VARCHAR_FIELD", OracleTypes.varchar2(100).opt(), s -> s.varcharField)
                      .addAttribute(
                          "NVARCHAR_FIELD", OracleTypes.nvarchar2(100).opt(), s -> s.nvarcharField)
                      .addAttribute("CHAR_FIELD", OracleTypes.char_(10).opt(), s -> s.charField)
                      .addAttribute("NCHAR_FIELD", OracleTypes.nchar(10).opt(), s -> s.ncharField)
                      .addAttribute("NUMBER_FIELD", OracleTypes.number.opt(), s -> s.numberField)
                      .addAttribute(
                          "NUMBER_INT_FIELD", OracleTypes.numberInt.opt(), s -> s.numberIntField)
                      .addAttribute(
                          "NUMBER_LONG_FIELD", OracleTypes.numberLong.opt(), s -> s.numberLongField)
                      .addAttribute(
                          "BINARY_FLOAT_FIELD",
                          OracleTypes.binaryFloat.opt(),
                          s -> s.binaryFloatField)
                      .addAttribute(
                          "BINARY_DOUBLE_FIELD",
                          OracleTypes.binaryDouble.opt(),
                          s -> s.binaryDoubleField)
                      .addAttribute("DATE_FIELD", OracleTypes.date.opt(), s -> s.dateField)
                      .addAttribute(
                          "TIMESTAMP_FIELD", OracleTypes.timestamp.opt(), s -> s.timestampField)
                      .addAttribute(
                          "TIMESTAMP_TZ_FIELD",
                          OracleTypes.timestampWithTimeZone.opt(),
                          s -> s.timestampTzField)
                      .addAttribute(
                          "TIMESTAMP_LTZ_FIELD",
                          OracleTypes.timestampWithLocalTimeZone.opt(),
                          s -> s.timestampLtzField)
                      .addAttribute(
                          "INTERVAL_YM_FIELD",
                          OracleTypes.intervalYearToMonth.opt(),
                          s -> s.intervalYmField)
                      .addAttribute(
                          "INTERVAL_DS_FIELD",
                          OracleTypes.intervalDayToSecond.opt(),
                          s -> s.intervalDsField)
                      .addAttribute(
                          "NESTED_OBJECT_FIELD", addressType().opt(), s -> s.nestedObjectField)
                      .addAttribute(
                          "VARRAY_FIELD",
                          OracleVArray.of("PHONE_LIST", 5, OracleTypes.varchar2(20)).opt(),
                          s -> s.varrayField)
                      .build(
                          attrs ->
                              new AllTypesStructOptional(
                                  (Optional<String>) attrs[0], // varcharField
                                  (Optional<String>) attrs[1], // nvarcharField
                                  (Optional<String>) attrs[2], // charField
                                  (Optional<String>) attrs[3], // ncharField
                                  (Optional<BigDecimal>) attrs[4], // numberField
                                  (Optional<Integer>) attrs[5], // numberIntField
                                  (Optional<Long>) attrs[6], // numberLongField
                                  (Optional<Float>) attrs[7], // binaryFloatField
                                  (Optional<Double>) attrs[8], // binaryDoubleField
                                  (Optional<LocalDateTime>) attrs[9], // dateField
                                  (Optional<LocalDateTime>) attrs[10], // timestampField
                                  (Optional<OffsetDateTime>) attrs[11], // timestampTzField
                                  (Optional<OffsetDateTime>) attrs[12], // timestampLtzField
                                  (Optional<OracleIntervalYM>) attrs[13], // intervalYmField
                                  (Optional<OracleIntervalDS>) attrs[14], // intervalDsField
                                  (Optional<Address>) attrs[15], // nestedObjectField
                                  (Optional<List<String>>) attrs[16] // varrayField
                                  ))
                      .asType(),
                  new AllTypesStructOptional(
                      Optional.of("test varchar"),
                      Optional.empty(), // Test null nvarcharField
                      Optional.of("char10    "),
                      Optional.empty(), // Test null ncharField
                      Optional.of(new BigDecimal("123.45")),
                      Optional.of(42),
                      Optional.empty(), // Test null numberLongField
                      Optional.of(3.14f),
                      Optional.of(2.718),
                      Optional.of(LocalDateTime.of(2024, 6, 15, 14, 30, 45)),
                      Optional.empty(), // Test null timestampField
                      Optional.of(
                          OffsetDateTime.of(2024, 6, 15, 14, 30, 45, 0, ZoneOffset.ofHours(2))),
                      Optional.of(
                          OffsetDateTime.of(2024, 6, 15, 14, 30, 45, 0, ZoneOffset.ofHours(3))),
                      Optional.of(new OracleIntervalYM(2, 5)),
                      Optional.empty(), // Test null intervalDsField
                      Optional.of(
                          new Address(
                              "123 Main St", "San Francisco", coords("37.7749", "-122.4194"))),
                      Optional.of(List.of("555-1234", "555-5678"))),
                  List.of(
                      "BEGIN EXECUTE IMMEDIATE 'DROP TYPE TEST_ALLTYPES_OPT FORCE';"
                          + " EXCEPTION WHEN OTHERS THEN NULL; END;",
                      """
                      CREATE OR REPLACE TYPE TEST_ALLTYPES_OPT AS OBJECT (
                        VARCHAR_FIELD VARCHAR2(100),
                        NVARCHAR_FIELD NVARCHAR2(100),
                        CHAR_FIELD CHAR(10),
                        NCHAR_FIELD NCHAR(10),
                        NUMBER_FIELD NUMBER,
                        NUMBER_INT_FIELD NUMBER(10),
                        NUMBER_LONG_FIELD NUMBER(19),
                        BINARY_FLOAT_FIELD BINARY_FLOAT,
                        BINARY_DOUBLE_FIELD BINARY_DOUBLE,
                        DATE_FIELD DATE,
                        TIMESTAMP_FIELD TIMESTAMP,
                        TIMESTAMP_TZ_FIELD TIMESTAMP WITH TIME ZONE,
                        TIMESTAMP_LTZ_FIELD TIMESTAMP WITH LOCAL TIME ZONE,
                        INTERVAL_YM_FIELD INTERVAL YEAR TO MONTH,
                        INTERVAL_DS_FIELD INTERVAL DAY TO SECOND,
                        NESTED_OBJECT_FIELD ADDRESS_T,
                        VARRAY_FIELD PHONE_LIST
                      )
                      """))
              .noIdentity()
              .noJsonRoundtrip(), // Complex struct - skip identity and JSON tests for now

          // ═══════════════════════════════════════════════════════════════════════════
          // Structs Without LOBs - For VARRAY Compatibility
          // Oracle restriction: VARRAYs cannot contain structs with embedded LOBs
          // ═══════════════════════════════════════════════════════════════════════════

          // TEST_ALLTYPES_NOLOBS - standalone struct without LOBs
          new OracleTypeAndExample<>(
                  OracleObject.<AllTypesStructNoLobs>builder("TEST_ALLTYPES_NOLOBS")
                      .addAttribute("VARCHAR_FIELD", OracleTypes.varchar2(100), s -> s.varcharField)
                      .addAttribute(
                          "NVARCHAR_FIELD", OracleTypes.nvarchar2(100), s -> s.nvarcharField)
                      .addAttribute("CHAR_FIELD", OracleTypes.char_(10), s -> s.charField)
                      .addAttribute("NCHAR_FIELD", OracleTypes.nchar(10), s -> s.ncharField)
                      .addAttribute("NUMBER_FIELD", OracleTypes.number, s -> s.numberField)
                      .addAttribute(
                          "NUMBER_INT_FIELD", OracleTypes.numberInt, s -> s.numberIntField)
                      .addAttribute(
                          "NUMBER_LONG_FIELD", OracleTypes.numberLong, s -> s.numberLongField)
                      .addAttribute(
                          "BINARY_FLOAT_FIELD", OracleTypes.binaryFloat, s -> s.binaryFloatField)
                      .addAttribute(
                          "BINARY_DOUBLE_FIELD", OracleTypes.binaryDouble, s -> s.binaryDoubleField)
                      .addAttribute("DATE_FIELD", OracleTypes.date, s -> s.dateField)
                      .addAttribute("TIMESTAMP_FIELD", OracleTypes.timestamp, s -> s.timestampField)
                      .addAttribute(
                          "TIMESTAMP_TZ_FIELD",
                          OracleTypes.timestampWithTimeZone,
                          s -> s.timestampTzField)
                      .addAttribute(
                          "TIMESTAMP_LTZ_FIELD",
                          OracleTypes.timestampWithLocalTimeZone,
                          s -> s.timestampLtzField)
                      .addAttribute(
                          "INTERVAL_YM_FIELD",
                          OracleTypes.intervalYearToMonth,
                          s -> s.intervalYmField)
                      .addAttribute(
                          "INTERVAL_DS_FIELD",
                          OracleTypes.intervalDayToSecond,
                          s -> s.intervalDsField)
                      .addAttribute("NESTED_OBJECT_FIELD", addressType(), s -> s.nestedObjectField)
                      .addAttribute(
                          "VARRAY_FIELD",
                          OracleVArray.of("PHONE_LIST", 5, OracleTypes.varchar2(20)),
                          s -> s.varrayField)
                      .build(
                          attrs ->
                              new AllTypesStructNoLobs(
                                  (String) attrs[0],
                                  (String) attrs[1],
                                  (String) attrs[2],
                                  (String) attrs[3],
                                  (BigDecimal) attrs[4],
                                  (Integer) attrs[5],
                                  (Long) attrs[6],
                                  (Float) attrs[7],
                                  (Double) attrs[8],
                                  (LocalDateTime) attrs[9],
                                  (LocalDateTime) attrs[10],
                                  (OffsetDateTime) attrs[11],
                                  (OffsetDateTime) attrs[12],
                                  (OracleIntervalYM) attrs[13],
                                  (OracleIntervalDS) attrs[14],
                                  (Address) attrs[15],
                                  (List<String>) attrs[16]))
                      .asType(),
                  new AllTypesStructNoLobs(
                      "varchar_val",
                      "nvarchar_val",
                      "char_val  ",
                      "nchar_val ",
                      new BigDecimal("123.45"),
                      42,
                      9876543210L,
                      3.14f,
                      2.718281828,
                      LocalDateTime.of(2024, 3, 15, 14, 30),
                      LocalDateTime.of(2024, 3, 15, 14, 30, 45, 123456789),
                      OffsetDateTime.of(2024, 3, 15, 14, 30, 45, 0, ZoneOffset.ofHours(2)),
                      OffsetDateTime.of(2024, 3, 15, 14, 30, 45, 0, ZoneOffset.ofHours(3)),
                      new OracleIntervalYM(2, 6),
                      new OracleIntervalDS(5, 12, 30, 45, 123456000),
                      new Address("456 Oak Ave", "Portland", coords("45.5152", "-122.6784")),
                      List.of("555-1234", "555-5678")),
                  List.of(
                      """
                      CREATE OR REPLACE TYPE TEST_ALLTYPES_NOLOBS AS OBJECT (
                        VARCHAR_FIELD VARCHAR2(100),
                        NVARCHAR_FIELD NVARCHAR2(100),
                        CHAR_FIELD CHAR(10),
                        NCHAR_FIELD NCHAR(10),
                        NUMBER_FIELD NUMBER,
                        NUMBER_INT_FIELD NUMBER(10),
                        NUMBER_LONG_FIELD NUMBER(19),
                        BINARY_FLOAT_FIELD BINARY_FLOAT,
                        BINARY_DOUBLE_FIELD BINARY_DOUBLE,
                        DATE_FIELD DATE,
                        TIMESTAMP_FIELD TIMESTAMP,
                        TIMESTAMP_TZ_FIELD TIMESTAMP WITH TIME ZONE,
                        TIMESTAMP_LTZ_FIELD TIMESTAMP WITH LOCAL TIME ZONE,
                        INTERVAL_YM_FIELD INTERVAL YEAR TO MONTH,
                        INTERVAL_DS_FIELD INTERVAL DAY TO SECOND,
                        NESTED_OBJECT_FIELD ADDRESS_T,
                        VARRAY_FIELD PHONE_LIST
                      )
                      """))
              .noJsonRoundtrip()
              .noIdentity(), // Oracle ORA-22901: cannot compare types with VARRAY attributes

          // TEST_ALLTYPES_NOLOBS_OPT - standalone struct without LOBs, optional fields
          new OracleTypeAndExample<>(
                  OracleObject.<AllTypesStructNoLobsOptional>builder("TEST_ALLTYPES_NOLOBS_OPT")
                      .addAttribute(
                          "VARCHAR_FIELD", OracleTypes.varchar2(100).opt(), s -> s.varcharField)
                      .addAttribute(
                          "NVARCHAR_FIELD", OracleTypes.nvarchar2(100).opt(), s -> s.nvarcharField)
                      .addAttribute("CHAR_FIELD", OracleTypes.char_(10).opt(), s -> s.charField)
                      .addAttribute("NCHAR_FIELD", OracleTypes.nchar(10).opt(), s -> s.ncharField)
                      .addAttribute("NUMBER_FIELD", OracleTypes.number.opt(), s -> s.numberField)
                      .addAttribute(
                          "NUMBER_INT_FIELD", OracleTypes.numberInt.opt(), s -> s.numberIntField)
                      .addAttribute(
                          "NUMBER_LONG_FIELD", OracleTypes.numberLong.opt(), s -> s.numberLongField)
                      .addAttribute(
                          "BINARY_FLOAT_FIELD",
                          OracleTypes.binaryFloat.opt(),
                          s -> s.binaryFloatField)
                      .addAttribute(
                          "BINARY_DOUBLE_FIELD",
                          OracleTypes.binaryDouble.opt(),
                          s -> s.binaryDoubleField)
                      .addAttribute("DATE_FIELD", OracleTypes.date.opt(), s -> s.dateField)
                      .addAttribute(
                          "TIMESTAMP_FIELD", OracleTypes.timestamp.opt(), s -> s.timestampField)
                      .addAttribute(
                          "TIMESTAMP_TZ_FIELD",
                          OracleTypes.timestampWithTimeZone.opt(),
                          s -> s.timestampTzField)
                      .addAttribute(
                          "TIMESTAMP_LTZ_FIELD",
                          OracleTypes.timestampWithLocalTimeZone.opt(),
                          s -> s.timestampLtzField)
                      .addAttribute(
                          "INTERVAL_YM_FIELD",
                          OracleTypes.intervalYearToMonth.opt(),
                          s -> s.intervalYmField)
                      .addAttribute(
                          "INTERVAL_DS_FIELD",
                          OracleTypes.intervalDayToSecond.opt(),
                          s -> s.intervalDsField)
                      .addAttribute(
                          "NESTED_OBJECT_FIELD", addressType().opt(), s -> s.nestedObjectField)
                      .addAttribute(
                          "VARRAY_FIELD",
                          OracleVArray.of("PHONE_LIST", 5, OracleTypes.varchar2(20)).opt(),
                          s -> s.varrayField)
                      .build(
                          attrs ->
                              new AllTypesStructNoLobsOptional(
                                  (Optional<String>) attrs[0],
                                  (Optional<String>) attrs[1],
                                  (Optional<String>) attrs[2],
                                  (Optional<String>) attrs[3],
                                  (Optional<BigDecimal>) attrs[4],
                                  (Optional<Integer>) attrs[5],
                                  (Optional<Long>) attrs[6],
                                  (Optional<Float>) attrs[7],
                                  (Optional<Double>) attrs[8],
                                  (Optional<LocalDateTime>) attrs[9],
                                  (Optional<LocalDateTime>) attrs[10],
                                  (Optional<OffsetDateTime>) attrs[11],
                                  (Optional<OffsetDateTime>) attrs[12],
                                  (Optional<OracleIntervalYM>) attrs[13],
                                  (Optional<OracleIntervalDS>) attrs[14],
                                  (Optional<Address>) attrs[15],
                                  (Optional<List<String>>) attrs[16]))
                      .asType(),
                  new AllTypesStructNoLobsOptional(
                      Optional.of("varchar_val"),
                      Optional.empty(),
                      Optional.of("char_val  "),
                      Optional.empty(),
                      Optional.of(new BigDecimal("123.45")),
                      Optional.of(42),
                      Optional.empty(),
                      Optional.of(3.14f),
                      Optional.of(2.718281828),
                      Optional.of(LocalDateTime.of(2024, 3, 15, 14, 30)),
                      Optional.empty(),
                      Optional.of(
                          OffsetDateTime.of(2024, 3, 15, 14, 30, 45, 0, ZoneOffset.ofHours(2))),
                      Optional.of(
                          OffsetDateTime.of(2024, 3, 15, 14, 30, 45, 0, ZoneOffset.ofHours(3))),
                      Optional.of(new OracleIntervalYM(2, 6)),
                      Optional.empty(),
                      Optional.of(
                          new Address("456 Oak Ave", "Portland", coords("45.5152", "-122.6784"))),
                      Optional.of(List.of("555-1234"))),
                  List.of(
                      """
                      CREATE OR REPLACE TYPE TEST_ALLTYPES_NOLOBS_OPT AS OBJECT (
                        VARCHAR_FIELD VARCHAR2(100),
                        NVARCHAR_FIELD NVARCHAR2(100),
                        CHAR_FIELD CHAR(10),
                        NCHAR_FIELD NCHAR(10),
                        NUMBER_FIELD NUMBER,
                        NUMBER_INT_FIELD NUMBER(10),
                        NUMBER_LONG_FIELD NUMBER(19),
                        BINARY_FLOAT_FIELD BINARY_FLOAT,
                        BINARY_DOUBLE_FIELD BINARY_DOUBLE,
                        DATE_FIELD DATE,
                        TIMESTAMP_FIELD TIMESTAMP,
                        TIMESTAMP_TZ_FIELD TIMESTAMP WITH TIME ZONE,
                        TIMESTAMP_LTZ_FIELD TIMESTAMP WITH LOCAL TIME ZONE,
                        INTERVAL_YM_FIELD INTERVAL YEAR TO MONTH,
                        INTERVAL_DS_FIELD INTERVAL DAY TO SECOND,
                        NESTED_OBJECT_FIELD ADDRESS_T,
                        VARRAY_FIELD PHONE_LIST
                      )
                      """))
              .noJsonRoundtrip()
              .noIdentity(), // Oracle ORA-22901: cannot compare types with VARRAY attributes

          // VARRAY of TEST_ALLTYPES_NOLOBS - array of structs without LOBs
          new OracleTypeAndExample<>(
                  OracleVArray.of(
                      "TEST_ALLTYPES_NOLOBS_ARR",
                      10,
                      OracleObject.<AllTypesStructNoLobs>builder("TEST_ALLTYPES_NOLOBS")
                          .addAttribute(
                              "VARCHAR_FIELD", OracleTypes.varchar2(100), s -> s.varcharField)
                          .addAttribute(
                              "NVARCHAR_FIELD", OracleTypes.nvarchar2(100), s -> s.nvarcharField)
                          .addAttribute("CHAR_FIELD", OracleTypes.char_(10), s -> s.charField)
                          .addAttribute("NCHAR_FIELD", OracleTypes.nchar(10), s -> s.ncharField)
                          .addAttribute("NUMBER_FIELD", OracleTypes.number, s -> s.numberField)
                          .addAttribute(
                              "NUMBER_INT_FIELD", OracleTypes.numberInt, s -> s.numberIntField)
                          .addAttribute(
                              "NUMBER_LONG_FIELD", OracleTypes.numberLong, s -> s.numberLongField)
                          .addAttribute(
                              "BINARY_FLOAT_FIELD",
                              OracleTypes.binaryFloat,
                              s -> s.binaryFloatField)
                          .addAttribute(
                              "BINARY_DOUBLE_FIELD",
                              OracleTypes.binaryDouble,
                              s -> s.binaryDoubleField)
                          .addAttribute("DATE_FIELD", OracleTypes.date, s -> s.dateField)
                          .addAttribute(
                              "TIMESTAMP_FIELD", OracleTypes.timestamp, s -> s.timestampField)
                          .addAttribute(
                              "TIMESTAMP_TZ_FIELD",
                              OracleTypes.timestampWithTimeZone,
                              s -> s.timestampTzField)
                          .addAttribute(
                              "TIMESTAMP_LTZ_FIELD",
                              OracleTypes.timestampWithLocalTimeZone,
                              s -> s.timestampLtzField)
                          .addAttribute(
                              "INTERVAL_YM_FIELD",
                              OracleTypes.intervalYearToMonth,
                              s -> s.intervalYmField)
                          .addAttribute(
                              "INTERVAL_DS_FIELD",
                              OracleTypes.intervalDayToSecond,
                              s -> s.intervalDsField)
                          .addAttribute(
                              "NESTED_OBJECT_FIELD", addressType(), s -> s.nestedObjectField)
                          .addAttribute(
                              "VARRAY_FIELD",
                              OracleVArray.of("PHONE_LIST", 5, OracleTypes.varchar2(20)),
                              s -> s.varrayField)
                          .build(
                              attrs ->
                                  new AllTypesStructNoLobs(
                                      (String) attrs[0],
                                      (String) attrs[1],
                                      (String) attrs[2],
                                      (String) attrs[3],
                                      (BigDecimal) attrs[4],
                                      (Integer) attrs[5],
                                      (Long) attrs[6],
                                      (Float) attrs[7],
                                      (Double) attrs[8],
                                      (LocalDateTime) attrs[9],
                                      (LocalDateTime) attrs[10],
                                      (OffsetDateTime) attrs[11],
                                      (OffsetDateTime) attrs[12],
                                      (OracleIntervalYM) attrs[13],
                                      (OracleIntervalDS) attrs[14],
                                      (Address) attrs[15],
                                      (List<String>) attrs[16]))
                          .asType()),
                  List.of(
                      new AllTypesStructNoLobs(
                          "varchar1",
                          "nvarchar1",
                          "char1     ",
                          "nchar1    ",
                          new BigDecimal("111.11"),
                          11,
                          1111L,
                          1.1f,
                          1.11,
                          LocalDateTime.of(2024, 1, 1, 10, 0),
                          LocalDateTime.of(2024, 1, 1, 10, 0, 0, 111000000),
                          OffsetDateTime.of(2024, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC),
                          OffsetDateTime.of(2024, 1, 1, 10, 0, 0, 0, ZoneOffset.ofHours(3)),
                          new OracleIntervalYM(1, 1),
                          new OracleIntervalDS(1, 1, 1, 1, 111000000),
                          new Address("111 First St", "City1", coords("40.7128", "-74.006")),
                          List.of("111-1111")),
                      new AllTypesStructNoLobs(
                          "varchar2",
                          "nvarchar2",
                          "char2     ",
                          "nchar2    ",
                          new BigDecimal("222.22"),
                          22,
                          2222L,
                          2.2f,
                          2.22,
                          LocalDateTime.of(2024, 2, 2, 20, 0),
                          LocalDateTime.of(2024, 2, 2, 20, 0, 0, 222000000),
                          OffsetDateTime.of(2024, 2, 2, 20, 0, 0, 0, ZoneOffset.ofHours(-5)),
                          OffsetDateTime.of(2024, 2, 2, 20, 0, 0, 0, ZoneOffset.ofHours(3)),
                          new OracleIntervalYM(2, 2),
                          new OracleIntervalDS(2, 2, 2, 2, 222000000),
                          new Address("222 Second St", "City2", coords("34.0522", "-118.2437")),
                          List.of("222-2222", "222-3333"))),
                  List.of(
                      "BEGIN EXECUTE IMMEDIATE 'DROP TYPE TEST_ALLTYPES_NOLOBS_ARR';"
                          + " EXCEPTION WHEN OTHERS THEN NULL; END;",
                      "BEGIN EXECUTE IMMEDIATE 'DROP TYPE TEST_ALLTYPES_NOLOBS FORCE';"
                          + " EXCEPTION WHEN OTHERS THEN NULL; END;",
                      """
                      CREATE OR REPLACE TYPE TEST_ALLTYPES_NOLOBS AS OBJECT (
                        VARCHAR_FIELD VARCHAR2(100),
                        NVARCHAR_FIELD NVARCHAR2(100),
                        CHAR_FIELD CHAR(10),
                        NCHAR_FIELD NCHAR(10),
                        NUMBER_FIELD NUMBER,
                        NUMBER_INT_FIELD NUMBER(10),
                        NUMBER_LONG_FIELD NUMBER(19),
                        BINARY_FLOAT_FIELD BINARY_FLOAT,
                        BINARY_DOUBLE_FIELD BINARY_DOUBLE,
                        DATE_FIELD DATE,
                        TIMESTAMP_FIELD TIMESTAMP,
                        TIMESTAMP_TZ_FIELD TIMESTAMP WITH TIME ZONE,
                        TIMESTAMP_LTZ_FIELD TIMESTAMP WITH LOCAL TIME ZONE,
                        INTERVAL_YM_FIELD INTERVAL YEAR TO MONTH,
                        INTERVAL_DS_FIELD INTERVAL DAY TO SECOND,
                        NESTED_OBJECT_FIELD ADDRESS_T,
                        VARRAY_FIELD PHONE_LIST
                      )
                      """,
                      "CREATE OR REPLACE TYPE TEST_ALLTYPES_NOLOBS_ARR AS VARRAY(10) OF"
                          + " TEST_ALLTYPES_NOLOBS"))
              .noIdentity(), // Complex array of structs - skip identity test

          // VARRAY of TEST_ALLTYPES_NOLOBS_OPT - array of structs without LOBs, optional
          // fields
          new OracleTypeAndExample<>(
                  OracleVArray.of(
                      "TEST_ALLTYPES_NOLOBS_OPT_ARR",
                      10,
                      OracleObject.<AllTypesStructNoLobsOptional>builder("TEST_ALLTYPES_NOLOBS_OPT")
                          .addAttribute(
                              "VARCHAR_FIELD", OracleTypes.varchar2(100).opt(), s -> s.varcharField)
                          .addAttribute(
                              "NVARCHAR_FIELD",
                              OracleTypes.nvarchar2(100).opt(),
                              s -> s.nvarcharField)
                          .addAttribute("CHAR_FIELD", OracleTypes.char_(10).opt(), s -> s.charField)
                          .addAttribute(
                              "NCHAR_FIELD", OracleTypes.nchar(10).opt(), s -> s.ncharField)
                          .addAttribute(
                              "NUMBER_FIELD", OracleTypes.number.opt(), s -> s.numberField)
                          .addAttribute(
                              "NUMBER_INT_FIELD",
                              OracleTypes.numberInt.opt(),
                              s -> s.numberIntField)
                          .addAttribute(
                              "NUMBER_LONG_FIELD",
                              OracleTypes.numberLong.opt(),
                              s -> s.numberLongField)
                          .addAttribute(
                              "BINARY_FLOAT_FIELD",
                              OracleTypes.binaryFloat.opt(),
                              s -> s.binaryFloatField)
                          .addAttribute(
                              "BINARY_DOUBLE_FIELD",
                              OracleTypes.binaryDouble.opt(),
                              s -> s.binaryDoubleField)
                          .addAttribute("DATE_FIELD", OracleTypes.date.opt(), s -> s.dateField)
                          .addAttribute(
                              "TIMESTAMP_FIELD", OracleTypes.timestamp.opt(), s -> s.timestampField)
                          .addAttribute(
                              "TIMESTAMP_TZ_FIELD",
                              OracleTypes.timestampWithTimeZone.opt(),
                              s -> s.timestampTzField)
                          .addAttribute(
                              "TIMESTAMP_LTZ_FIELD",
                              OracleTypes.timestampWithLocalTimeZone.opt(),
                              s -> s.timestampLtzField)
                          .addAttribute(
                              "INTERVAL_YM_FIELD",
                              OracleTypes.intervalYearToMonth.opt(),
                              s -> s.intervalYmField)
                          .addAttribute(
                              "INTERVAL_DS_FIELD",
                              OracleTypes.intervalDayToSecond.opt(),
                              s -> s.intervalDsField)
                          .addAttribute(
                              "NESTED_OBJECT_FIELD", addressType().opt(), s -> s.nestedObjectField)
                          .addAttribute(
                              "VARRAY_FIELD",
                              OracleVArray.of("PHONE_LIST", 5, OracleTypes.varchar2(20)).opt(),
                              s -> s.varrayField)
                          .build(
                              attrs ->
                                  new AllTypesStructNoLobsOptional(
                                      (Optional<String>) attrs[0],
                                      (Optional<String>) attrs[1],
                                      (Optional<String>) attrs[2],
                                      (Optional<String>) attrs[3],
                                      (Optional<BigDecimal>) attrs[4],
                                      (Optional<Integer>) attrs[5],
                                      (Optional<Long>) attrs[6],
                                      (Optional<Float>) attrs[7],
                                      (Optional<Double>) attrs[8],
                                      (Optional<LocalDateTime>) attrs[9],
                                      (Optional<LocalDateTime>) attrs[10],
                                      (Optional<OffsetDateTime>) attrs[11],
                                      (Optional<OffsetDateTime>) attrs[12],
                                      (Optional<OracleIntervalYM>) attrs[13],
                                      (Optional<OracleIntervalDS>) attrs[14],
                                      (Optional<Address>) attrs[15],
                                      (Optional<List<String>>) attrs[16]))
                          .asType()),
                  List.of(
                      new AllTypesStructNoLobsOptional(
                          Optional.of("varchar1"),
                          Optional.empty(),
                          Optional.of("char1     "),
                          Optional.empty(),
                          Optional.of(new BigDecimal("111.11")),
                          Optional.of(11),
                          Optional.empty(),
                          Optional.of(1.1f),
                          Optional.of(1.11),
                          Optional.of(LocalDateTime.of(2024, 1, 1, 10, 0)),
                          Optional.empty(),
                          Optional.of(OffsetDateTime.of(2024, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC)),
                          Optional.of(
                              OffsetDateTime.of(2024, 1, 1, 10, 0, 0, 0, ZoneOffset.ofHours(3))),
                          Optional.of(new OracleIntervalYM(1, 1)),
                          Optional.empty(),
                          Optional.of(
                              new Address("111 First St", "City1", coords("40.7128", "-74.006"))),
                          Optional.of(List.of("111-1111"))),
                      new AllTypesStructNoLobsOptional(
                          Optional.of("varchar2"),
                          Optional.of("nvarchar2"),
                          Optional.of("char2     "),
                          Optional.of("nchar2    "),
                          Optional.of(new BigDecimal("222.22")),
                          Optional.of(22),
                          Optional.of(2222L),
                          Optional.of(2.2f),
                          Optional.of(2.22),
                          Optional.of(LocalDateTime.of(2024, 2, 2, 20, 0)),
                          Optional.of(LocalDateTime.of(2024, 2, 2, 20, 0, 0, 222000000)),
                          Optional.of(
                              OffsetDateTime.of(2024, 2, 2, 20, 0, 0, 0, ZoneOffset.ofHours(-5))),
                          Optional.of(
                              OffsetDateTime.of(2024, 2, 2, 20, 0, 0, 0, ZoneOffset.ofHours(3))),
                          Optional.of(new OracleIntervalYM(2, 2)),
                          Optional.of(new OracleIntervalDS(2, 2, 2, 2, 222000000)),
                          Optional.of(
                              new Address(
                                  "222 Second St", "City2", coords("34.0522", "-118.2437"))),
                          Optional.of(List.of("222-2222", "222-3333")))),
                  List.of(
                      "BEGIN EXECUTE IMMEDIATE 'DROP TYPE TEST_ALLTYPES_NOLOBS_OPT_ARR';"
                          + " EXCEPTION WHEN OTHERS THEN NULL; END;",
                      "BEGIN EXECUTE IMMEDIATE 'DROP TYPE TEST_ALLTYPES_NOLOBS_OPT FORCE';"
                          + " EXCEPTION WHEN OTHERS THEN NULL; END;",
                      """
                      CREATE OR REPLACE TYPE TEST_ALLTYPES_NOLOBS_OPT AS OBJECT (
                        VARCHAR_FIELD VARCHAR2(100),
                        NVARCHAR_FIELD NVARCHAR2(100),
                        CHAR_FIELD CHAR(10),
                        NCHAR_FIELD NCHAR(10),
                        NUMBER_FIELD NUMBER,
                        NUMBER_INT_FIELD NUMBER(10),
                        NUMBER_LONG_FIELD NUMBER(19),
                        BINARY_FLOAT_FIELD BINARY_FLOAT,
                        BINARY_DOUBLE_FIELD BINARY_DOUBLE,
                        DATE_FIELD DATE,
                        TIMESTAMP_FIELD TIMESTAMP,
                        TIMESTAMP_TZ_FIELD TIMESTAMP WITH TIME ZONE,
                        TIMESTAMP_LTZ_FIELD TIMESTAMP WITH LOCAL TIME ZONE,
                        INTERVAL_YM_FIELD INTERVAL YEAR TO MONTH,
                        INTERVAL_DS_FIELD INTERVAL DAY TO SECOND,
                        NESTED_OBJECT_FIELD ADDRESS_T,
                        VARRAY_FIELD PHONE_LIST
                      )
                      """,
                      "CREATE OR REPLACE TYPE TEST_ALLTYPES_NOLOBS_OPT_ARR AS VARRAY(10)"
                          + " OF TEST_ALLTYPES_NOLOBS_OPT"))
              .noIdentity() // Complex array of structs - skip identity test
          );

  // Connection helper for Oracle - uses HikariCP connection pool
  // Uses Oracle Free 23c on port 1521, connecting to FREEPDB1 pluggable database
  static <T> T withConnection(SqlFunction<Connection, T> f) {
    try (var pooledConn = POOL.unwrap().getConnection()) {
      // Unwrap to get the underlying OracleConnection for STRUCT/ARRAY creation
      var conn = pooledConn.unwrap(oracle.jdbc.OracleConnection.class);
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
  public void test() {
    System.out.println("Testing Oracle type codecs...\n");

    // Test JSON roundtrip first (no database connection needed) - parallel
    System.out.println("=== JSON Roundtrip Tests (parallel) ===");
    All.parallelStream()
        .filter(t -> t.jsonRoundtripWorks)
        .forEach(OracleTypeTest::testJsonRoundtrip);
    System.out.println();

    // Create all user-defined types upfront (must be sequential to avoid conflicts)
    withConnection(
        conn -> {
          System.out.println("=== Creating user-defined types ===");
          var executedSql = new HashSet<String>();
          for (OracleTypeAndExample<?> t : All) {
            if (!t.setupSql.isEmpty()) {
              try (var stmt = conn.createStatement()) {
                for (String sql : t.setupSql) {
                  if (executedSql.add(sql)) {
                    try {
                      stmt.execute(sql);
                    } catch (SQLException e) {
                      if (!e.getMessage().contains("ORA-00955")
                          && !e.getMessage().contains("ORA-02303")) {
                        throw e;
                      }
                    }
                  }
                }
              }
            }
          }
          conn.commit();
          return null;
        });

    // Run all DB tests in parallel
    System.out.println("\n=== DB Roundtrip Tests (parallel) ===");
    var failures =
        All.parallelStream()
            .flatMap(
                t -> {
                  var errors = new ArrayList<String>();

                  // Native type roundtrip test
                  try {
                    withConnection(
                        conn -> {
                          testCase(conn, t);
                          return null;
                        });
                  } catch (Exception e) {
                    errors.add(
                        "Native test FAILED "
                            + t.type.typename().sqlType()
                            + ": "
                            + e.getMessage());
                  }

                  // JSON DB roundtrip test
                  if (t.jsonRoundtripWorks()) {
                    try {
                      withConnection(
                          conn -> {
                            testJsonDbRoundtrip(conn, t);
                            return null;
                          });
                    } catch (Exception e) {
                      errors.add(
                          "JSON DB test FAILED "
                              + t.type.typename().sqlType()
                              + ": "
                              + e.getMessage());
                    }
                  }

                  // getGeneratedKeys roundtrip test (skip user-defined types)
                  if (t.setupSql.isEmpty()) {
                    try {
                      withConnection(
                          conn -> {
                            testGeneratedKeysRoundtrip(conn, t);
                            return null;
                          });
                    } catch (Exception e) {
                      errors.add(
                          "getGeneratedKeys test FAILED "
                              + t.type.typename().sqlType()
                              + ": "
                              + e.getMessage());
                    }
                  }

                  return errors.stream();
                })
            .toList();

    System.out.println("\n=====================================");
    if (failures.isEmpty()) {
      System.out.println("All tests passed!");
    } else {
      failures.forEach(System.out::println);
      throw new RuntimeException(failures.size() + " tests failed");
    }
    System.out.println("=====================================");
  }

  /**
   * Test getGeneratedKeys roundtrip - simulates INSERT RETURNING behavior. Creates a table with an
   * auto-generated ID column plus a column of the type under test, inserts a value, and reads back
   * the entire row via getGeneratedKeys().
   */
  static <A> void testGeneratedKeysRoundtrip(Connection conn, OracleTypeAndExample<A> t)
      throws SQLException {
    String sqlType = t.type.typename().sqlType();
    A original = t.example;
    A expected = t.expected(); // May differ from original due to Oracle quirks

    // Create table with auto-generated ID + test column
    String tableName = uniqueTableName("TEST_GENKEYS");
    try (var stmt = conn.createStatement()) {
      stmt.execute(
          "CREATE TABLE "
              + tableName
              + " (id NUMBER GENERATED ALWAYS AS IDENTITY, v "
              + sqlType
              + ")");
    }

    try {
      // Insert using PreparedStatement with column names to get back via getGeneratedKeys
      String insertSql = "INSERT INTO " + tableName + " (v) VALUES (?)";
      var insert = conn.prepareStatement(insertSql, new String[] {"ID", "V"});
      t.type.write().set(insert, 1, original);
      insert.executeUpdate();

      // Read back via getGeneratedKeys
      var rs = insert.getGeneratedKeys();
      if (!rs.next()) {
        throw new RuntimeException("getGeneratedKeys returned no rows");
      }

      // Check metadata
      var meta = rs.getMetaData();
      System.out.println("getGeneratedKeys " + sqlType + ":");
      System.out.println("  Columns: " + meta.getColumnCount());
      for (int i = 1; i <= meta.getColumnCount(); i++) {
        System.out.println(
            "    " + i + ": " + meta.getColumnName(i) + " (" + meta.getColumnTypeName(i) + ")");
      }

      // Read ID (column 1)
      Long id = rs.getLong(1);
      System.out.println("  ID: " + id);

      // Read the value (column 2) - use optional reader if expecting NULL
      final A actual;
      if (expected == null) {
        Optional<A> actualOpt = t.type.opt().read().read(rs, 2);
        actual = actualOpt.orElse(null);
      } else {
        actual = t.type.read().read(rs, 2);
      }
      System.out.println("  Value: " + format(actual));

      rs.close();
      insert.close();

      assertEquals(actual, expected, "getGeneratedKeys value mismatch");
      System.out.println("  PASSED\n");

    } finally {
      // Drop table
      try (var stmt = conn.createStatement()) {
        stmt.execute("DROP TABLE " + tableName);
      }
    }
  }

  static <A> void testJsonRoundtrip(OracleTypeAndExample<A> t) {
    try {
      OracleJson<A> jsonCodec = t.type.oracleJson();
      A original = t.example;
      A expected = t.expected(); // May differ from original due to Oracle quirks

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

      if (t.hasIdentity && !areEqual(decoded, expected)) {
        throw new RuntimeException(
            "JSON roundtrip failed for "
                + t.type.typename().sqlType()
                + ": expected '"
                + format(expected)
                + "' but got '"
                + format(decoded)
                + "'");
      }
    } catch (Exception e) {
      throw new RuntimeException(
          "JSON roundtrip test failed for " + t.type.typename().sqlType(), e);
    }
  }

  // Test JSON roundtrip through the database - simulates MULTISET behavior
  // Insert value into native column, read back as JSON, parse back to value
  static <A> void testJsonDbRoundtrip(Connection conn, OracleTypeAndExample<A> t)
      throws SQLException {
    OracleJson<A> jsonCodec = t.type.oracleJson();
    A original = t.example;
    A expected = t.expected(); // May differ from original due to Oracle quirks
    String sqlType = t.type.typename().sqlType();

    // Create temp table (Oracle uses Global Temporary Tables differently, using regular table +
    // cleanup)
    String tableName = uniqueTableName("TEST_JSON_RT");
    try (var stmt = conn.createStatement()) {
      // NESTED TABLE columns require STORE AS clause
      String createTableDDL = "CREATE TABLE " + tableName + " (v " + sqlType + ")";
      if (sqlType.contains("ORDER_ITEMS_T")) { // Nested table type
        createTableDDL += " NESTED TABLE v STORE AS " + tableName + "_STORAGE";
      }
      stmt.execute(createTableDDL);
    }

    try {
      // Insert value using native type
      var insert = conn.prepareStatement("INSERT INTO " + tableName + " (v) VALUES (?)");
      t.type.write().set(insert, 1, original);
      insert.execute();
      insert.close();

      // Select back as JSON using JSON_OBJECT - this is what MULTISET does
      var select = conn.prepareStatement("SELECT JSON_OBJECT('v' VALUE v) FROM " + tableName);
      select.execute();
      var rs = select.getResultSet();

      if (!rs.next()) {
        throw new RuntimeException("No rows returned");
      }

      // Read the JSON string back from the database
      String jsonFromDb = rs.getString(1);
      rs.close();
      select.close();

      // Parse the JSON object and extract 'v' field
      JsonValue parsedFromDb = JsonValue.parse(jsonFromDb);
      JsonValue fieldValue = ((JsonValue.JObject) parsedFromDb).get("v");
      A decoded = jsonCodec.fromJson(fieldValue);

      System.out.println(
          "JSON DB roundtrip "
              + sqlType
              + ": "
              + format(original)
              + " -> DB -> "
              + jsonFromDb
              + " -> "
              + format(decoded));

      if (t.hasIdentity && !areEqual(decoded, expected)) {
        throw new RuntimeException(
            "JSON DB roundtrip failed for "
                + sqlType
                + ": expected '"
                + format(expected)
                + "' but got '"
                + format(decoded)
                + "'");
      }
    } finally {
      try (var stmt = conn.createStatement()) {
        stmt.execute("DROP TABLE " + tableName);
      }
    }
  }

  static <A> void testCase(Connection conn, OracleTypeAndExample<A> t) throws SQLException {
    String sqlType = t.type.typename().sqlType();

    // Execute setup SQL (for type definitions, etc.)
    if (!t.setupSql.isEmpty()) {
      try (var stmt = conn.createStatement()) {
        for (String sql : t.setupSql) {
          try {
            stmt.execute(sql);
          } catch (SQLException e) {
            // Ignore common type creation errors:
            // ORA-00955: name is already used by an existing object
            // ORA-02303: cannot DROP or REPLACE a type with type or table dependents
            if (!e.getMessage().contains("ORA-00955") && !e.getMessage().contains("ORA-02303")) {
              throw e;
            }
          }
        }
      }
    }

    // Create table (Oracle doesn't have CREATE TEMPORARY TABLE syntax in standard form)
    String tableName = uniqueTableName("TEST_TABLE");
    try (var stmt = conn.createStatement()) {
      // NESTED TABLE columns require STORE AS clause
      String createTableDDL = "CREATE TABLE " + tableName + " (v " + sqlType + ")";
      if (sqlType.contains("ORDER_ITEMS_T")
          || sqlType.contains("_NESTED_TABLE")) { // Nested table type
        createTableDDL += " NESTED TABLE v STORE AS " + tableName + "_STORAGE";
      }
      stmt.execute(createTableDDL);
    }

    try {
      // Insert using PreparedStatement
      var insert = conn.prepareStatement("INSERT INTO " + tableName + " (v) VALUES (?)");
      A original = t.example;
      A expected = t.expected(); // May differ from original due to Oracle quirks
      t.type.write().set(insert, 1, original);
      insert.execute();
      insert.close();

      // Select and verify
      final PreparedStatement select;
      if (t.hasIdentity) {
        // For NULL values, use IS NULL since WHERE v = NULL doesn't match (NULL = NULL is UNKNOWN)
        if (expected == null) {
          select = conn.prepareStatement("SELECT v, NULL FROM " + tableName + " WHERE v IS NULL");
        } else {
          select = conn.prepareStatement("SELECT v, NULL FROM " + tableName + " WHERE v = ?");
          t.type.write().set(select, 1, original);
        }
      } else {
        select = conn.prepareStatement("SELECT v, NULL FROM " + tableName);
      }

      select.execute();
      var rs = select.getResultSet();

      if (!rs.next()) {
        throw new RuntimeException("No rows returned");
      }

      // Read the value - use optional reader if expecting NULL
      final A actual;
      if (expected == null) {
        Optional<A> actualOpt = t.type.opt().read().read(rs, 1);
        actual = actualOpt.orElse(null);
      } else {
        actual = t.type.read().read(rs, 1);
      }

      // Read the null value using opt()
      Optional<A> actualNull = t.type.opt().read().read(rs, 2);

      rs.close();
      select.close();

      assertEquals(actual, expected, "value mismatch");
      assertEquals(actualNull, Optional.empty(), "null value mismatch");

    } finally {
      // Drop table
      try (var stmt = conn.createStatement()) {
        stmt.execute("DROP TABLE " + tableName);
      }
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

    // For BigDecimal, use compareTo to handle different scales
    if (expected instanceof BigDecimal && actual instanceof BigDecimal) {
      return ((BigDecimal) actual).compareTo((BigDecimal) expected) == 0;
    }

    // For Json, parse and compare structures (Oracle normalizes JSON formatting)
    if (expected instanceof Json && actual instanceof Json) {
      try {
        JsonValue v1 = JsonValue.parse(((Json) actual).value());
        JsonValue v2 = JsonValue.parse(((Json) expected).value());
        return v1.equals(v2);
      } catch (Exception e) {
        // If parsing fails, fall back to string comparison
        return ((Json) actual).value().equals(((Json) expected).value());
      }
    }

    return actual.equals(expected);
  }

  static <A> String format(A a) {
    return switch (a) {
      case null -> "null";
      case byte[] bytes -> bytesToHex(bytes);
      case Object[] objects -> Arrays.deepToString(objects);
      default -> a.toString();
    };
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

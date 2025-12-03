package typo.runtime;

import typo.data.Json;
import typo.data.maria.Inet4;
import typo.data.maria.Inet6;
import typo.data.maria.MariaSet;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Tester for MariaDB type codecs.
 * Similar to tester.java but for MariaDB types.
 * Tests all types defined in MariaTypes.
 */
public interface mariaTester {

    record TestPair<A>(A t0, Optional<A> t1) {}

    record MariaTypeAndExample<A>(MariaType<A> type, A example, boolean hasIdentity, boolean streamingWorks) {
        public MariaTypeAndExample(MariaType<A> type, A example) {
            this(type, example, true, true);
        }

        public MariaTypeAndExample<A> noStreaming() {
            return new MariaTypeAndExample<>(type, example, hasIdentity, false);
        }

        public MariaTypeAndExample<A> noIdentity() {
            return new MariaTypeAndExample<>(type, example, false, streamingWorks);
        }
    }

    // Sample enum for ENUM type testing
    enum Color { RED, GREEN, BLUE }

    List<MariaTypeAndExample<?>> All = List.of(
            // ==================== Integer Types (Signed) ====================
            new MariaTypeAndExample<>(MariaTypes.tinyint, (byte) 42),
            new MariaTypeAndExample<>(MariaTypes.smallint, (short) 4242),
            new MariaTypeAndExample<>(MariaTypes.mediumint, 424242),
            new MariaTypeAndExample<>(MariaTypes.int_, 42424242),
            new MariaTypeAndExample<>(MariaTypes.bigint, 4242424242424242L),

            // ==================== Integer Types (Unsigned) ====================
            new MariaTypeAndExample<>(MariaTypes.tinyintUnsigned, (short) 255), // Max TINYINT UNSIGNED
            new MariaTypeAndExample<>(MariaTypes.smallintUnsigned, 65535), // Max SMALLINT UNSIGNED
            new MariaTypeAndExample<>(MariaTypes.mediumintUnsigned, 16777215), // Max MEDIUMINT UNSIGNED
            new MariaTypeAndExample<>(MariaTypes.intUnsigned, 4294967295L), // Max INT UNSIGNED
            new MariaTypeAndExample<>(MariaTypes.bigintUnsigned, new BigInteger("18446744073709551615")), // Max BIGINT UNSIGNED

            // ==================== Fixed-Point Types ====================
            // Note: DECIMAL/NUMERIC without precision default to (10,0) in MariaDB, causing rounding
            // We test with integer values for the base types and use explicit precision for decimals
            new MariaTypeAndExample<>(MariaTypes.decimal, new BigDecimal("12345")),
            new MariaTypeAndExample<>(MariaTypes.numeric, new BigDecimal("99999")),
            new MariaTypeAndExample<>(MariaTypes.decimal(10, 2), new BigDecimal("12345678.90")),
            new MariaTypeAndExample<>(MariaTypes.decimal(10, 5), new BigDecimal("12345.67890")),

            // ==================== Floating-Point Types ====================
            // Note: FLOAT has precision issues with identity comparison
            new MariaTypeAndExample<>(MariaTypes.float_, 3.14159f).noIdentity(),
            new MariaTypeAndExample<>(MariaTypes.double_, 3.141592653589793),

            // ==================== Boolean Type ====================
            new MariaTypeAndExample<>(MariaTypes.bool, true),
            new MariaTypeAndExample<>(MariaTypes.bool, false),

            // ==================== Bit Types ====================
            new MariaTypeAndExample<>(MariaTypes.bit1, true),
            // BIT without length defaults to BIT(1), need to specify length for multi-byte
            // Skipping multi-byte BIT test as it requires type with explicit length

            // ==================== String Types ====================
            // Note: MariaDB JDBC driver strips trailing spaces from CHAR, so we test without them
            new MariaTypeAndExample<>(MariaTypes.char_(10), "hello"),
            new MariaTypeAndExample<>(MariaTypes.varchar(255), "Hello, MariaDB! Unicode: \u00e9\u00e8\u00ea \u4e2d\u6587"),
            new MariaTypeAndExample<>(MariaTypes.tinytext, "tiny text content"),
            new MariaTypeAndExample<>(MariaTypes.text, "Regular text content with special chars: ,.;{}[]-//#\u00ae\u2705"),
            new MariaTypeAndExample<>(MariaTypes.mediumtext, "Medium text can hold up to 16MB"),
            new MariaTypeAndExample<>(MariaTypes.longtext, "Long text can hold up to 4GB"),

            // ==================== Binary Types ====================
            new MariaTypeAndExample<>(MariaTypes.binary(5), new byte[]{0x01, 0x02, 0x03, 0x00, 0x00}), // BINARY pads with 0x00
            new MariaTypeAndExample<>(MariaTypes.varbinary(255), new byte[]{(byte) 0xFF, 0x00, 0x7F, (byte) 0x80}),
            new MariaTypeAndExample<>(MariaTypes.tinyblob, new byte[]{0x01, 0x02, 0x03}),
            new MariaTypeAndExample<>(MariaTypes.blob, new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF}),
            new MariaTypeAndExample<>(MariaTypes.mediumblob, new byte[]{0x00, 0x11, 0x22, 0x33, 0x44, 0x55}),
            new MariaTypeAndExample<>(MariaTypes.longblob, new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE}),

            // ==================== Date/Time Types ====================
            new MariaTypeAndExample<>(MariaTypes.date, LocalDate.of(2024, 6, 15)),
            new MariaTypeAndExample<>(MariaTypes.time, LocalTime.of(14, 30, 45)),
            new MariaTypeAndExample<>(MariaTypes.time(3), LocalTime.of(14, 30, 45, 123000000)), // TIME(3) with milliseconds
            new MariaTypeAndExample<>(MariaTypes.datetime, LocalDateTime.of(2024, 6, 15, 14, 30, 45)),
            new MariaTypeAndExample<>(MariaTypes.datetime(6), LocalDateTime.of(2024, 6, 15, 14, 30, 45, 123456000)), // DATETIME(6) with microseconds
            new MariaTypeAndExample<>(MariaTypes.timestamp, LocalDateTime.of(2024, 6, 15, 14, 30, 45)),
            new MariaTypeAndExample<>(MariaTypes.timestamp(6), LocalDateTime.of(2024, 6, 15, 14, 30, 45, 123456000)),
            new MariaTypeAndExample<>(MariaTypes.year, Year.of(2024)),

            // ==================== ENUM Type ====================
            new MariaTypeAndExample<>(MariaTypes.ofEnum("ENUM('RED','GREEN','BLUE')", s -> Color.valueOf(s)), Color.GREEN),

            // ==================== SET Type ====================
            new MariaTypeAndExample<>(MariaTypes.set.withTypename("SET('email','sms','push')"), MariaSet.of("email", "push")),
            new MariaTypeAndExample<>(MariaTypes.set.withTypename("SET('a','b','c')"), MariaSet.empty()),
            new MariaTypeAndExample<>(MariaTypes.set.withTypename("SET('x','y','z')"), MariaSet.of("x", "y", "z")),

            // ==================== JSON Type ====================
            new MariaTypeAndExample<>(MariaTypes.json, new Json("{\"name\": \"MariaDB\", \"version\": 10.11}")).noIdentity(),
            new MariaTypeAndExample<>(MariaTypes.json, new Json("[1, 2, 3, \"four\"]")).noIdentity(),

            // ==================== Network Types (MariaDB 10.10+) ====================
            new MariaTypeAndExample<>(MariaTypes.inet4, Inet4.parse("192.168.1.100")),
            new MariaTypeAndExample<>(MariaTypes.inet4, Inet4.parse("10.0.0.1")),
            new MariaTypeAndExample<>(MariaTypes.inet6, Inet6.parse("2001:db8::1")),
            new MariaTypeAndExample<>(MariaTypes.inet6, Inet6.parse("::ffff:192.168.1.1")) // IPv4-mapped

            // ==================== Spatial Types ====================
            // Note: Spatial types return WKB (Well-Known Binary) by default from MariaDB.
            // The MariaDB JDBC driver needs specific configuration to return geometry objects.
            // These tests are skipped for now as they require additional connection parameters
            // or ST_AsText/ST_GeomFromText functions for string-based round-tripping.
            // TODO: Implement proper geometry handling with WKB parsing or TEXT conversion
    );

    // Connection helper for MariaDB
    static <T> T withConnection(SqlFunction<Connection, T> f) {
        try (var conn = java.sql.DriverManager.getConnection(
                "jdbc:mariadb://localhost:3307/typo?user=typo&password=password")) {
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

    // Main entry point
    static void main(String[] args) {
        System.out.println("Testing MariaDB type codecs...\n");

        withConnection(conn -> {
            int passed = 0;
            int failed = 0;

            for (MariaTypeAndExample<?> t : All) {
                String typeName = t.type.typename().sqlType();
                try {
                    System.out.println("Testing " + typeName + " with example '" + format(t.example) + "'");
                    testCase(conn, t);
                    System.out.println("  PASSED\n");
                    passed++;
                } catch (Exception e) {
                    System.out.println("  FAILED: " + e.getMessage() + "\n");
                    e.printStackTrace();
                    failed++;
                }
            }

            System.out.println("=====================================");
            System.out.println("Results: " + passed + " passed, " + failed + " failed out of " + All.size() + " tests");
            System.out.println("=====================================");

            if (failed > 0) {
                throw new RuntimeException(failed + " tests failed");
            }

            return null;
        });
    }

    static <A> void testCase(Connection conn, MariaTypeAndExample<A> t) throws SQLException {
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
            conn.createStatement().execute("DROP TEMPORARY TABLE IF EXISTS test_table");
        }
    }

    static <A> void assertEquals(A actual, A expected, String message) {
        if (!areEqual(actual, expected)) {
            throw new RuntimeException(message + ": actual='" + format(actual) + "' expected='" + format(expected) + "'");
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

        // For spatial types, compare string representations
        if (expected instanceof org.mariadb.jdbc.type.Geometry) {
            return actual.toString().equals(expected.toString());
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

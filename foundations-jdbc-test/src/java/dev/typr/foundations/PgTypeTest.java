package dev.typr.foundations;

import dev.typr.foundations.data.*;
import dev.typr.foundations.data.JsonValue;
import dev.typr.foundations.data.Vector;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.Test;
import org.postgresql.geometric.*;
import org.postgresql.jdbc.PgConnection;
import org.postgresql.util.PGInterval;

public class PgTypeTest {

  // PostgreSQL only supports microsecond precision (6 digits), but Java's now() methods
  // return nanosecond precision (9 digits). Truncate to ensure roundtrip equality.
  private static LocalTime nowTime() {
    return LocalTime.now().truncatedTo(ChronoUnit.MICROS);
  }

  private static LocalDateTime nowDateTime() {
    return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
  }

  private static Instant nowInstant() {
    return Instant.now().truncatedTo(ChronoUnit.MICROS);
  }

  private static OffsetTime nowOffsetTime() {
    return OffsetTime.now().truncatedTo(ChronoUnit.MICROS);
  }

  record TestPair<A>(A t0, Optional<A> t1) {}

  record PgTypeAndExample<A>(
      PgType<A> type,
      A example,
      boolean hasIdentity,
      boolean streamingWorks,
      boolean compositeTextWorks) {
    public PgTypeAndExample(PgType<A> type, A example) {
      this(type, example, true, true, true);
    }

    public PgTypeAndExample<A> noStreaming() {
      return new PgTypeAndExample<>(type, example, hasIdentity, false, compositeTextWorks);
    }

    public PgTypeAndExample<A> noIdentity() {
      return new PgTypeAndExample<>(type, example, false, streamingWorks, compositeTextWorks);
    }

    public PgTypeAndExample<A> noCompositeText() {
      return new PgTypeAndExample<>(type, example, hasIdentity, streamingWorks, false);
    }
  }

  List<PgTypeAndExample<?>> All =
      List.<PgTypeAndExample<?>>of(
          // ==================== ACL Item Types ====================
          new PgTypeAndExample<>(PgTypes.aclitem, new AclItem("postgres=r*w/postgres")),
          new PgTypeAndExample<>(
              PgTypes.aclitemArray, new AclItem[] {new AclItem("postgres=r*w/postgres")}),

          // ==================== Boolean Types ====================
          new PgTypeAndExample<>(PgTypes.bool, true),
          new PgTypeAndExample<>(PgTypes.bool, false), // Edge case: false value
          new PgTypeAndExample<>(PgTypes.boolArray, new Boolean[] {true, false}),
          new PgTypeAndExample<>(PgTypes.boolArray, new Boolean[] {}), // Edge case: empty array
          new PgTypeAndExample<>(PgTypes.boolArrayUnboxed, new boolean[] {true, false}),
          new PgTypeAndExample<>(
              PgTypes.boolArrayUnboxed, new boolean[] {}), // Edge case: empty array

          // ==================== Geometric Types ====================
          new PgTypeAndExample<>(PgTypes.box, new PGbox(42, 42, 42, 42)).noIdentity(),
          new PgTypeAndExample<>(PgTypes.box, new PGbox(-100, -50, 100, 50))
              .noIdentity(), // Edge case: negative coords
          new PgTypeAndExample<>(PgTypes.boxArray, new PGbox[] {new PGbox(42, 42, 42, 42)})
              .noIdentity(),
          new PgTypeAndExample<>(PgTypes.circle, new PGcircle(new PGpoint(0.01, 42.34), 101.2)),
          new PgTypeAndExample<>(PgTypes.circle, new PGcircle(new PGpoint(0, 0), 0))
              .noIdentity(), // Edge case: zero radius
          new PgTypeAndExample<>(
                  PgTypes.circleArray,
                  new PGcircle[] {new PGcircle(new PGpoint(0.01, 42.34), 101.2)})
              .noIdentity(),
          new PgTypeAndExample<>(PgTypes.line, new PGline(1.1, 2.2, 3.3)).noIdentity(),
          new PgTypeAndExample<>(PgTypes.lineArray, new PGline[] {new PGline(1.1, 2.2, 3.3)})
              .noIdentity(),
          new PgTypeAndExample<>(PgTypes.lseg, new PGlseg(1.1, 2.2, 3.3, 4.4)).noIdentity(),
          new PgTypeAndExample<>(PgTypes.lsegArray, new PGlseg[] {new PGlseg(1.1, 2.2, 3.3, 4.4)})
              .noIdentity(),
          new PgTypeAndExample<>(
                  PgTypes.path,
                  new PGpath(new PGpoint[] {new PGpoint(1.1, 2.2), new PGpoint(3.3, 4.4)}, true))
              .noIdentity(),
          new PgTypeAndExample<>(
                  PgTypes.pathArray,
                  new PGpath[] {
                    new PGpath(new PGpoint[] {new PGpoint(1.1, 2.2), new PGpoint(3.3, 4.4)}, true)
                  })
              .noIdentity(),
          new PgTypeAndExample<>(PgTypes.point, new PGpoint(1.1, 2.2)).noIdentity(),
          new PgTypeAndExample<>(PgTypes.point, new PGpoint(0, 0))
              .noIdentity(), // Edge case: origin
          new PgTypeAndExample<>(PgTypes.pointArray, new PGpoint[] {new PGpoint(1.1, 2.2)})
              .noIdentity(),
          new PgTypeAndExample<>(
                  PgTypes.polygon,
                  new PGpolygon(new PGpoint[] {new PGpoint(1.1, 2.2), new PGpoint(3.3, 4.4)}))
              .noIdentity(),
          new PgTypeAndExample<>(
                  PgTypes.polygonArray,
                  new PGpolygon[] {
                    new PGpolygon(new PGpoint[] {new PGpoint(1.1, 2.2), new PGpoint(3.3, 4.4)})
                  })
              .noIdentity(),

          // ==================== Character Types ====================
          new PgTypeAndExample<>(PgTypes.bpchar(5), "377  "),
          new PgTypeAndExample<>(PgTypes.bpchar, "377"),
          new PgTypeAndExample<>(PgTypes.bpchar, ""), // Edge case: empty string
          new PgTypeAndExample<>(PgTypes.bpcharArray(5), new String[] {"377  "}),
          new PgTypeAndExample<>(PgTypes.bpcharArray, new String[] {"10101"}),
          new PgTypeAndExample<>(PgTypes.text, ",.;{}[]-//#®✅"),
          new PgTypeAndExample<>(PgTypes.text, ""), // Edge case: empty string
          new PgTypeAndExample<>(
              PgTypes.text, "Line1\nLine2\tTabbed"), // Edge case: whitespace chars
          new PgTypeAndExample<>(PgTypes.text, "Quote\"Test'Single"), // Edge case: quotes
          new PgTypeAndExample<>(PgTypes.text, "Emoji: 😀🎉🚀"), // Edge case: emoji
          new PgTypeAndExample<>(PgTypes.textArray, new String[] {",.;{}[]-//#®✅"}),
          new PgTypeAndExample<>(
              PgTypes.textArray, new String[] {"a", "b", "c"}), // Edge case: multiple elements
          new PgTypeAndExample<>(PgTypes.textArray, new String[] {}), // Edge case: empty array

          // ==================== Binary Types ====================
          new PgTypeAndExample<>(PgTypes.bytea, new byte[] {-1, 1, 127}),
          new PgTypeAndExample<>(PgTypes.bytea, new byte[] {}), // Edge case: empty byte array
          new PgTypeAndExample<>(PgTypes.bytea, new byte[] {0, 0, 0}), // Edge case: all zeros
          new PgTypeAndExample<>(
              PgTypes.bytea,
              new byte[] {(byte) 0xFF, (byte) 0xFE, (byte) 0xFD}), // Edge case: high bytes

          // ==================== Date/Time Types ====================
          new PgTypeAndExample<>(PgTypes.date, LocalDate.now()),
          new PgTypeAndExample<>(PgTypes.date, LocalDate.of(1970, 1, 1)), // Edge case: epoch
          new PgTypeAndExample<>(PgTypes.date, LocalDate.of(2099, 12, 31)), // Edge case: far future
          new PgTypeAndExample<>(PgTypes.dateArray, new LocalDate[] {LocalDate.now()}),
          new PgTypeAndExample<>(PgTypes.time, nowTime()),
          new PgTypeAndExample<>(PgTypes.time, LocalTime.of(0, 0, 0)), // Edge case: midnight
          new PgTypeAndExample<>(
              PgTypes.time, LocalTime.of(23, 59, 59, 999999000)), // Edge case: end of day
          new PgTypeAndExample<>(PgTypes.timeArray, new LocalTime[] {nowTime()}),
          new PgTypeAndExample<>(PgTypes.timestamp, nowDateTime()),
          new PgTypeAndExample<>(
              PgTypes.timestamp, LocalDateTime.of(1970, 1, 1, 0, 0, 0)), // Edge case: epoch
          new PgTypeAndExample<>(PgTypes.timestampArray, new LocalDateTime[] {nowDateTime()}),
          new PgTypeAndExample<>(PgTypes.timestamptz, nowInstant()),
          new PgTypeAndExample<>(PgTypes.timestamptz, Instant.EPOCH), // Edge case: epoch
          new PgTypeAndExample<>(PgTypes.timestamptzArray, new Instant[] {nowInstant()}),
          new PgTypeAndExample<>(PgTypes.timetz, nowOffsetTime()),
          new PgTypeAndExample<>(PgTypes.timetzArray, new OffsetTime[] {nowOffsetTime()}),
          new PgTypeAndExample<>(PgTypes.interval, new PGInterval(1, 2, 3, 4, 5, 6.666)),
          new PgTypeAndExample<>(PgTypes.interval, new PGInterval(0, 0, 0, 0, 0, 0))
              .noIdentity(), // Edge case: zero interval
          new PgTypeAndExample<>(
                  PgTypes.intervalArray, new PGInterval[] {new PGInterval(1, 2, 3, 4, 5, 6.666)})
              .noIdentity(),

          // ==================== Numeric Types ====================
          new PgTypeAndExample<>(PgTypes.int2, (short) 42),
          new PgTypeAndExample<>(PgTypes.int2, Short.MIN_VALUE), // Edge case: min value
          new PgTypeAndExample<>(PgTypes.int2, Short.MAX_VALUE), // Edge case: max value
          new PgTypeAndExample<>(PgTypes.int2, (short) 0), // Edge case: zero
          new PgTypeAndExample<>(PgTypes.int2Array, new Short[] {42}),
          new PgTypeAndExample<>(PgTypes.int2ArrayUnboxed, new short[] {42}),
          new PgTypeAndExample<>(
              PgTypes.int2ArrayUnboxed, new short[] {}), // Edge case: empty array
          new PgTypeAndExample<>(PgTypes.int4, 42),
          new PgTypeAndExample<>(PgTypes.int4, Integer.MIN_VALUE), // Edge case: min value
          new PgTypeAndExample<>(PgTypes.int4, Integer.MAX_VALUE), // Edge case: max value
          new PgTypeAndExample<>(PgTypes.int4, 0), // Edge case: zero
          new PgTypeAndExample<>(PgTypes.int4Array, new Integer[] {42}),
          new PgTypeAndExample<>(PgTypes.int4ArrayUnboxed, new int[] {42}),
          new PgTypeAndExample<>(PgTypes.int4ArrayUnboxed, new int[] {}), // Edge case: empty array
          new PgTypeAndExample<>(PgTypes.int8, 42L),
          new PgTypeAndExample<>(PgTypes.int8, Long.MIN_VALUE), // Edge case: min value
          new PgTypeAndExample<>(PgTypes.int8, Long.MAX_VALUE), // Edge case: max value
          new PgTypeAndExample<>(PgTypes.int8, 0L), // Edge case: zero
          new PgTypeAndExample<>(PgTypes.int8Array, new Long[] {42L}),
          new PgTypeAndExample<>(PgTypes.int8ArrayUnboxed, new long[] {42L}),
          new PgTypeAndExample<>(PgTypes.int8ArrayUnboxed, new long[] {}), // Edge case: empty array
          new PgTypeAndExample<>(PgTypes.float4, 42.42f),
          new PgTypeAndExample<>(PgTypes.float4, 0.0f), // Edge case: zero
          new PgTypeAndExample<>(PgTypes.float4, 1.0E-38f), // Edge case: small positive
          new PgTypeAndExample<>(PgTypes.float4Array, new Float[] {42.42f}),
          new PgTypeAndExample<>(PgTypes.float4ArrayUnboxed, new float[] {42.42f}),
          new PgTypeAndExample<>(
              PgTypes.float4ArrayUnboxed, new float[] {}), // Edge case: empty array
          new PgTypeAndExample<>(PgTypes.float8, 42.42),
          new PgTypeAndExample<>(PgTypes.float8, 0.0), // Edge case: zero
          new PgTypeAndExample<>(PgTypes.float8, Double.MAX_VALUE), // Edge case: max value
          new PgTypeAndExample<>(PgTypes.float8Array, new Double[] {42.42}),
          new PgTypeAndExample<>(PgTypes.float8ArrayUnboxed, new double[] {42.42}),
          new PgTypeAndExample<>(
              PgTypes.float8ArrayUnboxed, new double[] {}), // Edge case: empty array
          new PgTypeAndExample<>(PgTypes.numeric, new BigDecimal("0.002")),
          new PgTypeAndExample<>(PgTypes.numeric, BigDecimal.ZERO), // Edge case: zero
          new PgTypeAndExample<>(
              PgTypes.numeric,
              new BigDecimal("-99999999999999.999999999999")), // Edge case: large negative
          new PgTypeAndExample<>(
              PgTypes.numeric,
              new BigDecimal("99999999999999.999999999999")), // Edge case: large positive
          new PgTypeAndExample<>(PgTypes.numericArray, new BigDecimal[] {new BigDecimal("0.002")}),
          new PgTypeAndExample<>(PgTypes.smallint, (short) 42),
          new PgTypeAndExample<>(PgTypes.smallintArray, new Short[] {42}),
          new PgTypeAndExample<>(PgTypes.smallintArrayUnboxed, new short[] {42}),
          new PgTypeAndExample<>(PgTypes.money, new Money("42.22")),
          new PgTypeAndExample<>(PgTypes.money, new Money("0.00")), // Edge case: zero
          new PgTypeAndExample<>(PgTypes.money, new Money("-999.99")), // Edge case: negative
          new PgTypeAndExample<>(PgTypes.moneyArray, new Money[] {new Money("42.22")}),

          // ==================== Vector Types ====================
          new PgTypeAndExample<>(PgTypes.int2vector, new Int2Vector(new short[] {1, 2, 3})),
          new PgTypeAndExample<>(
              PgTypes.int2vectorArray, new Int2Vector[] {new Int2Vector(new short[] {1, 2, 3})}),
          new PgTypeAndExample<>(PgTypes.oidvector, new OidVector(new int[] {1, 2, 3})),
          new PgTypeAndExample<>(
              PgTypes.oidvectorArray, new OidVector[] {new OidVector(new int[] {1, 2, 3})}),
          new PgTypeAndExample<>(PgTypes.vector, new Vector(new float[] {1.0f, 2.0f, 3.0f})),
          new PgTypeAndExample<>(
              PgTypes.vector, new Vector(new float[] {0.0f, 0.0f, 0.0f})), // Edge case: zero vector
          new PgTypeAndExample<>(
              PgTypes.vectorArray, new Vector[] {new Vector(new float[] {1.0f, 2.0f, 3.0f})}),

          // ==================== Identifier Types ====================
          new PgTypeAndExample<>(PgTypes.name, "my_table_name"),
          new PgTypeAndExample<>(PgTypes.name, "a"), // Edge case: short name
          new PgTypeAndExample<>(
              PgTypes.name,
              "this_is_a_very_long_identifier_name_close_to_63_chars_limit"), // Edge case: long
          // name
          new PgTypeAndExample<>(PgTypes.nameArray, new String[] {"my_table", "my_column"}),
          new PgTypeAndExample<>(PgTypes.nameArray, new String[] {}), // Edge case: empty array

          // ==================== Network Types ====================
          new PgTypeAndExample<>(PgTypes.inet, new Inet("10.1.0.0")),
          new PgTypeAndExample<>(
              PgTypes.inet, new Inet("192.168.1.1")), // Edge case: common private IP
          new PgTypeAndExample<>(PgTypes.inet, new Inet("255.255.255.255")), // Edge case: broadcast
          new PgTypeAndExample<>(PgTypes.inet, new Inet("0.0.0.0")), // Edge case: any address
          new PgTypeAndExample<>(PgTypes.inetArray, new Inet[] {new Inet("10.1.0.0")}),

          // CIDR - network addresses
          new PgTypeAndExample<>(PgTypes.cidr, new Cidr("192.168.1.0/24")),
          new PgTypeAndExample<>(PgTypes.cidr, new Cidr("10.0.0.0/8")), // Edge case: Class A
          new PgTypeAndExample<>(
              PgTypes.cidr, new Cidr("172.16.0.0/12")), // Edge case: Class B private
          new PgTypeAndExample<>(PgTypes.cidrArray, new Cidr[] {new Cidr("192.168.1.0/24")}),

          // MAC addresses (6-byte format)
          new PgTypeAndExample<>(PgTypes.macaddr, new MacAddr("08:00:2b:01:02:03")),
          new PgTypeAndExample<>(
              PgTypes.macaddr, new MacAddr("00:00:00:00:00:00")), // Edge case: all zeros
          new PgTypeAndExample<>(
              PgTypes.macaddr, new MacAddr("ff:ff:ff:ff:ff:ff")), // Edge case: broadcast
          new PgTypeAndExample<>(
              PgTypes.macaddrArray, new MacAddr[] {new MacAddr("08:00:2b:01:02:03")}),

          // MAC addresses (8-byte format, EUI-64)
          new PgTypeAndExample<>(PgTypes.macaddr8, new MacAddr8("08:00:2b:01:02:03:04:05")),
          new PgTypeAndExample<>(
              PgTypes.macaddr8, new MacAddr8("00:00:00:00:00:00:00:00")), // Edge case: all zeros
          new PgTypeAndExample<>(
              PgTypes.macaddr8, new MacAddr8("ff:ff:ff:ff:ff:ff:ff:ff")), // Edge case: all ones
          new PgTypeAndExample<>(
              PgTypes.macaddr8Array, new MacAddr8[] {new MacAddr8("08:00:2b:01:02:03:04:05")}),

          // ==================== Key-Value Types ====================
          new PgTypeAndExample<>(PgTypes.hstore, Map.of(",.;{}[]-//#®✅", ",.;{}[]-//#®✅")),
          new PgTypeAndExample<>(PgTypes.hstore, Map.of()), // Edge case: empty map
          new PgTypeAndExample<>(
              PgTypes.hstore,
              Map.of("key1", "value1", "key2", "value2")), // Edge case: multiple entries

          // ==================== JSON Types ====================
          new PgTypeAndExample<>(PgTypes.json, new Json("{\"A\": 42}")).noIdentity(),
          new PgTypeAndExample<>(PgTypes.json, new Json("{}"))
              .noIdentity(), // Edge case: empty object
          new PgTypeAndExample<>(PgTypes.json, new Json("[]"))
              .noIdentity(), // Edge case: empty array
          new PgTypeAndExample<>(PgTypes.json, new Json("null")).noIdentity(), // Edge case: null
          new PgTypeAndExample<>(PgTypes.json, new Json("\"string\""))
              .noIdentity(), // Edge case: string value
          new PgTypeAndExample<>(PgTypes.jsonArray, new Json[] {new Json("{\"A\": 42}")})
              .noIdentity()
              .noStreaming(),
          new PgTypeAndExample<>(PgTypes.jsonb, new Jsonb("{\"A\": 42}"))
              .noIdentity(), // Whitespace normalized
          new PgTypeAndExample<>(PgTypes.jsonb, new Jsonb("{}"))
              .noIdentity(), // Edge case: empty object
          new PgTypeAndExample<>(PgTypes.jsonbArray, new Jsonb[] {new Jsonb("{\"A\": 42}")})
              .noIdentity()
              .noStreaming(),

          // ==================== Record Types ====================
          // TODO: Record JSON roundtrip needs special handling - PostgreSQL returns composite types
          // as JSON objects
          // with field names (e.g., {"r":1,"i":2}), but Record stores tuple format "(1,2)".
          // We'll implement something clever later using json_populate_record or similar.
          // new PgTypeAndExample<>(PgTypes.record("complex"), new Record("(1,2)")),
          // new PgTypeAndExample<>(PgTypes.recordArray("complex"), new Record[]{new
          // Record("(1,2)")}),

          // ==================== Reg* Types ====================
          new PgTypeAndExample<>(PgTypes.regconfig, new Regconfig("danish")),
          new PgTypeAndExample<>(
              PgTypes.regconfig, new Regconfig("english")), // Edge case: common config
          new PgTypeAndExample<>(PgTypes.regconfigArray, new Regconfig[] {new Regconfig("danish")}),
          new PgTypeAndExample<>(PgTypes.regdictionary, new Regdictionary("english_stem")),
          new PgTypeAndExample<>(
              PgTypes.regdictionaryArray, new Regdictionary[] {new Regdictionary("english_stem")}),
          new PgTypeAndExample<>(PgTypes.regnamespace, new Regnamespace("public")),
          new PgTypeAndExample<>(
              PgTypes.regnamespace, new Regnamespace("pg_catalog")), // Edge case: system namespace
          new PgTypeAndExample<>(
              PgTypes.regnamespaceArray, new Regnamespace[] {new Regnamespace("public")}),
          new PgTypeAndExample<>(PgTypes.regoperator, new Regoperator("-(bigint,bigint)")),
          new PgTypeAndExample<>(
              PgTypes.regoperatorArray, new Regoperator[] {new Regoperator("-(bigint,bigint)")}),
          new PgTypeAndExample<>(PgTypes.regprocedure, new Regprocedure("sum(integer)")),
          new PgTypeAndExample<>(
              PgTypes.regprocedureArray, new Regprocedure[] {new Regprocedure("sum(integer)")}),
          new PgTypeAndExample<>(PgTypes.regrole, new Regrole("pg_monitor")),
          new PgTypeAndExample<>(PgTypes.regroleArray, new Regrole[] {new Regrole("pg_monitor")}),
          new PgTypeAndExample<>(PgTypes.regtype, new Regtype("integer")),
          new PgTypeAndExample<>(PgTypes.regtype, new Regtype("text")), // Edge case: different type
          new PgTypeAndExample<>(PgTypes.regtypeArray, new Regtype[] {new Regtype("integer")}),

          // ==================== Transaction ID Types ====================
          new PgTypeAndExample<>(PgTypes.xid, new Xid("1")),
          new PgTypeAndExample<>(PgTypes.xidArray, new Xid[] {new Xid("1")}),

          // ==================== UUID Types ====================
          new PgTypeAndExample<>(PgTypes.uuid, UUID.randomUUID()),
          new PgTypeAndExample<>(PgTypes.uuid, new UUID(0, 0)), // Edge case: nil UUID
          new PgTypeAndExample<>(PgTypes.uuid, new UUID(-1, -1)), // Edge case: max UUID
          new PgTypeAndExample<>(PgTypes.uuidArray, new UUID[] {UUID.randomUUID()}),
          new PgTypeAndExample<>(PgTypes.uuidArray, new UUID[] {}), // Edge case: empty array

          // ==================== XML Types ====================
          new PgTypeAndExample<>(PgTypes.xml, new Xml("<a>42</a>")).noIdentity(),
          new PgTypeAndExample<>(
                  PgTypes.xml, new Xml("<root><child attr=\"value\">text</child></root>"))
              .noIdentity(), // Edge case: nested
          new PgTypeAndExample<>(PgTypes.xmlArray, new Xml[] {new Xml("<a>42</a>")}).noIdentity(),

          // ==================== Range Types ====================
          // int4range - uses Range.int4() which normalizes to [) form
          new PgTypeAndExample<>(
              PgTypes.int4range, Range.int4(new RangeBound.Closed<>(1), new RangeBound.Open<>(10))),
          new PgTypeAndExample<>(
              PgTypes.int4range,
              Range.int4(
                  new RangeBound.Closed<>(0), new RangeBound.Closed<>(100))), // [0,100] -> [0,101)
          new PgTypeAndExample<>(
              PgTypes.int4range,
              Range.int4(RangeBound.infinite(), new RangeBound.Open<>(10))), // unbounded lower
          new PgTypeAndExample<>(
              PgTypes.int4range,
              Range.int4(new RangeBound.Closed<>(1), RangeBound.infinite())), // unbounded upper
          new PgTypeAndExample<>(
              PgTypes.int4range,
              Range.int4(RangeBound.infinite(), RangeBound.infinite())), // fully unbounded
          new PgTypeAndExample<>(PgTypes.int4range, Range.empty()), // empty range
          new PgTypeAndExample<>(
              PgTypes.int4rangeArray,
              new Range[] {Range.int4(new RangeBound.Closed<>(1), new RangeBound.Open<>(10))}),

          // int8range - uses Range.int8() which normalizes to [) form
          new PgTypeAndExample<>(
              PgTypes.int8range,
              Range.int8(new RangeBound.Closed<>(1L), new RangeBound.Open<>(1000000L))),
          new PgTypeAndExample<>(
              PgTypes.int8range,
              Range.int8(
                  new RangeBound.Closed<>(Long.MIN_VALUE + 1),
                  new RangeBound.Open<>(Long.MAX_VALUE))),
          new PgTypeAndExample<>(PgTypes.int8range, Range.empty()),
          new PgTypeAndExample<>(
              PgTypes.int8rangeArray,
              new Range[] {Range.int8(new RangeBound.Closed<>(1L), new RangeBound.Open<>(100L))}),

          // numrange - uses Range.numeric() which does NOT normalize (continuous type)
          new PgTypeAndExample<>(
              PgTypes.numrange,
              Range.numeric(
                  new RangeBound.Closed<>(new BigDecimal("0.5")),
                  new RangeBound.Open<>(new BigDecimal("10.5")))),
          new PgTypeAndExample<>(
              PgTypes.numrange,
              Range.numeric(
                  new RangeBound.Open<>(BigDecimal.ZERO),
                  new RangeBound.Closed<>(new BigDecimal("99.99")))),
          new PgTypeAndExample<>(PgTypes.numrange, Range.empty()),
          new PgTypeAndExample<>(
              PgTypes.numrangeArray,
              new Range[] {
                Range.numeric(
                    new RangeBound.Closed<>(BigDecimal.ONE), new RangeBound.Open<>(BigDecimal.TEN))
              }),

          // daterange - uses Range.date() which normalizes to [) form
          new PgTypeAndExample<>(
              PgTypes.daterange,
              Range.date(
                  new RangeBound.Closed<>(LocalDate.of(2024, 1, 1)),
                  new RangeBound.Open<>(LocalDate.of(2024, 12, 31)))),
          new PgTypeAndExample<>(
              PgTypes.daterange,
              Range.date(
                  RangeBound.infinite(),
                  new RangeBound.Closed<>(
                      LocalDate.now()))), // unbounded lower, (,today] -> (,tomorrow)
          new PgTypeAndExample<>(PgTypes.daterange, Range.empty()),
          new PgTypeAndExample<>(
              PgTypes.daterangeArray,
              new Range[] {
                Range.date(
                    new RangeBound.Closed<>(LocalDate.of(2024, 1, 1)),
                    new RangeBound.Open<>(LocalDate.of(2024, 6, 30)))
              }),

          // tsrange (timestamp without timezone) - uses Range.timestamp() which does NOT normalize
          new PgTypeAndExample<>(
              PgTypes.tsrange,
              Range.timestamp(
                  new RangeBound.Closed<>(LocalDateTime.of(2024, 1, 1, 0, 0)),
                  new RangeBound.Open<>(LocalDateTime.of(2024, 12, 31, 23, 59, 59)))),
          new PgTypeAndExample<>(PgTypes.tsrange, Range.empty()),
          new PgTypeAndExample<>(
              PgTypes.tsrangeArray,
              new Range[] {
                Range.timestamp(
                    new RangeBound.Closed<>(LocalDateTime.of(2024, 1, 1, 0, 0)),
                    new RangeBound.Open<>(LocalDateTime.of(2024, 6, 30, 23, 59)))
              }),

          // tstzrange (timestamp with timezone) - uses Range.timestamptz() which does NOT normalize
          new PgTypeAndExample<>(
              PgTypes.tstzrange,
              Range.timestamptz(
                  new RangeBound.Closed<>(Instant.parse("2024-01-01T00:00:00Z")),
                  new RangeBound.Open<>(Instant.parse("2024-12-31T23:59:59Z")))),
          new PgTypeAndExample<>(PgTypes.tstzrange, Range.empty()),
          new PgTypeAndExample<>(
              PgTypes.tstzrangeArray,
              new Range[] {
                Range.timestamptz(
                    new RangeBound.Closed<>(Instant.parse("2024-01-01T00:00:00Z")),
                    new RangeBound.Open<>(Instant.parse("2024-06-30T23:59:59Z")))
              }));

  // in java
  static <T> void withConnection(SqlFunction<Connection, T> f) {
    try (var conn =
        java.sql.DriverManager.getConnection(
            "jdbc:postgresql://localhost:6432/Adventureworks?user=postgres&password=password")) {
      conn.setAutoCommit(false);
      try {
        f.apply(conn);
      } finally {
        conn.rollback();
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void test() {
    System.out.println(Arr.of(0, 1, 2, 3).reshape(2, 2));
    System.out.println(Arr.of("a", "b", "c", "d \",d").reshape(2, 2));
    System.out.println(ArrParser.parse(Arr.of(1, 2, 3, 4).encode(Object::toString)));
    System.out.println(ArrParser.parse("{{\"a\",\"b\"},{\"c\",\"d \\\",d\"}}"));

    // Test JSON roundtrip
    System.out.println("\n=== JSON Roundtrip Tests ===");
    for (PgTypeAndExample<?> t : All) {
      testJsonRoundtrip(t);
    }

    withConnection(
        conn -> {
          conn.unwrap(PgConnection.class).setPrepareThreshold(0);

          // Native type roundtrip tests
          System.out.println("\n=== Native Type Roundtrip Tests ===");
          for (PgTypeAndExample<?> t : All) {
            System.out.println(
                "Testing "
                    + t.type.typename().sqlType()
                    + " with example '"
                    + (format(t.example))
                    + "'");
            testCase(conn, t);
          }

          // JSON DB roundtrip tests (simulates MULTISET behavior)
          System.out.println("\n=== JSON DB Roundtrip Tests (MULTISET simulation) ===");
          for (PgTypeAndExample<?> t : All) {
            testJsonDbRoundtrip(conn, t);
          }

          // Composite type DB roundtrip tests
          // Only test each unique SQL type once to avoid creating too many composite types
          System.out.println("\n=== Composite Type DB Roundtrip Tests ===");
          Set<String> testedSqlTypes = new HashSet<>();
          for (PgTypeAndExample<?> t : All) {
            String sqlType = t.type.typename().sqlType();
            if (!testedSqlTypes.contains(sqlType)) {
              testedSqlTypes.add(sqlType);
              testCompositeDbRoundtrip(conn, t);
            }
          }

          // Test comprehensive composite with all supported types
          System.out.println("\n=== Comprehensive Composite Type Test ===");
          testComprehensiveComposite(conn);

          return null;
        });
  }

  // Test type wrapped in a composite, roundtripped through the database
  static <A> void testCompositeDbRoundtrip(Connection conn, PgTypeAndExample<A> t)
      throws SQLException {
    // Skip types that don't support composite text encoding
    if (!t.compositeTextWorks) {
      System.out.println(
          "SKIP Composite DB roundtrip "
              + t.type.typename().sqlType()
              + ": marked as not supported");
      return;
    }

    // Check if the type's PgCompositeText implementation works
    try {
      t.type.pgCompositeText().encode(t.example);
    } catch (UnsupportedOperationException e) {
      System.out.println(
          "SKIP Composite DB roundtrip " + t.type.typename().sqlType() + ": " + e.getMessage());
      return;
    }

    String sqlType = t.type.typename().sqlType();

    String compositeTypeName =
        "test_wrapper_"
            + sqlType
                .replace("(", "_")
                .replace(")", "_")
                .replace(",", "_")
                .replace(" ", "_")
                .replace("[", "_")
                .replace("]", "_");

    // Create composite type with single field
    try {
      conn.createStatement().execute("DROP TYPE IF EXISTS " + compositeTypeName + " CASCADE");
      conn.createStatement()
          .execute("CREATE TYPE " + compositeTypeName + " AS (wrapped_value " + sqlType + ")");

      // Build PgStruct for this wrapper
      PgStruct<SingleFieldWrapper<A>> wrapperStruct =
          PgStruct.<SingleFieldWrapper<A>>builder(compositeTypeName)
              .field("wrapped_value", t.type, SingleFieldWrapper::value)
              .build(values -> new SingleFieldWrapper<>((A) values[0]));

      PgType<SingleFieldWrapper<A>> wrapperType = wrapperStruct.asType();

      // Create temp table
      conn.createStatement()
          .execute("CREATE TEMP TABLE test_composite_rt (v " + compositeTypeName + ")");

      try {
        // Insert value
        SingleFieldWrapper<A> original = new SingleFieldWrapper<>(t.example);
        var insert = conn.prepareStatement("INSERT INTO test_composite_rt (v) VALUES (?)");
        wrapperType.write().set(insert, 1, original);
        insert.execute();
        insert.close();

        // Select back
        var select = conn.prepareStatement("SELECT v FROM test_composite_rt");
        select.execute();
        var rs = select.getResultSet();

        if (!rs.next()) {
          throw new RuntimeException("No rows returned");
        }

        SingleFieldWrapper<A> decoded = wrapperType.read().read(rs, 1);
        select.close();

        System.out.println(
            "Composite DB roundtrip "
                + sqlType
                + ": "
                + format(t.example)
                + " -> DB -> "
                + format(decoded.value));

        if (t.hasIdentity && !areEqual(decoded.value, t.example)) {
          throw new RuntimeException(
              "Composite DB roundtrip failed for "
                  + sqlType
                  + ": expected '"
                  + format(t.example)
                  + "' but got '"
                  + format(decoded.value)
                  + "'");
        }
      } finally {
        conn.createStatement().execute("DROP TABLE IF EXISTS test_composite_rt");
      }
    } finally {
      conn.createStatement().execute("DROP TYPE IF EXISTS " + compositeTypeName + " CASCADE");
    }
  }

  record SingleFieldWrapper<A>(A value) {}

  // Test a comprehensive composite type with all commonly-used field types
  record ComprehensiveComposite(
      String textField,
      Integer int4Field,
      Long int8Field,
      Short int2Field,
      Double float8Field,
      Float float4Field,
      Boolean boolField,
      BigDecimal numericField,
      UUID uuidField,
      LocalDate dateField,
      LocalTime timeField,
      LocalDateTime timestampField) {}

  static void testComprehensiveComposite(Connection conn) throws SQLException {
    String typeName = "test_comprehensive_composite";

    conn.createStatement().execute("DROP TYPE IF EXISTS " + typeName + " CASCADE");
    conn.createStatement()
        .execute(
            "CREATE TYPE "
                + typeName
                + " AS ("
                + "text_field TEXT, "
                + "int4_field INT4, "
                + "int8_field INT8, "
                + "int2_field INT2, "
                + "float8_field FLOAT8, "
                + "float4_field FLOAT4, "
                + "bool_field BOOL, "
                + "numeric_field NUMERIC, "
                + "uuid_field UUID, "
                + "date_field DATE, "
                + "time_field TIME, "
                + "timestamp_field TIMESTAMP"
                + ")");

    try {
      PgStruct<ComprehensiveComposite> struct =
          PgStruct.<ComprehensiveComposite>builder(typeName)
              .field("text_field", PgTypes.text, ComprehensiveComposite::textField)
              .field("int4_field", PgTypes.int4, ComprehensiveComposite::int4Field)
              .field("int8_field", PgTypes.int8, ComprehensiveComposite::int8Field)
              .field("int2_field", PgTypes.int2, ComprehensiveComposite::int2Field)
              .field("float8_field", PgTypes.float8, ComprehensiveComposite::float8Field)
              .field("float4_field", PgTypes.float4, ComprehensiveComposite::float4Field)
              .field("bool_field", PgTypes.bool, ComprehensiveComposite::boolField)
              .field("numeric_field", PgTypes.numeric, ComprehensiveComposite::numericField)
              .field("uuid_field", PgTypes.uuid, ComprehensiveComposite::uuidField)
              .field("date_field", PgTypes.date, ComprehensiveComposite::dateField)
              .field("time_field", PgTypes.time, ComprehensiveComposite::timeField)
              .field("timestamp_field", PgTypes.timestamp, ComprehensiveComposite::timestampField)
              .build(
                  values ->
                      new ComprehensiveComposite(
                          (String) values[0],
                          (Integer) values[1],
                          (Long) values[2],
                          (Short) values[3],
                          (Double) values[4],
                          (Float) values[5],
                          (Boolean) values[6],
                          (BigDecimal) values[7],
                          (UUID) values[8],
                          (LocalDate) values[9],
                          (LocalTime) values[10],
                          (LocalDateTime) values[11]));

      PgType<ComprehensiveComposite> compositeType = struct.asType();

      conn.createStatement().execute("CREATE TEMP TABLE test_comp (v " + typeName + ")");

      try {
        // Create test value with special characters
        ComprehensiveComposite original =
            new ComprehensiveComposite(
                "Hello, \"World\"! (with special chars: \n\t\\)",
                Integer.MAX_VALUE,
                Long.MIN_VALUE,
                (short) 42,
                3.14159265359,
                2.71828f,
                true,
                new BigDecimal("12345.67890"),
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                LocalDate.of(2024, 12, 25),
                LocalTime.of(14, 30, 45).truncatedTo(ChronoUnit.MICROS),
                LocalDateTime.of(2024, 12, 25, 14, 30, 45).truncatedTo(ChronoUnit.MICROS));

        // Test in-memory PgCompositeText roundtrip
        PgCompositeText<ComprehensiveComposite> compositeText = compositeType.pgCompositeText();
        String encoded = compositeText.encode(original).orElseThrow();
        ComprehensiveComposite decodedInMemory = compositeText.decode(encoded);

        System.out.println("Comprehensive composite in-memory roundtrip:");
        System.out.println("  Original: " + original);
        System.out.println("  Encoded: " + encoded);
        System.out.println("  Decoded: " + decodedInMemory);

        if (!original.equals(decodedInMemory)) {
          throw new RuntimeException(
              "In-memory roundtrip failed: expected " + original + " but got " + decodedInMemory);
        }

        // Insert into database
        var insert = conn.prepareStatement("INSERT INTO test_comp (v) VALUES (?)");
        compositeType.write().set(insert, 1, original);
        insert.execute();
        insert.close();

        // Read back
        var select = conn.prepareStatement("SELECT v FROM test_comp");
        select.execute();
        var rs = select.getResultSet();
        rs.next();
        ComprehensiveComposite decoded = compositeType.read().read(rs, 1);
        select.close();

        System.out.println("Comprehensive composite DB roundtrip:");
        System.out.println("  Original: " + original);
        System.out.println("  Decoded:  " + decoded);

        if (!original.equals(decoded)) {
          throw new RuntimeException(
              "DB roundtrip failed: expected " + original + " but got " + decoded);
        }

        System.out.println("Comprehensive composite tests PASSED!");
      } finally {
        conn.createStatement().execute("DROP TABLE IF EXISTS test_comp");
      }
    } finally {
      conn.createStatement().execute("DROP TYPE IF EXISTS " + typeName + " CASCADE");
    }
  }

  static <A> void testJsonRoundtrip(PgTypeAndExample<A> t) {
    try {
      PgJson<A> jsonCodec = t.type.pgJson();
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

  // Test JSON roundtrip through the database - simulates MULTISET behavior
  // Insert value into native column, read back as JSON using to_json(), parse back to value
  static <A> void testJsonDbRoundtrip(Connection conn, PgTypeAndExample<A> t) throws SQLException {
    PgJson<A> jsonCodec = t.type.pgJson();
    A original = t.example;
    String sqlType = t.type.typename().sqlType();

    // Create temp table with the native type column
    conn.createStatement().execute("CREATE TEMP TABLE test_json_rt (v " + sqlType + ")");

    try {
      // Insert value using native type
      var insert = conn.prepareStatement("INSERT INTO test_json_rt (v) VALUES (?)");
      t.type.write().set(insert, 1, original);
      insert.execute();
      insert.close();

      // Select back as JSON using to_json - this is what MULTISET does
      var select = conn.prepareStatement("SELECT to_json(v) FROM test_json_rt");
      select.execute();
      var rs = select.getResultSet();

      if (!rs.next()) {
        throw new RuntimeException("No rows returned");
      }

      // Read the JSON string back from the database
      String jsonFromDb = rs.getString(1);
      select.close();

      // Parse the JSON and convert back to value
      JsonValue parsedFromDb = JsonValue.parse(jsonFromDb);
      A decoded = jsonCodec.fromJson(parsedFromDb);

      System.out.println(
          "JSON DB roundtrip "
              + sqlType
              + ": "
              + format(original)
              + " -> DB -> "
              + jsonFromDb
              + " -> "
              + format(decoded));

      if (t.hasIdentity && !areEqual(decoded, original)) {
        throw new RuntimeException(
            "JSON DB roundtrip failed for "
                + sqlType
                + ": expected '"
                + format(original)
                + "' but got '"
                + format(decoded)
                + "'");
      }
    } finally {
      conn.createStatement().execute("DROP TABLE IF EXISTS test_json_rt");
    }
  }

  static <A> void testCase(Connection conn, PgTypeAndExample<A> t) throws SQLException {
    conn.createStatement()
        .execute("create temp table test (v " + t.type.typename().sqlType() + ")");
    var insert = conn.prepareStatement("insert into test (v) values (?)");
    A expected = t.example;
    t.type.write().set(insert, 1, expected);
    insert.execute();
    insert.close();
    if (t.streamingWorks) {
      streamingInsert.insert(
          "COPY test(v) FROM STDIN",
          100,
          Arrays.asList(t.example).iterator(),
          conn,
          t.type.pgText());
    }

    final PreparedStatement select;
    if (t.hasIdentity) {
      select = conn.prepareStatement("select v, null from test where v = ?");
      t.type.write().set(select, 1, expected);
    } else {
      select = conn.prepareStatement("select v, null from test");
    }

    select.execute();
    var rs = select.getResultSet();
    List<TestPair<A>> rows =
        RowParsers.of(t.type, t.type.opt(), TestPair::new, row -> new Object[] {row.t0, row.t1})
            .all()
            .apply(rs);
    select.close();
    conn.createStatement().execute("drop table test;");
    assertEquals(rows.get(0).t0(), expected);
    if (t.streamingWorks) {
      assertEquals(rows.get(1).t0(), expected);
    }
  }

  static <A> void assertEquals(A actual, A expected) {
    if (!areEqual(actual, expected)) {
      throw new RuntimeException(
          "actual: '" + format(actual) + "' != expected '" + format(expected) + "'");
    }
  }

  static <A> boolean areEqual(A actual, A expected) {
    if (expected instanceof byte[]) {
      return Arrays.equals((byte[]) actual, (byte[]) expected);
    }
    if (expected instanceof boolean[]) {
      return Arrays.equals((boolean[]) actual, (boolean[]) expected);
    }
    if (expected instanceof short[]) {
      return Arrays.equals((short[]) actual, (short[]) expected);
    }
    if (expected instanceof int[]) {
      return Arrays.equals((int[]) actual, (int[]) expected);
    }
    if (expected instanceof long[]) {
      return Arrays.equals((long[]) actual, (long[]) expected);
    }
    if (expected instanceof float[]) {
      return Arrays.equals((float[]) actual, (float[]) expected);
    }
    if (expected instanceof double[]) {
      return Arrays.equals((double[]) actual, (double[]) expected);
    }
    if (expected instanceof Object[]) {
      return Arrays.equals((Object[]) actual, (Object[]) expected);
    }
    return actual.equals(expected);
  }

  static <A> String format(A a) {
    if (a instanceof byte[]) {
      return Arrays.toString((byte[]) a);
    }
    if (a instanceof boolean[]) {
      return Arrays.toString((boolean[]) a);
    }
    if (a instanceof short[]) {
      return Arrays.toString((short[]) a);
    }
    if (a instanceof int[]) {
      return Arrays.toString((int[]) a);
    }
    if (a instanceof long[]) {
      return Arrays.toString((long[]) a);
    }
    if (a instanceof float[]) {
      return Arrays.toString((float[]) a);
    }
    if (a instanceof double[]) {
      return Arrays.toString((double[]) a);
    }
    if (a instanceof Object[]) {
      return Arrays.toString((Object[]) a);
    }
    return a.toString();
  }
}

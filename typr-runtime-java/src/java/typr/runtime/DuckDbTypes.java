package typr.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.util.UUID;
import java.util.function.Function;
import typr.data.Json;
import typr.data.JsonValue;

/**
 * DuckDB type definitions for the typr-runtime-java library.
 *
 * <p>DuckDB has a rich type system including: - Standard SQL types (INTEGER, VARCHAR, etc.) -
 * Extended integer types (HUGEINT, UHUGEINT, UTINYINT, etc.) - Nested types (LIST, STRUCT, MAP,
 * UNION) - Temporal types with various precisions
 */
public interface DuckDbTypes {
  // ==================== Integer Types (Signed) ====================

  DuckDbType<Byte> tinyint =
      DuckDbType.of(
          "TINYINT",
          DuckDbRead.readByte,
          DuckDbWrite.writeByte,
          DuckDbStringifier.tinyint,
          DuckDbJson.int1);

  DuckDbType<Short> smallint =
      DuckDbType.of(
          "SMALLINT",
          DuckDbRead.readShort,
          DuckDbWrite.writeShort,
          DuckDbStringifier.smallint,
          DuckDbJson.int2);

  DuckDbType<Integer> integer =
      DuckDbType.of(
          "INTEGER",
          DuckDbRead.readInteger,
          DuckDbWrite.writeInteger,
          DuckDbStringifier.integer,
          DuckDbJson.int4);

  DuckDbType<Long> bigint =
      DuckDbType.of(
          "BIGINT",
          DuckDbRead.readLong,
          DuckDbWrite.writeLong,
          DuckDbStringifier.bigint,
          DuckDbJson.int8);

  // HUGEINT: 128-bit signed integer (-170141183460469231731687303715884105728 to
  // 170141183460469231731687303715884105727)
  DuckDbType<BigInteger> hugeint =
      DuckDbType.of(
          "HUGEINT",
          DuckDbRead.readBigInteger,
          DuckDbWrite.writeBigInteger,
          DuckDbStringifier.hugeint,
          DuckDbJson.hugeint);

  // ==================== Integer Types (Unsigned) ====================

  // UTINYINT: 0-255, fits in Short
  DuckDbType<Short> utinyint =
      DuckDbType.of(
          "UTINYINT",
          DuckDbRead.readShort,
          DuckDbWrite.writeShort,
          DuckDbStringifier.smallint,
          DuckDbJson.int2);

  // USMALLINT: 0-65535, fits in Integer
  DuckDbType<Integer> usmallint =
      DuckDbType.of(
          "USMALLINT",
          DuckDbRead.readInteger,
          DuckDbWrite.writeInteger,
          DuckDbStringifier.integer,
          DuckDbJson.int4);

  // UINTEGER: 0-4294967295, fits in Long
  DuckDbType<Long> uinteger =
      DuckDbType.of(
          "UINTEGER",
          DuckDbRead.readLong,
          DuckDbWrite.writeLong,
          DuckDbStringifier.bigint,
          DuckDbJson.int8);

  // UBIGINT: 0-18446744073709551615, needs BigInteger
  DuckDbType<BigInteger> ubigint =
      DuckDbType.of(
          "UBIGINT",
          DuckDbRead.readBigInteger,
          DuckDbWrite.writeBigInteger,
          DuckDbStringifier.hugeint,
          DuckDbJson.hugeint);

  // UHUGEINT: 128-bit unsigned integer, needs BigInteger
  DuckDbType<BigInteger> uhugeint =
      DuckDbType.of(
          "UHUGEINT",
          DuckDbRead.readBigInteger,
          DuckDbWrite.writeBigInteger,
          DuckDbStringifier.hugeint,
          DuckDbJson.hugeint);

  // ==================== Floating-Point Types ====================

  DuckDbType<Float> float_ =
      DuckDbType.of(
          "FLOAT",
          DuckDbRead.readFloat,
          DuckDbWrite.writeFloat,
          DuckDbStringifier.float4,
          DuckDbJson.float4);

  DuckDbType<Double> double_ =
      DuckDbType.of(
          "DOUBLE",
          DuckDbRead.readDouble,
          DuckDbWrite.writeDouble,
          DuckDbStringifier.float8,
          DuckDbJson.float8);

  // Aliases
  DuckDbType<Float> real = float_.renamed("REAL");
  DuckDbType<Float> float4 = float_.renamed("FLOAT4");
  DuckDbType<Double> float8 = double_.renamed("FLOAT8");

  // ==================== Fixed-Point Types ====================

  DuckDbType<BigDecimal> decimal =
      DuckDbType.of(
          "DECIMAL",
          DuckDbRead.readBigDecimal,
          DuckDbWrite.writeBigDecimal,
          DuckDbStringifier.numeric,
          DuckDbJson.numeric);

  DuckDbType<BigDecimal> numeric = decimal.renamed("NUMERIC");

  static DuckDbType<BigDecimal> decimal(int precision, int scale) {
    return DuckDbType.of(
        DuckDbTypename.of("DECIMAL", precision, scale),
        DuckDbRead.readBigDecimal,
        DuckDbWrite.writeBigDecimal,
        DuckDbStringifier.numeric,
        DuckDbJson.numeric);
  }

  // ==================== Boolean Type ====================

  DuckDbType<Boolean> boolean_ =
      DuckDbType.of(
          "BOOLEAN",
          DuckDbRead.readBoolean,
          DuckDbWrite.writeBoolean,
          DuckDbStringifier.bool,
          DuckDbJson.bool);

  DuckDbType<Boolean> bool = boolean_.renamed("BOOL");

  // ==================== String Types ====================

  DuckDbType<String> varchar =
      DuckDbType.of(
          "VARCHAR",
          DuckDbRead.readString,
          DuckDbWrite.writeString,
          DuckDbStringifier.string,
          DuckDbJson.text);

  DuckDbType<String> text = varchar.renamed("TEXT");
  DuckDbType<String> string = varchar.renamed("STRING");
  DuckDbType<String> char_ = varchar.renamed("CHAR");
  DuckDbType<String> bpchar = varchar.renamed("BPCHAR");

  static DuckDbType<String> varchar(int length) {
    return DuckDbType.of(
        DuckDbTypename.of("VARCHAR", length),
        DuckDbRead.readString,
        DuckDbWrite.writeString,
        DuckDbStringifier.string,
        DuckDbJson.text);
  }

  static DuckDbType<String> char_(int length) {
    return DuckDbType.of(
        DuckDbTypename.of("CHAR", length),
        DuckDbRead.readString,
        DuckDbWrite.writeString,
        DuckDbStringifier.string,
        DuckDbJson.text);
  }

  // ==================== Binary Types ====================

  DuckDbType<byte[]> blob =
      DuckDbType.of(
          "BLOB",
          DuckDbRead.readByteArray,
          DuckDbWrite.writeByteArray,
          DuckDbStringifier.blob,
          DuckDbJson.blob);

  DuckDbType<byte[]> bytea = blob.renamed("BYTEA");
  DuckDbType<byte[]> binary = blob.renamed("BINARY");
  DuckDbType<byte[]> varbinary = blob.renamed("VARBINARY");

  // ==================== Bit String Type ====================

  // BIT type - stored as string of 0s and 1s
  DuckDbType<String> bit =
      DuckDbType.of(
          "BIT",
          DuckDbRead.readBitString,
          DuckDbWrite.writeString,
          DuckDbStringifier.string,
          DuckDbJson.bit);

  DuckDbType<String> bitstring = bit.renamed("BITSTRING");

  static DuckDbType<String> bit(int length) {
    return DuckDbType.of(
        DuckDbTypename.of("BIT", length),
        DuckDbRead.readBitString,
        DuckDbWrite.writeString,
        DuckDbStringifier.string,
        DuckDbJson.bit);
  }

  // ==================== Date/Time Types ====================

  DuckDbType<LocalDate> date =
      DuckDbType.of(
          "DATE",
          DuckDbRead.readLocalDate,
          DuckDbWrite.passObjectToJdbc(),
          DuckDbStringifier.date,
          DuckDbJson.date);

  DuckDbType<LocalTime> time =
      DuckDbType.of(
          "TIME",
          DuckDbRead.readLocalTime,
          DuckDbWrite.writeLocalTime,
          DuckDbStringifier.time,
          DuckDbJson.time);

  DuckDbType<LocalDateTime> timestamp =
      DuckDbType.of(
          "TIMESTAMP",
          DuckDbRead.readLocalDateTime,
          DuckDbWrite.passObjectToJdbc(),
          DuckDbStringifier.timestamp,
          DuckDbJson.timestamp);

  DuckDbType<LocalDateTime> datetime = timestamp.renamed("DATETIME");

  // Timestamp with timezone
  DuckDbType<OffsetDateTime> timestamptz =
      DuckDbType.of(
          "TIMESTAMP WITH TIME ZONE",
          DuckDbRead.readOffsetDateTime,
          DuckDbWrite.passObjectToJdbc(),
          DuckDbStringifier.timestamptz,
          DuckDbJson.timestamptz);

  // Time with timezone - represented as OffsetTime
  DuckDbType<OffsetDateTime> timetz =
      DuckDbType.of(
          "TIME WITH TIME ZONE",
          DuckDbRead.readOffsetDateTime,
          DuckDbWrite.passObjectToJdbc(),
          DuckDbStringifier.timestamptz,
          DuckDbJson.timestamptz);

  // Timestamp variants with different precisions
  DuckDbType<LocalDateTime> timestamp_s = timestamp.renamed("TIMESTAMP_S");
  DuckDbType<LocalDateTime> timestamp_ms = timestamp.renamed("TIMESTAMP_MS");
  DuckDbType<LocalDateTime> timestamp_ns = timestamp.renamed("TIMESTAMP_NS");

  // ==================== Interval Type ====================

  DuckDbType<Duration> interval =
      DuckDbType.of(
          "INTERVAL",
          DuckDbRead.readDuration,
          DuckDbWrite.writeDuration,
          DuckDbStringifier.interval,
          DuckDbJson.interval);

  // ==================== UUID Type ====================

  DuckDbType<UUID> uuid =
      DuckDbType.of(
          "UUID",
          DuckDbRead.readUuid,
          DuckDbWrite.writeUuid,
          DuckDbStringifier.uuid,
          DuckDbJson.uuid);

  // ==================== JSON Type ====================

  DuckDbType<Json> json =
      DuckDbType.of(
          "JSON",
          DuckDbRead.readString.map(Json::new),
          DuckDbWrite.writeString.contramap(Json::value),
          DuckDbStringifier.string.contramap(Json::value),
          DuckDbJson.json);

  // ==================== Enum Type ====================

  /**
   * Create a DuckDbType for ENUM columns. DuckDB ENUMs are read/written as strings.
   *
   * @param enumTypeName the name of the enum type (e.g., "mood" for CREATE TYPE mood AS
   *     ENUM('happy', 'sad'))
   * @param fromString function to convert string to enum value
   * @param <E> the enum type
   * @return DuckDbType for the enum
   */
  static <E extends Enum<E>> DuckDbType<E> ofEnum(
      String enumTypeName, Function<String, E> fromString) {
    return DuckDbType.of(
        enumTypeName,
        DuckDbRead.readString.map(fromString::apply),
        DuckDbWrite.writeString.contramap(Enum::name),
        DuckDbStringifier.string.contramap(Enum::name),
        DuckDbJson.text.bimap(fromString::apply, Enum::name));
  }

  // ==================== Array Types ====================

  /** TINYINT[] - array of tinyint values */
  DuckDbType<Byte[]> tinyintArray = tinyint.array();

  /** SMALLINT[] - array of smallint values */
  DuckDbType<Short[]> smallintArray = smallint.array();

  /** INTEGER[] - array of integer values */
  DuckDbType<Integer[]> integerArray = integer.array();

  /** BIGINT[] - array of bigint values */
  DuckDbType<Long[]> bigintArray = bigint.array();

  /** HUGEINT[] - array of hugeint values */
  DuckDbType<BigInteger[]> hugeintArray = hugeint.array();

  /** UTINYINT[] - array of utinyint values */
  DuckDbType<Short[]> utinyintArray = utinyint.array();

  /** USMALLINT[] - array of usmallint values */
  DuckDbType<Integer[]> usmallintArray = usmallint.array();

  /** UINTEGER[] - array of uinteger values */
  DuckDbType<Long[]> uintegerArray = uinteger.array();

  /** UBIGINT[] - array of ubigint values */
  DuckDbType<BigInteger[]> ubigintArray = ubigint.array();

  /** FLOAT[] - array of float values */
  DuckDbType<Float[]> floatArray = float_.array();

  /** DOUBLE[] - array of double values */
  DuckDbType<Double[]> doubleArray = double_.array();

  /** DECIMAL[] - array of decimal values */
  DuckDbType<BigDecimal[]> decimalArray = decimal.array();

  /** BOOLEAN[] - array of boolean values */
  DuckDbType<Boolean[]> booleanArray = boolean_.array();

  /** VARCHAR[] - array of varchar values */
  DuckDbType<String[]> varcharArray = varchar.array();

  /** BLOB[] - array of blob values */
  DuckDbType<byte[][]> blobArray = blob.array();

  /** DATE[] - array of date values */
  DuckDbType<LocalDate[]> dateArray = date.array();

  /** TIME[] - array of time values */
  DuckDbType<LocalTime[]> timeArray = time.array();

  /** TIMESTAMP[] - array of timestamp values */
  DuckDbType<LocalDateTime[]> timestampArray = timestamp.array();

  /** TIMESTAMPTZ[] - array of timestamptz values */
  DuckDbType<OffsetDateTime[]> timestamptzArray = timestamptz.array();

  /** INTERVAL[] - array of interval values */
  DuckDbType<Duration[]> intervalArray = interval.array();

  /** UUID[] - array of uuid values */
  DuckDbType<UUID[]> uuidArray = uuid.array();

  /** JSON[] - array of json values */
  DuckDbType<Json[]> jsonArray = json.array();

  // ==================== Pre-instantiated List Types ====================
  // Native JNI types (best performance)

  /** LIST&lt;BOOLEAN&gt; - native JNI support */
  DuckDbType<java.util.List<Boolean>> listBoolean =
      boolean_.listNative(Boolean.class, Boolean[]::new);

  /** LIST&lt;TINYINT&gt; - native JNI support */
  DuckDbType<java.util.List<Byte>> listTinyint = tinyint.listNative(Byte.class, Byte[]::new);

  /** LIST&lt;SMALLINT&gt; - native JNI support */
  DuckDbType<java.util.List<Short>> listSmallint = smallint.listNative(Short.class, Short[]::new);

  /** LIST&lt;INTEGER&gt; - native JNI support */
  DuckDbType<java.util.List<Integer>> listInteger =
      integer.listNative(Integer.class, Integer[]::new);

  /** LIST&lt;BIGINT&gt; - native JNI support */
  DuckDbType<java.util.List<Long>> listBigint = bigint.listNative(Long.class, Long[]::new);

  /** LIST&lt;FLOAT&gt; - native JNI support */
  DuckDbType<java.util.List<Float>> listFloat = float_.listNative(Float.class, Float[]::new);

  /** LIST&lt;DOUBLE&gt; - native JNI support */
  DuckDbType<java.util.List<Double>> listDouble = double_.listNative(Double.class, Double[]::new);

  /** LIST&lt;VARCHAR&gt; - native JNI support */
  DuckDbType<java.util.List<String>> listVarchar = varchar.listNative(String.class, String[]::new);

  // String-converted types (~33% overhead at 100k rows, but required for correctness)

  /** LIST&lt;UUID&gt; - SQL literal conversion (UUID has byte-ordering bug in JNI) */
  DuckDbType<java.util.List<UUID>> listUuid =
      uuid.listViaSqlLiteral(UUID.class, DuckDbStringifier.uuid);

  /** LIST&lt;DATE&gt; - SQL literal conversion (JNI doesn't recognize java.time.LocalDate) */
  DuckDbType<java.util.List<LocalDate>> listDate =
      date.listViaSqlLiteral(LocalDate.class, DuckDbStringifier.date);

  /** LIST&lt;TIME&gt; - SQL literal conversion (JNI doesn't recognize java.time.LocalTime) */
  DuckDbType<java.util.List<LocalTime>> listTime =
      time.listViaSqlLiteral(LocalTime.class, DuckDbStringifier.time);

  /**
   * LIST&lt;TIMESTAMP&gt; - SQL literal conversion (DuckDB JDBC returns java.sql.Timestamp in
   * arrays)
   */
  DuckDbType<java.util.List<LocalDateTime>> listTimestamp =
      timestamp.listViaSqlLiteral(
          java.sql.Timestamp.class,
          java.sql.Timestamp::toLocalDateTime,
          DuckDbStringifier.timestamp);

  /**
   * LIST&lt;TIMESTAMPTZ&gt; - SQL literal conversion (DuckDB JDBC returns java.sql.Timestamp in
   * arrays)
   */
  DuckDbType<java.util.List<OffsetDateTime>> listTimestamptz =
      timestamptz.listViaSqlLiteral(
          java.sql.Timestamp.class,
          ts -> ts.toLocalDateTime().atOffset(ZoneOffset.UTC),
          DuckDbStringifier.timestamptz);

  /** LIST&lt;DECIMAL&gt; - SQL literal conversion */
  DuckDbType<java.util.List<BigDecimal>> listDecimal =
      decimal.listViaSqlLiteral(BigDecimal.class, DuckDbStringifier.numeric);

  /** LIST&lt;HUGEINT&gt; - SQL literal conversion */
  DuckDbType<java.util.List<BigInteger>> listHugeint =
      hugeint.listViaSqlLiteral(BigInteger.class, DuckDbStringifier.hugeint);

  /** LIST&lt;INTERVAL&gt; - SQL literal conversion */
  DuckDbType<java.util.List<Duration>> listInterval =
      interval.listViaSqlLiteral(Duration.class, DuckDbStringifier.interval);

  // ==================== Pre-instantiated Map Types ====================

  /** MAP(VARCHAR, INTEGER) - native JDBC support */
  static DuckDbType<java.util.Map<String, Integer>> mapVarcharInteger() {
    return varchar.mapToNative(integer, String.class, Integer.class);
  }

  /** MAP(VARCHAR, VARCHAR) - native JDBC support */
  static DuckDbType<java.util.Map<String, String>> mapVarcharVarchar() {
    return varchar.mapToNative(varchar, String.class, String.class);
  }

  /** MAP(INTEGER, VARCHAR) - native JDBC support */
  static DuckDbType<java.util.Map<Integer, String>> mapIntegerVarchar() {
    return integer.mapToNative(varchar, Integer.class, String.class);
  }

  /** MAP(UUID, VARCHAR) - SQL literal conversion for UUID */
  static DuckDbType<java.util.Map<UUID, String>> mapUuidVarchar() {
    return uuid.mapToViaSqlLiteral(
        varchar, UUID.class, String.class, DuckDbStringifier.uuid, DuckDbStringifier.string);
  }

  /** MAP(VARCHAR, TIME) - SQL literal conversion for TIME */
  static DuckDbType<java.util.Map<String, LocalTime>> mapVarcharTime() {
    return varchar.mapToViaSqlLiteral(
        time, String.class, LocalTime.class, DuckDbStringifier.string, DuckDbStringifier.time);
  }

  /** MAP(UUID, TIME) - SQL literal conversion for both */
  static DuckDbType<java.util.Map<UUID, LocalTime>> mapUuidTime() {
    return uuid.mapToViaSqlLiteral(
        time, UUID.class, LocalTime.class, DuckDbStringifier.uuid, DuckDbStringifier.time);
  }

  /**
   * JSON codec for MAP types with typed keys and values. Uses JSON object format for compatibility
   * with DuckDB's JSON COPY. Keys are encoded to their JSON string representation.
   */
  static <K, V> DuckDbJson<java.util.Map<K, V>> mapJson(
      DuckDbJson<K> keyJson, DuckDbJson<V> valueJson) {
    return new DuckDbJson<>() {
      @Override
      public JsonValue toJson(java.util.Map<K, V> value) {
        // Serialize as JSON object for compatibility with DuckDB JSON COPY
        // Keys are converted to strings using their JSON representation (without quotes)
        java.util.Map<String, JsonValue> jsonMap = new java.util.LinkedHashMap<>();
        for (var entry : value.entrySet()) {
          JsonValue keyJsonValue = keyJson.toJson(entry.getKey());
          // For string keys, use the raw value; for other types, encode to JSON string
          String keyStr =
              keyJsonValue instanceof JsonValue.JString s ? s.value() : keyJsonValue.encode();
          JsonValue valJson = valueJson.toJson(entry.getValue());
          jsonMap.put(keyStr, valJson);
        }
        return new JsonValue.JObject(jsonMap);
      }

      @Override
      public java.util.Map<K, V> fromJson(JsonValue json) {
        if (!(json instanceof JsonValue.JObject(java.util.Map<String, JsonValue> obj))) {
          throw new IllegalArgumentException(
              "Expected JSON object for MAP, got: " + json.getClass().getSimpleName());
        }
        java.util.Map<K, V> result = new java.util.LinkedHashMap<>();
        for (var entry : obj.entrySet()) {
          String keyStr = entry.getKey();
          // Try to parse the key - if it's already a JSON string, use it directly
          // Otherwise parse it as JSON (for complex types encoded as JSON strings)
          JsonValue keyJsonValue;
          try {
            keyJsonValue = JsonValue.parse(keyStr);
          } catch (Exception e) {
            // Not valid JSON, treat as raw string
            keyJsonValue = new JsonValue.JString(keyStr);
          }
          K key = keyJson.fromJson(keyJsonValue);
          V val = valueJson.fromJson(entry.getValue());
          result.put(key, val);
        }
        return result;
      }
    };
  }
}

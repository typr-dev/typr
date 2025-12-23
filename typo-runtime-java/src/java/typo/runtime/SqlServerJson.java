package typo.runtime;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import typo.data.JsonValue;

/**
 * Encodes/decodes values to/from JSON for SQL Server.
 *
 * <p>Similar to MariaJson - SQL Server supports JSON natively since 2016.
 */
public abstract class SqlServerJson<A> implements DbJson<A> {
  public abstract JsonValue toJson(A a);

  public abstract A fromJson(JsonValue jsonValue);

  public <B> SqlServerJson<B> bimap(SqlFunction<A, B> f, Function<B, A> g) {
    var self = this;
    return SqlServerJson.instance(a -> self.toJson(g.apply(a)), jv -> f.apply(self.fromJson(jv)));
  }

  public <B> SqlServerJson<B> map(SqlFunction<A, B> f) {
    return bimap(f, null); // write not supported
  }

  public <B> SqlServerJson<B> contramap(Function<B, A> g) {
    return bimap(null, g); // read not supported
  }

  public SqlServerJson<Optional<A>> opt() {
    var self = this;
    return instance(
        a -> a.map(self::toJson).orElse(JsonValue.JNull.INSTANCE),
        jv -> jv instanceof JsonValue.JNull ? Optional.empty() : Optional.of(self.fromJson(jv)));
  }

  public static <A> SqlServerJson<A> instance(
      Function<A, JsonValue> toJson, SqlFunction<JsonValue, A> fromJson) {
    return new SqlServerJson<>() {
      @Override
      public JsonValue toJson(A a) {
        return toJson.apply(a);
      }

      @Override
      public A fromJson(JsonValue jsonValue) {
        try {
          return fromJson.apply(jsonValue);
        } catch (java.sql.SQLException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  // Standard JSON codecs
  public static final SqlServerJson<String> text =
      instance(s -> new JsonValue.JString(s), jv -> ((JsonValue.JString) jv).value());
  public static final SqlServerJson<Boolean> bool =
      instance(JsonValue.JBool::of, jv -> ((JsonValue.JBool) jv).value());
  public static final SqlServerJson<Short> int2 =
      instance(
          s -> JsonValue.JNumber.of(s.intValue()),
          jv -> Short.parseShort(((JsonValue.JNumber) jv).value()));
  public static final SqlServerJson<Integer> int4 =
      instance(
          i -> JsonValue.JNumber.of(i.longValue()),
          jv -> Integer.parseInt(((JsonValue.JNumber) jv).value()));
  public static final SqlServerJson<Long> int8 =
      instance(JsonValue.JNumber::of, jv -> Long.parseLong(((JsonValue.JNumber) jv).value()));
  public static final SqlServerJson<Float> float4 =
      instance(
          f -> JsonValue.JNumber.of(f.doubleValue()),
          jv -> Float.parseFloat(((JsonValue.JNumber) jv).value()));
  public static final SqlServerJson<Double> float8 =
      instance(JsonValue.JNumber::of, jv -> Double.parseDouble(((JsonValue.JNumber) jv).value()));
  public static final SqlServerJson<BigDecimal> numeric =
      instance(
          bd -> JsonValue.JNumber.of(bd.toString()),
          jv -> new BigDecimal(((JsonValue.JNumber) jv).value()));
  public static final SqlServerJson<byte[]> bytea =
      instance(
          bytes -> new JsonValue.JString(java.util.Base64.getEncoder().encodeToString(bytes)),
          jv -> java.util.Base64.getDecoder().decode(((JsonValue.JString) jv).value()));
  public static final SqlServerJson<UUID> uuid =
      instance(
          u -> new JsonValue.JString(u.toString()),
          jv -> UUID.fromString(((JsonValue.JString) jv).value()));
  public static final SqlServerJson<Object> unknown = instance(obj -> (JsonValue) obj, jv -> jv);

  // Date/Time codecs (ISO-8601 strings)
  public static final SqlServerJson<java.time.LocalDate> date =
      instance(
          d -> new JsonValue.JString(d.toString()),
          jv -> java.time.LocalDate.parse(((JsonValue.JString) jv).value()));
  public static final SqlServerJson<java.time.LocalTime> time =
      instance(
          t -> new JsonValue.JString(t.toString()),
          jv -> java.time.LocalTime.parse(((JsonValue.JString) jv).value()));
  public static final SqlServerJson<java.time.LocalDateTime> timestamp =
      instance(
          ts -> new JsonValue.JString(ts.toString()),
          jv -> java.time.LocalDateTime.parse(((JsonValue.JString) jv).value()));
  public static final SqlServerJson<java.time.OffsetDateTime> timestamptz =
      instance(
          odt -> new JsonValue.JString(odt.toString()),
          jv -> java.time.OffsetDateTime.parse(((JsonValue.JString) jv).value()));

  // Spatial types - serialize as WKT (Well-Known Text)
  public static final SqlServerJson<com.microsoft.sqlserver.jdbc.Geography> jsonGeography =
      instance(
          geo -> new JsonValue.JString(geo.toString()),
          jv ->
              com.microsoft.sqlserver.jdbc.Geography.STGeomFromText(
                  ((JsonValue.JString) jv).value(), 0));
  public static final SqlServerJson<com.microsoft.sqlserver.jdbc.Geometry> jsonGeometry =
      instance(
          geom -> new JsonValue.JString(geom.toString()),
          jv ->
              com.microsoft.sqlserver.jdbc.Geometry.STGeomFromText(
                  ((JsonValue.JString) jv).value(), 0));
}

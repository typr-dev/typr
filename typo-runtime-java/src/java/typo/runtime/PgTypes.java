package typo.runtime;

import org.postgresql.geometric.*;
import org.postgresql.util.PGInterval;
import org.postgresql.util.PGobject;
import typo.data.Record;
import typo.data.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public interface PgTypes {
    // Primitive types
    PgType<Boolean> bool = PgType.of("bool", PgRead.readBoolean, PgWrite.writeBoolean, PgText.textBoolean, PgJson.bool);
    PgType<Boolean[]> boolArray = bool.array(PgRead.readBooleanArray, Boolean[]::new);
    PgType<Short> int2 = PgType.of("int2", PgRead.readShort, PgWrite.writeShort, PgText.textShort, PgJson.int2);
    PgType<Short> smallint = int2.withTypename(PgTypename.of("smallint"));
    PgType<Short[]> int2Array = int2.array(PgRead.readShortArray, Short[]::new);
    PgType<Short[]> smallintArray = int2Array.renamed("smallint");
    PgType<Integer> int4 = PgType.of("int4", PgRead.readInteger, PgWrite.writeInteger, PgText.textInteger, PgJson.int4);
    PgType<Integer[]> int4Array = int4.array(PgRead.readIntegerArray, Integer[]::new);
    PgType<Long> int8 = PgType.of("int8", PgRead.readLong, PgWrite.writeLong, PgText.textLong, PgJson.int8);
    PgType<Long[]> int8Array = int8.array(PgRead.readLongArray, Long[]::new);
    PgType<Float> float4 = PgType.of("float4", PgRead.readFloat, PgWrite.writeFloat, PgText.textFloat, PgJson.float4);
    PgType<Float[]> float4Array = float4.array(PgRead.readFloatArray, Float[]::new);
    PgType<Double> float8 = PgType.of("float8", PgRead.readDouble, PgWrite.writeDouble, PgText.textDouble, PgJson.float8);
    PgType<Double[]> float8Array = float8.array(PgRead.readDoubleArray, Double[]::new);
    PgType<BigDecimal> numeric = PgType.of("numeric", PgRead.readBigDecimal, PgWrite.writeBigDecimal, PgText.textBigDecimal, PgJson.numeric);
    PgType<BigDecimal[]> numericArray = numeric.array(PgRead.readBigDecimalArray, BigDecimal[]::new);

    // String types
    PgType<String> text = PgType.of("text", PgRead.readString, PgWrite.writeString, PgText.textString, PgJson.text);
    PgType<String[]> textArray = text.array(PgRead.readStringArray, String[]::new);
    PgType<String> bpchar = PgType.of("bpchar", PgRead.readString, PgWrite.writeString, PgText.textString, PgJson.text);
    PgType<String[]> bpcharArray = bpchar.array(PgRead.readStringArray, String[]::new);
    PgType<String> name = PgType.of("name", PgRead.readString, PgWrite.writeString, PgText.textString, PgJson.text);
    PgType<String[]> nameArray = name.array(PgRead.readStringArray, String[]::new);

    // Binary
    PgType<byte[]> bytea = PgType.of("bytea", PgRead.readByteArray, PgWrite.writeByteArray, PgText.textByteArray, PgJson.bytea);

    // Date/Time types
    PgType<LocalDate> date = PgType.of("date", PgRead.readLocalDate, PgWrite.passObjectToJdbc(), PgText.instanceToString(), PgJson.date);
    PgType<LocalDate[]> dateArray = date.array(PgRead.readLocalDateArray, LocalDate[]::new);
    PgType<LocalTime> time = PgType.of("time", PgRead.readLocalTime, PgWrite.passObjectToJdbc(), PgText.instanceToString(), PgJson.time);
    PgType<LocalTime[]> timeArray = time.array(PgRead.readLocalTimeArray, LocalTime[]::new);
    PgType<LocalDateTime> timestamp = PgType.of("timestamp", PgRead.readLocalDateTime, PgWrite.passObjectToJdbc(), PgText.instanceToString(), PgJson.timestamp);
    PgType<LocalDateTime[]> timestampArray = timestamp.array(PgRead.readLocalDateTimeArray, LocalDateTime[]::new);
    PgType<Instant> timestamptz = PgType.of("timestamptz", PgRead.readInstant, PgWrite.primitive((ps, i, v) -> ps.setObject(i, v.atOffset(ZoneOffset.UTC))), PgText.instanceToString(), PgJson.timestamptz);
    PgType<Instant[]> timestamptzArray = timestamptz.array(PgRead.readInstantArray, Instant[]::new);
    PgType<OffsetTime> timetz = PgType.of("timetz", PgRead.readOffsetTime, PgWrite.passObjectToJdbc(), PgText.instanceToString(), PgJson.timetz);
    PgType<OffsetTime[]> timetzArray = timetz.array(PgRead.readOffsetTimeArray, OffsetTime[]::new);
    PgType<PGInterval> interval = pgObject("interval", PGInterval.class).withJson(PgJson.interval);
    PgType<PGInterval[]> intervalArray = interval.array(PgRead.castJdbcArrayTo(PGInterval.class), PGInterval[]::new);

    // UUID
    PgType<UUID> uuid = PgType.of("uuid", PgRead.readUUID, PgWrite.writeUUID, PgText.textUuid, PgJson.uuid);
    PgType<UUID[]> uuidArray = uuid.array(PgRead.massageJdbcArrayTo(UUID[].class), UUID[]::new);

    // JSON types
    PgType<Json> json = ofPgObject("json", Json::new, Json::value, PgJson.json);
    PgType<Json[]> jsonArray = json.array(PgRead.readJsonArray, Json[]::new).withText(PgText.NotWorking());
    PgType<Jsonb> jsonb = ofPgObject("jsonb", Jsonb::new, Jsonb::value, PgJson.jsonb);
    PgType<Jsonb[]> jsonbArray = jsonb.array(PgRead.readJsonbArray, Jsonb[]::new).withText(PgText.NotWorking());

    // Geometric types
    PgType<PGpoint> point = pgObject("point", PGpoint.class).withJson(PgJson.point);
    PgType<PGpoint[]> pointArray = point.array(PgRead.castJdbcArrayTo(PGpoint.class), PGpoint[]::new);
    PgType<PGbox> box = pgObject("box", PGbox.class).withJson(PgJson.box);
    PgType<PGbox[]> boxArray = box.array(PgRead.castJdbcArrayTo(PGbox.class), PGbox[]::new);
    PgType<PGcircle> circle = pgObject("circle", PGcircle.class).withJson(PgJson.circle);
    PgType<PGcircle[]> circleArray = circle.array(PgRead.castJdbcArrayTo(PGcircle.class), PGcircle[]::new);
    PgType<PGline> line = pgObject("line", PGline.class).withJson(PgJson.line);
    PgType<PGline[]> lineArray = line.array(PgRead.castJdbcArrayTo(PGline.class), PGline[]::new);
    PgType<PGlseg> lseg = pgObject("lseg", PGlseg.class).withJson(PgJson.lseg);
    PgType<PGlseg[]> lsegArray = lseg.array(PgRead.castJdbcArrayTo(PGlseg.class), PGlseg[]::new);
    PgType<PGpath> path = pgObject("path", PGpath.class).withJson(PgJson.path);
    PgType<PGpath[]> pathArray = path.array(PgRead.castJdbcArrayTo(PGpath.class), PGpath[]::new);
    PgType<PGpolygon> polygon = pgObject("polygon", PGpolygon.class).withJson(PgJson.polygon);
    PgType<PGpolygon[]> polygonArray = polygon.array(PgRead.castJdbcArrayTo(PGpolygon.class), PGpolygon[]::new);

    // Network types
    PgType<Inet> inet = ofPgObject("inet", Inet::new, Inet::value, PgJson.inet);
    PgType<Inet[]> inetArray = inet.array(PgRead.pgObjectArray(Inet::new, Inet.class), Inet[]::new);

    // Money
    PgType<Money> money = PgType.of("money", PgRead.readDouble.map(Money::new), PgWrite.pgObject("money").contramap(m -> String.valueOf(m.value())), PgText.textDouble.contramap(Money::value), PgJson.money);
    PgType<Money[]> moneyArray = money.array(PgRead.readMoneyArray, Money[]::new);

    // Hstore
    PgType<Map<String, String>> hstore = PgType.of("hstore", PgRead.readMapStringString, PgWrite.passObjectToJdbc(), PgText.textMapStringString, PgJson.hstore);

    // XML
    PgType<Xml> xml = PgType.of("xml", PgRead.readString, PgWrite.pgObject("xml"), PgText.textString, PgJson.text).bimap(Xml::new, Xml::value);
    PgType<Xml[]> xmlArray = xml.array(PgRead.pgObjectArray(Xml::new, Xml.class), Xml[]::new);

    // Vector types (for pgvector extension)
    PgType<Vector> vector = PgType.of("vector", PgRead.readString, PgWrite.pgObject("vector"), PgText.textString, PgJson.text).bimap(Vector::new, Vector::value);
    PgType<Vector[]> vectorArray = vector.array(PgRead.pgObjectArray(Vector::new, Vector.class), Vector[]::new);

    // Object identifier types
    PgType<AclItem> aclitem = ofPgObject("aclitem", AclItem::new, AclItem::value, PgJson.aclitem);
    PgType<AclItem[]> aclitemArray = aclitem.array(PgRead.pgObjectArray(AclItem::new, AclItem.class), AclItem[]::new);
    PgType<AnyArray> anyarray = ofPgObject("anyarray", AnyArray::new, AnyArray::value, PgJson.text.bimap(AnyArray::new, AnyArray::value));
    PgType<AnyArray[]> anyarrayArray = anyarray.array(PgRead.pgObjectArray(AnyArray::new, AnyArray.class), AnyArray[]::new);
    PgType<Int2Vector> int2vector = ofPgObject("int2vector", Int2Vector::new, Int2Vector::value, PgJson.int2vector);
    PgType<Int2Vector[]> int2vectorArray = int2vector.array(PgRead.pgObjectArray(Int2Vector::new, Int2Vector.class), Int2Vector[]::new);
    PgType<OidVector> oidvector = ofPgObject("oidvector", OidVector::new, OidVector::value, PgJson.oidvector);
    PgType<OidVector[]> oidvectorArray = oidvector.array(PgRead.pgObjectArray(OidVector::new, OidVector.class), OidVector[]::new);
    PgType<Xid> xid = ofPgObject("xid", Xid::new, Xid::value, PgJson.xid);
    PgType<Xid[]> xidArray = xid.array(PgRead.pgObjectArray(Xid::new, Xid.class), Xid[]::new);

    // Reg* types (system catalog types)
    PgType<Regclass> regclass = ofPgObject("regclass", Regclass::new, Regclass::value, PgJson.regclass);
    PgType<Regclass[]> regclassArray = regclass.array(PgRead.pgObjectArray(Regclass::new, Regclass.class), Regclass[]::new);
    PgType<Regconfig> regconfig = ofPgObject("regconfig", Regconfig::new, Regconfig::value, PgJson.regconfig);
    PgType<Regconfig[]> regconfigArray = regconfig.array(PgRead.pgObjectArray(Regconfig::new, Regconfig.class), Regconfig[]::new);
    PgType<Regdictionary> regdictionary = ofPgObject("regdictionary", Regdictionary::new, Regdictionary::value, PgJson.regdictionary);
    PgType<Regdictionary[]> regdictionaryArray = regdictionary.array(PgRead.pgObjectArray(Regdictionary::new, Regdictionary.class), Regdictionary[]::new);
    PgType<Regnamespace> regnamespace = ofPgObject("regnamespace", Regnamespace::new, Regnamespace::value, PgJson.regnamespace);
    PgType<Regnamespace[]> regnamespaceArray = regnamespace.array(PgRead.pgObjectArray(Regnamespace::new, Regnamespace.class), Regnamespace[]::new);
    PgType<Regoper> regoper = ofPgObject("regoper", Regoper::new, Regoper::value, PgJson.regoper);
    PgType<Regoper[]> regoperArray = regoper.array(PgRead.pgObjectArray(Regoper::new, Regoper.class), Regoper[]::new);
    PgType<Regoperator> regoperator = ofPgObject("regoperator", Regoperator::new, Regoperator::value, PgJson.regoperator);
    PgType<Regoperator[]> regoperatorArray = regoperator.array(PgRead.pgObjectArray(Regoperator::new, Regoperator.class), Regoperator[]::new);
    PgType<Regproc> regproc = ofPgObject("regproc", Regproc::new, Regproc::value, PgJson.regproc);
    PgType<Regproc[]> regprocArray = regproc.array(PgRead.pgObjectArray(Regproc::new, Regproc.class), Regproc[]::new);
    PgType<Regprocedure> regprocedure = ofPgObject("regprocedure", Regprocedure::new, Regprocedure::value, PgJson.regprocedure);
    PgType<Regprocedure[]> regprocedureArray = regprocedure.array(PgRead.pgObjectArray(Regprocedure::new, Regprocedure.class), Regprocedure[]::new);
    PgType<Regrole> regrole = ofPgObject("regrole", Regrole::new, Regrole::value, PgJson.regrole);
    PgType<Regrole[]> regroleArray = regrole.array(PgRead.pgObjectArray(Regrole::new, Regrole.class), Regrole[]::new);
    PgType<Regtype> regtype = ofPgObject("regtype", Regtype::new, Regtype::value, PgJson.regtype);
    PgType<Regtype[]> regtypeArray = regtype.array(PgRead.pgObjectArray(Regtype::new, Regtype.class), Regtype[]::new);

    // Internal types
    PgType<PgNodeTree> pgNodeTree = ofPgObject("pg_node_tree", PgNodeTree::new, PgNodeTree::value, PgJson.text.bimap(PgNodeTree::new, PgNodeTree::value));
    PgType<PgNodeTree[]> pgNodeTreeArray = pgNodeTree.array(PgRead.pgObjectArray(PgNodeTree::new, PgNodeTree.class), PgNodeTree[]::new);
    PgType<Unknown> unknown = PgType.of("unknown", PgRead.readString, PgWrite.pgObject("unknown"), PgText.textString, PgJson.text).bimap(Unknown::new, Unknown::value);
    PgType<Unknown[]> unknownArray = unknown.array(PgRead.pgObjectArray(Unknown::new, Unknown.class), Unknown[]::new);

    // Factory methods
    static <E extends Enum<E>> PgType<E> ofEnum(String sqlType, Function<String, E> fromString) {
        return PgType.of(sqlType, PgRead.readString.map(fromString::apply), PgWrite.writeString.contramap(Enum::name), PgText.textString.contramap(Enum::name), PgJson.text.bimap(fromString::apply, Enum::name));
    }

    static PgType<String> ofPgObject(String sqlType) {
        return PgType.of(sqlType, PgRead.pgObject(sqlType), PgWrite.pgObject(sqlType), PgText.textString, PgJson.text);
    }

    /**
     * Create a PgType backed by PGobject string representation, with a custom PgJson codec.
     * The string representation is used for native DB access, while pgJson handles JSON encoding
     * (e.g., for MULTISET).
     *
     * @param sqlType The SQL type name
     * @param fromString Function to parse the string representation into the target type
     * @param toString Function to convert the target type back to its string representation
     * @param pgJson The JSON codec for this type
     */
    static <T> PgType<T> ofPgObject(String sqlType, SqlFunction<String, T> fromString, Function<T, String> toString, PgJson<T> pgJson) {
        return PgType.of(
                sqlType,
                PgRead.pgObject(sqlType).map(fromString),
                PgWrite.pgObject(sqlType).contramap(toString),
                PgText.textString.contramap(toString),
                pgJson
        );
    }

    static PgType<Record> record(String sqlType) {
        return ofPgObject(sqlType, Record::new, Record::value, PgJson.record);
    }

    static PgType<Record[]> recordArray(String sqlType) {
        return record(sqlType).array(PgRead.pgObjectArray(Record::new, Record.class), Record[]::new);
    }

    static <T extends PGobject> PgType<T> pgObject(String sqlType, Class<T> clazz) {
        // Default JSON impl - just use toString which returns the PGobject value
        PgJson<T> json = new PgJson<>() {
            @Override
            public typo.data.JsonValue toJson(T value) {
                return new typo.data.JsonValue.JString(value.getValue());
            }

            @Override
            public T fromJson(typo.data.JsonValue j) {
                if (!(j instanceof typo.data.JsonValue.JString s)) {
                    throw new IllegalArgumentException("Expected string for " + sqlType + ", got: " + j.getClass().getSimpleName());
                }
                try {
                    T obj = clazz.getDeclaredConstructor().newInstance();
                    obj.setValue(s.value());
                    return obj;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        return new PgType<>(PgTypename.of(sqlType), PgRead.castJdbcObjectTo(clazz), PgWrite.passObjectToJdbc(), PgText.textPGobject(), json);
    }

    static PgType<String> bpchar(int precision) {
        return PgType.of(PgTypename.of("bpchar", precision), PgRead.readString, PgWrite.writeString, PgText.textString, PgJson.text);
    }

    static PgType<String[]> bpcharArray(int n) {
        return bpchar(n).array(PgRead.readStringArray, String[]::new);
    }
}

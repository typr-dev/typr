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
    PgType<AclItem> aclitem = ofPgObject("aclitem").bimap(AclItem::new, AclItem::value);
    PgType<AclItem[]> aclitemArray = aclitem.array(PgRead.pgObjectArray(AclItem::new, AclItem.class));
    PgType<AnyArray> anyarray = ofPgObject("anyarray").bimap(AnyArray::new, AnyArray::value);
    PgType<AnyArray[]> anyarrayArray = anyarray.array(PgRead.pgObjectArray(AnyArray::new, AnyArray.class));
    PgType<BigDecimal> numeric = PgType.of("numeric", PgRead.readBigDecimal, PgWrite.writeBigDecimal, PgText.textBigDecimal);
    PgType<BigDecimal[]> numericArray = numeric.array(PgRead.readBigDecimalArray);
    PgType<Boolean> bool = PgType.of("bool", PgRead.readBoolean, PgWrite.writeBoolean, PgText.textBoolean);
    PgType<Boolean[]> boolArray = bool.array(PgRead.readBooleanArray);
    PgType<Double> float8 = PgType.of("float8", PgRead.readDouble, PgWrite.writeDouble, PgText.textDouble);
    PgType<Double[]> float8Array = float8.array(PgRead.readDoubleArray);
    PgType<Float> float4 = PgType.of("float4", PgRead.readFloat, PgWrite.writeFloat, PgText.textFloat);
    PgType<Float[]> float4Array = float4.array(PgRead.readFloatArray);
    PgType<Inet> inet = ofPgObject("inet").bimap(Inet::new, Inet::value);
    PgType<Inet[]> inetArray = inet.array(PgRead.pgObjectArray(Inet::new, Inet.class));
    PgType<Instant> timestamptz = PgType.of("timestamptz", PgRead.readInstant, PgWrite.primitive((ps, i, v) -> ps.setObject(i, v.atOffset(ZoneOffset.UTC))), PgText.instanceToString());
    PgType<Instant[]> timestamptzArray = timestamptz.array(PgRead.readInstantArray);
    PgType<Int2Vector> int2vector = ofPgObject("int2vector").bimap(Int2Vector::new, Int2Vector::value);
    PgType<Int2Vector[]> int2vectorArray = int2vector.array(PgRead.pgObjectArray(Int2Vector::new, Int2Vector.class));
    PgType<Integer> int4 = PgType.of("int4", PgRead.readInteger, PgWrite.writeInteger, PgText.textInteger);
    PgType<Integer[]> int4Array = int4.array(PgRead.readIntegerArray);
    PgType<Json> json = ofPgObject("json").bimap(Json::new, Json::value);
    PgType<Json[]> jsonArray = json.array(PgRead.readJsonArray).withText(PgText.NotWorking());
    PgType<Jsonb> jsonb = ofPgObject("jsonb").bimap(Jsonb::new, Jsonb::value);
    PgType<Jsonb[]> jsonbArray = jsonb.array(PgRead.readJsonbArray);
    PgType<LocalDate> date = PgType.of("date", PgRead.readLocalDate, PgWrite.passObjectToJdbc(), PgText.instanceToString());
    PgType<LocalDateTime> timestamp = PgType.of("timestamp", PgRead.readLocalDateTime, PgWrite.passObjectToJdbc(), PgText.instanceToString());
    PgType<LocalDateTime[]> timestampArray = timestamp.array(PgRead.readLocalDateTimeArray);
    PgType<LocalDate[]> dateArray = date.array(PgRead.readLocalDateArray);
    PgType<LocalTime> time = PgType.of("time", PgRead.readLocalTime, PgWrite.passObjectToJdbc(), PgText.instanceToString());
    PgType<LocalTime[]> timeArray = time.array(PgRead.readLocalTimeArray);
    PgType<Long> int8 = PgType.of("int8", PgRead.readLong, PgWrite.writeLong, PgText.textLong);
    PgType<Long[]> int8Array = int8.array(PgRead.readLongArray);
    PgType<Map<String, String>> hstore = PgType.of("hstore", PgRead.readMapStringString, PgWrite.passObjectToJdbc(), PgText.textMapStringString);
    PgType<Money> money = PgType.of("money", PgRead.readDouble.map(Money::new), PgWrite.pgObject("money").contramap(m -> String.valueOf(m.value())), PgText.textDouble.contramap(Money::value));
    PgType<Money[]> moneyArray = money.array(PgRead.readMoneyArray);
    PgType<String> name = PgType.of("name", PgRead.readString, PgWrite.writeString, PgText.textString);
    PgType<String[]> nameArray = name.array(PgRead.readStringArray);
    PgType<OffsetTime> timetz = PgType.of("timetz", PgRead.readOffsetTime, PgWrite.passObjectToJdbc(), PgText.instanceToString());
    PgType<OffsetTime[]> timetzArray = timetz.array(PgRead.readOffsetTimeArray);
    PgType<OidVector> oidvector = ofPgObject("oidvector").bimap(OidVector::new, OidVector::value);
    PgType<OidVector[]> oidvectorArray = oidvector.array(PgRead.pgObjectArray(OidVector::new, OidVector.class));
    PgType<PGInterval> interval = pgObject("interval", PGInterval.class);
    PgType<PGInterval[]> intervalArray = interval.array(PgRead.castJdbcArrayTo(PGInterval.class));
    PgType<PGbox> box = pgObject("box", PGbox.class);
    PgType<PGbox[]> boxArray = box.array(PgRead.castJdbcArrayTo(PGbox.class));
    PgType<PGcircle> circle = pgObject("circle", PGcircle.class);
    PgType<PGcircle[]> circleArray = circle.array(PgRead.castJdbcArrayTo(PGcircle.class));
    PgType<PGline> line = pgObject("line", PGline.class);
    PgType<PGline[]> lineArray = line.array(PgRead.castJdbcArrayTo(PGline.class));
    PgType<PGlseg> lseg = pgObject("lseg", PGlseg.class);
    PgType<PGlseg[]> lsegArray = lseg.array(PgRead.castJdbcArrayTo(PGlseg.class));
    PgType<PGpath> path = pgObject("path", PGpath.class);
    PgType<PGpath[]> pathArray = path.array(PgRead.castJdbcArrayTo(PGpath.class));
    PgType<PGpoint> point = pgObject("point", PGpoint.class);
    PgType<PGpoint[]> pointArray = point.array(PgRead.castJdbcArrayTo(PGpoint.class));
    PgType<PGpolygon> polygon = pgObject("polygon", PGpolygon.class);
    PgType<PGpolygon[]> polygonArray = polygon.array(PgRead.castJdbcArrayTo(PGpolygon.class));
    PgType<PgNodeTree> pgNodeTree = ofPgObject("pg_node_tree").bimap(PgNodeTree::new, PgNodeTree::value);
    PgType<PgNodeTree[]> pgNodeTreeArray = pgNodeTree.array(PgRead.pgObjectArray(PgNodeTree::new, PgNodeTree.class));
    PgType<Regclass> regclass = ofPgObject("regclass").bimap(Regclass::new, Regclass::value);
    PgType<Regclass[]> regclassArray = regclass.array(PgRead.pgObjectArray(Regclass::new, Regclass.class));
    PgType<Regconfig> regconfig = ofPgObject("regconfig").bimap(Regconfig::new, Regconfig::value);
    PgType<Regconfig[]> regconfigArray = regconfig.array(PgRead.pgObjectArray(Regconfig::new, Regconfig.class));
    PgType<Regdictionary> regdictionary = ofPgObject("regdictionary").bimap(Regdictionary::new, Regdictionary::value);
    PgType<Regdictionary[]> regdictionaryArray = regdictionary.array(PgRead.pgObjectArray(Regdictionary::new, Regdictionary.class));
    PgType<Regnamespace> regnamespace = ofPgObject("regnamespace").bimap(Regnamespace::new, Regnamespace::value);
    PgType<Regnamespace[]> regnamespaceArray = regnamespace.array(PgRead.pgObjectArray(Regnamespace::new, Regnamespace.class));
    PgType<Regoper> regoper = ofPgObject("regoper").bimap(Regoper::new, Regoper::value);
    PgType<Regoper[]> regoperArray = regoper.array(PgRead.pgObjectArray(Regoper::new, Regoper.class));
    PgType<Regoperator> regoperator = ofPgObject("regoperator").bimap(Regoperator::new, Regoperator::value);
    PgType<Regoperator[]> regoperatorArray = regoperator.array(PgRead.pgObjectArray(Regoperator::new, Regoperator.class));
    PgType<Regproc> regproc = ofPgObject("regproc").bimap(Regproc::new, Regproc::value);
    PgType<Regproc[]> regprocArray = regproc.array(PgRead.pgObjectArray(Regproc::new, Regproc.class));
    PgType<Regprocedure> regprocedure = ofPgObject("regprocedure").bimap(Regprocedure::new, Regprocedure::value);
    PgType<Regprocedure[]> regprocedureArray = regprocedure.array(PgRead.pgObjectArray(Regprocedure::new, Regprocedure.class));
    PgType<Regrole> regrole = ofPgObject("regrole").bimap(Regrole::new, Regrole::value);
    PgType<Regrole[]> regroleArray = regrole.array(PgRead.pgObjectArray(Regrole::new, Regrole.class));
    PgType<Regtype> regtype = ofPgObject("regtype").bimap(Regtype::new, Regtype::value);
    PgType<Regtype[]> regtypeArray = regtype.array(PgRead.pgObjectArray(Regtype::new, Regtype.class));
    PgType<Short> int2 = PgType.of("int2", PgRead.readShort, PgWrite.writeShort, PgText.textShort);
    PgType<Short> smallint = int2.withTypename(PgTypename.of("smallint"));
    PgType<Short[]> int2Array = int2.array(PgRead.readShortArray);
    PgType<Short[]> smallintArray = int2Array.renamed("smallint");
    PgType<String> bpchar = PgType.of("bpchar", PgRead.readString, PgWrite.writeString, PgText.textString);
    PgType<String> text = PgType.of("text", PgRead.readString, PgWrite.writeString, PgText.textString);
    PgType<String[]> bpcharArray = bpchar.array(PgRead.readStringArray);
    PgType<String[]> textArray = text.array(PgRead.readStringArray);
    PgType<UUID> uuid = PgType.of("uuid", PgRead.readUUID, PgWrite.writeUUID, PgText.textUuid);
    PgType<UUID[]> uuidArray = uuid.array(PgRead.massageJdbcArrayTo(UUID[].class));
    PgType<Xid> xid = ofPgObject("xid").bimap(Xid::new, Xid::value);
    PgType<Xid[]> xidArray = xid.array(PgRead.pgObjectArray(Xid::new, Xid.class));
    PgType<Xml> xml = PgType.of("xml", PgRead.readString, PgWrite.pgObject("xml"), PgText.textString).bimap(Xml::new, Xml::value);
    PgType<Xml[]> xmlArray = xml.array(PgRead.pgObjectArray(Xml::new, Xml.class));
    PgType<Vector> vector = PgType.of("vector", PgRead.readString, PgWrite.pgObject("vector"), PgText.textString).bimap(Vector::new, Vector::value);
    PgType<Vector[]> vectorArray = vector.array(PgRead.pgObjectArray(Vector::new, Vector.class));
    PgType<Unknown> unknown = PgType.of("unknown", PgRead.readString, PgWrite.pgObject("unknown"), PgText.textString).bimap(Unknown::new, Unknown::value);
    PgType<Unknown[]> unknownArray = unknown.array(PgRead.pgObjectArray(Unknown::new, Unknown.class));
    PgType<byte[]> bytea = PgType.of("bytea", PgRead.readByteArray, PgWrite.writeByteArray, PgText.textByteArray);

    static <E extends Enum<E>> PgType<E> ofEnum(String sqlType, Function<String, E> fromString) {
        return PgType.of(sqlType, PgRead.readString.map(fromString::apply), PgWrite.writeString.contramap(Enum::name), PgText.textString.contramap(Enum::name));
    }

    static PgType<String> ofPgObject(String sqlType) {
        return PgType.of(sqlType, PgRead.pgObject(sqlType), PgWrite.pgObject(sqlType), PgText.textString);
    }

    static PgType<Record> record(String sqlType) {
        return ofPgObject(sqlType).bimap(Record::new, Record::value);
    }

    static PgType<Record[]> recordArray(String sqlType) {
        return record(sqlType).array(PgRead.pgObjectArray(Record::new, Record.class));
    }

    static <T extends PGobject> PgType<T> pgObject(String sqlType, Class<T> clazz) {
        return new PgType<>(PgTypename.of(sqlType), PgRead.castJdbcObjectTo(clazz), PgWrite.passObjectToJdbc(), PgText.textPGobject());
    }

    static PgType<String> bpchar(int precision) {
        return PgType.of(PgTypename.of("bpchar", precision), PgRead.readString, PgWrite.writeString, PgText.textString);
    }

    static PgType<String[]> bpcharArray(int n) {
        return bpchar(n).array(PgRead.readStringArray);
    }
}

package typr.runtime;

import java.math.BigDecimal;
import java.time.*;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.postgresql.geometric.*;
import org.postgresql.util.PGInterval;
import org.postgresql.util.PGobject;
import typr.data.*;
import typr.data.Record;

public interface PgTypes {
  PgType<AclItem> aclitem = ofPgObject("aclitem", AclItem::new, AclItem::value, PgJson.aclitem);
  PgType<AclItem[]> aclitemArray =
      aclitem.array(PgRead.pgObjectArray(AclItem::new, AclItem.class), AclItem[]::new);
  PgType<AnyArray> anyarray =
      ofPgObject(
          "anyarray",
          AnyArray::new,
          AnyArray::value,
          PgJson.text.bimap(AnyArray::new, AnyArray::value));
  PgType<AnyArray[]> anyarrayArray =
      anyarray.array(PgRead.pgObjectArray(AnyArray::new, AnyArray.class), AnyArray[]::new);
  PgType<BigDecimal> numeric =
      PgType.of(
          "numeric",
          PgRead.readBigDecimal,
          PgWrite.writeBigDecimal,
          PgText.textBigDecimal,
          PgJson.numeric);
  PgType<BigDecimal[]> numericArray = numeric.array(PgRead.readBigDecimalArray, BigDecimal[]::new);
  PgType<Boolean> bool =
      PgType.of("bool", PgRead.readBoolean, PgWrite.writeBoolean, PgText.textBoolean, PgJson.bool);
  PgType<Boolean[]> boolArray = bool.array(PgRead.readBooleanArray, Boolean[]::new);

  @SuppressWarnings("unchecked")
  PgType<boolean[]> boolArrayUnboxed =
      PgType.of(
          (PgTypename<boolean[]>) (PgTypename<?>) PgTypename.of("bool").array(),
          PgRead.readBooleanArrayUnboxed,
          PgWrite.writeBooleanArrayUnboxed,
          PgText.boolArrayUnboxed,
          PgJson.boolArrayUnboxed);

  PgType<Double> float8 =
      PgType.of("float8", PgRead.readDouble, PgWrite.writeDouble, PgText.textDouble, PgJson.float8);
  PgType<Double[]> float8Array = float8.array(PgRead.readDoubleArray, Double[]::new);

  @SuppressWarnings("unchecked")
  PgType<double[]> float8ArrayUnboxed =
      PgType.of(
          (PgTypename<double[]>) (PgTypename<?>) PgTypename.of("float8").array(),
          PgRead.readDoubleArrayUnboxed,
          PgWrite.writeDoubleArrayUnboxed,
          PgText.doubleArrayUnboxed,
          PgJson.doubleArrayUnboxed);

  PgType<Float> float4 =
      PgType.of("float4", PgRead.readFloat, PgWrite.writeFloat, PgText.textFloat, PgJson.float4);
  PgType<Float[]> float4Array = float4.array(PgRead.readFloatArray, Float[]::new);

  @SuppressWarnings("unchecked")
  PgType<float[]> float4ArrayUnboxed =
      PgType.of(
          (PgTypename<float[]>) (PgTypename<?>) PgTypename.of("float4").array(),
          PgRead.readFloatArrayUnboxed,
          PgWrite.writeFloatArrayUnboxed,
          PgText.floatArrayUnboxed,
          PgJson.floatArrayUnboxed);

  PgType<Inet> inet = ofPgObject("inet", Inet::new, Inet::value, PgJson.inet);
  PgType<Inet[]> inetArray = inet.array(PgRead.pgObjectArray(Inet::new, Inet.class), Inet[]::new);
  PgType<Instant> timestamptz =
      PgType.of(
          "timestamptz",
          PgRead.readInstant,
          PgWrite.primitive((ps, i, v) -> ps.setObject(i, v.atOffset(ZoneOffset.UTC))),
          PgText.instance(
              (t, sb) -> sb.append(t.atOffset(ZoneOffset.UTC).toString().replace('T', ' '))),
          PgJson.timestamptz);
  PgType<Instant[]> timestamptzArray = timestamptz.array(PgRead.readInstantArray, Instant[]::new);
  PgType<Int2Vector> int2vector =
      ofPgObject("int2vector", Int2Vector::new, Int2Vector::value, PgJson.int2vector);
  PgType<Int2Vector[]> int2vectorArray =
      int2vector.array(PgRead.pgObjectArray(Int2Vector::new, Int2Vector.class), Int2Vector[]::new);
  PgType<Integer> int4 =
      PgType.of("int4", PgRead.readInteger, PgWrite.writeInteger, PgText.textInteger, PgJson.int4);
  PgType<Integer[]> int4Array = int4.array(PgRead.readIntegerArray, Integer[]::new);

  @SuppressWarnings("unchecked")
  PgType<int[]> int4ArrayUnboxed =
      PgType.of(
          (PgTypename<int[]>) (PgTypename<?>) PgTypename.of("int4").array(),
          PgRead.readIntArrayUnboxed,
          PgWrite.writeIntArrayUnboxed,
          PgText.intArrayUnboxed,
          PgJson.intArrayUnboxed);

  PgType<Json> json = ofPgObject("json", Json::new, Json::value, PgJson.json);
  PgType<Json[]> jsonArray = json.array(PgRead.readJsonArray, Json[]::new);
  PgType<Jsonb> jsonb = ofPgObject("jsonb", Jsonb::new, Jsonb::value, PgJson.jsonb);
  PgType<Jsonb[]> jsonbArray = jsonb.array(PgRead.readJsonbArray, Jsonb[]::new);
  PgType<LocalDate> date =
      PgType.of(
          "date",
          PgRead.readLocalDate,
          PgWrite.passObjectToJdbc(),
          PgText.instance((d, sb) -> sb.append(d.toString())),
          PgJson.date);
  PgType<LocalDateTime> timestamp =
      PgType.of(
          "timestamp",
          PgRead.readLocalDateTime,
          PgWrite.passObjectToJdbc(),
          PgText.instance((t, sb) -> sb.append(t.toString().replace('T', ' '))),
          PgJson.timestamp);
  PgType<LocalDateTime[]> timestampArray =
      timestamp.array(PgRead.readLocalDateTimeArray, LocalDateTime[]::new);
  PgType<LocalDate[]> dateArray = date.array(PgRead.readLocalDateArray, LocalDate[]::new);
  PgType<LocalTime> time =
      PgType.of(
          "time",
          PgRead.readLocalTime,
          PgWrite.passObjectToJdbc(),
          PgText.instance((t, sb) -> sb.append(t.toString())),
          PgJson.time);
  PgType<LocalTime[]> timeArray = time.array(PgRead.readLocalTimeArray, LocalTime[]::new);
  PgType<Long> int8 =
      PgType.of("int8", PgRead.readLong, PgWrite.writeLong, PgText.textLong, PgJson.int8);
  PgType<Long[]> int8Array = int8.array(PgRead.readLongArray, Long[]::new);

  @SuppressWarnings("unchecked")
  PgType<long[]> int8ArrayUnboxed =
      PgType.of(
          (PgTypename<long[]>) (PgTypename<?>) PgTypename.of("int8").array(),
          PgRead.readLongArrayUnboxed,
          PgWrite.writeLongArrayUnboxed,
          PgText.longArrayUnboxed,
          PgJson.longArrayUnboxed);

  PgType<Map<String, String>> hstore =
      PgType.of(
          "hstore",
          PgRead.readMapStringString,
          PgWrite.passObjectToJdbc(),
          PgText.textMapStringString,
          PgJson.hstore);
  PgType<Money> money =
      PgType.of(
          "money",
          PgRead.readDouble.map(Money::new),
          PgWrite.pgObject("money").contramap(m -> String.valueOf(m.value())),
          PgText.textDouble.contramap(Money::value),
          PgJson.money);
  PgType<Money[]> moneyArray = money.array(PgRead.readMoneyArray, Money[]::new);
  // name is a 63-character identifier type in PostgreSQL, mapped to String
  PgType<String> name =
      PgType.of("name", PgRead.readString, PgWrite.writeString, PgText.textString, PgJson.text);
  PgType<String[]> nameArray = name.array(PgRead.readStringArray, String[]::new);
  PgType<OffsetTime> timetz =
      PgType.of(
          "timetz",
          PgRead.readOffsetTime,
          PgWrite.passObjectToJdbc(),
          PgText.instance((t, sb) -> sb.append(t.toString())),
          PgJson.timetz);
  PgType<OffsetTime[]> timetzArray = timetz.array(PgRead.readOffsetTimeArray, OffsetTime[]::new);
  PgType<OidVector> oidvector =
      ofPgObject("oidvector", OidVector::new, OidVector::value, PgJson.oidvector);
  PgType<OidVector[]> oidvectorArray =
      oidvector.array(PgRead.pgObjectArray(OidVector::new, OidVector.class), OidVector[]::new);
  PgType<PGInterval> interval = pgObject("interval", PGInterval.class, PgJson.interval);
  PgType<PGInterval[]> intervalArray =
      interval.array(PgRead.castJdbcArrayTo(PGInterval.class), PGInterval[]::new);
  PgType<PGbox> box = pgObject("box", PGbox.class, PgJson.box);
  PgType<PGbox[]> boxArray = box.array(PgRead.castJdbcArrayTo(PGbox.class), PGbox[]::new);
  PgType<PGcircle> circle = pgObject("circle", PGcircle.class, PgJson.circle);
  PgType<PGcircle[]> circleArray =
      circle.array(PgRead.castJdbcArrayTo(PGcircle.class), PGcircle[]::new);
  PgType<PGline> line = pgObject("line", PGline.class, PgJson.line);
  PgType<PGline[]> lineArray = line.array(PgRead.castJdbcArrayTo(PGline.class), PGline[]::new);
  PgType<PGlseg> lseg = pgObject("lseg", PGlseg.class, PgJson.lseg);
  PgType<PGlseg[]> lsegArray = lseg.array(PgRead.castJdbcArrayTo(PGlseg.class), PGlseg[]::new);
  PgType<PGpath> path = pgObject("path", PGpath.class, PgJson.path);
  PgType<PGpath[]> pathArray = path.array(PgRead.castJdbcArrayTo(PGpath.class), PGpath[]::new);
  PgType<PGpoint> point = pgObject("point", PGpoint.class, PgJson.point);
  PgType<PGpoint[]> pointArray = point.array(PgRead.castJdbcArrayTo(PGpoint.class), PGpoint[]::new);
  PgType<PGpolygon> polygon = pgObject("polygon", PGpolygon.class, PgJson.polygon);
  PgType<PGpolygon[]> polygonArray =
      polygon.array(PgRead.castJdbcArrayTo(PGpolygon.class), PGpolygon[]::new);
  PgType<PgNodeTree> pgNodeTree =
      ofPgObject(
          "pg_node_tree",
          PgNodeTree::new,
          PgNodeTree::value,
          PgJson.text.bimap(PgNodeTree::new, PgNodeTree::value));
  PgType<PgNodeTree[]> pgNodeTreeArray =
      pgNodeTree.array(PgRead.pgObjectArray(PgNodeTree::new, PgNodeTree.class), PgNodeTree[]::new);
  PgType<Regclass> regclass =
      ofPgObject("regclass", Regclass::new, Regclass::value, PgJson.regclass);
  PgType<Regclass[]> regclassArray =
      regclass.array(PgRead.pgObjectArray(Regclass::new, Regclass.class), Regclass[]::new);
  PgType<Regconfig> regconfig =
      ofPgObject("regconfig", Regconfig::new, Regconfig::value, PgJson.regconfig);
  PgType<Regconfig[]> regconfigArray =
      regconfig.array(PgRead.pgObjectArray(Regconfig::new, Regconfig.class), Regconfig[]::new);
  PgType<Regdictionary> regdictionary =
      ofPgObject("regdictionary", Regdictionary::new, Regdictionary::value, PgJson.regdictionary);
  PgType<Regdictionary[]> regdictionaryArray =
      regdictionary.array(
          PgRead.pgObjectArray(Regdictionary::new, Regdictionary.class), Regdictionary[]::new);
  PgType<Regnamespace> regnamespace =
      ofPgObject("regnamespace", Regnamespace::new, Regnamespace::value, PgJson.regnamespace);
  PgType<Regnamespace[]> regnamespaceArray =
      regnamespace.array(
          PgRead.pgObjectArray(Regnamespace::new, Regnamespace.class), Regnamespace[]::new);
  PgType<Regoper> regoper = ofPgObject("regoper", Regoper::new, Regoper::value, PgJson.regoper);
  PgType<Regoper[]> regoperArray =
      regoper.array(PgRead.pgObjectArray(Regoper::new, Regoper.class), Regoper[]::new);
  PgType<Regoperator> regoperator =
      ofPgObject("regoperator", Regoperator::new, Regoperator::value, PgJson.regoperator);
  PgType<Regoperator[]> regoperatorArray =
      regoperator.array(
          PgRead.pgObjectArray(Regoperator::new, Regoperator.class), Regoperator[]::new);
  PgType<Regproc> regproc = ofPgObject("regproc", Regproc::new, Regproc::value, PgJson.regproc);
  PgType<Regproc[]> regprocArray =
      regproc.array(PgRead.pgObjectArray(Regproc::new, Regproc.class), Regproc[]::new);
  PgType<Regprocedure> regprocedure =
      ofPgObject("regprocedure", Regprocedure::new, Regprocedure::value, PgJson.regprocedure);
  PgType<Regprocedure[]> regprocedureArray =
      regprocedure.array(
          PgRead.pgObjectArray(Regprocedure::new, Regprocedure.class), Regprocedure[]::new);
  PgType<Regrole> regrole = ofPgObject("regrole", Regrole::new, Regrole::value, PgJson.regrole);
  PgType<Regrole[]> regroleArray =
      regrole.array(PgRead.pgObjectArray(Regrole::new, Regrole.class), Regrole[]::new);
  PgType<Regtype> regtype = ofPgObject("regtype", Regtype::new, Regtype::value, PgJson.regtype);
  PgType<Regtype[]> regtypeArray =
      regtype.array(PgRead.pgObjectArray(Regtype::new, Regtype.class), Regtype[]::new);
  PgType<Short> int2 =
      PgType.of("int2", PgRead.readShort, PgWrite.writeShort, PgText.textShort, PgJson.int2);
  PgType<Short> smallint = int2.withTypename(PgTypename.of("smallint"));
  PgType<Short[]> int2Array = int2.array(PgRead.readShortArray, Short[]::new);

  @SuppressWarnings("unchecked")
  PgType<short[]> int2ArrayUnboxed =
      PgType.of(
          (PgTypename<short[]>) (PgTypename<?>) PgTypename.of("int2").array(),
          PgRead.readShortArrayUnboxed,
          PgWrite.writeShortArrayUnboxed,
          PgText.shortArrayUnboxed,
          PgJson.shortArrayUnboxed);

  PgType<Short[]> smallintArray = int2Array.renamed("smallint");
  PgType<short[]> smallintArrayUnboxed = int2ArrayUnboxed.renamed("smallint");
  PgType<String> bpchar =
      PgType.of("bpchar", PgRead.readString, PgWrite.writeString, PgText.textString, PgJson.text);
  PgType<String> text =
      PgType.of("text", PgRead.readString, PgWrite.writeString, PgText.textString, PgJson.text);
  PgType<String[]> bpcharArray = bpchar.array(PgRead.readStringArray, String[]::new);
  PgType<String[]> textArray = text.array(PgRead.readStringArray, String[]::new);
  PgType<UUID> uuid =
      PgType.of("uuid", PgRead.readUUID, PgWrite.writeUUID, PgText.textUuid, PgJson.uuid);
  PgType<UUID[]> uuidArray = uuid.array(PgRead.massageJdbcArrayTo(UUID[].class), UUID[]::new);
  PgType<Xid> xid = ofPgObject("xid", Xid::new, Xid::value, PgJson.xid);
  PgType<Xid[]> xidArray = xid.array(PgRead.pgObjectArray(Xid::new, Xid.class), Xid[]::new);
  PgType<Xml> xml =
      PgType.of("xml", PgRead.readString, PgWrite.pgObject("xml"), PgText.textString, PgJson.text)
          .bimap(Xml::new, Xml::value);
  PgType<Xml[]> xmlArray = xml.array(PgRead.pgObjectArray(Xml::new, Xml.class), Xml[]::new);
  PgType<Vector> vector =
      PgType.of(
              "vector",
              PgRead.readString,
              PgWrite.pgObject("vector"),
              PgText.textString,
              PgJson.text)
          .bimap(Vector::new, Vector::value);
  PgType<Vector[]> vectorArray =
      vector.array(PgRead.pgObjectArray(Vector::new, Vector.class), Vector[]::new);
  PgType<Unknown> unknown =
      PgType.of(
              "unknown",
              PgRead.readString,
              PgWrite.pgObject("unknown"),
              PgText.textString,
              PgJson.text)
          .bimap(Unknown::new, Unknown::value);
  PgType<Unknown[]> unknownArray =
      unknown.array(PgRead.pgObjectArray(Unknown::new, Unknown.class), Unknown[]::new);
  PgType<byte[]> bytea =
      PgType.of(
          "bytea",
          PgRead.readByteArray,
          PgWrite.writeByteArray,
          PgText.textByteArray,
          PgJson.bytea);

  // Range types - discrete types (int, date) are normalized to canonical [) form via Range factory
  // methods
  PgType<Range<Integer>> int4range =
      rangeType("int4range", RangeParser.INT4_PARSER, Range.INT4, PgJson.int4range);
  PgType<Range<Integer>[]> int4rangeArray =
      int4range.array(rangeArrayRead(RangeParser.INT4_PARSER, Range.INT4), rangeArrayFactory());
  PgType<Range<Long>> int8range =
      rangeType("int8range", RangeParser.INT8_PARSER, Range.INT8, PgJson.int8range);
  PgType<Range<Long>[]> int8rangeArray =
      int8range.array(rangeArrayRead(RangeParser.INT8_PARSER, Range.INT8), rangeArrayFactory());
  PgType<Range<BigDecimal>> numrange =
      rangeType("numrange", RangeParser.NUMERIC_PARSER, Range.NUMERIC, PgJson.numrange);
  PgType<Range<BigDecimal>[]> numrangeArray =
      numrange.array(
          rangeArrayRead(RangeParser.NUMERIC_PARSER, Range.NUMERIC), rangeArrayFactory());
  PgType<Range<LocalDate>> daterange =
      rangeType("daterange", RangeParser.DATE_PARSER, Range.DATE, PgJson.daterange);
  PgType<Range<LocalDate>[]> daterangeArray =
      daterange.array(rangeArrayRead(RangeParser.DATE_PARSER, Range.DATE), rangeArrayFactory());
  PgType<Range<LocalDateTime>> tsrange =
      rangeType("tsrange", RangeParser.TIMESTAMP_PARSER, Range.TIMESTAMP, PgJson.tsrange);
  PgType<Range<LocalDateTime>[]> tsrangeArray =
      tsrange.array(
          rangeArrayRead(RangeParser.TIMESTAMP_PARSER, Range.TIMESTAMP), rangeArrayFactory());
  PgType<Range<Instant>> tstzrange =
      rangeType("tstzrange", RangeParser.TIMESTAMPTZ_PARSER, Range.TIMESTAMPTZ, PgJson.tstzrange);
  PgType<Range<Instant>[]> tstzrangeArray =
      tstzrange.array(
          rangeArrayRead(RangeParser.TIMESTAMPTZ_PARSER, Range.TIMESTAMPTZ), rangeArrayFactory());

  static <E extends Enum<E>> PgType<E> ofEnum(String sqlType, Function<String, E> fromString) {
    return PgType.of(
        sqlType,
        PgRead.readString.map(fromString::apply),
        PgWrite.writeString.contramap(Enum::name),
        PgText.textString.contramap(Enum::name),
        PgJson.text.bimap(fromString::apply, Enum::name));
  }

  static <T> PgType<T> ofPgObject(
      String sqlType,
      SqlFunction<String, T> constructor,
      Function<T, String> extractor,
      PgJson<T> json) {
    return PgType.of(
        sqlType,
        PgRead.pgObject(sqlType).map(constructor),
        PgWrite.pgObject(sqlType).contramap(extractor),
        PgText.textString.contramap(extractor),
        json);
  }

  // Default record type for generic composite/record columns
  PgType<Record> record = ofPgObject("record", Record::new, Record::value, PgJson.record);
  PgType<Record[]> recordArray =
      record.array(PgRead.pgObjectArray(Record::new, Record.class), Record[]::new);

  static PgType<Record> record(String sqlType) {
    return ofPgObject(sqlType, Record::new, Record::value, PgJson.record);
  }

  static PgType<Record[]> recordArray(String sqlType) {
    return record(sqlType).array(PgRead.pgObjectArray(Record::new, Record.class), Record[]::new);
  }

  static <T extends PGobject> PgType<T> pgObject(String sqlType, Class<T> clazz, PgJson<T> json) {
    return PgType.of(
        sqlType,
        PgRead.castJdbcObjectTo(clazz),
        PgWrite.passObjectToJdbc(),
        PgText.textPGobject(),
        json);
  }

  static PgType<String> bpchar(int precision) {
    return PgType.of(
        PgTypename.of("bpchar", precision),
        PgRead.readString,
        PgWrite.writeString,
        PgText.textString,
        PgJson.text);
  }

  static PgType<String[]> bpcharArray(int n) {
    return bpchar(n).array(PgRead.readStringArray, String[]::new);
  }

  // Range type helpers
  static <T extends Comparable<? super T>> PgType<Range<T>> rangeType(
      String sqlType,
      SqlFunction<String, T> valueParser,
      java.util.function.BiFunction<RangeBound<T>, RangeBound<T>, Range<T>> rangeFactory,
      PgJson<Range<T>> json) {
    return PgType.of(
        sqlType,
        PgRead.pgObject(sqlType).map(str -> RangeParser.parse(str, valueParser, rangeFactory)),
        PgWrite.pgObject(sqlType).contramap(RangeParser::format),
        PgText.textString.contramap(RangeParser::format),
        json);
  }

  @SuppressWarnings("unchecked")
  static <T extends Comparable<? super T>> PgRead<Range<T>[]> rangeArrayRead(
      SqlFunction<String, T> valueParser,
      java.util.function.BiFunction<RangeBound<T>, RangeBound<T>, Range<T>> rangeFactory) {
    return PgRead.readPgArray.map(
        sqlArray -> {
          Object[] objects = (Object[]) sqlArray.getArray();
          Range<T>[] result =
              (Range<T>[]) java.lang.reflect.Array.newInstance(Range.class, objects.length);
          for (int i = 0; i < objects.length; i++) {
            var pgObj = (org.postgresql.util.PGobject) objects[i];
            result[i] = RangeParser.parse(pgObj.getValue(), valueParser, rangeFactory);
          }
          return result;
        });
  }

  @SuppressWarnings("unchecked")
  static <T extends Comparable<? super T>>
      java.util.function.IntFunction<Range<T>[]> rangeArrayFactory() {
    return n -> (Range<T>[]) java.lang.reflect.Array.newInstance(Range.class, n);
  }
}

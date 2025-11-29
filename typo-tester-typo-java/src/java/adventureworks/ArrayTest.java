package adventureworks;

import adventureworks.customtypes.*;
import adventureworks.public_.Mydomain;
import adventureworks.public_.Myenum;
import adventureworks.public_.pgtest.PgtestRepoImpl;
import adventureworks.public_.pgtest.PgtestRow;
import adventureworks.public_.pgtestnull.PgtestnullRepoImpl;
import adventureworks.public_.pgtestnull.PgtestnullRow;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static adventureworks.JsonEquals.assertJsonEquals;
import static org.junit.Assert.*;

/**
 * Tests for array types and DSL operations - equivalent to Scala ArrayTest.
 */
public class ArrayTest {
    private final PgtestnullRepoImpl pgtestnullRepo = new PgtestnullRepoImpl();
    private final PgtestRepoImpl pgtestRepo = new PgtestRepoImpl();

    static PgtestRow pgTestRow() {
        return new PgtestRow(
            true,
            new TypoBox(3.0, 4.0, 1.0, 2.0),
            "abc",
            new TypoBytea(new Byte[]{1, 2, 3}),
            "a",
            new TypoCircle(new TypoPoint(1.0, 2.0), 3.0),
            TypoLocalDate.now(),
            1.0f,
            2.45,
            new TypoHStore(Map.of("a", "1", "b", "2")),
            new TypoInet("::10.2.3.4"),
            new TypoShort((short) 1),
            new TypoInt2Vector("1 2 3"),
            4,
            (long) Integer.MAX_VALUE + 1,
            new TypoInterval(1, 2, 3, 4, 5, 6.5),
            new TypoJson("{\"a\": 1}"),
            new TypoJsonb("{\"a\": 2}"),
            new TypoLine(3.0, 4.5, 5.5),
            new TypoLineSegment(new TypoPoint(6.5, 4.3), new TypoPoint(1.5, 2.3)),
            new TypoMoney(new BigDecimal("22.50")),
            new Mydomain("a"),
            Myenum.c,
            "foo",
            new BigDecimal("3.14159"),
            new TypoPath(true, List.of(new TypoPoint(6.5, 4.3), new TypoPoint(8.5, 4.3))),
            new TypoPoint(6.5, 4.3),
            new TypoPolygon(List.of(new TypoPoint(6.5, 4.3), new TypoPoint(10.5, 4.3), new TypoPoint(-6.5, 4.3))),
            "flaff",
            TypoLocalTime.now(),
            TypoLocalDateTime.now(),
            TypoInstant.now(),
            TypoOffsetTime.now(),
            TypoUUID.randomUUID(),
            "asd asd ",
            new TypoVector(new Float[]{1.0f, 2.2f, 3.3f}),
            new TypoXml("<xml/>"),
            new TypoBox[]{new TypoBox(3.0, 4.0, 1.0, 2.0)},
            new String[]{"abc"},
            new String[]{"a"},
            new TypoCircle[]{new TypoCircle(new TypoPoint(1.0, 2.0), 3.0)},
            new TypoLocalDate[]{TypoLocalDate.now()},
            new Float[]{1.0f},
            new Double[]{2.45},
            new TypoInet[]{new TypoInet("::10.2.3.4")},
            new TypoShort[]{new TypoShort((short) 1)},
            new TypoInt2Vector[]{new TypoInt2Vector("1 2 3")},
            new Integer[]{4},
            new Long[]{(long) Integer.MAX_VALUE + 1},
            new TypoInterval[]{new TypoInterval(1, 2, 3, 4, 5, 6.5)},
            new TypoJson[]{new TypoJson("{\"a\": 1}")},
            new TypoJsonb[]{new TypoJsonb("{\"a\": 2}")},
            new TypoLine[]{new TypoLine(3.0, 4.5, 5.5)},
            new TypoLineSegment[]{new TypoLineSegment(new TypoPoint(6.5, 4.3), new TypoPoint(1.5, 2.3))},
            new TypoMoney[]{new TypoMoney(new BigDecimal("22.50"))},
            new Mydomain[]{new Mydomain("a")},
            new Myenum[]{Myenum.c},
            new String[]{"foo"},
            new BigDecimal[]{new BigDecimal("3.14159")},
            new TypoPath[]{new TypoPath(true, List.of(new TypoPoint(6.5, 4.3), new TypoPoint(8.5, 4.3)))},
            new TypoPoint[]{new TypoPoint(6.5, 4.3)},
            new TypoPolygon[]{new TypoPolygon(List.of(new TypoPoint(6.5, 4.3), new TypoPoint(10.5, 4.3), new TypoPoint(-6.5, 4.3)))},
            new String[]{"flaff"},
            new TypoLocalTime[]{TypoLocalTime.now()},
            new TypoLocalDateTime[]{TypoLocalDateTime.now()},
            new TypoInstant[]{TypoInstant.now()},
            new TypoOffsetTime[]{TypoOffsetTime.now()},
            new TypoUUID[]{TypoUUID.randomUUID()},
            new String[]{"asd asd "},
            new TypoXml[]{new TypoXml("<xml/>")}
        );
    }

    static PgtestnullRow pgtestnullRow() {
        return new PgtestnullRow(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        );
    }

    static PgtestnullRow pgtestnullRowWithValues() {
        return new PgtestnullRow(
            Optional.of(true),
            Optional.of(new TypoBox(3.0, 4.0, 1.0, 2.0)),
            Optional.of("abc"),
            Optional.of(new TypoBytea(new Byte[]{1, 2, 3})),
            Optional.of("a"),
            Optional.of(new TypoCircle(new TypoPoint(1.0, 2.0), 3.0)),
            Optional.of(TypoLocalDate.now()),
            Optional.of(1.0f),
            Optional.of(2.45),
            Optional.of(new TypoHStore(Map.of("a", "1", "b", "2"))),
            Optional.of(new TypoInet("::10.2.3.4")),
            Optional.of(new TypoShort((short) 1)),
            Optional.of(new TypoInt2Vector("1 2 3")),
            Optional.of(4),
            Optional.of((long) Integer.MAX_VALUE + 1),
            Optional.of(new TypoInterval(1, 2, 3, 4, 5, 6.5)),
            Optional.of(new TypoJson("{\"a\": 1}")),
            Optional.of(new TypoJsonb("{\"a\": 2}")),
            Optional.of(new TypoLine(3.0, 4.5, 5.5)),
            Optional.of(new TypoLineSegment(new TypoPoint(6.5, 4.3), new TypoPoint(1.5, 2.3))),
            Optional.of(new TypoMoney(new BigDecimal("22.50"))),
            Optional.of(new Mydomain("a")),
            Optional.of(Myenum.c),
            Optional.of("foo"),
            Optional.of(new BigDecimal("3.14159")),
            Optional.of(new TypoPath(true, List.of(new TypoPoint(6.5, 4.3), new TypoPoint(8.5, 4.3)))),
            Optional.of(new TypoPoint(6.5, 4.3)),
            Optional.of(new TypoPolygon(List.of(new TypoPoint(6.5, 4.3), new TypoPoint(10.5, 4.3), new TypoPoint(-6.5, 4.3)))),
            Optional.of("flaff"),
            Optional.of(TypoLocalTime.now()),
            Optional.of(TypoLocalDateTime.now()),
            Optional.of(TypoInstant.now()),
            Optional.of(TypoOffsetTime.now()),
            Optional.of(TypoUUID.randomUUID()),
            Optional.of("asd asd "),
            Optional.of(new TypoVector(new Float[]{1.0f, 2.2f, 3.3f})),
            Optional.of(new TypoXml("<xml/>")),
            Optional.of(new TypoBox[]{new TypoBox(3.0, 4.0, 1.0, 2.0)}),
            Optional.of(new String[]{"abc"}),
            Optional.of(new String[]{"a"}),
            Optional.of(new TypoCircle[]{new TypoCircle(new TypoPoint(1.0, 2.0), 3.0)}),
            Optional.of(new TypoLocalDate[]{TypoLocalDate.now()}),
            Optional.of(new Float[]{1.0f}),
            Optional.of(new Double[]{2.45}),
            Optional.of(new TypoInet[]{new TypoInet("::10.2.3.4")}),
            Optional.of(new TypoShort[]{new TypoShort((short) 1)}),
            Optional.of(new TypoInt2Vector[]{new TypoInt2Vector("1 2 3")}),
            Optional.of(new Integer[]{4}),
            Optional.of(new Long[]{(long) Integer.MAX_VALUE + 1}),
            Optional.of(new TypoInterval[]{new TypoInterval(1, 2, 3, 4, 5, 6.5)}),
            Optional.of(new TypoJson[]{new TypoJson("{\"a\": 1}")}),
            Optional.of(new TypoJsonb[]{new TypoJsonb("{\"a\": 2}")}),
            Optional.of(new TypoLine[]{new TypoLine(3.0, 4.5, 5.5)}),
            Optional.of(new TypoLineSegment[]{new TypoLineSegment(new TypoPoint(6.5, 4.3), new TypoPoint(1.5, 2.3))}),
            Optional.of(new TypoMoney[]{new TypoMoney(new BigDecimal("22.50"))}),
            Optional.of(new Mydomain[]{new Mydomain("a")}),
            Optional.of(new Myenum[]{Myenum.c}),
            Optional.of(new String[]{"foo"}),
            Optional.of(new BigDecimal[]{new BigDecimal("3.14159")}),
            Optional.of(new TypoPath[]{new TypoPath(true, List.of(new TypoPoint(6.5, 4.3), new TypoPoint(8.5, 4.3)))}),
            Optional.of(new TypoPoint[]{new TypoPoint(6.5, 4.3)}),
            Optional.of(new TypoPolygon[]{new TypoPolygon(List.of(new TypoPoint(6.5, 4.3), new TypoPoint(10.5, 4.3), new TypoPoint(-6.5, 4.3)))}),
            Optional.of(new String[]{"flaff"}),
            Optional.of(new TypoLocalTime[]{TypoLocalTime.now()}),
            Optional.of(new TypoLocalDateTime[]{TypoLocalDateTime.now()}),
            Optional.of(new TypoInstant[]{TypoInstant.now()}),
            Optional.of(new TypoOffsetTime[]{TypoOffsetTime.now()}),
            Optional.of(new TypoUUID[]{TypoUUID.randomUUID()}),
            Optional.of(new String[]{"asd asd "}),
            Optional.of(new TypoXml[]{new TypoXml("<xml/>")})
        );
    }

    @Test
    public void canInsertPgtestRows() {
        WithConnection.run(c -> {
            var before = pgTestRow();
            var after = pgtestRepo.insert(before, c);
            assertJsonEquals(before, after);
        });
    }

    @Test
    public void canStreamPgtestRows() {
        WithConnection.run(c -> {
            var before = List.of(pgTestRow());
            pgtestRepo.insertStreaming(before.iterator(), 1, c);
            var after = pgtestRepo.selectAll(c);
            assertJsonEquals(before, after);
        });
    }

    @Test
    public void canInsertNullPgtestnullRows() {
        WithConnection.run(c -> {
            var before = pgtestnullRow();
            var after = pgtestnullRepo.insert(before, c);
            assertJsonEquals(before, after);
        });
    }

    @Test
    public void canInsertNonNullPgtestnullRows() {
        WithConnection.run(c -> {
            var before = pgtestnullRowWithValues();
            var after = pgtestnullRepo.insert(before, c);
            assertJsonEquals(before, after);
        });
    }

    @Test
    public void canStreamPgtestnullRows() {
        WithConnection.run(c -> {
            var before = List.of(pgtestnullRow(), pgtestnullRowWithValues());
            pgtestnullRepo.insertStreaming(before.iterator(), 1, c);
            var after = pgtestnullRepo.selectAll(c);
            assertJsonEquals(before, after);
        });
    }

    @Test
    public void canQueryPgtestnullWithDSL() {
        WithConnection.run(c -> {
            var row = pgtestnullRepo.insert(pgtestnullRowWithValues(), c);

            // Test DSL select.where() for all types - same as Scala version
            assertEquals(row.bool(), pgtestnullRepo.select().where(p -> p.bool().isEqual(row.bool().orElse(null))).toList(c).get(0).bool());
            assertEquals(row.box(), pgtestnullRepo.select().where(p -> p.box().isEqual(row.box().orElse(null))).toList(c).get(0).box());
            assertEquals(row.bpchar(), pgtestnullRepo.select().where(p -> p.bpchar().isEqual(row.bpchar().orElse(null))).toList(c).get(0).bpchar());
            assertJsonEquals(row.bytea(), pgtestnullRepo.select().where(p -> p.bytea().isEqual(row.bytea().orElse(null))).toList(c).get(0).bytea());
            assertEquals(row.char_(), pgtestnullRepo.select().where(p -> p.char_().isEqual(row.char_().orElse(null))).toList(c).get(0).char_());
            assertEquals(row.circle(), pgtestnullRepo.select().where(p -> p.circle().isEqual(row.circle().orElse(null))).toList(c).get(0).circle());
            assertEquals(row.date(), pgtestnullRepo.select().where(p -> p.date().isEqual(row.date().orElse(null))).toList(c).get(0).date());
            assertEquals(row.float4(), pgtestnullRepo.select().where(p -> p.float4().isEqual(row.float4().orElse(null))).toList(c).get(0).float4());
            assertEquals(row.float8(), pgtestnullRepo.select().where(p -> p.float8().isEqual(row.float8().orElse(null))).toList(c).get(0).float8());
            assertEquals(row.hstore(), pgtestnullRepo.select().where(p -> p.hstore().isEqual(row.hstore().orElse(null))).toList(c).get(0).hstore());
            assertEquals(row.inet(), pgtestnullRepo.select().where(p -> p.inet().isEqual(row.inet().orElse(null))).toList(c).get(0).inet());
            assertEquals(row.int2(), pgtestnullRepo.select().where(p -> p.int2().isEqual(row.int2().orElse(null))).toList(c).get(0).int2());
            assertEquals(row.int2vector(), pgtestnullRepo.select().where(p -> p.int2vector().isEqual(row.int2vector().orElse(null))).toList(c).get(0).int2vector());
            assertEquals(row.int4(), pgtestnullRepo.select().where(p -> p.int4().isEqual(row.int4().orElse(null))).toList(c).get(0).int4());
            assertEquals(row.int8(), pgtestnullRepo.select().where(p -> p.int8().isEqual(row.int8().orElse(null))).toList(c).get(0).int8());
            assertEquals(row.interval(), pgtestnullRepo.select().where(p -> p.interval().isEqual(row.interval().orElse(null))).toList(c).get(0).interval());
            // json requires special handling in postgres
            assertEquals(row.jsonb(), pgtestnullRepo.select().where(p -> p.jsonb().isEqual(row.jsonb().orElse(null))).toList(c).get(0).jsonb());
            assertEquals(row.line(), pgtestnullRepo.select().where(p -> p.line().isEqual(row.line().orElse(null))).toList(c).get(0).line());
            assertEquals(row.lseg(), pgtestnullRepo.select().where(p -> p.lseg().isEqual(row.lseg().orElse(null))).toList(c).get(0).lseg());
            assertEquals(row.money(), pgtestnullRepo.select().where(p -> p.money().isEqual(row.money().orElse(null))).toList(c).get(0).money());
            assertEquals(row.mydomain(), pgtestnullRepo.select().where(p -> p.mydomain().isEqual(row.mydomain().orElse(null))).toList(c).get(0).mydomain());
            // myenum requires special handling
            assertEquals(row.name(), pgtestnullRepo.select().where(p -> p.name().isEqual(row.name().orElse(null))).toList(c).get(0).name());
            assertEquals(row.numeric(), pgtestnullRepo.select().where(p -> p.numeric().isEqual(row.numeric().orElse(null))).toList(c).get(0).numeric());
            assertEquals(row.path(), pgtestnullRepo.select().where(p -> p.path().isEqual(row.path().orElse(null))).toList(c).get(0).path());
            // point and polygon - need special handling in postgres
            assertEquals(row.text(), pgtestnullRepo.select().where(p -> p.text().isEqual(row.text().orElse(null))).toList(c).get(0).text());
            assertEquals(row.time(), pgtestnullRepo.select().where(p -> p.time().isEqual(row.time().orElse(null))).toList(c).get(0).time());
            assertEquals(row.timestamp(), pgtestnullRepo.select().where(p -> p.timestamp().isEqual(row.timestamp().orElse(null))).toList(c).get(0).timestamp());
            assertEquals(row.timestampz(), pgtestnullRepo.select().where(p -> p.timestampz().isEqual(row.timestampz().orElse(null))).toList(c).get(0).timestampz());
            assertEquals(row.timez(), pgtestnullRepo.select().where(p -> p.timez().isEqual(row.timez().orElse(null))).toList(c).get(0).timez());
            assertEquals(row.uuid(), pgtestnullRepo.select().where(p -> p.uuid().isEqual(row.uuid().orElse(null))).toList(c).get(0).uuid());
            assertEquals(row.varchar(), pgtestnullRepo.select().where(p -> p.varchar().isEqual(row.varchar().orElse(null))).toList(c).get(0).varchar());
            assertJsonEquals(row.vector(), pgtestnullRepo.select().where(p -> p.vector().isEqual(row.vector().orElse(null))).toList(c).get(0).vector());
            // xml requires special handling

            // Array types
            assertArrayEquals(row.datees().orElse(null), pgtestnullRepo.select().where(p -> p.datees().isEqual(row.datees().orElse(null))).toList(c).get(0).datees().orElse(null));
            assertArrayEquals(row.float4es().orElse(null), pgtestnullRepo.select().where(p -> p.float4es().isEqual(row.float4es().orElse(null))).toList(c).get(0).float4es().orElse(null));
            assertArrayEquals(row.float8es().orElse(null), pgtestnullRepo.select().where(p -> p.float8es().isEqual(row.float8es().orElse(null))).toList(c).get(0).float8es().orElse(null));
            assertArrayEquals(row.inetes().orElse(null), pgtestnullRepo.select().where(p -> p.inetes().isEqual(row.inetes().orElse(null))).toList(c).get(0).inetes().orElse(null));
            assertArrayEquals(row.int2es().orElse(null), pgtestnullRepo.select().where(p -> p.int2es().isEqual(row.int2es().orElse(null))).toList(c).get(0).int2es().orElse(null));
            assertArrayEquals(row.int2vectores().orElse(null), pgtestnullRepo.select().where(p -> p.int2vectores().isEqual(row.int2vectores().orElse(null))).toList(c).get(0).int2vectores().orElse(null));
            assertArrayEquals(row.int4es().orElse(null), pgtestnullRepo.select().where(p -> p.int4es().isEqual(row.int4es().orElse(null))).toList(c).get(0).int4es().orElse(null));
            assertArrayEquals(row.intervales().orElse(null), pgtestnullRepo.select().where(p -> p.intervales().isEqual(row.intervales().orElse(null))).toList(c).get(0).intervales().orElse(null));
            assertArrayEquals(row.moneyes().orElse(null), pgtestnullRepo.select().where(p -> p.moneyes().isEqual(row.moneyes().orElse(null))).toList(c).get(0).moneyes().orElse(null));
            assertArrayEquals(row.mydomaines().orElse(null), pgtestnullRepo.select().where(p -> p.mydomaines().isEqual(row.mydomaines().orElse(null))).toList(c).get(0).mydomaines().orElse(null));
            assertArrayEquals(row.myenumes().orElse(null), pgtestnullRepo.select().where(p -> p.myenumes().isEqual(row.myenumes().orElse(null))).toList(c).get(0).myenumes().orElse(null));
            assertArrayEquals(row.textes().orElse(null), pgtestnullRepo.select().where(p -> p.textes().isEqual(row.textes().orElse(null))).toList(c).get(0).textes().orElse(null));
            assertArrayEquals(row.timees().orElse(null), pgtestnullRepo.select().where(p -> p.timees().isEqual(row.timees().orElse(null))).toList(c).get(0).timees().orElse(null));
            assertArrayEquals(row.timestampes().orElse(null), pgtestnullRepo.select().where(p -> p.timestampes().isEqual(row.timestampes().orElse(null))).toList(c).get(0).timestampes().orElse(null));
            assertArrayEquals(row.timestampzes().orElse(null), pgtestnullRepo.select().where(p -> p.timestampzes().isEqual(row.timestampzes().orElse(null))).toList(c).get(0).timestampzes().orElse(null));
            assertArrayEquals(row.timezes().orElse(null), pgtestnullRepo.select().where(p -> p.timezes().isEqual(row.timezes().orElse(null))).toList(c).get(0).timezes().orElse(null));
            assertArrayEquals(row.uuides().orElse(null), pgtestnullRepo.select().where(p -> p.uuides().isEqual(row.uuides().orElse(null))).toList(c).get(0).uuides().orElse(null));
        });
    }

    @Test
    public void canQueryPgtestWithDSL() {
        WithConnection.run(c -> {
            var row = pgtestRepo.insert(pgTestRow(), c);

            // Test DSL update.setValue() for all types - same as Scala version
            pgtestRepo.update().setValue(p -> p.bool(), row.bool()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.box(), row.box()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.bpchar(), row.bpchar()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.bytea(), row.bytea()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.char_(), row.char_()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.circle(), row.circle()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.date(), row.date()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.float4(), row.float4()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.float8(), row.float8()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.hstore(), row.hstore()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.inet(), row.inet()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.int2(), row.int2()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.int2vector(), row.int2vector()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.int4(), row.int4()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.int8(), row.int8()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.interval(), row.interval()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.json(), row.json()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.jsonb(), row.jsonb()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.line(), row.line()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.lseg(), row.lseg()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.money(), row.money()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.mydomain(), row.mydomain()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.myenum(), row.myenum()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.name(), row.name()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.numeric(), row.numeric()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.path(), row.path()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.point(), row.point()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.polygon(), row.polygon()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.text(), row.text()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.time(), row.time()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.timestamp(), row.timestamp()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.timestampz(), row.timestampz()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.timez(), row.timez()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.uuid(), row.uuid()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.varchar(), row.varchar()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.vector(), row.vector()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.xml(), row.xml()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);

            // Array types
            pgtestRepo.update().setValue(p -> p.boxes(), row.boxes()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.bpchares(), row.bpchares()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.chares(), row.chares()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.circlees(), row.circlees()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.datees(), row.datees()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.float4es(), row.float4es()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.float8es(), row.float8es()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.inetes(), row.inetes()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.int2es(), row.int2es()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.int2vectores(), row.int2vectores()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.int4es(), row.int4es()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.int8es(), row.int8es()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.intervales(), row.intervales()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.jsones(), row.jsones()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.jsonbes(), row.jsonbes()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.linees(), row.linees()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.lseges(), row.lseges()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.moneyes(), row.moneyes()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.mydomaines(), row.mydomaines()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.myenumes(), row.myenumes()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.namees(), row.namees()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.numerices(), row.numerices()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.pathes(), row.pathes()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.pointes(), row.pointes()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.polygones(), row.polygones()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.textes(), row.textes()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.timees(), row.timees()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.timestampes(), row.timestampes()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.timestampzes(), row.timestampzes()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.timezes(), row.timezes()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.uuides(), row.uuides()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.varchares(), row.varchares()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
            pgtestRepo.update().setValue(p -> p.xmles(), row.xmles()).where(p -> p.uuid().isEqual(row.uuid())).execute(c);
        });
    }
}

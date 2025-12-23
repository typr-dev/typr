package adventureworks

import adventureworks.JsonEquals.assertJsonEquals
import adventureworks.public.Mydomain
import adventureworks.public.Myenum
import adventureworks.public.pgtest.PgtestRepoImpl
import adventureworks.public.pgtest.PgtestRow
import adventureworks.public.pgtestnull.PgtestnullRepoImpl
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetTime
import java.util.UUID
import org.junit.Test
import org.postgresql.geometric.PGbox
import org.postgresql.geometric.PGcircle
import org.postgresql.geometric.PGline
import org.postgresql.geometric.PGlseg
import org.postgresql.geometric.PGpath
import org.postgresql.geometric.PGpoint
import org.postgresql.geometric.PGpolygon
import org.postgresql.util.PGInterval
import typr.data.Inet
import typr.data.Int2Vector
import typr.data.Json
import typr.data.Jsonb
import typr.data.Money
import typr.data.Vector
import typr.data.Xml

class ArrayTest {
    private val pgtestnullRepo = PgtestnullRepoImpl()
    private val pgtestRepo = PgtestRepoImpl()

    @Test
    fun canInsertPgtestRows() {
        WithConnection.run { c ->
            val before = ArrayTestData.pgTestRow
            val after = pgtestRepo.insert(before, c)
            assertJsonEquals(before, after)
        }
    }

    @Test
    fun canStreamPgtestRows() {
        WithConnection.run { c ->
            val before = listOf(ArrayTestData.pgTestRow)
            pgtestRepo.insertStreaming(before.iterator(), 1, c)
            val after = pgtestRepo.selectAll(c)
            assertJsonEquals(before, after)
        }
    }

    @Test
    fun canInsertNullPgtestnullRows() {
        WithConnection.run { c ->
            val before = ArrayTestData.pgtestnullRow
            val after = pgtestnullRepo.insert(before, c)
            assertJsonEquals(after, before)
        }
    }

    @Test
    fun canInsertNonNullPgtestnullRows() {
        WithConnection.run { c ->
            val before = ArrayTestData.pgtestnullRowWithValues
            val after = pgtestnullRepo.insert(before, c)
            assertJsonEquals(before, after)
        }
    }

    @Test
    fun canStreamPgtestnullRows() {
        WithConnection.run { c ->
            val before = listOf(ArrayTestData.pgtestnullRow, ArrayTestData.pgtestnullRowWithValues)
            pgtestnullRepo.insertStreaming(before.iterator(), 1, c)
            val after = pgtestnullRepo.selectAll(c)
            assertJsonEquals(before, after)
        }
    }

    @Test
    fun canQueryPgtestnullWithDSL() {
        WithConnection.run { c ->
            val row = pgtestnullRepo.insert(ArrayTestData.pgtestnullRowWithValues, c)
            assertJsonEquals(row.bool, pgtestnullRepo.select().where { it.bool().isEqual(row.bool!!) }.toList(c).first().bool)
            assertJsonEquals(row.box, pgtestnullRepo.select().where { it.box().isEqual(row.box!!) }.toList(c).first().box)
            assertJsonEquals(row.bpchar, pgtestnullRepo.select().where { it.bpchar().isEqual(row.bpchar!!) }.toList(c).first().bpchar)
            assertJsonEquals(row.bytea, pgtestnullRepo.select().where { it.bytea().isEqual(row.bytea!!) }.toList(c).first().bytea)
            assertJsonEquals(row.char, pgtestnullRepo.select().where { it.char().isEqual(row.char!!) }.toList(c).first().char)
            assertJsonEquals(row.circle, pgtestnullRepo.select().where { it.circle().isEqual(row.circle!!) }.toList(c).first().circle)
            assertJsonEquals(row.date, pgtestnullRepo.select().where { it.date().isEqual(row.date!!) }.toList(c).first().date)
            assertJsonEquals(row.float4, pgtestnullRepo.select().where { it.float4().isEqual(row.float4!!) }.toList(c).first().float4)
            assertJsonEquals(row.float8, pgtestnullRepo.select().where { it.float8().isEqual(row.float8!!) }.toList(c).first().float8)
            assertJsonEquals(row.hstore, pgtestnullRepo.select().where { it.hstore().isEqual(row.hstore!!) }.toList(c).first().hstore)
            assertJsonEquals(row.inet, pgtestnullRepo.select().where { it.inet().isEqual(row.inet!!) }.toList(c).first().inet)
            assertJsonEquals(row.int2, pgtestnullRepo.select().where { it.int2().isEqual(row.int2!!) }.toList(c).first().int2)
            assertJsonEquals(row.int2vector, pgtestnullRepo.select().where { it.int2vector().isEqual(row.int2vector!!) }.toList(c).first().int2vector)
            assertJsonEquals(row.int4, pgtestnullRepo.select().where { it.int4().isEqual(row.int4!!) }.toList(c).first().int4)
            assertJsonEquals(row.int8, pgtestnullRepo.select().where { it.int8().isEqual(row.int8!!) }.toList(c).first().int8)
            assertJsonEquals(row.interval, pgtestnullRepo.select().where { it.interval().isEqual(row.interval!!) }.toList(c).first().interval)
            // assertJsonEquals(row.json, pgtestnullRepo.select().where { it.json().isEqual(row.json!!) }.toList(c).first().json)
            assertJsonEquals(row.jsonb, pgtestnullRepo.select().where { it.jsonb().isEqual(row.jsonb!!) }.toList(c).first().jsonb)
            assertJsonEquals(row.line, pgtestnullRepo.select().where { it.line().isEqual(row.line!!) }.toList(c).first().line)
            assertJsonEquals(row.lseg, pgtestnullRepo.select().where { it.lseg().isEqual(row.lseg!!) }.toList(c).first().lseg)
            assertJsonEquals(row.money, pgtestnullRepo.select().where { it.money().isEqual(row.money!!) }.toList(c).first().money)
            assertJsonEquals(row.mydomain, pgtestnullRepo.select().where { it.mydomain().isEqual(row.mydomain!!) }.toList(c).first().mydomain)
            // assertJsonEquals(row.myenum, pgtestnullRepo.select().where { it.myenum().isEqual(row.myenum!!) }.toList(c).first().myenum)
            assertJsonEquals(row.name, pgtestnullRepo.select().where { it.name().isEqual(row.name!!) }.toList(c).first().name)
            assertJsonEquals(row.numeric, pgtestnullRepo.select().where { it.numeric().isEqual(row.numeric!!) }.toList(c).first().numeric)
            assertJsonEquals(row.path, pgtestnullRepo.select().where { it.path().isEqual(row.path!!) }.toList(c).first().path)
            // assertJsonEquals(row.point, pgtestnullRepo.select().where { it.point().isEqual(row.point!!) }.toList(c).first().point)
            // assertJsonEquals(row.polygon, pgtestnullRepo.select().where { it.polygon().isEqual(row.polygon!!) }.toList(c).first().polygon)
            assertJsonEquals(row.text, pgtestnullRepo.select().where { it.text().isEqual(row.text!!) }.toList(c).first().text)
            assertJsonEquals(row.time, pgtestnullRepo.select().where { it.time().isEqual(row.time!!) }.toList(c).first().time)
            assertJsonEquals(row.timestamp, pgtestnullRepo.select().where { it.timestamp().isEqual(row.timestamp!!) }.toList(c).first().timestamp)
            assertJsonEquals(row.timestampz, pgtestnullRepo.select().where { it.timestampz().isEqual(row.timestampz!!) }.toList(c).first().timestampz)
            assertJsonEquals(row.timez, pgtestnullRepo.select().where { it.timez().isEqual(row.timez!!) }.toList(c).first().timez)
            assertJsonEquals(row.uuid, pgtestnullRepo.select().where { it.uuid().isEqual(row.uuid!!) }.toList(c).first().uuid)
            assertJsonEquals(row.varchar, pgtestnullRepo.select().where { it.varchar().isEqual(row.varchar!!) }.toList(c).first().varchar)
            assertJsonEquals(row.vector, pgtestnullRepo.select().where { it.vector().isEqual(row.vector!!) }.toList(c).first().vector)
            // assertJsonEquals(row.xml, pgtestnullRepo.select().where { it.xml().isEqual(row.xml) }.toList(c).first().xml)
            // assertJsonEquals(row.boxes, pgtestnullRepo.select().where { it.boxes().isEqual(row.boxes) }.toList(c).first().boxes)
            // assertJsonEquals(row.bpchares, pgtestnullRepo.select().where { it.bpchares().isEqual(row.bpchares) }.toList(c).first().bpchares) // can fix with custom type
            // assertJsonEquals(row.chares, pgtestnullRepo.select().where { it.chares().isEqual(row.chares) }.toList(c).first().chares) // can fix with custom type
            // assertJsonEquals(row.circlees, pgtestnullRepo.select().where { it.circlees().isEqual(row.circlees) }.toList(c).first().circlees)
            assertJsonEquals(row.datees, pgtestnullRepo.select().where { it.datees().isEqual(row.datees!!) }.toList(c).first().datees)
            assertJsonEquals(row.float4es, pgtestnullRepo.select().where { it.float4es().isEqual(row.float4es!!) }.toList(c).first().float4es)
            assertJsonEquals(row.float8es, pgtestnullRepo.select().where { it.float8es().isEqual(row.float8es!!) }.toList(c).first().float8es)
            assertJsonEquals(row.inetes, pgtestnullRepo.select().where { it.inetes().isEqual(row.inetes!!) }.toList(c).first().inetes)
            assertJsonEquals(row.int2es, pgtestnullRepo.select().where { it.int2es().isEqual(row.int2es!!) }.toList(c).first().int2es)
            assertJsonEquals(row.int2vectores, pgtestnullRepo.select().where { it.int2vectores().isEqual(row.int2vectores!!) }.toList(c).first().int2vectores)
            assertJsonEquals(row.int4es, pgtestnullRepo.select().where { it.int4es().isEqual(row.int4es!!) }.toList(c).first().int4es)
            // assertJsonEquals(row.int8es, pgtestnullRepo.select().where { it.int8es().isEqual(row.int8es!!) }.toList(c).first().int8es)
            assertJsonEquals(row.intervales, pgtestnullRepo.select().where { it.intervales().isEqual(row.intervales!!) }.toList(c).first().intervales)
            // assertJsonEquals(row.jsones, pgtestnullRepo.select().where { it.jsones().isEqual(row.jsones!!) }.toList(c).first().jsones)
            // assertJsonEquals(row.jsonbes, pgtestnullRepo.select().where { it.jsonbes().isEqual(row.jsonbes!!) }.toList(c).first().jsonbes)
            // assertJsonEquals(row.linees, pgtestnullRepo.select().where { it.linees().isEqual(row.linees!!) }.toList(c).first().linees)
            // assertJsonEquals(row.lseges, pgtestnullRepo.select().where { it.lseges().isEqual(row.lseges!!) }.toList(c).first().lseges)
            assertJsonEquals(row.moneyes, pgtestnullRepo.select().where { it.moneyes().isEqual(row.moneyes!!) }.toList(c).first().moneyes)
            assertJsonEquals(row.mydomaines, pgtestnullRepo.select().where { it.mydomaines().isEqual(row.mydomaines!!) }.toList(c).first().mydomaines)
            assertJsonEquals(row.myenumes, pgtestnullRepo.select().where { it.myenumes().isEqual(row.myenumes!!) }.toList(c).first().myenumes)
            // assertJsonEquals(row.namees, pgtestnullRepo.select().where { it.namees().isEqual(row.namees!!) }.toList(c).first().namees)
            // assertJsonEquals(row.numerices, pgtestnullRepo.select().where { it.numerices().isEqual(row.numerices!!) }.toList(c).first().numerices)
            // assertJsonEquals(row.pathes, pgtestnullRepo.select().where { it.pathes().isEqual(row.pathes!!) }.toList(c).first().pathes)
            // assertJsonEquals(row.pointes, pgtestnullRepo.select().where { it.pointes().isEqual(row.pointes!!) }.toList(c).first().pointes)
            // assertJsonEquals(row.polygones, pgtestnullRepo.select().where { it.polygones().isEqual(row.polygones!!) }.toList(c).first().polygones)
            assertJsonEquals(row.textes, pgtestnullRepo.select().where { it.textes().isEqual(row.textes!!) }.toList(c).first().textes)
            assertJsonEquals(row.timees, pgtestnullRepo.select().where { it.timees().isEqual(row.timees!!) }.toList(c).first().timees)
            assertJsonEquals(row.timestampes, pgtestnullRepo.select().where { it.timestampes().isEqual(row.timestampes!!) }.toList(c).first().timestampes)
            assertJsonEquals(row.timestampzes, pgtestnullRepo.select().where { it.timestampzes().isEqual(row.timestampzes!!) }.toList(c).first().timestampzes)
            assertJsonEquals(row.timezes, pgtestnullRepo.select().where { it.timezes().isEqual(row.timezes!!) }.toList(c).first().timezes)
            assertJsonEquals(row.uuides, pgtestnullRepo.select().where { it.uuides().isEqual(row.uuides!!) }.toList(c).first().uuides)
            // assertJsonEquals(row.varchares, pgtestnullRepo.select().where { it.varchares().isEqual(row.varchares) }.toList(c).first().varchares)
            // assertJsonEquals(row.xmles, pgtestnullRepo.select().where { it.xmles().isEqual(row.xmles) }.toList(c).first().xmles)
        }
    }

    @Test
    fun canQueryPgtestWithDSL() {
        WithConnection.run { c ->
            val row = pgtestRepo.insert(ArrayTestData.pgTestRow, c)
            pgtestRepo.update().setValue({ it.bool() }, row.bool).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.box() }, row.box).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.bpchar() }, row.bpchar).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.bytea() }, row.bytea).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.char() }, row.char).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.circle() }, row.circle).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.date() }, row.date).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.float4() }, row.float4).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.float8() }, row.float8).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.hstore() }, row.hstore).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.inet() }, row.inet).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.int2() }, row.int2).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.int2vector() }, row.int2vector).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.int4() }, row.int4).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.int8() }, row.int8).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.interval() }, row.interval).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.json() }, row.json).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.jsonb() }, row.jsonb).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.line() }, row.line).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.lseg() }, row.lseg).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.money() }, row.money).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.mydomain() }, row.mydomain).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.myenum() }, row.myenum).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.name() }, row.name).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.numeric() }, row.numeric).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.path() }, row.path).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.point() }, row.point).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.polygon() }, row.polygon).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.text() }, row.text).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.time() }, row.time).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.timestamp() }, row.timestamp).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.timestampz() }, row.timestampz).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.timez() }, row.timez).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.uuid() }, row.uuid).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.varchar() }, row.varchar).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.vector() }, row.vector).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.xml() }, row.xml).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.boxes() }, row.boxes).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.bpchares() }, row.bpchares).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.chares() }, row.chares).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.circlees() }, row.circlees).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.datees() }, row.datees).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.float4es() }, row.float4es).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.float8es() }, row.float8es).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.inetes() }, row.inetes).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.int2es() }, row.int2es).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.int2vectores() }, row.int2vectores).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.int4es() }, row.int4es).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.int8es() }, row.int8es).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.intervales() }, row.intervales).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.jsones() }, row.jsones).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.jsonbes() }, row.jsonbes).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.linees() }, row.linees).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.lseges() }, row.lseges).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.moneyes() }, row.moneyes).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.mydomaines() }, row.mydomaines).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.myenumes() }, row.myenumes).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.namees() }, row.namees).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.numerices() }, row.numerices).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.pathes() }, row.pathes).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.pointes() }, row.pointes).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.polygones() }, row.polygones).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.textes() }, row.textes).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.timees() }, row.timees).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.timestampes() }, row.timestampes).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.timestampzes() }, row.timestampzes).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.timezes() }, row.timezes).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.uuides() }, row.uuides).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.varchares() }, row.varchares).where { it.uuid().isEqual(row.uuid) }.execute(c)
            pgtestRepo.update().setValue({ it.xmles() }, row.xmles).where { it.uuid().isEqual(row.uuid) }.execute(c)
        }
    }
}

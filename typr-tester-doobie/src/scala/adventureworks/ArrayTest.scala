package adventureworks

import adventureworks.customtypes.*
import adventureworks.public.pgtest.{PgtestRepoImpl, PgtestRow}
import adventureworks.public.pgtestnull.{PgtestnullRepoImpl, PgtestnullRow}
import adventureworks.public.{Mydomain, Myenum}
import cats.effect.IO
import doobie.{ConnectionIO, WeakAsync}
import org.scalatest.funsuite.AnyFunSuite

class ArrayTest extends AnyFunSuite with JsonEquals {
  val pgtestnullRepo: PgtestnullRepoImpl = new PgtestnullRepoImpl
  val pgtestRepo: PgtestRepoImpl = new PgtestRepoImpl

  test("can insert pgtest rows") {
    withConnection {
      val before = ArrayTestData.pgTestRow
      pgtestRepo.insert(before).map { after =>
        assertJsonEquals(before, after)
      }
    }
  }

  test("can stream pgtest rows") {
    withConnection {
      val before = List(ArrayTestData.pgTestRow)
      for {
        _ <- pgtestRepo.insertStreaming(fs2.Stream.emits(before), 1)
        after <- pgtestRepo.selectAll.compile.toList
      } yield assertJsonEquals(before, after)
    }
  }

  test("can insert null pgtestnull rows") {
    withConnection {
      val before = ArrayTestData.pgtestnullRow
      pgtestnullRepo.insert(before).map { after =>
        assertJsonEquals(before, after)
      }
    }
  }

  test("can insert non-null pgtestnull rows") {
    withConnection {
      val before = ArrayTestData.pgtestnullRowWithValues
      pgtestnullRepo.insert(before).map { after =>
        assertJsonEquals(before, after)
      }
    }
  }
  test("can stream pgtestnull rows") {
    withConnection {
      val before = List(ArrayTestData.pgtestnullRow, ArrayTestData.pgtestnullRowWithValues)
      for {
        _ <- pgtestnullRepo.insertStreaming(fs2.Stream.emits(before), 1)
        after <- pgtestnullRepo.selectAll.compile.toList
      } yield assertJsonEquals(before, after)
    }
  }

  // this test is doobie-specific
  test("can stream insert IO stream") {
    val before = List(ArrayTestData.pgtestnullRow, ArrayTestData.pgtestnullRowWithValues)
    val beforeIOStream: fs2.Stream[IO, PgtestnullRow] = fs2.Stream.emits(before).covary[IO]

    WeakAsync.liftK[IO, ConnectionIO].use { liftK =>
      val transaction = for {
        _ <- pgtestnullRepo.insertStreaming(beforeIOStream.translate(liftK), 1)
        after <- pgtestnullRepo.selectAll.compile.toList
      } yield assertJsonEquals(before, after)

      import doobie.syntax.all.*

      transaction.transact(withConnection.testXa)
    }
  }
  test("can query pgtestnull with DSL") {
    withConnection {
      for {
        row <- pgtestnullRepo.insert(ArrayTestData.pgtestnullRowWithValues)
        _ <- pgtestnullRepo.select.where(_.bool === row.bool).toList.map(x => assertJsonEquals(row.bool, x.head.bool))
        _ <- pgtestnullRepo.select.where(_.box === row.box).toList.map(x => assertJsonEquals(row.box, x.head.box))
        _ <- pgtestnullRepo.select.where(_.bpchar === row.bpchar).toList.map(x => assertJsonEquals(row.bpchar, x.head.bpchar))
        _ <- pgtestnullRepo.select.where(_.bytea === row.bytea).toList.map(x => assertJsonEquals(row.bytea, x.head.bytea))
        _ <- pgtestnullRepo.select.where(_.char === row.char).toList.map(x => assertJsonEquals(row.char, x.head.char))
        _ <- pgtestnullRepo.select.where(_.circle === row.circle).toList.map(x => assertJsonEquals(row.circle, x.head.circle))
        _ <- pgtestnullRepo.select.where(_.date === row.date).toList.map(x => assertJsonEquals(row.date, x.head.date))
        _ <- pgtestnullRepo.select.where(_.float4 === row.float4).toList.map(x => assertJsonEquals(row.float4, x.head.float4))
        _ <- pgtestnullRepo.select.where(_.float8 === row.float8).toList.map(x => assertJsonEquals(row.float8, x.head.float8))
        _ <- pgtestnullRepo.select.where(_.hstore === row.hstore).toList.map(x => assertJsonEquals(row.hstore, x.head.hstore))
        _ <- pgtestnullRepo.select.where(_.inet === row.inet).toList.map(x => assertJsonEquals(row.inet, x.head.inet))
        _ <- pgtestnullRepo.select.where(_.int2 === row.int2).toList.map(x => assertJsonEquals(row.int2, x.head.int2))
        _ <- pgtestnullRepo.select.where(_.int2vector === row.int2vector).toList.map(x => assertJsonEquals(row.int2vector, x.head.int2vector))
        _ <- pgtestnullRepo.select.where(_.int4 === row.int4).toList.map(x => assertJsonEquals(row.int4, x.head.int4))
        _ <- pgtestnullRepo.select.where(_.int8 === row.int8).toList.map(x => assertJsonEquals(row.int8, x.head.int8))
        _ <- pgtestnullRepo.select.where(_.interval === row.interval).toList.map(x => assertJsonEquals(row.interval, x.head.interval))
        //        _ <- pgtestnullRepo.select.where(_.json === row.json).toList.map(x => assertJsonEquals(row.json, x.head.json))
        _ <- pgtestnullRepo.select.where(_.jsonb === row.jsonb).toList.map(x => assertJsonEquals(row.jsonb, x.head.jsonb))
        _ <- pgtestnullRepo.select.where(_.line === row.line).toList.map(x => assertJsonEquals(row.line, x.head.line))
        _ <- pgtestnullRepo.select.where(_.lseg === row.lseg).toList.map(x => assertJsonEquals(row.lseg, x.head.lseg))
        _ <- pgtestnullRepo.select.where(_.money === row.money).toList.map(x => assertJsonEquals(row.money, x.head.money))
        _ <- pgtestnullRepo.select.where(_.mydomain === row.mydomain).toList.map(x => assertJsonEquals(row.mydomain, x.head.mydomain))
        _ <- pgtestnullRepo.select.where(_.myenum === row.myenum).toList.map(x => assertJsonEquals(row.myenum, x.head.myenum))
        _ <- pgtestnullRepo.select.where(_.name === row.name).toList.map(x => assertJsonEquals(row.name, x.head.name))
        _ <- pgtestnullRepo.select.where(_.numeric === row.numeric).toList.map(x => assertJsonEquals(row.numeric, x.head.numeric)) // only doobie
        _ <- pgtestnullRepo.select.where(_.path === row.path).toList.map(x => assertJsonEquals(row.path, x.head.path))
//        _ <- pgtestnullRepo.select.where(_.point === row.point).toList.map(x => assertJsonEquals(row.point, x.head.point))
        //        _ <- pgtestnullRepo.select.where(_.polygon === row.polygon).toList.map(x => assertJsonEquals(row.polygon, x.head.polygon))
        //        _ <- pgtestnullRepo.select.where(_.text === row.text).toList.map(x => assertJsonEquals(row.text, x.head.text)) // only doobie
        _ <- pgtestnullRepo.select.where(_.time === row.time).toList.map(x => assertJsonEquals(row.time, x.head.time))
        _ <- pgtestnullRepo.select.where(_.timestamp === row.timestamp).toList.map(x => assertJsonEquals(row.timestamp, x.head.timestamp))
        _ <- pgtestnullRepo.select.where(_.timestampz === row.timestampz).toList.map(x => assertJsonEquals(row.timestampz, x.head.timestampz))
        _ <- pgtestnullRepo.select.where(_.timez === row.timez).toList.map(x => assertJsonEquals(row.timez, x.head.timez))
        _ <- pgtestnullRepo.select.where(_.uuid === row.uuid).toList.map(x => assertJsonEquals(row.uuid, x.head.uuid))
        _ <- pgtestnullRepo.select.where(_.varchar === row.varchar).toList.map(x => assertJsonEquals(row.varchar, x.head.varchar))
        _ <- pgtestnullRepo.select.where(_.vector === row.vector).toList.map(x => assertJsonEquals(row.vector, x.head.vector))
        //        _ <- pgtestnullRepo.select.where(_.xml === row.xml).toList.map(x => assertJsonEquals(row.xml, x.head.xml))
        //        _ <- pgtestnullRepo.select.where(_.boxes === row.boxes).toList.map(x => assertJsonEquals(row.boxes, x.head.boxes))
        //        _ <- pgtestnullRepo.select.where(_.bpchares === row.bpchares).toList.map(x => assertJsonEquals(row.bpchares, x.head.bpchares))
        //        _ <- pgtestnullRepo.select.where(_.chares === row.chares).toList.map(x => assertJsonEquals(row.chares, x.head.chares))
        //        _ <- pgtestnullRepo.select.where(_.circlees === row.circlees).toList.map(x => assertJsonEquals(row.circlees, x.head.circlees))
        _ <- pgtestnullRepo.select.where(_.datees === row.datees).toList.map(x => assertJsonEquals(row.datees, x.head.datees))
        _ <- pgtestnullRepo.select.where(_.float4es === row.float4es).toList.map(x => assertJsonEquals(row.float4es, x.head.float4es))
        _ <- pgtestnullRepo.select.where(_.float8es === row.float8es).toList.map(x => assertJsonEquals(row.float8es, x.head.float8es))
        _ <- pgtestnullRepo.select.where(_.inetes === row.inetes).toList.map(x => assertJsonEquals(row.inetes, x.head.inetes))
        _ <- pgtestnullRepo.select.where(_.int2es === row.int2es).toList.map(x => assertJsonEquals(row.int2es, x.head.int2es))
        _ <- pgtestnullRepo.select.where(_.int2vectores === row.int2vectores).toList.map(x => assertJsonEquals(row.int2vectores, x.head.int2vectores))
        _ <- pgtestnullRepo.select.where(_.int4es === row.int4es).toList.map(x => assertJsonEquals(row.int4es, x.head.int4es))
        _ <- pgtestnullRepo.select.where(_.int8es === row.int8es).toList.map(x => assertJsonEquals(row.int8es, x.head.int8es))
        _ <- pgtestnullRepo.select.where(_.intervales === row.intervales).toList.map(x => assertJsonEquals(row.intervales, x.head.intervales))
        //        _ <- pgtestnullRepo.select.where(_.jsones === row.jsones).toList.map(x => assertJsonEquals(row.jsones, x.head.jsones))
        _ <- pgtestnullRepo.select.where(_.jsonbes === row.jsonbes).toList.map(x => assertJsonEquals(row.jsonbes, x.head.jsonbes))
        //        _ <- pgtestnullRepo.select.where(_.linees === row.linees).toList.map(x => assertJsonEquals(row.linees, x.head.linees))
        //        _ <- pgtestnullRepo.select.where(_.lseges === row.lseges).toList.map(x => assertJsonEquals(row.lseges, x.head.lseges))
        _ <- pgtestnullRepo.select.where(_.moneyes === row.moneyes).toList.map(x => assertJsonEquals(row.moneyes, x.head.moneyes))
//        _ <- pgtestnullRepo.select.where(_.mydomaines === row.mydomaines).toList.map(x => assertJsonEquals(row.mydomaines, x.head.mydomaines))
        _ <- pgtestnullRepo.select.where(_.myenumes === row.myenumes).toList.map(x => assertJsonEquals(row.myenumes, x.head.myenumes))
        //        _ <- pgtestnullRepo.select.where(_.namees === row.namees).toList.map(x => assertJsonEquals(row.namees, x.head.namees))
//        _ <- pgtestnullRepo.select.where(_.numerices === row.numerices).toList.map(x => assertJsonEquals(row.numerices, x.head.numerices)) // only doobie
        //        _ <- pgtestnullRepo.select.where(_.pathes === row.pathes).toList.map(x => assertJsonEquals(row.pathes, x.head.pathes))
        //        _ <- pgtestnullRepo.select.where(_.pointes === row.pointes).toList.map(x => assertJsonEquals(row.pointes, x.head.pointes))
        //        _ <- pgtestnullRepo.select.where(_.polygones === row.polygones).toList.map(x => assertJsonEquals(row.polygones, x.head.polygones))
//        _ <- pgtestnullRepo.select.where(_.textes === row.textes).toList.map(x => assertJsonEquals(row.textes, x.head.textes)) // only doobie
        _ <- pgtestnullRepo.select.where(_.timees === row.timees).toList.map(x => assertJsonEquals(row.timees, x.head.timees))
        _ <- pgtestnullRepo.select.where(_.timestampes === row.timestampes).toList.map(x => assertJsonEquals(row.timestampes, x.head.timestampes))
        _ <- pgtestnullRepo.select.where(_.timestampzes === row.timestampzes).toList.map(x => assertJsonEquals(row.timestampzes, x.head.timestampzes))
        _ <- pgtestnullRepo.select.where(_.timezes === row.timezes).toList.map(x => assertJsonEquals(row.timezes, x.head.timezes))
        _ <- pgtestnullRepo.select.where(_.uuides === row.uuides).toList.map(x => assertJsonEquals(row.uuides, x.head.uuides))
        _ <- pgtestnullRepo.select.where(_.varchares === row.varchares).toList.map(x => assertJsonEquals(row.varchares, x.head.varchares))
        //        _ <- pgtestnullRepo.select.where(_.xmles === row.xmles).toList.map(x => assertJsonEquals(row.xmles, x.head.xmles))
      } yield ()
    }
  }

  test("can update pgtest with DSL") {
    withConnection {
      for {
        row <- pgtestRepo.insert(ArrayTestData.pgTestRow)
        _ <- pgtestRepo.update.setValue(_.bool)(row.bool).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.box)(row.box).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.bpchar)(row.bpchar).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.bytea)(row.bytea).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.char)(row.char).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.circle)(row.circle).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.date)(row.date).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.float4)(row.float4).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.float8)(row.float8).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.hstore)(row.hstore).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.inet)(row.inet).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.int2)(row.int2).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.int2vector)(row.int2vector).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.int4)(row.int4).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.int8)(row.int8).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.interval)(row.interval).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.json)(row.json).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.jsonb)(row.jsonb).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.line)(row.line).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.lseg)(row.lseg).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.money)(row.money).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.mydomain)(row.mydomain).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.myenum)(row.myenum).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.name)(row.name).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.numeric)(row.numeric).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.path)(row.path).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.point)(row.point).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.polygon)(row.polygon).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.text)(row.text).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.time)(row.time).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.timestamp)(row.timestamp).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.timestampz)(row.timestampz).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.timez)(row.timez).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.uuid)(row.uuid).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.varchar)(row.varchar).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.vector)(row.vector).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.xml)(row.xml).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.boxes)(row.boxes).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.bpchares)(row.bpchares).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.chares)(row.chares).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.circlees)(row.circlees).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.datees)(row.datees).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.float4es)(row.float4es).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.float8es)(row.float8es).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.inetes)(row.inetes).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.int2es)(row.int2es).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.int2vectores)(row.int2vectores).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.int4es)(row.int4es).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.int8es)(row.int8es).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.intervales)(row.intervales).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.jsones)(row.jsones).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.jsonbes)(row.jsonbes).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.linees)(row.linees).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.lseges)(row.lseges).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.moneyes)(row.moneyes).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.mydomaines)(row.mydomaines).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.myenumes)(row.myenumes).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.namees)(row.namees).where(_.uuid === row.uuid).execute
//        _ <- pgtestRepo.update.setValue(_.numerices)(row.numerices).where(_.uuid === row.uuid).execute // only doobie
        _ <- pgtestRepo.update.setValue(_.pathes)(row.pathes).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.pointes)(row.pointes).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.polygones)(row.polygones).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.textes)(row.textes).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.timees)(row.timees).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.timestampes)(row.timestampes).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.timestampzes)(row.timestampzes).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.timezes)(row.timezes).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.uuides)(row.uuides).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.varchares)(row.varchares).where(_.uuid === row.uuid).execute
        _ <- pgtestRepo.update.setValue(_.xmles)(row.xmles).where(_.uuid === row.uuid).execute
      } yield ()
    }
  }
}

object ArrayTestData {
  val pgTestRow = new PgtestRow(
    bool = true,
    box = TypoBox(3.0, 4.0, 1.0, 2.0),
    bpchar = "abc",
    bytea = TypoBytea(Array[Byte](1, 2, 3)),
    char = "a",
    circle = TypoCircle(TypoPoint(1.0, 2.0), 3.0),
    date = TypoLocalDate.now,
    float4 = 1.0f,
    float8 = 2.45,
    hstore = TypoHStore(Map("a" -> "1", "b" -> "2")),
    inet = TypoInet("::10.2.3.4"),
    int2 = TypoShort(1),
    int2vector = TypoInt2Vector("1 2 3"),
    int4 = 4,
    int8 = Int.MaxValue.toLong + 1,
    interval = TypoInterval(1, 2, 3, 4, 5, 6.5),
    json = TypoJson("""{"a": 1}"""),
    jsonb = TypoJsonb("""{"a": 2}"""),
    line = TypoLine(3.0, 4.5, 5.5),
    lseg = TypoLineSegment(TypoPoint(6.5, 4.3), TypoPoint(1.5, 2.3)),
    money = TypoMoney(BigDecimal("22.50")),
    mydomain = Mydomain("a"),
    myenum = Myenum.c,
    name = "foo",
    numeric = BigDecimal("3.14159"),
    path = TypoPath(open = true, List(TypoPoint(6.5, 4.3), TypoPoint(8.5, 4.3))),
    point = TypoPoint(6.5, 4.3),
    polygon = TypoPolygon(List(TypoPoint(6.5, 4.3), TypoPoint(10.5, 4.3), TypoPoint(-6.5, 4.3))),
    text = "flaff",
    time = TypoLocalTime.now,
    timestamp = TypoLocalDateTime.now,
    timestampz = TypoInstant.now,
    timez = TypoOffsetTime.now,
    uuid = TypoUUID.randomUUID,
    varchar = "asd asd ",
    vector = TypoVector(Array(1.0f, 2.2f, 3.3f)),
    xml = TypoXml("<xml/>"),
    boxes = Array(TypoBox(3.0, 4.0, 1.0, 2.0)),
    bpchares = Array("abc"),
    chares = Array("a"),
    circlees = Array(TypoCircle(TypoPoint(1.0, 2.0), 3.0)),
    datees = Array(TypoLocalDate.now),
    float4es = Array(1.0f),
    float8es = Array(2.45),
    inetes = Array(TypoInet("::10.2.3.4")),
    int2es = Array(TypoShort(1)),
    int2vectores = Array(TypoInt2Vector("1 2 3")),
    int4es = Array(4),
    int8es = Array(Int.MaxValue.toLong + 1),
    intervales = Array(TypoInterval(1, 2, 3, 4, 5, 6.5)),
    jsones = Array(TypoJson("""{"a": 1}""")),
    jsonbes = Array(TypoJsonb("""{"a": 2}""")),
    linees = Array(TypoLine(3.0, 4.5, 5.5)),
    lseges = Array(TypoLineSegment(TypoPoint(6.5, 4.3), TypoPoint(1.5, 2.3))),
    moneyes = Array(TypoMoney(BigDecimal("22.50"))),
    mydomaines = Array(Mydomain("a")),
    myenumes = Array(Myenum.c),
    namees = Array("foo"),
    numerices = Array(BigDecimal("3.14159")),
    pathes = Array(TypoPath(open = true, List(TypoPoint(6.5, 4.3), TypoPoint(8.5, 4.3)))),
    pointes = Array(TypoPoint(6.5, 4.3)),
    polygones = Array(TypoPolygon(List(TypoPoint(6.5, 4.3), TypoPoint(10.5, 4.3), TypoPoint(-6.5, 4.3)))),
    textes = Array("flaff"),
    timees = Array(TypoLocalTime.now),
    timestampes = Array(TypoLocalDateTime.now),
    timestampzes = Array(TypoInstant.now),
    timezes = Array(TypoOffsetTime.now),
    uuides = Array(TypoUUID.randomUUID),
    varchares = Array("asd asd "),
    xmles = Array(TypoXml("<xml/>"))
  )

  val pgtestnullRow = new PgtestnullRow(
    bool = None,
    box = None,
    bpchar = None,
    bytea = None,
    char = None,
    circle = None,
    date = None,
    float4 = None,
    float8 = None,
    hstore = None,
    inet = None,
    int2 = None,
    int2vector = None,
    int4 = None,
    int8 = None,
    interval = None,
    json = None,
    jsonb = None,
    line = None,
    lseg = None,
    money = None,
    mydomain = None,
    myenum = None,
    name = None,
    numeric = None,
    path = None,
    point = None,
    polygon = None,
    text = None,
    time = None,
    timestamp = None,
    timestampz = None,
    timez = None,
    uuid = None,
    varchar = None,
    vector = None,
    xml = None,
    boxes = None,
    bpchares = None,
    chares = None,
    circlees = None,
    datees = None,
    float4es = None,
    float8es = None,
    inetes = None,
    int2es = None,
    int2vectores = None,
    int4es = None,
    int8es = None,
    intervales = None,
    jsones = None,
    jsonbes = None,
    linees = None,
    lseges = None,
    moneyes = None,
    mydomaines = None,
    myenumes = None,
    namees = None,
    numerices = None,
    pathes = None,
    pointes = None,
    polygones = None,
    textes = None,
    timees = None,
    timestampes = None,
    timestampzes = None,
    timezes = None,
    uuides = None,
    varchares = None,
    xmles = None
  )

  val pgtestnullRowWithValues = new PgtestnullRow(
    bool = Some(true),
    box = Some(TypoBox(3.0, 4.0, 1.0, 2.0)),
    bpchar = Some("abc"),
    bytea = Some(TypoBytea(Array[Byte](1, 2, 3))),
    char = Some("a"),
    circle = Some(TypoCircle(TypoPoint(1.0, 2.0), 3.0)),
    date = Some(TypoLocalDate.now),
    float4 = Some(1.0f),
    float8 = Some(2.45),
    hstore = Some(TypoHStore(Map("a" -> "1", "b" -> "2"))),
    inet = Some(TypoInet("::10.2.3.4")),
    int2 = Some(TypoShort(1)),
    int2vector = Some(TypoInt2Vector("1 2 3")),
    int4 = Some(4),
    int8 = Some(Int.MaxValue.toLong + 1),
    interval = Some(TypoInterval(1, 2, 3, 4, 5, 6.5)),
    json = Some(TypoJson("""{"a": 1}""")),
    jsonb = Some(TypoJsonb("""{"a": 2}""")),
    line = Some(TypoLine(3.0, 4.5, 5.5)),
    lseg = Some(TypoLineSegment(TypoPoint(6.5, 4.3), TypoPoint(1.5, 2.3))),
    money = Some(TypoMoney(BigDecimal("22.50"))),
    mydomain = Some(Mydomain("a")),
    myenum = Some(Myenum.c),
    name = Some("foo"),
    numeric = Some(BigDecimal("3.14159")),
    path = Some(TypoPath(open = true, List(TypoPoint(6.5, 4.3), TypoPoint(8.5, 4.3)))),
    point = Some(TypoPoint(6.5, 4.3)),
    polygon = Some(TypoPolygon(List(TypoPoint(6.5, 4.3), TypoPoint(10.5, 4.3), TypoPoint(-6.5, 4.3)))),
    text = Some("flaff"),
    time = Some(TypoLocalTime.now),
    timestamp = Some(TypoLocalDateTime.now),
    timestampz = Some(TypoInstant.now),
    timez = Some(TypoOffsetTime.now),
    uuid = Some(TypoUUID.randomUUID),
    varchar = Some("asd asd "),
    vector = Some(TypoVector(Array(1.0f, 2.2f, 3.3f))),
    xml = Some(TypoXml("<xml/>")),
    boxes = Some(Array(TypoBox(3.0, 4.0, 1.0, 2.0))),
    bpchares = Some(Array("abc")),
    chares = Some(Array("a")),
    circlees = Some(Array(TypoCircle(TypoPoint(1.0, 2.0), 3.0))),
    datees = Some(Array(TypoLocalDate.now)),
    float4es = Some(Array(1.0f)),
    float8es = Some(Array(2.45)),
    inetes = Some(Array(TypoInet("::10.2.3.4"))),
    int2es = Some(Array(TypoShort(1))),
    int2vectores = Some(Array(TypoInt2Vector("1 2 3"))),
    int4es = Some(Array(4)),
    int8es = Some(Array(Int.MaxValue.toLong + 1)),
    intervales = Some(Array(TypoInterval(1, 2, 3, 4, 5, 6.5))),
    jsones = Some(Array(TypoJson("""{"a": 1}"""))),
    jsonbes = Some(Array(TypoJsonb("""{"a": 2}"""))),
    linees = Some(Array(TypoLine(3.0, 4.5, 5.5))),
    lseges = Some(Array(TypoLineSegment(TypoPoint(6.5, 4.3), TypoPoint(1.5, 2.3)))),
    moneyes = Some(Array(TypoMoney(BigDecimal("22.50")))),
    mydomaines = Some(Array(Mydomain("a"))),
    myenumes = Some(Array(Myenum.c)),
    namees = Some(Array("foo")),
    numerices = Some(Array(BigDecimal("3.14159"))),
    pathes = Some(Array(TypoPath(open = true, List(TypoPoint(6.5, 4.3), TypoPoint(8.5, 4.3))))),
    pointes = Some(Array(TypoPoint(6.5, 4.3))),
    polygones = Some(Array(TypoPolygon(List(TypoPoint(6.5, 4.3), TypoPoint(10.5, 4.3), TypoPoint(-6.5, 4.3))))),
    textes = Some(Array("flaff")),
    timees = Some(Array(TypoLocalTime.now)),
    timestampes = Some(Array(TypoLocalDateTime.now)),
    timestampzes = Some(Array(TypoInstant.now)),
    timezes = Some(Array(TypoOffsetTime.now)),
    uuides = Some(Array(TypoUUID.randomUUID)),
    varchares = Some(Array("asd asd ")),
    xmles = Some(Array(TypoXml("<xml/>")))
  )
}

package adventureworks

import adventureworks.customtypes.*
import adventureworks.public.pgtest.{PgtestRepoImpl, PgtestRow}
import adventureworks.public.pgtestnull.{PgtestnullRepoImpl, PgtestnullRow}
import adventureworks.public.{Mydomain, Myenum}
import org.scalatest.funsuite.AnyFunSuite

import scala.annotation.nowarn

class ArrayTest extends AnyFunSuite with JsonEquals {
  val pgtestnullRepo: PgtestnullRepoImpl = new PgtestnullRepoImpl
  val pgtestRepo: PgtestRepoImpl = new PgtestRepoImpl

  test("can insert pgtest rows") {
    withConnection { implicit c =>
      val before = ArrayTestData.pgTestRow
      val after = pgtestRepo.insert(before)
      assertJsonEquals(before, after)
    }
  }

  test("can stream pgtest rows") {
    withConnection { implicit c =>
      val before = List(ArrayTestData.pgTestRow)
      val _ = pgtestRepo.insertStreaming(before.iterator, 1)
      val after = pgtestRepo.selectAll
      assertJsonEquals(before, after)
    }
  }

  test("can insert null pgtestnull rows") {
    withConnection { implicit c =>
      val before = ArrayTestData.pgtestnullRow
      val after = pgtestnullRepo.insert(before)
      assertJsonEquals(after, before)
    }
  }

  test("can insert non-null pgtestnull rows") {
    withConnection { implicit c =>
      val before = ArrayTestData.pgtestnullRowWithValues
      val after = pgtestnullRepo.insert(before)
      assertJsonEquals(before, after)
    }
  }

  test("can stream pgtestnull rows") {
    withConnection { implicit c =>
      val before = List(ArrayTestData.pgtestnullRow, ArrayTestData.pgtestnullRowWithValues)
      val _ = pgtestnullRepo.insertStreaming(before.iterator, 1)
      val after = pgtestnullRepo.selectAll
      assertJsonEquals(before, after)
    }
  }

  test("can query pgtestnull with DSL") {
    withConnection { implicit c =>
      val row = pgtestnullRepo.insert(ArrayTestData.pgtestnullRowWithValues)
      assertJsonEquals(row.bool, pgtestnullRepo.select.where(_.bool === row.bool).toList.head.bool): @nowarn
      assertJsonEquals(row.box, pgtestnullRepo.select.where(_.box === row.box).toList.head.box): @nowarn
      assertJsonEquals(row.bpchar, pgtestnullRepo.select.where(_.bpchar === row.bpchar).toList.head.bpchar): @nowarn
      assertJsonEquals(row.bytea, pgtestnullRepo.select.where(_.bytea === row.bytea).toList.head.bytea): @nowarn
      assertJsonEquals(row.char, pgtestnullRepo.select.where(_.char === row.char).toList.head.char): @nowarn
      assertJsonEquals(row.circle, pgtestnullRepo.select.where(_.circle === row.circle).toList.head.circle): @nowarn
      assertJsonEquals(row.date, pgtestnullRepo.select.where(_.date === row.date).toList.head.date): @nowarn
      assertJsonEquals(row.float4, pgtestnullRepo.select.where(_.float4 === row.float4).toList.head.float4): @nowarn
      assertJsonEquals(row.float8, pgtestnullRepo.select.where(_.float8 === row.float8).toList.head.float8): @nowarn
      assertJsonEquals(row.hstore, pgtestnullRepo.select.where(_.hstore === row.hstore).toList.head.hstore): @nowarn
      assertJsonEquals(row.inet, pgtestnullRepo.select.where(_.inet === row.inet).toList.head.inet): @nowarn
      assertJsonEquals(row.int2, pgtestnullRepo.select.where(_.int2 === row.int2).toList.head.int2): @nowarn
      assertJsonEquals(row.int2vector, pgtestnullRepo.select.where(_.int2vector === row.int2vector).toList.head.int2vector): @nowarn
      assertJsonEquals(row.int4, pgtestnullRepo.select.where(_.int4 === row.int4).toList.head.int4): @nowarn
      assertJsonEquals(row.int8, pgtestnullRepo.select.where(_.int8 === row.int8).toList.head.int8): @nowarn
      assertJsonEquals(row.interval, pgtestnullRepo.select.where(_.interval === row.interval).toList.head.interval): @nowarn
//      assertJsonEquals(row.json, pgtestnullRepo.select.where(_.json === row.json).toList.head.json): @nowarn
      assertJsonEquals(row.jsonb, pgtestnullRepo.select.where(_.jsonb === row.jsonb).toList.head.jsonb): @nowarn
      assertJsonEquals(row.line, pgtestnullRepo.select.where(_.line === row.line).toList.head.line): @nowarn
      assertJsonEquals(row.lseg, pgtestnullRepo.select.where(_.lseg === row.lseg).toList.head.lseg): @nowarn
      assertJsonEquals(row.money, pgtestnullRepo.select.where(_.money === row.money).toList.head.money): @nowarn
      assertJsonEquals(row.mydomain, pgtestnullRepo.select.where(_.mydomain === row.mydomain).toList.head.mydomain): @nowarn
//      assertJsonEquals(row.myenum, pgtestnullRepo.select.where(_.myenum === row.myenum).toList.head.myenum): @nowarn
      assertJsonEquals(row.name, pgtestnullRepo.select.where(_.name === row.name).toList.head.name): @nowarn
      assertJsonEquals(row.numeric, pgtestnullRepo.select.where(_.numeric === row.numeric).toList.head.numeric): @nowarn
      assertJsonEquals(row.path, pgtestnullRepo.select.where(_.path === row.path).toList.head.path): @nowarn
//      assertJsonEquals(row.point, pgtestnullRepo.select.where(_.point === row.point).toList.head.point): @nowarn
//      assertJsonEquals(row.polygon, pgtestnullRepo.select.where(_.polygon === row.polygon).toList.head.polygon): @nowarn
      assertJsonEquals(row.text, pgtestnullRepo.select.where(_.text === row.text).toList.head.text): @nowarn
      assertJsonEquals(row.time, pgtestnullRepo.select.where(_.time === row.time).toList.head.time): @nowarn
      assertJsonEquals(row.timestamp, pgtestnullRepo.select.where(_.timestamp === row.timestamp).toList.head.timestamp): @nowarn
      assertJsonEquals(row.timestampz, pgtestnullRepo.select.where(_.timestampz === row.timestampz).toList.head.timestampz): @nowarn
      assertJsonEquals(row.timez, pgtestnullRepo.select.where(_.timez === row.timez).toList.head.timez): @nowarn
      assertJsonEquals(row.uuid, pgtestnullRepo.select.where(_.uuid === row.uuid).toList.head.uuid): @nowarn
      assertJsonEquals(row.varchar, pgtestnullRepo.select.where(_.varchar === row.varchar).toList.head.varchar): @nowarn
      assertJsonEquals(row.vector, pgtestnullRepo.select.where(_.vector === row.vector).toList.head.vector): @nowarn
//      assertJsonEquals(row.xml, pgtestnullRepo.select.where(_.xml === row.xml).toList.head.xml): @nowarn
//      assertJsonEquals(row.boxes, pgtestnullRepo.select.where(_.boxes === row.boxes).toList.head.boxes): @nowarn
//      assertJsonEquals(row.bpchares, pgtestnullRepo.select.where(_.bpchares === row.bpchares).toList.head.bpchares) // can fix with custom type: @nowarn
//      assertJsonEquals(row.chares, pgtestnullRepo.select.where(_.chares === row.chares).toList.head.chares) // can fix with custom type: @nowarn
//      assertJsonEquals(row.circlees, pgtestnullRepo.select.where(_.circlees === row.circlees).toList.head.circlees): @nowarn
      assertJsonEquals(row.datees, pgtestnullRepo.select.where(_.datees === row.datees).toList.head.datees): @nowarn
      assertJsonEquals(row.float4es, pgtestnullRepo.select.where(_.float4es === row.float4es).toList.head.float4es): @nowarn
      assertJsonEquals(row.float8es, pgtestnullRepo.select.where(_.float8es === row.float8es).toList.head.float8es): @nowarn
      assertJsonEquals(row.inetes, pgtestnullRepo.select.where(_.inetes === row.inetes).toList.head.inetes): @nowarn
      assertJsonEquals(row.int2es, pgtestnullRepo.select.where(_.int2es === row.int2es).toList.head.int2es): @nowarn
      assertJsonEquals(row.int2vectores, pgtestnullRepo.select.where(_.int2vectores === row.int2vectores).toList.head.int2vectores): @nowarn
      assertJsonEquals(row.int4es, pgtestnullRepo.select.where(_.int4es === row.int4es).toList.head.int4es): @nowarn
//      assertJsonEquals(row.int8es, pgtestnullRepo.select.where(_.int8es === row.int8es).toList.head.int8es): @nowarn
      assertJsonEquals(row.intervales, pgtestnullRepo.select.where(_.intervales === row.intervales).toList.head.intervales): @nowarn
//      assertJsonEquals(row.jsones, pgtestnullRepo.select.where(_.jsones === row.jsones).toList.head.jsones): @nowarn
//      assertJsonEquals(row.jsonbes, pgtestnullRepo.select.where(_.jsonbes === row.jsonbes).toList.head.jsonbes): @nowarn
//      assertJsonEquals(row.linees, pgtestnullRepo.select.where(_.linees === row.linees).toList.head.linees): @nowarn
//      assertJsonEquals(row.lseges, pgtestnullRepo.select.where(_.lseges === row.lseges).toList.head.lseges): @nowarn
      assertJsonEquals(row.moneyes, pgtestnullRepo.select.where(_.moneyes === row.moneyes).toList.head.moneyes): @nowarn
      assertJsonEquals(row.mydomaines, pgtestnullRepo.select.where(_.mydomaines === row.mydomaines).toList.head.mydomaines): @nowarn
      assertJsonEquals(row.myenumes, pgtestnullRepo.select.where(_.myenumes === row.myenumes).toList.head.myenumes): @nowarn
//      assertJsonEquals(row.namees, pgtestnullRepo.select.where(_.namees === row.namees).toList.head.namees): @nowarn
//      assertJsonEquals(row.numerices, pgtestnullRepo.select.where(_.numerices === row.numerices).toList.head.numerices): @nowarn
//      assertJsonEquals(row.pathes, pgtestnullRepo.select.where(_.pathes === row.pathes).toList.head.pathes): @nowarn
//      assertJsonEquals(row.pointes, pgtestnullRepo.select.where(_.pointes === row.pointes).toList.head.pointes): @nowarn
//      assertJsonEquals(row.polygones, pgtestnullRepo.select.where(_.polygones === row.polygones).toList.head.polygones): @nowarn
      assertJsonEquals(row.textes, pgtestnullRepo.select.where(_.textes === row.textes).toList.head.textes): @nowarn
      assertJsonEquals(row.timees, pgtestnullRepo.select.where(_.timees === row.timees).toList.head.timees): @nowarn
      assertJsonEquals(row.timestampes, pgtestnullRepo.select.where(_.timestampes === row.timestampes).toList.head.timestampes): @nowarn
      assertJsonEquals(row.timestampzes, pgtestnullRepo.select.where(_.timestampzes === row.timestampzes).toList.head.timestampzes): @nowarn
      assertJsonEquals(row.timezes, pgtestnullRepo.select.where(_.timezes === row.timezes).toList.head.timezes): @nowarn
      assertJsonEquals(row.uuides, pgtestnullRepo.select.where(_.uuides === row.uuides).toList.head.uuides)
//      assertJsonEquals(row.varchares, pgtestnullRepo.select.where(_.varchares === row.varchares).toList.head.varchares)
//      assertJsonEquals(row.xmles, pgtestnullRepo.select.where(_.xmles === row.xmles).toList.head.xmles): @nowarn
    }
  }

  test("can query pgtest with DSL") {
    withConnection { implicit c =>
      val row = pgtestRepo.insert(ArrayTestData.pgTestRow)
      pgtestRepo.update.setValue(_.bool)(row.bool).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.box)(row.box).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.bpchar)(row.bpchar).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.bytea)(row.bytea).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.char)(row.char).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.circle)(row.circle).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.date)(row.date).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.float4)(row.float4).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.float8)(row.float8).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.hstore)(row.hstore).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.inet)(row.inet).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.int2)(row.int2).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.int2vector)(row.int2vector).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.int4)(row.int4).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.int8)(row.int8).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.interval)(row.interval).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.json)(row.json).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.jsonb)(row.jsonb).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.line)(row.line).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.lseg)(row.lseg).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.money)(row.money).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.mydomain)(row.mydomain).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.myenum)(row.myenum).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.name)(row.name).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.numeric)(row.numeric).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.path)(row.path).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.point)(row.point).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.polygon)(row.polygon).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.text)(row.text).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.time)(row.time).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.timestamp)(row.timestamp).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.timestampz)(row.timestampz).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.timez)(row.timez).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.uuid)(row.uuid).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.varchar)(row.varchar).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.vector)(row.vector).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.xml)(row.xml).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.boxes)(row.boxes).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.bpchares)(row.bpchares).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.chares)(row.chares).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.circlees)(row.circlees).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.datees)(row.datees).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.float4es)(row.float4es).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.float8es)(row.float8es).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.inetes)(row.inetes).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.int2es)(row.int2es).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.int2vectores)(row.int2vectores).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.int4es)(row.int4es).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.int8es)(row.int8es).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.intervales)(row.intervales).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.jsones)(row.jsones).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.jsonbes)(row.jsonbes).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.linees)(row.linees).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.lseges)(row.lseges).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.moneyes)(row.moneyes).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.mydomaines)(row.mydomaines).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.myenumes)(row.myenumes).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.namees)(row.namees).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.numerices)(row.numerices).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.pathes)(row.pathes).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.pointes)(row.pointes).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.polygones)(row.polygones).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.textes)(row.textes).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.timees)(row.timees).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.timestampes)(row.timestampes).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.timestampzes)(row.timestampzes).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.timezes)(row.timezes).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.uuides)(row.uuides).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.varchares)(row.varchares).where(_.uuid === row.uuid).execute(): @nowarn
      pgtestRepo.update.setValue(_.xmles)(row.xmles).where(_.uuid === row.uuid).execute()
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
    myenumes = Array(Myenum.c),
    mydomaines = Array(Mydomain("a")),
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

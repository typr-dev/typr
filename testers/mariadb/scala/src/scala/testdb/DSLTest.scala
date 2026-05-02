package testdb

import dev.typr.foundations.data.{Json, Uint1, Uint2, Uint4, Uint8}
import dev.typr.foundations.data.maria.{Inet4, Inet6}
import dev.typr.foundations.Bijection
import org.scalatest.funsuite.AnyFunSuite
import testdb.mariatest.*
import testdb.mariatest_identity.*

import java.math.BigInteger
import java.time.{LocalDate, LocalDateTime, LocalTime, Year}

class DSLTest extends AnyFunSuite {
  val mariatestRepo: MariatestRepoImpl = new MariatestRepoImpl
  val identityRepo: MariatestIdentityRepoImpl = new MariatestIdentityRepoImpl

  def createSampleRow(id: Int): MariatestRow = MariatestRow(
    tinyintCol = 127.toByte,
    smallintCol = 32767.toShort,
    mediumintCol = 8388607,
    intCol = MariatestId(id),
    bigintCol = 9223372036854775807L,
    tinyintUCol = Uint1.of(255),
    smallintUCol = Uint2.of(65535),
    mediumintUCol = Uint4.of(16777215L),
    intUCol = Uint4.of(4294967295L),
    bigintUCol = Uint8.of(new BigInteger("18446744073709551615")),
    decimalCol = BigDecimal("12345.67"),
    numericCol = BigDecimal("9876.5432"),
    floatCol = 3.14f,
    doubleCol = 2.718281828,
    boolCol = true,
    bitCol = Array(0xff.toByte),
    bit1Col = Array(0x01.toByte),
    charCol = "char_val  ",
    varcharCol = "varchar_value",
    tinytextCol = "tinytext",
    textCol = "text content",
    mediumtextCol = "mediumtext content",
    longtextCol = "longtext content",
    binaryCol = Array(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
    varbinaryCol = Array(1, 2, 3),
    tinyblobCol = Array(4, 5, 6),
    blobCol = Array(7, 8, 9),
    mediumblobCol = Array(10, 11, 12),
    longblobCol = Array(13, 14, 15),
    dateCol = LocalDate.of(2025, 1, 15),
    timeCol = LocalTime.of(14, 30, 45),
    timeFspCol = LocalTime.of(14, 30, 45, 123456000),
    datetimeCol = LocalDateTime.of(2025, 1, 15, 14, 30, 45),
    datetimeFspCol = LocalDateTime.of(2025, 1, 15, 14, 30, 45, 123456000),
    timestampCol = LocalDateTime.now(),
    timestampFspCol = LocalDateTime.now(),
    yearCol = Year.of(2025),
    setCol = XYZSet.fromString("x,y"),
    jsonCol = Json("{\"key\": \"value\"}"),
    inet4Col = new Inet4("192.168.1.1"),
    inet6Col = new Inet6("::1")
  )

  test("selectWithWhere") {
    withConnection {
      val row1 = mariatestRepo.insert(createSampleRow(1001).copy(varcharCol = "test_varchar_dsl"))

      val results = mariatestRepo.select
        .where(m => m.intCol.isEqual(row1.intCol))
        .where(m => m.varcharCol.isEqual("test_varchar_dsl"))
        .toList

      val _ = assert(results.size == 1)
      val _ = assert(results.head.intCol == row1.intCol)
      assert(results.head.varcharCol == "test_varchar_dsl")
    }
  }

  test("selectWithOrdering") {
    withConnection {
      val _ = identityRepo.insert(MariatestIdentityRowUnsaved("DSL_Zulu"))
      val _ = identityRepo.insert(MariatestIdentityRowUnsaved("DSL_Alpha"))
      val _ = identityRepo.insert(MariatestIdentityRowUnsaved("DSL_Mike"))

      val results = identityRepo.select
        .where(m => m.name.isEqual("DSL_Zulu").or(m.name.isEqual("DSL_Alpha")).or(m.name.isEqual("DSL_Mike")))
        .orderBy(m => m.name.asc)
        .toList

      val _ = assert(results.size >= 3)
      val sorted = results.filter(r => r.name.startsWith("DSL_"))
      assert(sorted.head.name == "DSL_Alpha")
    }
  }

  test("selectWithOrderByDesc") {
    withConnection {
      val _ = identityRepo.insert(MariatestIdentityRowUnsaved("DSLDescA"))
      val _ = identityRepo.insert(MariatestIdentityRowUnsaved("DSLDescB"))
      val _ = identityRepo.insert(MariatestIdentityRowUnsaved("DSLDescC"))

      val results = identityRepo.select
        .where(m => m.name.like("DSLDesc%", Bijection.asString()))
        .orderBy(m => m.name.desc)
        .toList

      val _ = assert(results.size == 3)
      val _ = assert(results.head.name == "DSLDescC")
      val _ = assert(results(1).name == "DSLDescB")
      assert(results(2).name == "DSLDescA")
    }
  }

  test("selectWithLimit") {
    withConnection {
      for (i <- 0 until 10) {
        val _ = identityRepo.insert(MariatestIdentityRowUnsaved(s"Limit$i"))
      }

      val results = identityRepo.select
        .where(m => m.name.like("Limit%", Bijection.asString()))
        .limit(3)
        .toList

      assert(results.size == 3)
    }
  }

  test("selectWithOffset") {
    withConnection {
      val _ = identityRepo.insert(MariatestIdentityRowUnsaved("OffsetA"))
      val _ = identityRepo.insert(MariatestIdentityRowUnsaved("OffsetB"))
      val _ = identityRepo.insert(MariatestIdentityRowUnsaved("OffsetC"))
      val _ = identityRepo.insert(MariatestIdentityRowUnsaved("OffsetD"))

      val results = identityRepo.select
        .where(m => m.name.like("Offset%", Bijection.asString()))
        .orderBy(m => m.name.asc)
        .offset(2)
        .limit(10)
        .toList

      val _ = assert(results.size == 2)
      val _ = assert(results.head.name == "OffsetC")
      assert(results(1).name == "OffsetD")
    }
  }

  test("selectWithCount") {
    withConnection {
      val _ = identityRepo.insert(MariatestIdentityRowUnsaved("CountA"))
      val _ = identityRepo.insert(MariatestIdentityRowUnsaved("CountB"))
      val _ = identityRepo.insert(MariatestIdentityRowUnsaved("CountC"))

      val count = identityRepo.select
        .where(m => m.name.like("Count%", Bijection.asString()))
        .count

      assert(count == 3)
    }
  }

  test("selectWithGreaterThan") {
    withConnection {
      val row1 = identityRepo.insert(MariatestIdentityRowUnsaved("GT1"))
      val _ = identityRepo.insert(MariatestIdentityRowUnsaved("GT2"))
      val _ = identityRepo.insert(MariatestIdentityRowUnsaved("GT3"))

      val results = identityRepo.select
        .where(m => m.id.greaterThan(row1.id))
        .where(m => m.name.like("GT%", Bijection.asString()))
        .toList

      assert(results.size == 2)
    }
  }

  test("selectWithLike") {
    withConnection {
      val _ = identityRepo.insert(MariatestIdentityRowUnsaved("LikeTest_ABC"))
      val _ = identityRepo.insert(MariatestIdentityRowUnsaved("LikeTest_XYZ"))
      val _ = identityRepo.insert(MariatestIdentityRowUnsaved("OtherName"))

      val results = identityRepo.select
        .where(m => m.name.like("LikeTest%", Bijection.asString()))
        .toList

      assert(results.size == 2)
    }
  }

  test("selectWithIn") {
    withConnection {
      val row1 = identityRepo.insert(MariatestIdentityRowUnsaved("InTest1"))
      val _ = identityRepo.insert(MariatestIdentityRowUnsaved("InTest2"))
      val row3 = identityRepo.insert(MariatestIdentityRowUnsaved("InTest3"))

      val results = identityRepo.select
        .where(m => m.id.in(row1.id, row3.id))
        .toList

      assert(results.size == 2)
    }
  }

  test("selectWithProjection") {
    withConnection {
      val row = identityRepo.insert(MariatestIdentityRowUnsaved("ProjectionTest"))

      val results = identityRepo.select
        .where(m => m.id.isEqual(row.id))
        .map(m => m.name.tupleWith(m.id))
        .toList

      val _ = assert(results.size == 1)
      val _ = assert(results.head._1() == "ProjectionTest")
      assert(results.head._2() == row.id)
    }
  }

  test("deleteWithDSL") {
    withConnection {
      val row = identityRepo.insert(MariatestIdentityRowUnsaved("ToDeleteDSL"))

      val beforeCount = identityRepo.select
        .where(m => m.id.isEqual(row.id))
        .count
      val _ = assert(beforeCount == 1)

      val _ = identityRepo.deleteById(row.id)

      val afterCount = identityRepo.select
        .where(m => m.id.isEqual(row.id))
        .count
      assert(afterCount == 0)
    }
  }
}

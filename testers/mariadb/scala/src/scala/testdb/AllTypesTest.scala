package testdb

import dev.typr.foundations.data.{Json, Uint1, Uint2, Uint4, Uint8}
import dev.typr.foundations.data.maria.{Inet4, Inet6}
import org.scalatest.funsuite.AnyFunSuite
import testdb.mariatest.*
import testdb.mariatestnull.*
import testdb.customtypes.Defaulted

import java.math.BigInteger
import java.time.{LocalDate, LocalDateTime, LocalTime, Year}

class AllTypesTest extends AnyFunSuite {
  private val mariatestRepo: MariatestRepoImpl = new MariatestRepoImpl
  private val mariatestnullRepo: MariatestnullRepoImpl = new MariatestnullRepoImpl

  private def createSampleRow(id: Int): MariatestRow = MariatestRow(
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

  test("insertAndSelectAllTypes") {
    withConnection {
      val row = createSampleRow(1)
      val inserted = mariatestRepo.insert(row)

      val _ = assert(inserted != null)
      val _ = assert(inserted.intCol == row.intCol)
      val _ = assert(inserted.tinyintCol == row.tinyintCol)
      val _ = assert(inserted.varcharCol == row.varcharCol)
      val _ = assert(inserted.dateCol == row.dateCol)
      val _ = assert(inserted.yearCol == row.yearCol)
      val _ = assert(inserted.inet4Col == row.inet4Col)
      val _ = assert(inserted.inet6Col == row.inet6Col)

      val found = mariatestRepo.selectById(inserted.intCol)
      val _ = assert(found.isDefined)
      assert(found.get.intCol == inserted.intCol)
    }
  }

  test("updateAllTypes") {
    withConnection {
      val row = createSampleRow(2)
      val inserted = mariatestRepo.insert(row)

      val updated = inserted.copy(
        varcharCol = "updated_varchar",
        decimalCol = BigDecimal("999.99"),
        boolCol = false
      )

      val wasUpdated = mariatestRepo.update(updated)
      val _ = assert(wasUpdated)

      val found = mariatestRepo.selectById(inserted.intCol).get
      val _ = assert(found.varcharCol == "updated_varchar")
      val _ = assert(found.decimalCol == BigDecimal("999.99"))
      assert(found.boolCol == false)
    }
  }

  test("deleteAllTypes") {
    withConnection {
      val row = createSampleRow(3)
      val inserted = mariatestRepo.insert(row)

      val deleted = mariatestRepo.deleteById(inserted.intCol)
      val _ = assert(deleted)

      val found = mariatestRepo.selectById(inserted.intCol)
      assert(found.isEmpty)
    }
  }

  test("selectAll") {
    withConnection {
      val _ = mariatestRepo.insert(createSampleRow(4))
      val _ = mariatestRepo.insert(createSampleRow(5))

      val all = mariatestRepo.selectAll
      assert(all.size >= 2)
    }
  }

  // ==================== Nullable Types Tests ====================

  test("insertNullableWithAllNulls") {
    withConnection {
      // Use short constructor - all defaults to None/UseDefault
      val unsaved = MariatestnullRowUnsaved()
      val inserted = mariatestnullRepo.insert(unsaved)

      val _ = assert(inserted != null)
      val _ = assert(inserted.tinyintCol.isEmpty)
      val _ = assert(inserted.smallintCol.isEmpty)
      val _ = assert(inserted.varcharCol.isEmpty)
      val _ = assert(inserted.dateCol.isEmpty)
      val _ = assert(inserted.inet4Col.isEmpty)
      assert(inserted.inet6Col.isEmpty)
    }
  }

  test("insertNullableWithValues") {
    withConnection {
      // Explicitly test ALL 42 nullable columns with real values - use full constructor
      val unsaved = MariatestnullRowUnsaved(
        tinyintCol = Defaulted.Provided(Some(42.toByte)),
        smallintCol = Defaulted.Provided(Some(1000.toShort)),
        mediumintCol = Defaulted.Provided(Some(50000)),
        intCol = Defaulted.Provided(Some(100000)),
        bigintCol = Defaulted.Provided(Some(1234567890L)),
        tinyintUCol = Defaulted.Provided(Some(Uint1.of(200))),
        smallintUCol = Defaulted.Provided(Some(Uint2.of(40000))),
        mediumintUCol = Defaulted.Provided(Some(Uint4.of(8000000L))),
        intUCol = Defaulted.Provided(Some(Uint4.of(3000000000L))),
        bigintUCol = Defaulted.Provided(Some(Uint8.of(new BigInteger("12345678901234567890")))),
        decimalCol = Defaulted.Provided(Some(BigDecimal("123.45"))),
        numericCol = Defaulted.Provided(Some(BigDecimal("678.90"))),
        floatCol = Defaulted.Provided(Some(1.5f)),
        doubleCol = Defaulted.Provided(Some(2.5)),
        boolCol = Defaulted.Provided(Some(true)),
        bitCol = Defaulted.Provided(Some(Array(0xab.toByte))),
        bit1Col = Defaulted.Provided(Some(Array(0x01.toByte))),
        charCol = Defaulted.Provided(Some("char      ")),
        varcharCol = Defaulted.Provided(Some("varchar")),
        tinytextCol = Defaulted.Provided(Some("tinytext")),
        textCol = Defaulted.Provided(Some("text")),
        mediumtextCol = Defaulted.Provided(Some("mediumtext")),
        longtextCol = Defaulted.Provided(Some("longtext")),
        binaryCol = Defaulted.Provided(Some(Array[Byte](1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16))),
        varbinaryCol = Defaulted.Provided(Some(Array[Byte](1, 2, 3))),
        tinyblobCol = Defaulted.Provided(Some(Array[Byte](4, 5, 6))),
        blobCol = Defaulted.Provided(Some(Array[Byte](7, 8, 9))),
        mediumblobCol = Defaulted.Provided(Some(Array[Byte](10, 11, 12))),
        longblobCol = Defaulted.Provided(Some(Array[Byte](13, 14, 15))),
        dateCol = Defaulted.Provided(Some(LocalDate.of(2025, 6, 15))),
        timeCol = Defaulted.Provided(Some(LocalTime.of(10, 30))),
        timeFspCol = Defaulted.Provided(Some(LocalTime.of(10, 30, 45, 500000000))),
        datetimeCol = Defaulted.Provided(Some(LocalDateTime.of(2025, 6, 15, 10, 30))),
        datetimeFspCol = Defaulted.Provided(Some(LocalDateTime.of(2025, 6, 15, 10, 30, 45, 500000000))),
        timestampCol = Defaulted.Provided(Some(LocalDateTime.now())),
        timestampFspCol = Defaulted.Provided(Some(LocalDateTime.now())),
        yearCol = Defaulted.Provided(Some(Year.of(2024))),
        setCol = Defaulted.Provided(Some(XYZSet.fromString("y,z"))),
        jsonCol = Defaulted.Provided(Some(Json("{\"test\": true}"))),
        inet4Col = Defaulted.Provided(Some(new Inet4("10.0.0.1"))),
        inet6Col = Defaulted.Provided(Some(new Inet6("fe80::1")))
      )

      val inserted = mariatestnullRepo.insert(unsaved)

      val _ = assert(inserted != null)
      val _ = assert(inserted.tinyintCol == Some(42.toByte))
      val _ = assert(inserted.smallintCol == Some(1000.toShort))
      val _ = assert(inserted.decimalCol == Some(BigDecimal("123.45")))
      val _ = assert(inserted.boolCol == Some(true))
      val _ = assert(inserted.varcharCol == Some("varchar"))
      val _ = assert(inserted.dateCol == Some(LocalDate.of(2025, 6, 15)))
      val _ = assert(inserted.yearCol == Some(Year.of(2024)))
      assert(inserted.inet4Col == Some(new Inet4("10.0.0.1")))
    }
  }

  // ==================== Individual Type Tests ====================

  test("integerTypes") {
    withConnection {
      val row = createSampleRow(10)
      val inserted = mariatestRepo.insert(row)

      val _ = assert(inserted.tinyintCol == 127.toByte)
      val _ = assert(inserted.smallintCol == 32767.toShort)
      val _ = assert(inserted.mediumintCol == 8388607)
      assert(inserted.bigintCol == 9223372036854775807L)
    }
  }

  test("unsignedTypes") {
    withConnection {
      val row = createSampleRow(11)
      val inserted = mariatestRepo.insert(row)

      val _ = assert(inserted.tinyintUCol == Uint1.of(255))
      val _ = assert(inserted.smallintUCol == Uint2.of(65535))
      val _ = assert(inserted.mediumintUCol == Uint4.of(16777215L))
      val _ = assert(inserted.intUCol == Uint4.of(4294967295L))
      assert(inserted.bigintUCol == Uint8.of(new BigInteger("18446744073709551615")))
    }
  }

  test("decimalTypes") {
    withConnection {
      val row = createSampleRow(12)
      val inserted = mariatestRepo.insert(row)

      val _ = assert(inserted.decimalCol == BigDecimal("12345.67"))
      assert(inserted.numericCol == BigDecimal("9876.5432"))
    }
  }

  test("dateTimeTypes") {
    withConnection {
      val row = createSampleRow(13)
      val inserted = mariatestRepo.insert(row)

      val _ = assert(inserted.dateCol == LocalDate.of(2025, 1, 15))
      val _ = assert(inserted.timeCol == LocalTime.of(14, 30, 45))
      val _ = assert(inserted.yearCol == Year.of(2025))
      assert(inserted.timestampCol != null)
    }
  }

  test("setType") {
    withConnection {
      val row1 = createSampleRow(20).copy(setCol = XYZSet.of(List(XYZSetMember.x)))
      val inserted1 = mariatestRepo.insert(row1)
      val _ = assert(inserted1.setCol == XYZSet.of(List(XYZSetMember.x)))

      val row2 = createSampleRow(21).copy(setCol = XYZSet.fromString("x,y,z"))
      val inserted2 = mariatestRepo.insert(row2)
      assert(inserted2.setCol == XYZSet.of(List(XYZSetMember.x, XYZSetMember.y, XYZSetMember.z)))
    }
  }

  test("inetTypes") {
    withConnection {
      val row = createSampleRow(30).copy(
        inet4Col = new Inet4("192.168.0.1"),
        inet6Col = new Inet6("2001:db8::1")
      )
      val inserted = mariatestRepo.insert(row)

      val _ = assert(inserted.inet4Col == new Inet4("192.168.0.1"))
      assert(inserted.inet6Col == new Inet6("2001:db8::1"))
    }
  }

  test("jsonType") {
    withConnection {
      val jsonValue = Json("{\"name\": \"test\", \"values\": [1, 2, 3]}")
      val row = createSampleRow(40).copy(jsonCol = jsonValue)
      val inserted = mariatestRepo.insert(row)

      val _ = assert(inserted.jsonCol != null)
      assert(inserted.jsonCol.value.contains("name"))
    }
  }
}

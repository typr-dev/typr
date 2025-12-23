package adventureworks

import adventureworks.customtypes.{TypoLocalDateTime, TypoUUID}
import adventureworks.person.businessentity.*
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.Assertion
import org.scalatest.funsuite.AnyFunSuite

import java.time.LocalDateTime
import scala.annotation.nowarn

class SeekDbTest extends AnyFunSuite with TypeCheckedTripleEquals {
  def testUniformSeek(businessentityRepo: BusinessentityRepo): Assertion = {

    val limit = 3
    val now = LocalDateTime.of(2021, 1, 1, 0, 0)
    val rows = List.range(0, limit * 2).map { i =>
      // ensure some duplicate values
      val time = TypoLocalDateTime(now.minusDays(i % limit.toLong))
      BusinessentityRow(BusinessentityId.apply(i), TypoUUID.randomUUID, time)
    }

    // same as sql below
    val List(group1, group2) = rows.sortBy(x => (x.modifieddate.value, x.businessentityid.value)).grouped(limit).toList: @unchecked

    withConnection { implicit c =>
      // batch insert some rows
      businessentityRepo.insertStreaming(rows.iterator): @nowarn
      val rows1 = businessentityRepo.select
        .maybeSeek(_.modifieddate.asc)(None)
        .maybeSeek(_.businessentityid.asc)(None)
        .maybeSeek(_.rowguid.asc)(None)
        .limit(limit)
        .toList
      assert(rows1 == group1): @nowarn
      val rows2 = businessentityRepo.select
        .maybeSeek(_.modifieddate.asc)(Some(rows1.last.modifieddate))
        .maybeSeek(_.businessentityid.asc)(Some(rows1.last.businessentityid))
        .maybeSeek(_.rowguid.asc)(Some(rows1.last.rowguid))
        .limit(limit)
        .toList
      assert(rows2 == group2)
    }
  }

  test("uniform in-memory") {
    testUniformSeek(new BusinessentityRepoMock(_.toRow(???, ???, ???)))
  }

  test("uniform pg") {
    testUniformSeek(new BusinessentityRepoImpl)
  }

  def testNonUniformSeek(businessentityRepo: BusinessentityRepo): Assertion = {

    val limit = 3
    val now = LocalDateTime.of(2021, 1, 1, 0, 0)
    val rows = List.range(0, limit * 2).map { i =>
      // ensure some duplicate values
      val time = TypoLocalDateTime(now.minusDays(i % limit.toLong))
      BusinessentityRow(BusinessentityId.apply(i), TypoUUID.randomUUID, time)
    }

    // same as sql below
    val List(group1, group2) = rows.sortBy(x => (Reverse(x.modifieddate.value), x.businessentityid.value)).grouped(limit).toList: @unchecked

    withConnection { implicit c =>
      // batch insert some rows
      businessentityRepo.insertStreaming(rows.iterator): @nowarn
      val rows1 = businessentityRepo.select
        .maybeSeek(_.modifieddate.desc)(None)
        .maybeSeek(_.businessentityid.asc)(None)
        .maybeSeek(_.rowguid.asc)(None)
        .limit(limit)
        .toList
      assert(rows1 == group1): @nowarn
      val rows2 = businessentityRepo.select
        .maybeSeek(_.modifieddate.desc)(Some(rows1.last.modifieddate))
        .maybeSeek(_.businessentityid.asc)(Some(rows1.last.businessentityid))
        .maybeSeek(_.rowguid.asc)(Some(rows1.last.rowguid))
        .limit(limit)
        .toList
      assert(rows2 == group2)
    }
  }

  test("non-uniform in-memory") {
    testNonUniformSeek(new BusinessentityRepoMock(_.toRow(???, ???, ???)))
  }

  test("non-uniform pg") {
    testNonUniformSeek(new BusinessentityRepoImpl)
  }
}

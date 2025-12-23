package adventureworks

import adventureworks.person.businessentity.*
import org.junit.Assert.*
import org.junit.Test
import typr.dsl.SqlExpr
import typr.runtime.PgTypes

import java.sql.Connection
import java.time.LocalDateTime
import java.util.UUID
import scala.jdk.CollectionConverters.*

class SeekDbTest {
  private val timestampPgType = PgTypes.timestamp.withTypename("timestamp")

  private def testUniformSeek(businessentityRepo: BusinessentityRepo): Unit = {
    val limit = 3
    val now = LocalDateTime.of(2021, 1, 1, 0, 0)

    val rows = (0 until limit * 2).map { i =>
      val time = now.minusDays(i % limit)
      BusinessentityRow(BusinessentityId(i), UUID.randomUUID(), time)
    }.toList

    val sortedRows = rows.sortBy(r => (r.modifieddate, r.businessentityid.value))
    val group1 = sortedRows.take(limit)
    val group2 = sortedRows.slice(limit, limit * 2)

    WithConnection {
      val _ = businessentityRepo.insertStreaming(rows.iterator.asJava, 100)

      val rows1 = businessentityRepo.select
        .maybeSeek(f => f.modifieddate.asc, java.util.Optional.empty[LocalDateTime](), (v: LocalDateTime) => new SqlExpr.ConstReq(v, timestampPgType))
        .maybeSeek(f => f.businessentityid.asc, java.util.Optional.empty[BusinessentityId](), (v: BusinessentityId) => new SqlExpr.ConstReq(v, BusinessentityId.pgType))
        .maybeSeek(f => f.rowguid.asc, java.util.Optional.empty[UUID](), (v: UUID) => new SqlExpr.ConstReq(v, PgTypes.uuid))
        .limit(limit)
        .toList(summon[Connection])
        .asScala
        .toList

      assertEquals(group1, rows1)

      val lastRow = rows1.last
      val rows2 = businessentityRepo.select
        .maybeSeek(f => f.modifieddate.asc, java.util.Optional.of(lastRow.modifieddate), (v: LocalDateTime) => new SqlExpr.ConstReq(v, timestampPgType))
        .maybeSeek(f => f.businessentityid.asc, java.util.Optional.of(lastRow.businessentityid), (v: BusinessentityId) => new SqlExpr.ConstReq(v, BusinessentityId.pgType))
        .maybeSeek(f => f.rowguid.asc, java.util.Optional.of(lastRow.rowguid), (v: UUID) => new SqlExpr.ConstReq(v, PgTypes.uuid))
        .limit(limit)
        .toList(summon[Connection])
        .asScala
        .toList

      assertEquals(group2, rows2)
    }
  }

  @Test
  def uniformInMemory(): Unit = {
    testUniformSeek(new BusinessentityRepoMock(_ => throw new UnsupportedOperationException))
  }

  @Test
  def uniformPg(): Unit = {
    testUniformSeek(new BusinessentityRepoImpl)
  }

  private def testNonUniformSeek(businessentityRepo: BusinessentityRepo): Unit = {
    val limit = 3
    val now = LocalDateTime.of(2021, 1, 1, 0, 0)

    val rows = (0 until limit * 2).map { i =>
      val time = now.minusDays(i % limit)
      BusinessentityRow(BusinessentityId(i), UUID.randomUUID(), time)
    }.toList

    val sortedRows = rows.sortBy(r => (-r.modifieddate.toEpochSecond(java.time.ZoneOffset.UTC), r.businessentityid.value))
    val group1 = sortedRows.take(limit)
    val group2 = sortedRows.slice(limit, limit * 2)

    WithConnection {
      val _ = businessentityRepo.insertStreaming(rows.iterator.asJava, 100)

      val rows1 = businessentityRepo.select
        .maybeSeek(f => f.modifieddate.desc, java.util.Optional.empty[LocalDateTime](), (v: LocalDateTime) => new SqlExpr.ConstReq(v, timestampPgType))
        .maybeSeek(f => f.businessentityid.asc, java.util.Optional.empty[BusinessentityId](), (v: BusinessentityId) => new SqlExpr.ConstReq(v, BusinessentityId.pgType))
        .maybeSeek(f => f.rowguid.asc, java.util.Optional.empty[UUID](), (v: UUID) => new SqlExpr.ConstReq(v, PgTypes.uuid))
        .limit(limit)
        .toList(summon[Connection])
        .asScala
        .toList

      assertEquals(group1, rows1)

      val lastRow = rows1.last
      val rows2 = businessentityRepo.select
        .maybeSeek(f => f.modifieddate.desc, java.util.Optional.of(lastRow.modifieddate), (v: LocalDateTime) => new SqlExpr.ConstReq(v, timestampPgType))
        .maybeSeek(f => f.businessentityid.asc, java.util.Optional.of(lastRow.businessentityid), (v: BusinessentityId) => new SqlExpr.ConstReq(v, BusinessentityId.pgType))
        .maybeSeek(f => f.rowguid.asc, java.util.Optional.of(lastRow.rowguid), (v: UUID) => new SqlExpr.ConstReq(v, PgTypes.uuid))
        .limit(limit)
        .toList(summon[Connection])
        .asScala
        .toList

      assertEquals(group2, rows2)
    }
  }

  @Test
  def nonUniformInMemory(): Unit = {
    testNonUniformSeek(new BusinessentityRepoMock(_ => throw new UnsupportedOperationException))
  }

  @Test
  def nonUniformPg(): Unit = {
    testNonUniformSeek(new BusinessentityRepoImpl)
  }
}

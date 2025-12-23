package adventureworks;

import static org.junit.Assert.*;

import adventureworks.person.businessentity.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.Test;
import typr.dsl.SqlExpr;
import typr.runtime.PgType;

/** Tests for seek-based pagination - equivalent to Scala SeekDbTest. */
public class SeekDbTest {
  // Need to use timestamp type for seek comparisons, not text type
  private static final PgType<LocalDateTime> timestampPgType =
      typr.runtime.PgTypes.timestamp.withTypename("timestamp");

  private void testUniformSeek(BusinessentityRepo businessentityRepo) {
    int limit = 3;
    var now = LocalDateTime.of(2021, 1, 1, 0, 0);

    // Create rows with some duplicate modifieddate values
    List<BusinessentityRow> rows = new ArrayList<>();
    for (int i = 0; i < limit * 2; i++) {
      // ensure some duplicate values
      var time = now.minusDays(i % limit);
      rows.add(new BusinessentityRow(new BusinessentityId(i), UUID.randomUUID(), time));
    }

    // Sort to get expected groups - same as SQL: ORDER BY modifieddate ASC, businessentityid ASC
    List<BusinessentityRow> sortedRows = new ArrayList<>(rows);
    sortedRows.sort(
        Comparator.comparing((BusinessentityRow r) -> r.modifieddate())
            .thenComparing(r -> r.businessentityid().value()));

    List<BusinessentityRow> group1 = sortedRows.subList(0, limit);
    List<BusinessentityRow> group2 = sortedRows.subList(limit, limit * 2);

    WithConnection.run(
        c -> {
          // batch insert some rows
          businessentityRepo.insertStreaming(rows.iterator(), 100, c);

          // First page - no seek values
          var rows1 =
              businessentityRepo
                  .select()
                  .maybeSeek(
                      f -> f.modifieddate().asc(),
                      Optional.empty(),
                      v -> new SqlExpr.ConstReq<>(v, timestampPgType))
                  .maybeSeek(
                      f -> f.businessentityid().asc(),
                      Optional.empty(),
                      v -> new SqlExpr.ConstReq<>(v, BusinessentityId.pgType))
                  .maybeSeek(
                      f -> f.rowguid().asc(),
                      Optional.empty(),
                      v -> new SqlExpr.ConstReq<>(v, typr.runtime.PgTypes.uuid))
                  .limit(limit)
                  .toList(c);
          assertEquals(group1, rows1);

          // Second page - seek from last row of first page
          var lastRow = rows1.get(rows1.size() - 1);
          var rows2 =
              businessentityRepo
                  .select()
                  .maybeSeek(
                      f -> f.modifieddate().asc(),
                      Optional.of(lastRow.modifieddate()),
                      v -> new SqlExpr.ConstReq<>(v, timestampPgType))
                  .maybeSeek(
                      f -> f.businessentityid().asc(),
                      Optional.of(lastRow.businessentityid()),
                      v -> new SqlExpr.ConstReq<>(v, BusinessentityId.pgType))
                  .maybeSeek(
                      f -> f.rowguid().asc(),
                      Optional.of(lastRow.rowguid()),
                      v -> new SqlExpr.ConstReq<>(v, typr.runtime.PgTypes.uuid))
                  .limit(limit)
                  .toList(c);
          assertEquals(group2, rows2);
        });
  }

  @Test
  public void uniformInMemory() {
    // The mock's toRow converter isn't used because we insert fully-constructed rows
    testUniformSeek(
        new BusinessentityRepoMock(
            unsaved -> {
              throw new UnsupportedOperationException();
            }));
  }

  @Test
  public void uniformPg() {
    testUniformSeek(new BusinessentityRepoImpl());
  }

  private void testNonUniformSeek(BusinessentityRepo businessentityRepo) {
    int limit = 3;
    var now = LocalDateTime.of(2021, 1, 1, 0, 0);

    // Create rows with some duplicate modifieddate values
    List<BusinessentityRow> rows = new ArrayList<>();
    for (int i = 0; i < limit * 2; i++) {
      // ensure some duplicate values
      var time = now.minusDays(i % limit);
      rows.add(new BusinessentityRow(new BusinessentityId(i), UUID.randomUUID(), time));
    }

    // Sort to get expected groups - same as SQL: ORDER BY modifieddate DESC, businessentityid ASC
    List<BusinessentityRow> sortedRows = new ArrayList<>(rows);
    sortedRows.sort(
        Comparator.comparing((BusinessentityRow r) -> r.modifieddate())
            .reversed()
            .thenComparing(r -> r.businessentityid().value()));

    List<BusinessentityRow> group1 = sortedRows.subList(0, limit);
    List<BusinessentityRow> group2 = sortedRows.subList(limit, limit * 2);

    WithConnection.run(
        c -> {
          // batch insert some rows
          businessentityRepo.insertStreaming(rows.iterator(), 100, c);

          // First page - no seek values (descending modifieddate, ascending for rest)
          var rows1 =
              businessentityRepo
                  .select()
                  .maybeSeek(
                      f -> f.modifieddate().desc(),
                      Optional.empty(),
                      v -> new SqlExpr.ConstReq<>(v, timestampPgType))
                  .maybeSeek(
                      f -> f.businessentityid().asc(),
                      Optional.empty(),
                      v -> new SqlExpr.ConstReq<>(v, BusinessentityId.pgType))
                  .maybeSeek(
                      f -> f.rowguid().asc(),
                      Optional.empty(),
                      v -> new SqlExpr.ConstReq<>(v, typr.runtime.PgTypes.uuid))
                  .limit(limit)
                  .toList(c);
          assertEquals(group1, rows1);

          // Second page - seek from last row of first page
          var lastRow = rows1.get(rows1.size() - 1);
          var rows2 =
              businessentityRepo
                  .select()
                  .maybeSeek(
                      f -> f.modifieddate().desc(),
                      Optional.of(lastRow.modifieddate()),
                      v -> new SqlExpr.ConstReq<>(v, timestampPgType))
                  .maybeSeek(
                      f -> f.businessentityid().asc(),
                      Optional.of(lastRow.businessentityid()),
                      v -> new SqlExpr.ConstReq<>(v, BusinessentityId.pgType))
                  .maybeSeek(
                      f -> f.rowguid().asc(),
                      Optional.of(lastRow.rowguid()),
                      v -> new SqlExpr.ConstReq<>(v, typr.runtime.PgTypes.uuid))
                  .limit(limit)
                  .toList(c);
          assertEquals(group2, rows2);
        });
  }

  @Test
  public void nonUniformInMemory() {
    // The mock's toRow converter isn't used because we insert fully-constructed rows
    testNonUniformSeek(
        new BusinessentityRepoMock(
            unsaved -> {
              throw new UnsupportedOperationException();
            }));
  }

  @Test
  public void nonUniformPg() {
    testNonUniformSeek(new BusinessentityRepoImpl());
  }
}

package typo.runtime;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;

public sealed interface Operation<Out>
    permits Operation.Query,
        Operation.Update,
        Operation.UpdateReturning,
        Operation.UpdateReturningGeneratedKeys,
        Operation.UpdateManyReturning,
        Operation.UpdateMany,
        Operation.UpdateReturningEach {
  Out run(Connection conn) throws SQLException;

  default Out runUnchecked(Connection conn) {
    try {
      return run(conn);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  record Query<Out>(Fragment query, ResultSetParser<Out> parser) implements Operation<Out> {
    @Override
    public Out run(Connection conn) throws SQLException {
      try (PreparedStatement stmt = conn.prepareStatement(query.render())) {
        query.set(stmt);
        try (ResultSet rs = stmt.executeQuery()) {
          return parser.apply(rs);
        }
      }
    }
  }

  record Update(Fragment query) implements Operation<Integer> {
    @Override
    public Integer run(Connection conn) throws SQLException {
      try (PreparedStatement stmt = conn.prepareStatement(query.render())) {
        query.set(stmt);
        return stmt.executeUpdate();
      }
    }
  }

  record UpdateReturning<Out>(Fragment query, ResultSetParser<Out> parser)
      implements Operation<Out> {
    @Override
    public Out run(Connection conn) throws SQLException {
      try (PreparedStatement stmt = conn.prepareStatement(query.render())) {
        query.set(stmt);
        try (ResultSet rs = stmt.executeQuery()) {
          return parser.apply(rs);
        }
      }
    }
  }

  record UpdateReturningGeneratedKeys<Out>(
      Fragment query, String[] columnNames, ResultSetParser<Out> parser) implements Operation<Out> {
    @Override
    public Out run(Connection conn) throws SQLException {
      try (PreparedStatement stmt = conn.prepareStatement(query.render(), columnNames)) {
        query.set(stmt);
        stmt.executeUpdate();
        try (ResultSet rs = stmt.getGeneratedKeys()) {
          return parser.apply(rs);
        }
      }
    }
  }

  record UpdateMany<Row>(Fragment query, RowParser<Row> parser, Iterator<Row> rows)
      implements Operation<int[]> {
    @Override
    public int[] run(Connection conn) throws SQLException {
      try (PreparedStatement stmt = conn.prepareStatement(query.render())) {
        query.set(stmt);
        while (rows.hasNext()) {
          Row row = rows.next();
          parser.writeRow(stmt, row);
          stmt.addBatch();
        }
        return stmt.executeBatch();
      }
    }
  }

  record UpdateManyReturning<Row>(Fragment query, RowParser<Row> parser, Iterator<Row> rows)
      implements Operation<List<Row>> {
    @Override
    public List<Row> run(Connection conn) throws SQLException {
      try (PreparedStatement stmt =
          conn.prepareStatement(query.render(), java.sql.Statement.RETURN_GENERATED_KEYS)) {
        query.set(stmt);
        while (rows.hasNext()) {
          Row row = rows.next();
          parser.writeRow(stmt, row);
          stmt.addBatch();
        }
        stmt.executeBatch();
        try (ResultSet rs = stmt.getGeneratedKeys()) {
          return parser.all().apply(rs);
        }
      }
    }
  }

  /**
   * Executes each row individually with RETURNING clause. Used for MariaDB where batch mode with
   * RETURNING doesn't work properly via getGeneratedKeys(). Each INSERT/UPDATE is executed
   * separately and the RETURNING result is read from executeQuery().
   */
  record UpdateReturningEach<Row>(Fragment query, RowParser<Row> parser, Iterator<Row> rows)
      implements Operation<List<Row>> {
    @Override
    public List<Row> run(Connection conn) throws SQLException {
      java.util.ArrayList<Row> results = new java.util.ArrayList<>();
      String sql = query.render();
      while (rows.hasNext()) {
        Row row = rows.next();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
          query.set(stmt);
          parser.writeRow(stmt, row);
          try (ResultSet rs = stmt.executeQuery()) {
            results.addAll(parser.all().apply(rs));
          }
        }
      }
      return results;
    }
  }
}

package typr.runtime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public interface ResultSetParser<Out> {
  Out apply(ResultSet resultSet) throws SQLException;

  record All<Out>(RowParser<Out> rowParser) implements ResultSetParser<List<Out>> {
    @Override
    public List<Out> apply(ResultSet resultSet) throws SQLException {
      var rowNum = 0;
      ArrayList<Out> rows = new ArrayList<>();
      while (resultSet.next()) {
        rows.add(rowParser.readRow(resultSet, rowNum));
        rowNum += 1;
      }
      return rows;
    }
  }

  record Foreach<Out>(RowParser<Out> rowParser, Consumer<Out> consumer)
      implements ResultSetParser<Void> {
    @Override
    public Void apply(ResultSet resultSet) throws SQLException {
      var rowNum = 0;
      while (resultSet.next()) {
        consumer.accept(rowParser.readRow(resultSet, rowNum));
        rowNum += 1;
      }
      return null;
    }
  }

  record First<Out>(RowParser<Out> rowParser) implements ResultSetParser<Optional<Out>> {
    @Override
    public Optional<Out> apply(ResultSet resultSet) throws SQLException {
      if (resultSet.next()) {
        return Optional.of(rowParser.readRow(resultSet, 0));
      } else {
        return Optional.empty();
      }
    }
  }

  record MaxOne<Out>(RowParser<Out> rowParser) implements ResultSetParser<Optional<Out>> {
    @Override
    public Optional<Out> apply(ResultSet resultSet) throws SQLException {
      if (resultSet.next()) {
        Out result = rowParser.readRow(resultSet, 0);
        if (resultSet.next()) {
          throw new SQLException("Expected single row, but found more");
        }
        return Optional.of(result);
      } else {
        return Optional.empty();
      }
    }
  }

  record ExactlyOne<Out>(RowParser<Out> rowParser) implements ResultSetParser<Out> {
    @Override
    public Out apply(ResultSet resultSet) throws SQLException {
      if (resultSet.next()) {
        Out result = rowParser.readRow(resultSet, 0);
        if (resultSet.next()) {
          throw new SQLException("Expected single row, but found more");
        }
        return result;
      } else {
        throw new SQLException("No rows when expecting a single one");
      }
    }
  }
}

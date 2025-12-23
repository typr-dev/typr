package typr.runtime;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

public class Foo {
  record User(String name, Integer age) {}

  public static String a = "";
  static RowParser<User> userRowParser =
      RowParsers.of(PgTypes.text, PgTypes.int4, User::new, row -> new Object[] {row.name, row.age});

  public static void main(String[] args) throws SQLException {
    var value = "Alice";

    Operation.UpdateReturning<User> update =
        Fragment.interpolate("UPDATE users SET name = ")
            .param(PgTypes.text, value)
            .sql(" WHERE name = 'Bob' RETURNING name, age")
            .done()
            .updateReturning(userRowParser.exactlyOne());

    var predicate1 = Fragment.interpolate("name = ").param(PgTypes.text, value).done();
    var whereClause = Fragment.whereAnd(predicate1);
    var frag = Fragment.interpolate("SELECT * FROM users ").param(whereClause).done();

    System.out.println(frag.render());
    System.out.println(update.query().render());

    var c =
        DriverManager.getConnection("jdbc:postgresql://localhost:5432/mydb", "user", "password");
    List<User> users = frag.query(userRowParser.all()).run(c);
    User user = frag.query(userRowParser.exactlyOne()).run(c);

    System.out.println(user);
    System.out.println(users);
  }
}

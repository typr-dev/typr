package typr.runtime.internal;

import java.sql.SQLException;
import org.postgresql.util.PGobject;

/**
 * Helper class for creating PGobject instances with type and value set. This avoids multi-statement
 * lambdas in generated code.
 */
public final class TypoPGObjectHelper {
  private TypoPGObjectHelper() {}

  /**
   * Creates a PGobject with the given type and value.
   *
   * @param type the PostgreSQL type name
   * @param value the string value
   * @return a new PGobject with type and value set
   */
  public static PGobject create(String type, String value) {
    try {
      PGobject obj = new PGobject();
      obj.setType(type);
      obj.setValue(value);
      return obj;
    } catch (SQLException e) {
      throw new RuntimeException("Failed to create PGobject for type: " + type, e);
    }
  }
}

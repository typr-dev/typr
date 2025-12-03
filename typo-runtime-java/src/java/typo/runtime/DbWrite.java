package typo.runtime;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Common interface for writing values to PreparedStatement.
 * Implemented by both PgWrite (PostgreSQL) and MariaWrite (MariaDB).
 */
public interface DbWrite<A> {
    /**
     * Set a value in a PreparedStatement at the given index.
     */
    void set(PreparedStatement ps, int idx, A value) throws SQLException;
}

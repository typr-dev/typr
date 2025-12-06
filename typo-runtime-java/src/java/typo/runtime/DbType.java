package typo.runtime;

/**
 * Common interface for database type codecs.
 * Implemented by both PgType (PostgreSQL) and MariaType (MariaDB).
 */
public interface DbType<A> {
    /**
     * Get the typename for SQL rendering (e.g., for casts like ?::typename).
     */
    DbTypename<A> typename();

    /**
     * Get the read codec for reading ResultSet columns.
     */
    DbRead<A> read();

    /**
     * Get the write codec for setting PreparedStatement parameters.
     */
    DbWrite<A> write();

    /**
     * Get the text encoder for bulk loading (COPY/LOAD DATA).
     */
    DbText<A> text();

    /**
     * Get the JSON codec for converting values to/from JSON format
     * that the database can produce/consume.
     */
    DbJson<A> json();

    /**
     * Create an optional version of this type.
     */
    DbType<java.util.Optional<A>> opt();
}

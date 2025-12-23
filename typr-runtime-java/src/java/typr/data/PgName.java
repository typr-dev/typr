package typr.data;

// PostgreSQL `name` type - internal identifier type (max 63 bytes)
// Used for database object names: table names, column names, etc.
public record PgName(String value) {}

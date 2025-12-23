import java.sql.*;

public class CheckMetadata {
    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:../db/duckdb/test.db")) {
            String sql = "SELECT table_name, column_name, data_type, is_nullable " +
                        "FROM information_schema.columns " +
                        "WHERE table_name = 'products' AND column_name = 'metadata'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    System.out.println("Table: " + rs.getString(1));
                    System.out.println("Column: " + rs.getString(2));
                    System.out.println("Data Type: " + rs.getString(3));
                    System.out.println("Is Nullable: " + rs.getString(4));
                }
            }
        }
    }
}

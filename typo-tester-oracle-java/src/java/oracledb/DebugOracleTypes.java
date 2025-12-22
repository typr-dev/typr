package oracledb;

import java.math.BigDecimal;
import java.util.Optional;
import oracledb.customtypes.Defaulted;
import oracledb.products.ProductsRepoImpl;
import oracledb.products.ProductsRowUnsaved;

public class DebugOracleTypes {
  public static void main(String[] args) {
    ProductsRepoImpl repo = new ProductsRepoImpl();

    MoneyT price = new MoneyT(new BigDecimal("49.99"), "EUR");

    ProductsRowUnsaved unsaved =
        new ProductsRowUnsaved(
            "PROD-DEBUG", "Debug Product", price, Optional.empty(), new Defaulted.UseDefault<>());

    // Try to insert without connecting to database - just render SQL
    System.out.println("Testing Oracle types in generated SQL...");

    OracleTestHelper.run(
        c -> {
          try {
            repo.insert(unsaved, c);
          } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
          }
        });
  }
}

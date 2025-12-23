package adventureworks.production.product;

import adventureworks.SnapshotTest;
import adventureworks.public_.Name;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.Test;
import typr.dsl.SqlExpr;
import typr.runtime.PgTypes;

/** Tests for DSL seek functionality - equivalent to Scala SeekTest. */
public class SeekTest extends SnapshotTest {
  private final ProductRepoImpl productRepo = new ProductRepoImpl();

  @Test
  public void uniformAscending() {
    var query =
        productRepo
            .select()
            .seek(f -> f.name().asc(), new SqlExpr.ConstReq<>(new Name("foo"), Name.pgType))
            .seek(
                f -> f.weight().asc(),
                new SqlExpr.ConstOpt<>(Optional.of(new BigDecimal("22.2")), PgTypes.numeric))
            .seek(
                f -> f.listprice().asc(),
                new SqlExpr.ConstReq<>(new BigDecimal("33.3"), PgTypes.numeric));
    compareFragment("uniform-ascending", query.sql());
  }

  @Test
  public void uniformDescending() {
    var query =
        productRepo
            .select()
            .seek(f -> f.name().desc(), new SqlExpr.ConstReq<>(new Name("foo"), Name.pgType))
            .seek(
                f -> f.weight().desc(),
                new SqlExpr.ConstOpt<>(Optional.of(new BigDecimal("22.2")), PgTypes.numeric))
            .seek(
                f -> f.listprice().desc(),
                new SqlExpr.ConstReq<>(new BigDecimal("33.3"), PgTypes.numeric));
    compareFragment("uniform-descending", query.sql());
  }

  @Test
  public void complex() {
    var query =
        productRepo
            .select()
            .seek(f -> f.name().asc(), new SqlExpr.ConstReq<>(new Name("foo"), Name.pgType))
            .seek(
                f -> f.weight().desc(),
                new SqlExpr.ConstOpt<>(Optional.of(new BigDecimal("22.2")), PgTypes.numeric))
            .seek(
                f -> f.listprice().desc(),
                new SqlExpr.ConstReq<>(new BigDecimal("33.3"), PgTypes.numeric));
    compareFragment("complex", query.sql());
  }
}

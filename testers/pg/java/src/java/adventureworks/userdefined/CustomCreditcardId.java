package adventureworks.userdefined;

import dev.typr.foundations.Bijection;
import dev.typr.foundations.PgText;
import dev.typr.foundations.PgType;
import dev.typr.foundations.PgTypes;

/** Type for the primary key of table `sales.creditcard` */
public record CustomCreditcardId(Integer value) {
  public static PgText<CustomCreditcardId> pgText =
      PgText.instance((v, sb) -> PgText.textInteger.unsafeEncode(v.value(), sb));
  public static PgType<CustomCreditcardId> pgType =
      PgTypes.int4.to(Bijection.of(CustomCreditcardId::new, v -> v.value()));
}

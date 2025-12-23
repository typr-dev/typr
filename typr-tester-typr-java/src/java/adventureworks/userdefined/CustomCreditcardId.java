package adventureworks.userdefined;

import typr.runtime.PgText;
import typr.runtime.PgType;
import typr.runtime.PgTypes;
import typr.runtime.internal.arrayMap;

/** Type for the primary key of table `sales.creditcard` */
public record CustomCreditcardId(Integer value) {
  public static PgText<CustomCreditcardId> pgText =
      PgText.instance((v, sb) -> PgText.textInteger.unsafeEncode(v.value(), sb));
  public static PgType<CustomCreditcardId> pgType =
      PgTypes.int4.bimap(CustomCreditcardId::new, v -> v.value());
  public static PgType<CustomCreditcardId[]> pgTypeArray =
      PgTypes.int4Array.bimap(
          arr -> arrayMap.map(arr, CustomCreditcardId::new, CustomCreditcardId.class),
          arr -> arrayMap.map(arr, v -> v.value(), Integer.class));
}

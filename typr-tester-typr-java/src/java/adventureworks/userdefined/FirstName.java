package adventureworks.userdefined;

import typr.runtime.PgText;
import typr.runtime.PgType;
import typr.runtime.PgTypes;
import typr.runtime.internal.arrayMap;

public record FirstName(String value) {
  public static PgText<FirstName> pgText =
      PgText.instance((v, sb) -> PgText.textString.unsafeEncode(v.value(), sb));
  public static PgType<FirstName> pgType = PgTypes.text.bimap(FirstName::new, v -> v.value());
  public static PgType<FirstName[]> pgTypeArray =
      PgTypes.textArray.bimap(
          arr -> arrayMap.map(arr, FirstName::new, FirstName.class),
          arr -> arrayMap.map(arr, v -> v.value(), String.class));
}

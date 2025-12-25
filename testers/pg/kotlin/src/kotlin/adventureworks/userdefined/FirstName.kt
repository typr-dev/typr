package adventureworks.userdefined

import dev.typr.foundations.PgText
import dev.typr.foundations.PgType
import dev.typr.foundations.PgTypes
import dev.typr.foundations.internal.arrayMap

@JvmInline
value class FirstName(val value: String) {
  companion object {
    @JvmStatic
    val pgText: PgText<FirstName> = PgText.instance { v, sb -> PgText.textString.unsafeEncode(v.value, sb) }
    @JvmStatic
    val pgType: PgType<FirstName> = PgTypes.text.bimap({ FirstName(it) }, { it.value })
    @JvmStatic
    val pgTypeArray: PgType<Array<FirstName>> = PgTypes.textArray.bimap(
      { arr -> arrayMap.map(arr, { FirstName(it) }, FirstName::class.java) },
      { arr -> arrayMap.map(arr, { it.value }, String::class.java) }
    )
  }
}

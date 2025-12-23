package adventureworks.userdefined

import typr.runtime.PgText
import typr.runtime.PgType
import typr.runtime.PgTypes
import typr.runtime.internal.arrayMap

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

package adventureworks.userdefined

import typo.runtime.PgText
import typo.runtime.PgType
import typo.runtime.PgTypes
import typo.runtime.internal.arrayMap

/** Type for the primary key of table `sales.creditcard` */
@JvmInline
value class CustomCreditcardId(val value: Int) {
  companion object {
    @JvmStatic
    val pgText: PgText<CustomCreditcardId> = PgText.instance { v, sb -> PgText.textInteger.unsafeEncode(v.value, sb) }
    @JvmStatic
    val pgType: PgType<CustomCreditcardId> = PgTypes.int4.bimap({ CustomCreditcardId(it) }, { it.value })
    @JvmStatic
    val pgTypeArray: PgType<Array<CustomCreditcardId>> = PgTypes.int4Array.bimap(
      { arr -> arrayMap.map(arr, { CustomCreditcardId(it) }, CustomCreditcardId::class.java) },
      { arr -> arrayMap.map(arr, { it.value }, Int::class.javaObjectType) }
    )
  }
}

package adventureworks.userdefined

import dev.typr.foundations.PgText
import dev.typr.foundations.PgType
import dev.typr.foundations.PgTypes
import dev.typr.foundations.internal.arrayMap

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

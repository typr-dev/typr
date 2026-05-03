package adventureworks.userdefined

import dev.typr.foundations.PgText
import dev.typr.foundationskt.Bijection
import dev.typr.foundationskt.PgType
import dev.typr.foundationskt.PgTypes

/** Type for the primary key of table `sales.creditcard` */
@JvmInline
value class CustomCreditcardId(val value: Int) {
  companion object {
    @JvmStatic
    val pgText: PgText<CustomCreditcardId> = PgText.instance { v, sb -> PgText.textInteger.unsafeEncode(v.value, sb) }
    @JvmStatic
    val pgType: PgType<CustomCreditcardId> = PgTypes.int4.to(Bijection.of({ CustomCreditcardId(it) }, { it.value }))
    @JvmStatic
    val pgTypeArray: PgType<List<CustomCreditcardId>> = PgTypes.int4.array().to(Bijection.of(
      { list -> list.map { CustomCreditcardId(it) } },
      { list -> list.map { it.value } }
    ))
  }
}

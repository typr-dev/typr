package adventureworks.userdefined

import typo.runtime.PgText
import typo.runtime.PgType
import typo.runtime.PgTypes
import typo.runtime.internal.arrayMap

data class FirstName(val value: String) {
    companion object {
        val pgText: PgText<FirstName> = PgText.instance { v, sb -> PgText.textString.unsafeEncode(v.value, sb) }
        val pgType: PgType<FirstName> = PgTypes.text.bimap(::FirstName) { it.value }
        val pgTypeArray: PgType<Array<FirstName>> = PgTypes.textArray.bimap(
            { arr -> arrayMap.map(arr, ::FirstName, FirstName::class.java) },
            { arr -> arrayMap.map(arr, { it.value }, String::class.java) }
        )
    }
}

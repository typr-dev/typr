package testapi.model

import kotlin.collections.Map

enum class PetStatus(val value: String) {
    available("available"),
    pending("pending"),
    sold("sold");

    companion object {
        val Names: String = entries.joinToString(", ") { it.value }
        val ByName: Map<String, PetStatus> = entries.associateBy { it.value }
        

        fun force(str: String): PetStatus =
            ByName[str] ?: throw RuntimeException("'$str' does not match any of the following legal values: $Names")
    }
}

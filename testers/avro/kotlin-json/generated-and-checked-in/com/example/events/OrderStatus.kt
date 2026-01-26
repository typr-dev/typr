package com.example.events



enum class OrderStatus(val value: kotlin.String) {
    PENDING("PENDING"),
    CONFIRMED("CONFIRMED"),
    SHIPPED("SHIPPED"),
    DELIVERED("DELIVERED"),
    CANCELLED("CANCELLED");

    companion object {
        val Names: kotlin.String = entries.joinToString(", ") { it.value }
        val ByName: kotlin.collections.Map<kotlin.String, OrderStatus> = entries.associateBy { it.value }
        

        fun force(str: kotlin.String): OrderStatus =
            ByName[str] ?: throw RuntimeException("'$str' does not match any of the following legal values: $Names")
    }
}

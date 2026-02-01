package com.example.grpc

import java.lang.IllegalArgumentException

enum class OrderStatus(val value: kotlin.String) {
    ORDER_STATUS_UNSPECIFIED("ORDER_STATUS_UNSPECIFIED"),
    ORDER_STATUS_PENDING("ORDER_STATUS_PENDING"),
    ORDER_STATUS_PROCESSING("ORDER_STATUS_PROCESSING"),
    ORDER_STATUS_SHIPPED("ORDER_STATUS_SHIPPED"),
    ORDER_STATUS_DELIVERED("ORDER_STATUS_DELIVERED"),
    ORDER_STATUS_CANCELLED("ORDER_STATUS_CANCELLED");

    fun toValue(): Int {
      if (this.toString().equals("ORDER_STATUS_UNSPECIFIED")) { return 0 }
      else if (this.toString().equals("ORDER_STATUS_PENDING")) { return 1 }
      else if (this.toString().equals("ORDER_STATUS_PROCESSING")) { return 2 }
      else if (this.toString().equals("ORDER_STATUS_SHIPPED")) { return 3 }
      else if (this.toString().equals("ORDER_STATUS_DELIVERED")) { return 4 }
      else if (this.toString().equals("ORDER_STATUS_CANCELLED")) { return 5 }
      else { return 0 }
    }

    companion object {
        val Names: kotlin.String = entries.joinToString(", ") { it.value }
        val ByName: kotlin.collections.Map<kotlin.String, OrderStatus> = entries.associateBy { it.value }
        fun fromValue(value: Int): OrderStatus {
          if (value == 0) { return OrderStatus.ORDER_STATUS_UNSPECIFIED }
          else if (value == 1) { return OrderStatus.ORDER_STATUS_PENDING }
          else if (value == 2) { return OrderStatus.ORDER_STATUS_PROCESSING }
          else if (value == 3) { return OrderStatus.ORDER_STATUS_SHIPPED }
          else if (value == 4) { return OrderStatus.ORDER_STATUS_DELIVERED }
          else if (value == 5) { return OrderStatus.ORDER_STATUS_CANCELLED }
          else { throw IllegalArgumentException("Unknown enum value: " + value) }
        }

        fun force(str: kotlin.String): OrderStatus =
            ByName[str] ?: throw RuntimeException("'$str' does not match any of the following legal values: $Names")
    }
}

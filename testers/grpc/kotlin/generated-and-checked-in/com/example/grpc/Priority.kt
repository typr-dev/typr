package com.example.grpc

import java.lang.IllegalArgumentException

enum class Priority(val value: kotlin.String) {
    PRIORITY_UNSPECIFIED("PRIORITY_UNSPECIFIED"),
    PRIORITY_LOW("PRIORITY_LOW"),
    PRIORITY_MEDIUM("PRIORITY_MEDIUM"),
    PRIORITY_HIGH("PRIORITY_HIGH"),
    PRIORITY_CRITICAL("PRIORITY_CRITICAL");

    fun toValue(): Int {
      if (this.toString().equals("PRIORITY_UNSPECIFIED")) { return 0 }
      else if (this.toString().equals("PRIORITY_LOW")) { return 1 }
      else if (this.toString().equals("PRIORITY_MEDIUM")) { return 2 }
      else if (this.toString().equals("PRIORITY_HIGH")) { return 3 }
      else if (this.toString().equals("PRIORITY_CRITICAL")) { return 4 }
      else { return 0 }
    }

    companion object {
        val Names: kotlin.String = entries.joinToString(", ") { it.value }
        val ByName: kotlin.collections.Map<kotlin.String, Priority> = entries.associateBy { it.value }
        fun fromValue(value: Int): Priority {
          if (value == 0) { return Priority.PRIORITY_UNSPECIFIED }
          else if (value == 1) { return Priority.PRIORITY_LOW }
          else if (value == 2) { return Priority.PRIORITY_MEDIUM }
          else if (value == 3) { return Priority.PRIORITY_HIGH }
          else if (value == 4) { return Priority.PRIORITY_CRITICAL }
          else { throw IllegalArgumentException("Unknown enum value: " + value) }
        }

        fun force(str: kotlin.String): Priority =
            ByName[str] ?: throw RuntimeException("'$str' does not match any of the following legal values: $Names")
    }
}

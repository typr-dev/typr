package com.example.events

import com.fasterxml.jackson.annotation.JsonProperty

/** A recursive linked list for testing recursive type support */
data class LinkedListNode(
  /** The value stored in this node */
  @field:JsonProperty("value") val value: Int,
  /** Optional next node in the list */
  @field:JsonProperty("next") val next: LinkedListNode?
)
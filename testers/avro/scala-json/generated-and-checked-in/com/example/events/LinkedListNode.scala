package com.example.events

import com.fasterxml.jackson.annotation.JsonProperty

/** A recursive linked list for testing recursive type support */
case class LinkedListNode(
  /** The value stored in this node */
  @JsonProperty("value") value: Int,
  /** Optional next node in the list */
  @JsonProperty("next") next: Option[LinkedListNode]
)
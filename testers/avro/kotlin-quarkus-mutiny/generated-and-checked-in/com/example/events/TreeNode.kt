package com.example.events

import com.fasterxml.jackson.annotation.JsonProperty

/** A recursive tree structure for testing recursive type support */
data class TreeNode(
  /** The value stored in this node */
  @field:JsonProperty("value") val value: kotlin.String,
  /** Optional left child */
  @field:JsonProperty("left") val left: TreeNode?,
  /** Optional right child */
  @field:JsonProperty("right") val right: TreeNode?
)
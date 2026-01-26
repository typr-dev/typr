package com.example.events

import com.fasterxml.jackson.annotation.JsonProperty

/** A recursive tree structure for testing recursive type support */
case class TreeNode(
  /** The value stored in this node */
  @JsonProperty("value") value: String,
  /** Optional left child */
  @JsonProperty("left") left: Option[TreeNode],
  /** Optional right child */
  @JsonProperty("right") right: Option[TreeNode]
)
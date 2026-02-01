package com.example.events.consumer

import com.example.events.TreeNode
import com.example.events.header.StandardHeaders

/** Handler interface for tree-node topic events */
trait TreeNodeHandler {
  /** Handle a message from the topic */
  def handle(
    key: String,
    value: TreeNode,
    headers: StandardHeaders
  ): Unit
}
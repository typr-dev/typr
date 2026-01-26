package com.example.events.consumer

import com.example.events.TreeNode
import com.example.events.header.StandardHeaders

/** Handler interface for tree-node topic events */
interface TreeNodeHandler {
  /** Handle a message from the topic */
  abstract fun handle(
    key: kotlin.String,
    value: TreeNode,
    headers: StandardHeaders
  )
}
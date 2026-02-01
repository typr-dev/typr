package com.example.events.consumer

import com.example.events.LinkedListNode
import com.example.events.header.StandardHeaders

/** Handler interface for linked-list-node topic events */
interface LinkedListNodeHandler {
  /** Handle a message from the topic */
  abstract fun handle(
    key: kotlin.String,
    value: LinkedListNode,
    headers: StandardHeaders
  )
}
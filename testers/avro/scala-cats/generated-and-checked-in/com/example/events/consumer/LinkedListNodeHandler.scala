package com.example.events.consumer

import cats.effect.IO
import com.example.events.LinkedListNode
import com.example.events.header.StandardHeaders

/** Handler interface for linked-list-node topic events */
trait LinkedListNodeHandler {
  /** Handle a message from the topic */
  def handle(
    key: String,
    value: LinkedListNode,
    headers: StandardHeaders
  ): IO[Unit]
}
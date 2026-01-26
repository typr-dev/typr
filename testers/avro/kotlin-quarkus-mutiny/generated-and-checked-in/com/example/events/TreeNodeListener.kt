package com.example.events

import io.smallrye.mutiny.Uni
import java.lang.Void
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.eclipse.microprofile.reactive.messaging.Message
import org.eclipse.microprofile.reactive.messaging.Metadata

/** Event listener interface for tree-node topic. Implement this interface to handle events. */
interface TreeNodeListener {
  /** Handle TreeNode event */
  abstract fun onTreeNode(
    event: TreeNode,
    metadata: Metadata
  ): Uni<Void>

  /** Handle unknown event types. Override to customize behavior. */
  fun onUnknown(record: Message<Any>): Uni<Void> {
    return Uni.createFrom().voidItem()
  }

  /** Receive and dispatch events to handler methods */
  @Incoming("tree-node")
  fun receive(record: Message<Any>): Uni<Void> {
    return when (val __r = record.getPayload()) {
      null -> onUnknown(record)
      is TreeNode -> { val e = __r as TreeNode; onTreeNode(e, record.getMetadata()) }
      else -> onUnknown(record)
    }
  }
}
package com.example.events

import io.smallrye.mutiny.Uni
import io.smallrye.reactive.messaging.MutinyEmitter
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.lang.Void
import org.eclipse.microprofile.reactive.messaging.Channel

@ApplicationScoped
/** Type-safe event publisher for tree-node topic */
data class TreeNodePublisher @Inject constructor(
  @field:Channel("tree-node") val kafkaTemplate: MutinyEmitter<TreeNode>,
  val topic: kotlin.String = "tree-node"
) {
  /** Publish a TreeNode event */
  fun publish(
    key: kotlin.String,
    event: TreeNode
  ): Uni<Void> {
    return kafkaTemplate.send(event)
  }
}
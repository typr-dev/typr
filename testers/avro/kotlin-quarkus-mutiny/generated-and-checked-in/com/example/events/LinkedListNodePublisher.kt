package com.example.events

import io.smallrye.mutiny.Uni
import io.smallrye.reactive.messaging.MutinyEmitter
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.lang.Void
import org.eclipse.microprofile.reactive.messaging.Channel

@ApplicationScoped
/** Type-safe event publisher for linked-list-node topic */
data class LinkedListNodePublisher @Inject constructor(
  @field:Channel("linked-list-node") val kafkaTemplate: MutinyEmitter<LinkedListNode>,
  val topic: kotlin.String = "linked-list-node"
) {
  /** Publish a LinkedListNode event */
  fun publish(
    key: kotlin.String,
    event: LinkedListNode
  ): Uni<Void> {
    return kafkaTemplate.send(event)
  }
}
package com.example.events;

import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;

/** Event listener interface for tree-node topic. Implement this interface to handle events. */
public interface TreeNodeListener {
  /** Receive and dispatch events to handler methods */
  @Incoming("tree-node")
  default Uni<Void> receive(Message<Object> record) {
    return switch (record.getPayload()) {
      case null -> onUnknown(record);
      case TreeNode e -> onTreeNode(e, record.getMetadata());
      default -> onUnknown(record);
    };
  }

  /** Handle TreeNode event */
  Uni<Void> onTreeNode(TreeNode event, Metadata metadata);

  /** Handle unknown event types. Override to customize behavior. */
  default Uni<Void> onUnknown(Message<Object> record) {
    return Uni.createFrom().voidItem();
  }
}

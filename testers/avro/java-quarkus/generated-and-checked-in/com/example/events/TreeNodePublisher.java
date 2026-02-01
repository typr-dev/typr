package com.example.events;

import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;

/** Type-safe event publisher for tree-node topic */
@ApplicationScoped
public record TreeNodePublisher(
    @Channel("tree-node") MutinyEmitter<TreeNode> kafkaTemplate, String topic) {
  @Inject
  public TreeNodePublisher {}

  public TreeNodePublisher(@Channel("tree-node") MutinyEmitter<TreeNode> kafkaTemplate) {
    this(kafkaTemplate, "tree-node");
  }

  public TreeNodePublisher withKafkaTemplate(MutinyEmitter<TreeNode> kafkaTemplate) {
    return new TreeNodePublisher(kafkaTemplate, topic);
  }

  public TreeNodePublisher withTopic(String topic) {
    return new TreeNodePublisher(kafkaTemplate, topic);
  }

  /** Publish a TreeNode event */
  public Uni<Void> publish(String key, TreeNode event) {
    return kafkaTemplate.send(event);
  }
}

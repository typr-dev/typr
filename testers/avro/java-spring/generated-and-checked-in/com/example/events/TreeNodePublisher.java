package com.example.events;

import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

/** Type-safe event publisher for tree-node topic */
@Service
public record TreeNodePublisher(KafkaTemplate<String, TreeNode> kafkaTemplate, String topic) {
  public TreeNodePublisher(KafkaTemplate<String, TreeNode> kafkaTemplate) {
    this(kafkaTemplate, "tree-node");
  }

  public TreeNodePublisher withKafkaTemplate(KafkaTemplate<String, TreeNode> kafkaTemplate) {
    return new TreeNodePublisher(kafkaTemplate, topic);
  }

  public TreeNodePublisher withTopic(String topic) {
    return new TreeNodePublisher(kafkaTemplate, topic);
  }

  /** Publish a TreeNode event */
  public CompletableFuture<SendResult<String, TreeNode>> publish(String key, TreeNode event) {
    return kafkaTemplate.send(topic, key, event);
  }
}

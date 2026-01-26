package com.example.events.producer;

import com.example.events.TreeNode;
import com.example.events.header.StandardHeaders;
import java.util.concurrent.Future;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

/** Type-safe producer for tree-node topic */
public record TreeNodeProducer(Producer<String, TreeNode> producer, String topic)
    implements AutoCloseable {
  public TreeNodeProducer(Producer<String, TreeNode> producer) {
    this(producer, "tree-node");
  }

  public TreeNodeProducer withProducer(Producer<String, TreeNode> producer) {
    return new TreeNodeProducer(producer, topic);
  }

  public TreeNodeProducer withTopic(String topic) {
    return new TreeNodeProducer(producer, topic);
  }

  /** Close the producer */
  @Override
  public void close() {
    producer.close();
  }

  /** Send a message to the topic */
  public Future<RecordMetadata> send(String key, TreeNode value) {
    return producer.send(new ProducerRecord<String, TreeNode>(topic, key, value));
  }

  /** Send a message with headers to the topic */
  public Future<RecordMetadata> send(String key, TreeNode value, StandardHeaders headers) {
    return producer.send(
        new ProducerRecord<String, TreeNode>(topic, null, key, value, headers.toHeaders()));
  }
}

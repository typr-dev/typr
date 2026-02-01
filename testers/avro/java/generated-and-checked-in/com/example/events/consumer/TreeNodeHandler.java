package com.example.events.consumer;

import com.example.events.TreeNode;
import com.example.events.header.StandardHeaders;

/** Handler interface for tree-node topic events */
public interface TreeNodeHandler {
  /** Handle a message from the topic */
  void handle(String key, TreeNode value, StandardHeaders headers);
}

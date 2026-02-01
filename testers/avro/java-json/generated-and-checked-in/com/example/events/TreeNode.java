package com.example.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;

/** A recursive tree structure for testing recursive type support */
public record TreeNode(
    /** The value stored in this node */
    @JsonProperty("value") String value,
    /** Optional left child */
    @JsonProperty("left") Optional<TreeNode> left,
    /** Optional right child */
    @JsonProperty("right") Optional<TreeNode> right) {
  /** The value stored in this node */
  public TreeNode withValue(String value) {
    return new TreeNode(value, left, right);
  }

  /** Optional left child */
  public TreeNode withLeft(Optional<TreeNode> left) {
    return new TreeNode(value, left, right);
  }

  /** Optional right child */
  public TreeNode withRight(Optional<TreeNode> right) {
    return new TreeNode(value, left, right);
  }
}

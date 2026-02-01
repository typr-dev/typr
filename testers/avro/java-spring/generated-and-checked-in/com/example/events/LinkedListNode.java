package com.example.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;

/** A recursive linked list for testing recursive type support */
public record LinkedListNode(
    /** The value stored in this node */
    @JsonProperty("value") Integer value,
    /** Optional next node in the list */
    @JsonProperty("next") Optional<LinkedListNode> next) {
  /** The value stored in this node */
  public LinkedListNode withValue(Integer value) {
    return new LinkedListNode(value, next);
  }

  /** Optional next node in the list */
  public LinkedListNode withNext(Optional<LinkedListNode> next) {
    return new LinkedListNode(value, next);
  }
}

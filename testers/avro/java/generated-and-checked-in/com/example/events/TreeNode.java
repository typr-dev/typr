package com.example.events;

import java.util.Optional;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Parser;
import org.apache.avro.generic.GenericData.Record;
import org.apache.avro.generic.GenericRecord;

/** A recursive tree structure for testing recursive type support */
public record TreeNode(
    /** The value stored in this node */
    String value,
    /** Optional left child */
    Optional<TreeNode> left,
    /** Optional right child */
    Optional<TreeNode> right) {
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

  public static Schema SCHEMA =
      new Parser()
          .parse(
              "{\"type\": \"record\",\"name\": \"TreeNode\",\"namespace\":"
                  + " \"com.example.events\",\"doc\": \"A recursive tree structure for testing"
                  + " recursive type support\",\"fields\": [{\"name\": \"value\",\"doc\": \"The"
                  + " value stored in this node\",\"type\": \"string\"},{\"name\":"
                  + " \"left\",\"doc\": \"Optional left child\",\"type\":"
                  + " [\"null\",\"com.example.events.TreeNode\"],\"default\": null},{\"name\":"
                  + " \"right\",\"doc\": \"Optional right child\",\"type\":"
                  + " [\"null\",\"com.example.events.TreeNode\"],\"default\": null}]}");

  /** Create a record from a GenericRecord (for deserialization) */
  public static TreeNode fromGenericRecord(GenericRecord record) {
    return new TreeNode(
        record.get("value").toString(),
        Optional.ofNullable(
            (record.get("left") == null
                ? null
                : TreeNode.fromGenericRecord(((GenericRecord) record.get("left"))))),
        Optional.ofNullable(
            (record.get("right") == null
                ? null
                : TreeNode.fromGenericRecord(((GenericRecord) record.get("right"))))));
  }

  /** Convert this record to a GenericRecord for serialization */
  public GenericRecord toGenericRecord() {
    Record record = new Record(TreeNode.SCHEMA);
    record.put("value", this.value());
    record.put("left", (this.left().isEmpty() ? null : this.left().get().toGenericRecord()));
    record.put("right", (this.right().isEmpty() ? null : this.right().get().toGenericRecord()));
    return record;
  }
}

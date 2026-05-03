package combined.avro_events.consumer;

import com.example.events.TreeNode;
import combined.avro_events.header.StandardHeaders;
import java.lang.Void;
import java.util.concurrent.CompletableFuture;

/** Handler interface for tree-node topic events */
public interface TreeNodeHandler {
  /** Handle a message from the topic */
  CompletableFuture<Void> handle(
    String key,
    TreeNode value,
    StandardHeaders headers
  );
}
package combined.avro_events.consumer

import com.example.events.TreeNode
import combined.avro_events.header.StandardHeaders
import io.smallrye.mutiny.Uni

/** Handler interface for tree-node topic events */
interface TreeNodeHandler {
  /** Handle a message from the topic */
  abstract fun handle(
    key: kotlin.String,
    value: TreeNode,
    headers: StandardHeaders
  ): Uni<Unit>
}
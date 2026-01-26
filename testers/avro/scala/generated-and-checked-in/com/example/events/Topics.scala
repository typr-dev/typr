package com.example.events

import com.example.events.common.Money
import com.example.events.serde.AddressSerde
import com.example.events.serde.CustomerOrderSerde
import com.example.events.serde.DynamicValueSerde
import com.example.events.serde.InvoiceSerde
import com.example.events.serde.LinkedListNodeSerde
import com.example.events.serde.MoneySerde
import com.example.events.serde.OrderCancelledSerde
import com.example.events.serde.OrderEventsSerde
import com.example.events.serde.OrderPlacedSerde
import com.example.events.serde.OrderUpdatedSerde
import com.example.events.serde.TreeNodeSerde
import org.apache.kafka.common.serialization.Serdes

/** Type-safe topic binding constants */
class Topics

object Topics {
  val ADDRESS: TypedTopic[String, Address] = new TypedTopic[String, Address]("address", Serdes.String, new AddressSerde())

  val CUSTOMER_ORDER: TypedTopic[String, CustomerOrder] = new TypedTopic[String, CustomerOrder]("customer-order", Serdes.String, new CustomerOrderSerde())

  val DYNAMIC_VALUE: TypedTopic[String, DynamicValue] = new TypedTopic[String, DynamicValue]("dynamic-value", Serdes.String, new DynamicValueSerde())

  val INVOICE: TypedTopic[String, Invoice] = new TypedTopic[String, Invoice]("invoice", Serdes.String, new InvoiceSerde())

  val LINKED_LIST_NODE: TypedTopic[String, LinkedListNode] = new TypedTopic[String, LinkedListNode]("linked-list-node", Serdes.String, new LinkedListNodeSerde())

  val MONEY: TypedTopic[String, Money] = new TypedTopic[String, Money]("money", Serdes.String, new MoneySerde())

  val ORDER_CANCELLED: TypedTopic[String, OrderCancelled] = new TypedTopic[String, OrderCancelled]("order-cancelled", Serdes.String, new OrderCancelledSerde())

  val ORDER_EVENTS: TypedTopic[String, OrderEvents] = new TypedTopic[String, OrderEvents]("order-events", Serdes.String, new OrderEventsSerde())

  val ORDER_PLACED: TypedTopic[String, OrderPlaced] = new TypedTopic[String, OrderPlaced]("order-placed", Serdes.String, new OrderPlacedSerde())

  val ORDER_UPDATED: TypedTopic[String, OrderUpdated] = new TypedTopic[String, OrderUpdated]("order-updated", Serdes.String, new OrderUpdatedSerde())

  val TREE_NODE: TypedTopic[String, TreeNode] = new TypedTopic[String, TreeNode]("tree-node", Serdes.String, new TreeNodeSerde())
}
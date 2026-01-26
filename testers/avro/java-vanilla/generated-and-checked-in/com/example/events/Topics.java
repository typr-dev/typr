package com.example.events;

import com.example.events.common.Money;
import com.example.events.serde.AddressSerde;
import com.example.events.serde.CustomerOrderSerde;
import com.example.events.serde.DynamicValueSerde;
import com.example.events.serde.InvoiceSerde;
import com.example.events.serde.LinkedListNodeSerde;
import com.example.events.serde.MoneySerde;
import com.example.events.serde.OrderCancelledSerde;
import com.example.events.serde.OrderEventsSerde;
import com.example.events.serde.OrderPlacedSerde;
import com.example.events.serde.OrderUpdatedSerde;
import com.example.events.serde.TreeNodeSerde;
import org.apache.kafka.common.serialization.Serdes;

/** Type-safe topic binding constants */
public class Topics {
  public static TypedTopic<String, Address> ADDRESS =
      new TypedTopic<String, Address>("address", Serdes.String(), new AddressSerde());

  public static TypedTopic<String, CustomerOrder> CUSTOMER_ORDER =
      new TypedTopic<String, CustomerOrder>(
          "customer-order", Serdes.String(), new CustomerOrderSerde());

  public static TypedTopic<String, DynamicValue> DYNAMIC_VALUE =
      new TypedTopic<String, DynamicValue>(
          "dynamic-value", Serdes.String(), new DynamicValueSerde());

  public static TypedTopic<String, Invoice> INVOICE =
      new TypedTopic<String, Invoice>("invoice", Serdes.String(), new InvoiceSerde());

  public static TypedTopic<String, LinkedListNode> LINKED_LIST_NODE =
      new TypedTopic<String, LinkedListNode>(
          "linked-list-node", Serdes.String(), new LinkedListNodeSerde());

  public static TypedTopic<String, Money> MONEY =
      new TypedTopic<String, Money>("money", Serdes.String(), new MoneySerde());

  public static TypedTopic<String, OrderCancelled> ORDER_CANCELLED =
      new TypedTopic<String, OrderCancelled>(
          "order-cancelled", Serdes.String(), new OrderCancelledSerde());

  public static TypedTopic<String, OrderEvents> ORDER_EVENTS =
      new TypedTopic<String, OrderEvents>("order-events", Serdes.String(), new OrderEventsSerde());

  public static TypedTopic<String, OrderPlaced> ORDER_PLACED =
      new TypedTopic<String, OrderPlaced>("order-placed", Serdes.String(), new OrderPlacedSerde());

  public static TypedTopic<String, OrderUpdated> ORDER_UPDATED =
      new TypedTopic<String, OrderUpdated>(
          "order-updated", Serdes.String(), new OrderUpdatedSerde());

  public static TypedTopic<String, TreeNode> TREE_NODE =
      new TypedTopic<String, TreeNode>("tree-node", Serdes.String(), new TreeNodeSerde());
}

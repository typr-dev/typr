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
class Topics() {
  companion object {
    val ADDRESS: TypedTopic<kotlin.String, Address> = TypedTopic<kotlin.String, Address>("address", Serdes.String(), AddressSerde())

    val CUSTOMER_ORDER: TypedTopic<kotlin.String, CustomerOrder> = TypedTopic<kotlin.String, CustomerOrder>("customer-order", Serdes.String(), CustomerOrderSerde())

    val DYNAMIC_VALUE: TypedTopic<kotlin.String, DynamicValue> = TypedTopic<kotlin.String, DynamicValue>("dynamic-value", Serdes.String(), DynamicValueSerde())

    val INVOICE: TypedTopic<kotlin.String, Invoice> = TypedTopic<kotlin.String, Invoice>("invoice", Serdes.String(), InvoiceSerde())

    val LINKED_LIST_NODE: TypedTopic<kotlin.String, LinkedListNode> = TypedTopic<kotlin.String, LinkedListNode>("linked-list-node", Serdes.String(), LinkedListNodeSerde())

    val MONEY: TypedTopic<kotlin.String, Money> = TypedTopic<kotlin.String, Money>("money", Serdes.String(), MoneySerde())

    val ORDER_CANCELLED: TypedTopic<kotlin.String, OrderCancelled> = TypedTopic<kotlin.String, OrderCancelled>("order-cancelled", Serdes.String(), OrderCancelledSerde())

    val ORDER_EVENTS: TypedTopic<kotlin.String, OrderEvents> = TypedTopic<kotlin.String, OrderEvents>("order-events", Serdes.String(), OrderEventsSerde())

    val ORDER_PLACED: TypedTopic<kotlin.String, OrderPlaced> = TypedTopic<kotlin.String, OrderPlaced>("order-placed", Serdes.String(), OrderPlacedSerde())

    val ORDER_UPDATED: TypedTopic<kotlin.String, OrderUpdated> = TypedTopic<kotlin.String, OrderUpdated>("order-updated", Serdes.String(), OrderUpdatedSerde())

    val TREE_NODE: TypedTopic<kotlin.String, TreeNode> = TypedTopic<kotlin.String, TreeNode>("tree-node", Serdes.String(), TreeNodeSerde())
  }
}
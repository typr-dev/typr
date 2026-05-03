package combined.avro_events

import com.example.events.Address
import com.example.events.CustomerOrder
import com.example.events.DynamicValue
import com.example.events.Invoice
import com.example.events.LinkedListNode
import com.example.events.OrderCancelled
import com.example.events.OrderEvents
import com.example.events.OrderPlaced
import com.example.events.OrderUpdated
import com.example.events.PaymentCallback
import com.example.events.PaymentCharged
import com.example.events.TreeNode
import com.example.events.common.Money
import combined.avro_events.serde.AddressSerde
import combined.avro_events.serde.CustomerOrderSerde
import combined.avro_events.serde.DynamicValueSerde
import combined.avro_events.serde.InvoiceSerde
import combined.avro_events.serde.LinkedListNodeSerde
import combined.avro_events.serde.MoneySerde
import combined.avro_events.serde.OrderCancelledSerde
import combined.avro_events.serde.OrderEventsSerde
import combined.avro_events.serde.OrderPlacedSerde
import combined.avro_events.serde.OrderUpdatedSerde
import combined.avro_events.serde.PaymentCallbackSerde
import combined.avro_events.serde.PaymentChargedSerde
import combined.avro_events.serde.TreeNodeSerde
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

    val PAYMENT_CALLBACK: TypedTopic<kotlin.String, PaymentCallback> = TypedTopic<kotlin.String, PaymentCallback>("payment-callback", Serdes.String(), PaymentCallbackSerde())

    val PAYMENT_CHARGED: TypedTopic<kotlin.String, PaymentCharged> = TypedTopic<kotlin.String, PaymentCharged>("payment-charged", Serdes.String(), PaymentChargedSerde())

    val TREE_NODE: TypedTopic<kotlin.String, TreeNode> = TypedTopic<kotlin.String, TreeNode>("tree-node", Serdes.String(), TreeNodeSerde())
  }
}
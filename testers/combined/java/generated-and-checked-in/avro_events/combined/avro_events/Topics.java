package combined.avro_events;

import com.example.events.Address;
import com.example.events.CustomerOrder;
import com.example.events.DynamicValue;
import com.example.events.Invoice;
import com.example.events.LinkedListNode;
import com.example.events.OrderCancelled;
import com.example.events.OrderEvents;
import com.example.events.OrderPlaced;
import com.example.events.OrderUpdated;
import com.example.events.PaymentCallback;
import com.example.events.PaymentCharged;
import com.example.events.TreeNode;
import com.example.events.common.Money;
import combined.avro_events.serde.AddressSerde;
import combined.avro_events.serde.CustomerOrderSerde;
import combined.avro_events.serde.DynamicValueSerde;
import combined.avro_events.serde.InvoiceSerde;
import combined.avro_events.serde.LinkedListNodeSerde;
import combined.avro_events.serde.MoneySerde;
import combined.avro_events.serde.OrderCancelledSerde;
import combined.avro_events.serde.OrderEventsSerde;
import combined.avro_events.serde.OrderPlacedSerde;
import combined.avro_events.serde.OrderUpdatedSerde;
import combined.avro_events.serde.PaymentCallbackSerde;
import combined.avro_events.serde.PaymentChargedSerde;
import combined.avro_events.serde.TreeNodeSerde;
import org.apache.kafka.common.serialization.Serdes;

/** Type-safe topic binding constants */
public class Topics {
  public static TypedTopic<String, Address> ADDRESS = new TypedTopic<String, Address>("address", Serdes.String(), new AddressSerde());

  public static TypedTopic<String, CustomerOrder> CUSTOMER_ORDER = new TypedTopic<String, CustomerOrder>("customer-order", Serdes.String(), new CustomerOrderSerde());

  public static TypedTopic<String, DynamicValue> DYNAMIC_VALUE = new TypedTopic<String, DynamicValue>("dynamic-value", Serdes.String(), new DynamicValueSerde());

  public static TypedTopic<String, Invoice> INVOICE = new TypedTopic<String, Invoice>("invoice", Serdes.String(), new InvoiceSerde());

  public static TypedTopic<String, LinkedListNode> LINKED_LIST_NODE = new TypedTopic<String, LinkedListNode>("linked-list-node", Serdes.String(), new LinkedListNodeSerde());

  public static TypedTopic<String, Money> MONEY = new TypedTopic<String, Money>("money", Serdes.String(), new MoneySerde());

  public static TypedTopic<String, OrderCancelled> ORDER_CANCELLED = new TypedTopic<String, OrderCancelled>("order-cancelled", Serdes.String(), new OrderCancelledSerde());

  public static TypedTopic<String, OrderEvents> ORDER_EVENTS = new TypedTopic<String, OrderEvents>("order-events", Serdes.String(), new OrderEventsSerde());

  public static TypedTopic<String, OrderPlaced> ORDER_PLACED = new TypedTopic<String, OrderPlaced>("order-placed", Serdes.String(), new OrderPlacedSerde());

  public static TypedTopic<String, OrderUpdated> ORDER_UPDATED = new TypedTopic<String, OrderUpdated>("order-updated", Serdes.String(), new OrderUpdatedSerde());

  public static TypedTopic<String, PaymentCallback> PAYMENT_CALLBACK = new TypedTopic<String, PaymentCallback>("payment-callback", Serdes.String(), new PaymentCallbackSerde());

  public static TypedTopic<String, PaymentCharged> PAYMENT_CHARGED = new TypedTopic<String, PaymentCharged>("payment-charged", Serdes.String(), new PaymentChargedSerde());

  public static TypedTopic<String, TreeNode> TREE_NODE = new TypedTopic<String, TreeNode>("tree-node", Serdes.String(), new TreeNodeSerde());
}
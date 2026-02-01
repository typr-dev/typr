package com.example.events;

import static org.junit.Assert.*;

import com.example.events.common.Money;
import com.example.events.precisetypes.Decimal10_2;
import com.example.events.precisetypes.Decimal18_4;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.Test;

public class JsonSerializationTest {

  private final ObjectMapper mapper =
      new ObjectMapper().registerModule(new Jdk8Module()).registerModule(new JavaTimeModule());

  @Test
  public void testCustomerOrderRoundTrip() throws Exception {
    CustomerOrder order =
        new CustomerOrder(
            OrderId.valueOf("order-123"),
            CustomerId.valueOf(456L),
            Optional.of(Email.valueOf("test@example.com")),
            1000L);

    String json = mapper.writeValueAsString(order);
    CustomerOrder deserialized = mapper.readValue(json, CustomerOrder.class);

    assertEquals(order.orderId().unwrap(), deserialized.orderId().unwrap());
    assertEquals(order.customerId().unwrap(), deserialized.customerId().unwrap());
    assertEquals(order.email().get().unwrap(), deserialized.email().get().unwrap());
    assertEquals(order.amount(), deserialized.amount());
  }

  @Test
  public void testOrderPlacedRoundTrip() throws Exception {
    OrderPlaced event =
        new OrderPlaced(
            UUID.randomUUID(),
            123L,
            Decimal10_2.unsafeForce(new BigDecimal("99.99")),
            Instant.parse("2024-01-15T10:30:00Z"),
            List.of("item1", "item2"),
            Optional.of("123 Main St"));

    String json = mapper.writeValueAsString(event);
    OrderPlaced deserialized = mapper.readValue(json, OrderPlaced.class);

    assertEquals(event.orderId(), deserialized.orderId());
    assertEquals(event.customerId(), deserialized.customerId());
    assertEquals(
        0, event.totalAmount().decimalValue().compareTo(deserialized.totalAmount().decimalValue()));
    assertEquals(event.items(), deserialized.items());
    assertEquals(event.placedAt(), deserialized.placedAt());
    assertEquals(event.shippingAddress(), deserialized.shippingAddress());
  }

  @Test
  public void testAddressRoundTrip() throws Exception {
    Address address = new Address("123 Main St", "Springfield", "62701", "US");

    String json = mapper.writeValueAsString(address);
    Address deserialized = mapper.readValue(json, Address.class);

    assertEquals(address.street(), deserialized.street());
    assertEquals(address.city(), deserialized.city());
    assertEquals(address.postalCode(), deserialized.postalCode());
    assertEquals(address.country(), deserialized.country());
  }

  @Test
  public void testMoneyRoundTrip() throws Exception {
    Money money = new Money(Decimal18_4.unsafeForce(new BigDecimal("123.45")), "USD");

    String json = mapper.writeValueAsString(money);
    Money deserialized = mapper.readValue(json, Money.class);

    assertEquals(0, money.amount().decimalValue().compareTo(deserialized.amount().decimalValue()));
    assertEquals(money.currency(), deserialized.currency());
  }

  @Test
  public void testEnumRoundTrip() throws Exception {
    OrderStatus status = OrderStatus.SHIPPED;

    String json = mapper.writeValueAsString(status);
    OrderStatus deserialized = mapper.readValue(json, OrderStatus.class);

    assertEquals(status, deserialized);
  }

  @Test
  public void testInvoiceWithNestedRecords() throws Exception {
    Invoice invoice =
        new Invoice(
            UUID.randomUUID(),
            456L,
            new Money(Decimal18_4.unsafeForce(new BigDecimal("500.00")), "EUR"),
            Instant.parse("2024-01-15T10:30:00Z"));

    String json = mapper.writeValueAsString(invoice);
    Invoice deserialized = mapper.readValue(json, Invoice.class);

    assertEquals(invoice.invoiceId(), deserialized.invoiceId());
    assertEquals(invoice.customerId(), deserialized.customerId());
    assertEquals(
        0,
        invoice
            .total()
            .amount()
            .decimalValue()
            .compareTo(deserialized.total().amount().decimalValue()));
    assertEquals(invoice.total().currency(), deserialized.total().currency());
    assertEquals(invoice.issuedAt(), deserialized.issuedAt());
  }

  @Test
  public void testTreeNodeRecursive() throws Exception {
    TreeNode leaf = new TreeNode("leaf", Optional.empty(), Optional.empty());
    TreeNode root = new TreeNode("root", Optional.of(leaf), Optional.empty());

    String json = mapper.writeValueAsString(root);
    TreeNode deserialized = mapper.readValue(json, TreeNode.class);

    assertEquals(root.value(), deserialized.value());
    assertTrue(deserialized.left().isPresent());
    assertEquals("leaf", deserialized.left().get().value());
    assertTrue(deserialized.right().isEmpty());
  }

  @Test
  public void testLinkedListNode() throws Exception {
    LinkedListNode tail = new LinkedListNode(3, Optional.empty());
    LinkedListNode middle = new LinkedListNode(2, Optional.of(tail));
    LinkedListNode head = new LinkedListNode(1, Optional.of(middle));

    String json = mapper.writeValueAsString(head);
    LinkedListNode deserialized = mapper.readValue(json, LinkedListNode.class);

    assertEquals(Integer.valueOf(1), deserialized.value());
    assertEquals(Integer.valueOf(2), deserialized.next().get().value());
    assertEquals(Integer.valueOf(3), deserialized.next().get().next().get().value());
    assertTrue(deserialized.next().get().next().get().next().isEmpty());
  }
}

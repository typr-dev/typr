package com.example.grpc;

import java.util.Iterator;

/** Clean service interface for OrderService gRPC service */
public interface OrderService {
  GetCustomerResponse getCustomer(GetCustomerRequest request);

  CreateOrderResponse createOrder(CreateOrderRequest request);

  Iterator<OrderUpdate> listOrders(ListOrdersRequest request);

  OrderSummary submitOrders(Iterator<CreateOrderRequest> requests);

  Iterator<ChatMessage> chat(Iterator<ChatMessage> requests);
}

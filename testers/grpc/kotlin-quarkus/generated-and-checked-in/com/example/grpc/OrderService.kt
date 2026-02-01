package com.example.grpc

import io.smallrye.mutiny.Uni
import kotlin.collections.Iterator

/** Clean service interface for OrderService gRPC service */
interface OrderService {
  abstract fun chat(requests: Iterator<ChatMessage>): Iterator<ChatMessage>

  abstract fun createOrder(request: CreateOrderRequest): Uni<CreateOrderResponse>

  abstract fun getCustomer(request: GetCustomerRequest): Uni<GetCustomerResponse>

  abstract fun listOrders(request: ListOrdersRequest): Iterator<OrderUpdate>

  abstract fun submitOrders(requests: Iterator<CreateOrderRequest>): Uni<OrderSummary>
}
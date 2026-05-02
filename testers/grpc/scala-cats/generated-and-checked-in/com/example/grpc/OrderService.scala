package com.example.grpc

import cats.effect.IO

/** Clean service interface for OrderService gRPC service */
trait OrderService {
  def getCustomer(request: GetCustomerRequest): IO[GetCustomerResponse]

  def createOrder(request: CreateOrderRequest): IO[CreateOrderResponse]

  def listOrders(request: ListOrdersRequest): java.util.Iterator[OrderUpdate]

  def submitOrders(requests: java.util.Iterator[CreateOrderRequest]): IO[OrderSummary]

  def chat(requests: java.util.Iterator[ChatMessage]): java.util.Iterator[ChatMessage]
}
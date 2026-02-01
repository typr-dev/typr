package com.example.grpc



/** Clean service interface for OrderService gRPC service */
trait OrderService {
  def getCustomer(request: GetCustomerRequest): GetCustomerResponse

  def createOrder(request: CreateOrderRequest): CreateOrderResponse

  def listOrders(request: ListOrdersRequest): java.util.Iterator[OrderUpdate]

  def submitOrders(requests: java.util.Iterator[CreateOrderRequest]): OrderSummary

  def chat(requests: java.util.Iterator[ChatMessage]): java.util.Iterator[ChatMessage]
}
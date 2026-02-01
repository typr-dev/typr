package com.example.grpc

import cats.effect.IO
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.MethodDescriptor
import io.grpc.MethodDescriptor.MethodType
import io.grpc.stub.ClientCalls
import java.lang.UnsupportedOperationException

/** gRPC client wrapper for OrderService - wraps Channel with clean types */
class OrderServiceClient(val channel: Channel) extends OrderService {
  override def getCustomer(request: GetCustomerRequest): IO[GetCustomerResponse] = {
    return IO.delay((() => ClientCalls.blockingUnaryCall(channel, OrderServiceClient.GET_CUSTOMER, CallOptions.DEFAULT, request)).apply())
  }

  override def createOrder(request: CreateOrderRequest): IO[CreateOrderResponse] = {
    return IO.delay((() => ClientCalls.blockingUnaryCall(channel, OrderServiceClient.CREATE_ORDER, CallOptions.DEFAULT, request)).apply())
  }

  override def listOrders(request: ListOrdersRequest): java.util.Iterator[OrderUpdate] = {
    return ClientCalls.blockingServerStreamingCall(channel, OrderServiceClient.LIST_ORDERS, CallOptions.DEFAULT, request)
  }

  override def submitOrders(requests: java.util.Iterator[CreateOrderRequest]): IO[OrderSummary] = {
    throw new UnsupportedOperationException("Client streaming not yet implemented in client wrapper")
  }

  override def chat(requests: java.util.Iterator[ChatMessage]): java.util.Iterator[ChatMessage] = {
    throw new UnsupportedOperationException("Bidi streaming not yet implemented in client wrapper")
  }
}

object OrderServiceClient {
  val CHAT: MethodDescriptor[ChatMessage, ChatMessage] = MethodDescriptor.newBuilder(ChatMessage.marshaller, ChatMessage.marshaller).setType(MethodType.BIDI_STREAMING).setFullMethodName("testgrpc.OrderService/Chat").build()

  val CREATE_ORDER: MethodDescriptor[CreateOrderRequest, CreateOrderResponse] = MethodDescriptor.newBuilder(CreateOrderRequest.marshaller, CreateOrderResponse.marshaller).setType(MethodType.UNARY).setFullMethodName("testgrpc.OrderService/CreateOrder").build()

  val GET_CUSTOMER: MethodDescriptor[GetCustomerRequest, GetCustomerResponse] = MethodDescriptor.newBuilder(GetCustomerRequest.marshaller, GetCustomerResponse.marshaller).setType(MethodType.UNARY).setFullMethodName("testgrpc.OrderService/GetCustomer").build()

  val LIST_ORDERS: MethodDescriptor[ListOrdersRequest, OrderUpdate] = MethodDescriptor.newBuilder(ListOrdersRequest.marshaller, OrderUpdate.marshaller).setType(MethodType.SERVER_STREAMING).setFullMethodName("testgrpc.OrderService/ListOrders").build()

  val SUBMIT_ORDERS: MethodDescriptor[CreateOrderRequest, OrderSummary] = MethodDescriptor.newBuilder(CreateOrderRequest.marshaller, OrderSummary.marshaller).setType(MethodType.CLIENT_STREAMING).setFullMethodName("testgrpc.OrderService/SubmitOrders").build()
}
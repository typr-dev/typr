package com.example.grpc

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.MethodDescriptor
import io.grpc.MethodDescriptor.MethodType
import io.grpc.stub.ClientCalls
import java.lang.UnsupportedOperationException
import kotlin.collections.Iterator

/** gRPC client wrapper for OrderService - wraps Channel with clean types */
data class OrderServiceClient(val channel: Channel) : OrderService {
  override fun chat(requests: Iterator<ChatMessage>): Iterator<ChatMessage> {
    throw UnsupportedOperationException("Bidi streaming not yet implemented in client wrapper")
  }

  override fun createOrder(request: CreateOrderRequest): CreateOrderResponse {
    return ClientCalls.blockingUnaryCall(channel, OrderServiceClient.CREATE_ORDER, CallOptions.DEFAULT, request)
  }

  override fun getCustomer(request: GetCustomerRequest): GetCustomerResponse {
    return ClientCalls.blockingUnaryCall(channel, OrderServiceClient.GET_CUSTOMER, CallOptions.DEFAULT, request)
  }

  override fun listOrders(request: ListOrdersRequest): Iterator<OrderUpdate> {
    return ClientCalls.blockingServerStreamingCall(channel, OrderServiceClient.LIST_ORDERS, CallOptions.DEFAULT, request)
  }

  override fun submitOrders(requests: Iterator<CreateOrderRequest>): OrderSummary {
    throw UnsupportedOperationException("Client streaming not yet implemented in client wrapper")
  }

  companion object {
    val CHAT: MethodDescriptor<ChatMessage, ChatMessage> = MethodDescriptor.newBuilder(ChatMessage.MARSHALLER, ChatMessage.MARSHALLER).setType(MethodType.BIDI_STREAMING).setFullMethodName("testgrpc.OrderService/Chat").build()

    val CREATE_ORDER: MethodDescriptor<CreateOrderRequest, CreateOrderResponse> = MethodDescriptor.newBuilder(CreateOrderRequest.MARSHALLER, CreateOrderResponse.MARSHALLER).setType(MethodType.UNARY).setFullMethodName("testgrpc.OrderService/CreateOrder").build()

    val GET_CUSTOMER: MethodDescriptor<GetCustomerRequest, GetCustomerResponse> = MethodDescriptor.newBuilder(GetCustomerRequest.MARSHALLER, GetCustomerResponse.MARSHALLER).setType(MethodType.UNARY).setFullMethodName("testgrpc.OrderService/GetCustomer").build()

    val LIST_ORDERS: MethodDescriptor<ListOrdersRequest, OrderUpdate> = MethodDescriptor.newBuilder(ListOrdersRequest.MARSHALLER, OrderUpdate.MARSHALLER).setType(MethodType.SERVER_STREAMING).setFullMethodName("testgrpc.OrderService/ListOrders").build()

    val SUBMIT_ORDERS: MethodDescriptor<CreateOrderRequest, OrderSummary> = MethodDescriptor.newBuilder(CreateOrderRequest.MARSHALLER, OrderSummary.MARSHALLER).setType(MethodType.CLIENT_STREAMING).setFullMethodName("testgrpc.OrderService/SubmitOrders").build()
  }
}
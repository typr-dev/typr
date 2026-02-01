package com.example.grpc

import io.grpc.BindableService
import io.grpc.MethodDescriptor
import io.grpc.MethodDescriptor.MethodType
import io.grpc.ServerServiceDefinition
import io.grpc.stub.ServerCalls
import java.lang.UnsupportedOperationException

/** gRPC server adapter for OrderService - delegates to clean service interface */
data class OrderServiceServer(val delegate: OrderService) : BindableService {
  override fun bindService(): ServerServiceDefinition {
    return ServerServiceDefinition.builder("testgrpc.OrderService").addMethod(OrderServiceServer.GET_CUSTOMER, ServerCalls.asyncUnaryCall({ request, responseObserver -> responseObserver.onNext(delegate.getCustomer(request))
    responseObserver.onCompleted() })).addMethod(OrderServiceServer.CREATE_ORDER, ServerCalls.asyncUnaryCall({ request, responseObserver -> responseObserver.onNext(delegate.createOrder(request))
    responseObserver.onCompleted() })).addMethod(OrderServiceServer.LIST_ORDERS, ServerCalls.asyncServerStreamingCall({ request, responseObserver -> val results = delegate.listOrders(request)
    while (results.hasNext()) {
      responseObserver.onNext(results.next())
    }
    responseObserver.onCompleted() })).addMethod(OrderServiceServer.SUBMIT_ORDERS, ServerCalls.asyncClientStreamingCall({ responseObserver -> throw UnsupportedOperationException("Client streaming not yet implemented in server adapter") })).addMethod(OrderServiceServer.CHAT, ServerCalls.asyncBidiStreamingCall({ responseObserver -> throw UnsupportedOperationException("Bidi streaming not yet implemented in server adapter") })).build()
  }

  companion object {
    val CHAT: MethodDescriptor<ChatMessage, ChatMessage> = MethodDescriptor.newBuilder(ChatMessage.MARSHALLER, ChatMessage.MARSHALLER).setType(MethodType.BIDI_STREAMING).setFullMethodName("testgrpc.OrderService/Chat").build()

    val CREATE_ORDER: MethodDescriptor<CreateOrderRequest, CreateOrderResponse> = MethodDescriptor.newBuilder(CreateOrderRequest.MARSHALLER, CreateOrderResponse.MARSHALLER).setType(MethodType.UNARY).setFullMethodName("testgrpc.OrderService/CreateOrder").build()

    val GET_CUSTOMER: MethodDescriptor<GetCustomerRequest, GetCustomerResponse> = MethodDescriptor.newBuilder(GetCustomerRequest.MARSHALLER, GetCustomerResponse.MARSHALLER).setType(MethodType.UNARY).setFullMethodName("testgrpc.OrderService/GetCustomer").build()

    val LIST_ORDERS: MethodDescriptor<ListOrdersRequest, OrderUpdate> = MethodDescriptor.newBuilder(ListOrdersRequest.MARSHALLER, OrderUpdate.MARSHALLER).setType(MethodType.SERVER_STREAMING).setFullMethodName("testgrpc.OrderService/ListOrders").build()

    val SUBMIT_ORDERS: MethodDescriptor<CreateOrderRequest, OrderSummary> = MethodDescriptor.newBuilder(CreateOrderRequest.MARSHALLER, OrderSummary.MARSHALLER).setType(MethodType.CLIENT_STREAMING).setFullMethodName("testgrpc.OrderService/SubmitOrders").build()
  }
}
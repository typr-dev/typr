package com.example.grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.stub.ClientCalls;
import io.quarkus.grpc.GrpcClient;
import java.util.Iterator;

/** gRPC client wrapper for OrderService - wraps Channel with clean types */
public class OrderServiceClient implements OrderService {
  Channel channel;

  public OrderServiceClient(@GrpcClient("OrderService") Channel channel) {
    this.channel = channel;
  }

  public static MethodDescriptor<ChatMessage, ChatMessage> CHAT =
      MethodDescriptor.newBuilder(ChatMessage.MARSHALLER, ChatMessage.MARSHALLER)
          .setType(MethodType.BIDI_STREAMING)
          .setFullMethodName("testgrpc.OrderService/Chat")
          .build();

  public static MethodDescriptor<CreateOrderRequest, CreateOrderResponse> CREATE_ORDER =
      MethodDescriptor.newBuilder(CreateOrderRequest.MARSHALLER, CreateOrderResponse.MARSHALLER)
          .setType(MethodType.UNARY)
          .setFullMethodName("testgrpc.OrderService/CreateOrder")
          .build();

  public static MethodDescriptor<GetCustomerRequest, GetCustomerResponse> GET_CUSTOMER =
      MethodDescriptor.newBuilder(GetCustomerRequest.MARSHALLER, GetCustomerResponse.MARSHALLER)
          .setType(MethodType.UNARY)
          .setFullMethodName("testgrpc.OrderService/GetCustomer")
          .build();

  public static MethodDescriptor<ListOrdersRequest, OrderUpdate> LIST_ORDERS =
      MethodDescriptor.newBuilder(ListOrdersRequest.MARSHALLER, OrderUpdate.MARSHALLER)
          .setType(MethodType.SERVER_STREAMING)
          .setFullMethodName("testgrpc.OrderService/ListOrders")
          .build();

  public static MethodDescriptor<CreateOrderRequest, OrderSummary> SUBMIT_ORDERS =
      MethodDescriptor.newBuilder(CreateOrderRequest.MARSHALLER, OrderSummary.MARSHALLER)
          .setType(MethodType.CLIENT_STREAMING)
          .setFullMethodName("testgrpc.OrderService/SubmitOrders")
          .build();

  @Override
  public GetCustomerResponse getCustomer(GetCustomerRequest request) {
    return ClientCalls.blockingUnaryCall(
        channel, OrderServiceClient.GET_CUSTOMER, CallOptions.DEFAULT, request);
  }

  @Override
  public CreateOrderResponse createOrder(CreateOrderRequest request) {
    return ClientCalls.blockingUnaryCall(
        channel, OrderServiceClient.CREATE_ORDER, CallOptions.DEFAULT, request);
  }

  @Override
  public Iterator<OrderUpdate> listOrders(ListOrdersRequest request) {
    return ClientCalls.blockingServerStreamingCall(
        channel, OrderServiceClient.LIST_ORDERS, CallOptions.DEFAULT, request);
  }

  @Override
  public OrderSummary submitOrders(Iterator<CreateOrderRequest> requests) {
    throw new UnsupportedOperationException(
        "Client streaming not yet implemented in client wrapper");
  }

  @Override
  public Iterator<ChatMessage> chat(Iterator<ChatMessage> requests) {
    throw new UnsupportedOperationException("Bidi streaming not yet implemented in client wrapper");
  }
}

package com.example.grpc;

import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.ServerCalls;

/** gRPC server adapter for OrderService - delegates to clean service interface */
public class OrderServiceServer implements BindableService {
  OrderService delegate;

  public OrderServiceServer(OrderService delegate) {
    this.delegate = delegate;
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
  public ServerServiceDefinition bindService() {
    return ServerServiceDefinition.builder("testgrpc.OrderService")
        .addMethod(
            OrderServiceServer.GET_CUSTOMER,
            ServerCalls.asyncUnaryCall(
                (request, responseObserver) -> {
                  responseObserver.onNext(delegate.getCustomer(request));
                  responseObserver.onCompleted();
                }))
        .addMethod(
            OrderServiceServer.CREATE_ORDER,
            ServerCalls.asyncUnaryCall(
                (request, responseObserver) -> {
                  responseObserver.onNext(delegate.createOrder(request));
                  responseObserver.onCompleted();
                }))
        .addMethod(
            OrderServiceServer.LIST_ORDERS,
            ServerCalls.asyncServerStreamingCall(
                (request, responseObserver) -> {
                  var results = delegate.listOrders(request);
                  while (results.hasNext()) {
                    responseObserver.onNext(results.next());
                  }
                  ;
                  responseObserver.onCompleted();
                }))
        .addMethod(
            OrderServiceServer.SUBMIT_ORDERS,
            ServerCalls.asyncClientStreamingCall(
                responseObserver -> {
                  throw new UnsupportedOperationException(
                      "Client streaming not yet implemented in server adapter");
                }))
        .addMethod(
            OrderServiceServer.CHAT,
            ServerCalls.asyncBidiStreamingCall(
                responseObserver -> {
                  throw new UnsupportedOperationException(
                      "Bidi streaming not yet implemented in server adapter");
                }))
        .build();
  }
}

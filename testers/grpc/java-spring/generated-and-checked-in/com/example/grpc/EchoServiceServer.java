package com.example.grpc;

import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.ServerCalls;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.stereotype.Service;

/** gRPC server adapter for EchoService - delegates to clean service interface */
@GrpcService
@Service
public class EchoServiceServer implements BindableService {
  EchoService delegate;

  public EchoServiceServer(EchoService delegate) {
    this.delegate = delegate;
  }

  public static MethodDescriptor<Customer, Customer> ECHO_CUSTOMER =
      MethodDescriptor.newBuilder(Customer.MARSHALLER, Customer.MARSHALLER)
          .setType(MethodType.UNARY)
          .setFullMethodName("testgrpc.EchoService/EchoCustomer")
          .build();

  public static MethodDescriptor<Inventory, Inventory> ECHO_INVENTORY =
      MethodDescriptor.newBuilder(Inventory.MARSHALLER, Inventory.MARSHALLER)
          .setType(MethodType.UNARY)
          .setFullMethodName("testgrpc.EchoService/EchoInventory")
          .build();

  public static MethodDescriptor<Notification, Notification> ECHO_NOTIFICATION =
      MethodDescriptor.newBuilder(Notification.MARSHALLER, Notification.MARSHALLER)
          .setType(MethodType.UNARY)
          .setFullMethodName("testgrpc.EchoService/EchoNotification")
          .build();

  public static MethodDescriptor<OptionalFields, OptionalFields> ECHO_OPTIONAL_FIELDS =
      MethodDescriptor.newBuilder(OptionalFields.MARSHALLER, OptionalFields.MARSHALLER)
          .setType(MethodType.UNARY)
          .setFullMethodName("testgrpc.EchoService/EchoOptionalFields")
          .build();

  public static MethodDescriptor<Order, Order> ECHO_ORDER =
      MethodDescriptor.newBuilder(Order.MARSHALLER, Order.MARSHALLER)
          .setType(MethodType.UNARY)
          .setFullMethodName("testgrpc.EchoService/EchoOrder")
          .build();

  public static MethodDescriptor<Outer, Outer> ECHO_OUTER =
      MethodDescriptor.newBuilder(Outer.MARSHALLER, Outer.MARSHALLER)
          .setType(MethodType.UNARY)
          .setFullMethodName("testgrpc.EchoService/EchoOuter")
          .build();

  public static MethodDescriptor<PaymentMethod, PaymentMethod> ECHO_PAYMENT_METHOD =
      MethodDescriptor.newBuilder(PaymentMethod.MARSHALLER, PaymentMethod.MARSHALLER)
          .setType(MethodType.UNARY)
          .setFullMethodName("testgrpc.EchoService/EchoPaymentMethod")
          .build();

  public static MethodDescriptor<ScalarTypes, ScalarTypes> ECHO_SCALAR_TYPES =
      MethodDescriptor.newBuilder(ScalarTypes.MARSHALLER, ScalarTypes.MARSHALLER)
          .setType(MethodType.UNARY)
          .setFullMethodName("testgrpc.EchoService/EchoScalarTypes")
          .build();

  public static MethodDescriptor<WellKnownTypesMessage, WellKnownTypesMessage>
      ECHO_WELL_KNOWN_TYPES =
          MethodDescriptor.newBuilder(
                  WellKnownTypesMessage.MARSHALLER, WellKnownTypesMessage.MARSHALLER)
              .setType(MethodType.UNARY)
              .setFullMethodName("testgrpc.EchoService/EchoWellKnownTypes")
              .build();

  @Override
  public ServerServiceDefinition bindService() {
    return ServerServiceDefinition.builder("testgrpc.EchoService")
        .addMethod(
            EchoServiceServer.ECHO_SCALAR_TYPES,
            ServerCalls.asyncUnaryCall(
                (request, responseObserver) -> {
                  responseObserver.onNext(delegate.echoScalarTypes(request));
                  responseObserver.onCompleted();
                }))
        .addMethod(
            EchoServiceServer.ECHO_CUSTOMER,
            ServerCalls.asyncUnaryCall(
                (request, responseObserver) -> {
                  responseObserver.onNext(delegate.echoCustomer(request));
                  responseObserver.onCompleted();
                }))
        .addMethod(
            EchoServiceServer.ECHO_ORDER,
            ServerCalls.asyncUnaryCall(
                (request, responseObserver) -> {
                  responseObserver.onNext(delegate.echoOrder(request));
                  responseObserver.onCompleted();
                }))
        .addMethod(
            EchoServiceServer.ECHO_INVENTORY,
            ServerCalls.asyncUnaryCall(
                (request, responseObserver) -> {
                  responseObserver.onNext(delegate.echoInventory(request));
                  responseObserver.onCompleted();
                }))
        .addMethod(
            EchoServiceServer.ECHO_OUTER,
            ServerCalls.asyncUnaryCall(
                (request, responseObserver) -> {
                  responseObserver.onNext(delegate.echoOuter(request));
                  responseObserver.onCompleted();
                }))
        .addMethod(
            EchoServiceServer.ECHO_OPTIONAL_FIELDS,
            ServerCalls.asyncUnaryCall(
                (request, responseObserver) -> {
                  responseObserver.onNext(delegate.echoOptionalFields(request));
                  responseObserver.onCompleted();
                }))
        .addMethod(
            EchoServiceServer.ECHO_WELL_KNOWN_TYPES,
            ServerCalls.asyncUnaryCall(
                (request, responseObserver) -> {
                  responseObserver.onNext(delegate.echoWellKnownTypes(request));
                  responseObserver.onCompleted();
                }))
        .addMethod(
            EchoServiceServer.ECHO_PAYMENT_METHOD,
            ServerCalls.asyncUnaryCall(
                (request, responseObserver) -> {
                  responseObserver.onNext(delegate.echoPaymentMethod(request));
                  responseObserver.onCompleted();
                }))
        .addMethod(
            EchoServiceServer.ECHO_NOTIFICATION,
            ServerCalls.asyncUnaryCall(
                (request, responseObserver) -> {
                  responseObserver.onNext(delegate.echoNotification(request));
                  responseObserver.onCompleted();
                }))
        .build();
  }
}

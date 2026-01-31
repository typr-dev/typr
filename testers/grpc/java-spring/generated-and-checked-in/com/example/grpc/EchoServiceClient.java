package com.example.grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.stub.ClientCalls;

/** gRPC client wrapper for EchoService - wraps Channel with clean types */
public class EchoServiceClient implements EchoService {
  Channel channel;

  public EchoServiceClient(Channel channel) {
    this.channel = channel;
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
  public ScalarTypes echoScalarTypes(ScalarTypes request) {
    return ClientCalls.blockingUnaryCall(
        channel, EchoServiceClient.ECHO_SCALAR_TYPES, CallOptions.DEFAULT, request);
  }

  @Override
  public Customer echoCustomer(Customer request) {
    return ClientCalls.blockingUnaryCall(
        channel, EchoServiceClient.ECHO_CUSTOMER, CallOptions.DEFAULT, request);
  }

  @Override
  public Order echoOrder(Order request) {
    return ClientCalls.blockingUnaryCall(
        channel, EchoServiceClient.ECHO_ORDER, CallOptions.DEFAULT, request);
  }

  @Override
  public Inventory echoInventory(Inventory request) {
    return ClientCalls.blockingUnaryCall(
        channel, EchoServiceClient.ECHO_INVENTORY, CallOptions.DEFAULT, request);
  }

  @Override
  public Outer echoOuter(Outer request) {
    return ClientCalls.blockingUnaryCall(
        channel, EchoServiceClient.ECHO_OUTER, CallOptions.DEFAULT, request);
  }

  @Override
  public OptionalFields echoOptionalFields(OptionalFields request) {
    return ClientCalls.blockingUnaryCall(
        channel, EchoServiceClient.ECHO_OPTIONAL_FIELDS, CallOptions.DEFAULT, request);
  }

  @Override
  public WellKnownTypesMessage echoWellKnownTypes(WellKnownTypesMessage request) {
    return ClientCalls.blockingUnaryCall(
        channel, EchoServiceClient.ECHO_WELL_KNOWN_TYPES, CallOptions.DEFAULT, request);
  }

  @Override
  public PaymentMethod echoPaymentMethod(PaymentMethod request) {
    return ClientCalls.blockingUnaryCall(
        channel, EchoServiceClient.ECHO_PAYMENT_METHOD, CallOptions.DEFAULT, request);
  }

  @Override
  public Notification echoNotification(Notification request) {
    return ClientCalls.blockingUnaryCall(
        channel, EchoServiceClient.ECHO_NOTIFICATION, CallOptions.DEFAULT, request);
  }
}

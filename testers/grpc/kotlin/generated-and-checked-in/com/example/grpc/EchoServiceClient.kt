package com.example.grpc

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.MethodDescriptor
import io.grpc.MethodDescriptor.MethodType
import io.grpc.stub.ClientCalls

/** gRPC client wrapper for EchoService - wraps Channel with clean types */
data class EchoServiceClient(val channel: Channel) : EchoService {
  override fun echoCustomer(request: Customer): Customer {
    return ClientCalls.blockingUnaryCall(channel, EchoServiceClient.ECHO_CUSTOMER, CallOptions.DEFAULT, request)
  }

  override fun echoInventory(request: Inventory): Inventory {
    return ClientCalls.blockingUnaryCall(channel, EchoServiceClient.ECHO_INVENTORY, CallOptions.DEFAULT, request)
  }

  override fun echoNotification(request: Notification): Notification {
    return ClientCalls.blockingUnaryCall(channel, EchoServiceClient.ECHO_NOTIFICATION, CallOptions.DEFAULT, request)
  }

  override fun echoOptionalFields(request: OptionalFields): OptionalFields {
    return ClientCalls.blockingUnaryCall(channel, EchoServiceClient.ECHO_OPTIONAL_FIELDS, CallOptions.DEFAULT, request)
  }

  override fun echoOrder(request: Order): Order {
    return ClientCalls.blockingUnaryCall(channel, EchoServiceClient.ECHO_ORDER, CallOptions.DEFAULT, request)
  }

  override fun echoOuter(request: Outer): Outer {
    return ClientCalls.blockingUnaryCall(channel, EchoServiceClient.ECHO_OUTER, CallOptions.DEFAULT, request)
  }

  override fun echoPaymentMethod(request: PaymentMethod): PaymentMethod {
    return ClientCalls.blockingUnaryCall(channel, EchoServiceClient.ECHO_PAYMENT_METHOD, CallOptions.DEFAULT, request)
  }

  override fun echoScalarTypes(request: ScalarTypes): ScalarTypes {
    return ClientCalls.blockingUnaryCall(channel, EchoServiceClient.ECHO_SCALAR_TYPES, CallOptions.DEFAULT, request)
  }

  override fun echoWellKnownTypes(request: WellKnownTypesMessage): WellKnownTypesMessage {
    return ClientCalls.blockingUnaryCall(channel, EchoServiceClient.ECHO_WELL_KNOWN_TYPES, CallOptions.DEFAULT, request)
  }

  companion object {
    val ECHO_CUSTOMER: MethodDescriptor<Customer, Customer> = MethodDescriptor.newBuilder(Customer.MARSHALLER, Customer.MARSHALLER).setType(MethodType.UNARY).setFullMethodName("testgrpc.EchoService/EchoCustomer").build()

    val ECHO_INVENTORY: MethodDescriptor<Inventory, Inventory> = MethodDescriptor.newBuilder(Inventory.MARSHALLER, Inventory.MARSHALLER).setType(MethodType.UNARY).setFullMethodName("testgrpc.EchoService/EchoInventory").build()

    val ECHO_NOTIFICATION: MethodDescriptor<Notification, Notification> = MethodDescriptor.newBuilder(Notification.MARSHALLER, Notification.MARSHALLER).setType(MethodType.UNARY).setFullMethodName("testgrpc.EchoService/EchoNotification").build()

    val ECHO_OPTIONAL_FIELDS: MethodDescriptor<OptionalFields, OptionalFields> = MethodDescriptor.newBuilder(OptionalFields.MARSHALLER, OptionalFields.MARSHALLER).setType(MethodType.UNARY).setFullMethodName("testgrpc.EchoService/EchoOptionalFields").build()

    val ECHO_ORDER: MethodDescriptor<Order, Order> = MethodDescriptor.newBuilder(Order.MARSHALLER, Order.MARSHALLER).setType(MethodType.UNARY).setFullMethodName("testgrpc.EchoService/EchoOrder").build()

    val ECHO_OUTER: MethodDescriptor<Outer, Outer> = MethodDescriptor.newBuilder(Outer.MARSHALLER, Outer.MARSHALLER).setType(MethodType.UNARY).setFullMethodName("testgrpc.EchoService/EchoOuter").build()

    val ECHO_PAYMENT_METHOD: MethodDescriptor<PaymentMethod, PaymentMethod> = MethodDescriptor.newBuilder(PaymentMethod.MARSHALLER, PaymentMethod.MARSHALLER).setType(MethodType.UNARY).setFullMethodName("testgrpc.EchoService/EchoPaymentMethod").build()

    val ECHO_SCALAR_TYPES: MethodDescriptor<ScalarTypes, ScalarTypes> = MethodDescriptor.newBuilder(ScalarTypes.MARSHALLER, ScalarTypes.MARSHALLER).setType(MethodType.UNARY).setFullMethodName("testgrpc.EchoService/EchoScalarTypes").build()

    val ECHO_WELL_KNOWN_TYPES: MethodDescriptor<WellKnownTypesMessage, WellKnownTypesMessage> = MethodDescriptor.newBuilder(WellKnownTypesMessage.MARSHALLER, WellKnownTypesMessage.MARSHALLER).setType(MethodType.UNARY).setFullMethodName("testgrpc.EchoService/EchoWellKnownTypes").build()
  }
}
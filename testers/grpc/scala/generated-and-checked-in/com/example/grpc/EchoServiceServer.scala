package com.example.grpc

import io.grpc.BindableService
import io.grpc.MethodDescriptor
import io.grpc.MethodDescriptor.MethodType
import io.grpc.ServerServiceDefinition
import io.grpc.stub.ServerCalls

/** gRPC server adapter for EchoService - delegates to clean service interface */
class EchoServiceServer(val delegate: EchoService) extends BindableService {
  override def bindService: ServerServiceDefinition = {
    return ServerServiceDefinition.builder("testgrpc.EchoService").addMethod(EchoServiceServer.ECHO_SCALAR_TYPES, ServerCalls.asyncUnaryCall((request, responseObserver) => { responseObserver.onNext(delegate.echoScalarTypes(request)); responseObserver.onCompleted() })).addMethod(EchoServiceServer.ECHO_CUSTOMER, ServerCalls.asyncUnaryCall((request, responseObserver) => { responseObserver.onNext(delegate.echoCustomer(request)); responseObserver.onCompleted() })).addMethod(EchoServiceServer.ECHO_ORDER, ServerCalls.asyncUnaryCall((request, responseObserver) => { responseObserver.onNext(delegate.echoOrder(request)); responseObserver.onCompleted() })).addMethod(EchoServiceServer.ECHO_INVENTORY, ServerCalls.asyncUnaryCall((request, responseObserver) => { responseObserver.onNext(delegate.echoInventory(request)); responseObserver.onCompleted() })).addMethod(EchoServiceServer.ECHO_OUTER, ServerCalls.asyncUnaryCall((request, responseObserver) => { responseObserver.onNext(delegate.echoOuter(request)); responseObserver.onCompleted() })).addMethod(EchoServiceServer.ECHO_OPTIONAL_FIELDS, ServerCalls.asyncUnaryCall((request, responseObserver) => { responseObserver.onNext(delegate.echoOptionalFields(request)); responseObserver.onCompleted() })).addMethod(EchoServiceServer.ECHO_WELL_KNOWN_TYPES, ServerCalls.asyncUnaryCall((request, responseObserver) => { responseObserver.onNext(delegate.echoWellKnownTypes(request)); responseObserver.onCompleted() })).addMethod(EchoServiceServer.ECHO_PAYMENT_METHOD, ServerCalls.asyncUnaryCall((request, responseObserver) => { responseObserver.onNext(delegate.echoPaymentMethod(request)); responseObserver.onCompleted() })).addMethod(EchoServiceServer.ECHO_NOTIFICATION, ServerCalls.asyncUnaryCall((request, responseObserver) => { responseObserver.onNext(delegate.echoNotification(request)); responseObserver.onCompleted() })).build()
  }
}

object EchoServiceServer {
  val ECHO_CUSTOMER: MethodDescriptor[Customer, Customer] = MethodDescriptor.newBuilder(Customer.marshaller, Customer.marshaller).setType(MethodType.UNARY).setFullMethodName("testgrpc.EchoService/EchoCustomer").build()

  val ECHO_INVENTORY: MethodDescriptor[Inventory, Inventory] = MethodDescriptor.newBuilder(Inventory.marshaller, Inventory.marshaller).setType(MethodType.UNARY).setFullMethodName("testgrpc.EchoService/EchoInventory").build()

  val ECHO_NOTIFICATION: MethodDescriptor[Notification, Notification] = MethodDescriptor.newBuilder(Notification.marshaller, Notification.marshaller).setType(MethodType.UNARY).setFullMethodName("testgrpc.EchoService/EchoNotification").build()

  val ECHO_OPTIONAL_FIELDS: MethodDescriptor[OptionalFields, OptionalFields] = MethodDescriptor.newBuilder(OptionalFields.marshaller, OptionalFields.marshaller).setType(MethodType.UNARY).setFullMethodName("testgrpc.EchoService/EchoOptionalFields").build()

  val ECHO_ORDER: MethodDescriptor[Order, Order] = MethodDescriptor.newBuilder(Order.marshaller, Order.marshaller).setType(MethodType.UNARY).setFullMethodName("testgrpc.EchoService/EchoOrder").build()

  val ECHO_OUTER: MethodDescriptor[Outer, Outer] = MethodDescriptor.newBuilder(Outer.marshaller, Outer.marshaller).setType(MethodType.UNARY).setFullMethodName("testgrpc.EchoService/EchoOuter").build()

  val ECHO_PAYMENT_METHOD: MethodDescriptor[PaymentMethod, PaymentMethod] = MethodDescriptor.newBuilder(PaymentMethod.marshaller, PaymentMethod.marshaller).setType(MethodType.UNARY).setFullMethodName("testgrpc.EchoService/EchoPaymentMethod").build()

  val ECHO_SCALAR_TYPES: MethodDescriptor[ScalarTypes, ScalarTypes] = MethodDescriptor.newBuilder(ScalarTypes.marshaller, ScalarTypes.marshaller).setType(MethodType.UNARY).setFullMethodName("testgrpc.EchoService/EchoScalarTypes").build()

  val ECHO_WELL_KNOWN_TYPES: MethodDescriptor[WellKnownTypesMessage, WellKnownTypesMessage] = MethodDescriptor.newBuilder(WellKnownTypesMessage.marshaller, WellKnownTypesMessage.marshaller).setType(MethodType.UNARY).setFullMethodName("testgrpc.EchoService/EchoWellKnownTypes").build()
}
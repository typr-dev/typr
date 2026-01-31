package com.example.grpc

import io.grpc.BindableService
import io.grpc.MethodDescriptor
import io.grpc.MethodDescriptor.MethodType
import io.grpc.ServerServiceDefinition
import io.grpc.stub.ServerCalls
import io.quarkus.grpc.GrpcService
import jakarta.inject.Singleton

/** gRPC server adapter for EchoService - delegates to clean service interface */
data class EchoServiceServer(val delegate: EchoService) : BindableService {
  override fun bindService(): ServerServiceDefinition {
    return ServerServiceDefinition.builder("testgrpc.EchoService").addMethod(EchoServiceServer.ECHO_SCALAR_TYPES, ServerCalls.asyncUnaryCall({ request, responseObserver -> delegate.echoScalarTypes(request).subscribe().with({ response -> responseObserver.onNext(response)
    responseObserver.onCompleted() }, { error -> responseObserver.onError(error) }) })).addMethod(EchoServiceServer.ECHO_CUSTOMER, ServerCalls.asyncUnaryCall({ request, responseObserver -> delegate.echoCustomer(request).subscribe().with({ response -> responseObserver.onNext(response)
    responseObserver.onCompleted() }, { error -> responseObserver.onError(error) }) })).addMethod(EchoServiceServer.ECHO_ORDER, ServerCalls.asyncUnaryCall({ request, responseObserver -> delegate.echoOrder(request).subscribe().with({ response -> responseObserver.onNext(response)
    responseObserver.onCompleted() }, { error -> responseObserver.onError(error) }) })).addMethod(EchoServiceServer.ECHO_INVENTORY, ServerCalls.asyncUnaryCall({ request, responseObserver -> delegate.echoInventory(request).subscribe().with({ response -> responseObserver.onNext(response)
    responseObserver.onCompleted() }, { error -> responseObserver.onError(error) }) })).addMethod(EchoServiceServer.ECHO_OUTER, ServerCalls.asyncUnaryCall({ request, responseObserver -> delegate.echoOuter(request).subscribe().with({ response -> responseObserver.onNext(response)
    responseObserver.onCompleted() }, { error -> responseObserver.onError(error) }) })).addMethod(EchoServiceServer.ECHO_OPTIONAL_FIELDS, ServerCalls.asyncUnaryCall({ request, responseObserver -> delegate.echoOptionalFields(request).subscribe().with({ response -> responseObserver.onNext(response)
    responseObserver.onCompleted() }, { error -> responseObserver.onError(error) }) })).addMethod(EchoServiceServer.ECHO_WELL_KNOWN_TYPES, ServerCalls.asyncUnaryCall({ request, responseObserver -> delegate.echoWellKnownTypes(request).subscribe().with({ response -> responseObserver.onNext(response)
    responseObserver.onCompleted() }, { error -> responseObserver.onError(error) }) })).addMethod(EchoServiceServer.ECHO_PAYMENT_METHOD, ServerCalls.asyncUnaryCall({ request, responseObserver -> delegate.echoPaymentMethod(request).subscribe().with({ response -> responseObserver.onNext(response)
    responseObserver.onCompleted() }, { error -> responseObserver.onError(error) }) })).addMethod(EchoServiceServer.ECHO_NOTIFICATION, ServerCalls.asyncUnaryCall({ request, responseObserver -> delegate.echoNotification(request).subscribe().with({ response -> responseObserver.onNext(response)
    responseObserver.onCompleted() }, { error -> responseObserver.onError(error) }) })).build()
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
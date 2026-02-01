package com.example.grpc

import io.smallrye.mutiny.Uni

/** Clean service interface for EchoService gRPC service */
interface EchoService {
  abstract fun echoCustomer(request: Customer): Uni<Customer>

  abstract fun echoInventory(request: Inventory): Uni<Inventory>

  abstract fun echoNotification(request: Notification): Uni<Notification>

  abstract fun echoOptionalFields(request: OptionalFields): Uni<OptionalFields>

  abstract fun echoOrder(request: Order): Uni<Order>

  abstract fun echoOuter(request: Outer): Uni<Outer>

  abstract fun echoPaymentMethod(request: PaymentMethod): Uni<PaymentMethod>

  abstract fun echoScalarTypes(request: ScalarTypes): Uni<ScalarTypes>

  abstract fun echoWellKnownTypes(request: WellKnownTypesMessage): Uni<WellKnownTypesMessage>
}
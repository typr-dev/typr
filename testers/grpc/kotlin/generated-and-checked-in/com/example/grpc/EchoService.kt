package com.example.grpc



/** Clean service interface for EchoService gRPC service */
interface EchoService {
  abstract fun echoCustomer(request: Customer): Customer

  abstract fun echoInventory(request: Inventory): Inventory

  abstract fun echoNotification(request: Notification): Notification

  abstract fun echoOptionalFields(request: OptionalFields): OptionalFields

  abstract fun echoOrder(request: Order): Order

  abstract fun echoOuter(request: Outer): Outer

  abstract fun echoPaymentMethod(request: PaymentMethod): PaymentMethod

  abstract fun echoScalarTypes(request: ScalarTypes): ScalarTypes

  abstract fun echoWellKnownTypes(request: WellKnownTypesMessage): WellKnownTypesMessage
}
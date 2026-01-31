package com.example.grpc



/** Clean service interface for EchoService gRPC service */
trait EchoService {
  def echoScalarTypes(request: ScalarTypes): ScalarTypes

  def echoCustomer(request: Customer): Customer

  def echoOrder(request: Order): Order

  def echoInventory(request: Inventory): Inventory

  def echoOuter(request: Outer): Outer

  def echoOptionalFields(request: OptionalFields): OptionalFields

  def echoWellKnownTypes(request: WellKnownTypesMessage): WellKnownTypesMessage

  def echoPaymentMethod(request: PaymentMethod): PaymentMethod

  def echoNotification(request: Notification): Notification
}
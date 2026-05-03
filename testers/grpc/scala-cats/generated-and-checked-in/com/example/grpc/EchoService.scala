package com.example.grpc

import cats.effect.IO

/** Clean service interface for EchoService gRPC service */
trait EchoService {
  def echoScalarTypes(request: ScalarTypes): IO[ScalarTypes]

  def echoCustomer(request: Customer): IO[Customer]

  def echoOrder(request: Order): IO[Order]

  def echoInventory(request: Inventory): IO[Inventory]

  def echoOuter(request: Outer): IO[Outer]

  def echoOptionalFields(request: OptionalFields): IO[OptionalFields]

  def echoWellKnownTypes(request: WellKnownTypesMessage): IO[WellKnownTypesMessage]

  def echoPaymentMethod(request: PaymentMethod): IO[PaymentMethod]

  def echoNotification(request: Notification): IO[Notification]
}
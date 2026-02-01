package com.example.grpc;

/** Clean service interface for EchoService gRPC service */
public interface EchoService {
  ScalarTypes echoScalarTypes(ScalarTypes request);

  Customer echoCustomer(Customer request);

  Order echoOrder(Order request);

  Inventory echoInventory(Inventory request);

  Outer echoOuter(Outer request);

  OptionalFields echoOptionalFields(OptionalFields request);

  WellKnownTypesMessage echoWellKnownTypes(WellKnownTypesMessage request);

  PaymentMethod echoPaymentMethod(PaymentMethod request);

  Notification echoNotification(Notification request);
}

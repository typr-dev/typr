package com.example.events;

import com.example.events.common.Money;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/** An invoice with money amount using ref */
public record Invoice(
    /** Unique identifier for the invoice */
    @JsonProperty("invoiceId") UUID invoiceId,
    /** Customer ID */
    @JsonProperty("customerId") Long customerId,
    /** Total amount with currency */
    @JsonProperty("total") Money total,
    /** When the invoice was issued */
    @JsonProperty("issuedAt") Instant issuedAt) {
  /** Unique identifier for the invoice */
  public Invoice withInvoiceId(UUID invoiceId) {
    return new Invoice(invoiceId, customerId, total, issuedAt);
  }

  /** Customer ID */
  public Invoice withCustomerId(Long customerId) {
    return new Invoice(invoiceId, customerId, total, issuedAt);
  }

  /** Total amount with currency */
  public Invoice withTotal(Money total) {
    return new Invoice(invoiceId, customerId, total, issuedAt);
  }

  /** When the invoice was issued */
  public Invoice withIssuedAt(Instant issuedAt) {
    return new Invoice(invoiceId, customerId, total, issuedAt);
  }
}

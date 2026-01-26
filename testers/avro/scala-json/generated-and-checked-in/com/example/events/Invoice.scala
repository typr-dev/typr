package com.example.events

import com.example.events.common.Money
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.UUID

/** An invoice with money amount using ref */
case class Invoice(
  /** Unique identifier for the invoice */
  @JsonProperty("invoiceId") invoiceId: UUID,
  /** Customer ID */
  @JsonProperty("customerId") customerId: Long,
  /** Total amount with currency */
  @JsonProperty("total") total: Money,
  /** When the invoice was issued */
  @JsonProperty("issuedAt") issuedAt: Instant
)
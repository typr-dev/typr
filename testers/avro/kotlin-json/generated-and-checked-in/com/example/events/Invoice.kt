package com.example.events

import com.example.events.common.Money
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.UUID

/** An invoice with money amount using ref */
data class Invoice(
  /** Unique identifier for the invoice */
  @field:JsonProperty("invoiceId") val invoiceId: UUID,
  /** Customer ID */
  @field:JsonProperty("customerId") val customerId: kotlin.Long,
  /** Total amount with currency */
  @field:JsonProperty("total") val total: Money,
  /** When the invoice was issued */
  @field:JsonProperty("issuedAt") val issuedAt: Instant
)
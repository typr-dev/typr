package testapi.model

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotNull

data class Money(
  @field:JsonProperty("amount") @NotNull val amount: Double,
  @field:JsonProperty("currency") @NotNull val currency: Currency
)
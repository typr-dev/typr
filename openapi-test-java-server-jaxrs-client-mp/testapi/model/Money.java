package testapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record Money(
  @JsonProperty("amount") @NotNull Double amount,
  @JsonProperty("currency") @NotNull Currency currency
) {
  public Money withAmount(Double amount) {
    return new Money(amount, currency);
  };

  public Money withCurrency(Currency currency) {
    return new Money(amount, currency);
  };
}
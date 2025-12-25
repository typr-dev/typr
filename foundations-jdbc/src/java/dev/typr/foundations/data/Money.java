package dev.typr.foundations.data;

public record Money(double value) {
  public Money(String value) {
    this(Double.parseDouble(value.replace("$", "")));
  }
}

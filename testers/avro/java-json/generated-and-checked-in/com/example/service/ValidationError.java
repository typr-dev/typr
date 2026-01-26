package com.example.service;

/** Thrown when input validation fails */
public record ValidationError(String field, String message) {
  public ValidationError withField(String field) {
    return new ValidationError(field, message);
  }

  public ValidationError withMessage(String message) {
    return new ValidationError(field, message);
  }
}

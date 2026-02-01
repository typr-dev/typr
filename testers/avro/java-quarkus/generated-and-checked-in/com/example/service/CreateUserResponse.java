package com.example.service;

import com.example.service.CreateUserResponse.Error;
import com.example.service.CreateUserResponse.Success;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Response wrapper for createUser RPC call */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@JsonSubTypes(
    value = {
      @Type(value = Success.class, name = "Success"),
      @Type(value = Error.class, name = "Error")
    })
public sealed interface CreateUserResponse
    permits CreateUserResponse.Success, CreateUserResponse.Error {
  /** Error response */
  record Error(String correlationId, ValidationError error) implements CreateUserResponse {
    public Error withCorrelationId(String correlationId) {
      return new Error(correlationId, error);
    }

    public Error withError(ValidationError error) {
      return new Error(correlationId, error);
    }
  }

  /** Successful response */
  record Success(String correlationId, User value) implements CreateUserResponse {
    public Success withCorrelationId(String correlationId) {
      return new Success(correlationId, value);
    }

    public Success withValue(User value) {
      return new Success(correlationId, value);
    }
  }

  String correlationId();
}

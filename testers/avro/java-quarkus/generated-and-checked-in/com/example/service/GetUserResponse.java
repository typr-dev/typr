package com.example.service;

import com.example.service.GetUserResponse.Error;
import com.example.service.GetUserResponse.Success;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Response wrapper for getUser RPC call */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@JsonSubTypes(
    value = {
      @Type(value = Success.class, name = "Success"),
      @Type(value = Error.class, name = "Error")
    })
public sealed interface GetUserResponse permits GetUserResponse.Success, GetUserResponse.Error {
  /** Error response */
  record Error(String correlationId, UserNotFoundError error) implements GetUserResponse {
    public Error withCorrelationId(String correlationId) {
      return new Error(correlationId, error);
    }

    public Error withError(UserNotFoundError error) {
      return new Error(correlationId, error);
    }
  }

  /** Successful response */
  record Success(String correlationId, User value) implements GetUserResponse {
    public Success withCorrelationId(String correlationId) {
      return new Success(correlationId, value);
    }

    public Success withValue(User value) {
      return new Success(correlationId, value);
    }
  }

  String correlationId();
}

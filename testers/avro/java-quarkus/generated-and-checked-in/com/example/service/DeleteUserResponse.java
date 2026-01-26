package com.example.service;

import com.example.service.DeleteUserResponse.Error;
import com.example.service.DeleteUserResponse.Success;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Response wrapper for deleteUser RPC call */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@JsonSubTypes(
    value = {
      @Type(value = Success.class, name = "Success"),
      @Type(value = Error.class, name = "Error")
    })
public sealed interface DeleteUserResponse
    permits DeleteUserResponse.Success, DeleteUserResponse.Error {
  /** Error response */
  record Error(String correlationId, UserNotFoundError error) implements DeleteUserResponse {
    public Error withCorrelationId(String correlationId) {
      return new Error(correlationId, error);
    }

    public Error withError(UserNotFoundError error) {
      return new Error(correlationId, error);
    }
  }

  /** Successful response */
  record Success(String correlationId, Void value) implements DeleteUserResponse {
    public Success withCorrelationId(String correlationId) {
      return new Success(correlationId, value);
    }

    public Success withValue(Void value) {
      return new Success(correlationId, value);
    }
  }

  String correlationId();
}

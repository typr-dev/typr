package com.example.events;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@JsonSubTypes(
    value = {
      @Type(value = OrderCancelled.class, name = "OrderCancelled"),
      @Type(value = OrderPlaced.class, name = "OrderPlaced"),
      @Type(value = OrderUpdated.class, name = "OrderUpdated")
    })
public sealed interface OrderEvents permits OrderCancelled, OrderPlaced, OrderUpdated {}

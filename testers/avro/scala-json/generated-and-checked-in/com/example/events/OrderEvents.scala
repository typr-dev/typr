package com.example.events

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@JsonSubTypes(value = Array(new Type(value = classOf[OrderCancelled], name = "OrderCancelled"), new Type(value = classOf[OrderPlaced], name = "OrderPlaced"), new Type(value = classOf[OrderUpdated], name = "OrderUpdated")))
trait OrderEvents
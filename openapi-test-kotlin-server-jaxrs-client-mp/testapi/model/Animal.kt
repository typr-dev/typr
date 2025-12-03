package testapi.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "animal_type")
@JsonSubTypes(value = [Type(value = Cat::class, name = "cat"), Type(value = Dog::class, name = "dog"), Type(value = Bird::class, name = "bird")])
sealed interface Animal {
  @JsonProperty("animal_type")
  fun animal_type(): String
}
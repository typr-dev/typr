package testapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "animal_type")
@JsonSubTypes({ @JsonSubTypes.Type(value = Cat.class, name = "cat"), @JsonSubTypes.Type(value = Dog.class, name = "dog"), @JsonSubTypes.Type(value = Bird.class, name = "bird") })
public sealed interface Animal {
  @JsonProperty("animal_type")
  String animal_type();
}
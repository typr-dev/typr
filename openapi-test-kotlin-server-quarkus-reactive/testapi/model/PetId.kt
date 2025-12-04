package testapi.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/** Unique pet identifier */
data class PetId @JsonCreator constructor(@get:JsonValue val value: String)
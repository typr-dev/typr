package testapi.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/** Email address wrapper */
data class Email @JsonCreator constructor(@get:JsonValue val value: String)
package testdb

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.Assert.assertEquals
import org.junit.Assert.fail

object JsonEquals {
    val mapper: ObjectMapper = ObjectMapper()
        .registerModule(Jdk8Module())
        .registerModule(JavaTimeModule())
        .registerModule(KotlinModule.Builder().build())

    fun assertJsonEquals(expected: Any?, actual: Any?) {
        assertJsonEquals(null, expected, actual)
    }

    fun assertJsonEquals(message: String?, expected: Any?, actual: Any?) {
        try {
            val expectedJson: JsonNode = mapper.valueToTree(expected)
            val actualJson: JsonNode = mapper.valueToTree(actual)
            if (message != null) {
                assertEquals(message, expectedJson, actualJson)
            } else {
                assertEquals(expectedJson, actualJson)
            }
        } catch (e: Exception) {
            fail("Failed to serialize to JSON: ${e.message}")
        }
    }
}

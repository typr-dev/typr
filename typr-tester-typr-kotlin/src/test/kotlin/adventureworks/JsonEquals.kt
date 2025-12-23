package adventureworks

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.junit.Assert.assertEquals

/**
 * Helper to compare objects using JSON serialization.
 * Needed because arrays don't compare with structural equality.
 */
object JsonEquals {
    private val objectMapper = ObjectMapper().apply {
        registerModule(Jdk8Module())
        registerModule(JavaTimeModule())
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    }

    fun <T> assertJsonEquals(expected: T, actual: T) {
        val expectedJson = objectMapper.writeValueAsString(expected)
        val actualJson = objectMapper.writeValueAsString(actual)
        assertEquals(expectedJson, actualJson)
    }
}

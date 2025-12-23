package adventureworks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * JSON-based equality testing for Java tests, matching the Scala test approach. Uses Jackson
 * ObjectMapper to serialize objects to JSON and compare.
 */
public class JsonEquals {
  public static final ObjectMapper mapper =
      new ObjectMapper().registerModule(new Jdk8Module()).registerModule(new JavaTimeModule());

  public static void assertJsonEquals(Object expected, Object actual) {
    assertJsonEquals(null, expected, actual);
  }

  public static void assertJsonEquals(String message, Object expected, Object actual) {
    try {
      JsonNode expectedJson = mapper.valueToTree(expected);
      JsonNode actualJson = mapper.valueToTree(actual);
      if (message != null) {
        assertEquals(message, expectedJson, actualJson);
      } else {
        assertEquals(expectedJson, actualJson);
      }
    } catch (Exception e) {
      fail("Failed to serialize to JSON: " + e.getMessage());
    }
  }
}

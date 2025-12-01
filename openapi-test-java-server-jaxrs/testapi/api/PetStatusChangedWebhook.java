package testapi.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.Void;

public sealed interface PetStatusChangedWebhook {
  /** Called when a pet's status changes */
  Void onPetStatusChanged(JsonNode body);
}
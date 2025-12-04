package testapi.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.smallrye.mutiny.Uni;
import java.lang.Void;

public interface PetStatusChangedWebhook {
  /** Called when a pet's status changes */
  Uni<Void> onPetStatusChanged(JsonNode body);
}
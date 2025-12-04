package testapi.api

import com.fasterxml.jackson.databind.JsonNode
import io.smallrye.mutiny.Uni
import java.lang.Void

interface PetStatusChangedWebhook {
  /** Called when a pet's status changes */
  fun onPetStatusChanged(body: JsonNode): Uni<Void>
}
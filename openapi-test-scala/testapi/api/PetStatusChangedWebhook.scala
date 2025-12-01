package testapi.api

import io.circe.Json
import java.lang.Void

trait PetStatusChangedWebhook {
  /** Called when a pet's status changes */
  def onPetStatusChanged(body: Json): Void
}
package testapi.api

import java.lang.Void
import testapi.model.Pet

/** Callback handler for createPet - OnPetCreated
  * Runtime expression: {$request.body#/callbackUrl}
  */
interface CreatePetOnPetCreatedCallback {
  /** Called when pet is created */
  fun onPetCreatedCallback(body: Pet): Void
}
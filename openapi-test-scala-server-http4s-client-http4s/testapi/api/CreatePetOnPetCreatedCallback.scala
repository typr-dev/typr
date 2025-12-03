package testapi.api

import cats.effect.IO
import java.lang.Void
import testapi.model.Pet

/** Callback handler for createPet - OnPetCreated Runtime expression: {$request.body#/callbackUrl}
  */
trait CreatePetOnPetCreatedCallback {

  /** Called when pet is created */
  def onPetCreatedCallback(body: Pet): IO[Void]
}

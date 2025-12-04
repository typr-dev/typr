package testapi.api;

import java.lang.Void;
import testapi.model.Pet;

/** Callback handler for createPet - OnPetCreated
  * Runtime expression: {$request.body#/callbackUrl}
  */
public interface CreatePetOnPetCreatedCallback {
  /** Called when pet is created */
  Void onPetCreatedCallback(Pet body);
}
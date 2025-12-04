package testapi.api;

import io.smallrye.mutiny.Uni;
import java.lang.Void;
import testapi.model.Pet;

/** Callback handler for createPet - OnPetCreated
  * Runtime expression: {$request.body#/callbackUrl}
  */
public interface CreatePetOnPetCreatedCallback {
  /** Called when pet is created */
  Uni<Void> onPetCreatedCallback(Pet body);
}
package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.domain.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

final class PetVoiceProfileTest {
    private static final Instant AT = Instant.parse("2026-09-26T00:00:00Z");

    @Test void speciesAndDurableTraitsSetAStableVoiceIndependentOfMood() {
        Pet cat = pet(PetSpecies.CAT);
        Pet dog = pet(PetSpecies.DOG);
        assertNotEquals(PetVoiceProfile.forPet(cat), PetVoiceProfile.forPet(dog));
        Pet excitedCat = new Pet(cat.petId(), cat.ownerUuid(), cat.name(), cat.appearance(),
                cat.traits(), PetMood.initial(90, 90, 0, 0, AT), cat.placement(),
                cat.recordVersion(), cat.createdAt(), cat.updatedAt());
        assertEquals(PetVoiceProfile.forPet(cat), PetVoiceProfile.forPet(excitedCat));
    }

    private static Pet pet(PetSpecies species) {
        return new Pet(UUID.randomUUID(), UUID.randomUUID(), "Mochi",
                PetAppearance.create(species, species == PetSpecies.CAT
                        ? "minecraft:tabby" : "minecraft:pale",
                        species == PetSpecies.CAT ? .8 : .75, 1L,
                        AppearanceRules.defaults()),
                PetTraits.initial(50, 50, 50, 50, 50, AT),
                PetMood.initial(50, 20, 10, 20, AT), HeldPlacement.INSTANCE, 1, AT, AT);
    }
}

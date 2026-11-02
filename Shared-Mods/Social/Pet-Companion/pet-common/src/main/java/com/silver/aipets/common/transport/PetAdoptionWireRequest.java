package com.silver.aipets.common.transport;

import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PetSpecies;

import java.util.Objects;
import java.util.UUID;

public record PetAdoptionWireRequest(UUID ownerUuid, PetSpecies species, String name) {
    public PetAdoptionWireRequest {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(species, "species");
        Objects.requireNonNull(name, "name");
        if (!name.equals(name.strip()) || name.isBlank()) {
            throw new IllegalArgumentException("Pet name must be non-blank with no surrounding whitespace");
        }
        if (name.codePointCount(0, name.length()) > Pet.MAX_NAME_CODE_POINTS) {
            throw new IllegalArgumentException("Pet name is too long");
        }
        if (name.indexOf('\u00a7') >= 0 || name.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Pet name contains unsafe formatting/control characters");
        }
    }
}

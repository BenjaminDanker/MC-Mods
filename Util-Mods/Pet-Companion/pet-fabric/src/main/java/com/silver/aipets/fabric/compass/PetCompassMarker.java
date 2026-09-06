package com.silver.aipets.fabric.compass;

import java.util.UUID;

/** Parsed, signature-verified compass identity. */
public record PetCompassMarker(UUID ownerUuid, UUID petId) {
}

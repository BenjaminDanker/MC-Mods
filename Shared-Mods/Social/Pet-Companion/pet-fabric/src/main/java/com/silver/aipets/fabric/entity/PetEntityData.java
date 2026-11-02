package com.silver.aipets.fabric.entity;

import java.util.UUID;

/** Persistent identity mixed into tameable entities; non-pet entities retain null IDs. */
public interface PetEntityData {
    boolean aipets$isPet();

    UUID aipets$getPetId();

    UUID aipets$getOwnerUuid();

    long aipets$getRecordVersion();

    boolean aipets$isSleeping();

    void aipets$mark(UUID petId, UUID ownerUuid, long recordVersion, boolean sleeping);

    void aipets$setRecordVersion(long recordVersion);

    void aipets$setSleeping(boolean sleeping);
}

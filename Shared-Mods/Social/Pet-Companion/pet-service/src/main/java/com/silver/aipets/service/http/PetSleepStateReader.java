package com.silver.aipets.service.http;

import java.util.UUID;

@FunctionalInterface
public interface PetSleepStateReader {
    boolean isSleeping(UUID petId);
}

package com.silver.aipets.service.health;

@FunctionalInterface
public interface PetReadinessProbe {
    PetReadinessSnapshot probe();
}

package com.silver.aipets.fabric.entity;

import com.silver.aipets.common.domain.ResourceId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeterministicVariantFallbackTest {
    @Test
    void selectionIsOrderIndependentAndStable() {
        List<ResourceId> firstOrder = List.of(
                ResourceId.parse("minecraft:woods"),
                ResourceId.parse("minecraft:black"),
                ResourceId.parse("minecraft:pale"));
        List<ResourceId> secondOrder = List.of(
                ResourceId.parse("minecraft:pale"),
                ResourceId.parse("minecraft:woods"),
                ResourceId.parse("minecraft:black"));

        assertEquals(
                DeterministicVariantFallback.select(firstOrder, 42L),
                DeterministicVariantFallback.select(secondOrder, 42L));
    }

    @Test
    void emptyRegistryIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DeterministicVariantFallback.select(List.of(), 1L));
    }
}

package com.silver.aipets.fabric.conversation;

import com.silver.aipets.common.domain.BackendId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetConversationSessionRegistryTest {
    @Test
    void bindsNormalizesLocksCorrelatesCoolsDownAndExpires() {
        Instant start = Instant.parse("2026-08-31T12:00:00Z");
        UUID owner = UUID.fromString("20000000-0000-0000-0000-0000000000f1");
        UUID pet = UUID.fromString("10000000-0000-0000-0000-0000000000f1");
        UUID entity = UUID.fromString("30000000-0000-0000-0000-0000000000f1");
        UUID firstSession = UUID.fromString("40000000-0000-0000-0000-0000000000f1");
        UUID secondSession = UUID.fromString("40000000-0000-0000-0000-0000000000f2");
        Queue<UUID> ids = new ArrayDeque<>();
        ids.add(firstSession);
        ids.add(secondSession);
        PetConversationSessionRegistry registry = new PetConversationSessionRegistry(
                Duration.ofSeconds(30), Duration.ofSeconds(5), 500, ids::remove);

        PetConversationSession opened = registry.open(
                owner, pet, entity, new BackendId("survival"),
                "minecraft:overworld", start);
        assertEquals(firstSession, opened.sessionId());
        assertEquals(owner, opened.ownerUuid());
        assertEquals(pet, opened.petId());
        assertEquals(entity, opened.petEntityUuid());
        assertEquals("survival", opened.backendId().value());
        assertEquals("minecraft:overworld", opened.dimensionId());

        UUID request = UUID.fromString("50000000-0000-0000-0000-0000000000f1");
        PetConversationSessionRegistry.BeginResult begun = registry.beginSubmission(
                firstSession, owner, request, "  hello\n§a pet  ", start);
        assertEquals(PetConversationSessionRegistry.BeginStatus.ACCEPTED, begun.status());
        assertEquals("hello pet", begun.normalizedMessage().orElseThrow());
        assertEquals(
                PetConversationSessionRegistry.BeginStatus.ALREADY_SUBMITTING,
                registry.beginSubmission(
                        firstSession, owner, UUID.randomUUID(), "again", start).status());
        assertFalse(registry.complete(firstSession, UUID.randomUUID(), start));
        assertTrue(registry.current(firstSession, request, start).isPresent());
        assertTrue(registry.complete(firstSession, request, start));
        assertEquals(1, registry.activeCount());
        assertEquals(
                PetConversationSessionRegistry.BeginStatus.COOLDOWN,
                registry.beginSubmission(
                        firstSession, owner, UUID.randomUUID(), "again", start.plusSeconds(1)).status());

        PetConversationSession reopened = registry.open(
                owner, pet, entity, new BackendId("survival"),
                "minecraft:overworld", start.plusSeconds(1));
        assertEquals(secondSession, reopened.sessionId());
        assertEquals(
                PetConversationSessionRegistry.BeginStatus.COOLDOWN,
                registry.beginSubmission(
                        secondSession, owner, UUID.randomUUID(), "again", start.plusSeconds(1)).status());
        assertEquals(1, registry.purgeExpired(start.plusSeconds(31)));
        assertEquals(0, registry.activeCount());
    }
}

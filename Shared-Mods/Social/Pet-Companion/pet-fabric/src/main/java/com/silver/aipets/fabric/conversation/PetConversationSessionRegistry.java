package com.silver.aipets.fabric.conversation;

import com.silver.aipets.common.domain.BackendId;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server-thread-friendly session registry which atomically owns expiry, cooldown and the
 * one-request-per-pet lock. Methods are synchronized so UI callbacks cannot race async results.
 */
public final class PetConversationSessionRegistry {
    private final Duration lifetime;
    private final Duration cooldown;
    private final int maximumMessageCharacters;
    private final Supplier<UUID> sessionIds;
    private final Map<UUID, PetConversationSession> bySession = new HashMap<>();
    private final Map<UUID, UUID> byOwner = new HashMap<>();
    private final Map<UUID, UUID> byPet = new HashMap<>();
    private final Map<UUID, Instant> petCooldownUntil = new HashMap<>();

    public PetConversationSessionRegistry(
            Duration lifetime,
            Duration cooldown,
            int maximumMessageCharacters,
            Supplier<UUID> sessionIds) {
        this.lifetime = positive(lifetime, "lifetime");
        this.cooldown = nonNegative(cooldown, "cooldown");
        if (maximumMessageCharacters < 1 || maximumMessageCharacters > 4_000) {
            throw new IllegalArgumentException("maximumMessageCharacters is outside safe bounds");
        }
        this.maximumMessageCharacters = maximumMessageCharacters;
        this.sessionIds = Objects.requireNonNull(sessionIds, "sessionIds");
    }

    public static PetConversationSessionRegistry defaults() {
        return new PetConversationSessionRegistry(
                Duration.ofMinutes(10), Duration.ofSeconds(5), 500, UUID::randomUUID);
    }

    public synchronized PetConversationSession open(
            UUID ownerUuid,
            UUID petId,
            UUID petEntityUuid,
            BackendId backendId,
            String dimensionId,
            Instant now) {
        Objects.requireNonNull(now, "now");
        purgeExpired(now);
        removeIndexed(byOwner.get(Objects.requireNonNull(ownerUuid, "ownerUuid")));
        removeIndexed(byPet.get(Objects.requireNonNull(petId, "petId")));
        PetConversationSession session = new PetConversationSession(
                Objects.requireNonNull(sessionIds.get(), "sessionIds returned null"),
                ownerUuid,
                petId,
                Objects.requireNonNull(petEntityUuid, "petEntityUuid"),
                Objects.requireNonNull(backendId, "backendId"),
                Objects.requireNonNull(dimensionId, "dimensionId"),
                now.plus(lifetime),
                PetConversationSession.State.OPEN,
                Optional.empty());
        bySession.put(session.sessionId(), session);
        byOwner.put(ownerUuid, session.sessionId());
        byPet.put(petId, session.sessionId());
        return session;
    }

    public synchronized BeginResult beginSubmission(
            UUID sessionId,
            UUID ownerUuid,
            UUID correlationId,
            String untrustedMessage,
            Instant now) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(now, "now");
        PetConversationSession current = bySession.get(sessionId);
        if (current == null) return BeginResult.rejected(BeginStatus.SESSION_NOT_FOUND);
        if (current.isExpired(now)) {
            removeIndexed(sessionId);
            return BeginResult.rejected(BeginStatus.EXPIRED);
        }
        if (!current.ownerUuid().equals(ownerUuid)) {
            return BeginResult.rejected(BeginStatus.OWNER_MISMATCH);
        }
        if (current.state() == PetConversationSession.State.SUBMITTING) {
            return BeginResult.rejected(BeginStatus.ALREADY_SUBMITTING);
        }
        Instant cooldownUntil = petCooldownUntil.get(current.petId());
        if (cooldownUntil != null && now.isBefore(cooldownUntil)) {
            return BeginResult.rejected(BeginStatus.COOLDOWN);
        }
        Optional<String> normalized = normalize(untrustedMessage, maximumMessageCharacters);
        if (normalized.isEmpty()) return BeginResult.rejected(BeginStatus.INVALID_INPUT);

        PetConversationSession submitting = new PetConversationSession(
                current.sessionId(), current.ownerUuid(), current.petId(), current.petEntityUuid(),
                current.backendId(), current.dimensionId(), now.plus(lifetime),
                PetConversationSession.State.SUBMITTING, Optional.of(correlationId));
        bySession.put(sessionId, submitting);
        petCooldownUntil.put(current.petId(), now.plus(cooldown));
        return BeginResult.accepted(submitting, normalized.orElseThrow());
    }

    public synchronized Optional<PetConversationSession> current(
            UUID sessionId, UUID correlationId, Instant now) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(now, "now");
        PetConversationSession current = bySession.get(sessionId);
        if (current == null) return Optional.empty();
        if (current.isExpired(now)) {
            removeIndexed(sessionId);
            return Optional.empty();
        }
        return current.state() == PetConversationSession.State.SUBMITTING
                && current.correlationId().filter(correlationId::equals).isPresent()
                ? Optional.of(current) : Optional.empty();
    }

    /** Completes the correlated request and reopens the same private conversation. */
    public synchronized boolean complete(UUID sessionId, UUID correlationId, Instant now) {
        Optional<PetConversationSession> matched = current(sessionId, correlationId, now);
        if (matched.isEmpty()) return false;
        PetConversationSession current = matched.orElseThrow();
        bySession.put(sessionId, new PetConversationSession(
                current.sessionId(), current.ownerUuid(), current.petId(), current.petEntityUuid(),
                current.backendId(), current.dimensionId(), now.plus(lifetime),
                PetConversationSession.State.OPEN, Optional.empty()));
        return true;
    }

    /** Terminates only the currently correlated request and its conversation. */
    public synchronized boolean end(UUID sessionId, UUID correlationId, Instant now) {
        if (current(sessionId, correlationId, now).isEmpty()) return false;
        removeIndexed(sessionId);
        return true;
    }

    public synchronized void cancelOwner(UUID ownerUuid) {
        removeIndexed(byOwner.get(Objects.requireNonNull(ownerUuid, "ownerUuid")));
    }

    public synchronized void cancelSession(UUID sessionId) {
        removeIndexed(Objects.requireNonNull(sessionId, "sessionId"));
    }

    public synchronized void cancelPet(UUID petId) {
        removeIndexed(byPet.get(Objects.requireNonNull(petId, "petId")));
    }

    public synchronized int purgeExpired(Instant now) {
        Objects.requireNonNull(now, "now");
        int before = bySession.size();
        bySession.values().stream()
                .filter(session -> session.isExpired(now))
                .map(PetConversationSession::sessionId)
                .toList()
                .forEach(this::removeIndexed);
        petCooldownUntil.entrySet().removeIf(entry -> !now.isBefore(entry.getValue()));
        return before - bySession.size();
    }

    /** Keeps deliberately open UI sessions alive; submitting requests retain a hard deadline. */
    public synchronized void keepOpenSessionsAlive(Instant now) {
        Objects.requireNonNull(now, "now");
        bySession.replaceAll((sessionId, current) ->
                current.state() == PetConversationSession.State.OPEN
                        ? new PetConversationSession(
                                current.sessionId(), current.ownerUuid(), current.petId(),
                                current.petEntityUuid(), current.backendId(), current.dimensionId(),
                                now.plus(lifetime), current.state(), current.correlationId())
                        : current);
    }

    public synchronized int activeCount() {
        return bySession.size();
    }

    public synchronized void clear() {
        bySession.clear();
        byOwner.clear();
        byPet.clear();
        petCooldownUntil.clear();
    }

    private void removeIndexed(UUID sessionId) {
        if (sessionId == null) return;
        PetConversationSession removed = bySession.remove(sessionId);
        if (removed == null) return;
        byOwner.remove(removed.ownerUuid(), sessionId);
        byPet.remove(removed.petId(), sessionId);
    }

    private static Optional<String> normalize(String value, int maximumCharacters) {
        if (value == null) return Optional.empty();
        String normalized = value.replaceAll("§.", "")
                .replaceAll("\\p{Cc}", " ")
                .replaceAll("\\s+", " ")
                .strip();
        if (normalized.isBlank()
                || normalized.codePointCount(0, normalized.length()) > maximumCharacters) {
            return Optional.empty();
        }
        return Optional.of(normalized);
    }

    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static Duration nonNegative(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isNegative()) throw new IllegalArgumentException(name + " must be non-negative");
        return value;
    }

    public enum BeginStatus {
        ACCEPTED,
        SESSION_NOT_FOUND,
        EXPIRED,
        OWNER_MISMATCH,
        ALREADY_SUBMITTING,
        COOLDOWN,
        INVALID_INPUT
    }

    public record BeginResult(
            BeginStatus status,
            Optional<PetConversationSession> session,
            Optional<String> normalizedMessage) {
        public BeginResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(normalizedMessage, "normalizedMessage");
            boolean accepted = status == BeginStatus.ACCEPTED;
            if (accepted != (session.isPresent() && normalizedMessage.isPresent())) {
                throw new IllegalArgumentException("Only an accepted result contains submission data");
            }
        }

        private static BeginResult accepted(PetConversationSession session, String message) {
            return new BeginResult(BeginStatus.ACCEPTED, Optional.of(session), Optional.of(message));
        }

        private static BeginResult rejected(BeginStatus status) {
            return new BeginResult(status, Optional.empty(), Optional.empty());
        }
    }
}

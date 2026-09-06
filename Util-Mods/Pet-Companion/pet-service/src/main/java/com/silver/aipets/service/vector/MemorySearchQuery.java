package com.silver.aipets.service.vector;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Original player text plus deterministic metadata; there is deliberately no query-rewrite hook. */
public record MemorySearchQuery(
        UUID petId,
        String originalPlayerMessage,
        Optional<String> backend,
        Optional<String> dimension,
        Optional<String> eventType,
        Set<String> namedEntities,
        double minimumSimilarity,
        int maximumResults) {

    public MemorySearchQuery {
        Objects.requireNonNull(petId, "petId");
        originalPlayerMessage = normalize(originalPlayerMessage, 500, "originalPlayerMessage");
        backend = normalizedOptional(backend, "backend");
        dimension = normalizedOptional(dimension, "dimension");
        eventType = normalizedOptional(eventType, "eventType");
        Objects.requireNonNull(namedEntities, "namedEntities");
        if (namedEntities.size() > 16) {
            throw new IllegalArgumentException("namedEntities cannot exceed 16");
        }
        namedEntities = namedEntities.stream()
                .map(value -> normalize(value, 128, "namedEntity"))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!Double.isFinite(minimumSimilarity)
                || minimumSimilarity < -1.0 || minimumSimilarity > 1.0) {
            throw new IllegalArgumentException("minimumSimilarity must be within [-1, 1]");
        }
        if (maximumResults < 1 || maximumResults > 3) {
            throw new IllegalArgumentException("maximumResults must be between 1 and 3");
        }
    }

    public String deterministicSearchText() {
        StringBuilder text = new StringBuilder(originalPlayerMessage);
        backend.ifPresent(value -> text.append(" | server:").append(value));
        dimension.ifPresent(value -> text.append(" | dimension:").append(value));
        eventType.ifPresent(value -> text.append(" | event:").append(value));
        namedEntities.stream().sorted().forEach(value -> text.append(" | entity:").append(value));
        return text.toString();
    }

    private static Optional<String> normalizedOptional(Optional<String> value, String name) {
        Objects.requireNonNull(value, name);
        return value.map(item -> normalize(item, 191, name));
    }

    private static String normalize(String value, int maximumLength, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.strip().replaceAll("\\s+", " ");
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(name + " length is invalid");
        }
        return normalized;
    }
}

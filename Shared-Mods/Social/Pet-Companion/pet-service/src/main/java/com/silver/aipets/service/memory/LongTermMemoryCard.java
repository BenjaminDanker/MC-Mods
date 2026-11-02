package com.silver.aipets.service.memory;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;

/** Compact, validated relational memory card; raw dialogue is intentionally not represented. */
public record LongTermMemoryCard(
        UUID memoryId,
        UUID petId,
        long version,
        String text,
        MemoryImportance importance,
        Set<String> emotionTags,
        Set<String> entityTags,
        Set<String> locationTags,
        boolean active,
        Instant createdAt,
        MemoryProvenance provenance,
        MemoryKind kind,
        double confidence,
        int salience,
        int emotionalSignificance,
        java.util.Optional<UUID> supersedesMemoryId) {

    public LongTermMemoryCard(UUID memoryId, UUID petId, long version, String text,
            MemoryImportance importance, Set<String> emotionTags, Set<String> entityTags,
            Set<String> locationTags, boolean active) {
        this(memoryId, petId, version, text, importance, emotionTags, entityTags,
                locationTags, active, Instant.EPOCH, MemoryProvenance.INFERENCE,
                MemoryKind.FACT, .5, 1, 0, java.util.Optional.empty());
    }

    public LongTermMemoryCard(UUID memoryId, UUID petId, long version, String text,
            MemoryImportance importance, Set<String> emotionTags, Set<String> entityTags,
            Set<String> locationTags, boolean active, Instant createdAt,
            MemoryProvenance provenance) {
        this(memoryId,petId,version,text,importance,emotionTags,entityTags,locationTags,active,
                createdAt,provenance,MemoryKind.FACT,provenance==MemoryProvenance.OWNER_STATEMENT?1:.5,
                importance==MemoryImportance.HIGH?4:2,emotionTags.isEmpty()?0:2,java.util.Optional.empty());
    }

    public LongTermMemoryCard {
        Objects.requireNonNull(memoryId, "memoryId");
        Objects.requireNonNull(petId, "petId");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        text = normalize(text, "text", 4_000);
        Objects.requireNonNull(importance, "importance");
        emotionTags = normalizedTags(emotionTags, "emotionTags");
        entityTags = normalizedTags(entityTags, "entityTags");
        locationTags = normalizedTags(locationTags, "locationTags");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(kind,"kind");
        Objects.requireNonNull(supersedesMemoryId,"supersedesMemoryId");
        if(!Double.isFinite(confidence)||confidence<0||confidence>1)throw new IllegalArgumentException("confidence outside [0,1]");
        if(salience<0||salience>5||emotionalSignificance<0||emotionalSignificance>5)throw new IllegalArgumentException("memory significance outside [0,5]");
    }

    private static Set<String> normalizedTags(Set<String> source, String name) {
        Objects.requireNonNull(source, name);
        if (source.size() > 32) {
            throw new IllegalArgumentException(name + " cannot exceed 32 entries");
        }
        return source.stream().map(tag -> name.equals("locationTags")
                        && tag.startsWith(WitnessedLocations.PREFIX) ? spatialTag(tag)
                                : normalize(tag, name, 128)).collect(
                java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String spatialTag(String value) {
        String bounded = value.strip();
        if (bounded.length() > 2048) throw new IllegalArgumentException("Spatial metadata too large");
        // Whitespace inside a JSON world identifier is significant; do not normalize it as prose.
        return bounded;
    }

    private static String normalize(String value, String name, int maximumLength) {
        Objects.requireNonNull(value, name);
        String normalized = value.strip().replaceAll("\\s+", " ");
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(name + " length is invalid");
        }
        return normalized;
    }
}

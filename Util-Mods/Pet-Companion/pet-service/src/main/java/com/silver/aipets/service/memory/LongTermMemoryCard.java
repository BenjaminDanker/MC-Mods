package com.silver.aipets.service.memory;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

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
        boolean active) {

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
    }

    private static Set<String> normalizedTags(Set<String> source, String name) {
        Objects.requireNonNull(source, name);
        if (source.size() > 32) {
            throw new IllegalArgumentException(name + " cannot exceed 32 entries");
        }
        return source.stream().map(tag -> normalize(tag, name, 128)).collect(
                java.util.stream.Collectors.toUnmodifiableSet());
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

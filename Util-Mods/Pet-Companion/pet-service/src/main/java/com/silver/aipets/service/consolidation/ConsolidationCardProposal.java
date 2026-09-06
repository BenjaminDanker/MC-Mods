package com.silver.aipets.service.consolidation;

import com.silver.aipets.service.memory.MemoryImportance;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ConsolidationCardProposal(
        String text,
        MemoryImportance importance,
        List<UUID> sourceEventIds,
        Set<String> emotionTags,
        Set<String> entityTags,
        Set<String> locationTags) {
    public ConsolidationCardProposal {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(importance, "importance");
        sourceEventIds = List.copyOf(Objects.requireNonNull(sourceEventIds, "sourceEventIds"));
        emotionTags = Set.copyOf(Objects.requireNonNull(emotionTags, "emotionTags"));
        entityTags = Set.copyOf(Objects.requireNonNull(entityTags, "entityTags"));
        locationTags = Set.copyOf(Objects.requireNonNull(locationTags, "locationTags"));
        if (text.isBlank() || sourceEventIds.isEmpty() || sourceEventIds.size() > 30
                || emotionTags.size() > 16 || entityTags.size() > 16 || locationTags.size() > 16) {
            throw new IllegalArgumentException("Invalid consolidation card proposal");
        }
    }
}

package com.silver.aipets.service.consolidation;

import com.silver.aipets.common.domain.TraitName;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ConsolidationModelOutput(
        List<ConsolidationCardProposal> memoryCards,
        String relationshipSummary,
        Map<TraitName, Integer> traitDeltas) {
    public ConsolidationModelOutput {
        memoryCards = List.copyOf(Objects.requireNonNull(memoryCards, "memoryCards"));
        Objects.requireNonNull(relationshipSummary, "relationshipSummary");
        traitDeltas = Map.copyOf(Objects.requireNonNull(traitDeltas, "traitDeltas"));
        if (!traitDeltas.keySet().equals(java.util.EnumSet.allOf(TraitName.class))
                || traitDeltas.values().stream().anyMatch(delta -> delta < -100 || delta > 100)) {
            throw new IllegalArgumentException("Invalid consolidation trait proposals");
        }
    }
}

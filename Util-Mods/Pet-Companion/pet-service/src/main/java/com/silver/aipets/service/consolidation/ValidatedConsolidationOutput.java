package com.silver.aipets.service.consolidation;

import com.silver.aipets.common.domain.TraitName;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ValidatedConsolidationOutput(
        List<ConsolidationCardProposal> memoryCards,
        String relationshipSummary,
        Map<TraitName, Integer> proposedTraitDeltas) {
    public ValidatedConsolidationOutput {
        memoryCards = List.copyOf(Objects.requireNonNull(memoryCards, "memoryCards"));
        Objects.requireNonNull(relationshipSummary, "relationshipSummary");
        proposedTraitDeltas = Map.copyOf(Objects.requireNonNull(proposedTraitDeltas, "proposedTraitDeltas"));
    }
}

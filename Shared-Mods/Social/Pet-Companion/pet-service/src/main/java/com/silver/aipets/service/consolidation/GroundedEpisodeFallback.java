package com.silver.aipets.service.consolidation;

import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.TraitName;
import com.silver.aipets.service.dialogue.DialogueImportance;
import com.silver.aipets.service.memory.MemoryImportance;
import com.silver.aipets.service.memory.MemoryProvenance;

import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Preserves one verbatim witnessed episode when every generated card fails grounding. */
final class GroundedEpisodeFallback {
    private GroundedEpisodeFallback() { }

    static Optional<ValidatedConsolidationOutput> from(Pet pet,
            CandidateSelection selection, ConsolidationOutputValidator validator) {
        for (ConsolidationEvent source : selection.selected()) {
            if (source.provenance() != MemoryProvenance.VERIFIED_OBSERVATION
                    || source.importance() != DialogueImportance.HIGH) continue;
            EnumMap<TraitName,Integer> zero = new EnumMap<>(TraitName.class);
            for (TraitName name : TraitName.values()) zero.put(name,0);
            var card = new ConsolidationCardProposal(source.summary(), MemoryImportance.HIGH,
                    List.of(source.eventId()), Set.of(), Set.of(), Set.of());
            try {
                return Optional.of(validator.validate(new ConsolidationModelOutput(
                        List.of(card), pet.traits().relationshipSummary(), zero), selection));
            } catch (ConsolidationOutputException invalid) {
                // A too-long/otherwise invalid event is not silently promoted.
            }
        }
        return Optional.empty();
    }
}

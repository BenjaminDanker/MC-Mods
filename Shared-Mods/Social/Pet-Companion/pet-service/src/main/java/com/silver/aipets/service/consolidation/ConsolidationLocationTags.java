package com.silver.aipets.service.consolidation;

import com.silver.aipets.service.memory.MemoryProvenance;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Derives location tags from cited, verified source facts instead of model suggestions. */
final class ConsolidationLocationTags {
    private static final Pattern BIOME = Pattern.compile(
            "(?:^|\\s)biome=([a-z0-9_.-]+:[a-z0-9_./-]+)");

    private ConsolidationLocationTags() { }

    static Set<String> from(ConsolidationCardProposal proposal,
            Map<UUID, ConsolidationEvent> events) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (UUID id : proposal.sourceEventIds()) {
            ConsolidationEvent source = events.get(id);
            if (source != null && source.provenance() == MemoryProvenance.VERIFIED_OBSERVATION)
                source.location().filter(place -> place.server().equals(source.sourceServer())
                        && place.dimension().equals(source.sourceDimension())).ifPresent(place -> {
                    if (tags.size() < 32) tags.add(com.silver.aipets.service.memory.WitnessedLocations.tag(place));
                });
        }
        for (UUID id : proposal.sourceEventIds()) {
            ConsolidationEvent event = events.get(id);
            if (event == null || event.provenance() != MemoryProvenance.VERIFIED_OBSERVATION)
                continue;
            // Prioritize all cited coordinates before ordinary tags; no model-supplied coordinates.
            if (!event.sourceServer().isBlank() && tags.size() < 32)
                tags.add(event.sourceServer());
            if (!event.sourceDimension().isBlank() && tags.size() < 32)
                tags.add(event.sourceDimension());
            var biomes = BIOME.matcher(event.summary());
            while (biomes.find() && tags.size() < 32) tags.add(biomes.group(1));
            if (tags.size() >= 32) break;
        }
        return Set.copyOf(tags);
    }
}

package com.silver.aipets.service.experience;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** A remembered place alone is insufficient evidence for an unrelated reaction. */
public final class MemoryInfluencePolicy {
    private static final Set<String> STOP = Set.of("owner", "nearby", "minecraft",
            "with", "while", "pet", "often", "usually", "from", "that", "this",
            "area", "visited", "biome", "same", "together", "around", "there");
    private MemoryInfluencePolicy() { }

    public static boolean relevant(String type, String event, String memory) {
        if (!Set.of("OWNER_ACTION", "SURVIVED_DANGER", "NEARBY_THREAT").contains(type))
            return false;
        String cue = event.split("biome=", 2)[0];
        Set<String> terms = terms(cue);
        terms.removeAll(Set.of("broke", "defeated", "survived", "danger", "hostile", "action"));
        if (terms.isEmpty()) return false;
        Set<String> remembered = terms(memory);
        return terms.stream().anyMatch(remembered::contains);
    }

    private static Set<String> terms(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(word -> word.length() >= 3 && !STOP.contains(word))
                .collect(Collectors.toSet());
    }
}

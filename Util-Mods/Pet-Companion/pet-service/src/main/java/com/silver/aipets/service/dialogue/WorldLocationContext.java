package com.silver.aipets.service.dialogue;

import java.util.Locale;
import java.util.Map;

/** Display labels only; raw server, dimension and biome IDs remain the factual keys. */
final class WorldLocationContext {
    private static final Map<String, String> REALMS = Map.ofEntries(
            Map.entry("vanilla", "Vanilla world"),
            Map.entry("vanilla1", "Vanilla world"),
            Map.entry("ocean", "Ocean world"),
            Map.entry("sky-island", "Sky Islands world"),
            Map.entry("sky-islands", "Sky Islands world"),
            Map.entry("desert", "Desert world"),
            Map.entry("cave", "Cave world"),
            Map.entry("magic", "Magic World"));
    private static final Map<String, String> DIMENSIONS = Map.of(
            "minecraft:overworld", "Overworld",
            "minecraft:the_nether", "Nether",
            "minecraft:the_end", "The End");

    private WorldLocationContext() { }

    static String realm(String backend) {
        return REALMS.getOrDefault(backend.toLowerCase(Locale.ROOT), backend);
    }

    static String dimension(String id) {
        return DIMENSIONS.getOrDefault(id, id);
    }
}

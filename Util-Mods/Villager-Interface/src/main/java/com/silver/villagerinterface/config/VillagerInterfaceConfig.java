package com.silver.villagerinterface.config;

import java.util.Collections;
import java.util.List;

public final class VillagerInterfaceConfig {
    public static final String DEFAULT_SYSTEM_PROMPT = """
        You are a village elder speaking to a player.

        Personality
        - You are grumpy, blunt, and wary of outsiders… but you do want to help.
        - You know a great deal of ancient lore, and you treat it like a loaded crossbow: useful, dangerous, and not for careless hands.
        - Do not volunteer big lore dumps. Keep answers short unless the player asks for details.
        - You avoid getting the player killed by oversharing: give hints and warnings first; only reveal more if the player asks directly.
        - If the player asks a direct lore question, answer clearly and expand only as far as asked.
        - If the player seems unprepared, urge caution and ask what they’re trying to do before giving deeper guidance.
        - Stay in-universe; speak like a cautious elder villager.
        - Do not invent new canon beyond the lore below. If you do not know, say so.

        Core Lore (you may reference this when asked)
        - Four domain kings guard the portals to their respective worlds, wary of intruders.
        - Our blacksmith can help forge an item into a Soulbound item, allowing it to cross worlds with you.
        - The guardian of the ocean protects a world of endless ocean, where only one remaining bastion of civilization lasts.
            The scales of his brethren lead the way to the remains of the city.
        - The dragon of the sky protects a world of endless sky and clouds.
            The saliva of a dragon is said to help dragons locate each other.
        - The three-headed undead destroys all who trespass into his eroded world,
            protecting the corpses of his long-lost friends.
        - The blind fool, anger clouding his judgement, does not distinguish friend from foe,
            preventing all from entering his world of endless caves.
        - Once all fragments of each world are combined, a new realm appears—one full of wonders yet unseen.

        Conversation rules
        - Be cautious with spoilers: hint first; confirm details only when the player asks.
        - If the player asks for guidance that could get them killed, start with the safest, least-specific advice and a warning.
        - If asked “tell me everything,” give a structured overview first, then ask what part they want to dig into.
        """;

    public static VillagerInterfaceConfig createDefault() {
        return new VillagerInterfaceConfig(
            10,
            "http://localhost:11434",
            "openhermes",
            "-1",
            120,
            10,
            List.of(createDefaultVillagerEntry())
        );
    }

    public static VillagerConfigEntry createDefaultVillagerEntry() {
        return new VillagerConfigEntry(
            "main",
            "villager",
            "Main Villager",
            "minecraft:overworld",
            new VillagerPosition(0.0, 64.0, 0.0),
            0.0f,
            0.0f,
            5.0,
            DEFAULT_SYSTEM_PROMPT
        );
    }

    private final int checkIntervalSeconds;
    private final String ollamaBaseUrl;
    private final String ollamaModel;
    private final String ollamaKeepAlive;
    private final int ollamaTimeoutSeconds;
    private final int maxHistoryTurns;
    private final List<VillagerConfigEntry> villagers;

    public VillagerInterfaceConfig(
        int checkIntervalSeconds,
        String ollamaBaseUrl,
        String ollamaModel,
        String ollamaKeepAlive,
        int ollamaTimeoutSeconds,
        int maxHistoryTurns,
        List<VillagerConfigEntry> villagers
    ) {
        this.checkIntervalSeconds = checkIntervalSeconds;
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.ollamaModel = ollamaModel;
        this.ollamaKeepAlive = ollamaKeepAlive;
        this.ollamaTimeoutSeconds = ollamaTimeoutSeconds;
        this.maxHistoryTurns = maxHistoryTurns;
        this.villagers = villagers != null ? List.copyOf(villagers) : Collections.emptyList();
    }

    public int checkIntervalSeconds() {
        return checkIntervalSeconds;
    }

    public String ollamaBaseUrl() {
        return ollamaBaseUrl;
    }

    public String ollamaModel() {
        return ollamaModel;
    }

    public String ollamaKeepAlive() {
        return ollamaKeepAlive;
    }

    public int ollamaTimeoutSeconds() {
        return ollamaTimeoutSeconds;
    }

    public int maxHistoryTurns() {
        return maxHistoryTurns;
    }

    public List<VillagerConfigEntry> villagers() {
        return villagers;
    }
}

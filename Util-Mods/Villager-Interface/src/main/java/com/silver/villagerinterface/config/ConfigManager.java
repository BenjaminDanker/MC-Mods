package com.silver.villagerinterface.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import com.silver.villagerinterface.VillagerInterfaceMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "villagerinterface.json";

    private final Path configPath;
    private VillagerInterfaceConfig config;

    public ConfigManager() {
        this.configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);
    }

    public void load() {
        if (!Files.exists(configPath)) {
            VillagerInterfaceMod.LOGGER.info("No Villager Interface config present, writing defaults to {}", configPath);
            config = VillagerInterfaceConfig.createDefault();
            save();
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            boolean legacyFormat = !root.has("conversation");
            boolean needsOpenAiUpgrade = !legacyFormat && isMissingOpenAiFields(root);
            VillagerInterfaceConfig loaded = legacyFormat
                ? migrateLegacy(GSON.fromJson(root, LegacyConfig.class))
                : GSON.fromJson(root, VillagerInterfaceConfig.class);
            config = coerceConfig(loaded != null ? loaded : VillagerInterfaceConfig.createDefault());
            if (legacyFormat || needsOpenAiUpgrade) {
                VillagerInterfaceMod.LOGGER.info("Updating Villager Interface configuration");
                save();
            }
        } catch (IOException | IllegalStateException | JsonParseException ex) {
            VillagerInterfaceMod.LOGGER.warn("Failed to read Villager Interface config, falling back to defaults", ex);
            config = VillagerInterfaceConfig.createDefault();
        }
    }

    private VillagerInterfaceConfig migrateLegacy(LegacyConfig legacy) {
        VillagerInterfaceConfig defaults = VillagerInterfaceConfig.createDefault();
        if (legacy == null) {
            return defaults;
        }
        return new VillagerInterfaceConfig(
            new ConversationSettings("ollama", legacy.checkIntervalSeconds, legacy.maxHistoryTurns),
            new OllamaSettings(legacy.ollamaBaseUrl, legacy.ollamaModel, legacy.ollamaKeepAlive, legacy.ollamaTimeoutSeconds),
            defaults.openai(),
            legacy.villagers
        );
    }

    private boolean isMissingOpenAiFields(JsonObject root) {
        if (!root.has("openai") || !root.get("openai").isJsonObject()) {
            return true;
        }
        JsonObject openai = root.getAsJsonObject("openai");
        return !openai.has("reasoningEffort")
            || !openai.has("maxCompletionTokens")
            || !openai.has("logUsage");
    }

    private VillagerInterfaceConfig coerceConfig(VillagerInterfaceConfig loaded) {
        VillagerInterfaceConfig defaults = VillagerInterfaceConfig.createDefault();
        ConversationSettings conversation = loaded.conversation();
        OllamaSettings ollama = loaded.ollama();
        OpenAiSettings openai = loaded.openai();

        String provider = conversation != null ? conversation.activeProvider() : null;
        provider = provider != null ? provider.trim().toLowerCase(Locale.ROOT) : "";
        if (!provider.equals("ollama") && !provider.equals("openai")) {
            provider = defaults.conversation().activeProvider();
        }
        int interval = positive(conversation != null ? conversation.checkIntervalSeconds() : 0, defaults.conversation().checkIntervalSeconds());
        int history = positive(conversation != null ? conversation.maxHistoryTurns() : 0, defaults.conversation().maxHistoryTurns());

        OllamaSettings safeOllama = new OllamaSettings(
            nonBlank(ollama != null ? ollama.baseUrl() : null, defaults.ollama().baseUrl()),
            nonBlank(ollama != null ? ollama.model() : null, defaults.ollama().model()),
            nonBlank(ollama != null ? ollama.keepAlive() : null, defaults.ollama().keepAlive()),
            positive(ollama != null ? ollama.timeoutSeconds() : 0, defaults.ollama().timeoutSeconds())
        );
        OpenAiSettings safeOpenAi = new OpenAiSettings(
            nonBlank(openai != null ? openai.baseUrl() : null, defaults.openai().baseUrl()),
            openai != null && openai.apiKey() != null ? openai.apiKey().trim() : "",
            nonBlank(openai != null ? openai.model() : null, defaults.openai().model()),
            reasoningEffort(openai != null ? openai.reasoningEffort() : null, defaults.openai().reasoningEffort()),
            Math.max(0, openai != null ? openai.maxCompletionTokens() : 0),
            positive(openai != null ? openai.timeoutSeconds() : 0, defaults.openai().timeoutSeconds()),
            openai == null || openai.logUsage() == null || openai.logUsage()
        );

        List<VillagerConfigEntry> entries = new ArrayList<>();
        if (loaded.villagers() != null) {
            for (VillagerConfigEntry entry : loaded.villagers()) {
                if (entry == null || entry.id() == null || entry.id().isBlank()) {
                    continue;
                }
                entries.add(new VillagerConfigEntry(
                    entry.id(),
                    nonBlank(entry.villagerType(), "villager"),
                    nonBlank(entry.displayName(), entry.id()),
                    nonBlank(entry.dimension(), "minecraft:overworld"),
                    entry.position() != null ? entry.position() : new VillagerPosition(0.0, 64.0, 0.0),
                    entry.yaw(), entry.pitch(),
                    entry.maxDistance() > 0.0 ? entry.maxDistance() : 5.0,
                    nonBlank(entry.systemPrompt(), VillagerInterfaceConfig.DEFAULT_SYSTEM_PROMPT)
                ));
            }
        }
        if (entries.isEmpty()) {
            entries.add(VillagerInterfaceConfig.createDefaultVillagerEntry());
        }

        return new VillagerInterfaceConfig(new ConversationSettings(provider, interval, history), safeOllama, safeOpenAi, entries);
    }

    private int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private String nonBlank(String value, String fallback) {
        return value != null && !value.isBlank() ? value.trim() : fallback;
    }

    private String reasoningEffort(String value, String fallback) {
        String normalized = value != null ? value.trim().toLowerCase(Locale.ROOT) : "";
        return switch (normalized) {
            case "none", "low", "medium", "high", "xhigh", "max" -> normalized;
            default -> fallback;
        };
    }

    public void save() {
        try {
            Files.createDirectories(configPath.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException ex) {
            VillagerInterfaceMod.LOGGER.error("Unable to write config file {}", configPath, ex);
        }
    }

    public VillagerInterfaceConfig getConfig() {
        return config != null ? config : VillagerInterfaceConfig.createDefault();
    }

    public Path getConfigPath() {
        return configPath;
    }

    /** JSON shape used before provider-specific configuration sections were introduced. */
    private static final class LegacyConfig {
        private int checkIntervalSeconds;
        private String ollamaBaseUrl;
        private String ollamaModel;
        private String ollamaKeepAlive;
        private int ollamaTimeoutSeconds;
        private int maxHistoryTurns;
        private List<VillagerConfigEntry> villagers;
    }
}

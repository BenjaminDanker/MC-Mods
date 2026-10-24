package com.silver.aipets.service.devplayer;

import com.silver.aipets.service.config.PetServiceConfig;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

/**
 * Fail-closed boundary for the operator-only, separate-process diagnostic CLI.
 *
 * Model settings resolve as {@code DEV_PLAYER_<NAME>}, then the service's {@code PET_<NAME>},
 * then the service default, so candidate models can be tried without editing the service file.
 * OpenAI is the primary provider; an OpenRouter key, when present, enables the chat and embedding
 * fallback exactly as in the service.
 */
public record DevPlayerEnvironment(String jdbcUrl, String dbUser, String dbPassword,
        URI qdrantUri, String qdrantCollection, String qdrantKey, URI openAiUri, String openAiKey,
        URI openRouterUri, String openRouterKey, boolean openRouterFallback,
        String dialogueModel, String embeddingModel, String consolidationModel, String moderationModel,
        int embeddingDimension, String dialogueReasoningEffort, String plannerModel,
        String memoryModel, java.util.Set<String> moderationBlockedCategories) {
    public static final String DATABASE = "pet_companion_dev_player";
    public static final String COLLECTION = "pet_companion_dev_player_memories";
    /** Dimension of the original isolated collection; other dimensions use a suffixed collection. */
    public static final int LEGACY_DIMENSION = 1_536;

    public static DevPlayerEnvironment from(Map<String, String> env) {
        Objects.requireNonNull(env);
        String url = required(env, "DEV_PLAYER_DB_JDBC_URL");
        if (!url.startsWith("jdbc:mariadb://"))
            throw new IllegalArgumentException("Dev Player requires MariaDB");
        URI uri = URI.create(url.substring(5)); // jdbc:mariadb://...
        if (!uri.getPath().equals("/" + DATABASE)
                || !"127.0.0.1".equals(uri.getHost()) || uri.getPort() != 3306
                || uri.getUserInfo() != null || uri.getQuery() != null) {
            throw new IllegalArgumentException("Dev Player requires the exact isolated MariaDB schema");
        }
        String collection = required(env, "DEV_PLAYER_QDRANT_COLLECTION");
        if (!COLLECTION.equals(collection)) {
            throw new IllegalArgumentException("Dev Player requires the exact isolated Qdrant collection");
        }
        String dbUser = required(env, "DEV_PLAYER_DB_USER");
        if (!"pet_dev_player".equals(dbUser)) {
            throw new IllegalArgumentException("Dev Player requires its dedicated SQL account");
        }
        URI qdrant = URI.create(required(env, "DEV_PLAYER_QDRANT_URL"));
        if (!"127.0.0.1".equals(qdrant.getHost()) || qdrant.getPort() != 6333
                || !"http".equals(qdrant.getScheme()) || !qdrant.getPath().isEmpty())
            throw new IllegalArgumentException("Dev Player requires local Qdrant");
        int dimension = Integer.parseInt(setting(env, "EMBEDDING_DIMENSION",
                Integer.toString(PetServiceConfig.DEFAULT_EMBEDDING_DIMENSION)));
        String openRouterKey = env.getOrDefault("PET_OPENROUTER_API_KEY", "");
        return new DevPlayerEnvironment(url, dbUser, required(env, "DEV_PLAYER_DB_PASSWORD"),
                qdrant, collectionFor(dimension),
                env.getOrDefault("DEV_PLAYER_QDRANT_API_KEY", ""),
                URI.create(env.getOrDefault("PET_OPENAI_BASE_URL", "https://api.openai.com")),
                required(env, "PET_OPENAI_API_KEY"),
                URI.create(env.getOrDefault("PET_OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1")),
                openRouterKey,
                !openRouterKey.isBlank() && !"false".equalsIgnoreCase(env.getOrDefault("PET_OPENROUTER_FALLBACK", "true")),
                model(env, "DIALOGUE_MODEL", PetServiceConfig.DEFAULT_CHAT_MODEL),
                model(env, "EMBEDDING_MODEL", PetServiceConfig.DEFAULT_EMBEDDING_MODEL),
                model(env, "CONSOLIDATION_MODEL", PetServiceConfig.DEFAULT_CHAT_MODEL),
                setting(env, "MODERATION_MODEL", PetServiceConfig.DEFAULT_MODERATION_MODEL),
                dimension,
                setting(env, "DIALOGUE_REASONING_EFFORT", "none"),
                model(env, "PLANNER_MODEL", PetServiceConfig.DEFAULT_CHAT_MODEL),
                model(env, "MEMORY_MODEL", PetServiceConfig.DEFAULT_CHAT_MODEL),
                blocked(setting(env, "MODERATION_BLOCKED_CATEGORIES", "")));
    }

    /** The isolated collection for one embedding dimension; never a production collection. */
    public static String collectionFor(int dimension) {
        if (dimension < 1 || dimension > 16_384) throw new IllegalArgumentException("dimension is invalid");
        return dimension == LEGACY_DIMENSION ? COLLECTION : COLLECTION + "_d" + dimension;
    }

    private static java.util.Set<String> blocked(String value) {
        if (value.isBlank()) return com.silver.aipets.service.dialogue.OpenAiModerationClient.DEFAULT_BLOCKED_CATEGORIES;
        return java.util.Arrays.stream(value.split(",")).map(String::strip).filter(item -> !item.isEmpty())
                .collect(java.util.stream.Collectors.toSet());
    }

    private static String model(Map<String, String> env, String name, String fallback) {
        return PetServiceConfig.openAiModel(setting(env, name, fallback), name);
    }

    private static String setting(Map<String, String> env, String name, String fallback) {
        String override = env.get("DEV_PLAYER_" + name);
        if (override != null) return override.strip();
        String service = env.get("PET_" + name);
        return service == null || service.isBlank() ? fallback : service.strip();
    }

    private static String required(Map<String, String> env, String key) {
        String value = env.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key);
        return value;
    }
}

package com.silver.aipets.service.devplayer;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

/** Fail-closed boundary for the operator-only, separate-process diagnostic CLI. */
public record DevPlayerEnvironment(String jdbcUrl, String dbUser, String dbPassword,
        URI qdrantUri, String qdrantCollection, String qdrantKey, URI openAiUri,
        String openAiKey, String dialogueModel, String embeddingModel,
        String consolidationModel, String moderationModel, int embeddingDimension) {
    public static final String DATABASE = "pet_companion_dev_player";
    public static final String COLLECTION = "pet_companion_dev_player_memories";

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
        return new DevPlayerEnvironment(url, dbUser, required(env, "DEV_PLAYER_DB_PASSWORD"),
                qdrant, collection,
                env.getOrDefault("DEV_PLAYER_QDRANT_API_KEY", ""),
                URI.create(env.getOrDefault("PET_OPENAI_BASE_URL", "https://api.openai.com")),
                required(env, "PET_OPENAI_API_KEY"),
                env.getOrDefault("PET_DIALOGUE_MODEL", "gpt-6-luna"),
                env.getOrDefault("PET_EMBEDDING_MODEL", "text-embedding-3-small"),
                env.getOrDefault("PET_CONSOLIDATION_MODEL", "gpt-6-luna"),
                env.getOrDefault("PET_MODERATION_MODEL", "omni-moderation-latest"),
                Integer.parseInt(env.getOrDefault("PET_EMBEDDING_DIMENSION", "1536")));
    }

    private static String required(Map<String, String> env, String key) {
        String value = env.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key);
        return value;
    }
}

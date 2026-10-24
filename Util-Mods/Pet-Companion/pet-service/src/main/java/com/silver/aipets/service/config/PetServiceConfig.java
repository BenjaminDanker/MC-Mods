package com.silver.aipets.service.config;

import java.net.URI;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.UUID;
import com.silver.aipets.common.domain.PetSpecies;
import com.silver.aipets.service.billing.AiPricing;

/** Environment-only service configuration. Secret values are never included in {@link #toString()}. */
public final class PetServiceConfig {
    public static final String TOKEN_ENV = "PET_SERVICE_TOKEN";
    public static final String JDBC_URL_ENV = "PET_DB_JDBC_URL";
    public static final String DB_USER_ENV = "PET_DB_USER";
    public static final String DB_PASSWORD_ENV = "PET_DB_PASSWORD";
    public static final String ALLOWED_SPECIES_ENV = "PET_ALLOWED_SPECIES";
    public static final String RAW_TEXT_RETENTION_DAYS_ENV = "PET_RAW_TEXT_RETENTION_DAYS";
    public static final String AI_ENABLED_ENV = "PET_AI_ENABLED";
    public static final String STRIPE_ENABLED_ENV = "PET_STRIPE_ENABLED";
    public static final String STRIPE_PRICE_ID_ENV = "PET_STRIPE_PRICE_ID";
    public static final String SUBSCRIPTION_GROSS_USD_ENV = "PET_SUBSCRIPTION_GROSS_USD";
    public static final String STRIPE_PAYMENT_PERCENT_ENV = "PET_STRIPE_PAYMENT_PERCENT";
    public static final String STRIPE_FIXED_FEE_USD_ENV = "PET_STRIPE_FIXED_FEE_USD";
    public static final String STRIPE_BILLING_PERCENT_ENV = "PET_STRIPE_BILLING_PERCENT";
    public static final String STRIPE_SECRET_KEY_ENV = "PET_STRIPE_SECRET_KEY";
    public static final String STRIPE_WEBHOOK_SECRET_ENV = "PET_STRIPE_WEBHOOK_SECRET";
    public static final String PUBLIC_BASE_URL_ENV = "PET_PUBLIC_BASE_URL";
    public static final String ACCOUNT_LINK_PEPPER_ENV = "PET_ACCOUNT_LINK_PEPPER";
    public static final String STRIPE_PAYMENT_GRACE_DAYS_ENV = "PET_STRIPE_PAYMENT_GRACE_DAYS";
    public static final String ACCOUNT_LINK_TTL_MINUTES_ENV = "PET_ACCOUNT_LINK_TTL_MINUTES";
    /** Explicitly scoped development substitute for Stripe entitlement during staging only. */
    public static final String DUMMY_SUBSCRIPTION_ENABLED_ENV = "PET_DUMMY_SUBSCRIPTION_ENABLED";
    public static final String DUMMY_SUBSCRIPTION_OWNER_UUID_ENV = "PET_DUMMY_SUBSCRIPTION_OWNER_UUID";
    public static final String QDRANT_ENABLED_ENV = "PET_QDRANT_ENABLED";
    public static final String QDRANT_URL_ENV = "PET_QDRANT_URL";
    public static final String QDRANT_COLLECTION_ENV = "PET_QDRANT_COLLECTION";
    public static final String QDRANT_DIMENSION_ENV = "PET_QDRANT_DIMENSION";
    public static final String QDRANT_API_KEY_ENV = "PET_QDRANT_API_KEY";
    public static final String QDRANT_TIMEOUT_MS_ENV = "PET_QDRANT_TIMEOUT_MS";
    /** OpenAI is the primary provider for chat, moderation, and embeddings. */
    public static final String OPENAI_API_KEY_ENV = "PET_OPENAI_API_KEY";
    public static final String OPENAI_BASE_URL_ENV = "PET_OPENAI_BASE_URL";
    public static final String MODERATION_MODEL_ENV = "PET_MODERATION_MODEL";
    /** Comma-separated omni-moderation categories that block a message; everything else passes. */
    public static final String MODERATION_BLOCKED_CATEGORIES_ENV = "PET_MODERATION_BLOCKED_CATEGORIES";
    /**
     * Optional OpenRouter key. When present (and PET_OPENROUTER_FALLBACK is not "false") a failed
     * OpenAI chat call is retried once on the same model through OpenRouter, charged at OpenRouter's
     * reported cost.
     */
    public static final String OPENROUTER_API_KEY_ENV = "PET_OPENROUTER_API_KEY";
    public static final String OPENROUTER_BASE_URL_ENV = "PET_OPENROUTER_BASE_URL";
    public static final String OPENROUTER_FALLBACK_ENV = "PET_OPENROUTER_FALLBACK";
    public static final String EMBEDDING_MODEL_ENV = "PET_EMBEDDING_MODEL";
    public static final String EMBEDDING_DIMENSION_ENV = "PET_EMBEDDING_DIMENSION";
    public static final String DIALOGUE_ENABLED_ENV = "PET_DIALOGUE_ENABLED";
    public static final String DIALOGUE_MODEL_ENV = "PET_DIALOGUE_MODEL";
    public static final String DIALOGUE_REASONING_EFFORT_ENV = "PET_DIALOGUE_REASONING_EFFORT";
    public static final String DIALOGUE_TIMEOUT_MS_ENV = "PET_DIALOGUE_TIMEOUT_MS";
    public static final String DIALOGUE_DAILY_REPLY_CAP_ENV = "PET_DIALOGUE_DAILY_REPLY_CAP";
    public static final String CONSOLIDATION_ENABLED_ENV = "PET_CONSOLIDATION_ENABLED";
    public static final String CONSOLIDATION_MODEL_ENV = "PET_CONSOLIDATION_MODEL";
    public static final String PLANNER_MODEL_ENV = "PET_PLANNER_MODEL";
    /** Structured model for the per-turn memory bookkeeping call; blank disables it. */
    public static final String MEMORY_MODEL_ENV = "PET_MEMORY_MODEL";
    /** USD per one million tokens for OpenAI calls, which report tokens but not dollars. */
    public static final String AI_PRICE_DIALOGUE_INPUT_ENV = "PET_AI_PRICE_DIALOGUE_INPUT_PER_MILLION";
    public static final String AI_PRICE_DIALOGUE_CACHED_ENV = "PET_AI_PRICE_DIALOGUE_CACHED_PER_MILLION";
    public static final String AI_PRICE_DIALOGUE_OUTPUT_ENV = "PET_AI_PRICE_DIALOGUE_OUTPUT_PER_MILLION";
    public static final String AI_PRICE_EMBEDDING_INPUT_ENV = "PET_AI_PRICE_EMBEDDING_INPUT_PER_MILLION";
    public static final String AI_HOLD_DIALOGUE_USD_ENV = "PET_AI_HOLD_DIALOGUE_USD";
    public static final String AI_HOLD_CONSOLIDATION_USD_ENV = "PET_AI_HOLD_CONSOLIDATION_USD";
    public static final String AI_HOLD_PLANNING_USD_ENV = "PET_AI_HOLD_PLANNING_USD";
    public static final String AI_HOLD_EMBEDDING_USD_ENV = "PET_AI_HOLD_EMBEDDING_USD";
    public static final String DEFAULT_CHAT_MODEL = "gpt-6-luna";
    public static final String DEFAULT_MODERATION_MODEL = "omni-moderation-latest";
    public static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-3-small";
    public static final int DEFAULT_EMBEDDING_DIMENSION = 1_536;

    private final String bindAddress;
    private final int port;
    private final int backlog;
    private final int workerThreads;
    private final int workerQueueCapacity;
    private final int shutdownGraceSeconds;
    private final String bearerToken;
    private final String jdbcUrl;
    private final String databaseUser;
    private final String databasePassword;
    private final int poolMaximumSize;
    private final int poolMinimumIdle;
    private final long connectionTimeoutMs;
    private final long validationTimeoutMs;
    private final Set<PetSpecies> allowedSpecies;
    private final int rawTextRetentionDays;
    private final boolean aiEnabled;
    private final boolean stripeEnabled;
    private final String stripePriceId;
    private final AiPricing aiPricing;
    private final String stripeSecretKey;
    private final String stripeWebhookSecret;
    private final URI publicBaseUri;
    private final String accountLinkPepper;
    private final int stripePaymentGraceDays;
    private final int accountLinkTtlMinutes;
    private final boolean dummySubscriptionEnabled;
    private final UUID dummySubscriptionOwnerUuid;
    private final boolean qdrantEnabled;
    private final URI qdrantUri;
    private final String qdrantCollection;
    private final int qdrantDimension;
    private final String qdrantApiKey;
    private final long qdrantTimeoutMs;
    private final String openAiApiKey;
    private final URI openAiBaseUri;
    private final String openRouterApiKey;
    private final URI openRouterBaseUri;
    private final boolean openRouterFallback;
    private final String moderationModel;
    private final java.util.Set<String> moderationBlockedCategories;
    private final String embeddingModel;
    private final int embeddingDimension;
    private final boolean dialogueEnabled;
    private final String dialogueModel;
    private final long dialogueTimeoutMs;
    private final int dialogueDailyReplyCap;
    private final boolean consolidationEnabled;
    private final String consolidationModel;
    private final String plannerModel;
    private final String memoryModel;
    private final String dialogueReasoningEffort;

    private PetServiceConfig(
            String bindAddress,
            int port,
            int backlog,
            int workerThreads,
            int workerQueueCapacity,
            int shutdownGraceSeconds,
            String bearerToken,
            String jdbcUrl,
            String databaseUser,
            String databasePassword,
            int poolMaximumSize,
            int poolMinimumIdle,
            long connectionTimeoutMs,
            long validationTimeoutMs,
            Set<PetSpecies> allowedSpecies,
            int rawTextRetentionDays,
            boolean aiEnabled,
            boolean stripeEnabled,
            String stripePriceId,
            AiPricing aiPricing,
            String stripeSecretKey,
            String stripeWebhookSecret,
            URI publicBaseUri,
            String accountLinkPepper,
            int stripePaymentGraceDays,
            int accountLinkTtlMinutes,
            boolean dummySubscriptionEnabled,
            UUID dummySubscriptionOwnerUuid,
            boolean qdrantEnabled,
            URI qdrantUri,
            String qdrantCollection,
            int qdrantDimension,
            String qdrantApiKey,
            long qdrantTimeoutMs,
            String openAiApiKey,
            URI openAiBaseUri,
            String openRouterApiKey,
            URI openRouterBaseUri,
            boolean openRouterFallback,
            String embeddingModel,
            int embeddingDimension,
            boolean dialogueEnabled,
            String dialogueModel,
            String moderationModel,
            java.util.Set<String> moderationBlockedCategories,
            long dialogueTimeoutMs,
            int dialogueDailyReplyCap,
            boolean consolidationEnabled,
            String consolidationModel,
            String plannerModel,
            String memoryModel,
            String dialogueReasoningEffort) {
        this.bindAddress = bindAddress;
        this.port = port;
        this.backlog = backlog;
        this.workerThreads = workerThreads;
        this.workerQueueCapacity = workerQueueCapacity;
        this.shutdownGraceSeconds = shutdownGraceSeconds;
        this.bearerToken = bearerToken;
        this.jdbcUrl = jdbcUrl;
        this.databaseUser = databaseUser;
        this.databasePassword = databasePassword;
        this.poolMaximumSize = poolMaximumSize;
        this.poolMinimumIdle = poolMinimumIdle;
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.validationTimeoutMs = validationTimeoutMs;
        this.allowedSpecies = Set.copyOf(allowedSpecies);
        this.rawTextRetentionDays = rawTextRetentionDays;
        this.aiEnabled = aiEnabled;
        this.stripeEnabled = stripeEnabled;
        this.stripePriceId = stripePriceId;
        this.aiPricing = aiPricing;
        this.stripeSecretKey = stripeSecretKey;
        this.stripeWebhookSecret = stripeWebhookSecret;
        this.publicBaseUri = publicBaseUri;
        this.accountLinkPepper = accountLinkPepper;
        this.stripePaymentGraceDays = stripePaymentGraceDays;
        this.accountLinkTtlMinutes = accountLinkTtlMinutes;
        this.dummySubscriptionEnabled = dummySubscriptionEnabled;
        this.dummySubscriptionOwnerUuid = dummySubscriptionOwnerUuid;
        this.qdrantEnabled = qdrantEnabled;
        this.qdrantUri = qdrantUri;
        this.qdrantCollection = qdrantCollection;
        this.qdrantDimension = qdrantDimension;
        this.qdrantApiKey = qdrantApiKey;
        this.qdrantTimeoutMs = qdrantTimeoutMs;
        this.openAiApiKey = openAiApiKey;
        this.openAiBaseUri = openAiBaseUri;
        this.openRouterApiKey = openRouterApiKey;
        this.openRouterBaseUri = openRouterBaseUri;
        this.openRouterFallback = openRouterFallback;
        this.moderationModel = moderationModel;
        this.moderationBlockedCategories = java.util.Set.copyOf(moderationBlockedCategories);
        this.embeddingModel = embeddingModel;
        this.embeddingDimension = embeddingDimension;
        this.dialogueEnabled = dialogueEnabled;
        this.dialogueModel = dialogueModel;
        this.dialogueTimeoutMs = dialogueTimeoutMs;
        this.dialogueDailyReplyCap = dialogueDailyReplyCap;
        this.consolidationEnabled = consolidationEnabled;
        this.consolidationModel = consolidationModel;
        this.plannerModel = plannerModel;
        this.memoryModel = memoryModel;
        this.dialogueReasoningEffort = dialogueReasoningEffort;
    }

    public static PetServiceConfig fromEnvironment(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment");
        String bindAddress = optional(environment, "PET_SERVICE_BIND_ADDRESS", "127.0.0.1");
        if (bindAddress.isBlank() || bindAddress.length() > 255 || containsWhitespace(bindAddress)
                || isWildcardBind(bindAddress)) {
            throw new IllegalArgumentException("PET_SERVICE_BIND_ADDRESS is invalid");
        }
        int port = integer(environment, "PET_SERVICE_PORT", 8787, 1, 65_535);
        int backlog = integer(environment, "PET_SERVICE_BACKLOG", 64, 1, 1_024);
        int workerThreads = integer(environment, "PET_SERVICE_WORKER_THREADS", 4, 1, 32);
        int workerQueue = integer(environment, "PET_SERVICE_WORKER_QUEUE", 128, 1, 4_096);
        int shutdownGrace = integer(environment, "PET_SERVICE_SHUTDOWN_GRACE_SECONDS", 10, 1, 60);

        String bearerToken = required(environment, TOKEN_ENV);
        if (bearerToken.length() < 32 || bearerToken.length() > 512) {
            throw new IllegalArgumentException(TOKEN_ENV + " must contain 32-512 characters");
        }
        String jdbcUrl = required(environment, JDBC_URL_ENV);
        validateJdbcUrl(jdbcUrl);
        String databaseUser = required(environment, DB_USER_ENV);
        String databasePassword = required(environment, DB_PASSWORD_ENV);
        if (databaseUser.length() > 128 || databasePassword.length() > 1_024) {
            throw new IllegalArgumentException("Database credentials exceed safe configured lengths");
        }

        int poolMaximum = integer(environment, "PET_DB_POOL_MAXIMUM", 4, 1, 16);
        int poolMinimum = integer(environment, "PET_DB_POOL_MINIMUM_IDLE", 1, 0, poolMaximum);
        long connectionTimeout = longValue(
                environment, "PET_DB_CONNECTION_TIMEOUT_MS", 3_000L, 250L, 30_000L);
        long validationTimeout = longValue(
                environment, "PET_DB_VALIDATION_TIMEOUT_MS", 1_000L, 250L, connectionTimeout);
        Set<PetSpecies> allowedSpecies = allowedSpecies(environment);
        int rawTextRetentionDays = integer(
                environment, RAW_TEXT_RETENTION_DAYS_ENV, 7, 1, 30);
        boolean aiEnabled = booleanValue(environment, AI_ENABLED_ENV, true);
        boolean stripeEnabled = booleanValue(environment, STRIPE_ENABLED_ENV, false);
        String stripePriceId = stripeEnabled ? required(environment, STRIPE_PRICE_ID_ENV) : "";
        AiPricing aiPricing = new AiPricing(
                decimal(environment, SUBSCRIPTION_GROSS_USD_ENV, "2.00"),
                decimal(environment, STRIPE_PAYMENT_PERCENT_ENV, "2.9"),
                decimal(environment, STRIPE_FIXED_FEE_USD_ENV, "0.30"),
                decimal(environment, STRIPE_BILLING_PERCENT_ENV, "0.7"),
                decimal(environment, AI_PRICE_DIALOGUE_INPUT_ENV, AiPricing.Defaults.DIALOGUE_INPUT.toPlainString()),
                decimal(environment, AI_PRICE_DIALOGUE_CACHED_ENV, AiPricing.Defaults.DIALOGUE_CACHED_INPUT.toPlainString()),
                decimal(environment, AI_PRICE_DIALOGUE_OUTPUT_ENV, AiPricing.Defaults.DIALOGUE_OUTPUT.toPlainString()),
                decimal(environment, AI_PRICE_EMBEDDING_INPUT_ENV, AiPricing.Defaults.EMBEDDING_INPUT.toPlainString()),
                decimal(environment, AI_HOLD_DIALOGUE_USD_ENV, AiPricing.Defaults.DIALOGUE_HOLD.toPlainString()),
                decimal(environment, AI_HOLD_CONSOLIDATION_USD_ENV, AiPricing.Defaults.CONSOLIDATION_HOLD.toPlainString()),
                decimal(environment, AI_HOLD_PLANNING_USD_ENV, AiPricing.Defaults.PLANNING_HOLD.toPlainString()),
                decimal(environment, AI_HOLD_EMBEDDING_USD_ENV, AiPricing.Defaults.EMBEDDING_HOLD.toPlainString()));
        String stripeSecretKey = stripeEnabled ? required(environment, STRIPE_SECRET_KEY_ENV) : "";
        String stripeWebhookSecret = stripeEnabled
                ? required(environment, STRIPE_WEBHOOK_SECRET_ENV) : "";
        URI publicBaseUri = stripeEnabled
                ? validatePublicBaseUri(required(environment, PUBLIC_BASE_URL_ENV))
                : URI.create("https://disabled.invalid");
        String accountLinkPepper = stripeEnabled
                ? required(environment, ACCOUNT_LINK_PEPPER_ENV) : "";
        int stripePaymentGraceDays = integer(
                environment, STRIPE_PAYMENT_GRACE_DAYS_ENV, 3, 0, 14);
        int accountLinkTtlMinutes = integer(
                environment, ACCOUNT_LINK_TTL_MINUTES_ENV, 15, 2, 30);
        if (stripeEnabled) {
            validateStripeConfiguration(
                    stripePriceId, stripeSecretKey, stripeWebhookSecret, accountLinkPepper);
        }
        boolean dummySubscriptionEnabled = booleanValue(
                environment, DUMMY_SUBSCRIPTION_ENABLED_ENV, false);
        UUID dummySubscriptionOwnerUuid = dummySubscriptionEnabled
                ? uuid(environment, DUMMY_SUBSCRIPTION_OWNER_UUID_ENV)
                : null;
        if (dummySubscriptionEnabled && stripeEnabled) {
            throw new IllegalArgumentException(
                    DUMMY_SUBSCRIPTION_ENABLED_ENV + " cannot be combined with "
                            + STRIPE_ENABLED_ENV);
        }
        boolean qdrantEnabled = booleanValue(environment, QDRANT_ENABLED_ENV, false);
        URI qdrantUri = qdrantEnabled
                ? validateQdrantUri(required(environment, QDRANT_URL_ENV))
                : URI.create("http://disabled.invalid");
        String qdrantCollection = qdrantEnabled
                ? optional(environment, QDRANT_COLLECTION_ENV, "aipets_memories") : "";
        if (qdrantEnabled && !qdrantCollection.matches("[A-Za-z0-9_-]{1,100}")) {
            throw new IllegalArgumentException(QDRANT_COLLECTION_ENV + " contains unsupported characters");
        }
        int qdrantDimension = qdrantEnabled
                ? integer(environment, QDRANT_DIMENSION_ENV, DEFAULT_EMBEDDING_DIMENSION, 1, 16_384) : 0;
        String qdrantApiKey = qdrantEnabled ? optional(environment, QDRANT_API_KEY_ENV, "") : "";
        if (qdrantApiKey.length() > 512 || containsWhitespace(qdrantApiKey)) {
            throw new IllegalArgumentException(QDRANT_API_KEY_ENV + " is invalid");
        }
        long qdrantTimeoutMs = longValue(
                environment, QDRANT_TIMEOUT_MS_ENV, 2_000L, 250L, 30_000L);
        String embeddingModel = openAiModel(optional(environment, EMBEDDING_MODEL_ENV, DEFAULT_EMBEDDING_MODEL),
                EMBEDDING_MODEL_ENV);
        validateModelName(embeddingModel, EMBEDDING_MODEL_ENV);
        int embeddingDimension = integer(
                environment, EMBEDDING_DIMENSION_ENV, DEFAULT_EMBEDDING_DIMENSION, 1, 16_384);
        boolean dialogueEnabled = booleanValue(environment, DIALOGUE_ENABLED_ENV, false);
        boolean consolidationEnabled = booleanValue(environment, CONSOLIDATION_ENABLED_ENV, false);
        String openAiApiKey = qdrantEnabled || dialogueEnabled || consolidationEnabled
                ? required(environment, OPENAI_API_KEY_ENV)
                : optional(environment, OPENAI_API_KEY_ENV, "");
        if (openAiApiKey.length() > 512 || containsWhitespace(openAiApiKey)) {
            throw new IllegalArgumentException(OPENAI_API_KEY_ENV + " is invalid");
        }
        URI openAiBaseUri = validateHttpOrigin(
                optional(environment, OPENAI_BASE_URL_ENV, "https://api.openai.com"), OPENAI_BASE_URL_ENV);
        String openRouterApiKey = optional(environment, OPENROUTER_API_KEY_ENV, "");
        if (openRouterApiKey.length() > 512 || containsWhitespace(openRouterApiKey)) {
            throw new IllegalArgumentException(OPENROUTER_API_KEY_ENV + " is invalid");
        }
        URI openRouterBaseUri = validateHttpBase(
                optional(environment, OPENROUTER_BASE_URL_ENV, "https://openrouter.ai/api/v1"),
                OPENROUTER_BASE_URL_ENV);
        boolean openRouterFallback = !openRouterApiKey.isEmpty()
                && booleanValue(environment, OPENROUTER_FALLBACK_ENV, true);
        if (qdrantEnabled && qdrantDimension != embeddingDimension) {
            throw new IllegalArgumentException(
                    QDRANT_DIMENSION_ENV + " must equal " + EMBEDDING_DIMENSION_ENV);
        }
        String dialogueModel = openAiModel(optional(environment, DIALOGUE_MODEL_ENV, DEFAULT_CHAT_MODEL),
                DIALOGUE_MODEL_ENV);
        String moderationModel = optional(environment, MODERATION_MODEL_ENV, DEFAULT_MODERATION_MODEL);
        validateModelName(moderationModel, MODERATION_MODEL_ENV);
        java.util.Set<String> moderationBlockedCategories = Arrays.stream(optional(environment,
                        MODERATION_BLOCKED_CATEGORIES_ENV, String.join(",",
                                new java.util.TreeSet<>(com.silver.aipets.service.dialogue.OpenAiModerationClient
                                        .DEFAULT_BLOCKED_CATEGORIES))).split(","))
                .map(String::strip).filter(item -> !item.isEmpty()).collect(java.util.stream.Collectors.toSet());
        if (!com.silver.aipets.service.dialogue.OpenAiModerationClient.KNOWN_CATEGORIES
                .containsAll(moderationBlockedCategories)) {
            throw new IllegalArgumentException(MODERATION_BLOCKED_CATEGORIES_ENV + " contains an unknown category");
        }
        long dialogueTimeoutMs = longValue(
                environment, DIALOGUE_TIMEOUT_MS_ENV, 10_000L, 1_000L, 60_000L);
        int dialogueDailyReplyCap = integer(
                environment, DIALOGUE_DAILY_REPLY_CAP_ENV, 100, 1, 10_000);
        validateModelName(dialogueModel, DIALOGUE_MODEL_ENV);
        // "none" turns reasoning off for OpenAI reasoning models; blank omits the parameter.
        String dialogueReasoningEffort = environment.containsKey(DIALOGUE_REASONING_EFFORT_ENV)
                ? environment.get(DIALOGUE_REASONING_EFFORT_ENV).strip().toLowerCase(Locale.ROOT) : "none";
        if (!dialogueReasoningEffort.isEmpty()
                && !Set.of("none", "minimal", "low", "medium", "high").contains(dialogueReasoningEffort)) {
            throw new IllegalArgumentException(DIALOGUE_REASONING_EFFORT_ENV + " is invalid");
        }
        String consolidationModel = openAiModel(optional(environment, CONSOLIDATION_MODEL_ENV, DEFAULT_CHAT_MODEL),
                CONSOLIDATION_MODEL_ENV);
        validateModelName(consolidationModel, CONSOLIDATION_MODEL_ENV);
        String plannerModel = openAiModel(optional(environment, PLANNER_MODEL_ENV, DEFAULT_CHAT_MODEL),
                PLANNER_MODEL_ENV);
        validateModelName(plannerModel, PLANNER_MODEL_ENV);
        String memoryModel = environment.containsKey(MEMORY_MODEL_ENV)
                ? openAiModel(environment.get(MEMORY_MODEL_ENV), MEMORY_MODEL_ENV) : DEFAULT_CHAT_MODEL;
        if (!memoryModel.isEmpty()) validateModelName(memoryModel, MEMORY_MODEL_ENV);

        return new PetServiceConfig(
                bindAddress, port, backlog, workerThreads, workerQueue, shutdownGrace,
                bearerToken, jdbcUrl, databaseUser, databasePassword,
                poolMaximum, poolMinimum, connectionTimeout, validationTimeout,
                allowedSpecies, rawTextRetentionDays, aiEnabled,
                stripeEnabled, stripePriceId, aiPricing, stripeSecretKey, stripeWebhookSecret,
                publicBaseUri, accountLinkPepper,
                stripePaymentGraceDays, accountLinkTtlMinutes,
                dummySubscriptionEnabled, dummySubscriptionOwnerUuid,
                qdrantEnabled, qdrantUri, qdrantCollection, qdrantDimension,
                qdrantApiKey, qdrantTimeoutMs,
                openAiApiKey, openAiBaseUri, openRouterApiKey, openRouterBaseUri, openRouterFallback,
                embeddingModel, embeddingDimension,
                dialogueEnabled, dialogueModel, moderationModel, moderationBlockedCategories, dialogueTimeoutMs,
                dialogueDailyReplyCap,
                consolidationEnabled, consolidationModel, plannerModel, memoryModel,
                dialogueReasoningEffort);
    }

    private static URI validateQdrantUri(String encoded) {
        return validateHttpOrigin(encoded, QDRANT_URL_ENV);
    }

    /**
     * The OpenAI model id for a configured name. An {@code openai/} prefix (the OpenRouter spelling)
     * is accepted and stripped; a model from another vendor cannot be served by the primary provider.
     */
    public static String openAiModel(String value, String environmentName) {
        Objects.requireNonNull(value, "value");
        String normalized = value.strip();
        if (normalized.startsWith("openai/")) normalized = normalized.substring("openai/".length());
        if (normalized.contains("/")) {
            throw new IllegalArgumentException(environmentName + " must name an OpenAI model");
        }
        return normalized;
    }

    /** The same model's OpenRouter id, used by the fallback provider. */
    public static String openRouterModel(String openAiModel) {
        Objects.requireNonNull(openAiModel, "openAiModel");
        String normalized = openAiModel.strip();
        return normalized.isEmpty() || normalized.contains("/") ? normalized : "openai/" + normalized;
    }

    private static void validateModelName(String value, String environmentName) {
        if (value.isBlank() || value.length() > 191 || containsWhitespace(value)) {
            throw new IllegalArgumentException(environmentName + " is invalid");
        }
    }

    private static UUID uuid(Map<String, String> environment, String name) {
        String encoded = required(environment, name);
        try {
            return UUID.fromString(encoded);
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException(name + " must be a UUID", malformed);
        }
    }

    /** An HTTP(S) API base such as {@code https://openrouter.ai/api/v1}: a path is allowed, nothing else. */
    private static URI validateHttpBase(String encoded, String environmentName) {
        final URI uri;
        try {
            uri = URI.create(encoded);
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException(environmentName + " is malformed", malformed);
        }
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException(
                    environmentName + " must be an HTTP(S) URL without credentials/query/fragment");
        }
        String normalized = uri.toString();
        return URI.create(normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized);
    }

    private static URI validateHttpOrigin(String encoded, String environmentName) {
        final URI uri;
        try {
            uri = URI.create(encoded);
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException(environmentName + " is malformed", malformed);
        }
        if (!("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !(uri.getRawPath() == null || uri.getRawPath().isEmpty()
                     || "/".equals(uri.getRawPath()))) {
            throw new IllegalArgumentException(
                    environmentName + " must be an HTTP(S) origin without credentials/path/query/fragment");
        }
        String normalized = uri.toString();
        return URI.create(normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1) : normalized);
    }

    private static void validateStripeConfiguration(
            String priceId,
            String secretKey,
            String webhookSecret,
            String accountLinkPepper) {
        if (!priceId.startsWith("price_") || priceId.length() > 255
                || containsWhitespace(priceId)) {
            throw new IllegalArgumentException(
                    STRIPE_PRICE_ID_ENV + " must be a Stripe Price ID");
        }
        if (!(secretKey.startsWith("sk_test_") || secretKey.startsWith("sk_live_"))
                || secretKey.length() < 16 || secretKey.length() > 512
                || containsWhitespace(secretKey)) {
            throw new IllegalArgumentException(
                    STRIPE_SECRET_KEY_ENV + " must be a Stripe secret key");
        }
        if (!webhookSecret.startsWith("whsec_") || webhookSecret.length() < 16
                || webhookSecret.length() > 512 || containsWhitespace(webhookSecret)) {
            throw new IllegalArgumentException(
                    STRIPE_WEBHOOK_SECRET_ENV + " must be a Stripe webhook signing secret");
        }
        if (accountLinkPepper.length() < 32 || accountLinkPepper.length() > 512) {
            throw new IllegalArgumentException(
                    ACCOUNT_LINK_PEPPER_ENV + " must contain 32-512 characters");
        }
    }

    private static URI validatePublicBaseUri(String encoded) {
        final URI uri;
        try {
            uri = URI.create(encoded);
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException(PUBLIC_BASE_URL_ENV + " is malformed", malformed);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || !(uri.getRawPath() == null || uri.getRawPath().isEmpty()
                     || "/".equals(uri.getRawPath()))) {
            throw new IllegalArgumentException(
                    PUBLIC_BASE_URL_ENV + " must be an HTTPS origin without path/query/fragment");
        }
        String normalized = uri.toString();
        return URI.create(normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1) : normalized);
    }

    private static Set<PetSpecies> allowedSpecies(Map<String, String> environment) {
        String encoded = optional(
                environment, ALLOWED_SPECIES_ENV, "minecraft:cat,minecraft:wolf");
        Set<PetSpecies> parsed = new LinkedHashSet<>();
        for (String raw : encoded.split(",", -1)) {
            String value = raw.trim().toLowerCase(Locale.ROOT);
            PetSpecies species = switch (value) {
                case "minecraft:cat" -> PetSpecies.CAT;
                case "minecraft:wolf" -> PetSpecies.DOG;
                default -> throw new IllegalArgumentException(
                        ALLOWED_SPECIES_ENV + " contains unsupported species: " + raw);
            };
            if (!parsed.add(species)) {
                throw new IllegalArgumentException(ALLOWED_SPECIES_ENV + " contains a duplicate");
            }
        }
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException(ALLOWED_SPECIES_ENV + " must not be empty");
        }
        return Set.copyOf(parsed);
    }

    private static void validateJdbcUrl(String jdbcUrl) {
        if (!jdbcUrl.startsWith("jdbc:mariadb://")) {
            throw new IllegalArgumentException(JDBC_URL_ENV + " must use jdbc:mariadb://");
        }
        final URI uri;
        try {
            uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException(JDBC_URL_ENV + " is malformed", malformed);
        }
        if (uri.getHost() == null
                || uri.getRawUserInfo() != null
                || uri.getRawFragment() != null
                || uri.getRawPath() == null
                || uri.getRawPath().length() < 2) {
            throw new IllegalArgumentException(
                    JDBC_URL_ENV + " must name a host/database and contain no credentials or fragment");
        }
        if (uri.getRawQuery() != null) {
            boolean secretParameter = Arrays.stream(uri.getRawQuery().split("&"))
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .anyMatch(value -> value.startsWith("user=")
                            || value.startsWith("password=")
                            || value.startsWith("credential="));
            if (secretParameter) {
                throw new IllegalArgumentException(
                        JDBC_URL_ENV + " must not embed database credentials");
            }
        }
    }

    private static String required(Map<String, String> environment, String key) {
        String value = environment.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required and must not be blank");
        }
        return value;
    }

    private static String optional(Map<String, String> environment, String key, String fallback) {
        String value = environment.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int integer(
            Map<String, String> environment,
            String key,
            int fallback,
            int minimum,
            int maximum) {
        return Math.toIntExact(longValue(environment, key, fallback, minimum, maximum));
    }

    private static long longValue(
            Map<String, String> environment,
            String key,
            long fallback,
            long minimum,
            long maximum) {
        String encoded = environment.get(key);
        final long value;
        try {
            value = encoded == null || encoded.isBlank() ? fallback : Long.parseLong(encoded);
        } catch (NumberFormatException malformed) {
            throw new IllegalArgumentException(key + " must be an integer", malformed);
        }
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(key + " must be in " + minimum + ".." + maximum);
        }
        return value;
    }

    private static BigDecimal decimal(
            Map<String, String> environment, String key, String fallback) {
        String encoded = environment.get(key);
        try {
            BigDecimal value = encoded == null || encoded.isBlank()
                    ? new BigDecimal(fallback) : new BigDecimal(encoded.trim());
            if (value.scale() > 8 || value.signum() < 0) {
                throw new IllegalArgumentException(key + " must be a non-negative decimal with <= 8 decimals");
            }
            return value;
        } catch (NumberFormatException malformed) {
            throw new IllegalArgumentException(key + " must be a decimal", malformed);
        }
    }

    private static boolean containsWhitespace(String value) {
        return value.chars().anyMatch(Character::isWhitespace);
    }

    private static boolean isWildcardBind(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "0.0.0.0", "::", "[::]", "0:0:0:0:0:0:0:0" -> true;
            default -> false;
        };
    }

    private static boolean booleanValue(
            Map<String, String> environment, String key, boolean fallback) {
        String encoded = environment.get(key);
        if (encoded == null || encoded.isBlank()) return fallback;
        return switch (encoded.trim().toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException(key + " must be true or false");
        };
    }

    public String bindAddress() {
        return bindAddress;
    }

    public int port() {
        return port;
    }

    public int backlog() {
        return backlog;
    }

    public int workerThreads() {
        return workerThreads;
    }

    public int workerQueueCapacity() {
        return workerQueueCapacity;
    }

    public int shutdownGraceSeconds() {
        return shutdownGraceSeconds;
    }

    public String bearerToken() {
        return bearerToken;
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public String databaseUser() {
        return databaseUser;
    }

    public String databasePassword() {
        return databasePassword;
    }

    public int poolMaximumSize() {
        return poolMaximumSize;
    }

    public int poolMinimumIdle() {
        return poolMinimumIdle;
    }

    public long connectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public long validationTimeoutMs() {
        return validationTimeoutMs;
    }

    public Set<PetSpecies> allowedSpecies() {
        return allowedSpecies;
    }

    public int rawTextRetentionDays() {
        return rawTextRetentionDays;
    }

    public boolean aiEnabled() {
        return aiEnabled;
    }

    public boolean stripeEnabled() {
        return stripeEnabled;
    }

    public boolean dummySubscriptionEnabled() {
        return dummySubscriptionEnabled;
    }

    public UUID dummySubscriptionOwnerUuid() {
        return dummySubscriptionOwnerUuid;
    }

    public String stripePriceId() {
        return stripePriceId;
    }

    public AiPricing aiPricing() {
        return aiPricing;
    }

    public String stripeSecretKey() {
        return stripeSecretKey;
    }

    public String stripeWebhookSecret() {
        return stripeWebhookSecret;
    }

    public URI publicBaseUri() {
        return publicBaseUri;
    }

    public String accountLinkPepper() {
        return accountLinkPepper;
    }

    public int stripePaymentGraceDays() {
        return stripePaymentGraceDays;
    }

    public int accountLinkTtlMinutes() {
        return accountLinkTtlMinutes;
    }

    public boolean qdrantEnabled() {
        return qdrantEnabled;
    }

    public URI qdrantUri() {
        return qdrantUri;
    }

    public String qdrantCollection() {
        return qdrantCollection;
    }

    public int qdrantDimension() {
        return qdrantDimension;
    }

    public String qdrantApiKey() {
        return qdrantApiKey;
    }

    public long qdrantTimeoutMs() {
        return qdrantTimeoutMs;
    }

    public String openAiApiKey() {
        return openAiApiKey;
    }

    public URI openAiBaseUri() {
        return openAiBaseUri;
    }

    public String moderationModel() {
        return moderationModel;
    }

    public java.util.Set<String> moderationBlockedCategories() {
        return moderationBlockedCategories;
    }

    /** True when a failed OpenAI chat or embedding call is retried through OpenRouter. */
    public boolean openRouterFallback() {
        return openRouterFallback;
    }

    public String openRouterApiKey() {
        return openRouterApiKey;
    }

    public URI openRouterBaseUri() {
        return openRouterBaseUri;
    }

    public String embeddingModel() {
        return embeddingModel;
    }

    public int embeddingDimension() {
        return embeddingDimension;
    }

    public boolean dialogueEnabled() {
        return dialogueEnabled;
    }

    public String dialogueModel() {
        return dialogueModel;
    }

    public long dialogueTimeoutMs() {
        return dialogueTimeoutMs;
    }

    public int dialogueDailyReplyCap() {
        return dialogueDailyReplyCap;
    }

    public boolean consolidationEnabled() {
        return consolidationEnabled;
    }

    public String consolidationModel() {
        return consolidationModel;
    }

    public String plannerModel() {
        return plannerModel;
    }

    /** Empty when the memory bookkeeping call is disabled. */
    public String memoryModel() {
        return memoryModel;
    }

    /** Empty when the reasoning parameter is omitted. */
    public String dialogueReasoningEffort() {
        return dialogueReasoningEffort;
    }

    @Override
    public String toString() {
        return "PetServiceConfig[bindAddress=" + bindAddress
                + ", port=" + port
                + ", backlog=" + backlog
                + ", workerThreads=" + workerThreads
                + ", workerQueueCapacity=" + workerQueueCapacity
                + ", poolMaximumSize=" + poolMaximumSize
                + ", poolMinimumIdle=" + poolMinimumIdle
                + ", connectionTimeoutMs=" + connectionTimeoutMs
                + ", validationTimeoutMs=" + validationTimeoutMs
                + ", allowedSpecies=" + allowedSpecies
                + ", rawTextRetentionDays=" + rawTextRetentionDays
                + ", aiEnabled=" + aiEnabled
                + ", stripeEnabled=" + stripeEnabled
                + ", subscriptionGrossUsd=" + aiPricing.subscriptionGrossUsd()
                + ", aiNetBudgetUsd=" + aiPricing.netBudgetUsd()
                + ", dummySubscriptionEnabled=" + dummySubscriptionEnabled
                + ", qdrantEnabled=" + qdrantEnabled
                + ", qdrantCollection=" + qdrantCollection
                + ", qdrantDimension=" + qdrantDimension
                + ", qdrantTimeoutMs=" + qdrantTimeoutMs
                + ", embeddingModel=" + embeddingModel
                + ", embeddingDimension=" + embeddingDimension
                + ", dialogueEnabled=" + dialogueEnabled
                + ", dialogueModel=" + dialogueModel
                + ", dialogueReasoningEffort=" + dialogueReasoningEffort
                + ", moderationModel=" + moderationModel
                + ", moderationBlockedCategories=" + new java.util.TreeSet<>(moderationBlockedCategories)
                + ", openRouterFallback=" + openRouterFallback
                + ", dialogueTimeoutMs=" + dialogueTimeoutMs
                + ", dialogueDailyReplyCap=" + dialogueDailyReplyCap
                + ", consolidationEnabled=" + consolidationEnabled
                + ", consolidationModel=" + consolidationModel
                + ", plannerModel=" + plannerModel
                + ", memoryModel=" + memoryModel
                + ", stripePaymentGraceDays=" + stripePaymentGraceDays
                + ", accountLinkTtlMinutes=" + accountLinkTtlMinutes
                + "]";
    }
}

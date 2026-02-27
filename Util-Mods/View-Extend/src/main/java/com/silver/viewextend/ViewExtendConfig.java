package com.silver.viewextend;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record ViewExtendConfig(
    int unsimulatedViewDistance,
    int maxChunksPerPlayerPerTick,
    int maxMainThreadPreparedChunksPerTick,
    int maxUnloadsPerPlayerPerTick,
    int lod1StartDistance,
    int lod1TopNonAirSections,
    int unloadBufferChunks,
    int unloadGraceTicks,
    int pendingChunksHardLimit,
    int payloadCacheTtlTicks,
    int payloadCacheMaxEntriesPerPlayer,
    int clientReportedViewDistanceHardCap,
    int globalPacketTemplateCacheMaxEntries,
    int maxNbtReadsPerTick,
    int preparedQueueHardLimit,
    int tickInterval,
    boolean metricsInfoLogsEnabled,
    int metricsLogIntervalTicks,
    int fallbackSkyLightLevel,
    int fallbackBlockLightLevel,
    boolean oceanFallbackEnabled,
    int oceanFallbackSkyLightLevel) {
    private static final String FILE_NAME = "viewextend.properties";
    private static final String UNSIMULATED_VIEW_DISTANCE = "unsimulated-view-distance";
    private static final String MAX_CHUNKS_PER_PLAYER_PER_TICK = "max-chunks-per-player-per-tick";
    private static final String MAX_MAIN_THREAD_PREPARED_CHUNKS_PER_TICK = "max-main-thread-prepared-chunks-per-tick";
    private static final String MAX_UNLOADS_PER_PLAYER_PER_TICK = "max-unloads-per-player-per-tick";
    private static final String LOD1_START_DISTANCE = "lod1-start-distance";
    private static final String LOD1_TOP_NON_AIR_SECTIONS = "lod1-top-non-air-sections";
    private static final String UNLOAD_BUFFER_CHUNKS = "unload-buffer-chunks";
    private static final String UNLOAD_GRACE_TICKS = "unload-grace-ticks";
    private static final String PENDING_CHUNKS_HARD_LIMIT = "pending-chunks-hard-limit";
    private static final String PAYLOAD_CACHE_TTL_TICKS = "payload-cache-ttl-ticks";
    private static final String PAYLOAD_CACHE_MAX_ENTRIES_PER_PLAYER = "payload-cache-max-entries-per-player";
    private static final String CLIENT_REPORTED_VIEW_DISTANCE_HARD_CAP = "client-reported-view-distance-hard-cap";
    private static final String GLOBAL_PACKET_TEMPLATE_CACHE_MAX_ENTRIES = "global-packet-template-cache-max-entries";
    private static final String MAX_NBT_READS_PER_TICK = "max-nbt-reads-per-tick";
    private static final String PREPARED_QUEUE_HARD_LIMIT = "prepared-queue-hard-limit";
    private static final String TICK_INTERVAL = "tick-interval";
    private static final String METRICS_INFO_LOGS_ENABLED = "metrics-info-logs-enabled";
    private static final String METRICS_LOG_INTERVAL_TICKS = "metrics-log-interval-ticks";
    private static final String FALLBACK_SKY_LIGHT_LEVEL = "fallback-sky-light-level";
    private static final String FALLBACK_BLOCK_LIGHT_LEVEL = "fallback-block-light-level";
    private static final String OCEAN_FALLBACK_ENABLED = "ocean-fallback-enabled";
    private static final String OCEAN_FALLBACK_SKY_LIGHT_LEVEL = "ocean-fallback-sky-light-level";

    private static final int DEFAULT_UNSIMULATED_VIEW_DISTANCE = 96;
    private static final int DEFAULT_MAX_CHUNKS_PER_PLAYER_PER_TICK = 16;
    private static final int DEFAULT_MAX_MAIN_THREAD_PREPARED_CHUNKS_PER_TICK = 48;
    private static final int DEFAULT_MAX_UNLOADS_PER_PLAYER_PER_TICK = 1024;
    private static final int DEFAULT_LOD1_START_DISTANCE = 48;
    private static final int DEFAULT_LOD1_TOP_NON_AIR_SECTIONS = 4;
    private static final int DEFAULT_UNLOAD_BUFFER_CHUNKS = 2;
    private static final int DEFAULT_UNLOAD_GRACE_TICKS = 300;
    private static final int DEFAULT_PENDING_CHUNKS_HARD_LIMIT = 32768;
    private static final int DEFAULT_PAYLOAD_CACHE_TTL_TICKS = 600;
    private static final int DEFAULT_PAYLOAD_CACHE_MAX_ENTRIES_PER_PLAYER = 1024;
    private static final int DEFAULT_CLIENT_REPORTED_VIEW_DISTANCE_HARD_CAP = 96;
    private static final int DEFAULT_GLOBAL_PACKET_TEMPLATE_CACHE_MAX_ENTRIES = 65536;
    private static final int DEFAULT_MAX_NBT_READS_PER_TICK = 256;
    private static final int DEFAULT_PREPARED_QUEUE_HARD_LIMIT = 10000;
    private static final int DEFAULT_TICK_INTERVAL = 2;
    private static final boolean DEFAULT_METRICS_INFO_LOGS_ENABLED = true;
    private static final int DEFAULT_METRICS_LOG_INTERVAL_TICKS = 100;
    private static final int DEFAULT_FALLBACK_SKY_LIGHT_LEVEL = 15;
    private static final int DEFAULT_FALLBACK_BLOCK_LIGHT_LEVEL = 0;
    private static final boolean DEFAULT_OCEAN_FALLBACK_ENABLED = false;
    private static final int DEFAULT_OCEAN_FALLBACK_SKY_LIGHT_LEVEL = 0;

    public static ViewExtendConfig load() {
        Path configDir = Path.of("config");
        Path configPath = configDir.resolve(FILE_NAME);
        Properties properties = new Properties();

        if (Files.exists(configPath)) {
            try (InputStream input = Files.newInputStream(configPath)) {
                properties.load(input);
            } catch (IOException exception) {
                ViewExtendMod.LOGGER.error("Failed to read {}. Using defaults.", configPath, exception);
            }
        }

        int unsimulated = parseInt(
                properties,
                UNSIMULATED_VIEW_DISTANCE,
                DEFAULT_UNSIMULATED_VIEW_DISTANCE,
                0,
            Integer.MAX_VALUE);
        int maxPerTick = parseInt(
                properties,
                MAX_CHUNKS_PER_PLAYER_PER_TICK,
                DEFAULT_MAX_CHUNKS_PER_PLAYER_PER_TICK,
                1,
                128);
        int maxMainThreadPreparedPerTick = parseInt(
            properties,
            MAX_MAIN_THREAD_PREPARED_CHUNKS_PER_TICK,
            DEFAULT_MAX_MAIN_THREAD_PREPARED_CHUNKS_PER_TICK,
            1,
            2048);
        int maxUnloadsPerTick = parseInt(
            properties,
            MAX_UNLOADS_PER_PLAYER_PER_TICK,
            DEFAULT_MAX_UNLOADS_PER_PLAYER_PER_TICK,
            1,
            1024);
        int lod1StartDistance = parseInt(
            properties,
            LOD1_START_DISTANCE,
            DEFAULT_LOD1_START_DISTANCE,
            1,
            4096);
        int lod1TopNonAirSections = parseInt(
            properties,
            LOD1_TOP_NON_AIR_SECTIONS,
            DEFAULT_LOD1_TOP_NON_AIR_SECTIONS,
            1,
            24);
        int unloadBufferChunks = parseInt(
            properties,
            UNLOAD_BUFFER_CHUNKS,
            DEFAULT_UNLOAD_BUFFER_CHUNKS,
            0,
            16);
        int unloadGraceTicks = parseInt(
            properties,
            UNLOAD_GRACE_TICKS,
            DEFAULT_UNLOAD_GRACE_TICKS,
            0,
            72000);
        int pendingChunksHardLimit = parseInt(
            properties,
            PENDING_CHUNKS_HARD_LIMIT,
            DEFAULT_PENDING_CHUNKS_HARD_LIMIT,
            128,
            1048576);
        int payloadCacheTtlTicks = parseInt(
            properties,
            PAYLOAD_CACHE_TTL_TICKS,
            DEFAULT_PAYLOAD_CACHE_TTL_TICKS,
            0,
            72000);
        int payloadCacheMaxEntriesPerPlayer = parseInt(
            properties,
            PAYLOAD_CACHE_MAX_ENTRIES_PER_PLAYER,
            DEFAULT_PAYLOAD_CACHE_MAX_ENTRIES_PER_PLAYER,
            0,
            65536);
        int clientReportedViewDistanceHardCap = parseInt(
            properties,
            CLIENT_REPORTED_VIEW_DISTANCE_HARD_CAP,
            DEFAULT_CLIENT_REPORTED_VIEW_DISTANCE_HARD_CAP,
            0,
            4096);
        int globalPacketTemplateCacheMaxEntries = parseInt(
            properties,
            GLOBAL_PACKET_TEMPLATE_CACHE_MAX_ENTRIES,
            DEFAULT_GLOBAL_PACKET_TEMPLATE_CACHE_MAX_ENTRIES,
            0,
            1048576);
        int maxNbtReadsPerTick = parseInt(
            properties,
            MAX_NBT_READS_PER_TICK,
            DEFAULT_MAX_NBT_READS_PER_TICK,
            1,
            10000);
        int preparedQueueHardLimit = parseInt(
            properties,
            PREPARED_QUEUE_HARD_LIMIT,
            DEFAULT_PREPARED_QUEUE_HARD_LIMIT,
            0,
            500000);
        int tickInterval = parseInt(properties, TICK_INTERVAL, DEFAULT_TICK_INTERVAL, 1, 20);
        boolean metricsInfoLogsEnabled = parseBoolean(
            properties,
            METRICS_INFO_LOGS_ENABLED,
            DEFAULT_METRICS_INFO_LOGS_ENABLED);
        int metricsLogIntervalTicks = parseInt(
            properties,
            METRICS_LOG_INTERVAL_TICKS,
            DEFAULT_METRICS_LOG_INTERVAL_TICKS,
            20,
            72000);
        int fallbackSkyLightLevel = parseInt(
            properties,
            FALLBACK_SKY_LIGHT_LEVEL,
            DEFAULT_FALLBACK_SKY_LIGHT_LEVEL,
            0,
            15);
        int fallbackBlockLightLevel = parseInt(
            properties,
            FALLBACK_BLOCK_LIGHT_LEVEL,
            DEFAULT_FALLBACK_BLOCK_LIGHT_LEVEL,
            0,
            15);
        boolean oceanFallbackEnabled = parseBoolean(
            properties,
            OCEAN_FALLBACK_ENABLED,
            DEFAULT_OCEAN_FALLBACK_ENABLED);
        int oceanFallbackSkyLightLevel = parseInt(
            properties,
            OCEAN_FALLBACK_SKY_LIGHT_LEVEL,
            DEFAULT_OCEAN_FALLBACK_SKY_LIGHT_LEVEL,
            0,
            15);

        ViewExtendConfig config = new ViewExtendConfig(
            unsimulated,
            maxPerTick,
            maxMainThreadPreparedPerTick,
            maxUnloadsPerTick,
            lod1StartDistance,
            lod1TopNonAirSections,
            unloadBufferChunks,
            unloadGraceTicks,
            pendingChunksHardLimit,
            payloadCacheTtlTicks,
            payloadCacheMaxEntriesPerPlayer,
            clientReportedViewDistanceHardCap,
            globalPacketTemplateCacheMaxEntries,
            maxNbtReadsPerTick,
            preparedQueueHardLimit,
            tickInterval,
            metricsInfoLogsEnabled,
            metricsLogIntervalTicks,
            fallbackSkyLightLevel,
            fallbackBlockLightLevel,
            oceanFallbackEnabled,
            oceanFallbackSkyLightLevel);
        writeDefaults(configDir, configPath, config);
        return config;
    }

    private static int parseInt(Properties properties, String key, int fallback, int min, int max) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException exception) {
            ViewExtendMod.LOGGER.warn("Invalid value '{}' for '{}'. Using {}.", raw, key, fallback);
            return fallback;
        }
    }

    private static boolean parseBoolean(Properties properties, String key, boolean fallback) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        if ("true".equalsIgnoreCase(raw.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw.trim())) {
            return false;
        }
        ViewExtendMod.LOGGER.warn("Invalid boolean '{}' for '{}'. Using {}.", raw, key, fallback);
        return fallback;
    }

    private static void writeDefaults(Path configDir, Path configPath, ViewExtendConfig config) {
        try {
            Files.createDirectories(configDir);
        } catch (IOException exception) {
            ViewExtendMod.LOGGER.error("Failed to create config directory {}", configDir, exception);
            return;
        }

        Properties properties = new Properties();
        properties.setProperty(UNSIMULATED_VIEW_DISTANCE, Integer.toString(config.unsimulatedViewDistance()));
        properties.setProperty(MAX_CHUNKS_PER_PLAYER_PER_TICK, Integer.toString(config.maxChunksPerPlayerPerTick()));
        properties.setProperty(MAX_MAIN_THREAD_PREPARED_CHUNKS_PER_TICK, Integer.toString(config.maxMainThreadPreparedChunksPerTick()));
        properties.setProperty(MAX_UNLOADS_PER_PLAYER_PER_TICK, Integer.toString(config.maxUnloadsPerPlayerPerTick()));
        properties.setProperty(LOD1_START_DISTANCE, Integer.toString(config.lod1StartDistance()));
        properties.setProperty(LOD1_TOP_NON_AIR_SECTIONS, Integer.toString(config.lod1TopNonAirSections()));
        properties.setProperty(UNLOAD_BUFFER_CHUNKS, Integer.toString(config.unloadBufferChunks()));
        properties.setProperty(UNLOAD_GRACE_TICKS, Integer.toString(config.unloadGraceTicks()));
        properties.setProperty(PENDING_CHUNKS_HARD_LIMIT, Integer.toString(config.pendingChunksHardLimit()));
        properties.setProperty(PAYLOAD_CACHE_TTL_TICKS, Integer.toString(config.payloadCacheTtlTicks()));
        properties.setProperty(PAYLOAD_CACHE_MAX_ENTRIES_PER_PLAYER, Integer.toString(config.payloadCacheMaxEntriesPerPlayer()));
        properties.setProperty(CLIENT_REPORTED_VIEW_DISTANCE_HARD_CAP, Integer.toString(config.clientReportedViewDistanceHardCap()));
        properties.setProperty(GLOBAL_PACKET_TEMPLATE_CACHE_MAX_ENTRIES, Integer.toString(config.globalPacketTemplateCacheMaxEntries()));
        properties.setProperty(MAX_NBT_READS_PER_TICK, Integer.toString(config.maxNbtReadsPerTick()));
        properties.setProperty(PREPARED_QUEUE_HARD_LIMIT, Integer.toString(config.preparedQueueHardLimit()));
        properties.setProperty(TICK_INTERVAL, Integer.toString(config.tickInterval()));
        properties.setProperty(METRICS_INFO_LOGS_ENABLED, Boolean.toString(config.metricsInfoLogsEnabled()));
        properties.setProperty(METRICS_LOG_INTERVAL_TICKS, Integer.toString(config.metricsLogIntervalTicks()));
        properties.setProperty(FALLBACK_SKY_LIGHT_LEVEL, Integer.toString(config.fallbackSkyLightLevel()));
        properties.setProperty(FALLBACK_BLOCK_LIGHT_LEVEL, Integer.toString(config.fallbackBlockLightLevel()));
        properties.setProperty(OCEAN_FALLBACK_ENABLED, Boolean.toString(config.oceanFallbackEnabled()));
        properties.setProperty(OCEAN_FALLBACK_SKY_LIGHT_LEVEL, Integer.toString(config.oceanFallbackSkyLightLevel()));

        try (OutputStream output = Files.newOutputStream(configPath)) {
            properties.store(output, "View Extend server config");
        } catch (IOException exception) {
            ViewExtendMod.LOGGER.error("Failed to write {}", configPath, exception);
        }
    }
}
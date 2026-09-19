package com.silver.viewextend;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Global settings only: every player receives the same service policy. */
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
        int clientReportedViewDistanceHardCap,
        int globalPacketTemplateCacheMaxEntries,
        int globalPacketTemplateCacheTtlTicks,
        int globalPacketTemplateCacheMaxMiB,
        int maxNbtReadsPerTick,
        int preparedQueueHardLimit,
        int workerThreads,
        boolean metricsInfoLogsEnabled,
        int metricsLogIntervalTicks,
        int fallbackSkyLightLevel,
        int fallbackBlockLightLevel,
        boolean oceanFallbackEnabled,
        int oceanFallbackSkyLightLevel) {
    public long globalPacketTemplateCacheMaxBytes() {
        return globalPacketTemplateCacheMaxMiB * 1024L * 1024L;
    }

    public static ViewExtendConfig load() {
        Path path = Path.of("config", "viewextend.properties");
        Properties properties = new Properties();
        try {
            if (Files.exists(path)) {
                try (InputStream input = Files.newInputStream(path)) { properties.load(input); }
                if (!"3".equals(properties.getProperty("config-version"))) {
                    Path backup = path.resolveSibling("viewextend.properties.previous.bak");
                    if (!Files.exists(backup)) Files.copy(path, backup);
                    ViewExtendMod.LOGGER.info("Migrating View Extend throughput settings to v3; previous config saved at {}", backup);
                    migrate(properties);
                }
            }
            ViewExtendConfig config = fromProperties(properties);
            config.store(properties);
            Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(path)) {
                properties.store(output, "View Extend v3: equal throughput, worker-built shared packets; see README.md");
            }
            return config;
        } catch (IOException exception) {
            ViewExtendMod.LOGGER.error("Could not read/migrate/write {}; using parsed settings without overwriting it", path, exception);
            return fromProperties(properties);
        }
    }

    static void migrate(Properties properties) {
        if ("2".equals(properties.getProperty("config-version"))) {
            String[][] defaults = {
                {"max-chunks-per-player-per-tick", "256", "64"},
                {"max-main-thread-prepared-chunks-per-tick", "4096", "512"},
                {"pending-chunks-hard-limit", "2048", "128"},
                {"global-packet-template-cache-ttl-ticks", "6000", "1200"},
                {"global-packet-template-cache-max-mib", "1024", "256"},
                {"max-nbt-reads-per-tick", "1024", "256"},
                {"prepared-queue-hard-limit", "8192", "1024"}
            };
            for (String[] setting : defaults) {
                if (setting[1].equals(properties.getProperty(setting[0]))) properties.setProperty(setting[0], setting[2]);
            }
            properties.setProperty("config-version", "3");
            return;
        }
        if (!"0".equals(properties.getProperty("unsimulated-view-distance")))
            properties.setProperty("unsimulated-view-distance", "127");
        properties.setProperty("max-chunks-per-player-per-tick", "64");
        properties.setProperty("max-main-thread-prepared-chunks-per-tick", "512");
        properties.setProperty("max-unloads-per-player-per-tick", "2048");
        properties.setProperty("lod1-start-distance", "128");
        properties.setProperty("unload-grace-ticks", "20");
        properties.setProperty("pending-chunks-hard-limit", "128");
        properties.setProperty("client-reported-view-distance-hard-cap", "127");
        properties.setProperty("global-packet-template-cache-ttl-ticks", "1200");
        properties.setProperty("max-nbt-reads-per-tick", "256");
        properties.setProperty("prepared-queue-hard-limit", "1024");
        properties.setProperty("global-packet-template-cache-max-mib", "256");
        properties.remove("payload-cache-ttl-ticks");
        properties.remove("payload-cache-max-entries-per-player");
        properties.remove("tick-interval");
        properties.setProperty("config-version", "3");
    }

    static ViewExtendConfig fromProperties(Properties properties) {
        return new ViewExtendConfig(
                number(properties, "unsimulated-view-distance", 127, 0, 127),
                number(properties, "max-chunks-per-player-per-tick", 64, 1, 4096),
                number(properties, "max-main-thread-prepared-chunks-per-tick", 512, 1, 16384),
                number(properties, "max-unloads-per-player-per-tick", 2048, 1, 65536),
                number(properties, "lod1-start-distance", 128, 1, 4096),
                number(properties, "lod1-top-non-air-sections", 4, 1, 256),
                number(properties, "unload-buffer-chunks", 2, 0, 3),
                number(properties, "unload-grace-ticks", 20, 0, 1200),
                number(properties, "pending-chunks-hard-limit", 128, 128, 65536),
                number(properties, "client-reported-view-distance-hard-cap", 127, 1, 127),
                number(properties, "global-packet-template-cache-max-entries", 65536, 0, 1048576),
                number(properties, "global-packet-template-cache-ttl-ticks", 1200, 0, 72000),
                number(properties, "global-packet-template-cache-max-mib", 256, 0, 16384),
                number(properties, "max-nbt-reads-per-tick", 256, 1, 16384),
                number(properties, "prepared-queue-hard-limit", 1024, 128, 131072),
                number(properties, "worker-threads", 0, 0, 64),
                bool(properties, "metrics-info-logs-enabled", true),
                number(properties, "metrics-log-interval-ticks", 100, 20, 72000),
                number(properties, "fallback-sky-light-level", 15, 0, 15),
                number(properties, "fallback-block-light-level", 0, 0, 15),
                bool(properties, "ocean-fallback-enabled", false),
                number(properties, "ocean-fallback-sky-light-level", 0, 0, 15));
    }

    private void store(Properties properties) {
        properties.setProperty("config-version", "3");
        properties.setProperty("unsimulated-view-distance", String.valueOf(unsimulatedViewDistance));
        properties.setProperty("max-chunks-per-player-per-tick", String.valueOf(maxChunksPerPlayerPerTick));
        properties.setProperty("max-main-thread-prepared-chunks-per-tick", String.valueOf(maxMainThreadPreparedChunksPerTick));
        properties.setProperty("max-unloads-per-player-per-tick", String.valueOf(maxUnloadsPerPlayerPerTick));
        properties.setProperty("lod1-start-distance", String.valueOf(lod1StartDistance));
        properties.setProperty("lod1-top-non-air-sections", String.valueOf(lod1TopNonAirSections));
        properties.setProperty("unload-buffer-chunks", String.valueOf(unloadBufferChunks));
        properties.setProperty("unload-grace-ticks", String.valueOf(unloadGraceTicks));
        properties.setProperty("pending-chunks-hard-limit", String.valueOf(pendingChunksHardLimit));
        properties.setProperty("client-reported-view-distance-hard-cap", String.valueOf(clientReportedViewDistanceHardCap));
        properties.setProperty("global-packet-template-cache-max-entries", String.valueOf(globalPacketTemplateCacheMaxEntries));
        properties.setProperty("global-packet-template-cache-ttl-ticks", String.valueOf(globalPacketTemplateCacheTtlTicks));
        properties.setProperty("global-packet-template-cache-max-mib", String.valueOf(globalPacketTemplateCacheMaxMiB));
        properties.setProperty("max-nbt-reads-per-tick", String.valueOf(maxNbtReadsPerTick));
        properties.setProperty("prepared-queue-hard-limit", String.valueOf(preparedQueueHardLimit));
        properties.setProperty("worker-threads", String.valueOf(workerThreads));
        properties.setProperty("metrics-info-logs-enabled", String.valueOf(metricsInfoLogsEnabled));
        properties.setProperty("metrics-log-interval-ticks", String.valueOf(metricsLogIntervalTicks));
        properties.setProperty("fallback-sky-light-level", String.valueOf(fallbackSkyLightLevel));
        properties.setProperty("fallback-block-light-level", String.valueOf(fallbackBlockLightLevel));
        properties.setProperty("ocean-fallback-enabled", String.valueOf(oceanFallbackEnabled));
        properties.setProperty("ocean-fallback-sky-light-level", String.valueOf(oceanFallbackSkyLightLevel));
    }

    private static int number(Properties properties, String key, int fallback, int min, int max) {
        try { return Math.clamp(Integer.parseInt(properties.getProperty(key, String.valueOf(fallback)).trim()), min, max); }
        catch (NumberFormatException exception) { return fallback; }
    }

    private static boolean bool(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key, "").trim();
        return "true".equalsIgnoreCase(value) || (!"false".equalsIgnoreCase(value) && fallback);
    }
}

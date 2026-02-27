package com.silver.viewextend;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ViewExtendMod implements ModInitializer {
    public static final String MOD_ID = "viewextend";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static ViewExtendService service;

    @Override
    public void onInitialize() {
        ViewExtendConfig config = ViewExtendConfig.load();
        service = new ViewExtendService(config);
        LOGGER.info(
            "View Extend initialized (unsimulated-view-distance={}, max-chunks-per-player-per-tick={}, max-main-thread-prepared-chunks-per-tick={}, max-unloads-per-player-per-tick={}, lod1-start-distance={}, unload-buffer-chunks={}, unload-grace-ticks={}, pending-chunks-hard-limit={}, payload-cache-ttl-ticks={}, payload-cache-max-entries-per-player={}, client-reported-view-distance-hard-cap={}, global-packet-template-cache-max-entries={}, max-nbt-reads-per-tick={}, prepared-queue-hard-limit={}, tick-interval={}, metrics-info-logs-enabled={}, metrics-log-interval-ticks={})",
                config.unsimulatedViewDistance(),
                config.maxChunksPerPlayerPerTick(),
                config.maxMainThreadPreparedChunksPerTick(),
                config.maxUnloadsPerPlayerPerTick(),
                config.lod1StartDistance(),
                config.unloadBufferChunks(),
                config.unloadGraceTicks(),
                config.pendingChunksHardLimit(),
                config.payloadCacheTtlTicks(),
                config.payloadCacheMaxEntriesPerPlayer(),
                config.clientReportedViewDistanceHardCap(),
                config.globalPacketTemplateCacheMaxEntries(),
                config.maxNbtReadsPerTick(),
                config.preparedQueueHardLimit(),
            config.tickInterval(),
            config.metricsInfoLogsEnabled(),
            config.metricsLogIntervalTicks());
    }

    public static ViewExtendService getService() {
        return service;
    }
}

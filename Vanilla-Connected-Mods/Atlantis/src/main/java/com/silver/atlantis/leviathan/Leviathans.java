package com.silver.atlantis.leviathan;

import com.silver.atlantis.AtlantisMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import java.nio.file.Path;
import java.util.List;

public final class Leviathans {
    private static volatile LeviathansConfig activeConfig;
    private static volatile VirtualLeviathanStore virtualStore;
    private static volatile boolean shutdownFlushHookRegistered;

    private Leviathans() {
    }

    public static void init() {
        AtlantisMod.LOGGER.info("[Atlantis][leviathan] init start");
        LeviathansConfig loaded = LeviathansConfig.loadOrCreateStrict();
        VirtualLeviathanStore store = new VirtualLeviathanStore();

        activeConfig = loaded;
        virtualStore = store;
        LeviathanManager.init(loaded, store);

        if (!shutdownFlushHookRegistered) {
            ServerLifecycleEvents.SERVER_STOPPING.register(server -> flushVirtualState());
            shutdownFlushHookRegistered = true;
            AtlantisMod.LOGGER.info("[Atlantis][leviathan] registered shutdown flush hook");
        }

        AtlantisMod.LOGGER.info("[Atlantis][leviathan] loaded strict config path={} entityTypeId={} minimumLeviathans={}",
            LeviathansConfig.configPath(), loaded.entityTypeId, loaded.minimumLeviathans);
        AtlantisMod.LOGGER.info("[Atlantis][leviathan] loaded virtual store path={} count={}",
            Path.of(LeviathansConfig.configPath().getParent().toString(), VirtualLeviathanStore.FILE_NAME),
            store.size());
    }

    public static LeviathansConfig getConfig() {
        LeviathansConfig cfg = activeConfig;
        if (cfg == null) {
            throw new IllegalStateException("Leviathans config not initialized");
        }
        return cfg;
    }

    public static ReloadResult reloadConfig() {
        Path path = LeviathansConfig.configPath();
        AtlantisMod.LOGGER.info("[Atlantis][leviathan] config reload requested path={}", path);
        try {
            LeviathansConfig loaded = LeviathansConfig.loadStrict(path);
            activeConfig = loaded;
            LeviathanManager.onConfigReload(loaded);
            AtlantisMod.LOGGER.info("[Atlantis][leviathan] config reload applied path={} entityTypeId={} minimumLeviathans={}",
                path,
                loaded.entityTypeId,
                loaded.minimumLeviathans);
            return ReloadResult.success(path, loaded);
        } catch (LeviathansConfig.ValidationException e) {
            AtlantisMod.LOGGER.warn("[Atlantis][leviathan] config reload rejected path={} errors={}", path, e.errors());
            return ReloadResult.failure(path, e.errors());
        }
    }

    public static VirtualLeviathanStore getVirtualStore() {
        VirtualLeviathanStore store = virtualStore;
        if (store == null) {
            throw new IllegalStateException("VirtualLeviathanStore not initialized");
        }
        return store;
    }

    public static void flushVirtualState() {
        VirtualLeviathanStore store = virtualStore;
        if (store != null) {
            AtlantisMod.LOGGER.info("[Atlantis][leviathan] flushing virtual state store");
            store.flush();
            AtlantisMod.LOGGER.info("[Atlantis][leviathan] virtual state store flush complete");
        } else {
            AtlantisMod.LOGGER.debug("[Atlantis][leviathan] flush requested but virtual store is not initialized");
        }
    }

    public record ReloadResult(boolean applied, Path path, LeviathansConfig config, List<String> errors) {
        private static ReloadResult success(Path path, LeviathansConfig config) {
            return new ReloadResult(true, path, config, List.of());
        }

        private static ReloadResult failure(Path path, List<String> errors) {
            return new ReloadResult(false, path, null, errors);
        }
    }
}

package com.silver.atlantis;

import com.silver.atlantis.util.AtomicFiles;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Keeps a ".busy" file in the server folder while an Atlantis job runs (build, undo, search, mob seeding, or a cycle
 * step that is due). ~/mc-servers/bin/mc-ctl refuses to stop a server with a fresh marker, so the proxy's idle stop
 * waits until the job is done even if every player left. The file is refreshed every 30 s; a marker older than two
 * minutes (the server crashed) is ignored by mc-ctl.
 */
final class BusyMarker {
    private static final Path FILE = Path.of(".busy");
    private static final int CHECK_EVERY_TICKS = 20;
    private static final long REFRESH_MILLIS = 30_000L;

    private int ticks;
    private long writtenAtMillis;
    private boolean present;

    void register() {
        ServerTickEvents.END_SERVER_TICK.register(this::onTick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
        clear(); // a marker from before a crash says nothing about this run
    }

    private void onTick(MinecraftServer server) {
        if (++ticks < CHECK_EVERY_TICKS) {
            return;
        }
        ticks = 0;
        boolean busy = AtlantisMod.shouldKeepServerTicking(server);
        long now = System.currentTimeMillis();
        if (busy && (!present || now - writtenAtMillis >= REFRESH_MILLIS)) {
            try {
                AtomicFiles.writeString(FILE, "atlantis job running since the marker was first written\n");
                if (!present) {
                    AtlantisMod.LOGGER.info("[Atlantis] job running: this server won't be stopped for being idle until it finishes");
                }
                present = true;
                writtenAtMillis = now;
            } catch (Exception e) {
                AtlantisMod.LOGGER.warn("[Atlantis] could not write the busy marker {}", FILE.toAbsolutePath(), e);
            }
        } else if (!busy && present) {
            clear();
            AtlantisMod.LOGGER.info("[Atlantis] job finished: idle stop allowed again");
        }
    }

    private void clear() {
        try {
            Files.deleteIfExists(FILE);
        } catch (Exception e) {
            AtlantisMod.LOGGER.warn("[Atlantis] could not remove the busy marker {}", FILE.toAbsolutePath(), e);
        }
        present = false;
    }
}

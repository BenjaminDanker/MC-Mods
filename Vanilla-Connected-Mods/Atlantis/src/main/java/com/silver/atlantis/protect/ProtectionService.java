package com.silver.atlantis.protect;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

/**
 * Ticks background protection indexing work so large protection sets
 * never freeze the server watchdog.
 */
public final class ProtectionService {

    // Keep small; protection indexing is background work.
    private static final long TICK_BUDGET_NANOS = 2_000_000L; // 2ms

    public void register() {
        ServerTickEvents.END_SERVER_TICK.register(this::onEndTick);
    }

    private void onEndTick(MinecraftServer server) {
        ProtectionManager.INSTANCE.tick(TICK_BUDGET_NANOS);
    }
}

package com.silver.portals;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

/** Entry points for this mod's own mixins (they live in another package). */
public final class PortalsHooks {
    private PortalsHooks() {
    }

    public static CompoundTag placeAtArrival(MinecraftServer server, UUID player, CompoundTag saved) {
        return Arrivals.placeAtArrival(server, player, saved);
    }
}

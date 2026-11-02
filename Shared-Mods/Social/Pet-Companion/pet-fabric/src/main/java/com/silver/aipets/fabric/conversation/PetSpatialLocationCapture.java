package com.silver.aipets.fabric.conversation;

import com.silver.aipets.common.transport.SpatialLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

/** Existing server save namespace and coordinates; no terrain scan or new sensor. */
public final class PetSpatialLocationCapture {
    private PetSpatialLocationCapture() { }

    public static SpatialLocation capture(ServerLevel world, String backend, double x, double y, double z) {
        String save = world.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath()
                .normalize().getFileName().toString();
        return new SpatialLocation(backend, save, world.dimension().identifier().toString(), x, y, z);
    }
}

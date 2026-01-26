package com.silver.atlantis.construct;

import net.minecraft.server.MinecraftServer;

interface ConstructJob {
    boolean tick(MinecraftServer server);

    void onError(MinecraftServer server, Throwable throwable);

    default void onCancel(MinecraftServer server, String reason) {
    }
}

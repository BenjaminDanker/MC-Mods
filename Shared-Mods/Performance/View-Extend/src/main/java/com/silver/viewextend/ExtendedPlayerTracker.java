package com.silver.viewextend;

import net.minecraft.server.level.ServerPlayer;

/** Refresh stationary player tracking when the observer's visual chunk arrives. */
public interface ExtendedPlayerTracker {
    boolean viewextend$eligible();
    void viewextend$refresh(ServerPlayer observer);
}

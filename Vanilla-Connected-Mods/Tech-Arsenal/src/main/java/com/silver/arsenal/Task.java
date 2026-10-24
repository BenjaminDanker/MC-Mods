package com.silver.arsenal;

import net.minecraft.server.MinecraftServer;

/** Something that keeps happening over several ticks: a flame burst, a flying grenade, a mine waiting to be stepped on. */
interface Task {
    /** Runs once per server tick; returns true when finished and ready to be dropped. */
    boolean tick(MinecraftServer server);

    /** Called instead of {@link #tick} when the server stops, so entities a task owns do not outlive it. */
    default void cancel() {
    }
}

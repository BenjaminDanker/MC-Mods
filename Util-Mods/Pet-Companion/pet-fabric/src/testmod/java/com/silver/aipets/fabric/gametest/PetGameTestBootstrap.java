package com.silver.aipets.fabric.gametest;

import com.silver.aipets.fabric.permission.PetPermissions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/** GameTest-only mock permission provider; production still uses central authorization. */
public final class PetGameTestBootstrap implements ModInitializer {
    @Override public void onInitialize() {
        // This initializer only exists in the GameTest source set. Install after every
        // production initializer and again once the server has finished command setup.
        PetPermissions.install((source, node) -> true);
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                PetPermissions.install((source, node) -> true));
    }
}

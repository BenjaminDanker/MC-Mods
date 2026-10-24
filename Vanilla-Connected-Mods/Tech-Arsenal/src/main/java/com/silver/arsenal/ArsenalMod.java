package com.silver.arsenal;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Tech Arsenal: the Tech faction's weapons and gadgets, for vanilla clients. */
public final class ArsenalMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("tech-arsenal");

    @Override
    public void onInitialize() {
        List<Spec> specs = Tuning.load(FabricLoader.getInstance().getConfigDir().resolve("tech-arsenal.json"));
        int registered = Arms.register(specs).size();
        PolymerResourcePackUtils.addModAssets("tech-arsenal");
        Displays.init();
        ServerTickEvents.END_SERVER_TICK.register(Engine::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> Engine.shutdown());
        LOGGER.info("Tech Arsenal: {} items registered", registered);
    }
}

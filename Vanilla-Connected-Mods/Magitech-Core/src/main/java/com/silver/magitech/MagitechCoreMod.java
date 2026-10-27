package com.silver.magitech;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Magitech's resource loop. Benders gather Ley Crystals at ley wells; drills (and only drills) pull Deepsteel ore from deep
 * veins. Neither faction can gather the other's resource, so they trade for it or take it.
 */
public final class MagitechCoreMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("magitech");
    private Wells wells;

    @Override
    public void onInitialize() {
        MagitechItems.init();
        PolymerResourcePackUtils.addModAssets("magitech");
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_ORES,
            ResourceKey.create(Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath("magitech", "deep_vein")));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            MagitechConfig config = MagitechConfig.load(FabricLoader.getInstance().getConfigDir().resolve("magitech.json"));
            if (FabricLoader.getInstance().isModLoaded("server-portals")) {
                Landings landings = new Landings(server, config);
                server.execute(landings::ensure);
                DockRespawn.register(server, landings);
            }
            if (FabricLoader.getInstance().isModLoaded("bending")) {
                wells = new Wells(server, config);
            } else {
                LOGGER.info("Bending is not installed here, so ley wells are inactive");
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (wells != null) {
                wells.save();
            }
        });
        LOGGER.info("Magitech Core loaded");
    }
}

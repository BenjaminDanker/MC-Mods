package com.silver.portals;

import java.nio.file.Path;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Portals: see {@link Portals} for other mods, {@link PortalConfig} for the config file. */
public final class PortalsMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("portals");
    private static final Identifier BLOCK_ID = Identifier.fromNamespaceAndPath("portals", "portal");
    private static PortalBlock block;
    private static Path configFile;
    private static List<PortalConfig.Portal> portals = List.of();

    @Override
    public void onInitialize() {
        block = Registry.register(BuiltInRegistries.BLOCK, BLOCK_ID, new PortalBlock(BlockBehaviour.Properties.of()
            .setId(ResourceKey.create(Registries.BLOCK, BLOCK_ID))
            .noCollision().randomTicks().strength(-1.0f).sound(SoundType.GLASS).lightLevel(state -> 11)
            .pushReaction(PushReaction.BLOCK)));
        configFile = FabricLoader.getInstance().getConfigDir().resolve("portals.json");
        portals = PortalConfig.load(configFile);
        portals.forEach(Frames::register);
        LOGGER.info("{} portal(s) on this server", portals.size());

        PayloadTypeRegistry.clientboundPlay().register(Travel.Request.TYPE, Travel.Request.CODEC);
        Arrivals.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> PortalsCommands.register(dispatcher, registries));

        // lighting a frame: use the portal's item while looking at the frame
        UseItemCallback.EVENT.register((player, level, hand) -> {
            Item item = player.getItemInHand(hand).getItem();
            if (level.isClientSide() || !Frames.lightsSomething(item)) {
                return InteractionResult.PASS;
            }
            HitResult hit = player.pick(6.0D, 1.0F, false);
            if (hit instanceof BlockHitResult blockHit) {
                BlockPos inside = blockHit.getBlockPos().relative(blockHit.getDirection());
                if (Frames.light(level, inside, item, player)) {
                    return InteractionResult.SUCCESS_SERVER;
                }
            }
            return InteractionResult.PASS;
        });
    }

    static PortalBlock portalBlock() {
        return block;
    }

    static List<PortalConfig.Portal> portals() {
        return portals;
    }

    /** Adds a portal to the config (works from the next start); false when the name is taken. */
    static synchronized boolean register(PortalConfig.Portal portal) {
        List<PortalConfig.Portal> stored = PortalConfig.load(configFile);
        if (stored.stream().anyMatch(existing -> portal.name.equals(existing.name))) {
            return false;
        }
        stored.add(portal);
        PortalConfig.save(configFile, stored);
        return true;
    }

    static synchronized boolean unregister(String name) {
        List<PortalConfig.Portal> stored = PortalConfig.load(configFile);
        boolean removed = stored.removeIf(existing -> name.equals(existing.name));
        if (removed) {
            PortalConfig.save(configFile, stored);
        }
        return removed;
    }

    static synchronized boolean setArrival(String name, String world, double x, double y, double z) {
        List<PortalConfig.Portal> stored = PortalConfig.load(configFile);
        for (PortalConfig.Portal portal : stored) {
            if (name.equals(portal.name)) {
                PortalConfig.Arrival at = portal.arrival == null ? new PortalConfig.Arrival() : portal.arrival;
                at.world = world;
                at.x = x;
                at.y = y;
                at.z = z;
                portal.arrival = at;
                PortalConfig.save(configFile, stored);
                return true;
            }
        }
        return false;
    }
}

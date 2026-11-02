package com.silver.bossunlock;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.silver.bossunlock.BossUnlockConfig.Rule;
import com.silver.playersync.PlayerSync;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Unlocks an element (stored in Player Sync, so every server shares it) for the players who defeat a configured
 * secondary boss. The player only gets one cryptic line the first time; what it means appears later, in the
 * Magic server's skill tree.
 */
public final class BossUnlockMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("boss-unlock");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final ExecutorService io = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "boss-unlock-io");
        thread.setDaemon(true);
        return thread;
    });
    private BossUnlockConfig config = new BossUnlockConfig();

    @Override
    public void onInitialize() {
        config = load();
        ServerLivingEntityEvents.AFTER_DEATH.register(this::onDeath);
        LOGGER.info("Boss Unlock watching {} rule(s), shareRadius={}", config.rules.size(), config.shareRadiusBlocks);
    }

    private BossUnlockConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("boss-unlock.json");
        if (Files.notExists(path)) {
            BossUnlockConfig defaults = new BossUnlockConfig();
            try {
                Files.writeString(path, GSON.toJson(defaults));
            } catch (IOException failure) {
                LOGGER.warn("Could not write a default {}", path, failure);
            }
            LOGGER.warn("No rules configured: edit {}", path);
            return defaults;
        }
        try {
            BossUnlockConfig loaded = GSON.fromJson(Files.readString(path), BossUnlockConfig.class);
            return loaded == null ? new BossUnlockConfig() : loaded;
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("Could not read {}; no boss will unlock anything", path, failure);
            return new BossUnlockConfig();
        }
    }

    private void onDeath(LivingEntity entity, DamageSource source) {
        if (config.rules.isEmpty()) {
            return;
        }
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        Set<String> tags = entity.entityTags();
        for (Rule rule : config.rules) {
            BossRule matcher = new BossRule(rule.tag, rule.tagPrefix, rule.entityType, rule.element);
            String element = matcher.normalizedElement();
            if (element == null || !matcher.matches(type, tags)) {
                continue;
            }
            for (ServerPlayer player : slayers(entity, source)) {
                award(player, element);
            }
        }
    }

    /** The killer, plus anyone fighting alongside them within the share radius. */
    private List<ServerPlayer> slayers(LivingEntity boss, DamageSource source) {
        Set<ServerPlayer> result = new LinkedHashSet<>();
        if (source.getEntity() instanceof ServerPlayer killer) {
            result.add(killer);
        }
        if (boss.level() instanceof net.minecraft.server.level.ServerLevel level) {
            double radius = config.shareRadiusBlocks;
            for (ServerPlayer nearby : level.players()) {
                if (nearby.isAlive() && nearby.distanceToSqr(boss) <= radius * radius) {
                    result.add(nearby);
                }
            }
        }
        return List.copyOf(result);
    }

    /** The one line a slayer is told: something stayed with them, never what. */
    static String omen(String element) {
        return switch (element) {
            case "air" -> "The wind lingers around you a moment longer than it should.";
            case "water" -> "For a heartbeat, the sea beats with you.";
            case "earth" -> "The stone under your feet hums, and then is still.";
            default -> "A warmth settles in your chest, and stays.";
        };
    }

    private void award(ServerPlayer player, String element) {
        String name = player.getName().getString();
        String uuid = player.getStringUUID();
        int bit = switch (element) {
            case "air" -> PlayerSync.ElementProgress.AIR;
            case "water" -> PlayerSync.ElementProgress.WATER;
            case "earth" -> PlayerSync.ElementProgress.EARTH;
            default -> PlayerSync.ElementProgress.FIRE;
        };
        net.minecraft.server.MinecraftServer server = player.level().getServer();
        java.util.UUID id = player.getUUID();
        io.execute(() -> {
            try {
                boolean added = PlayerSync.unlockElement(name, uuid, bit);
                LOGGER.info("{} defeated a boss: {} unlock recorded (new={})", name, element, added);
                if (added) {
                    server.execute(() -> {
                        ServerPlayer online = server.getPlayerList().getPlayer(id);
                        if (online != null) {
                            online.sendSystemMessage(net.minecraft.network.chat.Component.literal(omen(element))
                                .withStyle(net.minecraft.ChatFormatting.DARK_AQUA, net.minecraft.ChatFormatting.ITALIC));
                        }
                    });
                }
            } catch (Exception failure) {
                LOGGER.error("Could not record the {} unlock for {}", element, name, failure);
            }
        });
    }
}

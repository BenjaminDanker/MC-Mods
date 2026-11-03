package com.silver.playersync;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.silver.authorization.NetworkShared;
import com.silver.authorization.fabric.NetworkAuthorizationMod;
import com.silver.playersync.net.ItemReturn;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Player Sync: what follows a player across the network (see {@link PlayerSnapshot}) and their network-wide progress
 * (see {@link PlayerSync}). The database login is the host's shared one; this server's name comes from Network
 * Authorization; the "family" servers that share whole inventories are the network value
 * {@code player_sync.family_servers} (default magic, tech, magitech). There is no per-server config.
 */
public final class PlayerSyncMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("player-sync");
    private static final String OLD_TAG_PREFIX = "mpds_";
    private static volatile RegistryOps<JsonElement> ops;
    private static boolean family;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            String name = NetworkAuthorizationMod.serverId().value();
            List<String> familyServers = Arrays.stream(NetworkShared.get("player_sync.family_servers", "magic,tech,magitech").split(","))
                .map(String::trim).toList();
            family = familyServers.contains(name);
            SyncStore.start(name, FabricLoader.getInstance().getConfigDir().resolve("player-sync").resolve("queue"));
            SharedAdvancements.configure(name);
            LOGGER.info("Player Sync on {}{}", name, family ? " (shares whole inventories with " + familyServers + ")" : "");
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SyncStore.writeQueuedNow(); // players were disconnected at shutdown and their saves queued
            SyncStore.stop();
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            renameOldTags(handler.getPlayer());
            JoinAndLeave.joined(server, handler.getPlayer());
            SharedAdvancements.joined(server, handler.getPlayer());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> JoinAndLeave.left(handler.getPlayer()));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> PlayerSyncCommands.register(dispatcher));

        PayloadTypeRegistry.serverboundPlay().register(ItemReturn.Request.TYPE, ItemReturn.Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ItemReturn.Response.TYPE, ItemReturn.Response.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ItemReturn.Request.TYPE, (request, context) ->
            context.server().execute(() -> returnItems(context.player(), request)));
    }

    /** Progress tags were once named "mpds_..."; give old players the current names. */
    private static void renameOldTags(ServerPlayer player) {
        for (String tag : List.copyOf(player.entityTags())) {
            if (tag.startsWith(OLD_TAG_PREFIX)) {
                player.removeTag(tag);
                player.addTag(AdvancementRewards.TAG_PREFIX + tag.substring(OLD_TAG_PREFIX.length()));
            }
        }
    }

    /** The proxy takes items back: removes matching items carried and stored, and answers with the counts. */
    private static void returnItems(ServerPlayer player, ItemReturn.Request request) {
        try {
            int removedCarried = 0;
            int removedStored = 0;
            int leftCarried = 0;
            int leftStored = 0;
            for (ItemReturn.Pair pair : request.pairs()) {
                removedCarried += StoredItems.removeCarried(player, pair.key(), pair.value());
                removedStored += StoredItems.removeStored(player.getStringUUID(), pair.key(), pair.value());
                leftCarried += StoredItems.countCarried(player, pair.key(), pair.value());
                leftStored += StoredItems.countStored(player.getStringUUID(), pair.key(), pair.value());
            }
            ServerPlayNetworking.send(player, new ItemReturn.Response(request.id(), true, removedCarried, removedStored, leftCarried, leftStored, ""));
        } catch (Exception failure) {
            LOGGER.error("Taking items back from {} failed", player.getName().getString(), failure);
            ServerPlayNetworking.send(player, new ItemReturn.Response(request.id(), false, 0, 0, 0, 0, failure.toString()));
        }
    }

    static RegistryOps<JsonElement> ops() {
        RegistryOps<JsonElement> current = ops;
        if (current == null) {
            throw new IllegalStateException("the server has not started yet");
        }
        return current;
    }

    static boolean familyServer() {
        return family;
    }

    /** For the advancement mixin. */
    public static boolean rewarded(Identifier advancement) {
        return AdvancementRewards.rewarded(advancement);
    }

    public static void advancementCompleted(ServerPlayer player, Identifier advancement) {
        AdvancementRewards.completed(player, advancement);
    }
}

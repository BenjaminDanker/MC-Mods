package com.silver.playersync;

import java.sql.SQLException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Join: load the player's saved state (off the server thread) and give it to them. Leave: capture it and queue the save.
 *
 * <p>Until a joining player's state has been given to them, they are "unsettled": leaving then saves nothing (their
 * stored state is the real one; saving this server's copy over it would lose what they carry).
 */
final class JoinAndLeave {
    /** Players whose saved state has not been given to them yet. */
    private static final Set<UUID> unsettled = ConcurrentHashMap.newKeySet();
    /** Load attempts while the row has no saved state yet, and while another server still holds it. */
    private static final int NEW_ROW_WAITS = 3;
    private static final int HELD_ROW_WAITS = 15;

    private JoinAndLeave() {
    }

    static void joined(MinecraftServer server, ServerPlayer player) {
        unsettled.add(player.getUUID());
        Thread loader = new Thread(() -> load(server, player), "Player-Sync-Load-" + player.getUUID());
        loader.setDaemon(true);
        loader.start();
    }

    /**
     * Fabric reports the join before the player is in the player list, so "not in the list" doesn't mean gone; only a
     * disconnected player that the list no longer points to has left.
     */
    private static boolean gone(MinecraftServer server, ServerPlayer player) {
        return player.hasDisconnected() && server.getPlayerList().getPlayer(player.getUUID()) != player;
    }

    private static void load(MinecraftServer server, ServerPlayer player) {
        String name = player.getName().getString();
        String uuid = player.getStringUUID();
        long pause = 1_000;
        int newRowWaits = 0;
        int heldRowWaits = 0;
        while (server.isRunning()) {
            if (gone(server, player)) {
                PlayerSyncMod.LOGGER.warn("{} left before their state was loaded; nothing applied", name);
                return; // the leave handler frees the row without saving
            }
            try {
                SyncStore.awaitDatabase();
                SyncStore.awaitSaved(player.getUUID()); // a save of theirs still queued here must land first
                Optional<SyncStore.Stored> found = SyncStore.find(uuid);
                if (found.isEmpty()) {
                    if (++newRowWaits < NEW_ROW_WAITS) {
                        Thread.sleep(1_000);
                        continue;
                    }
                    SyncStore.ensureRow(name, uuid);
                    // own the new row like any other, so a quick server switch waits for the first save
                    if (!SyncStore.take(uuid)) {
                        continue;
                    }
                    unsettled.remove(player.getUUID());
                    PlayerSyncMod.LOGGER.info("{} had no saved state; started a row, kept this server's state", name);
                    return;
                }
                SyncStore.Stored stored = found.get();
                if (!stored.free()) {
                    String holder = stored.server();
                    if (SyncStore.server().equals(holder) || "*".equals(holder)) {
                        // this server already holds it (it went down with the player online): its own file is newer
                        if (SyncStore.server().equals(holder)) {
                            SyncStore.reclaim(uuid);
                        }
                        unsettled.remove(player.getUUID());
                        PlayerSyncMod.LOGGER.info("{}'s state is already held by this server", name);
                        return;
                    }
                    if (++heldRowWaits < HELD_ROW_WAITS) { // the other server normally saves within moments
                        Thread.sleep(1_000);
                        continue;
                    }
                    if (SyncStore.takeFromDead(uuid, holder)) {
                        PlayerSyncMod.LOGGER.warn("{} stopped renewing its hold on {}'s state (it went down); taking the last save", holder, name);
                        Optional<SyncStore.Stored> fresh = SyncStore.find(uuid);
                        if (fresh.isPresent()) {
                            give(server, player, fresh.get().saved());
                        }
                        return;
                    }
                    PlayerSyncMod.LOGGER.error("ALERT: {}'s state is still held by {}; not loading or saving it", name, holder);
                    return;
                }
                if (!SyncStore.take(uuid)) {
                    continue; // another server took it between the read and now
                }
                give(server, player, stored.saved());
                return;
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                return;
            } catch (SQLException | RuntimeException failure) {
                PlayerSyncMod.LOGGER.warn("Loading {} is waiting for the database", name, failure);
                try {
                    Thread.sleep(pause);
                } catch (InterruptedException stopped) {
                    Thread.currentThread().interrupt();
                    return;
                }
                pause = Math.min(30_000, pause * 2);
            }
        }
        unsettled.remove(player.getUUID());
    }

    private static void give(MinecraftServer server, ServerPlayer player, PlayerSnapshot.Saved saved) {
        server.execute(() -> {
            if (gone(server, player)) {
                PlayerSyncMod.LOGGER.warn("{} left before their state could be given to them", player.getName().getString());
                return;
            }
            try {
                PlayerSnapshot.apply(player, saved);
                unsettled.remove(player.getUUID());
                PlayerSyncMod.LOGGER.info("Gave {} their saved state", player.getName().getString());
            } catch (RuntimeException failure) {
                PlayerSyncMod.LOGGER.error("ALERT: could not give {} their saved state; they won't be saved over it",
                    player.getName().getString(), failure);
            }
        });
    }

    static void left(ServerPlayer player) {
        UUID id = player.getUUID();
        String name = player.getName().getString();
        if (unsettled.remove(id)) {
            PlayerSyncMod.LOGGER.warn("Not saving {}: their saved state was never loaded here", name);
            Thread release = new Thread(() -> {
                try {
                    SyncStore.giveBack(id);
                } catch (SQLException failure) {
                    PlayerSyncMod.LOGGER.warn("Could not free {}'s row; it frees itself when the hold expires", name, failure);
                    SyncStore.forget(id);
                }
            }, "Player-Sync-Release-" + id);
            release.setDaemon(true);
            release.start();
            return;
        }
        try {
            // captured on the server thread before the player is cleaned up; the queue keeps retrying until written
            SyncStore.queue(PlayerSnapshot.capture(player));
            SyncStore.forget(id);
            PlayerSyncMod.LOGGER.info("Queued {}'s save", name);
        } catch (RuntimeException failure) {
            PlayerSyncMod.LOGGER.error("ALERT: could not capture {}'s state; nothing was taken from them", name, failure);
        }
    }
}

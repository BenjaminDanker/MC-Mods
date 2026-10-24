package com.silver.branchreset;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import mpds.mpds.sql;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Magic and Tech are exclusive branches. When Babylon moves a player to the other one, MPDS records a wipe request
 * for the branch they left; the server for that branch carries it out here, as soon as the player is offline.
 * Requests wait in the database, so a server that was off when the switch happened does it on its next start.
 */
public final class BranchResetMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("branch-reset");
    private static final int POLL_TICKS = 100;

    private final ExecutorService io = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "branch-reset-io");
        thread.setDaemon(true);
        return thread;
    });
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();
    private volatile String branch;
    private int ticks;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            branch = loadBranch();
            if (branch != null) {
                LOGGER.info("Branch Reset active for branch '{}'", branch);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (branch != null && ++ticks % POLL_TICKS == 0) {
                poll(server);
            }
        });
    }

    private static String loadBranch() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("branch-reset.json");
        try {
            if (!Files.exists(file)) {
                Files.writeString(file, "{\n  \"branch\": \"\"\n}\n");
            }
            JsonObject json = new Gson().fromJson(Files.readString(file), JsonObject.class);
            String value = json.has("branch") ? json.get("branch").getAsString().trim() : "";
            if (value.isEmpty()) {
                LOGGER.error("ALERT: set \"branch\" in {} (e.g. magic or tech); no wipes will run until then", file);
                return null;
            }
            return value;
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("ALERT: could not read {}; no wipes will run", file, failure);
            return null;
        }
    }

    private void poll(MinecraftServer server) {
        String mine = branch;
        io.execute(() -> {
            try {
                List<sql.BranchWipe> pending = sql.pendingBranchWipes(mine);
                if (!pending.isEmpty()) {
                    server.execute(() -> pending.forEach(wipe -> process(server, wipe)));
                }
            } catch (Exception failure) {
                LOGGER.error("ALERT: could not read pending branch wipes", failure);
            }
        });
    }

    /** Server thread. A player who is still online waits for the next poll. */
    private void process(MinecraftServer server, sql.BranchWipe wipe) {
        UUID player;
        try {
            player = UUID.fromString(wipe.uuid());
        } catch (IllegalArgumentException malformed) {
            if (inFlight.add(wipe.id())) {
                LOGGER.error("ALERT: branch wipe {} has a malformed uuid '{}'; skipped", wipe.id(), wipe.uuid());
            }
            return;
        }
        if (server.getPlayerList().getPlayer(player) != null || !inFlight.add(wipe.id())) {
            return;
        }
        try {
            int claimChunks = 0;
            if (FabricLoader.getInstance().isModLoaded("openpartiesandclaims")) {
                claimChunks = Claims.removeAll(server, player);
            }
            List<String> files = WipeFiles.erase(player,
                server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR),
                server.getWorldPath(LevelResource.PLAYER_STATS_DIR),
                server.getWorldPath(LevelResource.PLAYER_DATA_DIR));
            LOGGER.info("Wiped {} ({}) from branch '{}': {} claimed chunk(s) released, removed {}",
                wipe.name(), player, wipe.branch(), claimChunks, files);
            io.execute(() -> {
                try {
                    sql.markBranchWiped(wipe.id());
                } catch (Exception failure) {
                    LOGGER.error("ALERT: wiped {} but could not mark request {} done; it will run again", wipe.name(), wipe.id(), failure);
                } finally {
                    inFlight.remove(wipe.id());
                }
            });
        } catch (Exception | LinkageError failure) {
            inFlight.remove(wipe.id());
            LOGGER.error("ALERT: branch wipe for {} failed; will retry", wipe.name(), failure);
        }
    }
}

package com.silver.arcade.selftest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Test players: Carpet fake players, spawned and killed through Carpet's own /player command (so they are real entries in
 * the PlayerList and all the normal join and disconnect code runs). Everything a bot leaves on disk (player data,
 * stats, advancements) is remembered in a ledger file and removed again; only files that did not exist before the bot
 * joined are ever removed.
 */
public final class Bots {
    public static final String PREFIX = "zst_";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Bots that should get the first-visit intro (everybody else is tagged as met on join, so tests start at once). */
    static final Set<String> WANT_INTRO = ConcurrentHashMap.newKeySet();
    /** Names of bots this run spawned (they are recorded in the packet log, tagged on join and cleaned up). */
    static final Map<String, UUID> LIVE = new ConcurrentHashMap<>();
    /** Every UUID a bot had in this run (to check afterwards that nothing of them is left). */
    public static final Set<UUID> EVER = ConcurrentHashMap.newKeySet();
    /** Game mode bots join in (Carpet's default is creative): adventure on the arcade, survival on the world servers. */
    public static volatile String mode = "survival";
    private static Path ledger;
    /** Only files written after this moment are ever removed (nothing that was there before the test). */
    private static long since;

    private Bots() {
    }

    public static boolean isBot(ServerPlayer player) {
        return player.getClass().getName().equals("carpet.patches.EntityPlayerMPFake") || LIVE.containsKey(player.getGameProfile().name());
    }

    public static void setLedger(Path file) {
        ledger = file;
        since = System.currentTimeMillis() - 60_000;
        try {
            if (Files.exists(file)) {
                since = Math.min(since, Files.getLastModifiedTime(file).toMillis() - 3_600_000L); // leftovers of a crashed run
            }
        } catch (IOException ignored) {
            // keep the default
        }
    }

    /** Spawns a bot and waits until it is in the player list; returns it (server thread not required). */
    public static ServerPlayer spawn(T t, String name, double x, double y, double z, boolean intro) {
        if (!name.startsWith(PREFIX)) {
            throw new IllegalArgumentException("bot names start with " + PREFIX);
        }
        if (intro) {
            WANT_INTRO.add(name);
        }
        PacketLog.watch(name);
        t.run(() -> {
            seed(t.server, name);
            LIVE.put(name, UUID.randomUUID()); // placeholder until the real UUID is known
            remember(t.server, name, null);
            command(t.server, "player " + name + " spawn at " + x + " " + y + " " + z + " facing 0 0 in minecraft:overworld in " + mode);
        });
        if (!t.until(() -> t.server.getPlayerList().getPlayerByName(name) != null, 20 * 60)) {
            throw new IllegalStateException("bot " + name + " did not join within 60 s");
        }
        return t.on(() -> {
            ServerPlayer bot = t.server.getPlayerList().getPlayerByName(name);
            LIVE.put(name, bot.getUUID());
            EVER.add(bot.getUUID());
            remember(t.server, name, bot.getUUID());
            ready(bot);
            var range = bot.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
            if (range != null) {
                range.setBaseValue(60); // test bots reach every block of a room from where they stand
            }
            return bot;
        });
    }

    /** Brings a bot back after it was killed, from its saved data (like a player reconnecting). */
    public static ServerPlayer respawn(T t, String name) {
        t.run(() -> command(t.server, "player " + name + " spawn"));
        if (!t.until(() -> t.server.getPlayerList().getPlayerByName(name) != null, 20 * 60)) {
            throw new IllegalStateException("bot " + name + " did not come back within 60 s");
        }
        return t.on(() -> {
            ServerPlayer bot = t.server.getPlayerList().getPlayerByName(name);
            ready(bot);
            var range = bot.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
            if (range != null) {
                range.setBaseValue(60);
            }
            return bot;
        });
    }

    /** What a client sends when its world has loaded; without it the server ignores the bot's use-item and use-on-block packets. */
    public static void ready(ServerPlayer bot) {
        if (!bot.connection.hasClientLoaded()) {
            bot.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        }
    }

    /**
     * The server ignores a player's block clicks until the client has confirmed the last position it was sent (a teleport).
     * A bot has no client to confirm, so the confirmation packet is sent for it.
     */
    public static void ack(ServerPlayer bot) {
        Object waiting = Reflect.get(bot.connection, "awaitingPositionFromClient");
        if (waiting != null) {
            int id = Reflect.get(bot.connection, "awaitingTeleport");
            bot.connection.handleAcceptTeleportPacket(new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(id));
        }
    }

    /** The bot, or null when it is not online. */
    public static ServerPlayer get(T t, String name) {
        return t.on(() -> t.server.getPlayerList().getPlayerByName(name));
    }

    public static void kill(T t, String name) {
        t.run(() -> {
            if (t.server.getPlayerList().getPlayerByName(name) != null) {
                command(t.server, "player " + name + " kill");
            }
        });
        t.until(() -> t.server.getPlayerList().getPlayerByName(name) == null, 100);
    }

    /** Kills every bot of this run and removes the files they left; returns what was removed. */
    public static List<String> killAllAndClean(T t) {
        List<String> names = new ArrayList<>(LIVE.keySet());
        for (String name : names) {
            kill(t, name);
        }
        t.ticks(10);
        List<String> removed = clean(t.server);
        if (!removed.isEmpty()) {
            t.report.notes.add("bot files removed: " + removed);
        }
        for (String name : names) {
            PacketLog.unwatch(name);
        }
        LIVE.clear();
        WANT_INTRO.clear();
        return removed;
    }

    /**
     * Carpet looks a new bot's name up at Mojang on the server thread (seconds, when the network is slow); the name cache
     * answers that lookup without the network when it already knows the name, so the test's names are put there, as
     * offline players, and taken out again when the test is cleaned up.
     */
    static void seed(MinecraftServer server, String name) {
        try {
            server.services().nameToIdCache().add(net.minecraft.server.players.NameAndId.createOffline(name));
        } catch (RuntimeException failure) {
            // the lookup then goes to the network, slower but the same
        }
    }

    static void unseed(MinecraftServer server, Set<String> names) {
        try {
            Object cache = server.services().nameToIdCache();
            Map<String, Object> byName = Reflect.get(cache, "profilesByName");
            Map<UUID, Object> byId = Reflect.get(cache, "profilesByUUID");
            for (String name : names) {
                byName.keySet().removeIf(k -> k.equalsIgnoreCase(name));
                UUID offline = net.minecraft.core.UUIDUtil.createOfflinePlayerUUID(name);
                byId.remove(offline);
            }
            // also anything with the test prefix an earlier run left in usercache.json
            byName.keySet().removeIf(k -> k.startsWith(PREFIX));
            server.services().nameToIdCache().save();
        } catch (RuntimeException failure) {
            // best effort
        }
    }

    public static void command(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }

    private static List<Path> filesOf(MinecraftServer server, UUID id) {
        return List.of(
            server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(id + ".dat"),
            server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(id + ".dat_old"),
            server.getWorldPath(LevelResource.PLAYER_STATS_DIR).resolve(id + ".json"),
            server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR).resolve(id + ".json"));
    }

    private static void remember(MinecraftServer server, String name, UUID id) {
        if (ledger == null) {
            return;
        }
        try {
            JsonObject root = Files.exists(ledger) ? GSON.fromJson(Files.readString(ledger), JsonObject.class) : new JsonObject();
            JsonArray bots = root.has("bots") ? root.getAsJsonArray("bots") : new JsonArray();
            JsonObject entry = null;
            for (var e : bots) {
                if (e.getAsJsonObject().get("name").getAsString().equals(name)) {
                    entry = e.getAsJsonObject();
                }
            }
            if (entry == null) {
                entry = new JsonObject();
                entry.addProperty("name", name);
                bots.add(entry);
            }
            if (id != null) {
                entry.addProperty("uuid", id.toString());
            }
            root.add("bots", bots);
            root.addProperty("serverDir", server.getServerDirectory().toString());
            Files.createDirectories(ledger.getParent());
            Files.writeString(ledger, GSON.toJson(root));
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** Files of the run's bots that are still on disk (written during the run). */
    public static List<String> leftoverFiles(MinecraftServer server) {
        List<String> left = new ArrayList<>();
        for (UUID id : EVER) {
            for (Path file : filesOf(server, id)) {
                try {
                    if (Files.exists(file) && Files.getLastModifiedTime(file).toMillis() >= since) {
                        left.add(file.getFileName().toString());
                    }
                } catch (IOException ignored) {
                    // unreadable: not ours to judge
                }
            }
        }
        return left;
    }

    /** Removes every test-prefixed name from the server's name cache (and usercache.json). */
    public static void forgetAllNames(MinecraftServer server) {
        unseed(server, Set.of());
    }

    /** Names of the run's bots still in the server's name cache. */
    public static List<String> cachedNames(MinecraftServer server) {
        try {
            Map<String, Object> byName = Reflect.get(server.services().nameToIdCache(), "profilesByName");
            return byName.keySet().stream().filter(n -> n.startsWith(PREFIX)).toList();
        } catch (RuntimeException failure) {
            return List.of();
        }
    }

    /** Removes player data, stats and advancement files of the bots in the ledger. */
    public static List<String> clean(MinecraftServer server) {
        List<String> removed = new ArrayList<>();
        if (ledger == null || !Files.exists(ledger)) {
            return removed;
        }
        try {
            JsonObject root = GSON.fromJson(Files.readString(ledger), JsonObject.class);
            for (var e : root.getAsJsonArray("bots")) {
                JsonObject entry = e.getAsJsonObject();
                if (!entry.has("uuid")) {
                    continue;
                }
                String name = entry.get("name").getAsString();
                if (server.getPlayerList().getPlayerByName(name) != null) {
                    continue; // still online; not ours to touch yet
                }
                for (Path file : filesOf(server, UUID.fromString(entry.get("uuid").getAsString()))) {
                    if (Files.exists(file) && Files.getLastModifiedTime(file).toMillis() >= since && Files.deleteIfExists(file)) {
                        removed.add(server.getServerDirectory().toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize()).toString());
                    }
                }
            }
            Set<String> names = new java.util.HashSet<>();
            for (var e : root.getAsJsonArray("bots")) {
                names.add(e.getAsJsonObject().get("name").getAsString());
            }
            names.removeIf(n -> server.getPlayerList().getPlayerByName(n) != null);
            unseed(server, names);
            Files.deleteIfExists(ledger);
        } catch (IOException | RuntimeException failure) {
            removed.add("ERROR " + failure);
        }
        return removed;
    }

    /** UUIDs of the bots in a (possibly old) ledger. */
    public static Map<String, UUID> ledgerUuids() {
        Map<String, UUID> out = new LinkedHashMap<>();
        if (ledger == null || !Files.exists(ledger)) {
            return out;
        }
        try {
            JsonObject root = GSON.fromJson(Files.readString(ledger), JsonObject.class);
            for (var e : root.getAsJsonArray("bots")) {
                JsonObject entry = e.getAsJsonObject();
                if (entry.has("uuid")) {
                    out.put(entry.get("name").getAsString(), UUID.fromString(entry.get("uuid").getAsString()));
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // an unreadable ledger has nothing to give back
        }
        return out;
    }
}

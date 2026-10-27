package com.silver.magitech;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The old city's weekly standing. Everything a player does for their side counts, alone or together: crystals drawn
 * at a well, Deep Ore drilled, ruins' chests opened, hostile kills (a daily cap) and kills of the other side. The side
 * ahead when the week turns (Monday, Central time) is favoured for the next week: its wells or drills give a little
 * more. Personal marks (100 and 500 points in a week) are advancements, so a solo player always has something to aim
 * at. Server thread; saved to world/magitech/standing.json.
 */
final class Standing {
    private static final Logger LOGGER = LoggerFactory.getLogger("magitech");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String OBJECTIVE = "magitech_standing";

    static final class PlayerWeek {
        String faction;
        int points;
        String day;
        int killsToday;
    }

    static final class State {
        String week;
        int magic;
        int tech;
        /** The side favoured this week (last week's leader), or null. */
        String favoured;
        Map<String, PlayerWeek> players = new HashMap<>();
        /** Who helped the favoured side last week and has not been honoured yet (uuid to faction). */
        Map<String, String> toHonour = new HashMap<>();
    }

    private static Standing instance;

    private final MinecraftServer server;
    private final Path file;
    private State state;
    private boolean dirty;

    private Standing(MinecraftServer server) {
        this.server = server;
        this.file = server.getWorldPath(LevelResource.ROOT).resolve("magitech").resolve("standing.json");
    }

    static Standing start(MinecraftServer server) {
        Standing standing = new Standing(server);
        standing.state = standing.load();
        standing.rollIfDue();
        standing.refreshBoard();
        instance = standing;
        return standing;
    }

    /** The running standing, or null where Magitech Core has not started one (no Faction-Lock). */
    static Standing get() {
        return instance;
    }

    /** The side favoured this week, or null. */
    String favoured() {
        return state.favoured;
    }

    /** Credits {@code points} to the player's side; kind "kill" is subject to the daily cap. */
    void award(ServerPlayer player, int points, String kind) {
        String faction = Factions.of(player.getUUID());
        if (faction == null || points <= 0) {
            return;
        }
        rollIfDue();
        PlayerWeek mine = state.players.computeIfAbsent(player.getStringUUID(), id -> new PlayerWeek());
        mine.faction = faction;
        String today = StandingRules.day(Instant.now());
        if (!today.equals(mine.day)) {
            mine.day = today;
            mine.killsToday = 0;
        }
        if ("kill".equals(kind)) {
            int counted = StandingRules.countableKills(mine.killsToday, points);
            mine.killsToday += counted;
            points = counted;
            if (points <= 0) {
                return;
            }
        }
        int before = mine.points;
        mine.points += points;
        if ("magic".equals(faction)) {
            state.magic += points;
        } else {
            state.tech += points;
        }
        player.sendSystemMessage(Component.literal("+" + points + " for " + Factions.display(faction) + " (your week: " + mine.points + ")")
            .withStyle(ChatFormatting.DARK_AQUA), true);
        if (StandingRules.crossed(before, mine.points, StandingRules.RECLAIMER)) {
            Grants.grant(player, "magitech:old_city/reclaimer");
        }
        if (StandingRules.crossed(before, mine.points, StandingRules.PILLAR)) {
            Grants.grant(player, "magitech:old_city/pillar");
        }
        refreshBoard();
        dirty = true;
    }

    /** Called on join: honours a player whose side was favoured for last week's work. */
    void onJoin(ServerPlayer player) {
        rollIfDue();
        String side = state.toHonour.remove(player.getStringUUID());
        if (side != null) {
            Grants.grant(player, "magitech:old_city/remembered");
            dirty = true;
        }
    }

    /** Once a minute: turns the week over when Monday has come, and saves what changed. */
    void tick() {
        if (server.getTickCount() % 1200 == 0) {
            rollIfDue();
            if (dirty) {
                save();
            }
        }
    }

    private void rollIfDue() {
        String week = StandingRules.week(Instant.now());
        if (week.equals(state.week)) {
            return;
        }
        String winner = state.week == null ? null : StandingRules.winner(state.magic, state.tech);
        if (state.week != null) {
            LOGGER.info("The week of {} ends: Magic {}, Tech {}; favoured next: {}", state.week, state.magic, state.tech, winner);
        }
        Map<String, String> honour = new HashMap<>();
        if (winner != null) {
            state.players.forEach((id, week1) -> {
                if (winner.equals(week1.faction) && week1.points > 0) {
                    honour.put(id, winner);
                }
            });
        }
        state = new State();
        state.week = week;
        state.favoured = winner;
        state.toHonour = honour;
        if (winner != null) {
            server.getPlayerList().broadcastSystemMessage(Component.literal("The week has turned. The old city favours "
                + Factions.display(winner) + ": " + ("magic".equals(winner) ? "its wells give a little more." : "its drills bring up a little more.")
                + " A new week begins.").withStyle(ChatFormatting.GOLD), false);
            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                onJoin(online);
            }
        }
        refreshBoard();
        save();
    }

    private void refreshBoard() {
        Scoreboard board = server.getScoreboard();
        Objective objective = board.getObjective(OBJECTIVE);
        if (objective == null) {
            objective = board.addObjective(OBJECTIVE, ObjectiveCriteria.DUMMY,
                Component.literal("The Old City: this week").withStyle(ChatFormatting.GOLD),
                ObjectiveCriteria.RenderType.INTEGER, true, null);
        }
        board.setDisplayObjective(DisplaySlot.SIDEBAR, objective);
        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("Magic"), objective).set(state.magic);
        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("Tech"), objective).set(state.tech);
    }

    private State load() {
        try {
            if (Files.exists(file)) {
                State loaded = GSON.fromJson(Files.readString(file), State.class);
                if (loaded != null) {
                    if (loaded.players == null) {
                        loaded.players = new HashMap<>();
                    }
                    if (loaded.toHonour == null) {
                        loaded.toHonour = new HashMap<>();
                    }
                    return loaded;
                }
            }
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("ALERT: could not read {}; the standing starts fresh", file, failure);
        }
        return new State();
    }

    void save() {
        dirty = false;
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(state));
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) {
            LOGGER.error("ALERT: could not save {}", file, failure);
        }
    }

    /** For callers that only have a UUID (e.g. a loot context's entity that may not be a player). */
    static boolean isRival(UUID a, UUID b) {
        String first = Factions.of(a);
        String second = Factions.of(b);
        return first != null && second != null && !first.equals(second);
    }
}

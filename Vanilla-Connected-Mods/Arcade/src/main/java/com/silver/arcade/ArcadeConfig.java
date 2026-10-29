package com.silver.arcade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** config/arcade.json. {@code role}: "realm" on the arcade server, "door" on every world server. */
public final class ArcadeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String role = "door";
    /** The registry name of the arcade server, where doors lead. */
    public String arcadeServer = "arcade";
    /** The arcade's server-portals arrival (its hall). */
    public String hallPortal = "arcade_hall";
    /** This server's registry name (rewards are kept per server). */
    public String serverName = "";

    public Door door = new Door();
    /** Prize bands of this world, by the highest tier a run cashed out at. */
    public List<RewardBand> rewards = new ArrayList<>();

    public static final class Door {
        /** How often each player is considered for a door. */
        public int checkEveryMinutes = 5;
        /** No second door for the same player within this many minutes. */
        public int cooldownMinutes = 60;
        public int lifetimeSeconds = 120;
        public int minDistance = 6;
        public int maxDistance = 14;
        /** Players who have never visited: a rare discovery door, at most this many times, this many hours apart. */
        public double discoveryChance = 0.02;
        public int discoveryMax = 2;
        public int discoveryCooldownHours = 24;
        /** The Gamemaster's Die: rolling it opens a door on the spot (anyone may walk through while it stands). */
        public int dieCooldownSeconds = 120;
        public int dieDoorSeconds = 60;
        /** Where the die altar stands {x, y, z}; empty: open ground near the world spawn. */
        public int[] altar = new int[0];
    }

    public static final class RewardBand {
        public int minTier;
        /** How many times the band's pool is drawn. */
        public int rolls = 1;
        public List<RewardItem> pool = new ArrayList<>();
        /** Magic only: skill points added through MPDS. */
        public int skillPoints;
    }

    public static final class RewardItem {
        public String item;
        /** Instead of an item: a loot table of this world to roll (e.g. a datapack's custom items). */
        public String lootTable;
        public int min = 1;
        public int max = 1;
        public int weight = 1;
    }

    public static ArcadeConfig load(Path file) throws IOException {
        if (Files.exists(file)) {
            ArcadeConfig loaded = GSON.fromJson(Files.readString(file), ArcadeConfig.class);
            if (loaded != null) {
                return loaded;
            }
        }
        ArcadeConfig defaults = new ArcadeConfig();
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(defaults));
        return defaults;
    }
}

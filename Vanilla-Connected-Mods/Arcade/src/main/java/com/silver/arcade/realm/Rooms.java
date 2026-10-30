package com.silver.arcade.realm;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Every room type, and the picker: a random room that fits the number of players and the tier. */
public final class Rooms {
    private static final List<RoomType> TYPES = new ArrayList<>();

    private Rooms() {
    }

    public static void add(RoomType type) {
        TYPES.add(type);
    }

    public static List<RoomType> all() {
        return List.copyOf(TYPES);
    }

    /** A room for exactly {@code players} players at {@code tier}, avoiding the previous room's type when possible. */
    public static RoomType pick(int players, int tier, String previous, Random random) {
        return pick(players, tier, previous, random, false);
    }

    /** {@code testPlayers}: everyone in the room is a test player, so the testing force file may choose the room. */
    public static RoomType pick(int players, int tier, String previous, Random random, boolean testPlayers) {
        RoomType forced = testPlayers ? forced() : null;
        if (forced != null && players >= forced.minPlayers() && players <= forced.maxPlayers()) {
            return forced;
        }
        List<RoomType> fits = new ArrayList<>();
        for (RoomType type : TYPES) {
            if (players >= type.minPlayers() && players <= type.maxPlayers() && Difficulty.level(type, tier) > 0) {
                fits.add(type);
            }
        }
        if (fits.size() > 1) {
            fits.removeIf(type -> type.id().equals(previous));
        }
        return fits.isEmpty() ? null : fits.get(random.nextInt(fits.size()));
    }

    /** Testing: an optional second word in config/arcade-force-room.txt sets the tier; -1 when absent. */
    public static int forcedTier() {
        java.nio.file.Path file = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("arcade-force-room.txt");
        try {
            String[] words = java.nio.file.Files.exists(file) ? java.nio.file.Files.readString(file).trim().split("\\s+") : new String[0];
            return words.length > 1 ? Integer.parseInt(words[1]) : -1;
        } catch (java.io.IOException | NumberFormatException failure) {
            return -1;
        }
    }

    /** Testing: config/arcade-force-room.txt names a room the picker uses whenever it fits (delete the file to stop). */
    private static RoomType forced() {
        java.nio.file.Path file = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("arcade-force-room.txt");
        if (!java.nio.file.Files.exists(file)) {
            return null;
        }
        try {
            String id = java.nio.file.Files.readString(file).trim().split("\\s+")[0];
            return TYPES.stream().filter(type -> type.id().equals(id)).findFirst().orElse(null);
        } catch (java.io.IOException failure) {
            return null;
        }
    }
}

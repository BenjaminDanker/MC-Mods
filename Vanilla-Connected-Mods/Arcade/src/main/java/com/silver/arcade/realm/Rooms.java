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
        List<RoomType> fits = new ArrayList<>();
        for (RoomType type : TYPES) {
            if (type.fits(players, tier)) {
                fits.add(type);
            }
        }
        if (fits.size() > 1) {
            fits.removeIf(type -> type.id().equals(previous));
        }
        return fits.isEmpty() ? null : fits.get(random.nextInt(fits.size()));
    }
}

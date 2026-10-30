package com.silver.arcade.realm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Building aid: config/arcade-preview.txt lists rooms ("id tier" per line); each is built with no players in its own
 * arena and left standing (no clock) for renders. Where they stand goes to config/arcade-preview-slots.txt
 * ("id tier originX originY originZ"). Editing the file rebuilds; deleting it clears them. A line "slot N" puts the
 * following previews in slots N and up (fresh chunks there, to measure first builds).
 */
final class Previews {
    private static final Logger LOGGER = LoggerFactory.getLogger("arcade");
    private final ServerLevel level;
    private final List<Arena> standing = new ArrayList<>();
    private String last = "";

    Previews(ServerLevel level) {
        this.level = level;
    }

    /** Rooms built to be thrown away: {arena, due tick} (line "discard id tier ticks" measures what a thrown-away room costs). */
    private final List<Object[]> discards = new ArrayList<>();

    void tick(long now) {
        discards.removeIf(entry -> {
            if (now >= (Long) entry[1]) {
                ((Arena) entry[0]).release();
                return true;
            }
            return false;
        });
        if (now % 40 != 0) {
            return;
        }
        Path dir = FabricLoader.getInstance().getConfigDir();
        Path file = dir.resolve("arcade-preview.txt");
        String wanted;
        try {
            wanted = Files.exists(file) ? Files.readString(file).trim() : "";
        } catch (java.io.IOException failure) {
            return;
        }
        if (wanted.equals(last)) {
            return;
        }
        last = wanted;
        standing.forEach(Arena::release);
        standing.clear();
        StringBuilder slots = new StringBuilder();
        int from = 0;
        for (String line : wanted.split("\\R")) {
            String[] words = line.trim().split("\\s+");
            if (words[0].isEmpty()) {
                continue;
            }
            if (words[0].equals("slot") && words.length > 1) {
                from = Integer.parseInt(words[1]); // "slot N": the following previews go to slots N, N+1, ...
                continue;
            }
            boolean discard = words[0].equals("discard") && words.length > 3;
            if (discard) {
                words = java.util.Arrays.copyOfRange(words, 1, words.length);
            }
            int tier = words.length > 1 ? Integer.parseInt(words[1]) : 1;
            final String[] w = words;
            RoomType type = Rooms.all().stream().filter(t -> t.id().equals(w[0])).findFirst().orElse(null);
            if (type == null) {
                LOGGER.warn("Preview: no room {}", words[0]);
                continue;
            }
            Arena arena = Arena.take(level, from);
            if (discard) {
                discards.add(new Object[] {arena, now + Long.parseLong(words[2])});
            } else {
                standing.add(arena);
            }
            try {
                Room room = type.factory().get();
                room.timeLimitTicks(tier);
                long t0 = System.nanoTime();
                arena.label(type.id() + " t" + tier);
                room.start(arena, List.of(), tier);
                arena.commit();
                LOGGER.info("Preview: {} tier {} built in {} ms (before decoration)", type.id(), tier, (System.nanoTime() - t0) / 1_000_000);
                if (discard) {
                    continue;
                }
                arena.flushLedger();
                var o = arena.origin();
                slots.append(type.id()).append(' ').append(tier).append(' ').append(o.getX()).append(' ').append(o.getY()).append(' ')
                    .append(o.getZ()).append('\n');
                LOGGER.info("Preview: {} tier {} at {}", type.id(), tier, o);
            } catch (RuntimeException failure) {
                LOGGER.error("Preview: {} tier {} failed to build", type.id(), tier, failure);
            }
        }
        try {
            Files.writeString(dir.resolve("arcade-preview-slots.txt"), slots.toString());
        } catch (java.io.IOException failure) {
            LOGGER.error("Preview: could not write the slot list", failure);
        }
    }
}

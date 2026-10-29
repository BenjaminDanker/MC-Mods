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
 * ("id tier originX originY originZ"). Editing the file rebuilds; deleting it clears them.
 */
final class Previews {
    private static final Logger LOGGER = LoggerFactory.getLogger("arcade");
    private final ServerLevel level;
    private final List<Arena> standing = new ArrayList<>();
    private String last = "";

    Previews(ServerLevel level) {
        this.level = level;
    }

    void tick(long now) {
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
        for (String line : wanted.split("\\R")) {
            String[] words = line.trim().split("\\s+");
            if (words[0].isEmpty()) {
                continue;
            }
            int tier = words.length > 1 ? Integer.parseInt(words[1]) : 1;
            RoomType type = Rooms.all().stream().filter(t -> t.id().equals(words[0])).findFirst().orElse(null);
            if (type == null) {
                LOGGER.warn("Preview: no room {}", words[0]);
                continue;
            }
            Arena arena = Arena.take(level);
            standing.add(arena);
            try {
                Room room = type.factory().get();
                room.timeLimitTicks(tier);
                room.start(arena, List.of(), tier);
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

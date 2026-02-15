package com.silver.atlantis.leviathan;

import net.minecraft.entity.Entity;

import java.util.Optional;
import java.util.UUID;

public final class LeviathanIdTags {
    private static final String PREFIX = "atlantis_leviathan_id:";

    private LeviathanIdTags() {
    }

    public static String toTag(UUID id) {
        return PREFIX + id;
    }

    public static Optional<UUID> getId(Entity entity) {
        for (String tag : entity.getCommandTags()) {
            if (!tag.startsWith(PREFIX)) {
                continue;
            }
            try {
                return Optional.of(UUID.fromString(tag.substring(PREFIX.length())));
            } catch (Exception ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}

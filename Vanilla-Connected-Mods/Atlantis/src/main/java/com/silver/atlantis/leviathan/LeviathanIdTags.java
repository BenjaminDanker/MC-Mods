package com.silver.atlantis.leviathan;

import com.silver.atlantis.AtlantisMod;
import net.minecraft.entity.Entity;

import java.util.Optional;
import java.util.UUID;

public final class LeviathanIdTags {
    private static final String PREFIX = "atlantis_leviathan_id:";

    private LeviathanIdTags() {
    }

    public static String toTag(UUID id) {
        AtlantisMod.LOGGER.debug("[Atlantis][leviathan] id tag encode id={}", shortId(id));
        return PREFIX + id;
    }

    public static Optional<UUID> getId(Entity entity) {
        for (String tag : entity.getCommandTags()) {
            if (!tag.startsWith(PREFIX)) {
                continue;
            }
            try {
                UUID id = UUID.fromString(tag.substring(PREFIX.length()));
                AtlantisMod.LOGGER.debug("[Atlantis][leviathan] id tag parse success id={} entityUuid={}", shortId(id), entity.getUuidAsString());
                return Optional.of(id);
            } catch (Exception e) {
                AtlantisMod.LOGGER.warn("[Atlantis][leviathan] id tag parse failed tag={} entityUuid={} error={}",
                    tag,
                    entity.getUuidAsString(),
                    e.getMessage());
                return Optional.empty();
            }
        }
        AtlantisMod.LOGGER.debug("[Atlantis][leviathan] id tag missing entityUuid={}", entity.getUuidAsString());
        return Optional.empty();
    }

    private static String shortId(UUID id) {
        String value = id.toString();
        return value.length() <= 8 ? value : value.substring(0, 8);
    }
}

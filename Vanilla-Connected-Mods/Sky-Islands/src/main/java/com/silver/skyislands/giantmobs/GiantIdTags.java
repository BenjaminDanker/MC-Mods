package com.silver.skyislands.giantmobs;

import net.minecraft.entity.mob.GiantEntity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

public final class GiantIdTags {
    private static final String PREFIX = "sky_islands_giant_id:";
    private static final Logger LOGGER = LoggerFactory.getLogger(GiantIdTags.class);

    private GiantIdTags() {
    }

    public static String toTag(UUID id) {
        return PREFIX + id;
    }

    public static Optional<UUID> getId(GiantEntity giant) {
        for (String tag : giant.getCommandTags()) {
            if (!tag.startsWith(PREFIX)) {
                continue;
            }
            try {
                return Optional.of(UUID.fromString(tag.substring(PREFIX.length())));
            } catch (Exception ignored) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Sky-Islands][giants][id] invalid id tag on giant uuid={} tag={}", giant.getUuidAsString(), tag);
                }
                return Optional.empty();
            }
        }

        return Optional.empty();
    }
}
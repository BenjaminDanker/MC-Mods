package com.silver.skyislands.enderdragons;

import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

public final class DragonIdTags {
    private static final String PREFIX = "sky_islands_dragon_id:";
    private static final Logger LOGGER = LoggerFactory.getLogger(DragonIdTags.class);

    private DragonIdTags() {
    }

    public static String toTag(UUID id) {
        return PREFIX + id;
    }

    public static Optional<UUID> getId(EnderDragon dragon) {
        for (String tag : dragon.entityTags()) {
            if (!tag.startsWith(PREFIX)) {
                continue;
            }
            try {
                return Optional.of(UUID.fromString(tag.substring(PREFIX.length())));
            } catch (Exception ignored) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Sky-Islands][dragons][id] invalid id tag on dragon uuid={} tag={}", dragon.getStringUUID(), tag);
                }
                return Optional.empty();
            }
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][dragons][id] no id tag on dragon uuid={} tags={}", dragon.getStringUUID(), dragon.entityTags().size());
        }
        return Optional.empty();
    }
}

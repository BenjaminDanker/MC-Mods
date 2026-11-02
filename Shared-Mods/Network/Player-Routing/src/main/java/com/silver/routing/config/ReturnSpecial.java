package com.silver.routing.config;

import java.util.Objects;

/**
 * Represents a special item type to remove when a player uses /return.
 *
 * <p>Removal is done on the destination server by Player Sync (channel playersync:item_return) using the
 * provided custom-data key/value pair. The displayName is used for player-facing messages.
 */
public record ReturnSpecial(String key, String value, String displayName) {
    public ReturnSpecial {
        key = Objects.requireNonNull(key, "key");
        value = Objects.requireNonNull(value, "value");
        displayName = Objects.requireNonNull(displayName, "displayName");
    }
}

package com.silver.bossunlock;

import java.util.Collection;
import java.util.Locale;

/**
 * One secondary boss: what to look for on the entity that died, and the element its slayers unlock.
 * {@code tag} matches an exact entity tag, {@code tagPrefix} a tag that starts with the text, and
 * {@code entityType} (optional) the entity's type id. Every given condition must hold.
 */
public record BossRule(String tag, String tagPrefix, String entityType, String element) {
    public boolean matches(String type, Collection<String> tags) {
        if (entityType != null && !entityType.equals(type)) {
            return false;
        }
        if (tag == null && tagPrefix == null) {
            return entityType != null;
        }
        for (String candidate : tags) {
            if (tag != null && tag.equals(candidate)) {
                return true;
            }
            if (tagPrefix != null && candidate.startsWith(tagPrefix)) {
                return true;
            }
        }
        return false;
    }

    /** The element as a lower-case word the unlock bit table understands, or null when it is not one of the four. */
    public String normalizedElement() {
        if (element == null) {
            return null;
        }
        String value = element.toLowerCase(Locale.ROOT);
        return switch (value) {
            case "air", "water", "earth", "fire" -> value;
            default -> null;
        };
    }
}

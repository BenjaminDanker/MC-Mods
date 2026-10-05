package com.silver.aipets.service.memory;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative exclusive preference keys; likes are nonexclusive and never overwritten. */
public final class MemoryFactKey {
    private static final Pattern FAVORITE = Pattern.compile(
            "(?:owner(?:'s)?|my) (?:current )?favou?rite ([a-z]{3,24})");
    private static final Pattern PREFER = Pattern.compile(
            "(?:owner|i) (?:now |currently )?prefer(?:s)? ");
    private MemoryFactKey() { }

    public static Optional<String> fromOwnerStatement(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        Matcher favorite = FAVORITE.matcher(lower);
        if (favorite.find()) return Optional.of("owner.favorite." + favorite.group(1));
        if (PREFER.matcher(lower).find()) return Optional.of("owner.preference.general");
        return Optional.empty();
    }
}

package com.silver.prettychat.common;

import java.util.UUID;

/** Stable muted player colors shared by the Fabric and Velocity renderers. */
public final class PlayerColors {
    private PlayerColors() {}

    public static int nameColor(UUID playerId) {
        return fromHue(hue(playerId), 0.36, 0.67);
    }

    public static int messageColor(UUID playerId) {
        return fromHue(hue(playerId), 0.26, 0.56);
    }

    private static int hue(UUID id) {
        long value = id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 23);
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        value ^= value >>> 31;
        return (int) Math.floorMod(value, 360L);
    }

    private static int fromHue(int hue, double saturation, double lightness) {
        double h = hue / 60.0;
        double chroma = (1.0 - Math.abs(2.0 * lightness - 1.0)) * saturation;
        double x = chroma * (1.0 - Math.abs(h % 2.0 - 1.0));
        double red;
        double green;
        double blue;
        if (h < 1) { red = chroma; green = x; blue = 0; }
        else if (h < 2) { red = x; green = chroma; blue = 0; }
        else if (h < 3) { red = 0; green = chroma; blue = x; }
        else if (h < 4) { red = 0; green = x; blue = chroma; }
        else if (h < 5) { red = x; green = 0; blue = chroma; }
        else { red = chroma; green = 0; blue = x; }
        double offset = lightness - chroma / 2.0;
        return channel(red + offset) << 16 | channel(green + offset) << 8 | channel(blue + offset);
    }

    private static int channel(double value) {
        return (int) Math.round(Math.max(0, Math.min(1, value)) * 255.0);
    }
}

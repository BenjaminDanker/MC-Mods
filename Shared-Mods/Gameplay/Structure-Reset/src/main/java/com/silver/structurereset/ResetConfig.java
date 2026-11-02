package com.silver.structurereset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** config/structure-reset.json. Nothing is tracked until {@code structures} lists something. */
public final class ResetConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("structure-reset");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Exact structure ids or {@code namespace:*}. */
    public List<String> structures = List.of();
    /** How long a beaten structure stays as it is before it is restored. */
    public double intervalHours = 24;
    /** Zones larger than this many blocks are not tracked. */
    public long maxVolume = 4_000_000;
    /** Blocks added around the structure's own bounding box, since pieces do not cover everything built with them. */
    public int paddingHorizontal = 4;
    public int paddingDown = 16;
    public int paddingUp = 8;
    /** Ticks between checks of where players stand. */
    public int scanTicks = 40;
    /** How far outside the zone ejected players are placed. */
    public int ejectPadding = 3;
    /** A mob dying inside a zone beats it when it has at least this much max health... */
    public double bossMinHealth = 100;
    /** ...or when its type is listed here, or it carries one of these tags. */
    public List<String> bossTypes = List.of();
    public List<String> bossTags = List.of();

    public long intervalMillis() {
        return (long) (intervalHours * 3_600_000d);
    }

    public static ResetConfig load(Path file) {
        try {
            if (Files.exists(file)) {
                ResetConfig loaded = GSON.fromJson(Files.readString(file), ResetConfig.class);
                if (loaded != null) {
                    return loaded;
                }
            }
            ResetConfig defaults = new ResetConfig();
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(defaults));
            return defaults;
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("ALERT: could not read {}; tracking nothing until it is fixed", file, failure);
            return new ResetConfig();
        }
    }
}

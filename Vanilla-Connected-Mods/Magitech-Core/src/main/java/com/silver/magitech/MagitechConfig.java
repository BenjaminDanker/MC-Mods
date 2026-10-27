package com.silver.magitech;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** config/magitech.json: the tunable numbers of the resource loop. */
public final class MagitechConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("magitech");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Abilities a bender must use at a well before it yields. */
    public int chargeNeeded = 12;
    /** How long a well is quiet after yielding. */
    public double cooldownMinutes = 15;
    public int minYield = 1;
    public int maxYield = 3;
    /** Benders who channelled within this many seconds of a yield share in it: each past the first draws this many more crystals. */
    public int coopWindowSeconds = 60;
    public int coopBonusPerBender = 1;
    /** The most a well yields in total, base plus cooperation. */
    public int coopYieldCap = 6;
    /** A player's uses closer together than this do not count twice. */
    public long countGapMillis = 400;

    /**
     * Where each faction's landing dock is sought (it moves to the nearest flat land on the shared continent) and what its
     * return gate is made of. 1200 blocks apart: far enough to be each side's own, near enough that the sides meet.
     * Changing a target moves that dock at the next start.
     */
    public Dock magicDock = new Dock("magic", -600, 0, "minecraft:amethyst_block", "magitech_landing_magic");
    public Dock techDock = new Dock("tech", 600, 0, "polyfactory:steel_block", "magitech_landing_tech");
    /** An item nobody can hold, so the return gates are only ever lit by the server. */
    public String ignitionItem = "minecraft:barrier";

    public static final class Dock {
        public String faction;
        public int x;
        public int z;
        public String frameBlock;
        /** The portal registration (in server-portals.json) that this dock's gate belongs to. */
        public String portal;

        public Dock() {
        }

        public Dock(String faction, int x, int z, String frameBlock, String portal) {
            this.faction = faction;
            this.x = x;
            this.z = z;
            this.frameBlock = frameBlock;
            this.portal = portal;
        }
    }

    public long cooldownMillis() {
        return (long) (cooldownMinutes * 60_000d);
    }

    public static MagitechConfig load(Path file) {
        try {
            if (Files.exists(file)) {
                MagitechConfig loaded = GSON.fromJson(Files.readString(file), MagitechConfig.class);
                if (loaded != null) {
                    return loaded;
                }
            }
            MagitechConfig defaults = new MagitechConfig();
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(defaults));
            return defaults;
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("ALERT: could not read {}; using defaults", file, failure);
            return new MagitechConfig();
        }
    }
}

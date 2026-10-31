package com.silver.gaterites;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** config/gate-rites.json. {@code role} is "tech" or "magic"; the mod does nothing until it is set. */
public final class GateConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("gate-rites");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String role = "";
    /** The server a gate leads to, and the name of the landing the traveller arrives at there (a Portals arrival name). */
    public String targetServer = "magitech";
    public String arrivalPortal = "";
    /** How long a gate stays open for the one traveller it was made for. */
    public int openSeconds = 60;
    /** Tech: the frame the Gate Core lights, and the item its portal registration is lit with (an item nobody can hold). */
    public String frameBlock = "polyfactory:steel_block";
    public String ignitionItem = "minecraft:barrier";
    /** Tech: the tag the finished Tech story gives a player. Only they may light and enter a gate. */
    public String completedTag = "progress_tech_completed";

    public static GateConfig load(Path file) {
        try {
            if (Files.exists(file)) {
                GateConfig loaded = GSON.fromJson(Files.readString(file), GateConfig.class);
                if (loaded != null) {
                    return loaded;
                }
            }
            GateConfig defaults = new GateConfig();
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(defaults));
            return defaults;
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("ALERT: could not read {}; the gate does nothing", file, failure);
            return new GateConfig();
        }
    }
}

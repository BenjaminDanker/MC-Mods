package com.silver.portals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * This server's portals, in config/portals.json:
 * <pre>
 * {"portals": [{"name": "vanilla_entrance", "frame": "minecraft:bedrock", "lightWith": "minecraft:experience_bottle",
 *   "color": 5592575, "toServer": "magic", "toPortal": "magic_entrance", "anyoneCanLight": false,
 *   "arrival": {"world": "minecraft:overworld", "x": -2498.5, "y": 70, "z": -2294.5, "yaw": 90, "pitch": 0}}]}
 * </pre>
 * A portal sends whoever steps in to {@code toServer}, where they appear at the arrival of the portal named
 * {@code toPortal} (or of the portal with that name's arrival). {@code arrival} is where travellers announced under
 * this portal's name appear on THIS server. Changes take effect at the next start.
 */
final class PortalConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** One portal as written in the file. */
    static final class Portal {
        String name;
        String frame;
        String lightWith;
        int color;
        String toServer;
        String toPortal;
        boolean anyoneCanLight;
        Arrival arrival;

        Block frameBlock() {
            return BuiltInRegistries.BLOCK.getValue(Identifier.tryParse(frame));
        }

        Item lightItem() {
            return BuiltInRegistries.ITEM.getValue(Identifier.tryParse(lightWith));
        }

        @Override
        public String toString() {
            return name + ": " + frame + " lit with " + lightWith + " -> " + toServer + (toPortal == null ? "" : " (" + toPortal + ")")
                + (arrival == null ? "" : ", arrivals at " + arrival);
        }
    }

    /** Where arriving players appear; {@code yaw}/{@code pitch} absent keeps their own facing. */
    static final class Arrival {
        String world;
        double x;
        double y;
        double z;
        Float yaw;
        Float pitch;

        @Override
        public String toString() {
            return x + " " + y + " " + z + (world == null ? "" : " in " + world);
        }
    }

    private static final class File {
        List<Portal> portals = new ArrayList<>();
    }

    private PortalConfig() {
    }

    static List<Portal> load(Path path) {
        if (!Files.exists(path)) {
            save(path, new ArrayList<>());
            return new ArrayList<>();
        }
        try {
            File file = GSON.fromJson(Files.readString(path), File.class);
            return file == null || file.portals == null ? new ArrayList<>() : new ArrayList<>(file.portals);
        } catch (IOException | JsonParseException broken) {
            PortalsMod.LOGGER.error("ALERT: {} can't be read; no portals this run", path, broken);
            return new ArrayList<>();
        }
    }

    static void save(Path path, List<Portal> portals) {
        File file = new File();
        file.portals = portals;
        try {
            Files.createDirectories(path.getParent());
            Path partial = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(partial, GSON.toJson(file));
            Files.move(partial, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) {
            PortalsMod.LOGGER.error("Could not write {}", path, failure);
        }
    }
}

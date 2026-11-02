package com.silver.structurereset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The tracked instances and the chunks this mod forced, saved in the world folder. */
final class Index {
    private static final Logger LOGGER = LoggerFactory.getLogger("structure-reset");
    private static final Gson GSON = new GsonBuilder().create();

    Map<String, Instance> instances = new LinkedHashMap<>();
    /** "dimension|chunkX|chunkZ" of chunks this mod holds forced, so a crash cannot leave them forced forever. */
    List<String> forced = new ArrayList<>();

    static Index load(Path file) {
        try {
            if (Files.exists(file)) {
                Index loaded = GSON.fromJson(Files.readString(file), Index.class);
                if (loaded != null) {
                    return loaded;
                }
            }
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("ALERT: could not read {}; starting with no tracked structures", file, failure);
        }
        return new Index();
    }

    void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(this));
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) {
            LOGGER.error("ALERT: could not save {}", file, failure);
        }
    }
}

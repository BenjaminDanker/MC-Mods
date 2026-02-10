package com.silver.atlantis.construct.state;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConstructRunStateIO {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConstructRunStateIO() {
    }

    public static void write(Path file, ConstructRunState state) throws IOException {
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(state, writer);
        }
    }

    public static ConstructRunState read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            ConstructRunState state = GSON.fromJson(reader, ConstructRunState.class);
            if (state == null) {
                throw new IOException("Invalid construct state file: " + file);
            }
            if (state.version() != ConstructRunState.CURRENT_VERSION) {
                throw new IOException("Unsupported construct state version: " + state.version());
            }
            return state;
        }
    }
}

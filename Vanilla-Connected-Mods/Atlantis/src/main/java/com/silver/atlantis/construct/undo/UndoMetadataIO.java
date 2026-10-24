package com.silver.atlantis.construct.undo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class UndoMetadataIO {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private UndoMetadataIO() {
    }

    public static void write(Path file, UndoRunMetadata metadata) throws IOException {
        com.silver.atlantis.util.AtomicFiles.writeString(file, GSON.toJson(metadata));
    }

    public static UndoRunMetadata read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            UndoRunMetadata metadata = GSON.fromJson(reader, UndoRunMetadata.class);
            if (metadata == null) {
                throw new IOException("Invalid undo metadata file: " + file);
            }
            if (metadata.version() != UndoRunMetadata.CURRENT_VERSION) {
                throw new IOException("Unsupported undo metadata version: " + metadata.version());
            }
            return metadata;
        }
    }
}

package com.silver.atlantis.construct;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SchematicSliceScanner {

    private static final Pattern TRAILING_NUMBER = Pattern.compile("(\\d+)(?:\\.[^.]+)?$", Pattern.CASE_INSENSITIVE);

    private SchematicSliceScanner() {
    }

    public static Path defaultSchematicDir() {
        // "config folder" in Fabric terms.
        // We use a mod-specific subfolder so we don't accidentally scan other mods' configs.
        return FabricLoader.getInstance().getConfigDir().resolve("atlantis");
    }

    static List<SchematicSlice> scanSlices(Path dir) throws IOException {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return List.of();
        }

        List<SchematicSlice> slices = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream
                .filter(p -> Files.isRegularFile(p))
                .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".schem"))
                .forEach(path -> slices.add(new SchematicSlice(extractIndex(path.getFileName().toString()), path)));
        }

        slices.sort(Comparator
            .comparingInt(SchematicSlice::index)
            .thenComparing(s -> s.path().getFileName().toString(), String.CASE_INSENSITIVE_ORDER)
        );

        return slices;
    }

    private static int extractIndex(String filename) {
        Matcher matcher = TRAILING_NUMBER.matcher(filename);
        if (!matcher.find()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }
}

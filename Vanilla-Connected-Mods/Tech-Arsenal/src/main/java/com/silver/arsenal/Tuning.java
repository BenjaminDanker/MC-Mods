package com.silver.arsenal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The arsenal's numbers. Defaults come from arms.json inside the mod; config/tech-arsenal.json holds the server's own values
 * ("arms": {"rivet_pistol": {"use.damage": 2.4}}) and the deepsteel-tempering bonus. The file is written with every value on
 * first start so balancing is an edit, not a rebuild.
 */
final class Tuning {
    private static final Logger LOGGER = LoggerFactory.getLogger("tech-arsenal");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String[] SECTIONS = {"melee", "use", "hit", "ammo", "cell"};

    /** Damage multiplier of a deepsteel-tempered weapon. */
    static double temperedDamage = 1.3;

    private Tuning() {
    }

    /** The specs from arms.json with the server's overrides applied. */
    static List<Spec> load(Path configFile) {
        List<Spec> specs = readDefaults();
        JsonObject overrides = readOverrides(configFile);
        if (overrides == null) {
            writeDefaults(configFile, specs);
        } else {
            apply(specs, overrides);
        }
        return specs;
    }

    static List<Spec> readDefaults() {
        try (InputStream in = Tuning.class.getResourceAsStream("/data/arsenal/arms.json")) {
            if (in == null) {
                throw new IllegalStateException("arms.json is missing from the mod");
            }
            JsonObject root = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            return GSON.fromJson(root.get("arms"), new TypeToken<List<Spec>>() { }.getType());
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("could not read arms.json", failure);
        }
    }

    private static JsonObject readOverrides(Path file) {
        try {
            if (!Files.exists(file)) {
                return null;
            }
            return GSON.fromJson(Files.readString(file), JsonObject.class);
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("ALERT: could not read {}; the arsenal uses its built-in numbers", file, failure);
            return new JsonObject();
        }
    }

    private static void apply(List<Spec> specs, JsonObject overrides) {
        if (overrides.has("tempered_damage") && overrides.get("tempered_damage").isJsonPrimitive()) {
            temperedDamage = overrides.get("tempered_damage").getAsDouble();
        }
        if (!overrides.has("arms") || !overrides.get("arms").isJsonObject()) {
            return;
        }
        JsonObject arms = overrides.getAsJsonObject("arms");
        for (Spec spec : specs) {
            if (!arms.has(spec.id) || !arms.get(spec.id).isJsonObject()) {
                continue;
            }
            for (Map.Entry<String, JsonElement> entry : arms.getAsJsonObject(spec.id).entrySet()) {
                String path = entry.getKey();
                int dot = path.indexOf('.');
                if (dot < 0 || !entry.getValue().isJsonPrimitive()) {
                    LOGGER.error("ALERT: tech-arsenal.json: '{}' for {} is not 'section.key': value; ignored", path, spec.id);
                    continue;
                }
                Map<String, Object> section = section(spec, path.substring(0, dot));
                if (section == null) {
                    LOGGER.error("ALERT: tech-arsenal.json: {} has no '{}' section; ignored", spec.id, path.substring(0, dot));
                    continue;
                }
                var primitive = entry.getValue().getAsJsonPrimitive();
                section.put(path.substring(dot + 1), primitive.isNumber() ? (Object) primitive.getAsDouble()
                    : primitive.isBoolean() ? (Object) primitive.getAsBoolean() : primitive.getAsString());
            }
        }
    }

    private static Map<String, Object> section(Spec spec, String name) {
        return switch (name) {
            case "melee" -> spec.melee;
            case "use" -> spec.use;
            case "hit" -> spec.hit;
            case "ammo" -> spec.ammo;
            case "cell" -> spec.cell;
            default -> null;
        };
    }

    private static void writeDefaults(Path file, List<Spec> specs) {
        Map<String, Object> arms = new LinkedHashMap<>();
        for (Spec spec : specs) {
            Map<String, Object> numbers = new LinkedHashMap<>();
            for (String name : SECTIONS) {
                Map<String, Object> section = section(spec, name);
                if (section == null) {
                    continue;
                }
                for (Map.Entry<String, Object> entry : section.entrySet()) {
                    if (entry.getValue() instanceof Number) {
                        numbers.put(name + "." + entry.getKey(), entry.getValue());
                    }
                }
            }
            if (!numbers.isEmpty()) {
                arms.put(spec.id, numbers);
            }
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("tempered_damage", temperedDamage);
        root.put("arms", arms);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root));
            LOGGER.info("Wrote the arsenal's default numbers to {}", file);
        } catch (IOException failure) {
            LOGGER.error("ALERT: could not write {}", file, failure);
        }
    }

    /** Names of the sections that exist on a spec, for the tooltip generator. */
    static List<String> sectionsOf(Spec spec) {
        List<String> found = new ArrayList<>();
        for (String name : SECTIONS) {
            if (section(spec, name) != null) {
                found.add(name);
            }
        }
        return found;
    }
}

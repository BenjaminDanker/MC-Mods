package com.silver.elementchoice;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.silver.elementchoice.skills.AdvancementPoints;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Loads the advancement allowlist from config/element-choice-advancements.json, writing defaults the first time. */
final class AdvancementRewards {
    private static final Logger LOGGER = LoggerFactory.getLogger("element-choice");
    private static volatile AdvancementPoints current = AdvancementPoints.DEFAULT;

    private AdvancementRewards() {
    }

    static AdvancementPoints current() {
        return current;
    }

    static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("element-choice-advancements.json");
        try {
            if (!Files.exists(file)) {
                JsonObject defaults = new JsonObject();
                defaults.add("namespaces", new Gson().toJsonTree(AdvancementPoints.DEFAULT.namespaces()));
                defaults.add("excluded", new Gson().toJsonTree(Set.of()));
                defaults.addProperty("task", 1);
                defaults.addProperty("goal", 2);
                defaults.addProperty("challenge", 3);
                defaults.add("bossTags", new Gson().toJsonTree(Map.of()));
                Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(defaults));
            }
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            current = new AdvancementPoints(strings(json, "namespaces"), strings(json, "excluded"),
                json.get("task").getAsInt(), json.get("goal").getAsInt(), json.get("challenge").getAsInt(), bosses(json));
            LOGGER.info("Advancement points: namespaces={} task/goal/challenge={}/{}/{}",
                current.namespaces(), current.task(), current.goal(), current.challenge());
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("ALERT: could not read {}; advancements pay with the defaults", file, failure);
            current = AdvancementPoints.DEFAULT;
        }
    }

    private static Map<String, Integer> bosses(JsonObject json) {
        Map<String, Integer> out = new HashMap<>();
        if (json.has("bossTags")) {
            json.getAsJsonObject("bossTags").entrySet().forEach(entry -> out.put(entry.getKey(), entry.getValue().getAsInt()));
        }
        return Map.copyOf(out);
    }

    private static Set<String> strings(JsonObject json, String key) {
        Set<String> out = new HashSet<>();
        json.getAsJsonArray(key).forEach(element -> out.add(element.getAsString()));
        return Set.copyOf(out);
    }
}

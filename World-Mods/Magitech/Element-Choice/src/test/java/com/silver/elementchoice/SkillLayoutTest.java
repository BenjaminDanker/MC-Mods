package com.silver.elementchoice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.silver.elementchoice.skills.AbilityCatalog;
import com.silver.elementchoice.skills.AbilityDef;
import com.silver.elementchoice.skills.Realm;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The screen layout and the artwork are generated together; these checks catch a layout that no longer fits the catalog. */
class SkillLayoutTest {
    private static JsonObject layout() {
        return new Gson().fromJson(new InputStreamReader(
            SkillLayoutTest.class.getResourceAsStream("/skill-layout.json"), StandardCharsets.UTF_8), JsonObject.class);
    }

    @Test
    void everyScreenFitsTheChestWithoutOverlaps() {
        JsonObject screens = layout().getAsJsonObject("screens");
        for (var screen : screens.entrySet()) {
            Set<Integer> slots = new HashSet<>();
            for (JsonElement element : screen.getValue().getAsJsonObject().getAsJsonArray("nodes")) {
                JsonObject node = element.getAsJsonObject();
                int row = node.get("row").getAsInt();
                int col = node.get("col").getAsInt();
                assertTrue(row >= 0 && row < 6 && col >= 0 && col < 9, screen.getKey() + " node outside the grid: " + node);
                assertTrue(slots.add(row * 9 + col), screen.getKey() + " has two nodes in one slot: " + node);
            }
        }
    }

    @Test
    void everyAbilityAppearsOnItsElementScreen() {
        JsonObject screens = layout().getAsJsonObject("screens");
        for (Realm realm : Realm.values()) {
            Set<String> onScreen = new HashSet<>();
            for (JsonElement element : screens.getAsJsonObject(realm.id()).getAsJsonArray("nodes")) {
                onScreen.add(element.getAsJsonObject().get("id").getAsString());
            }
            for (AbilityDef def : AbilityCatalog.forRealm(realm)) {
                assertTrue(onScreen.contains(def.id()), def.id() + " is missing from the " + realm.id() + " screen");
            }
        }
    }

    @Test
    void everyScreenHasItsArtworkGlyph() {
        JsonObject glyphs = layout().getAsJsonObject("glyphs");
        assertEquals(5, glyphs.size());
        for (var entry : glyphs.entrySet()) {
            assertEquals(1, entry.getValue().getAsString().length(), entry.getKey());
        }
    }
}

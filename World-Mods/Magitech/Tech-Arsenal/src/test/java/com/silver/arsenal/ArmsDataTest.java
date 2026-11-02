package com.silver.arsenal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The table, its recipes, art and names must agree: a missing texture or recipe would only show up on a player's screen. */
class ArmsDataTest {
    private static final List<Spec> SPECS = Tuning.readDefaults();

    private static boolean exists(String path) {
        return ArmsDataTest.class.getResource(path) != null;
    }

    private static List<String> ids() {
        return SPECS.stream().flatMap(spec -> spec.temper ? java.util.stream.Stream.of(spec.id, spec.id + "_tempered") : java.util.stream.Stream.of(spec.id)).toList();
    }

    @Test
    void everyArmHasItsRecipeArtModelAndName() throws Exception {
        JsonObject lang;
        try (InputStream in = ArmsDataTest.class.getResourceAsStream("/assets/arsenal/lang/en_us.json")) {
            lang = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
        }
        for (String id : ids()) {
            assertTrue(exists("/data/arsenal/recipe/" + id + ".json"), id + " has no recipe");
            assertTrue(exists("/assets/arsenal/items/" + id + ".json"), id + " has no item definition");
            assertTrue(exists("/assets/arsenal/models/item/" + id + ".json"), id + " has no model");
            assertTrue(exists("/assets/arsenal/textures/item/" + id + ".png"), id + " has no texture");
            assertTrue(lang.has("item.arsenal." + id), id + " has no name");
        }
    }

    @Test
    void theWorldModelsExist() {
        for (String name : List.of("sentry_turret_deployed", "proximity_mine_placed")) {
            assertTrue(exists("/assets/arsenal/items/" + name + ".json"), name);
            assertTrue(exists("/assets/arsenal/models/item/" + name + ".json"), name);
        }
        assertTrue(exists("/assets/arsenal/textures/item/palette.png"));
    }

    @Test
    void idsAreUniqueAndReferencesResolve() {
        Set<String> seen = new HashSet<>();
        for (Spec spec : SPECS) {
            assertTrue(seen.add(spec.id), "duplicate id " + spec.id);
        }
        for (Spec spec : SPECS) {
            if (spec.ammo != null) {
                Params ammo = new Params(spec.ammo);
                String item = ammo.str(ammo.has("item") ? "item" : "cell", "");
                assertTrue(seen.contains(item), spec.id + " uses unknown ammunition " + item);
            }
            if (spec.use != null && "bolt".equals(new Params(spec.use).kind())) {
                assertTrue(seen.contains(new Params(spec.use).str("projectile", "")), spec.id + " throws an unknown projectile");
            }
        }
    }

    @Test
    void everyBehaviourNamedInTheTableExists() {
        for (Spec spec : SPECS) {
            if (spec.use != null) {
                assertNotNull(Actions.create(new Params(spec.use).kind()), spec.id + " has unknown use kind " + new Params(spec.use).kind());
            }
            if (spec.hit != null) {
                assertNotNull(HitProcs.create(new Params(spec.hit)), spec.id + " has unknown hit kind " + new Params(spec.hit).kind());
            }
        }
    }

    @Test
    void theTableIsLargeEnoughToKeepPlayersBusy() {
        long weapons = SPECS.stream().filter(spec -> spec.melee != null || spec.use != null).count();
        long melee = SPECS.stream().filter(spec -> spec.melee != null).count();
        long ranged = SPECS.stream().filter(spec -> spec.use != null && spec.melee == null && spec.ammo != null).count();
        assertTrue(weapons >= 25, "weapons and gadgets: " + weapons);
        assertTrue(melee >= 8, "melee: " + melee);
        assertTrue(ranged >= 10, "ranged: " + ranged);
        assertEquals(SPECS.size() + SPECS.stream().filter(spec -> spec.temper).count(), ids().size());
    }
}

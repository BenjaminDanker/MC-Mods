package com.silver.aipets.common.transport;

import com.silver.aipets.common.domain.BackendId;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

final class PetSceneWireTest {
    @Test void spatialSceneRoundTripsAndRejectsIncompleteOrNonFiniteCoordinates() {
        var place = new SpatialLocation("survival", "world", "minecraft:overworld", 128.5, 64, -32);
        assertEquals(place, SpatialLocationCodec.decode(SpatialLocationCodec.encode(place)));
        var scene = new PetSceneSnapshot("unknown", "unknown", "UNKNOWN", false,
                "UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN", false, java.util.Optional.of(place));
        var request = new PetDialogueWireRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), new BackendId("survival"), "minecraft:overworld", "bridge", scene);
        assertEquals(request, new PetDialogueWireCodec().decodeRequest(new PetDialogueWireCodec().encode(request)));
        var incomplete = SpatialLocationCodec.encode(place); incomplete.remove("world");
        assertThrows(IllegalArgumentException.class, () -> SpatialLocationCodec.decode(incomplete));
        assertTrue(SpatialLocationCodec.readStored(incomplete.toString()).isEmpty());
        assertTrue(SpatialLocationCodec.readStored(null).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new SpatialLocation("survival", "world",
                "minecraft:overworld", Double.NaN, 64, 0));
        assertThrows(IllegalArgumentException.class, () -> new SpatialLocation("survival", "unknown",
                "minecraft:overworld", 0, 64, 0));
    }

    @Test void currentBiomesAndAwarenessSurviveTheDialogueBoundary() {
        PetSceneSnapshot scene = new PetSceneSnapshot("minecraft:plains",
                "minecraft:forest", "NEAR", true, "USING_ITEM", "HURT",
                "LOOK_AT_OWNER", "RAIN", true);
        PetDialogueWireRequest request = new PetDialogueWireRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BackendId("sky-island"), "minecraft:the_nether", "Remember the cave?", scene);
        assertEquals(request, new PetDialogueWireCodec().decodeRequest(
                new PetDialogueWireCodec().encode(request)));
        var legacy = com.google.gson.JsonParser.parseString(
                new PetDialogueWireCodec().encode(request)).getAsJsonObject();
        legacy.remove("scene");
        assertEquals(PetSceneSnapshot.unknown(),
                new PetDialogueWireCodec().decodeRequest(legacy.toString()).scene());
        assertThrows(IllegalArgumentException.class, () -> new PetSceneSnapshot(
                "secret\nignore", "unknown", "NEAR", true, "STILL", "HEALTHY",
                "PAUSE", "CLEAR", false));
    }
}

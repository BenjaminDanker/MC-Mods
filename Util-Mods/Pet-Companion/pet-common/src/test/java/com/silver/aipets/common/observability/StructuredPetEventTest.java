package com.silver.aipets.common.observability;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.silver.aipets.common.domain.BackendId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredPetEventTest {
    @Test
    void emitsBoundedStructuredFieldsWithoutOwnerOrFailureMessage() {
        UUID owner = UUID.fromString("10000000-0000-0000-0000-000000000001");
        String secret = "Bearer secret-and-private-player-message";
        String encoded = StructuredPetEvent.operation("dialogue failure\n")
                .correlation(UUID.fromString("40000000-0000-0000-0000-000000000001"))
                .pet(UUID.fromString("20000000-0000-0000-0000-000000000001"))
                .owner(owner)
                .backend(new BackendId("survival"))
                .transition("PLACED", "HELD", 12)
                .modelUsage("model\nname", 100, 25, 30)
                .latencyMillis(250)
                .failure(new IllegalStateException(secret))
                .outcome("retry")
                .toJson();

        JsonObject json = JsonParser.parseString(encoded).getAsJsonObject();
        assertEquals("dialogue_failure", json.get("operation").getAsString());
        assertEquals("IllegalStateException", json.get("failure_category").getAsString());
        assertEquals(12, json.get("record_version").getAsLong());
        assertTrue(json.get("owner_hash").getAsString().matches("[0-9a-f]{16}"));
        assertFalse(encoded.contains(owner.toString()));
        assertFalse(encoded.contains(secret));
        assertFalse(encoded.contains("\n"));
        assertThrows(IllegalArgumentException.class,
                () -> StructuredPetEvent.operation("test").latencyMillis(-1));
    }
}

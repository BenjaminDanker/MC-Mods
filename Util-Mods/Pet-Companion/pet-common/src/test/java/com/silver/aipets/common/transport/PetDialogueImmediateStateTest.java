package com.silver.aipets.common.transport;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PetDialogueImmediateStateTest {
    @Test
    void actionAndAttentionRoundTripAcrossFabricWire() {
        PetDialogueWireCodec codec = new PetDialogueWireCodec();
        PetDialogueWireResult original = new PetDialogueWireResult(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                PetDialogueWireResult.Status.SUCCEEDED, "The rain has eased.",
                PetDialogueAction.LOOK_AT_INTEREST, PetDialogueAttention.CURIOUS);
        PetDialogueWireResult decoded = codec.decodeResult(codec.encode(original));
        assertEquals(original, decoded);
    }
}

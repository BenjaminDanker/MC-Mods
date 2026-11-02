package com.silver.aipets.common.transport;

import java.util.Objects;

/** Bounded prompt-part accounting returned only to permission-gated admin tooling. */
public record DialogueContextUsageWire(
        int systemTokens,
        int identityTokens,
        int shortTermDbTokens,
        int longTermRelationalTokens,
        int longTermVectorTokens,
        int recentTurnsDbTokens,
        int gameContextTokens,
        int ownerInputTokens,
        int instructionTokens,
        int totalPromptTokens,
        int shortTermItems,
        int longTermRelationalItems,
        int longTermVectorItems,
        int recentTurnItems) {
    public DialogueContextUsageWire {
        int[] values = {
                systemTokens, identityTokens, shortTermDbTokens,
                longTermRelationalTokens, longTermVectorTokens, recentTurnsDbTokens,
                gameContextTokens, ownerInputTokens, instructionTokens, totalPromptTokens,
                shortTermItems, longTermRelationalItems, longTermVectorItems, recentTurnItems};
        for (int value : values) {
            if (value < 0) throw new IllegalArgumentException("context usage values must be non-negative");
        }
        if (totalPromptTokens < 1) {
            throw new IllegalArgumentException("totalPromptTokens must be positive");
        }
    }

    public static DialogueContextUsageWire empty() {
        return new DialogueContextUsageWire(0, 0, 0, 0, 0, 0, 0, 0, 0,
                1, 0, 0, 0, 0);
    }
}

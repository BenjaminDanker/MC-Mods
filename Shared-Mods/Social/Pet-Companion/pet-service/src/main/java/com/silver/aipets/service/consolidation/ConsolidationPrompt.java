package com.silver.aipets.service.consolidation;

import com.silver.aipets.service.dialogue.DialoguePrompt;

import java.util.Objects;

public record ConsolidationPrompt(DialoguePrompt prompt, CandidateSelection selection) {
    public ConsolidationPrompt {
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(selection, "selection");
        if (selection.selected().isEmpty()) {
            throw new IllegalArgumentException("A consolidation prompt needs selected events");
        }
    }
}

package com.silver.aipets.service.dialogue;

import java.util.Objects;
import java.util.Optional;

public record DialogueResult(
        DialogueResultStatus status,
        String playerMessage,
        Optional<ValidatedDialogueOutput> output,
        Optional<DialoguePersistResult> persisted) {
    public DialogueResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(playerMessage, "playerMessage");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(persisted, "persisted");
        boolean succeeded = status == DialogueResultStatus.SUCCEEDED;
        if (succeeded != (output.isPresent() && persisted.isPresent())) {
            throw new IllegalArgumentException("Only success may contain output/persistence result");
        }
    }

    static DialogueResult denied(DialogueResultStatus status, String message) {
        return new DialogueResult(status, message, Optional.empty(), Optional.empty());
    }

    static DialogueResult succeeded(
            ValidatedDialogueOutput output, DialoguePersistResult persisted) {
        return new DialogueResult(
                DialogueResultStatus.SUCCEEDED, output.reply(),
                Optional.of(output), Optional.of(persisted));
    }
}

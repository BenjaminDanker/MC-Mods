package com.silver.aipets.service.dialogue;

public enum DialogueResultStatus {
    SUCCEEDED,
    NO_AI_ACCESS,
    SLEEPING,
    UNSAFE_INPUT,
    LIMITED,
    PROVIDER_FAILURE,
    INVALID_MODEL_OUTPUT,
    PERSISTENCE_FAILURE
}

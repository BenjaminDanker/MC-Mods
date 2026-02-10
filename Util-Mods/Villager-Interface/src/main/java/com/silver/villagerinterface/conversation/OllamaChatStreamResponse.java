package com.silver.villagerinterface.conversation;

public final class OllamaChatStreamResponse {
    private OllamaChatMessage message;
    private boolean done;

    public OllamaChatMessage message() {
        return message;
    }

    public boolean done() {
        return done;
    }
}

package com.silver.villagerinterface.conversation;

public final class OllamaChatStreamResponse {
    private ChatMessage message;
    private boolean done;

    public ChatMessage message() {
        return message;
    }

    public boolean done() {
        return done;
    }
}

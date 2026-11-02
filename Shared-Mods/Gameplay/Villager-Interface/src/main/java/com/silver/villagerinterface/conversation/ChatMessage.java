package com.silver.villagerinterface.conversation;

/** A provider-neutral chat message understood by Ollama and OpenAI. */
public final class ChatMessage {
    private final String role;
    private final String content;

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public static ChatMessage system(String content) { return new ChatMessage("system", content); }
    public static ChatMessage user(String content) { return new ChatMessage("user", content); }
    public static ChatMessage assistant(String content) { return new ChatMessage("assistant", content); }

    public String role() { return role; }
    public String content() { return content; }
}

package com.silver.villagerinterface.conversation;

public final class OllamaChatMessage {
    private final String role;
    private final String content;

    public OllamaChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public static OllamaChatMessage system(String content) {
        return new OllamaChatMessage("system", content);
    }

    public static OllamaChatMessage user(String content) {
        return new OllamaChatMessage("user", content);
    }

    public static OllamaChatMessage assistant(String content) {
        return new OllamaChatMessage("assistant", content);
    }

    public String role() {
        return role;
    }

    public String content() {
        return content;
    }
}

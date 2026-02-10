package com.silver.villagerinterface.conversation;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class OllamaChatRequest {
    private final String model;
    private final List<OllamaChatMessage> messages;
    private final boolean stream;
    @SerializedName("keep_alive")
    private final String keepAlive;

    public OllamaChatRequest(String model, List<OllamaChatMessage> messages, String keepAlive) {
        this.model = model;
        this.messages = messages;
        this.stream = false;
        this.keepAlive = keepAlive;
    }

    public OllamaChatRequest(String model, List<OllamaChatMessage> messages, String keepAlive, boolean stream) {
        this.model = model;
        this.messages = messages;
        this.stream = stream;
        this.keepAlive = keepAlive;
    }

    public String model() {
        return model;
    }

    public List<OllamaChatMessage> messages() {
        return messages;
    }

    public boolean stream() {
        return stream;
    }

    public String keepAlive() {
        return keepAlive;
    }
}

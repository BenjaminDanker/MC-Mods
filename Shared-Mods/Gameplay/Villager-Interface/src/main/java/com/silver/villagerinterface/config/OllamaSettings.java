package com.silver.villagerinterface.config;

public final class OllamaSettings {
    private final String baseUrl;
    private final String model;
    private final String keepAlive;
    private final int timeoutSeconds;

    public OllamaSettings(String baseUrl, String model, String keepAlive, int timeoutSeconds) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.keepAlive = keepAlive;
        this.timeoutSeconds = timeoutSeconds;
    }

    public String baseUrl() { return baseUrl; }
    public String model() { return model; }
    public String keepAlive() { return keepAlive; }
    public int timeoutSeconds() { return timeoutSeconds; }
}

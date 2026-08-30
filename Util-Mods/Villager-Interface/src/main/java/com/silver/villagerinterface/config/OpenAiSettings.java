package com.silver.villagerinterface.config;

public final class OpenAiSettings {
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final String reasoningEffort;
    private final int maxCompletionTokens;
    private final int timeoutSeconds;
    private final Boolean logUsage;

    public OpenAiSettings(String baseUrl, String apiKey, String model, String reasoningEffort, int maxCompletionTokens, int timeoutSeconds, Boolean logUsage) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.reasoningEffort = reasoningEffort;
        this.maxCompletionTokens = maxCompletionTokens;
        this.timeoutSeconds = timeoutSeconds;
        this.logUsage = logUsage;
    }

    public String baseUrl() { return baseUrl; }
    public String apiKey() { return apiKey; }
    public String model() { return model; }
    public String reasoningEffort() { return reasoningEffort; }
    /** Zero leaves the API's hard output limit unset. */
    public int maxCompletionTokens() { return maxCompletionTokens; }
    public int timeoutSeconds() { return timeoutSeconds; }
    public Boolean logUsage() { return logUsage; }
}

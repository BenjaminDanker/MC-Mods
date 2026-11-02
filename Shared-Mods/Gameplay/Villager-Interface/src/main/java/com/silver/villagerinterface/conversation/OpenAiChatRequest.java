package com.silver.villagerinterface.conversation;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** Request body for OpenAI's streamed Chat Completions API. */
public final class OpenAiChatRequest {
    private final String model;
    private final List<ChatMessage> messages;
    private final boolean stream;
    @SerializedName("reasoning_effort")
    private final String reasoningEffort;
    @SerializedName("max_completion_tokens")
    private final Integer maxCompletionTokens;
    @SerializedName("prompt_cache_key")
    private final String promptCacheKey;
    @SerializedName("stream_options")
    private final StreamOptions streamOptions;
    private final String verbosity = "low";

    public OpenAiChatRequest(String model, List<ChatMessage> messages, String reasoningEffort, int maxCompletionTokens, boolean includeUsage, String promptCacheKey) {
        this.model = model;
        this.messages = messages;
        this.stream = true;
        this.reasoningEffort = reasoningEffort;
        this.maxCompletionTokens = maxCompletionTokens > 0 ? maxCompletionTokens : null;
        this.streamOptions = includeUsage ? new StreamOptions() : null;
        this.promptCacheKey = promptCacheKey;
    }

    private static final class StreamOptions {
        @SerializedName("include_usage")
        private final boolean includeUsage = true;
    }
}

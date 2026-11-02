package com.silver.aipets.service.dialogue;

/** Must be implemented with the configured model's tokenizer in production. */
public interface DialogueTokenCounter {
    int count(String text);

    String truncate(String text, int maximumTokens);
}

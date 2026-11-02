package com.silver.aipets.service.dialogue;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;

import java.util.Objects;

/** OpenAI-compatible o200k_base tokenizer used by GPT-5-family dialogue prompts. */
public final class JTokkitDialogueTokenCounter implements DialogueTokenCounter {
    private final Encoding encoding;

    public JTokkitDialogueTokenCounter() {
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        this.encoding = registry.getEncoding(EncodingType.O200K_BASE);
    }

    @Override
    public int count(String text) {
        Objects.requireNonNull(text, "text");
        return encoding.countTokens(text);
    }

    @Override
    public String truncate(String text, int maximumTokens) {
        Objects.requireNonNull(text, "text");
        if (maximumTokens < 1) throw new IllegalArgumentException("maximumTokens must be positive");
        if (count(text) <= maximumTokens) return text;
        var all = encoding.encode(text);
        var bounded = new com.knuddels.jtokkit.api.IntArrayList(maximumTokens);
        for (int index = 0; index < maximumTokens && index < all.size(); index++) {
            bounded.add(all.get(index));
        }
        return encoding.decode(bounded);
    }
}
